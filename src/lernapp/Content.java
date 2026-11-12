package lernapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Laedt Lektionen und Interview-Fragen aus den Textdateien und speichert den Fortschritt. */
final class Content {

    record Lesson(String id, String kapitel, String titel, String erklaerung, String aufgabe,
                  String code, String loesung, String erwartet, List<String> muss, String eingabe,
                  List<String> tipps, List<String> notizen) {

        /** Aufgabe ohne automatische Pruefung: wird von Hand als erledigt markiert. */
        boolean freieAufgabe() {
            return aufgabe != null && erwartet == null;
        }
    }

    private static final List<String> LEKTIONS_DATEIEN = List.of("/lektionen.txt", "/projekt_bank.txt",
            "/projekt_bank_app.txt", "/fehlersuche.txt", "/wissen.txt", "/workout.txt", "/projekte.txt", "/werkzeuge.txt");

    static List<Lesson> lessons() {
        Map<String, Map<String, String>> extras = new HashMap<>();
        for (Map<String, String> e : parseBlocks(resource("/extras.txt"))) extras.put(e.get("id"), e);

        List<Lesson> list = new ArrayList<>();
        List<Map<String, String>> blocks = new ArrayList<>();
        for (String file : LEKTIONS_DATEIEN) blocks.addAll(parseBlocks(resource(file)));
        for (Map<String, String> b : blocks) {
            Map<String, String> x = extras.getOrDefault(b.get("id"), Map.of());
            String gemeinsam = b.getOrDefault("gemeinsam", "");
            String code = b.getOrDefault("code", "").replace("//@@", gemeinsam);
            String loesung = b.get("loesung");
            if (loesung != null) loesung = loesung.replace("//@@", gemeinsam);
            list.add(new Lesson(b.get("id"), b.getOrDefault("kapitel", ""), b.getOrDefault("titel", "?"),
                    b.getOrDefault("erklaerung", ""), b.get("aufgabe"), code, loesung, b.get("erwartet"),
                    lines(b.get("muss")), b.get("eingabe"),
                    lines(b.getOrDefault("tipps", x.get("tipps"))),
                    lines(b.getOrDefault("notizen", x.get("notizen")))));
        }
        return list;
    }

    private static List<String> lines(String s) {
        List<String> res = new ArrayList<>();
        if (s != null) for (String l : s.split("\n")) if (!l.isBlank()) res.add(l.trim());
        return res;
    }

    static List<Map<String, String>> questions() {
        List<Map<String, String>> res = new ArrayList<>(parseBlocks(resource("/fragen.txt")));
        res.addAll(parseBlocks(resource("/fragen2.txt")));
        return res;
    }

