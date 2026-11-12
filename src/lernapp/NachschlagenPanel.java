package lernapp;

import lernapp.Content.Lesson;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Suche ueber Begriffe, Lektionen und Karten. Ohne Suchbegriff zeigt der Bereich den Spickzettel. */
class NachschlagenPanel extends JPanel {

    private record Treffer(String art, String titel, String inhalt, Lesson lesson) {
        @Override
        public String toString() {
            return art + ":  " + titel;
        }
    }

    private final List<Lesson> lessons;
    private final List<Map<String, String>> questions;
    private final Consumer<Lesson> oeffneLektion;
    private final JTextField suche = new JTextField();
    private final DefaultListModel<Treffer> model = new DefaultListModel<>();
    private final JList<Treffer> list = new JList<>(model);
    private final JEditorPane view = JavaLernApp.htmlPane();
    private final JButton zurLektion = new JButton("Zur Lektion");
    private final String spickzettel = Content.resource("/spickzettel.md");

    NachschlagenPanel(List<Lesson> lessons, List<Map<String, String>> questions, Consumer<Lesson> oeffneLektion) {
        super(new BorderLayout());
        this.lessons = lessons;
        this.questions = questions;
        this.oeffneLektion = oeffneLektion;

        suche.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 15));
        suche.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { suchen(); }
            public void removeUpdate(DocumentEvent e) { suchen(); }
            public void changedUpdate(DocumentEvent e) { suchen(); }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) zeigeTreffer();
        });
        list.setFont(list.getFont().deriveFont(14f));
        list.setFixedCellHeight(26);

        JPanel kopf = new JPanel(new BorderLayout(0, 4));
        kopf.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        kopf.add(new JLabel("Suche nach einem Begriff, z. B. HashMap oder static:"), BorderLayout.NORTH);
        kopf.add(suche, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(380, 100));
        left.add(kopf, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JButton spick = new JButton("Spickzettel zeigen");
        spick.addActionListener(e -> {
            suche.setText("");
            zeigeSpickzettel();
        });
        zurLektion.addActionListener(e -> {
            Treffer t = list.getSelectedValue();
            if (t != null && t.lesson() != null) oeffneLektion.accept(t.lesson());
        });
        bar.add(spick);
        bar.add(zurLektion);

        JPanel right = new JPanel(new BorderLayout());
        right.add(bar, BorderLayout.NORTH);
        JScrollPane vs = new JScrollPane(view);
        vs.getVerticalScrollBar().setUnitIncrement(18);
        right.add(vs, BorderLayout.CENTER);

        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right), BorderLayout.CENTER);
        alleBegriffe();
        zeigeSpickzettel();
    }

    private void zeigeSpickzettel() {
        zurLektion.setEnabled(false);
        list.clearSelection();
        view.setText("<html><body>" + new Markdown().toHtml(spickzettel) + "</body></html>");
        view.setCaretPosition(0);
    }

    private void suchen() {
        String q = suche.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        if (q.length() < 2) {
            alleBegriffe();
            zeigeSpickzettel();
            return;
        }
        for (Map.Entry<String, String> e : Explanations.glossar().entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(new Treffer("Begriff", e.getKey(), e.getValue(), null));
            }
        }
        for (Lesson l : lessons) {
            if ((l.titel() + " " + l.erklaerung()).toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(new Treffer("Lektion", l.titel(), l.erklaerung(), l));
            }
        }
        for (Map<String, String> k : questions) {
            if ((k.get("frage") + " " + k.get("antwort")).toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(new Treffer("Karte", k.get("frage"), k.get("antwort"), null));
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            zurLektion.setEnabled(false);
            view.setText("<html><body><p>Keine Treffer. Probiere einen anderen Begriff.</p></body></html>");
        }
    }

    private void alleBegriffe() {
        for (Map.Entry<String, String> e : Explanations.glossar().entrySet()) {
            model.addElement(new Treffer("Begriff", e.getKey(), e.getValue(), null));
        }
    }

    /** Fuer den Prueflauf: sucht und liefert die Anzahl der Treffer. */
    int sucheTest(String begriff) {
        suche.setText(begriff);
        return model.size();
    }

    private void zeigeTreffer() {
        Treffer t = list.getSelectedValue();
        if (t == null) return;
        zurLektion.setEnabled(t.lesson() != null);
        view.setText("<html><body><div class=kapitel>" + Markdown.esc(t.art()) + "</div><h1>"
                + Markdown.esc(t.titel()) + "</h1>" + new Markdown().toHtml(t.inhalt()) + "</body></html>");
        view.setCaretPosition(0);
    }
}
