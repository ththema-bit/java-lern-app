package lernapp;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Startet das Benutzerprogramm im Sandbox-Prozess:
 * - ungepufferte Ausgabe (damit Eingabe-Aufforderungen wie "Betrag: " sofort erscheinen)
 * - danach Security-Manager ohne Rechte aktivieren
 */
public final class SandboxLauncher {

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8");
        System.setOut(out);
        System.setErr(err);

        Class<?> c = Class.forName(args[0], false, ClassLoader.getSystemClassLoader());
        Method main = c.getMethod("main", String[].class);
        System.setSecurityManager(new SecurityManager());
        try {
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            clean(t);
            err.print("Exception in thread \"main\" ");
            t.printStackTrace(err);
            err.flush();
            System.exit(1);
        }
    }

    /** Interne Aufruf-Zeilen aus dem Stack-Trace entfernen. */
    private static void clean(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            List<StackTraceElement> keep = new ArrayList<>();
            for (StackTraceElement el : x.getStackTrace()) {
                String cn = el.getClassName();
                if (cn.startsWith("jdk.internal.reflect") || cn.startsWith("java.lang.reflect")
                        || cn.equals(SandboxLauncher.class.getName())) continue;
                keep.add(el);
            }
            x.setStackTrace(keep.toArray(new StackTraceElement[0]));
            if (x.getCause() == x) break;
        }
    }
}
