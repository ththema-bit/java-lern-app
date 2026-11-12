package lernapp;

import lernapp.Content.Lesson;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

/** Startseite: Countdown zum Interview, Tagesplan, Fortschritt und Handschrift-Tipp. */
class DashboardPanel extends JPanel {

    interface Navigation {
        void weiterLernen();
        void zeigeTab(int index);
        void vorExport();
        void nachImport();
    }

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.uuuu");

    private static final String[] HANDSCHRIFT_TIPPS = {
            "Blurting: Schließ die Erklärung und schreib alles auf, was du noch weißt. Vergleiche danach und ergänze mit einer anderen Farbe.",
            "Fasse in eigenen Worten zusammen. Abschreiben bringt wenig, dein Kopf muss die Sätze selbst bilden.",
            "Schreib Code auf Papier, ohne Autovervollständigung. So merkst du sofort, welche Syntax noch nicht sitzt.",
            "Zeichne statt nur zu schreiben: Stack und Heap, Buckets einer HashMap oder den Aufrufstapel einer Rekursion.",
            "Spiele Schleifen auf Papier mit einer Variablen-Tabelle durch: eine Spalte pro Variable, eine Zeile pro Durchlauf.",
            "Lies morgen zuerst deine eigene Notiz und erkläre sie laut. Wiederholen mit Abstand festigt das Wissen.",
            "Eine Seite pro Thema reicht. Am Tag vor dem Interview liest du nur noch deine eigenen Blätter."
    };

    private final List<Lesson> lessons;
    private final List<Map<String, String>> questions;
    private final int quizAnzahl;
    private final Content.Store store;
    private final Navigation nav;

    private final JLabel titel = new JLabel();
    private final JLabel untertitel = new JLabel();
    private final JTextField datumFeld = new JTextField(10);
    private final JPanel kacheln = new JPanel(new GridLayout(1, 5, 10, 10));
    private final JPanel plan = new JPanel();
    private final JPanel kapitel = new JPanel(new GridBagLayout()) {
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    };
    private final JTextArea tipp = new JTextArea();

