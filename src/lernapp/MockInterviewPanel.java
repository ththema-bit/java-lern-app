package lernapp;

import lernapp.Content.Lesson;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Probe-Interview: Fragen und eine Papier-Aufgabe unter Zeitdruck, danach ehrliche Selbstbewertung. */
class MockInterviewPanel extends JPanel {

    private static final int DAUER_SEKUNDEN = 25 * 60;

    private record Schritt(String art, String frage, String antwort, String karte) {}

    private final List<Lesson> lessons;
    private final List<Map<String, String>> questions;
    private final List<QuizPanel.Frage> quiz;
    private final Content.Store store;
    private final Random random = new Random();

    private final JEditorPane view = JavaLernApp.htmlPane();
    private final JLabel uhr = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JButton startBtn = new JButton("Probe-Interview starten");
    private final JButton zeigenBtn = new JButton("Antwort zeigen");
    private final JButton gutBtn = new JButton("Gut");
    private final JButton mittelBtn = new JButton("Geht so");
    private final JButton schlechtBtn = new JButton("Nicht gewusst");
    private final javax.swing.Timer timer;

    private List<Schritt> schritte = List.of();
    private final List<Integer> bewertungen = new ArrayList<>();
    private int index = -1;
    private int restSekunden;
    private boolean gezeigt;

    MockInterviewPanel(List<Lesson> lessons, List<Map<String, String>> questions,
                       List<QuizPanel.Frage> quiz, Content.Store store) {
        super(new BorderLayout());
        this.lessons = lessons;
        this.questions = questions;
        this.quiz = quiz;
        this.store = store;

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        startBtn.setFont(startBtn.getFont().deriveFont(Font.BOLD, 14f));
        uhr.setFont(Theme.schrift("Consolas", Font.BOLD, 20));
        uhr.setForeground(Theme.UEBERSCHRIFT);
        gutBtn.setForeground(Theme.GRUEN);
        schlechtBtn.setForeground(Theme.ROT);
        bar.add(startBtn);
        bar.add(uhr);
        bar.add(status);
        bar.add(zeigenBtn);
        bar.add(schlechtBtn);
        bar.add(mittelBtn);
        bar.add(gutBtn);
        add(bar, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(view);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);

        startBtn.addActionListener(e -> starten());
        zeigenBtn.addActionListener(e -> {
            gezeigt = true;
            render();
        });
        schlechtBtn.addActionListener(e -> bewerten(0));
        mittelBtn.addActionListener(e -> bewerten(1));
        gutBtn.addActionListener(e -> bewerten(2));
        timer = new javax.swing.Timer(1000, e -> tick());
        startseite();
    }

    /** Fuer den Prueflauf: startet das Probe-Interview und zeigt die erste Station. */
    void testStart() {
        startBtn.doClick();
    }

    /** Fuer den Prueflauf: spielt alle Stationen durch und liefert die Anzahl der Bewertungen. */
    int testDurchlauf() {
        while (index >= 0 && index < schritte.size()) {
            zeigenBtn.doClick();
            mittelBtn.doClick();
        }
        return bewertungen.size();
    }

    private void startseite() {
        timer.stop();
        index = -1;
        uhr.setText(" ");
        status.setText(" ");
        startBtn.setText("Probe-Interview starten");
        zeigeButtons(false);
        view.setText("<html><body>" + new Markdown().toHtml("""
                # Probe-Interview
                Übe den Ablauf unter realistischen Bedingungen: **25 Minuten**, 8 Stationen.

                ## Das brauchst du
                - **Stift und Papier.** Du darfst die Sandbox nicht benutzen.
                - Einen ruhigen Platz, an dem du **laut** sprechen kannst.

                ## So läuft es ab
                1. Stell dich kurz vor.
                2. Beantworte 3 Wissensfragen laut.
                3. Löse 2 Code-Fragen im Kopf oder auf Papier.
                4. Löse eine Coding-Aufgabe **handschriftlich** und erkläre dabei jeden Schritt.
                5. Stell selbst eine Frage an das Unternehmen.

                Bewerte dich nach jeder Station ehrlich. Am Ende siehst du, was du wiederholen solltest. Nicht gewusste Karten kommen automatisch wieder dran.
                """) + "</body></html>");
        view.setCaretPosition(0);
    }

    private void zeigeButtons(boolean aktiv) {
        zeigenBtn.setVisible(aktiv && !gezeigt);
        schlechtBtn.setVisible(aktiv && gezeigt);
        mittelBtn.setVisible(aktiv && gezeigt);
        gutBtn.setVisible(aktiv && gezeigt);
    }

