package lernapp;

import lernapp.Content.Lesson;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Uebersicht aller Handnotizen und Anleitung, wie man von Hand lernt. */
class HandnotizPanel extends JPanel {

    private final Content.Store store;
    private final Consumer<Lesson> oeffneLektion;
    private final DefaultListModel<Lesson> model = new DefaultListModel<>();
    private final JList<Lesson> list = new JList<>(model);
    private final JEditorPane view = JavaLernApp.htmlPane();
    private final JLabel fortschritt = new JLabel();
    private final JToggleButton erledigt = new JToggleButton("Von Hand notiert");

    HandnotizPanel(List<Lesson> lessons, Content.Store store, Consumer<Lesson> oeffneLektion) {
        super(new BorderLayout());
        this.store = store;
        this.oeffneLektion = oeffneLektion;
        for (Lesson l : lessons) if (!l.notizen().isEmpty()) model.addElement(l);

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                Lesson lesson = (Lesson) v;
                boolean ok = store.set("notiert").contains(lesson.id());
                c.setText("<html>" + (ok ? "<font color=" + Theme.H_GRUEN + ">✓</font>" : "○") + "&nbsp; <font color=" + Theme.H_GEDAEMPFT + ">"
                        + Markdown.esc(lesson.kapitel().split("·")[0].trim()) + "</font>&nbsp; "
                        + Markdown.esc(lesson.titel()) + "</html>");
                c.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                c.setFont(c.getFont().deriveFont(14f));
                return c;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) render();
        });

        JPanel left = new JPanel(new BorderLayout());
        fortschritt.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        fortschritt.setFont(fortschritt.getFont().deriveFont(Font.BOLD, 13f));
        left.add(fortschritt, BorderLayout.NORTH);
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(380, 100));
        left.add(ls, BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JButton zurLektion = new JButton("Zur Lektion");
        erledigt.setFont(erledigt.getFont().deriveFont(Font.BOLD, 14f));
        bar.add(erledigt);
        bar.add(zurLektion);
        erledigt.addActionListener(e -> {
            Lesson l = list.getSelectedValue();
            if (l == null) return;
            if (erledigt.isSelected()) store.add("notiert", l.id());
            else store.remove("notiert", l.id());
            store.markActive();
            refresh();
        });
        zurLektion.addActionListener(e -> {
            Lesson l = list.getSelectedValue();
            if (l != null) oeffneLektion.accept(l);
        });

        JPanel right = new JPanel(new BorderLayout());
        right.add(bar, BorderLayout.NORTH);
        JScrollPane vs = new JScrollPane(view);
        vs.getVerticalScrollBar().setUnitIncrement(18);
        right.add(vs, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(430);
        add(split, BorderLayout.CENTER);

        refresh();
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    void refresh() {
        long ok = 0;
        for (int i = 0; i < model.size(); i++) if (store.set("notiert").contains(model.get(i).id())) ok++;
        fortschritt.setText("Von Hand notiert: " + ok + " / " + model.size());
        list.repaint();
        render();
    }

    private void render() {
        Lesson l = list.getSelectedValue();
        if (l == null) return;
        erledigt.setSelected(store.set("notiert").contains(l.id()));
        StringBuilder md = new StringBuilder("# ").append(l.titel()).append("\n\n");
        md.append("## Das schreibst du von Hand auf\n\n");
        for (String n : l.notizen()) md.append("- ").append(n).append("\n");
        md.append("""

                ## So bleibt es hängen
                1. **Lesen:** Arbeite die Lektion durch und probiere den Code aus.
                2. **Weglegen:** Verdecke die Erklärung. Dafür gibt es in der Lektion einen eigenen Knopf.
                3. **Aufschreiben:** Schreib aus dem Kopf und in eigenen Worten alles auf, was du noch weißt.
                4. **Vergleichen:** Decke die Erklärung auf und ergänze Lücken in einer anderen Farbe.
                5. **Wiederholen:** Lies die Notiz morgen und erkläre sie laut.

                > Beim Schreiben von Hand musst du den Stoff selbst abrufen und in eigene Worte fassen. Genau das bleibt besser hängen als bloßes Abtippen. Bewahre deine Blätter auf: Am Tag vor dem Interview sind sie dein bester Spickzettel.
                """);
        view.setText("<html><body>" + new Markdown().toHtml(md.toString()) + "</body></html>");
        view.setCaretPosition(0);
    }
}
