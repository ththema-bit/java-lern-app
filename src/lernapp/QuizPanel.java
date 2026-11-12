package lernapp;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Quiz "Was gibt dieser Code aus?" mit typischen Interview-Fangfragen. */
class QuizPanel extends JPanel {

    record Frage(String id, String kategorie, String text, String code, List<String> optionen,
                 int richtig, String erklaerung) {}

    static List<Frage> laden() {
        List<Frage> res = new ArrayList<>();
        List<Map<String, String>> blocks = new ArrayList<>(Content.parseBlocks(Content.resource("/quiz.txt")));
        blocks.addAll(Content.parseBlocks(Content.resource("/quiz2.txt")));
        for (Map<String, String> b : blocks) {
            List<String> opts = new ArrayList<>();
            for (String o : b.getOrDefault("optionen", "").split("\n")) if (!o.isBlank()) opts.add(o.trim());
            res.add(new Frage(b.get("id"), b.getOrDefault("kategorie", ""),
                    b.getOrDefault("frage", "Was gibt dieser Code aus?"), b.getOrDefault("code", ""), opts,
                    Integer.parseInt(b.getOrDefault("richtig", "1").trim()) - 1, b.getOrDefault("erklaerung", "")));
        }
        return res;
    }

    private static final String[] BUCHSTABEN = {"A", "B", "C", "D", "E", "F"};

    private final List<Frage> fragen;
    private final Content.Store store;
    private final Consumer<String> tryIt;
    private final JList<Frage> list;
    private final JEditorPane frageView = JavaLernApp.htmlPane();
    private final JEditorPane loesungView = JavaLernApp.htmlPane();
    private final JPanel antworten = new JPanel(new GridLayout(0, 1, 6, 6));
    private final JLabel score = new JLabel();
    private final Map<Integer, Integer> gewaehlt = new HashMap<>();
    private final Random random = new Random();

