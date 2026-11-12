package lernapp;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Kompiliert Benutzer-Code und fuehrt ihn in einer eigenen, abgeschotteten JVM aus.
 * - eigener Prozess: Abstuerze/Endlosschleifen koennen die App nicht beschaedigen
 * - Zeitlimit, Speicherlimit, Ausgabelimit
 * - Security-Manager ohne Rechte: kein Dateizugriff, kein Netzwerk, keine Programme starten
 */
public class CodeRunner {

    public static final int TIMEOUT_SECONDS = 10;
    public static final int MAX_OUTPUT_CHARS = 200_000;
    public static final int MAX_INTERACTIVE_MINUTES = 30;

    public record CompileError(long line, long column, String code, String message) {}

    public static class Result {
        public boolean compiled;
        public boolean noMain;
        public int exitCode = -1;
        public boolean timedOut, stopped, outputLimit;
        public long millis;
        public String fileName = "Main.java";
        public final StringBuilder out = new StringBuilder();
        public final StringBuilder err = new StringBuilder();
        public final List<CompileError> errors = new ArrayList<>();
    }

    public interface Listener {
        default void out(String s) {}
        default void err(String s) {}
    }

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:(?:final|abstract|sealed|strictfp)\\s+)*(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");

    private final Path baseDir;
    private final Path policyFile;
    private volatile Process current;
    private volatile boolean stopRequested;

    public CodeRunner() throws IOException {
        baseDir = Files.createTempDirectory("javalernapp-");
        policyFile = baseDir.resolve("sandbox.policy");
        Files.writeString(policyFile, "// Sandbox: dein Code bekommt keine zusaetzlichen Rechte\n");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(baseDir)));
    }

    public void stop() {
        stopRequested = true;
        Process p = current;
        if (p != null) p.destroyForcibly();
    }

    /** Schickt eine Zeile an das laufende Programm (System.in). */
    public void sendInput(String line) {
        Writer w = currentIn;
        if (w == null) return;
        try {
            w.write(line + "\n");
            w.flush();
        } catch (IOException ignored) {
        }
    }

    public boolean isRunning() {
        Process p = current;
        return p != null && p.isAlive();
    }

    public Result run(String source, String stdin, Listener listener) {
        return run(source, stdin, listener, false);
    }

    /**
     * Laeuft synchron – aus einem Hintergrund-Thread aufrufen.
     * interactive: System.in bleibt offen, weitere Zeilen kommen ueber sendInput().
     */
    public Result run(String source, String stdin, Listener listener, boolean interactive) {
        stopRequested = false;
        Result r = new Result();
        long t0 = System.currentTimeMillis();
        Path dir = null;
        try {
            dir = Files.createTempDirectory(baseDir, "run");
            Matcher m = PUBLIC_TYPE.matcher(stripComments(source));
            String name = m.find() ? m.group(1) : "Main";
            r.fileName = name + ".java";
            Path src = dir.resolve(r.fileName);
            Files.writeString(src, source, UTF_8);

            if (!compile(src, dir, r)) return r;
            r.compiled = true;

            String mainClass = findMainClass(dir, name);
            if (mainClass == null) {
                r.noMain = true;
                return r;
            }
            execute(dir, mainClass, stdin, listener, r, interactive);
        } catch (Exception e) {
            r.err.append("Interner Fehler: ").append(e).append('\n');
        } finally {
            r.millis = System.currentTimeMillis() - t0;
            current = null;
            if (dir != null) deleteTree(dir);
        }
        return r;
    }

    private boolean compile(Path src, Path dir, Result r) throws IOException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) {
            r.errors.add(new CompileError(0, 0, "no.compiler",
                    "Kein Java-Compiler gefunden. Die App muss mit einem JDK (nicht nur JRE) gestartet werden."));
            return false;
        }
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(dc, null, UTF_8)) {
            Boolean ok = jc.getTask(new StringWriter(), fm, dc,
                    List.of("-g", "-d", dir.toString(), "-encoding", "UTF-8", "-proc:none"),
                    null, fm.getJavaFileObjects(src.toFile())).call();
            for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    r.errors.add(new CompileError(d.getLineNumber(), d.getColumnNumber(), d.getCode(), d.getMessage(null)));
                }
            }
            return Boolean.TRUE.equals(ok) && r.errors.isEmpty();
        }
    }

    private String findMainClass(Path dir, String preferred) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String rel = dir.relativize(p).toString().replace(File.separatorChar, '.');
                names.add(rel.substring(0, rel.length() - ".class".length()));
            });
        }
        names.sort(Comparator.comparing((String n) -> !n.equals(preferred) && !n.endsWith("." + preferred)));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            for (String n : names) {
                try {
                    Class<?> c = Class.forName(n, false, cl);
                    Method main = c.getMethod("main", String[].class);
                    if (Modifier.isStatic(main.getModifiers())) return n;
                } catch (Throwable ignored) {
                    // keine main-Methode
                }
            }
        }
        return null;
    }

    private volatile Writer currentIn;

    /** Ergebnis von prepare(): kompilierter Code fuer den Schritt-Modus. Der Aufrufer loescht dir. */
    public record Prepared(Path dir, String mainClass, Result result) {}

    /** Nur kompilieren und die Hauptklasse finden, ohne das Programm zu starten. */
    public Prepared prepare(String source) {
        Result r = new Result();
        Path dir = null;
        String main = null;
        try {
            dir = Files.createTempDirectory(baseDir, "step");
            Matcher m = PUBLIC_TYPE.matcher(stripComments(source));
            String name = m.find() ? m.group(1) : "Main";
            r.fileName = name + ".java";
            Path src = dir.resolve(r.fileName);
            Files.writeString(src, source, UTF_8);
            if (compile(src, dir, r)) {
                r.compiled = true;
                main = findMainClass(dir, name);
                r.noMain = main == null;
            }
        } catch (Exception e) {
            r.err.append("Interner Fehler: ").append(e).append('\n');
        }
        return new Prepared(dir, main, r);
    }

    /** JVM-Optionen und Klassenpfad fuer einen Sandbox-Prozess. */
    String sandboxOptions(Path dir) {
        return "-Xmx256m -XX:+UseSerialGC -Dfile.encoding=UTF-8"
                + " -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
                + " -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
                + " -Duser.language=de -Duser.country=DE"
                + " -Djava.security.manager=allow -Djava.security.policy=" + policyFile.toUri()
                + " -cp \"" + dir + File.pathSeparator + launcherClasspath() + "\"";
    }

    private static String launcherClasspath() {
        try {
            return Paths.get(SandboxLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception e) {
            return System.getProperty("java.class.path");
        }
    }

    private void execute(Path dir, String mainClass, String stdin, Listener l, Result r, boolean interactive) throws Exception {
        Path javaExe = Paths.get(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.exists(javaExe)) javaExe = Paths.get(System.getProperty("java.home"), "bin", "java");

        List<String> cmd = List.of(
                javaExe.toString(),
                "-Xmx256m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                "-Dfile.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Duser.language=de", "-Duser.country=DE",
                "-Djava.security.manager=allow",
                "-Djava.security.policy=" + policyFile.toUri(),
                "-cp", dir + File.pathSeparator + launcherClasspath(),
                SandboxLauncher.class.getName(), mainClass);

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        Process p = pb.start();
        current = p;
        if (stopRequested) p.destroyForcibly();

        Writer w = new OutputStreamWriter(p.getOutputStream(), UTF_8);
        try {
            String s = stdin == null ? "" : stdin.replace("\r\n", "\n");
            if (!s.isEmpty() && !s.endsWith("\n")) s += "\n";
            w.write(s);
            w.flush();
            if (interactive) currentIn = w;
            else w.close();
        } catch (IOException ignored) {
        }

        Thread out = pumpChars(p.getInputStream(), r.out, l::out, r, p);
        Thread err = pumpLines(p.getErrorStream(), r.err, l::err, r, p);

        // Zeitlimit: Rechenzeit (CPU) statt Wartezeit – wer auf eine Eingabe wartet, verbraucht keine CPU.
        long start = System.currentTimeMillis();
        long maxWall = interactive ? MAX_INTERACTIVE_MINUTES * 60_000L : TIMEOUT_SECONDS * 3000L;
        while (!p.waitFor(200, TimeUnit.MILLISECONDS)) {
            long wall = System.currentTimeMillis() - start;
            long cpu = p.toHandle().info().totalCpuDuration().map(d -> d.toMillis()).orElse(wall);
            if (cpu > TIMEOUT_SECONDS * 1000L || wall > maxWall) {
                if (!stopRequested) r.timedOut = true;
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                break;
            }
        }
        currentIn = null;
        try {
            w.close();
        } catch (IOException ignored) {
        }
        out.join(3000);
        err.join(3000);
        r.stopped = stopRequested;
        try {
            r.exitCode = p.exitValue();
        } catch (IllegalThreadStateException e) {
            r.exitCode = -1;
        }
    }

    private Thread pumpChars(InputStream is, StringBuilder target, Consumer<String> cb, Result r, Process p) {
        Thread t = new Thread(() -> {
            try (Reader rd = new InputStreamReader(is, UTF_8)) {
                char[] buf = new char[4096];
                int n;
                while ((n = rd.read(buf)) > 0) {
                    String s = new String(buf, 0, n);
                    if (!accept(target, s, cb, r, p)) break;
                }
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private Thread pumpLines(InputStream is, StringBuilder target, Consumer<String> cb, Result r, Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, UTF_8))) {
                String line;
                while ((line = rd.readLine()) != null) {
                    // Hinweise zum (absichtlich verwendeten) Security-Manager ausblenden
                    if (line.startsWith("WARNING: A command line option has enabled the Security Manager")
                            || line.startsWith("WARNING: The Security Manager is deprecated")
                            || line.startsWith("WARNING: A terminally deprecated method in java.lang.System")
                            || line.startsWith("WARNING: System::setSecurityManager")
                            || line.startsWith("WARNING: Please consider reporting this to the maintainers")) continue;
                    if (!accept(target, line + "\n", cb, r, p)) break;
                }
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private final Object lock = new Object();

    private boolean accept(StringBuilder target, String s, Consumer<String> cb, Result r, Process p) {
        synchronized (lock) {
            if (r.outputLimit) return false;
            if (r.out.length() + r.err.length() + s.length() > MAX_OUTPUT_CHARS) {
                r.outputLimit = true;
                p.destroyForcibly();
                return false;
            }
            target.append(s);
        }
        cb.accept(s);
        return true;
    }

    static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
