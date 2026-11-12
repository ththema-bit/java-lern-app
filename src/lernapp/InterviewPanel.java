package lernapp;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Karteikarten mit Wiederholung im Abstand: Schwieriges kommt oefter, Gewusstes spaeter wieder dran. */
class InterviewPanel extends JPanel {

    enum Note { NOCHMAL, SCHWER, GUT }

    private static final int[] ABSTAND_TAGE = {1, 2, 4, 8};

    private final List<Map<String, String>> questions;
    private final Content.Store store;
    private final JList<Map<String, String>> list;
    private final DefaultListModel<Map<String, String>> model = new DefaultListModel<>();
    private final JEditorPane view = JavaLernApp.htmlPane();
    private final JButton showBtn = new JButton("Antwort zeigen");
    private final JButton nochmalBtn = new JButton("Nochmal (heute)");
    private final JButton schwerBtn = new JButton("Schwer (morgen)");
    private final JButton gutBtn = new JButton("Gut gewusst");
    private final JCheckBox nurFaellig = new JCheckBox("Nur fällige Karten", true);
    private final JLabel progress = new JLabel();
    private boolean shown;

    InterviewPanel(List<Map<String, String>> questions, Content.Store store) {
        super(new BorderLayout());
        this.questions = questions;
        this.store = store;

        list = new JList<>(model);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                @SuppressWarnings("unchecked") Map<String, String> q = (Map<String, String>) v;
                int box = box(store, q.get("frage"));
                String mark = box >= 1 ? "<font color=" + Theme.H_GRUEN + ">✓</font>" : istFaellig(store, q.get("frage")) ? "○" : "•";
                c.setText("<html>" + mark + "&nbsp; <font color=" + Theme.H_GEDAEMPFT + ">[" + Markdown.esc(q.getOrDefault("kategorie", ""))
                        + "]</font> " + Markdown.esc(q.get("frage")) + "</html>");
                c.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
                c.setFont(c.getFont().deriveFont(13.5f));
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                shown = false;
                render();
            }
        });

        JPanel kopf = new JPanel(new BorderLayout());
        progress.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        progress.setFont(progress.getFont().deriveFont(Font.BOLD, 13f));
        kopf.add(progress, BorderLayout.NORTH);
        nurFaellig.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        nurFaellig.addActionListener(e -> refresh());
        kopf.add(nurFaellig, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout());
        left.add(kopf, BorderLayout.NORTH);
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(460, 100));
        left.add(ls, BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        showBtn.setFont(showBtn.getFont().deriveFont(Font.BOLD, 14f));
        nochmalBtn.setForeground(Theme.ROT);
        gutBtn.setForeground(Theme.GRUEN);
        bar.add(showBtn);
        bar.add(nochmalBtn);
        bar.add(schwerBtn);
        bar.add(gutBtn);

        showBtn.addActionListener(e -> {
            shown = !shown;
            render();
        });
        nochmalBtn.addActionListener(e -> bewerten(Note.NOCHMAL));
        schwerBtn.addActionListener(e -> bewerten(Note.SCHWER));
        gutBtn.addActionListener(e -> bewerten(Note.GUT));

        JPanel right = new JPanel(new BorderLayout());
        right.add(bar, BorderLayout.NORTH);
        JScrollPane vs = new JScrollPane(view);
        vs.getVerticalScrollBar().setUnitIncrement(18);
        right.add(vs, BorderLayout.CENTER);

        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right), BorderLayout.CENTER);
        refresh();
    }

    // ------------------------------------------------------------------ Lernplan der Karten

    static String key(String frage) {
        return "f" + Integer.toHexString(frage.hashCode());
    }

    /** Stufe der Karte: 0 = neu oder nicht gewusst, ab 1 = gewusst. */
    static int box(Content.Store store, String frage) {
        String s = store.get("karte." + key(frage));
        if (s != null) return Integer.parseInt(s.split(";")[0]);
        return store.set("gewusst").contains(key(frage)) ? 1 : 0;
    }

    static boolean istFaellig(Content.Store store, String frage) {
        String s = store.get("karte." + key(frage));
        if (s == null) return true;
        return Long.parseLong(s.split(";")[1]) <= LocalDate.now().toEpochDay();
    }

    static void bewerte(Content.Store store, String frage, Note note) {
        long heute = LocalDate.now().toEpochDay();
        int box = box(store, frage);
        long naechster;
        switch (note) {
            case NOCHMAL -> {
                box = 0;
                naechster = heute;
            }
            case SCHWER -> {
                box = Math.max(1, box);
                naechster = heute + 1;
            }
            default -> {
                naechster = heute + ABSTAND_TAGE[Math.min(box, ABSTAND_TAGE.length - 1)];
                box = Math.min(box + 1, ABSTAND_TAGE.length);
            }
        }
        // Vor dem Interview soll jede Karte noch einmal drankommen.
        Long termin = DashboardPanel.interviewTag(store);
        if (termin != null && termin > heute + 1) naechster = Math.min(naechster, termin - 1);
        store.put("karte." + key(frage), box + ";" + naechster);
        store.markActive();
    }

    // ------------------------------------------------------------------ Oberflaeche

    void refresh() {
        Map<String, String> aktuell = list.getSelectedValue();
        model.clear();
        for (Map<String, String> q : questions) {
            if (!nurFaellig.isSelected() || istFaellig(store, q.get("frage"))) model.addElement(q);
        }
        updateProgress();
        int idx = aktuell == null ? -1 : model.indexOf(aktuell);
        if (idx >= 0) list.setSelectedIndex(idx);
        else if (!model.isEmpty()) list.setSelectedIndex(0);
        else render();
    }

    private void updateProgress() {
        long gewusst = questions.stream().filter(q -> box(store, q.get("frage")) >= 1).count();
        long faellig = questions.stream().filter(q -> istFaellig(store, q.get("frage"))).count();
        progress.setText("Gewusst: " + gewusst + " / " + questions.size() + "   ·   heute fällig: " + faellig);
        list.repaint();
    }

    private void bewerten(Note note) {
        Map<String, String> q = list.getSelectedValue();
        if (q == null) return;
        int i = list.getSelectedIndex();
        bewerte(store, q.get("frage"), note);
        Map<String, String> naechste = i + 1 < model.size() ? model.get(i + 1) : null;
        if (nurFaellig.isSelected() && note != Note.NOCHMAL) model.remove(i);
        updateProgress();
        if (model.isEmpty()) {
            list.clearSelection();
            render();
        } else if (naechste != null && model.contains(naechste)) {
            list.setSelectedValue(naechste, true);
        } else {
            list.setSelectedIndex(0);
        }
    }

    private void render() {
        Map<String, String> q = list.getSelectedValue();
        boolean hatKarte = q != null;
        showBtn.setEnabled(hatKarte);
        nochmalBtn.setEnabled(hatKarte && shown);
        schwerBtn.setEnabled(hatKarte && shown);
        gutBtn.setEnabled(hatKarte && shown);
        if (!hatKarte) {
            view.setText("<html><body><h1>Für heute geschafft!</h1><p>Alle fälligen Karten sind durch. "
                    + "Nimm dir deine handgeschriebenen Notizen und lies sie einmal laut durch.</p>"
                    + "<p>Mit einem Klick auf „Nur fällige Karten“ siehst du wieder alle Karten.</p></body></html>");
            return;
        }
        showBtn.setText(shown ? "Antwort verbergen" : "Antwort zeigen");
        StringBuilder h = new StringBuilder("<html><body>");
        h.append("<div class=kapitel>").append(Markdown.esc(q.getOrDefault("kategorie", ""))).append("</div>");
        h.append("<h1>").append(Markdown.esc(q.get("frage"))).append("</h1>");
        if (shown) {
            h.append("<hr>").append(new Markdown().toHtml(q.get("antwort")));
            h.append("<p><font color=" + Theme.H_GEDAEMPFT + ">Vergleiche mit deinen Stichworten, ergänze auf dem Papier, was gefehlt hat, "
                    + "und bewerte dich dann ehrlich.</font></p>");
        } else {
            h.append("<p><b>Stift raus:</b> Schreib zuerst 2 bis 3 Stichworte auf Papier. "
                    + "Antworte dann laut in ganzen Sätzen, so wie im Gespräch.</p>");
            h.append("<p><font color=" + Theme.H_GEDAEMPFT + ">Erst danach „Antwort zeigen“ klicken.</font></p>");
        }
        h.append("</body></html>");
        view.setText(h.toString());
        view.setCaretPosition(0);
    }

    static List<Map<String, String>> gemischt(List<Map<String, String>> alle, int anzahl, java.util.Random r) {
        List<Map<String, String>> kopie = new ArrayList<>(alle);
        java.util.Collections.shuffle(kopie, r);
        return kopie.subList(0, Math.min(anzahl, kopie.size()));
    }
}