    QuizPanel(List<Frage> fragen, Content.Store store, Consumer<String> tryIt) {
        super(new BorderLayout());
        this.fragen = fragen;
        this.store = store;
        this.tryIt = tryIt;

        DefaultListModel<Frage> model = new DefaultListModel<>();
        fragen.forEach(model::addElement);
        list = new JList<>(model);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                Frage q = (Frage) v;
                String mark = status(q).equals("ok") ? "<font color=" + Theme.H_GRUEN + ">✓</font>"
                        : status(q).equals("falsch") ? "<font color=" + Theme.H_ROT + ">✗</font>" : "○";
                c.setText("<html>" + mark + "&nbsp; Frage " + (i + 1) + " <font color=" + Theme.H_GEDAEMPFT + ">· "
                        + Markdown.esc(q.kategorie()) + "</font></html>");
                c.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                c.setFont(c.getFont().deriveFont(14f));
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) render();
        });

        JPanel left = new JPanel(new BorderLayout());
        score.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        score.setFont(score.getFont().deriveFont(Font.BOLD, 13f));
        left.add(score, BorderLayout.NORTH);
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(260, 100));
        left.add(ls, BorderLayout.CENTER);
        JButton reset = new JButton("Ergebnisse zurücksetzen");
        reset.addActionListener(e -> {
            for (Frage q : fragen) {
                store.remove("quiz_ok", q.id());
                store.remove("quiz_falsch", q.id());
            }
            gewaehlt.clear();
            updateScore();
            render();
        });
        left.add(reset, BorderLayout.SOUTH);

        JPanel upper = new JPanel(new BorderLayout());
        JScrollPane fs = new JScrollPane(frageView);
        fs.getVerticalScrollBar().setUnitIncrement(18);
        upper.add(fs, BorderLayout.CENTER);
        antworten.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        upper.add(antworten, BorderLayout.SOUTH);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JButton tryBtn = new JButton("▶  Code im Editor ausprobieren");
        JButton nextBtn = new JButton("Nächste Frage →");
        JButton randomBtn = new JButton("Zufällige offene Frage");
        bar.add(tryBtn);
        bar.add(nextBtn);
        bar.add(randomBtn);
        tryBtn.addActionListener(e -> {
            Frage q = list.getSelectedValue();
            if (q != null) tryIt.accept(q.code());
        });
        nextBtn.addActionListener(e -> select(Math.min(fragen.size() - 1, list.getSelectedIndex() + 1)));
        randomBtn.addActionListener(e -> {
            List<Integer> offen = new ArrayList<>();
            for (int i = 0; i < fragen.size(); i++) if (!status(fragen.get(i)).equals("ok")) offen.add(i);
            if (offen.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Alle Fragen richtig beantwortet – stark!");
                return;
            }
            select(offen.get(random.nextInt(offen.size())));
        });

        JPanel lower = new JPanel(new BorderLayout());
        lower.add(bar, BorderLayout.NORTH);
        JScrollPane lsv = new JScrollPane(loesungView);
        lsv.getVerticalScrollBar().setUnitIncrement(18);
        lower.add(lsv, BorderLayout.CENTER);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, lower);
        right.setResizeWeight(0.62);
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right), BorderLayout.CENTER);

        updateScore();
        if (!fragen.isEmpty()) list.setSelectedIndex(0);
    }

    private String status(Frage q) {
        if (store.set("quiz_ok").contains(q.id())) return "ok";
        if (store.set("quiz_falsch").contains(q.id())) return "falsch";
        return "offen";
    }

    private void select(int i) {
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void updateScore() {
        long ok = fragen.stream().filter(q -> status(q).equals("ok")).count();
        score.setText("Richtig: " + ok + " / " + fragen.size());
        list.repaint();
    }

    private void antworten(int frage, int option) {
        gewaehlt.put(frage, option);
        store.markActive();
        Frage q = fragen.get(frage);
        if (option == q.richtig()) {
            store.add("quiz_ok", q.id());
            store.remove("quiz_falsch", q.id());
        } else if (!status(q).equals("ok")) {
            store.add("quiz_falsch", q.id());
        }
        updateScore();
        render();
    }

    private void render() {
        int i = list.getSelectedIndex();
        if (i < 0) return;
        Frage q = fragen.get(i);
        Integer wahl = gewaehlt.get(i);

        frageView.setText("<html><body><div class=kapitel>Frage " + (i + 1) + " von " + fragen.size() + " · "
                + Markdown.esc(q.kategorie()) + "</div><h1>" + Markdown.esc(q.text()) + "</h1><pre>"
                + Markdown.esc(q.code()) + "</pre></body></html>");
        frageView.setCaretPosition(0);

        antworten.removeAll();
        for (int k = 0; k < q.optionen().size(); k++) {
            String text = BUCHSTABEN[k] + ")   " + q.optionen().get(k);
            JButton b = new JButton();
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(Theme.schrift("Consolas", Font.PLAIN, 15));
            if (wahl != null) {
                b.setEnabled(k == q.richtig() || k == wahl);
                if (k == q.richtig()) {
                    text = "✓  " + text;
                    b.setForeground(Theme.GRUEN);
                } else if (k == wahl) {
                    text = "✗  " + text;
                    b.setForeground(Theme.ROT);
                }
            }
            b.setText(text);
            final int option = k;
            b.addActionListener(e -> {
                if (gewaehlt.get(i) == null) antworten(i, option);
            });
            antworten.add(b);
        }
        antworten.revalidate();
        antworten.repaint();

        StringBuilder h = new StringBuilder("<html><body>");
        if (wahl == null) {
            h.append("<p><b>Stift raus:</b> Spiele den Code auf Papier durch und notiere die Variablen Zeile für Zeile. "
                    + "Klicke erst dann auf eine Antwort.</p>");
        } else {
            boolean ok = wahl == q.richtig();
            h.append(ok ? "<h2><font color=" + Theme.H_GRUEN + ">✓ Richtig!</font></h2>"
                    : "<h2><font color=" + Theme.H_ROT + ">✗ Leider falsch</font> – richtig ist "
                    + BUCHSTABEN[q.richtig()] + ")</h2>");
            h.append(new Markdown().toHtml(q.erklaerung()));
            h.append("<p><font color=" + Theme.H_GEDAEMPFT + ">Tipp: Mit „Code im Editor ausprobieren“ kannst du den Code ausführen "
                    + "und verändern.</font></p>");
        }
        h.append("</body></html>");
        loesungView.setText(h.toString());
        loesungView.setCaretPosition(0);
    }
}