    static String resource(String name) {
        try (InputStream in = Content.class.getResourceAsStream(name)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Format:
     * <pre>
     * === NAME
     * schluessel: wert
     * --- abschnitt
     * mehrzeiliger Inhalt
     * </pre>
     */
    static List<Map<String, String>> parseBlocks(String text) {
        List<Map<String, String>> res = new ArrayList<>();
        Map<String, String> cur = null;
        String section = null;
        StringBuilder buf = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            if (line.matches("=== [A-Z]+\\s*")) {
                if (cur != null && section != null) cur.put(section, clean(buf));
                cur = new LinkedHashMap<>();
                res.add(cur);
                section = null;
                continue;
            }
            if (cur == null) continue;
            if (line.matches("--- [a-z]+\\s*")) {
                if (section != null) cur.put(section, clean(buf));
                section = line.substring(4).trim();
                buf = new StringBuilder();
                continue;
            }
            if (section == null) {
                int c = line.indexOf(':');
                if (c > 0) cur.put(line.substring(0, c).trim(), line.substring(c + 1).trim());
                continue;
            }
            buf.append(line).append('\n');
        }
        if (cur != null && section != null) cur.put(section, clean(buf));
        return res;
    }

    private static String clean(StringBuilder sb) {
        String s = sb.toString();
        while (s.startsWith("\n")) s = s.substring(1);
        return s.stripTrailing();
    }

    /**
     * Fortschritt + eigener Code pro Lektion in %USERPROFILE%\.javalernapp.
     * Jede Aenderung wird sofort und atomar geschrieben (erst in eine Hilfsdatei, dann umbenannt),
     * es gibt eine Sicherungskopie, und es darf immer nur ein Fenster der App laufen.
     */
    static final class Store {
        /** Im Testmodus wird nichts gespeichert (Fortschritt und eigener Code bleiben unberuehrt). */
        static volatile boolean readOnly = false;

        static final Path DIR = Paths.get(System.getProperty("user.home"), ".javalernapp");
        private static java.nio.channels.FileChannel sperrKanal;
        private static java.nio.channels.FileLock sperre;

        private final Path dir = DIR;
        private final Path file = dir.resolve("fortschritt.properties");
        private final Path backup = dir.resolve("fortschritt.sicherung.properties");
        private final Properties p = new Properties();
        private final Map<String, String> letzterCode = new HashMap<>();
        /** Wird einmal aufgerufen, wenn das Speichern scheitert. */
        java.util.function.Consumer<String> beiFehler = m -> { };
        private boolean fehlerGemeldet;

        Store() {
            try {
                Files.createDirectories(dir.resolve("code"));
            } catch (IOException ignored) {
            }
            boolean ok = lade(file);
            if (!ok) lade(backup);
            // Sicherung des zuletzt gueltigen Stands bei jedem Start
            if (!readOnly && ok) {
                try {
                    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }

        /** @return true, wenn die Datei existiert, gelesen werden konnte und nicht leer ist */
        private boolean lade(Path f) {
            if (!Files.exists(f)) return false;
            Properties neu = new Properties();
            try (Reader r = Files.newBufferedReader(f, UTF_8)) {
                neu.load(r);
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
            if (neu.isEmpty()) return false;
            p.clear();
            p.putAll(neu);
            return true;
        }

        /**
         * Sorgt dafuer, dass nur ein Fenster der App laeuft. Zwei Fenster wuerden sich sonst
         * gegenseitig den Fortschritt ueberschreiben.
         * @return false, wenn die App schon offen ist
         */
        static synchronized boolean einzigeInstanz() {
            if (sperre != null) return true;
            try {
                Files.createDirectories(DIR);
                sperrKanal = java.nio.channels.FileChannel.open(DIR.resolve("app.sperre"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                sperre = sperrKanal.tryLock();
                return sperre != null;
            } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
                return true;   // im Zweifel starten lassen
            }
        }

        Set<String> set(String key) {
            Set<String> s = new LinkedHashSet<>();
            for (String v : p.getProperty(key, "").split(",")) if (!v.isBlank()) s.add(v.trim());
            return s;
        }

        void add(String key, String value) {
            Set<String> s = set(key);
            s.add(value);
            p.setProperty(key, String.join(",", s));
            save();
        }

        void remove(String key, String value) {
            Set<String> s = set(key);
            s.remove(value);
            p.setProperty(key, String.join(",", s));
            save();
        }

        /** Merkt sich den heutigen Tag als Lerntag (fuer die Serie auf der Startseite). */
        void markActive() {
            String today = String.valueOf(java.time.LocalDate.now().toEpochDay());
            if (!set("tage").contains(today)) add("tage", today);
        }

        /** Anzahl aufeinanderfolgender Lerntage bis heute (oder gestern). */
        int streak() {
            Set<String> tage = set("tage");
            long d = java.time.LocalDate.now().toEpochDay();
            if (!tage.contains(String.valueOf(d))) d--;
            int n = 0;
            while (tage.contains(String.valueOf(d))) {
                n++;
                d--;
            }
            return n;
        }

        String get(String key) { return p.getProperty(key); }

        void put(String key, String value) {
            p.setProperty(key, value);
            save();
        }

        synchronized void save() {
            if (readOnly) return;
            try {
                schreibeAtomar(file, alsText());
            } catch (IOException e) {
                fehler(e);
            }
        }

        private String alsText() throws IOException {
            StringWriter w = new StringWriter();
            p.store(w, "Java Lern-Sandbox Fortschritt");
            return w.toString();
        }

        private static void schreibeAtomar(Path ziel, String inhalt) throws IOException {
            Path tmp = ziel.resolveSibling(ziel.getFileName() + ".tmp");
            Files.writeString(tmp, inhalt, UTF_8);
            try {
                Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private void fehler(Exception e) {
            if (fehlerGemeldet) return;
            fehlerGemeldet = true;
            beiFehler.accept("Dein Fortschritt konnte nicht gespeichert werden (" + e.getMessage() + ").\n"
                    + "Ordner: " + dir);
        }

        String loadCode(String id) {
            Path f = dir.resolve("code").resolve(id + ".java");
            try {
                return Files.exists(f) ? Files.readString(f, UTF_8) : null;
            } catch (IOException e) {
                return null;
            }
        }

        /** Speichert nur, wenn sich der Code seit dem letzten Speichern geaendert hat. */
        synchronized void saveCode(String id, String code, String original) {
            if (readOnly) return;
            if (code.equals(letzterCode.get(id))) return;
            Path f = dir.resolve("code").resolve(id + ".java");
            try {
                if (code.strip().equals(original.strip())) Files.deleteIfExists(f);
                else schreibeAtomar(f, code);
                letzterCode.put(id, code);
            } catch (IOException e) {
                fehler(e);
            }
        }

        // ---------------------------------------------------------------- Mitnehmen auf einen anderen PC

        /** Packt Fortschritt und eigenen Code in eine ZIP-Datei. */
        synchronized void exportiere(Path zip) throws IOException {
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new java.util.zip.ZipEntry("fortschritt.properties"));
                out.write(alsText().getBytes(UTF_8));
                out.closeEntry();
                if (Files.isDirectory(dir.resolve("code"))) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir.resolve("code"), "*.java")) {
                        for (Path f : ds) {
                            out.putNextEntry(new java.util.zip.ZipEntry("code/" + f.getFileName()));
                            out.write(Files.readAllBytes(f));
                            out.closeEntry();
                        }
                    }
                }
            }
        }

        /**
         * Ersetzt Fortschritt und eigenen Code durch den Inhalt einer exportierten ZIP-Datei.
         * Vorher wird der bisherige Stand als Sicherung im Lernstand-Ordner abgelegt.
         */
        synchronized void importiere(Path zip) throws IOException {
            Properties neu = null;
            Map<String, byte[]> code = new LinkedHashMap<>();
            try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
                java.util.zip.ZipEntry e;
                while ((e = in.getNextEntry()) != null) {
                    String name = e.getName();
                    byte[] daten = in.readAllBytes();
                    if (name.equals("fortschritt.properties")) {
                        neu = new Properties();
                        neu.load(new StringReader(new String(daten, UTF_8)));
                    } else if (name.matches("code/[A-Za-z0-9_]+\\.java")) {
                        code.put(name.substring(5), daten);
                    }
                }
            }
            if (neu == null) throw new IOException("Das ist keine Lernstand-Datei dieser App.");
            if (!readOnly) exportiere(dir.resolve("vor-import-" + System.currentTimeMillis() + ".zip"));
            p.clear();
            p.putAll(neu);
            letzterCode.clear();
            if (readOnly) return;
            save();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir.resolve("code"), "*.java")) {
                for (Path f : ds) Files.delete(f);
            }
            for (Map.Entry<String, byte[]> c : code.entrySet()) {
                schreibeAtomar(dir.resolve("code").resolve(c.getKey()), new String(c.getValue(), UTF_8));
            }
        }
    }
}
