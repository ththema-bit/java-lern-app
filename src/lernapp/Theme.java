package lernapp;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;

/**
 * Dunkles, augenschonendes Farbschema der ganzen App.
 * Kein reines Schwarz oder Weiss und gedaempfte Akzentfarben. Alle Farben stehen nur hier.
 */
public final class Theme {

    private Theme() {
    }

    // Flaechen, von dunkel nach hell
    static final Color KONSOLE = new Color(0x191c20);
    static final Color EDITOR = new Color(0x1d2026);
    static final Color FENSTER = new Color(0x1e2127);
    static final Color FLAECHE = new Color(0x23272e);
    static final Color ERHOEHT = new Color(0x2c313a);
    static final Color HOVER = new Color(0x363c47);
    static final Color LINIE = new Color(0x3a404b);
    static final Color AUSWAHL = new Color(0x34404f);

    // Text
    static final Color TEXT = new Color(0xd3d7de);
    static final Color TEXT_HELL = new Color(0xe4e7ec);
    static final Color GEDAEMPFT = new Color(0x8d95a3);
    static final Color UEBERSCHRIFT = new Color(0xa7c2e6);

    // Akzente
    static final Color AKZENT = new Color(0x7aa2d6);
    static final Color FORTSCHRITT = new Color(0x466388);
    static final Color GRUEN = new Color(0x86c49a);
    static final Color ROT = new Color(0xe39393);
    static final Color GELB = new Color(0xe3c48a);

    // Getoente Kaesten
    static final Color BOX_GELB = new Color(0x332e24);
    static final Color BOX_GRUEN = new Color(0x26322b);
    static final Color BOX_BLAU = new Color(0x26303f);
    static final Color GEAENDERT = new Color(0x4a4026);

    // Code-Editor
    static final Color RANDLEISTE = new Color(0x23272e);
    static final Color ZEILENNUMMER = new Color(0x6f7785);
    static final Color MARKIERUNG = new Color(0x3a4a5e);
    static final Color FEHLERZEILE = new Color(0x4e2a2e);
    static final Color FEHLERRAND = new Color(0x9c4a4a);
    static final Color SCHRITTZEILE = new Color(0x2d4a66);
    static final Color SCHRITTRAND = new Color(0x4d7ba8);
    static final Color CODE_KEYWORD = new Color(0x8fb4de);
    static final Color CODE_TYP = new Color(0x7fc4b6);
    static final Color CODE_TEXT = new Color(0xd4a58a);
    static final Color CODE_KOMMENTAR = new Color(0x7d8a78);
    static final Color CODE_ZAHL = new Color(0xb8cfa8);
    static final Color CODE_ANNOTATION = new Color(0xd6cfa0);

    // Dieselben Farben fuer HTML-Texte
    static final String H_TEXT = hex(TEXT);
    static final String H_GEDAEMPFT = hex(GEDAEMPFT);
    static final String H_UEBERSCHRIFT = hex(UEBERSCHRIFT);
    static final String H_GRUEN = hex(GRUEN);
    static final String H_ROT = hex(ROT);
    static final String H_AKZENT = hex(AKZENT);
    static final String H_BOX_GELB = hex(BOX_GELB);
    static final String H_BOX_GRUEN = hex(BOX_GRUEN);
    static final String H_FLAECHE = hex(FLAECHE);
    static final String H_ERHOEHT = hex(ERHOEHT);
    static final String H_LINIE = hex(LINIE);
    static final String H_CODE_BG = hex(EDITOR);
    static final String H_CODE_INLINE = "#e0a8c0";
    static final String H_HINWEIS_GRUEN = "#9db8a5";

