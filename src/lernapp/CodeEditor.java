package lernapp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Code-Editor mit Syntax-Highlighting, Zeilennummern, Auto-Einrueckung und Fehlermarkierung. */
class CodeEditor extends JPanel {

    static final Color BG = Theme.EDITOR;
    static final Color FG = Theme.TEXT;

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits"));

    private static final Pattern TOKENS = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$))"
                    + "|(?<string>\"(?:[^\"\\\\\\n]|\\\\.)*\"?|'(?:[^'\\\\\\n]|\\\\.)*'?)"
                    + "|(?<anno>@\\w+)"
                    + "|(?<num>\\b\\d[\\d_]*(?:\\.\\d+)?[lLfFdD]?\\b)"
                    + "|(?<word>\\b[A-Za-z_$][\\w$]*\\b)");

    private final JTextPane pane = new JTextPane() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            Component p = getParent();
            return p == null || getUI().getPreferredSize(this).width <= p.getSize().width;
        }
    };
    private final StyledDocument doc = pane.getStyledDocument();
    private final UndoManager undo = new UndoManager();
    private final javax.swing.Timer highlightTimer;
    private final LineNumbers lineNumbers = new LineNumbers();
    private final Set<Integer> errorLines = new HashSet<>();
    private final List<Object> errorTags = new ArrayList<>();
    private final DefaultHighlighter.DefaultHighlightPainter errorPainter =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.FEHLERZEILE);
    private final DefaultHighlighter.DefaultHighlightPainter stepPainter =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.SCHRITTZEILE);
    private Object stepTag;
    private int stepLine;
    private int fontSize = 16;
    private SimpleAttributeSet sNormal, sKeyword, sType, sString, sComment, sNumber, sAnno;
    private Consumer<String> wordListener = w -> {};

    CodeEditor() {
        super(new BorderLayout());
        pane.setBackground(BG);
        pane.setForeground(FG);
        pane.setCaretColor(Theme.TEXT_HELL);
        pane.setSelectionColor(Theme.MARKIERUNG);
        pane.setSelectedTextColor(Theme.TEXT_HELL);
        pane.setMargin(new Insets(6, 8, 6, 8));
        buildStyles();

        JScrollPane sp = new JScrollPane(pane);
        sp.setRowHeaderView(lineNumbers);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);

        highlightTimer = new javax.swing.Timer(120, e -> highlight());
        highlightTimer.setRepeats(false);

        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        doc.addUndoableEditListener(e -> {
            // Farb-Aenderungen (Highlighting) nicht in die Rueckgaengig-Liste aufnehmen
            if (e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent dde
                    && dde.getType() == DocumentEvent.EventType.CHANGE) return;
            undo.addEdit(e.getEdit());
        });
        pane.addCaretListener(e -> wordListener.accept(wordAtCaret()));
        installKeys();
    }

    private void changed() {
        highlightTimer.restart();
        lineNumbers.revalidate();
        lineNumbers.repaint();
    }

    private void buildStyles() {
        Font f = Theme.schrift("Consolas", Font.PLAIN, fontSize);
        pane.setFont(f);
        sNormal = style(FG, false, false);
        sKeyword = style(Theme.CODE_KEYWORD, true, false);
        sType = style(Theme.CODE_TYP, false, false);
        sString = style(Theme.CODE_TEXT, false, false);
        sComment = style(Theme.CODE_KOMMENTAR, false, true);
        sNumber = style(Theme.CODE_ZAHL, false, false);
        sAnno = style(Theme.CODE_ANNOTATION, false, false);
        Style def = doc.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(def, "Consolas");
        StyleConstants.setFontSize(def, fontSize);
        StyleConstants.setForeground(def, FG);
        TabStop[] stops = new TabStop[40];
        int tabW = pane.getFontMetrics(f).charWidth(' ') * 4;
        for (int i = 0; i < stops.length; i++) stops[i] = new TabStop((i + 1) * tabW);
        StyleConstants.setTabSet(def, new TabSet(stops));
    }

    private SimpleAttributeSet style(Color c, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setFontFamily(s, "Consolas");
        StyleConstants.setFontSize(s, fontSize);
        StyleConstants.setForeground(s, c);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        return s;
    }

    private void highlight() {
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }
        doc.setCharacterAttributes(0, text.length(), sNormal, true);
        Matcher m = TOKENS.matcher(text);
        while (m.find()) {
            AttributeSet s = null;
            if (m.group("comment") != null) s = sComment;
            else if (m.group("string") != null) s = sString;
            else if (m.group("anno") != null) s = sAnno;
            else if (m.group("num") != null) s = sNumber;
            else {
                String w = m.group("word");
                if (KEYWORDS.contains(w)) s = sKeyword;
                else if (Character.isUpperCase(w.charAt(0))) s = sType;
            }
            if (s != null) doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, false);
        }
        lineNumbers.repaint();
    }

    private void installKeys() {
        InputMap im = pane.getInputMap();
        ActionMap am = pane.getActionMap();
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        im.put(KeyStroke.getKeyStroke("ENTER"), "smartEnter");
        am.put("smartEnter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                String text = getText();
                int pos = Math.min(pane.getSelectionStart(), text.length());
                int ls = text.lastIndexOf('\n', pos - 1) + 1;
                String line = text.substring(ls, pos);
                String indent = line.substring(0, line.length() - line.stripLeading().length());
                String extra = line.trim().endsWith("{") ? "    " : "";
                boolean closing = pos < text.length() && text.charAt(pos) == '}' && !extra.isEmpty();
                if (closing) {
                    pane.replaceSelection("\n" + indent + extra + "\n" + indent);
                    pane.setCaretPosition(pos + 1 + indent.length() + extra.length());
                } else {
                    pane.replaceSelection("\n" + indent + extra);
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("TAB"), "fourSpaces");
        am.put("fourSpaces", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (pane.getSelectedText() != null && pane.getSelectedText().contains("\n")) indent(true);
                else pane.replaceSelection("    ");
            }
        });
        im.put(KeyStroke.getKeyStroke("shift TAB"), "unindent");
        am.put("unindent", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { indent(false); }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_7, menu | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "comment");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SLASH, menu), "comment");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DIVIDE, menu), "comment");
        am.put("comment", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggleComment(); }
        });
        pane.setFocusTraversalKeysEnabled(false);

        im.put(KeyStroke.getKeyStroke('}'), "closeBrace");
        am.put("closeBrace", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                String text = getText();
                int pos = pane.getSelectionStart();
                int ls = text.lastIndexOf('\n', pos - 1) + 1;
                String before = text.substring(ls, pos);
                if (pane.getSelectionEnd() == pos && before.length() >= 4 && before.isBlank()) {
                    try {
                        doc.remove(pos - 4, 4);
                    } catch (BadLocationException ignored) {
                    }
                }
                pane.replaceSelection("}");
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, menu), "undoIt");
        am.put("undoIt", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canUndo()) undo.undo();
                } catch (CannotUndoException ignored) {
                }
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, menu), "redoIt");
        am.put("redoIt", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undo.canRedo()) undo.redo();
                } catch (CannotRedoException ignored) {
                }
            }
        });
    }

    String getText() {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    /** Ersetzt den Text komplett (ohne Rueckgaengig-Moeglichkeit), z. B. beim Lektionswechsel. */
    void setText(String code) {
        clearErrors();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, code.replace("\r\n", "\n"), sNormal);
        } catch (BadLocationException ignored) {
        }
        undo.discardAllEdits();
        highlight();
        pane.setCaretPosition(0);
    }

    /** Ersetzt den Text so, dass Strg+Z den alten Stand zurueckholt. */
    void replaceUndoable(String code) {
        clearErrors();
        pane.selectAll();
        pane.replaceSelection(code.replace("\r\n", "\n"));
        highlight();
        pane.setCaretPosition(0);
    }

    void changeFontSize(int delta) {
        fontSize = Math.max(10, Math.min(32, fontSize + delta));
        buildStyles();
        highlight();
        lineNumbers.revalidate();
    }

    int getFontSize() { return fontSize; }

    void setWordListener(Consumer<String> l) { wordListener = l; }

    void focusEditor() { pane.requestFocusInWindow(); }

    void markErrorLine(int line) {
        Element root = doc.getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return;
        errorLines.add(line);
        Element el = root.getElement(line - 1);
        try {
            errorTags.add(pane.getHighlighter().addHighlight(el.getStartOffset(),
                    Math.max(el.getStartOffset(), el.getEndOffset() - 1), errorPainter));
        } catch (BadLocationException ignored) {
        }
        lineNumbers.repaint();
    }

    /** Markiert im Schritt-Modus die Zeile, die als Naechstes ausgefuehrt wird (0 = keine). */
    void highlightStep(int line) {
        if (stepTag != null) {
            pane.getHighlighter().removeHighlight(stepTag);
            stepTag = null;
        }
        stepLine = line;
        Element root = doc.getDefaultRootElement();
        if (line >= 1 && line <= root.getElementCount()) {
            Element el = root.getElement(line - 1);
            try {
                stepTag = pane.getHighlighter().addHighlight(el.getStartOffset(),
                        Math.max(el.getStartOffset(), el.getEndOffset() - 1), stepPainter);
                pane.setCaretPosition(el.getStartOffset());
            } catch (BadLocationException ignored) {
            }
        }
        lineNumbers.repaint();
    }

    void setEditable(boolean editable) {
        pane.setEditable(editable);
    }

    /** Strg+/ schaltet Kommentare fuer die markierten Zeilen um. */
    private void toggleComment() {
        int[] b = selectedLines();
        boolean alle = true;
        Element root = doc.getDefaultRootElement();
        String text = getText();
        for (int i = b[0]; i <= b[1]; i++) {
            Element el = root.getElement(i);
            String line = text.substring(el.getStartOffset(), Math.min(el.getEndOffset(), text.length()));
            if (!line.isBlank() && !line.stripLeading().startsWith("//")) alle = false;
        }
        try {
            for (int i = b[1]; i >= b[0]; i--) {
                Element el = root.getElement(i);
                int start = el.getStartOffset();
                String line = text.substring(start, Math.min(el.getEndOffset(), text.length()));
                int einzug = line.length() - line.stripLeading().length();
                if (alle) {
                    if (line.stripLeading().startsWith("//")) {
                        int laenge = line.stripLeading().startsWith("// ") ? 3 : 2;
                        doc.remove(start + einzug, laenge);
                    }
                } else if (!line.isBlank()) {
                    doc.insertString(start + einzug, "// ", sComment);
                }
            }
        } catch (BadLocationException ignored) {
        }
    }

    /** Tab und Shift+Tab ruecken markierte Zeilen ein oder aus. */
    private void indent(boolean mehr) {
        int[] b = selectedLines();
        Element root = doc.getDefaultRootElement();
        String text = getText();
        try {
            for (int i = b[1]; i >= b[0]; i--) {
                int start = root.getElement(i).getStartOffset();
                if (mehr) {
                    doc.insertString(start, "    ", sNormal);
                } else {
                    int n = 0;
                    while (n < 4 && start + n < text.length() && text.charAt(start + n) == ' ') n++;
                    if (n > 0) doc.remove(start, n);
                }
            }
        } catch (BadLocationException ignored) {
        }
    }

    private int[] selectedLines() {
        Element root = doc.getDefaultRootElement();
        int von = root.getElementIndex(pane.getSelectionStart());
        int bis = root.getElementIndex(Math.max(pane.getSelectionStart(), pane.getSelectionEnd() - 1));
        return new int[]{von, Math.max(von, bis)};
    }

    void goToLine(int line) {
        Element root = doc.getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return;
        pane.setCaretPosition(root.getElement(line - 1).getStartOffset());
        pane.requestFocusInWindow();
    }

    void clearErrors() {
        for (Object t : errorTags) pane.getHighlighter().removeHighlight(t);
        errorTags.clear();
        errorLines.clear();
        lineNumbers.repaint();
    }

    private String wordAtCaret() {
        String text = getText();
        int pos = Math.min(pane.getCaretPosition(), text.length());
        int s = pos, e = pos;
        while (s > 0 && Character.isJavaIdentifierPart(text.charAt(s - 1))) s--;
        while (e < text.length() && Character.isJavaIdentifierPart(text.charAt(e))) e++;
        return s < e ? text.substring(s, e) : "";
    }

    /** Zeilennummern am linken Rand. */
    private class LineNumbers extends JComponent {
        LineNumbers() {
            setOpaque(true);
            setBackground(Theme.RANDLEISTE);
        }

        @Override
        public Dimension getPreferredSize() {
            int lines = doc.getDefaultRootElement().getElementCount();
            FontMetrics fm = getFontMetrics(pane.getFont());
            int w = fm.stringWidth(String.valueOf(Math.max(100, lines))) + 18;
            return new Dimension(w, pane.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            g.setColor(getBackground());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setFont(pane.getFont());
            FontMetrics fm = g.getFontMetrics();
            Element root = doc.getDefaultRootElement();
            for (int i = 0; i < root.getElementCount(); i++) {
                try {
                    Rectangle2D r = pane.modelToView2D(root.getElement(i).getStartOffset());
                    if (r == null) continue;
                    int y = (int) r.getY();
                    int h = (int) r.getHeight();
                    if (y + h < clip.y) continue;
                    if (y > clip.y + clip.height) break;
                    String num = String.valueOf(i + 1);
                    if (i + 1 == stepLine) {
                        g.setColor(Theme.SCHRITTRAND);
                        g.fillRect(0, y, getWidth(), h);
                        g.setColor(Theme.TEXT_HELL);
                    } else if (errorLines.contains(i + 1)) {
                        g.setColor(Theme.FEHLERRAND);
                        g.fillRect(0, y, getWidth(), h);
                        g.setColor(Theme.TEXT_HELL);
                    } else {
                        g.setColor(Theme.ZEILENNUMMER);
                    }
                    g.drawString(num, getWidth() - fm.stringWidth(num) - 8, y + fm.getAscent()
                            + (h - fm.getHeight()) / 2);
                } catch (BadLocationException ignored) {
                }
            }
        }
    }
}
