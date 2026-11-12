package lernapp;

import lernapp.Content.Lesson;

/** Prueft alle Musterloesungen und Beispiele: java -jar JavaLernApp.jar --selftest */
final class SelfTest {

    static void run() throws Exception {
        CodeRunner runner = new CodeRunner();
        int fail = 0;
        for (Lesson l : Content.lessons()) {
            CodeRunner.Result start = runner.run(l.code(), l.eingabe(), new CodeRunner.Listener() {});
            String startInfo = start.compiled ? "start ok" : "start kompiliert NICHT (" + start.errors.size() + " Fehler)";
            if (l.loesung() == null) {
                System.out.println("[----] " + l.id() + "  (" + startInfo + ")");
            } else {
                CodeRunner.Result r = runner.run(l.loesung(), l.eingabe(), new CodeRunner.Listener() {});
                String problem;
                if (!r.compiled) problem = "kompiliert nicht: " + r.errors;
                else if (r.exitCode != 0) problem = "exit " + r.exitCode + " " + r.err;
                else if (l.erwartet() == null) problem = null;
                else problem = JavaLernApp.checkResult(l, r.out.toString(), l.loesung());
                if (problem != null) fail++;
                if (l.erwartet() == null && l.eingabe() != null) System.out.println(r.out);
                System.out.println((problem == null ? "[ OK ] " : "[FAIL] ") + l.id() + "  (" + startInfo + ")"
                        + (problem == null ? "" : "\n       " + problem));
            }
            Markdown md = new Markdown();
            md.toHtml(l.erklaerung());
            md.toHtml(l.aufgabe());
            for (int i = 0; i < md.snippets.size(); i++) {
                String s = md.snippets.get(i);
                String code = JavaLernApp.wrap(s);
                CodeRunner.Result r = runner.run(code, "", new CodeRunner.Listener() {});
                String st = !r.compiled ? "KOMPILIERT NICHT " + r.errors : r.exitCode != 0 ? "exit " + r.exitCode + " (" + firstLine(r.err.toString()) + ")" : "ok";
                System.out.println("         snippet " + i + ": " + st);
            }
        }
        for (QuizPanel.Frage q : QuizPanel.laden()) {
            CodeRunner.Result r = runner.run(JavaLernApp.wrap(q.code()), "", new CodeRunner.Listener() {});
            String ist;
            if (!r.compiled) ist = "Kompilierfehler";
            else if (r.exitCode != 0) {
                String[] ex = Explanations.runtime(r.err.toString());
                ist = "Exception: " + (ex == null ? "?" : ex[0]);
            } else ist = JavaLernApp.normalize(r.out.toString()).replace('\n', ' ');
            String soll = q.optionen().get(q.richtig());
            boolean ok = ist.equals(soll);
            if (!ok) fail++;
            System.out.println((ok ? "[ OK ] " : "[FAIL] ") + "Quiz " + q.id() + (ok ? "" : "  soll »" + soll + "«  ist »" + ist + "«"));
        }
        for (Lesson l : Content.lessons()) {
            String problem = null;
            if (l.notizen().isEmpty()) problem = "keine Handnotizen";
            else if (l.aufgabe() != null && l.tipps().isEmpty()) problem = "keine Tipps";
            else if (l.aufgabe() != null && l.loesung() == null && l.erwartet() != null) problem = "keine Lösung";
            if (problem != null) {
                fail++;
                System.out.println("[FAIL] Inhalt " + l.id() + ": " + problem);
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Lesson l : Content.lessons()) {
            if (!ids.add(l.id())) {
                fail++;
                System.out.println("[FAIL] doppelte id " + l.id());
            }
        }
        fail += schrittTest(runner);
        System.out.println(Content.lessons().size() + " Lektionen, " + Content.questions().size() + " Karten, "
                + QuizPanel.laden().size() + " Quizfragen");
        System.out.println(fail == 0 ? "ALLES OK" : fail + " FEHLER");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Faehrt ein kleines Programm im Schritt-Modus ab und prueft Zeilen und Variablen. */
    private static int schrittTest(CodeRunner runner) throws Exception {
        String code = """
                import java.util.*;

                public class Main {
                    static int doppelt(int x) {
                        return x * 2;
                    }

                    public static void main(String[] args) {
                        int a = 21;
                        List<String> l = new ArrayList<>();
                        l.add("x");
                        int b = doppelt(a);
                        System.out.println(b);
                    }
                }
                """;
        StringBuilder out = new StringBuilder();
        java.util.List<Integer> zeilen = new java.util.ArrayList<>();
        java.util.List<String> werte = new java.util.ArrayList<>();
        java.util.concurrent.CountDownLatch ende = new java.util.concurrent.CountDownLatch(1);
        StepDebugger[] d = new StepDebugger[1];
        d[0] = new StepDebugger(new StepDebugger.Listener() {
            public void stopped(int line, java.util.List<String> stack, java.util.List<StepDebugger.Variable> vars) {
                zeilen.add(line);
                for (StepDebugger.Variable v : vars) werte.add(v.name() + "=" + v.value());
                d[0].stepInto();
            }
            public void out(String t) { synchronized (out) { out.append(t); } }
            public void err(String t) { System.out.println("step err: " + t); }
            public void finished(int exit, boolean stopped) { ende.countDown(); }
        });
        CodeRunner.Prepared p = runner.prepare(code);
        Thread t = new Thread(() -> d[0].run(runner, p));
        t.setDaemon(true);
        t.start();
        boolean fertig = ende.await(30, java.util.concurrent.TimeUnit.SECONDS);
        Thread.sleep(300);
        boolean ok = fertig && zeilen.contains(9) && zeilen.contains(5) && zeilen.contains(13)
                && werte.contains("a=21") && werte.contains("x=21") && werte.contains("l=[x]")
                && out.toString().contains("42");
        System.out.println((ok ? "[ OK ] " : "[FAIL] ") + "Schritt-Modus  Zeilen=" + zeilen + (ok ? "" : " Werte=" + werte + " out=" + out));
        return ok ? 0 : 1;
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