    /** Schrift, die fehlende Zeichen wie Haken oder Pfeile aus anderen Schriften ergaenzt. */
    static Font schrift(String name, int stil, int groesse) {
        return javax.swing.text.StyleContext.getDefaultStyleContext().getFont(name, stil, groesse);
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xffffff);
    }

    /** Muss vor dem Erzeugen des ersten Swing-Bausteins aufgerufen werden. */
    static void install() throws Exception {
        MetalLookAndFeel.setCurrentTheme(new Farben());
        UIManager.setLookAndFeel(new MetalLookAndFeel());

        String paket = Theme.class.getName();
        UIManager.put("ButtonUI", paket + "$KnopfUI");
        UIManager.put("ToggleButtonUI", paket + "$KnopfUI");
        UIManager.put("ScrollBarUI", paket + "$RollbalkenUI");
        UIManager.put("TabbedPaneUI", paket + "$TabUI");
        UIManager.put("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");
        UIManager.put("ProgressBarUI", "javax.swing.plaf.basic.BasicProgressBarUI");

        farbe("Panel.background", FENSTER);
        farbe("Label.foreground", TEXT);
        farbe("Label.disabledForeground", GEDAEMPFT);
        farbe("OptionPane.background", FENSTER);
        farbe("OptionPane.messageForeground", TEXT);

        farbe("Button.background", ERHOEHT);
        farbe("Button.foreground", TEXT);
        farbe("ToggleButton.background", ERHOEHT);
        farbe("ToggleButton.foreground", TEXT);
        Border knopfRand = BorderFactory.createEmptyBorder(7, 14, 7, 14);
        UIManager.put("Button.border", knopfRand);
        UIManager.put("ToggleButton.border", knopfRand);

        farbe("CheckBox.background", FENSTER);
        farbe("CheckBox.foreground", TEXT);
        farbe("CheckBox.focus", LINIE);
        UIManager.put("CheckBox.icon", new HakenIcon());

        farbe("TabbedPane.background", FENSTER);
        farbe("TabbedPane.foreground", GEDAEMPFT);
        farbe("TabbedPane.selectedForeground", TEXT_HELL);
        UIManager.put("TabbedPane.opaque", Boolean.TRUE);

        farbe("ScrollBar.background", FENSTER);
        farbe("ScrollBar.track", FENSTER);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.minimumThumbSize", new Dimension(28, 28));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        farbe("ScrollPane.background", FLAECHE);
        farbe("Viewport.background", FLAECHE);

        farbe("SplitPane.background", LINIE);
        farbe("SplitPaneDivider.draggingColor", HOVER);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 5);

        farbe("List.background", FLAECHE);
        farbe("List.foreground", TEXT);
        farbe("List.selectionBackground", AUSWAHL);
        farbe("List.selectionForeground", TEXT_HELL);
        Border listenRand = BorderFactory.createEmptyBorder(3, 8, 3, 8);
        UIManager.put("List.cellNoFocusBorder", listenRand);
        UIManager.put("List.focusCellHighlightBorder", listenRand);
        UIManager.put("List.focusSelectedCellHighlightBorder", listenRand);

        farbe("Table.background", FLAECHE);
        farbe("Table.foreground", TEXT);
        farbe("Table.gridColor", LINIE);
        farbe("Table.selectionBackground", AUSWAHL);
        farbe("Table.selectionForeground", TEXT_HELL);
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder(1, 1, 1, 1));
        UIManager.put("Table.scrollPaneBorder", BorderFactory.createEmptyBorder());
        farbe("TableHeader.background", ERHOEHT);
        farbe("TableHeader.foreground", TEXT);
        UIManager.put("TableHeader.cellBorder", BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, LINIE),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        for (String typ : new String[]{"TextField", "FormattedTextField", "PasswordField", "TextArea", "TextPane", "EditorPane"}) {
            farbe(typ + ".background", FLAECHE);
            farbe(typ + ".foreground", TEXT);
            farbe(typ + ".caretForeground", TEXT_HELL);
            farbe(typ + ".selectionBackground", MARKIERUNG);
            farbe(typ + ".selectionForeground", TEXT_HELL);
            farbe(typ + ".inactiveBackground", FENSTER);
            farbe(typ + ".inactiveForeground", GEDAEMPFT);
        }
        Border feldRand = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINIE),
                BorderFactory.createEmptyBorder(4, 6, 4, 6));
        UIManager.put("TextField.border", feldRand);
        UIManager.put("FormattedTextField.border", feldRand);

        farbe("ProgressBar.background", ERHOEHT);
        farbe("ProgressBar.foreground", FORTSCHRITT);
        farbe("ProgressBar.selectionBackground", TEXT);
        farbe("ProgressBar.selectionForeground", TEXT_HELL);
        UIManager.put("ProgressBar.border", BorderFactory.createEmptyBorder());

        farbe("ToolTip.background", HOVER);
        farbe("ToolTip.foreground", TEXT_HELL);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINIE),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        // Menues
        UIManager.put("MenuBar.gradient", null);
        UIManager.put("MenuItem.gradient", null);
        UIManager.put("MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, LINIE));
        farbe("MenuBar.background", FENSTER);
        farbe("MenuBar.foreground", TEXT);
        for (String typ : new String[]{"Menu", "MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem"}) {
            farbe(typ + ".background", FLAECHE);
            farbe(typ + ".foreground", TEXT);
            farbe(typ + ".selectionBackground", AUSWAHL);
            farbe(typ + ".selectionForeground", TEXT_HELL);
            farbe(typ + ".disabledForeground", GEDAEMPFT);
            farbe(typ + ".acceleratorForeground", GEDAEMPFT);
            UIManager.put(typ + ".border", BorderFactory.createEmptyBorder(6, 10, 6, 14));
        }
        UIManager.put("Menu.font", new FontUIResource(schrift("Segoe UI", Font.PLAIN, 14)));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(LINIE));
        farbe("PopupMenu.background", FLAECHE);
        farbe("Separator.foreground", LINIE);
        farbe("Separator.background", FLAECHE);
        farbe("PopupMenuSeparator.foreground", LINIE);
        farbe("PopupMenuSeparator.background", FLAECHE);

        farbe("ComboBox.background", ERHOEHT);
        farbe("ComboBox.foreground", TEXT);
        farbe("ComboBox.selectionBackground", AUSWAHL);
        farbe("ComboBox.selectionForeground", TEXT_HELL);
    }

    private static void farbe(String key, Color c) {
        UIManager.put(key, new ColorUIResource(c));
    }

    private static Graphics2D glatt(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    /** Grundfarben und Schriften fuer alles, was nicht einzeln gesetzt ist, z. B. den Speichern-Dialog. */
    private static final class Farben extends DefaultMetalTheme {
        private static final FontUIResource NORMAL = new FontUIResource(schrift("Segoe UI", Font.PLAIN, 14));
        private static final FontUIResource KLEIN = new FontUIResource(schrift("Segoe UI", Font.PLAIN, 13));

        @Override public String getName() { return "Dunkel"; }
        @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(AKZENT); }
        @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(AKZENT); }
        @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(AUSWAHL); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(LINIE); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(ERHOEHT); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(FENSTER); }
        @Override protected ColorUIResource getBlack() { return new ColorUIResource(TEXT); }
        @Override protected ColorUIResource getWhite() { return new ColorUIResource(FLAECHE); }
        @Override public ColorUIResource getInactiveControlTextColor() { return new ColorUIResource(GEDAEMPFT); }
        @Override public ColorUIResource getInactiveSystemTextColor() { return new ColorUIResource(GEDAEMPFT); }
        @Override public ColorUIResource getMenuDisabledForeground() { return new ColorUIResource(GEDAEMPFT); }
        @Override public FontUIResource getControlTextFont() { return NORMAL; }
        @Override public FontUIResource getSystemTextFont() { return NORMAL; }
        @Override public FontUIResource getUserTextFont() { return NORMAL; }
        @Override public FontUIResource getMenuTextFont() { return NORMAL; }
        @Override public FontUIResource getWindowTitleFont() { return NORMAL; }
        @Override public FontUIResource getSubTextFont() { return KLEIN; }
    }

    /** Flacher Knopf mit runden Ecken; auch fuer Umschaltknoepfe. */
    public static final class KnopfUI extends BasicButtonUI {
        private static final KnopfUI INSTANZ = new KnopfUI();

        public static ComponentUI createUI(JComponent c) {
            return INSTANZ;
        }

        @Override
        protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
            LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Graphics2D g2 = glatt(g);
            int w = c.getWidth() - 1, h = c.getHeight() - 1;
            Color flaeche;
            if (!m.isEnabled()) flaeche = FLAECHE;
            else if (m.isArmed() && m.isPressed()) flaeche = LINIE;
            else if (m.isSelected()) flaeche = AUSWAHL;
            else if (m.isRollover()) flaeche = HOVER;
            else flaeche = ERHOEHT;
            g2.setColor(flaeche);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            if (m.isSelected() || (b.isFocusPainted() && b.isFocusOwner())) {
                g2.setColor(AKZENT);
                g2.drawRoundRect(0, 0, w, h, 8, 8);
            }
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            if (c.isEnabled()) {
                super.paintText(g, c, textRect, text);
                return;
            }
            g.setColor(GEDAEMPFT);
            BasicGraphicsUtils.drawString(c, (Graphics2D) g, text, textRect.x,
                    textRect.y + c.getFontMetrics(c.getFont()).getAscent());
        }
    }

    /** Schmaler Scrollbalken ohne Pfeile. */
    public static final class RollbalkenUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) {
            return new RollbalkenUI();
        }

        private static JButton keinKnopf() {
            JButton b = new JButton();
            Dimension null0 = new Dimension(0, 0);
            b.setPreferredSize(null0);
            b.setMinimumSize(null0);
            b.setMaximumSize(null0);
            return b;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return keinKnopf(); }
        @Override protected JButton createIncreaseButton(int orientation) { return keinKnopf(); }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(FENSTER);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = glatt(g);
            g2.setColor(isDragging || isThumbRollover() ? GEDAEMPFT : LINIE);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    /** Ruhige Reiter: der aktive ist nur heller und unten farbig unterstrichen. */
    public static final class TabUI extends BasicTabbedPaneUI {
        public static ComponentUI createUI(JComponent c) {
            return new TabUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(9, 8, 9, 8);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(6, 10, 0, 10);
            contentBorderInsets = new Insets(1, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
            if (selected) g.setColor(FLAECHE);
            else if (index == getRolloverTab()) g.setColor(ERHOEHT);
            else return;
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
            if (!selected) return;
            g.setColor(AKZENT);
            g.fillRect(x, y + h - 2, w, 2);
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index,
                                           Rectangle icon, Rectangle text, boolean selected) {
            if (!selected || !tabPane.isFocusOwner()) return;
            g.setColor(AKZENT);
            g.drawRect(text.x - 3, text.y, text.width + 5, text.height);
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            g.setColor(LINIE);
            int y = calculateTabAreaHeight(placement, runCount, maxTabHeight);
            g.fillRect(0, y, tabPane.getWidth(), 1);
        }
    }

    /** Haekchen im selben ruhigen Stil. */
    private static final class HakenIcon implements Icon {
        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel m = ((AbstractButton) c).getModel();
            Graphics2D g2 = glatt(g);
            g2.setColor(m.isSelected() ? FORTSCHRITT : m.isRollover() ? HOVER : ERHOEHT);
            g2.fillRoundRect(x + 1, y + 1, 15, 15, 5, 5);
            g2.setColor(m.isSelected() ? AKZENT : GEDAEMPFT);
            g2.drawRoundRect(x + 1, y + 1, 15, 15, 5, 5);
            if (m.isSelected()) {
                g2.setColor(TEXT_HELL);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 5, y + 9, x + 8, y + 12);
                g2.drawLine(x + 8, y + 12, x + 13, y + 5);
            }
            g2.dispose();
        }
    }
}