    DashboardPanel(List<Lesson> lessons, List<Map<String, String>> questions, int quizAnzahl,
                   Content.Store store, Navigation nav) {
        super(new BorderLayout());
        this.lessons = lessons;
        this.questions = questions;
        this.quizAnzahl = quizAnzahl;
        this.store = store;
        this.nav = nav;

        JPanel inhalt = new FensterBreitePanel();
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        titel.setFont(Theme.schrift("Segoe UI", Font.BOLD, 26));
        titel.setForeground(Theme.UEBERSCHRIFT);
        untertitel.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 15));
        JPanel datum = zeile();
        JLabel datumText = new JLabel("Dein Interview-Termin (TT.MM.JJJJ):");
        datumText.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 14));
        datum.add(datumText);
        datum.add(datumFeld);
        JButton speichern = new JButton("Speichern");
        speichern.addActionListener(e -> datumSpeichern());
        datumFeld.addActionListener(e -> datumSpeichern());
        datum.add(speichern);

        inhalt.add(links(titel));
        inhalt.add(links(untertitel));
        inhalt.add(Box.createVerticalStrut(6));
        inhalt.add(datum);
        inhalt.add(Box.createVerticalStrut(12));
        kacheln.setAlignmentX(LEFT_ALIGNMENT);
        kacheln.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        inhalt.add(kacheln);
        inhalt.add(Box.createVerticalStrut(16));

        tipp.setEditable(false);
        tipp.setLineWrap(true);
        tipp.setWrapStyleWord(true);
        tipp.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 15));
        tipp.setBackground(Theme.BOX_GELB);
        tipp.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        tipp.setAlignmentX(LEFT_ALIGNMENT);
        tipp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        inhalt.add(ueberschrift("Stift und Papier: der beste Merk-Trick"));
        inhalt.add(tipp);
        inhalt.add(Box.createVerticalStrut(16));

        inhalt.add(ueberschrift("Heute dran"));
        plan.setLayout(new BoxLayout(plan, BoxLayout.Y_AXIS));
        plan.setAlignmentX(LEFT_ALIGNMENT);
        inhalt.add(plan);
        inhalt.add(Box.createVerticalStrut(16));

        inhalt.add(ueberschrift("Fortschritt nach Kapitel"));
        kapitel.setAlignmentX(LEFT_ALIGNMENT);
        inhalt.add(kapitel);
        inhalt.add(Box.createVerticalStrut(20));

        inhalt.add(ueberschrift("Dein Lernstand"));
        JLabel ort = new JLabel("<html>Wird bei jeder Änderung automatisch gespeichert in "
                + Markdown.esc(Content.Store.DIR.toString())
                + ". Zum Mitnehmen auf einen anderen PC: hier sichern, dort laden.</html>");
        ort.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 14));
        ort.setForeground(Theme.GEDAEMPFT);
        ort.setAlignmentX(LEFT_ALIGNMENT);
        inhalt.add(ort);
        JPanel knoepfe = zeile();
        JButton sichern = new JButton("Lernstand sichern …");
        JButton laden = new JButton("Lernstand laden …");
        sichern.addActionListener(e -> lernstandSichern());
        laden.addActionListener(e -> lernstandLaden());
        knoepfe.add(sichern);
        knoepfe.add(laden);
        inhalt.add(Box.createVerticalStrut(6));
        inhalt.add(knoepfe);

        JScrollPane sp = new JScrollPane(inhalt, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.FENSTER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);
        refresh();
    }

    /** Passt sich immer der Fensterbreite an, damit nichts seitlich abgeschnitten wird. */
    private static final class FensterBreitePanel extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JPanel zeile() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return p;
    }

    private static JComponent links(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel ueberschrift(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.schrift("Segoe UI", Font.BOLD, 17));
        l.setForeground(Theme.UEBERSCHRIFT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private void lernstandSichern() {
        nav.vorExport();
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lernstand sichern");
        fc.setSelectedFile(new java.io.File(fc.getFileSystemView().getDefaultDirectory(),
                "Lernstand-" + LocalDate.now() + ".zip"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path ziel = fc.getSelectedFile().toPath();
        if (!ziel.toString().toLowerCase().endsWith(".zip")) ziel = ziel.resolveSibling(ziel.getFileName() + ".zip");
        try {
            store.exportiere(ziel);
            JOptionPane.showMessageDialog(this, "Gesichert in:\n" + ziel
                    + "\n\nAuf dem anderen PC: Start → „Lernstand laden …“ und diese Datei wählen.");
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Sichern fehlgeschlagen: " + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void lernstandLaden() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lernstand laden");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Lernstand (*.zip)", "zip"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Dein jetziger Lernstand auf diesem PC wird durch die Datei ersetzt.\n"
                        + "Eine Sicherung des jetzigen Stands landet vorher im Lernstand-Ordner.\n\nFortfahren?",
                "Lernstand laden", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            store.importiere(fc.getSelectedFile().toPath());
            nav.nachImport();
            JOptionPane.showMessageDialog(this, "Lernstand geladen.");
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Laden fehlgeschlagen: " + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** @return Tag des Interviews als Epoch-Day oder null, wenn keiner eingetragen ist */
    static Long interviewTag(Content.Store store) {
        String s = store.get("interview");
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void datumSpeichern() {
        String text = datumFeld.getText().trim();
        if (text.isEmpty()) {
            store.put("interview", "");
            refresh();
            return;
        }
        try {
            store.put("interview", String.valueOf(LocalDate.parse(text, DATUM).toEpochDay()));
            refresh();
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "Bitte im Format TT.MM.JJJJ eingeben, z. B. 24.11.2026.");
        }
    }

    void refresh() {
        Long tag = interviewTag(store);
        Long tage = tag == null ? null : tag - LocalDate.now().toEpochDay();
        if (tage == null) {
            titel.setText(store.set("tage").isEmpty() ? "Willkommen!" : "Willkommen zurück!");
            untertitel.setText("Trag deinen Interview-Termin ein, dann passt sich dein Tagesplan an.");
            datumFeld.setText("");
        } else {
            titel.setText(tage > 1 ? "Noch " + tage + " Tage bis zu deinem Interview"
                    : tage == 1 ? "Morgen ist dein Interview"
                    : tage == 0 ? "Heute ist dein Interview. Viel Erfolg!"
                    : "Dein Interview-Termin ist vorbei");
            untertitel.setText(tage < 0 ? "Trag einen neuen Termin ein oder lerne einfach weiter."
                    : "Kleine Einheiten jeden Tag bringen mehr als eine lange Nacht.");
            datumFeld.setText(LocalDate.ofEpochDay(tag).format(DATUM));
        }

        Set<String> geloest = store.set("geloest");
        Set<String> notiert = store.set("notiert");
        long aufgaben = lessons.stream().filter(l -> l.aufgabe() != null).count();
        long fertig = lessons.stream().filter(l -> l.aufgabe() != null && geloest.contains(l.id())).count();
        long mitNotizen = lessons.stream().filter(l -> !l.notizen().isEmpty()).count();
        long notizen = lessons.stream().filter(l -> !l.notizen().isEmpty() && notiert.contains(l.id())).count();
        long gewusst = questions.stream().filter(q -> InterviewPanel.box(store, q.get("frage")) >= 1).count();
        long faellig = questions.stream().filter(q -> InterviewPanel.istFaellig(store, q.get("frage"))).count();
        int quizOk = store.set("quiz_ok").size();

        kacheln.removeAll();
        kacheln.add(kachel(fertig + " / " + aufgaben, "Aufgaben gelöst"));
        kacheln.add(kachel(notizen + " / " + mitNotizen, "Handnotizen"));
        kacheln.add(kachel(gewusst + " / " + questions.size(), "Karten gewusst"));
        kacheln.add(kachel(quizOk + " / " + quizAnzahl, "Quiz richtig"));
        int serie = store.streak();
        kacheln.add(kachel(serie + (serie == 1 ? " Tag" : " Tage"), "Lernserie"));

        int t = (int) (LocalDate.now().toEpochDay() % HANDSCHRIFT_TIPPS.length);
        tipp.setText(HANDSCHRIFT_TIPPS[t]);

        baueplan(tage, faellig, notizen, mitNotizen);
        baueKapitel(geloest);
        revalidate();
        repaint();
    }

    private JComponent kachel(String wert, String text) {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setBackground(Theme.ERHOEHT);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel w = new JLabel(wert);
        w.setFont(Theme.schrift("Segoe UI", Font.BOLD, 22));
        w.setForeground(Theme.UEBERSCHRIFT);
        JLabel b = new JLabel(text);
        b.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 13));
        p.add(w);
        p.add(b);
        return p;
    }

    private void baueplan(Long tage, long faellig, long notizen, long mitNotizen) {
        plan.removeAll();
        Lesson naechste = lessons.stream()
                .filter(l -> l.aufgabe() != null && !store.set("geloest").contains(l.id()))
                .findFirst().orElse(null);
        String lektion = naechste == null ? "Alle Aufgaben gelöst! Wiederhole deine Lieblingsaufgabe auf Papier."
                : "Lektion: " + naechste.titel();

        if (tage != null && tage == 0) {
            planZeile("Lies nur deine handgeschriebenen Notizen. Heute kommt nichts Neues mehr dazu.", "Handnotizen", JavaLernApp.TAB_NOTIZEN);
            planZeile("Wirf einen kurzen Blick auf den Spickzettel und atme durch.", "Nachschlagen", JavaLernApp.TAB_NACHSCHLAGEN);
            return;
        }
        if (tage != null && tage == 1) {
            planZeile("Lies deine Handnotizen und erkläre jede Seite laut.", "Handnotizen", JavaLernApp.TAB_NOTIZEN);
            planZeile("Mach ein Probe-Interview, nur zur Sicherheit.", "Probe-Interview", JavaLernApp.TAB_PROBE);
            planZeile("Wiederhole " + Math.min(faellig, 10) + " fällige Karten. Danach Schluss und früh schlafen.", "Karten", JavaLernApp.TAB_KARTEN);
            return;
        }
        boolean endspurt = tage != null && tage >= 2 && tage <= 3;
        planLektion(lektion, naechste);
        planZeile((notizen < mitNotizen ? "Schreib mindestens eine Seite Handnotizen aus dem Kopf."
                : "Lies deine Handnotizen und ergänze, was noch fehlt."), "Handnotizen", JavaLernApp.TAB_NOTIZEN);
        planZeile(faellig > 0 ? "Wiederhole " + Math.min(faellig, endspurt ? 15 : 10) + " fällige Karten."
                : "Heute sind keine Karten fällig. Sehr gut!", "Karten", JavaLernApp.TAB_KARTEN);
        planZeile("Löse 5 Quizfragen zuerst im Kopf oder auf Papier.", "Code-Quiz", JavaLernApp.TAB_QUIZ);
        if (endspurt) {
            planZeile("Mach ein Probe-Interview mit Timer.", "Probe-Interview", JavaLernApp.TAB_PROBE);
        }
    }

    private void planLektion(String text, Lesson naechste) {
        JPanel z = zeile();
        z.add(planLabel(text));
        if (naechste != null) {
            JButton b = new JButton("Weiterlernen");
            b.addActionListener(e -> nav.weiterLernen());
            z.add(b);
        }
        plan.add(z);
    }

    private void planZeile(String text, String knopf, int tab) {
        JPanel z = zeile();
        z.add(planLabel(text));
        JButton b = new JButton(knopf);
        b.addActionListener(e -> nav.zeigeTab(tab));
        z.add(b);
        plan.add(z);
    }

    private static JLabel planLabel(String text) {
        JLabel l = new JLabel("•  " + text);
        l.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 15));
        return l;
    }

    private void baueKapitel(Set<String> geloest) {
        kapitel.removeAll();
        Map<String, int[]> werte = new LinkedHashMap<>();
        for (Lesson l : lessons) {
            if (l.aufgabe() == null) continue;
            int[] w = werte.computeIfAbsent(l.kapitel(), k -> new int[2]);
            w[1]++;
            if (geloest.contains(l.id())) w[0]++;
        }
        int y = 0;
        for (Map.Entry<String, int[]> e : werte.entrySet()) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = y++;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 0, 2, 12);
            JLabel name = new JLabel(e.getKey());
            name.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 14));
            kapitel.add(name, c);
            JProgressBar bar = new JProgressBar(0, e.getValue()[1]);
            bar.setValue(e.getValue()[0]);
            bar.setStringPainted(true);
            bar.setString(e.getValue()[0] + " / " + e.getValue()[1]);
            bar.setPreferredSize(new Dimension(300, 20));
            c.gridx = 1;
            kapitel.add(bar, c);
        }
    }
}