    private void starten() {
        if (index >= 0 && index < schritte.size()) {
            startseite();
            return;
        }
        List<Schritt> neu = new ArrayList<>();
        neu.add(new Schritt("Vorstellung", "Erzähl kurz etwas über dich. Du hast 90 Sekunden.",
                findeAntwort("Erzähl kurz etwas über dich."), null));

        List<Map<String, String>> wissen = new ArrayList<>(questions);
        wissen.removeIf(q -> "Soft Skills".equals(q.get("kategorie")));
        for (Map<String, String> q : InterviewPanel.gemischt(wissen, 3, random)) {
            neu.add(new Schritt("Wissensfrage", q.get("frage"), q.get("antwort"), q.get("frage")));
        }

        List<QuizPanel.Frage> code = new ArrayList<>(quiz);
        Collections.shuffle(code, random);
        for (QuizPanel.Frage f : code.subList(0, Math.min(2, code.size()))) {
            neu.add(new Schritt("Code-Frage", "Was gibt dieser Code aus? Sag auch, warum.\n\n```java\n" + f.code() + "\n```",
                    "**Antwort: " + f.optionen().get(f.richtig()) + "**\n\n" + f.erklaerung(), null));
        }

        List<Lesson> aufgaben = new ArrayList<>(lessons);
        aufgaben.removeIf(l -> !l.id().startsWith("w_"));
        if (!aufgaben.isEmpty()) {
            Lesson l = aufgaben.get(random.nextInt(aufgaben.size()));
            neu.add(new Schritt("Coding auf Papier",
                    "**" + l.titel() + "**\n\n" + l.aufgabe()
                            + "\n\n> Schreib die Lösung **von Hand** und erkläre laut, was du tust. Nenne am Ende die Laufzeit.",
                    "```java\n" + l.loesung() + "\n```\n\nTippe deine Lösung später in der Lektion ein und prüfe sie dort.", null));
        }
        neu.add(new Schritt("Deine Frage", "Haben Sie noch Fragen an uns?",
                findeAntwort("Welche Fragen solltest du selbst am Ende stellen?"), null));

        schritte = neu;
        bewertungen.clear();
        index = 0;
        gezeigt = false;
        restSekunden = DAUER_SEKUNDEN;
        startBtn.setText("Abbrechen");
        timer.start();
        store.markActive();
        tickAnzeige();
        render();
    }

    private String findeAntwort(String frage) {
        for (Map<String, String> q : questions) if (frage.equals(q.get("frage"))) return q.get("antwort");
        return "";
    }

    private void tick() {
        restSekunden--;
        tickAnzeige();
        if (restSekunden <= 0) {
            timer.stop();
            uhr.setForeground(Theme.ROT);
        }
    }

    private void tickAnzeige() {
        int s = Math.max(0, restSekunden);
        uhr.setText(String.format("%02d:%02d", s / 60, s % 60));
    }

    private void bewerten(int punkte) {
        Schritt s = schritte.get(index);
        bewertungen.add(punkte);
        if (s.karte() != null) {
            InterviewPanel.bewerte(store, s.karte(), punkte == 0 ? InterviewPanel.Note.NOCHMAL
                    : punkte == 1 ? InterviewPanel.Note.SCHWER : InterviewPanel.Note.GUT);
        }
        index++;
        gezeigt = false;
        if (index >= schritte.size()) auswertung();
        else render();
    }

    private void render() {
        Schritt s = schritte.get(index);
        status.setText("Station " + (index + 1) + " von " + schritte.size() + ": " + s.art());
        zeigeButtons(true);
        StringBuilder md = new StringBuilder("# ").append(s.art()).append("\n\n").append(s.frage());
        if (gezeigt) md.append("\n\n## So könnte eine gute Antwort aussehen\n\n").append(s.antwort());
        else md.append("\n\n> Antworte laut oder schreib auf Papier. Klicke erst danach auf „Antwort zeigen“.");
        view.setText("<html><body>" + new Markdown().toHtml(md.toString()) + "</body></html>");
        view.setCaretPosition(0);
    }

    private void auswertung() {
        timer.stop();
        uhr.setForeground(Theme.UEBERSCHRIFT);
        startBtn.setText("Neues Probe-Interview");
        status.setText("Fertig");
        zeigeButtons(false);
        int benutzt = DAUER_SEKUNDEN - restSekunden;
        int punkte = bewertungen.stream().mapToInt(Integer::intValue).sum();
        StringBuilder md = new StringBuilder("# Auswertung\n\n");
        md.append("**").append(punkte).append(" von ").append(bewertungen.size() * 2).append(" Punkten** in ")
                .append(benutzt / 60).append(" Minuten und ").append(benutzt % 60).append(" Sekunden.\n\n");
        md.append("## Das solltest du wiederholen\n\n");
        boolean offen = false;
        for (int i = 0; i < schritte.size(); i++) {
            if (bewertungen.get(i) < 2) {
                offen = true;
                String text = schritte.get(i).frage().split("\n")[0].replace("**", "");
                md.append("- ").append(schritte.get(i).art()).append(": ").append(text).append("\n");
            }
        }
        if (!offen) md.append("Alles gut gelaufen! Starte eine neue Runde mit anderen Fragen.\n");
        md.append("\n> **Stift raus:** Schreib die offenen Punkte jetzt von Hand auf und erkläre sie dir morgen früh noch einmal.");
        view.setText("<html><body>" + new Markdown().toHtml(md.toString()) + "</body></html>");
        view.setCaretPosition(0);
    }
}
