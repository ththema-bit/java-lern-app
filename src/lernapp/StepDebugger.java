package lernapp;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.StepRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Schritt-fuer-Schritt-Modus: fuehrt das Programm Zeile fuer Zeile im Sandbox-Prozess aus
 * und liest nach jedem Schritt die Variablen aus (ueber das JDK-Debugger-Interface JDI).
 */
final class StepDebugger {

    record Variable(String name, String type, String value) {}

    interface Listener {
        /** Das Programm steht VOR der Ausfuehrung dieser Zeile. */
        void stopped(int line, List<String> stack, List<Variable> variables);
        void out(String text);
        void err(String text);
        void finished(int exitCode, boolean stoppedByUser);
    }

    private enum Command { INTO, OVER, RUN, STOP }

    private static final String[] EXCLUDES = {"java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*", "lernapp.*"};
    private static final long CPU_LIMIT_MS = CodeRunner.TIMEOUT_SECONDS * 1000L;

    private final BlockingQueue<Command> commands = new LinkedBlockingQueue<>();
    private volatile VirtualMachine vm;
    private volatile Process process;
    private volatile Writer stdin;
    private volatile boolean stoppedByUser;
    private final Listener listener;

    StepDebugger(Listener listener) {
        this.listener = listener;
    }

    /** Laeuft synchron – aus einem Hintergrund-Thread aufrufen. */
    void run(CodeRunner runner, CodeRunner.Prepared prepared) {
        int exit = -1;
        try {
            launch(runner, prepared.dir(), prepared.mainClass());
            pump();
            eventLoop(prepared.mainClass());
            if (process != null) {
                process.waitFor();
                exit = process.exitValue();
            }
        } catch (VMDisconnectedException e) {
            exit = process != null && !process.isAlive() ? process.exitValue() : -1;
        } catch (Exception | LinkageError e) {
            listener.err("Der Schritt-Modus konnte nicht starten: " + e + "\n");
        } finally {
            kill();
            CodeRunner.deleteTree(prepared.dir());
            listener.finished(exit, stoppedByUser);
        }
    }

    void stepInto() { send(Command.INTO); }
    void stepOver() { send(Command.OVER); }
    void runToEnd() { send(Command.RUN); }

    void stop() {
        stoppedByUser = true;
        commands.offer(Command.STOP);
        kill();
    }

    void sendInput(String line) {
        Writer w = stdin;
        if (w == null) return;
        try {
            w.write(line + "\n");
            w.flush();
        } catch (IOException ignored) {
        }
    }

    private void send(Command c) {
        commands.offer(c);
    }

    private void kill() {
        Process p = process;
        if (p != null) p.destroyForcibly();
    }

    private void launch(CodeRunner runner, Path dir, String mainClass) throws Exception {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("main").setValue(SandboxLauncher.class.getName() + " " + mainClass);
        args.get("options").setValue(runner.sandboxOptions(dir));
        vm = connector.launch(args);
        process = vm.process();
        stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);

