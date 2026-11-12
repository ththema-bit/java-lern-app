package lernapp;

import lernapp.Content.Lesson;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class JavaLernApp extends JFrame {

    static final int TAB_START = 0, TAB_LEKTIONEN = 1, TAB_KARTEN = 2, TAB_QUIZ = 3, TAB_PROBE = 4,
            TAB_NOTIZEN = 5, TAB_NACHSCHLAGEN = 6;

    private final List<Lesson> lessons = Content.lessons();
    private final List<Map<String, String>> questions = Content.questions();
    private final List<QuizPanel.Frage> quiz = QuizPanel.laden();
    private final Content.Store store = new Content.Store();
    private final CodeRunner runner;

    private final CodeEditor editor = new CodeEditor();
    private final Console console = new Console();
    private final JEditorPane explain = htmlPane();
    private final JScrollPane explainScroll = new JScrollPane(explain);
    private final JPanel work = new JPanel(new BorderLayout());
    private JSplitPane rightSplit;
    /** Rechte Seite der Lektionen: Erklaerung ueber dem Editor, oder nur der Editor, wenn die Erklaerung ausgelagert ist. */
    private final JPanel rechts = new JPanel(new BorderLayout());
    private final JButton erklaerungBtn = new JButton("Erklärung in eigenem Fenster");
    Fenster fenster;
    private final JTextField input = new JTextField();
    private final JLabel inputLabel = new JLabel();
    private final javax.swing.Timer focusInputTimer = new javax.swing.Timer(700, e -> {
        if (this.running) input.requestFocusInWindow();
    });
    private final DefaultListModel<Object> lessonModel = new DefaultListModel<>();
    private final JList<Object> lessonList = new JList<>(lessonModel);
    private final JTextField lessonFilter = new JTextField();
    private final JTextArea hint = new JTextArea(2, 40);
    private final JLabel progressLabel = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    private final JButton runBtn = new JButton("▶  Ausführen  (Strg+Enter)");
    private final JButton stepBtn = new JButton("Schritt für Schritt");
    private final JButton stopBtn = new JButton("■  Stopp");
    private final JButton tippBtn = new JButton("Tipp");
    private final JButton solutionBtn = new JButton("Lösung zeigen");
    private final JButton nextBtn = new JButton("Nächste Lektion  →");
    private final JButton notesBtn = new JButton("Handnotizen ansehen");
    private final JCheckBox notedBox = new JCheckBox("Von Hand notiert");
    private final JToggleButton coverBtn = new JToggleButton("Erklärung verdecken");
    private final JCheckBox doneBox = new JCheckBox("Aufgabe erledigt");

    private final JPanel previewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
    private final JLabel previewLabel = new JLabel();

    private final JPanel stepBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
    private final JButton stepIntoBtn = new JButton("Nächster Schritt  (F6)");
    private final JButton stepOverBtn = new JButton("Methode überspringen  (F7)");
    private final JButton stepRunBtn = new JButton("Bis zum Ende");
    private final JToggleButton stepAutoBtn = new JToggleButton("Automatisch");
    private final JLabel stepInfo = new JLabel();
    private final DefaultTableModel varModel = new DefaultTableModel(new String[]{"Variable", "Typ", "Wert"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable varTable = new JTable(varModel);
    private final JScrollPane varScroll = new JScrollPane(varTable);
    private final Set<String> changedVars = new HashSet<>();
    private Map<String, String> lastVars = new HashMap<>();
    private final javax.swing.Timer stepAutoTimer = new javax.swing.Timer(900, e -> {
        if (this.stepWaiting) stepCommand(0);
    });

    private DashboardPanel dashboard;
    private InterviewPanel kartenPanel;
    private HandnotizPanel notizPanel;
    private MockInterviewPanel probePanel;
    private NachschlagenPanel nachschlagenPanel;

    private Lesson current;
    private List<String> snippets = List.of();
    private boolean running;
    private boolean covered;
    private int tippIndex;
    /** Eigener Code, solange ein Beispiel oder die Loesung angezeigt wird (sonst null). */
    private String ownCode;
    private StepDebugger stepper;
    private boolean stepWaiting;

    private static final String DEFAULT_HINT =
            "Klicke im Code auf ein Wort wie static oder ArrayList, dann erscheint hier eine Erklärung.   "
                    + "Strg+Z rückgängig  ·  Strg+Y wiederholen  ·  Strg+/ (oder Strg+Umschalt+7) Kommentar  ·  Strg+Mausrad Schriftgröße";

    public JavaLernApp() throws Exception {
        super("Java Lern-Sandbox");
        runner = new CodeRunner();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (fenster != null) fenster.speichern();
                saveCurrentCode();
                stopAll();
                dispose();
                System.exit(0);
            }
        });

        store.beiFehler = text -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, text, "Speichern fehlgeschlagen", JOptionPane.WARNING_MESSAGE));
        dashboard = new DashboardPanel(lessons, questions, quiz.size(), store, new DashboardPanel.Navigation() {
            public void weiterLernen() { continueLessons(); }
            public void zeigeTab(int index) { zeigeBereich(index); }
            public void vorExport() { saveCurrentCode(); }
            public void nachImport() { lernstandNeuLaden(); }
        });
        kartenPanel = new InterviewPanel(questions, store);
        notizPanel = new HandnotizPanel(lessons, store, this::showLesson);

        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 14f));
        tabs.addTab("  Start  ", dashboard);
        tabs.addTab("  Lektionen & Sandbox  ", lessonsPanel());
        tabs.addTab("  Interview-Karten  ", kartenPanel);
        tabs.addTab("  Code-Quiz  ", new QuizPanel(quiz, store, this::tryInPlayground));
        probePanel = new MockInterviewPanel(lessons, questions, quiz, store);
        nachschlagenPanel = new NachschlagenPanel(lessons, questions, this::showLesson);
        tabs.addTab("  Probe-Interview  ", probePanel);
        tabs.addTab("  Handnotizen  ", notizPanel);
        tabs.addTab("  Nachschlagen  ", nachschlagenPanel);
        tabs.addChangeListener(e -> {
            int t = tabs.getSelectedIndex();
            if (t == TAB_START) dashboard.refresh();
            else if (t == TAB_KARTEN) kartenPanel.refresh();
            else if (t == TAB_NOTIZEN) notizPanel.refresh();
        });
        setContentPane(tabs);

        fenster = new Fenster(this, store, this::tastenkuerzel);
        fenster.geaendert = () -> erklaerungBtn.setText(fenster.istAusgelagert("erklaerung")
                ? "Erklärung zurückholen" : "Erklärung in eigenem Fenster");
        for (int i = 0; i < tabs.getTabCount(); i++) registriereTab(i);
        fenster.registriere("erklaerung", "Erklärung der Lektion", explainScroll,
                () -> {
                    rechts.removeAll();
                    rechts.add(work, BorderLayout.CENTER);
                    rechts.revalidate();
                    rechts.repaint();
                },
                () -> {
                    rightSplit.setTopComponent(explainScroll);
                    rightSplit.setBottomComponent(work);
                    rechts.removeAll();
                    rechts.add(rightSplit, BorderLayout.CENTER);
                    rechts.revalidate();
                    rechts.repaint();
                    SwingUtilities.invokeLater(() -> rightSplit.setDividerLocation(0.42));
                },
                () -> { });
        tabs.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { tabMenue(e); }
            @Override public void mouseReleased(MouseEvent e) { tabMenue(e); }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = tabs.indexAtLocation(e.getX(), e.getY());
                if (i >= 0 && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) fenster.auslagern("tab" + i);
            }
        });
        tabs.setToolTipText("Rechtsklick oder Doppelklick auf einen Tab: in eigenem Fenster öffnen");
        setJMenuBar(menueleiste());

        installShortcuts();
        setRunning(false);
        setSize(1500, 950);
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);

        Lesson start = lessons.get(0);
        String last = store.get("last");
        for (Lesson l : lessons) if (l.id().equals(last)) start = l;
        lessonList.setSelectedValue(start, true);

        // Speichert nur, wenn sich etwas geaendert hat, daher genuegt ein kurzer Takt
        javax.swing.Timer autosave = new javax.swing.Timer(3_000, e -> saveCurrentCode());
        autosave.start();
    }

    private void stopAll() {
        runner.stop();
        if (stepper != null) stepper.stop();
    }

    // ------------------------------------------------------------------ Aufbau

    private JComponent lessonsPanel() {
        rebuildLessonList("");
        lessonList.setCellRenderer(new LessonRenderer());
        lessonList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lessonList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Object v = lessonList.getSelectedValue();
            if (v instanceof Lesson l) {
                if (l != current) openLesson(l);
            } else if (v != null) {
                int i = lessonList.getSelectedIndex();
                if (i + 1 < lessonModel.size()) lessonList.setSelectedIndex(i + 1);
            }
        });
        lessonFilter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterChanged(); }
            public void removeUpdate(DocumentEvent e) { filterChanged(); }
            public void changedUpdate(DocumentEvent e) { filterChanged(); }
        });
        lessonFilter.setToolTipText("Lektionen nach Titel filtern");

        JScrollPane listScroll = new JScrollPane(lessonList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getVerticalScrollBar().setUnitIncrement(18);
        JPanel kopf = new JPanel(new BorderLayout(0, 4));
        kopf.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 13f));
        kopf.add(progressLabel, BorderLayout.NORTH);
        JPanel filterZeile = new JPanel(new BorderLayout(6, 0));
        filterZeile.add(new JLabel("Suchen:"), BorderLayout.WEST);
        filterZeile.add(lessonFilter, BorderLayout.CENTER);
        kopf.add(filterZeile, BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout());
        left.add(kopf, BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(370, 100));
        updateProgress();

        explain.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String d = e.getDescription();
            if (d != null && d.startsWith("snippet:")) loadSnippet(Integer.parseInt(d.substring(8)));
        });
        explainScroll.getVerticalScrollBar().setUnitIncrement(18);
        explainScroll.setBorder(BorderFactory.createEmptyBorder());

        // Knopfleisten
        JPanel bar1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD, 14f));
        runBtn.setForeground(Theme.GRUEN);
        stopBtn.setEnabled(false);
        JButton resetBtn = new JButton("↺  Zurücksetzen");
        runBtn.addActionListener(e -> runCode());
        stepBtn.addActionListener(e -> startStepping());
        stopBtn.addActionListener(e -> stopAll());
        tippBtn.addActionListener(e -> showNextHint());
        resetBtn.addActionListener(e -> resetCode());
        solutionBtn.addActionListener(e -> showSolution());
        nextBtn.addActionListener(e -> nextLesson());
        runBtn.setToolTipText("Code kompilieren und ausführen (Strg+Enter oder F5)");
        stepBtn.setToolTipText("Programm Zeile für Zeile ausführen und die Variablen beobachten");
        tippBtn.setToolTipText("Gibt dir den nächsten Hinweis, ohne die Lösung zu verraten");
        resetBtn.setToolTipText("Code auf den Anfangszustand der Lektion zurücksetzen");
        for (JComponent c : new JComponent[]{runBtn, stepBtn, stopBtn, tippBtn, solutionBtn, resetBtn, nextBtn}) bar1.add(c);

        JPanel bar2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton saveBtn = new JButton("Als .java speichern");
        JButton smaller = new JButton("A−");
        JButton bigger = new JButton("A+");
        notesBtn.addActionListener(e -> {
            if (covered) {
                covered = false;
                coverBtn.setSelected(false);
                renderExplanation();
            }
            SwingUtilities.invokeLater(() -> explain.scrollToReference("notizen"));
        });
        notedBox.addActionListener(e -> {
            if (current == null) return;
            if (notedBox.isSelected()) store.add("notiert", current.id());
            else store.remove("notiert", current.id());
            store.markActive();
            lessonList.repaint();
        });
        coverBtn.addActionListener(e -> {
            covered = coverBtn.isSelected();
            renderExplanation();
        });
        doneBox.addActionListener(e -> {
            if (current == null) return;
            if (doneBox.isSelected()) store.add("geloest", current.id());
            else store.remove("geloest", current.id());
            store.markActive();
            updateProgress();
        });
        saveBtn.addActionListener(e -> exportCode());
        smaller.addActionListener(e -> fontSize(-1));
        bigger.addActionListener(e -> fontSize(1));
        saveBtn.setToolTipText("Speichert deinen Code als Datei, z. B. für IntelliJ oder das Terminal");
        erklaerungBtn.setToolTipText("Zeigt die Erklärung in einem eigenen Fenster, z. B. auf einem zweiten Bildschirm");
        erklaerungBtn.addActionListener(e -> fenster.umschalten("erklaerung"));
        for (JComponent c : new JComponent[]{notesBtn, notedBox, coverBtn, doneBox, saveBtn, smaller, bigger, erklaerungBtn}) bar2.add(c);

        previewBar.setBackground(Theme.BOX_BLAU);
        JButton backBtn = new JButton("←  Zurück zu meinem Code");
        backBtn.addActionListener(e -> endPreview());
        previewBar.add(previewLabel);
        previewBar.add(backBtn);
        previewBar.setVisible(false);

        stepBar.setBackground(Theme.BOX_GRUEN);
        stepIntoBtn.addActionListener(e -> stepCommand(0));
        stepOverBtn.addActionListener(e -> stepCommand(1));
        stepRunBtn.addActionListener(e -> stepCommand(2));
        stepAutoBtn.addActionListener(e -> {
            if (stepAutoBtn.isSelected()) stepAutoTimer.start();
            else stepAutoTimer.stop();
        });
        stepInfo.setFont(stepInfo.getFont().deriveFont(Font.BOLD));
        for (JComponent c : new JComponent[]{stepIntoBtn, stepOverBtn, stepRunBtn, stepAutoBtn, stepInfo}) stepBar.add(c);
        stepBar.setVisible(false);

        JPanel bars = new JPanel();
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        for (JPanel p : new JPanel[]{bar1, bar2, previewBar, stepBar}) {
            p.setAlignmentX(LEFT_ALIGNMENT);
            bars.add(p);
        }

        // Editor, Variablen, Konsole
        JPanel inputPanel = new JPanel(new BorderLayout(0, 4));
        inputPanel.add(inputLabel, BorderLayout.NORTH);
        input.setFont(Theme.schrift("Consolas", Font.PLAIN, 15));
        input.addActionListener(e -> {
            if (!running) return;
            String line = input.getText();
            input.setText("");
            console.input(line + "\n");
            if (stepper != null) stepper.sendInput(line);
            else runner.sendInput(line);
        });
        inputPanel.add(input, BorderLayout.CENTER);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        focusInputTimer.setRepeats(false);

        varTable.setRowHeight(24);
        varTable.setFont(Theme.schrift("Consolas", Font.PLAIN, 14));
        varTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        varTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        varTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        varTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                boolean geaendert = changedVars.contains(String.valueOf(t.getValueAt(r, 0)));
                comp.setBackground(geaendert ? Theme.GEAENDERT : Theme.FLAECHE);
                comp.setForeground(Theme.TEXT);
                setToolTipText(String.valueOf(v));
                return comp;
            }
        });
        varScroll.setPreferredSize(new Dimension(300, 190));
        varScroll.setVisible(false);

        JPanel consolePanel = new JPanel(new BorderLayout());
        JLabel outLabel = new JLabel("  Ausgabe");
        outLabel.setFont(outLabel.getFont().deriveFont(Font.BOLD));
        outLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        JPanel obenRechts = new JPanel(new BorderLayout());
        obenRechts.add(varScroll, BorderLayout.NORTH);
        obenRechts.add(outLabel, BorderLayout.SOUTH);
        consolePanel.add(obenRechts, BorderLayout.NORTH);
        consolePanel.add(console.scroll(), BorderLayout.CENTER);
        consolePanel.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane codeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, consolePanel);
        codeSplit.setResizeWeight(0.58);
        codeSplit.setBorder(BorderFactory.createEmptyBorder());

        work.add(bars, BorderLayout.NORTH);
        work.add(codeSplit, BorderLayout.CENTER);

        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, explainScroll, work);
        rightSplit.setResizeWeight(0.42);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());
        rechts.add(rightSplit, BorderLayout.CENTER);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rechts);
        main.setBorder(BorderFactory.createEmptyBorder());
        main.setDividerLocation(370);

        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 14));
        hint.setBackground(Theme.FLAECHE);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        hint.setText(DEFAULT_HINT);
        editor.setWordListener(w -> {
            String g = Explanations.glossary(w);
            hint.setText(g == null ? DEFAULT_HINT : w + "  –  " + g);
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(main, BorderLayout.CENTER);
        root.add(hint, BorderLayout.SOUTH);
        return root;
    }

    private void rebuildLessonList(String filter) {
        String f = filter.trim().toLowerCase(Locale.ROOT);
        lessonModel.clear();
        String lastKapitel = null;
        for (Lesson l : lessons) {
            if (!f.isEmpty() && !(l.titel() + " " + l.kapitel()).toLowerCase(Locale.ROOT).contains(f)) continue;
            if (!l.kapitel().equals(lastKapitel)) {
                lessonModel.addElement(l.kapitel());
                lastKapitel = l.kapitel();
            }
            lessonModel.addElement(l);
        }
    }

    private void filterChanged() {
        rebuildLessonList(lessonFilter.getText());
        if (current != null && lessonModel.contains(current)) lessonList.setSelectedValue(current, true);
    }

    // ------------------------------------------------------------------ Mehrere Fenster

    private static final String[] TAB_NAMEN = {"Start", "Lektionen & Sandbox", "Interview-Karten", "Code-Quiz",
            "Probe-Interview", "Handnotizen", "Nachschlagen"};

    private void registriereTab(int i) {
        JComponent inhalt = (JComponent) tabs.getComponentAt(i);
        String name = TAB_NAMEN[i];
        fenster.registriere("tab" + i, name, inhalt,
                () -> tabs.setComponentAt(i, platzhalter(i, name)),
                () -> {
                    tabs.setComponentAt(i, inhalt);
                    tabs.setSelectedIndex(i);
                },
                () -> {
                    if (i == TAB_START) dashboard.refresh();
                    else if (i == TAB_KARTEN) kartenPanel.refresh();
                    else if (i == TAB_NOTIZEN) notizPanel.refresh();
                });
    }

    /** Steht im Tab, solange sein Inhalt in einem eigenen Fenster ist. */
    private JComponent platzhalter(int i, String name) {
        JPanel p = new JPanel(new GridBagLayout());
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("„" + name + "“ ist gerade in einem eigenen Fenster.");
        text.setFont(Theme.schrift("Segoe UI", Font.BOLD, 18));
        text.setForeground(Theme.UEBERSCHRIFT);
        text.setAlignmentX(CENTER_ALIGNMENT);
        JLabel klein = new JLabel("Schließt du das Fenster, kommt der Bereich hierher zurück.");
        klein.setForeground(Theme.GEDAEMPFT);
        klein.setAlignmentX(CENTER_ALIGNMENT);
        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        JButton zeigen = new JButton("Fenster zeigen");
        JButton zurueck = new JButton("Hierher zurückholen");
        zeigen.addActionListener(e -> fenster.zeige("tab" + i));
        zurueck.addActionListener(e -> fenster.zurueckholen("tab" + i));
        knoepfe.add(zeigen);
        knoepfe.add(zurueck);
        knoepfe.setAlignmentX(CENTER_ALIGNMENT);
        box.add(text);
        box.add(Box.createVerticalStrut(6));
        box.add(klein);
        box.add(Box.createVerticalStrut(14));
        box.add(knoepfe);
        p.add(box);
        return p;
    }

    private void tabMenue(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int i = tabs.indexAtLocation(e.getX(), e.getY());
        if (i < 0) return;
        JPopupMenu m = new JPopupMenu();
        String id = "tab" + i;
        JMenuItem item = new JMenuItem(fenster.istAusgelagert(id) ? "Hierher zurückholen" : "In eigenem Fenster öffnen");
        item.addActionListener(a -> fenster.umschalten(id));
        m.add(item);
        m.show(tabs, e.getX(), e.getY());
    }

    /** Zeigt einen Bereich, egal ob er im Hauptfenster oder in einem eigenen Fenster ist. */
    private void zeigeBereich(int tab) {
        if (fenster != null && fenster.istAusgelagert("tab" + tab)) fenster.zeige("tab" + tab);
        else tabs.setSelectedIndex(tab);
    }

    /** Alle Bildschirme nutzen: Lektion im Hauptfenster, Erklaerung und Nachschlagen daneben. */
    private void alleBildschirmeNutzen() {
        int n = Fenster.bildschirme().size();
        if (fenster.istAusgelagert("tab" + TAB_LEKTIONEN)) fenster.zurueckholen("tab" + TAB_LEKTIONEN);
        tabs.setSelectedIndex(TAB_LEKTIONEN);
        fenster.auslagern("erklaerung");
        if (n >= 3 || n == 1) fenster.auslagern("tab" + TAB_NACHSCHLAGEN);
        fenster.verteilen();
    }

    private JMenuBar menueleiste() {
        JMenuBar bar = new JMenuBar();
        JMenu m = new JMenu("Fenster");
        m.setMnemonic(KeyEvent.VK_F);
        JMenuItem nutzen = new JMenuItem("Alle Bildschirme nutzen (Lektion, Erklärung, Nachschlagen)");
        nutzen.addActionListener(e -> alleBildschirmeNutzen());
        m.add(nutzen);
        m.addSeparator();
        JMenu einzeln = new JMenu("In eigenem Fenster öffnen");
        List<JCheckBoxMenuItem> haken = new ArrayList<>();
        for (String id : fenster.ids()) {
            JCheckBoxMenuItem c = new JCheckBoxMenuItem(fenster.titel(id));
            c.addActionListener(e -> fenster.umschalten(id));
            c.putClientProperty("id", id);
            haken.add(c);
            einzeln.add(c);
        }
        m.add(einzeln);
        JMenuItem verteilen = new JMenuItem("Offene Fenster auf die Bildschirme verteilen");
        verteilen.addActionListener(e -> fenster.verteilen());
        m.add(verteilen);
        JMenuItem zurueck = new JMenuItem("Alle Fenster zurückholen");
        zurueck.addActionListener(e -> fenster.alleZurueckholen());
        m.add(zurueck);
        m.addSeparator();
        JMenuItem tipp = new JMenuItem("Tipp: Rechtsklick oder Doppelklick auf einen Tab öffnet ihn in einem eigenen Fenster.");
        tipp.setEnabled(false);
        m.add(tipp);
        m.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuSelected(javax.swing.event.MenuEvent e) {
                for (JCheckBoxMenuItem c : haken) c.setSelected(fenster.istAusgelagert((String) c.getClientProperty("id")));
                verteilen.setEnabled(fenster.ids().stream().anyMatch(fenster::istAusgelagert));
                zurueck.setEnabled(verteilen.isEnabled());
            }
            public void menuDeselected(javax.swing.event.MenuEvent e) { }
            public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        bar.add(m);
        fensterMenue = m;
        return bar;
    }

    private JMenu fensterMenue;

    private void installShortcuts() {
        tastenkuerzel(getRootPane());
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (ev instanceof MouseWheelEvent mw && mw.isControlDown() && mw.getID() == MouseEvent.MOUSE_WHEEL
                    && SwingUtilities.isDescendingFrom(mw.getComponent(), editor)) {
                fontSize(mw.getWheelRotation() < 0 ? 1 : -1);
                mw.consume();
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    /** Tastenkuerzel wie Strg+Enter gelten in jedem Fenster der App. */
    private void tastenkuerzel(JRootPane rp) {
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        Map<String, Runnable> aktionen = new HashMap<>();
        aktionen.put("run", this::runCode);
        aktionen.put("bigger", () -> fontSize(1));
        aktionen.put("smaller", () -> fontSize(-1));
        aktionen.put("stepInto", () -> stepCommand(0));
        aktionen.put("stepOver", () -> stepCommand(1));
        aktionen.forEach((name, r) -> am.put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { r.run(); }
        }));
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menu), "run");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "stepInto");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "stepOver");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, menu), "bigger");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, menu), "bigger");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menu), "smaller");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, menu), "smaller");
    }

    private void fontSize(int delta) {
        editor.changeFontSize(delta);
        console.setFontSize(editor.getFontSize() - 1);
    }

    // ------------------------------------------------------------------ Lektionen

    /** Wechselt in den Lektionen-Tab und oeffnet die Lektion. */
    private void showLesson(Lesson l) {
        zeigeBereich(TAB_LEKTIONEN);
        if (!lessonModel.contains(l)) lessonFilter.setText("");
        lessonList.setSelectedValue(l, true);
    }

    /** Nach dem Laden eines Lernstands alles neu anzeigen, ohne den alten Code zurueckzuschreiben. */
    private void lernstandNeuLaden() {
        stopAll();
        current = null;
        ownCode = null;
        Lesson ziel = lessons.get(0);
        for (Lesson l : lessons) if (l.id().equals(store.get("last"))) ziel = l;
        lessonList.clearSelection();
        openLesson(ziel);
        lessonList.setSelectedValue(ziel, true);
        updateProgress();
        lessonList.repaint();
        dashboard.refresh();
        kartenPanel.refresh();
        notizPanel.refresh();
        repaint();
    }

    private void continueLessons() {
        Set<String> geloest = store.set("geloest");
        for (Lesson l : lessons) {
            if (l.aufgabe() != null && !geloest.contains(l.id())) {
                showLesson(l);
                return;
            }
        }
        zeigeBereich(TAB_LEKTIONEN);
    }

    private void openLesson(Lesson l) {
        if (stepper != null || running) stopAll();
        saveCurrentCode();
        boolean vorherNichtNotiert = current != null && !current.notizen().isEmpty()
                && store.set("geloest").contains(current.id()) && !store.set("notiert").contains(current.id());
        Lesson vorher = current;
        current = l;
        ownCode = null;
        previewBar.setVisible(false);
        covered = false;
        coverBtn.setSelected(false);
        tippIndex = 0;
        String saved = store.loadCode(l.id());
        editor.setText(saved != null ? saved : l.code());
        renderExplanation();
        solutionBtn.setEnabled(l.loesung() != null);
        tippBtn.setEnabled(!l.tipps().isEmpty());
        tippBtn.setText(l.tipps().isEmpty() ? "Tipp" : "Tipp (" + l.tipps().size() + " verfügbar)");
        nextBtn.setEnabled(lessons.indexOf(l) < lessons.size() - 1);
        notesBtn.setEnabled(!l.notizen().isEmpty());
        notedBox.setEnabled(!l.notizen().isEmpty());
        notedBox.setSelected(store.set("notiert").contains(l.id()));
        doneBox.setVisible(l.freieAufgabe());
        doneBox.setSelected(store.set("geloest").contains(l.id()));
        console.clear();
        console.info("Lektion „" + l.titel() + "“ geladen.\n");
        if (saved != null) console.info("Dein zuletzt bearbeiteter Code wurde wiederhergestellt.\n");
        console.info("Drücke Strg+Enter, um den Code zu starten.\n");
        if (vorherNichtNotiert) {
            console.hint("\nKleine Erinnerung: Zu „" + vorher.titel() + "“ fehlt dir noch die Handnotiz. "
                    + "Du findest sie im Tab Handnotizen.\n");
        }
        store.put("last", l.id());
        editor.focusEditor();
        SwingUtilities.invokeLater(() -> hint.setText(DEFAULT_HINT));
    }

    private void renderExplanation() {
        Lesson l = current;
        Markdown md = new Markdown();
        boolean solved = store.set("geloest").contains(l.id());
        StringBuilder h = new StringBuilder("<html><body>");
        h.append("<div class=kapitel>").append(Markdown.esc(l.kapitel())).append("</div>");
        h.append("<h1>").append(Markdown.esc(l.titel()));
        if (solved && l.aufgabe() != null) h.append(" <font color=" + Theme.H_GRUEN + ">&#10003; gelöst</font>");
        h.append("</h1>");
        if (covered) {
            h.append("<table width=100% cellpadding=14 cellspacing=0><tr><td bgcolor=" + Theme.H_BOX_GELB + ">")
                    .append("<h2>Stift raus: Was weißt du noch?</h2>")
                    .append("<p>Die Erklärung ist verdeckt. Schreib jetzt <b>von Hand</b> und in eigenen Worten alles auf, "
                            + "was du zu diesem Thema noch weißt.</p>")
                    .append("<p>Klicke danach wieder auf „Erklärung verdecken“, vergleiche und ergänze deine Lücken in einer anderen Farbe.</p>");
            if (!l.notizen().isEmpty()) {
                h.append("<p><b>Daran kannst du dich orientieren:</b></p><ul>");
                for (String n : l.notizen()) h.append("<li>").append(Markdown.inline(n)).append("</li>");
                h.append("</ul>");
            }
            h.append("</td></tr></table></body></html>");
            snippets = List.of();
            explain.setText(h.toString());
            explain.setCaretPosition(0);
            return;
        }
        if (l.aufgabe() != null) {
            h.append("<table width=100% cellpadding=10 cellspacing=0><tr><td bgcolor=" + Theme.H_BOX_GRUEN + ">")
                    .append("<h2>Deine Aufgabe</h2>")
                    .append(md.toHtml(l.aufgabe()))
                    .append("<p><font color=" + Theme.H_HINWEIS_GRUEN + ">Schreibe deine Lösung unten im Editor und drücke <b>Strg+Enter</b>. ");
            h.append(l.freieAufgabe() ? "Danach testest du selbst und setzt den Haken bei „Aufgabe erledigt“."
                    : "Die App prüft automatisch, ob die Ausgabe stimmt.");
            h.append(" Hängst du fest? Der Knopf <b>Tipp</b> hilft dir weiter.</font></p>")
                    .append("</td></tr></table>")
                    .append("<h2>Erklärung &amp; Beispiele</h2>");
        }
        h.append(md.toHtml(l.erklaerung()));
        if (!l.notizen().isEmpty()) {
            h.append("<a name=\"notizen\"></a><br><table width=100% cellpadding=12 cellspacing=0><tr><td bgcolor=" + Theme.H_BOX_GELB + ">")
                    .append("<h2>Stift raus: Das schreibst du von Hand auf</h2>")
                    .append("<p>Verdecke zuerst die Erklärung und schreib aus dem Kopf. Von Hand bleibt es am besten hängen.</p><ul>");
            for (String n : l.notizen()) h.append("<li>").append(Markdown.inline(n)).append("</li>");
            h.append("</ul></td></tr></table>");
        }
        h.append("<br></body></html>");
        snippets = md.snippets;
        explain.setText(h.toString());
        explain.setCaretPosition(0);
    }

    private void saveCurrentCode() {
        if (current == null) return;
        store.saveCode(current.id(), ownCode != null ? ownCode : editor.getText(), current.code());
    }

    /** Zeigt fremden Code an, ohne den eigenen Code der Lektion zu ueberschreiben. */
    private void beginPreview(String code, String text) {
        if (ownCode == null) ownCode = editor.getText();
        previewLabel.setText(text + "  Dein eigener Code ist sicher, Änderungen hier werden nicht gespeichert.");
        previewBar.setVisible(true);
        editor.setText(code);
    }

    private void endPreview() {
        if (ownCode == null) return;
        if (stepper != null || running) stopAll();
        String code = ownCode;
        ownCode = null;
        previewBar.setVisible(false);
        editor.setText(code);
        console.clear();
        console.info("Dein eigener Code ist wieder da.\n");
        editor.focusEditor();
    }

    private void loadSnippet(int idx) {
        if (idx < 0 || idx >= snippets.size() || running) return;
        beginPreview(wrap(snippets.get(idx)), "Beispiel aus der Erklärung.");
        runCode();
    }

    /** Macht aus einem Code-Schnipsel ein vollstaendiges Programm (falls noetig). */
    static String wrap(String s) {
        if (s.contains("class ") || s.contains("record ") || s.contains("enum ") || s.contains("interface ")) return s;
        StringBuilder b = new StringBuilder("import java.util.*;\nimport java.util.stream.*;\n\npublic class Main {\n"
                + "    public static void main(String[] args) {\n");
        for (String line : s.split("\n")) b.append(line.isBlank() ? "" : "        " + line).append('\n');
        b.append("    }\n}\n");
        return b.toString();
    }

    /** Laedt Code (z. B. aus dem Quiz) in den Spielplatz und fuehrt ihn aus. */
    private void tryInPlayground(String code) {
        if (running) stopAll();
        zeigeBereich(TAB_LEKTIONEN);
        if (!lessonModel.contains(lessons.get(0))) lessonFilter.setText("");
        lessonList.setSelectedValue(lessons.get(0), true);
        beginPreview(wrap(code), "Code aus dem Quiz.");
        runCode();
    }

    private void showNextHint() {
        if (current == null || current.tipps().isEmpty()) return;
        List<String> t = current.tipps();
        console.hint("\nTipp " + (tippIndex % t.size() + 1) + " von " + t.size() + ": "
                + t.get(tippIndex % t.size()).replace("`", "") + "\n");
        tippIndex++;
        tippBtn.setText(tippIndex < t.size() ? "Nächster Tipp (" + (tippIndex + 1) + "/" + t.size() + ")" : "Tipps nochmal");
    }

    private void resetCode() {
        if (current == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Code auf den Anfangszustand der Lektion zurücksetzen?\nMit Strg+Z im Editor kannst du das rückgängig machen.",
                "Zurücksetzen", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            ownCode = null;
            previewBar.setVisible(false);
            editor.replaceUndoable(current.code());
            console.clear();
            console.info("Code zurückgesetzt.\n");
        }
    }

    private void showSolution() {
        if (current == null || current.loesung() == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Hast du alle Tipps ausprobiert? Wer selbst auf die Lösung kommt, merkt sie sich viel besser.\n\n"
                        + "Lösung trotzdem ansehen? Dein eigener Code bleibt erhalten.",
                "Lösung zeigen", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            beginPreview(current.loesung(), "Musterlösung.");
            console.clear();
            console.info("Lies die Lösung und erkläre sie dir laut.\n");
            console.hint("Geh danach zurück zu deinem Code und schreib die Lösung aus dem Kopf neu. "
                    + "Erst dann zählt die Aufgabe als gelöst.\n");
        }
    }

    private void nextLesson() {
        int i = lessons.indexOf(current);
        if (i >= 0 && i + 1 < lessons.size()) showLesson(lessons.get(i + 1));
    }

    private void exportCode() {
        if (current == null) return;
        String code = ownCode != null ? ownCode : editor.getText();   // nur eigener Code wird exportiert
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)").matcher(code);
        String name = (m.find() ? m.group(1) : "Main") + ".java";
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(name));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " überschreiben?", "Speichern",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            Files.writeString(f.toPath(), code, StandardCharsets.UTF_8);
            console.info("\nGespeichert: " + f + "\n");
            if (!f.getName().equals(name)) {
                console.hint("Achtung: Eine public-Klasse muss in einer Datei mit ihrem Namen liegen: " + name + "\n");
            }
        } catch (IOException e) {
            console.err("\nSpeichern fehlgeschlagen: " + e.getMessage() + "\n");
        }
    }

    private void updateProgress() {
        long total = lessons.stream().filter(l -> l.aufgabe() != null).count();
        Set<String> solved = store.set("geloest");
        long done = lessons.stream().filter(l -> l.aufgabe() != null && solved.contains(l.id())).count();
        progressLabel.setText("Fortschritt: " + done + " / " + total + " Aufgaben gelöst");
        lessonList.repaint();
    }

    // ------------------------------------------------------------------ Ausfuehren

    private void runCode() {
        if (running) return;
        saveCurrentCode();
        store.markActive();
        editor.clearErrors();
        console.clear();
        console.info("Kompiliere und starte ...\n\n");
        setRunning(true);
        String code = editor.getText();
        Lesson lesson = ownCode == null ? current : null;   // Beispiele und Loesungen zaehlen nicht
        Thread t = new Thread(() -> {
            CodeRunner.Result r = runner.run(code, "", new CodeRunner.Listener() {
                public void out(String s) { SwingUtilities.invokeLater(() -> console.out(s)); }
                public void err(String s) { SwingUtilities.invokeLater(() -> console.err(s)); }
            }, true);
            SwingUtilities.invokeLater(() -> {
                setRunning(false);
                finished(r, lesson, code);
            });
        }, "code-runner");
        t.setDaemon(true);
        t.start();
    }

    private void setRunning(boolean r) {
        running = r;
        runBtn.setEnabled(!r);
        stepBtn.setEnabled(!r);
        stopBtn.setEnabled(r);
        input.setEnabled(r);
        input.setBackground(r ? Theme.BOX_GELB : UIManager.getColor("TextField.inactiveBackground"));
        inputLabel.setText(r
                ? "Programm läuft. Wartet es auf eine Eingabe? Hier eintippen und Enter drücken:"
                : "Eingabe (Scanner): wird aktiv, sobald dein Programm läuft.");
        if (r) focusInputTimer.restart();
        else {
            focusInputTimer.stop();
            if (input.isFocusOwner()) editor.focusEditor();
        }
    }

    private boolean showCompileProblems(CodeRunner.Result r) {
        if (!r.compiled) {
            if (r.errors.isEmpty()) {
                console.err(r.err.toString());
                return true;
            }
            console.err("✗ Kompilierfehler (" + r.errors.size() + "): Der Code konnte nicht übersetzt werden.\n\n");
            for (CodeRunner.CompileError e : r.errors) {
                console.err("Zeile " + e.line() + ": " + e.message() + "\n");
                console.hint("   Erklärung: " + Explanations.compiler(e.code(), e.message()) + "\n\n");
                editor.markErrorLine((int) e.line());
            }
            editor.goToLine((int) r.errors.get(0).line());
            return true;
        }
        if (r.noMain) {
            console.err("✗ Keine main-Methode gefunden.\n");
            console.hint("   Erklärung: Jedes Programm braucht einen Einstiegspunkt:\n"
                    + "   public static void main(String[] args) { ... }\n");
            return true;
        }
        return false;
    }

    private void finished(CodeRunner.Result r, Lesson lesson, String code) {
        if (showCompileProblems(r)) return;
        if (r.out.length() == 0 && r.err.length() == 0) console.info("(Das Programm hat nichts ausgegeben.)\n");
        console.info("\n");
        if (r.stopped) {
            console.hint("■ Programm gestoppt.\n");
            return;
        }
        if (r.timedOut) {
            console.err("✗ Abgebrochen: Das Programm hat mehr als " + CodeRunner.TIMEOUT_SECONDS
                    + " Sekunden gerechnet (oder lief länger als " + CodeRunner.MAX_INTERACTIVE_MINUTES + " Minuten).\n");
            console.hint("   Erklärung: Wahrscheinlich eine Endlosschleife. Prüfe, ob die Schleifenbedingung irgendwann "
                    + "false wird. Der Knopf „Schritt für Schritt“ zeigt dir, was passiert.\n");
            return;
        }
        if (r.outputLimit) {
            console.err("✗ Abgebrochen: Zu viel Ausgabe (mehr als " + CodeRunner.MAX_OUTPUT_CHARS + " Zeichen).\n");
            console.hint("   Erklärung: Vermutlich gibt eine Endlosschleife ununterbrochen etwas aus.\n");
            return;
        }
        if (r.exitCode != 0) {
            String[] ex = Explanations.runtime(r.err.toString());
            int line = Explanations.errorLine(r.err.toString(), r.fileName);
            if (ex != null) {
                console.err("✗ Laufzeitfehler: " + ex[0] + (line > 0 ? " in Zeile " + line : "") + "\n");
                console.hint("   Erklärung: " + ex[1] + "\n");
            } else {
                console.err("✗ Programm mit Exit-Code " + r.exitCode + " beendet.\n");
            }
            if (line > 0) editor.markErrorLine(line);
            return;
        }
        console.info("✓ Fertig in " + r.millis + " ms.\n");
        if (lesson != null && lesson.erwartet() != null && lesson == current) check(lesson, r.out.toString(), code);
    }

    static String normalize(String s) {
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(l.stripTrailing()).append('\n');
        return b.toString().strip();
    }

    /** @return null wenn ok, sonst Fehlermeldung */
    static String checkResult(Lesson l, String output, String code) {
        String got = normalize(output);
        String exp = normalize(l.erwartet());
        if (!got.equals(exp)) {
            String[] g = got.split("\n", -1), e = exp.split("\n", -1);
            for (int i = 0; i < Math.max(g.length, e.length); i++) {
                String gl = i < g.length ? g[i] : "(keine Zeile mehr)";
                String el = i < e.length ? e[i] : "(nichts mehr)";
                if (!gl.equals(el)) {
                    return "Ausgabe-Zeile " + (i + 1) + ":\n   erwartet  »" + el + "«\n   bekommen  »" + gl + "«";
                }
            }
            return "Die Ausgabe weicht ab.";
        }
        String stripped = CodeRunner.stripComments(code).replaceAll("\\s+", "");
        for (String m : l.muss()) {
            if (!stripped.contains(m.replaceAll("\\s+", ""))) {
                return "Die Ausgabe stimmt, aber benutze bitte »" + m + "« in deiner Lösung.";
            }
        }
        return null;
    }

    private void check(Lesson l, String output, String code) {
        String problem = checkResult(l, output, code);
        console.info("\n");
        if (problem == null) {
            boolean first = !store.set("geloest").contains(l.id());
            store.add("geloest", l.id());
            console.ok("★ Super, Aufgabe gelöst! ★\n");
            if (first) console.info("Weiter geht's mit „Nächste Lektion“, oder experimentiere noch ein bisschen.\n");
            if (!l.notizen().isEmpty() && !store.set("notiert").contains(l.id())) {
                console.hint("\nStift raus! Klicke auf „Erklärung verdecken“ und schreib aus dem Kopf:\n");
                for (String n : l.notizen()) console.hint("  • " + n.replace("`", "") + "\n");
                console.hint("Setze danach den Haken bei „Von Hand notiert“.\n");
            }
            updateProgress();
            renderExplanation();
        } else {
            console.hint("Noch nicht ganz:\n" + problem + "\n");
            console.info("\nErwartete Ausgabe:\n" + normalize(l.erwartet()) + "\n");
            if (!l.tipps().isEmpty()) console.info("\nDer Knopf „Tipp“ gibt dir einen Hinweis.\n");
        }
    }

    // ------------------------------------------------------------------ Schritt-fuer-Schritt-Modus

    private void startStepping() {
        if (running) return;
        saveCurrentCode();
        store.markActive();
        editor.clearErrors();
        console.clear();
        console.info("Schritt-Modus: Die blaue Zeile wird als Nächstes ausgeführt.\n"
                + "Geänderte Variablen werden gelb markiert.\n\n");
        setRunning(true);
        stepBar.setVisible(true);
        varScroll.setVisible(true);
        varModel.setRowCount(0);
        lastVars = new HashMap<>();
        changedVars.clear();
        stepInfo.setText("Startet ...");
        editor.setEditable(false);
        setStepButtons(false);
        varScroll.getParent().revalidate();
        String code = editor.getText();
        Thread t = new Thread(() -> {
            CodeRunner.Prepared p = runner.prepare(code);
            if (!p.result().compiled || p.result().noMain) {
                CodeRunner.deleteTree(p.dir());
                SwingUtilities.invokeLater(() -> {
                    leaveStepping();
                    showCompileProblems(p.result());
                });
                return;
            }
            StepDebugger d = new StepDebugger(new StepDebugger.Listener() {
                public void stopped(int line, List<String> stack, List<StepDebugger.Variable> vars) {
                    SwingUtilities.invokeLater(() -> showStop(line, stack, vars));
                }
                public void out(String s) { SwingUtilities.invokeLater(() -> console.out(s)); }
                public void err(String s) { SwingUtilities.invokeLater(() -> console.err(s)); }
                public void finished(int exit, boolean stopped) {
                    SwingUtilities.invokeLater(() -> {
                        leaveStepping();
                        console.info(stopped ? "\nSchritt-Modus beendet.\n" : "\nProgramm zu Ende.\n");
                    });
                }
            });
            SwingUtilities.invokeLater(() -> stepper = d);
            d.run(runner, p);
        }, "step-runner");
        t.setDaemon(true);
        t.start();
    }

    private void showStop(int line, List<String> stack, List<StepDebugger.Variable> vars) {
        if (stepper == null) return;
        stepWaiting = true;
        setStepButtons(true);
        editor.highlightStep(line);
        changedVars.clear();
        Map<String, String> jetzt = new HashMap<>();
        varModel.setRowCount(0);
        for (StepDebugger.Variable v : vars) {
            jetzt.put(v.name(), v.value());
            if (!v.value().equals(lastVars.get(v.name()))) changedVars.add(v.name());
            varModel.addRow(new Object[]{v.name(), v.type(), v.value()});
        }
        lastVars = jetzt;
        List<String> umgedreht = new ArrayList<>(stack);
        Collections.reverse(umgedreht);
        stepInfo.setText("Zeile " + line + "   ·   Aufrufe: " + String.join(" → ", umgedreht));
    }

    private void setStepButtons(boolean aktiv) {
        stepIntoBtn.setEnabled(aktiv);
        stepOverBtn.setEnabled(aktiv);
        stepRunBtn.setEnabled(aktiv);
    }

    /** 0 = naechster Schritt, 1 = Methode ueberspringen, 2 = bis zum Ende */
    private void stepCommand(int art) {
        if (stepper == null || !stepWaiting) return;
        stepWaiting = false;
        setStepButtons(false);
        switch (art) {
            case 0 -> stepper.stepInto();
            case 1 -> stepper.stepOver();
            default -> {
                stepAutoTimer.stop();
                stepAutoBtn.setSelected(false);
                editor.highlightStep(0);
                stepper.runToEnd();
            }
        }
    }

    private void leaveStepping() {
        stepper = null;
        stepWaiting = false;
        stepAutoTimer.stop();
        stepAutoBtn.setSelected(false);
        stepBar.setVisible(false);
        varScroll.setVisible(false);
        editor.highlightStep(0);
        editor.setEditable(true);
        setRunning(false);
        varScroll.getParent().revalidate();
    }

    // ------------------------------------------------------------------ Hilfsklassen

    static JEditorPane htmlPane() {
        JEditorPane p = new JEditorPane();
        p.setEditable(false);
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet ss = kit.getStyleSheet();
        ss.addRule("body { font-family: 'Segoe UI', sans-serif; font-size: 13pt; margin: 10px 18px; color: " + Theme.H_TEXT + "; }");
        ss.addRule("h1 { font-size: 24pt; margin-top: 4px; margin-bottom: 6px; color: " + Theme.H_UEBERSCHRIFT + "; }");
        ss.addRule("h2 { font-size: 18pt; margin-top: 12px; margin-bottom: 4px; color: " + Theme.H_UEBERSCHRIFT + "; }");
        ss.addRule("h3 { font-size: 16pt; margin-top: 10px; margin-bottom: 2px; color: " + Theme.H_TEXT + "; }");
        ss.addRule("p { margin-top: 4px; margin-bottom: 6px; }");
        ss.addRule("pre { font-family: Consolas, monospace; font-size: 15pt; background-color: " + Theme.H_CODE_BG + "; "
                + "padding: 8px; margin-top: 4px; margin-bottom: 2px; color: " + Theme.H_TEXT + "; }");
        ss.addRule("code { font-family: Consolas, monospace; font-size: 15pt; color: " + Theme.H_CODE_INLINE + "; background-color: " + Theme.H_CODE_BG + "; }");
        ss.addRule("li { margin-bottom: 3px; }");
        ss.addRule(".kapitel { color: " + Theme.H_GEDAEMPFT + "; font-size: 13pt; }");
        ss.addRule(".run { margin-top: 0px; margin-bottom: 10px; }");
        ss.addRule("a { color: " + Theme.H_AKZENT + "; font-weight: bold; }");
        ss.addRule("td { font-size: 16pt; }");
        p.setEditorKit(kit);
        p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        p.setFont(Theme.schrift("Segoe UI", Font.PLAIN, 16));
        p.setBackground(Theme.FLAECHE);
        p.setForeground(Theme.TEXT);
        return p;
    }

    private class LessonRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof Lesson l) {
                boolean solved = store.set("geloest").contains(l.id());
                String mark = l.aufgabe() == null ? "•" : solved ? "✓" : "○";
                c.setText("   " + mark + "  " + l.titel());
                c.setToolTipText(l.titel());
                c.setFont(c.getFont().deriveFont(Font.PLAIN, 14f));
                if (!sel && solved) c.setForeground(Theme.GRUEN);
                c.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            } else {
                c.setText(String.valueOf(value).toUpperCase());
                c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                c.setForeground(Theme.GEDAEMPFT);
                c.setBackground(list.getBackground());
                c.setBorder(BorderFactory.createEmptyBorder(14, 8, 4, 4));
            }
            return c;
        }
    }

    /** Ausgabe-Konsole mit Farben. */
    static class Console {
        private final JTextPane pane = new JTextPane();
        private final StyledDocument doc = pane.getStyledDocument();
        private final SimpleAttributeSet sOut = new SimpleAttributeSet(), sErr = new SimpleAttributeSet(),
                sInfo = new SimpleAttributeSet(), sHint = new SimpleAttributeSet(), sOk = new SimpleAttributeSet(),
                sInput = new SimpleAttributeSet();

        Console() {
            pane.setEditable(false);
            pane.setBackground(Theme.KONSOLE);
            pane.setMargin(new Insets(6, 10, 6, 10));
            StyleConstants.setForeground(sOut, Theme.TEXT);
            StyleConstants.setForeground(sErr, Theme.ROT);
            StyleConstants.setForeground(sInfo, Theme.GEDAEMPFT);
            StyleConstants.setForeground(sHint, Theme.GELB);
            StyleConstants.setForeground(sOk, Theme.GRUEN);
            StyleConstants.setBold(sOk, true);
            StyleConstants.setForeground(sInput, Theme.AKZENT);
            StyleConstants.setBold(sInput, true);
            setFontSize(15);
        }

        void setFontSize(int size) {
            for (SimpleAttributeSet s : List.of(sOut, sErr, sInfo, sHint, sOk, sInput)) {
                StyleConstants.setFontFamily(s, "Consolas");
                StyleConstants.setFontSize(s, size);
            }
            pane.setFont(Theme.schrift("Consolas", Font.PLAIN, size));
        }

        JScrollPane scroll() {
            JScrollPane sp = new JScrollPane(pane);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getVerticalScrollBar().setUnitIncrement(16);
            return sp;
        }

        void clear() { pane.setText(""); }

        String text() { return pane.getText(); }

        private void append(String s, AttributeSet a) {
            try {
                doc.insertString(doc.getLength(), s, a);
                pane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {
            }
        }

        void out(String s) { append(s, sOut); }
        void err(String s) { append(s, sErr); }
        void info(String s) { append(s, sInfo); }
        void hint(String s) { append(s, sHint); }
        void ok(String s) { append(s, sOk); }
        void input(String s) { append(s, sInput); }
    }

    // ------------------------------------------------------------------ Prueflauf ohne Fenster

    /**
     * Baut die Oberflaeche, ohne je ein Fenster zu zeigen, spielt die wichtigsten Ablaeufe durch
     * und speichert Bilder. Dabei wird nichts vom Lernstand veraendert.
     */
    private void pruefeOhneFenster(Path ordner) {
        fenster.unsichtbar = true;
        pack();
        setSize(1500, 950);
        Thread t = new Thread(() -> {
            int code = 1;
            try {
                Files.createDirectories(ordner);
                String[] namen = {"start", "lektion", "karten", "quiz", "probe", "notizen", "nachschlagen"};
                for (int i = 0; i < namen.length; i++) {
                    final int tab = i;
                    SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(tab));
                    bild(ordner, namen[i]);
                }

                Lesson schleifen = finde("schleifen");
                SwingUtilities.invokeAndWait(() -> showLesson(schleifen));
                bild(ordner, "lektion-aufgabe");

                SwingUtilities.invokeAndWait(() -> {
                    coverBtn.doClick();
                    showNextHint();
                });
                bild(ordner, "lektion-verdeckt");
                SwingUtilities.invokeAndWait(coverBtn::doClick);

                // Eigener Code muss einen Beispiel-Lauf ueberstehen.
                SwingUtilities.invokeAndWait(() -> {
                    editor.setText(schleifen.loesung());
                    runCode();
                });
                warte(() -> !running);
                pruefe(console.text().contains("Aufgabe gelöst"), "Aufgabe wird als gelöst erkannt");
                pruefe(console.text().contains("Stift raus"), "Handnotiz-Aufforderung nach der Lösung");
                bild(ordner, "lektion-geloest");

                String eigener = "// mein eigener Code\n" + schleifen.code();
                SwingUtilities.invokeAndWait(() -> {
                    editor.setText(eigener);
                    loadSnippet(0);
                });
                warte(() -> !running);
                pruefe(previewBar.isVisible(), "Beispielmodus ist aktiv");
                bild(ordner, "lektion-beispiel");
                SwingUtilities.invokeAndWait(this::endPreview);
                pruefe(editor.getText().equals(eigener), "Eigener Code bleibt nach dem Beispiel erhalten");

                // Schritt-Modus durch die Oberflaeche
                Lesson rek = finde("rekursion");
                SwingUtilities.invokeAndWait(() -> {
                    showLesson(rek);
                    editor.setText(rek.loesung());
                    startStepping();
                });
                warte(() -> stepWaiting);
                for (int i = 0; i < 6; i++) {
                    SwingUtilities.invokeAndWait(() -> stepCommand(0));
                    warte(() -> stepWaiting);
                }
                pruefe(varModel.getRowCount() > 0, "Schritt-Modus zeigt Variablen");
                bild(ordner, "schritt-modus");
                SwingUtilities.invokeAndWait(() -> stepCommand(2));
                warte(() -> !running);
                pruefe(console.text().contains("1024"), "Schritt-Modus läuft bis zum Ende");

                // Export nimmt nie die Musterloesung mit, Beispiele zaehlen nicht als geloest
                Lesson bank = finde("bank1");
                SwingUtilities.invokeAndWait(() -> {
                    showLesson(bank);
                    beginPreview(bank.loesung(), "Musterlösung.");
                    runCode();
                });
                warte(() -> !running);
                pruefe(!store.set("geloest").contains("bank1"), "Musterlösung zählt nicht als gelöst");
                pruefe(ownCode != null && ownCode.equals(bank.code()), "Eigener Code bleibt beim Export die Quelle");
                SwingUtilities.invokeAndWait(this::endPreview);

                // Karten mit Wiederholung im Abstand
                String frage = questions.get(0).get("frage");
                InterviewPanel.bewerte(store, frage, InterviewPanel.Note.GUT);
                pruefe(InterviewPanel.box(store, frage) == 1 && !InterviewPanel.istFaellig(store, frage), "Gewusste Karte wartet bis später");
                InterviewPanel.bewerte(store, frage, InterviewPanel.Note.NOCHMAL);
                pruefe(InterviewPanel.box(store, frage) == 0 && InterviewPanel.istFaellig(store, frage), "Nicht gewusste Karte kommt heute wieder");

                // Probe-Interview
                SwingUtilities.invokeAndWait(() -> {
                    tabs.setSelectedIndex(TAB_PROBE);
                    probePanel.testStart();
                });
                bild(ordner, "probe-station");
                int[] stationen = new int[1];
                SwingUtilities.invokeAndWait(() -> stationen[0] = probePanel.testDurchlauf());
                pruefe(stationen[0] == 8, "Probe-Interview hat 8 Stationen");
                bild(ordner, "probe-auswertung");

                // Suche
                int[] treffer = new int[1];
                SwingUtilities.invokeAndWait(() -> {
                    tabs.setSelectedIndex(TAB_NACHSCHLAGEN);
                    treffer[0] = nachschlagenPanel.sucheTest("hashmap");
                });
                pruefe(treffer[0] >= 3, "Suche findet Begriffe, Lektionen und Karten");
                bild(ordner, "nachschlagen-suche");

                // Startseite mit Termin
                store.put("interview", String.valueOf(java.time.LocalDate.now().plusDays(3).toEpochDay()));
                SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(TAB_START));
                bild(ordner, "start-termin");

                // Ganze Erklaerungen einiger Lektionen als lange Bilder
                for (String id : new String[]{"scanner", "schleifen", "hashmap"}) {
                    Lesson l = finde(id);
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            showLesson(l);
                            JEditorPane voll = htmlPane();
                            voll.setText(explain.getText());
                            voll.setSize(1100, 10);
                            int h = Math.min(9000, voll.getPreferredSize().height);
                            bildVon(voll, 1100, h, ordner, "erklaerung-" + id);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                // Mehrere Fenster: auslagern, verteilen, zurueckholen (Fenster werden gebaut, aber nie gezeigt)
                SwingUtilities.invokeAndWait(() -> {
                    showLesson(finde("schleifen"));
                    fenster.auslagern("tab" + TAB_NACHSCHLAGEN);
                    fenster.auslagern("erklaerung");
                });
                pruefe(SwingUtilities.getWindowAncestor(nachschlagenPanel) == fenster.frame("tab" + TAB_NACHSCHLAGEN),
                        "Nachschlagen liegt im eigenen Fenster");
                pruefe(tabs.getComponentAt(TAB_NACHSCHLAGEN) != nachschlagenPanel, "Tab zeigt den Platzhalter");
                pruefe(SwingUtilities.getWindowAncestor(explainScroll) == fenster.frame("erklaerung"),
                        "Erklärung liegt im eigenen Fenster");
                pruefe(SwingUtilities.isDescendingFrom(work, this), "Editor bleibt im Hauptfenster");
                SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(TAB_LEKTIONEN));
                bild(ordner, "fenster-lektion-ohne-erklaerung");
                SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(TAB_NACHSCHLAGEN));
                bild(ordner, "fenster-platzhalter");
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        bildVon((JComponent) fenster.frame("erklaerung").getContentPane(), 900, 700, ordner, "fenster-erklaerung");
                        JPopupMenu pm = fensterMenue.getPopupMenu();
                        Dimension d = pm.getPreferredSize();
                        bildVon(pm, d.width, d.height, ordner, "fenster-menue");
                        Map<String, Rectangle> verteilt = fenster.verteilen();
                        List<Rectangle> schirme = Fenster.bildschirme();
                        System.out.println("       Bildschirme: " + schirme.size() + ", verteilt: " + verteilt);
                        for (Map.Entry<String, Rectangle> e : verteilt.entrySet()) {
                            pruefe(schirme.stream().anyMatch(s -> s.contains(e.getValue())), "Fenster " + e.getKey() + " liegt ganz auf einem Bildschirm");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                SwingUtilities.invokeAndWait(() -> {
                    fenster.alleZurueckholen();
                    zeigeBereich(TAB_NACHSCHLAGEN);
                });
                pruefe(tabs.getComponentAt(TAB_NACHSCHLAGEN) == nachschlagenPanel, "Nachschlagen ist zurück im Tab");
                pruefe(SwingUtilities.isDescendingFrom(explainScroll, rightSplit), "Erklärung ist zurück über dem Editor");
                pruefe(tabs.getSelectedIndex() == TAB_NACHSCHLAGEN, "Navigation führt zum Tab");
                SwingUtilities.invokeAndWait(() -> {
                    fenster.auslagern("tab" + TAB_LEKTIONEN);
                    tabs.setSelectedIndex(TAB_START);
                    showLesson(finde("arrays"));
                });
                pruefe(current == finde("arrays") && tabs.getSelectedIndex() == TAB_START,
                        "Lektion öffnet sich im ausgelagerten Fenster");
                SwingUtilities.invokeAndWait(fenster::alleZurueckholen);
                SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(TAB_LEKTIONEN));
                bild(ordner, "fenster-alles-zurueck");

                // Dialoge einmal als Bild, ohne sie zu oeffnen
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JFileChooser fc = new JFileChooser();
                        fc.setDialogType(JFileChooser.SAVE_DIALOG);
                        bildVon(fc, 760, 480, ordner, "dialog-speichern");
                        bildVon(new JOptionPane("Bitte im Format TT.MM.JJJJ eingeben, z. B. 24.11.2026."), 520, 130, ordner, "dialog-meldung");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                System.out.println("PRUEFLAUF OK");
                code = 0;
            } catch (Throwable e) {
                System.out.println("PRUEFLAUF FEHLER: " + e);
                e.printStackTrace(System.out);
            }
            System.exit(code);
        }, "prueflauf");
        t.start();
    }

    private Lesson finde(String id) {
        for (Lesson l : lessons) if (l.id().equals(id)) return l;
        throw new IllegalArgumentException(id);
    }

    private static void pruefe(boolean ok, String was) {
        System.out.println((ok ? "[ OK ] " : "[FAIL] ") + was);
        if (!ok) throw new IllegalStateException(was);
    }

    private interface Bedingung {
        boolean erfuellt();
    }

    private void warte(Bedingung b) throws Exception {
        long ende = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < ende) {
            boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(() -> ok[0] = b.erfuellt());
            if (ok[0]) return;
            Thread.sleep(50);
        }
        throw new IllegalStateException("Zeitüberschreitung");
    }

    private void bild(Path ordner, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> {
            try {
                JComponent inhalt = (JComponent) getContentPane();
                inhalt.setSize(getWidth(), getHeight());
                layoutAlles(inhalt);
                BufferedImage img = new BufferedImage(inhalt.getWidth(), inhalt.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                inhalt.printAll(g);
                g.dispose();
                ImageIO.write(img, "png", ordner.resolve(name + ".png").toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void bildVon(JComponent k, int w, int h, Path ordner, String name) throws IOException {
        k.setSize(w, h);
        layoutAlles(k);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Theme.FENSTER);
        g.fillRect(0, 0, w, h);
        k.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", ordner.resolve(name + ".png").toFile());
    }

    private static void layoutAlles(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof Container kc) layoutAlles(kc);
    }

    // ------------------------------------------------------------------ Start

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--selftest")) {
            SelfTest.run();
            return;
        }
        boolean prueflauf = args.length > 1 && args[0].equals("--prueflauf");
        if (prueflauf) Content.Store.readOnly = true;
        SwingUtilities.invokeLater(() -> {
            try {
                Theme.install();
            } catch (Exception ignored) {
            }
            JComponent.setDefaultLocale(Locale.GERMAN);   // Dialoge wie "Speichern unter" auf Deutsch
            if (!prueflauf && !Content.Store.einzigeInstanz()) {
                JOptionPane.showMessageDialog(null,
                        "Die Java-Lern-Sandbox ist schon geöffnet. Schau in der Taskleiste nach.\n"
                                + "Zwei Fenster würden sich gegenseitig den Fortschritt überschreiben.",
                        "Schon geöffnet", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }
            try {
                JavaLernApp app = new JavaLernApp();
                if (prueflauf) {
                    app.pruefeOhneFenster(Paths.get(args[1]));
                    return;
                }
                app.fenster.wiederherstellen();
                app.setVisible(true);
                app.fenster.offeneWiederOeffnen();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Start fehlgeschlagen: " + e, "Fehler", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