        Thread guard = new Thread(() -> {
            try {
                Process p = process;
                while (p.isAlive()) {
                    long cpu = p.toHandle().info().totalCpuDuration().map(d -> d.toMillis()).orElse(0L);
                    if (cpu > CPU_LIMIT_MS) {
                        listener.err("\nAbgebrochen: mehr als " + CodeRunner.TIMEOUT_SECONDS + " Sekunden Rechenzeit.\n");
                        p.destroyForcibly();
                        return;
                    }
                    Thread.sleep(300);
                }
            } catch (InterruptedException ignored) {
            }
        }, "step-guard");
        guard.setDaemon(true);
        guard.start();
    }

    private void pump() {
        Thread out = new Thread(() -> {
            try (Reader r = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) listener.out(new String(buf, 0, n));
            } catch (IOException ignored) {
            }
        }, "step-out");
        Thread err = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("WARNING: ")) continue;
                    listener.err(line + "\n");
                }
            } catch (IOException ignored) {
            }
        }, "step-err");
        out.setDaemon(true);
        err.setDaemon(true);
        out.start();
        err.start();
    }

    private void eventLoop(String mainClass) throws Exception {
        EventRequestManager erm = vm.eventRequestManager();
        MethodEntryRequest entry = erm.createMethodEntryRequest();
        entry.addClassFilter(mainClass);
        entry.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        entry.enable();

        EventQueue queue = vm.eventQueue();
        while (true) {
            EventSet set = queue.remove();
            boolean resume = true;
            for (Event e : set) {
                if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) return;
                if (e instanceof MethodEntryEvent me && me.method().name().equals("main")) {
                    erm.deleteEventRequest(entry);
                    if (!handleStop(me.thread(), me.location())) return;
                } else if (e instanceof StepEvent se) {
                    Location loc = se.location();
                    if (loc.lineNumber() <= 0 || loc.method().isBridge()) continue;
                    if (!handleStop(se.thread(), loc)) return;
                    resume = true;
                }
            }
            if (resume) set.resume();
        }
    }

    /** @return false wenn beendet werden soll */
    private boolean handleStop(ThreadReference thread, Location loc) throws Exception {
        listener.stopped(loc.lineNumber(), stack(thread), variables(thread));
        Command c = commands.take();
        EventRequestManager erm = vm.eventRequestManager();
        erm.deleteEventRequests(new ArrayList<>(erm.stepRequests()));
        switch (c) {
            case INTO -> newStep(thread, StepRequest.STEP_INTO);
            case OVER -> newStep(thread, StepRequest.STEP_OVER);
            case RUN -> { }
            case STOP -> { return false; }
        }
        return true;
    }

    private void newStep(ThreadReference thread, int depth) {
        StepRequest s = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, depth);
        for (String x : EXCLUDES) s.addClassExclusionFilter(x);
        s.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        s.enable();
    }

    private static boolean isUser(StackFrame f) {
        String n = f.location().declaringType().name();
        for (String x : EXCLUDES) if (n.startsWith(x.substring(0, x.length() - 1))) return false;
        return true;
    }

    private List<String> stack(ThreadReference thread) throws Exception {
        List<String> res = new ArrayList<>();
        for (StackFrame f : thread.frames()) {
            if (!isUser(f)) continue;
            String method = f.location().method().name();
            if (method.equals("<init>")) method = "new";
            res.add(f.location().declaringType().name() + "." + method);
        }
        return res;
    }

    private List<Variable> variables(ThreadReference thread) throws Exception {
        StackFrame frame = thread.frame(0);
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        try {
            for (LocalVariable v : frame.visibleVariables()) {
                names.add(v.name());
                types.add(v.typeName());
                values.add(frame.getValue(v));
            }
        } catch (AbsentInformationException ignored) {
        }
        ObjectReference self = frame.thisObject();
        if (self != null) {
            for (Field f : self.referenceType().allFields()) {
                if (f.isStatic()) continue;
                names.add("this." + f.name());
                types.add(f.typeName());
                values.add(self.getValue(f));
            }
        }
        for (Field f : frame.location().declaringType().allFields()) {
            if (!f.isStatic() || f.isSynthetic() || f.isFinal()) continue;
            names.add(f.name());
            types.add("static " + f.typeName());
            values.add(frame.location().declaringType().getValue(f));
        }
        List<Variable> res = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            res.add(new Variable(names.get(i), shortType(types.get(i)), format(values.get(i), thread, 0)));
        }
        return res;
    }

    private static String shortType(String t) {
        return t.replace("java.lang.", "").replace("java.util.", "");
    }

    private String format(Value v, ThreadReference thread, int depth) {
        if (v == null) return "null";
        if (v instanceof StringReference s) return "\"" + s.value() + "\"";
        if (v instanceof CharValue c) return "'" + c.value() + "'";
        if (v instanceof PrimitiveValue) return v.toString();
        if (v instanceof ArrayReference a) {
            StringBuilder b = new StringBuilder("[");
            int n = Math.min(a.length(), 20);
            for (int i = 0; i < n; i++) {
                if (i > 0) b.append(", ");
                b.append(format(a.getValue(i), thread, depth + 1));
            }
            if (a.length() > n) b.append(", ... (").append(a.length()).append(" Elemente)");
            return b.append("]").toString();
        }
        if (v instanceof ObjectReference o) {
            String type = o.referenceType().name();
            if (type.startsWith("java.")) return safeToString(o, thread);
            if (depth >= 1) return type + " {...}";
            StringBuilder b = new StringBuilder(type).append(" {");
            boolean first = true;
            for (Field f : o.referenceType().allFields()) {
                if (f.isStatic()) continue;
                if (!first) b.append(", ");
                first = false;
                b.append(f.name()).append("=").append(format(o.getValue(f), thread, depth + 1));
            }
            return b.append("}").toString();
        }
        return v.toString();
    }

    /** toString() nur fuer JDK-Objekte (Listen, Maps ...), damit kein fremder Code nebenbei laeuft. */
    private String safeToString(ObjectReference o, ThreadReference thread) {
        try {
            Method m = o.referenceType().methodsByName("toString", "()Ljava/lang/String;").get(0);
            Value r = o.invokeMethod(thread, m, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
            String s = r == null ? "null" : ((StringReference) r).value();
            return s.length() > 300 ? s.substring(0, 300) + " ..." : s;
        } catch (Exception e) {
            return o.referenceType().name();
        }
    }
}
