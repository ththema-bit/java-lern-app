package lernapp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lagert Bereiche der App in eigene Fenster aus, damit man sie auf mehrere Bildschirme
 * verteilen kann. Merkt sich, welche Fenster offen waren und wo sie standen.
 */
final class Fenster {

    /** Ein Bereich, der in ein eigenes Fenster wandern kann. */
    private static final class Bereich {
        final String id, titel;
        final JComponent inhalt;
        final Runnable herausnehmen, zurueckstellen, beimAktivieren;
        JFrame frame;

        Bereich(String id, String titel, JComponent inhalt, Runnable herausnehmen,
                Runnable zurueckstellen, Runnable beimAktivieren) {
            this.id = id;
            this.titel = titel;
            this.inhalt = inhalt;
            this.herausnehmen = herausnehmen;
            this.zurueckstellen = zurueckstellen;
            this.beimAktivieren = beimAktivieren;
        }
    }

    private final JFrame haupt;
    private final Content.Store store;
    private final Consumer<JRootPane> tastenkuerzel;
    private final Map<String, Bereich> bereiche = new LinkedHashMap<>();
    /** Im Pruefllauf werden Fenster gebaut, aber nie gezeigt. */
    boolean unsichtbar;
    /** Wird nach jedem Aus- oder Einlagern aufgerufen, z. B. um Knopftexte anzupassen. */
    Runnable geaendert = () -> { };

    Fenster(JFrame haupt, Content.Store store, Consumer<JRootPane> tastenkuerzel) {
        this.haupt = haupt;
        this.store = store;
        this.tastenkuerzel = tastenkuerzel;
    }

    void registriere(String id, String titel, JComponent inhalt, Runnable herausnehmen,
                     Runnable zurueckstellen, Runnable beimAktivieren) {
        bereiche.put(id, new Bereich(id, titel, inhalt, herausnehmen, zurueckstellen, beimAktivieren));
    }

    List<String> ids() { return new ArrayList<>(bereiche.keySet()); }

    String titel(String id) { return bereiche.get(id).titel; }

    boolean istAusgelagert(String id) {
        Bereich b = bereiche.get(id);
        return b != null && b.frame != null;
    }

    JFrame frame(String id) { return bereiche.get(id).frame; }

    void umschalten(String id) {
        if (istAusgelagert(id)) zurueckholen(id);
        else auslagern(id);
    }

    /** Oeffnet den Bereich in einem eigenen Fenster neben dem Hauptfenster. */
    void auslagern(String id) {
        Bereich b = bereiche.get(id);
        if (b == null || b.frame != null) return;
        b.herausnehmen.run();
        JFrame f = new JFrame("Java Lern-Sandbox · " + b.titel);
        f.setIconImages(haupt.getIconImages());
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { zurueckholen(id); }

            @Override
            public void windowActivated(WindowEvent e) { b.beimAktivieren.run(); }
        });
        JPanel inhalt = new JPanel(new BorderLayout());
        inhalt.add(b.inhalt, BorderLayout.CENTER);
        f.setContentPane(inhalt);
        tastenkuerzel.accept(f.getRootPane());
        b.frame = f;

        Rectangle gespeichert = gespeicherteGrenzen(id);
        if (gespeichert != null) {
            f.setBounds(gespeichert);
        } else {
            f.setSize(1000, 800);
            Rectangle h = haupt.getBounds();
            f.setLocation(h.x + 60, h.y + 60);
        }
        if (!unsichtbar) {
            f.setVisible(true);
            if (gespeichert != null && "max".equals(gespeicherterZustand(id))) f.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
        b.inhalt.revalidate();
        b.beimAktivieren.run();
        speichern();
        geaendert.run();
    }

    /** Schliesst das eigene Fenster und setzt den Bereich wieder an seinen alten Platz. */
    void zurueckholen(String id) {
        Bereich b = bereiche.get(id);
        if (b == null || b.frame == null) return;
        merkeGrenzen(id, b.frame);
        JFrame f = b.frame;
        b.frame = null;
        f.getContentPane().remove(b.inhalt);
        f.dispose();
        b.zurueckstellen.run();
        b.inhalt.revalidate();
        b.inhalt.repaint();
        speichern();
        geaendert.run();
    }

    void alleZurueckholen() {
        for (String id : ids()) zurueckholen(id);
    }

    /** Holt einen ausgelagerten Bereich nach vorn. */
    void zeige(String id) {
        JFrame f = frame(id);
        if (f == null) return;
        if ((f.getExtendedState() & Frame.ICONIFIED) != 0) f.setExtendedState(f.getExtendedState() & ~Frame.ICONIFIED);
        f.toFront();
        f.requestFocus();
    }

    // ------------------------------------------------------------------ Bildschirme

    /** Nutzbare Flaechen aller Bildschirme, ohne Taskleiste. */
    static List<Rectangle> bildschirme() {
        List<Rectangle> r = new ArrayList<>();
        Toolkit tk = Toolkit.getDefaultToolkit();
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            Insets i = tk.getScreenInsets(gc);
            r.add(new Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom));
        }
        r.sort(Comparator.comparingInt((Rectangle a) -> a.x).thenComparingInt(a -> a.y));
        return r;
    }

    private static Rectangle bildschirmVon(Rectangle fenster, List<Rectangle> schirme) {
        Point mitte = new Point(fenster.x + fenster.width / 2, fenster.y + fenster.height / 2);
        for (Rectangle s : schirme) if (s.contains(mitte)) return s;
        return schirme.get(0);
    }

    /**
     * Verteilt die ausgelagerten Fenster auf die Bildschirme, auf denen das Hauptfenster nicht steht.
     * Gibt es nur einen Bildschirm, teilen sich alle Fenster ihn nebeneinander.
     * @return Bildschirm je Fenster-ID (fuer den Pruefllauf)
     */
    Map<String, Rectangle> verteilen() {
        List<Rectangle> schirme = bildschirme();
        Rectangle hauptSchirm = bildschirmVon(haupt.getBounds(), schirme);
        List<Rectangle> andere = new ArrayList<>(schirme);
        andere.remove(hauptSchirm);
        List<Bereich> offen = new ArrayList<>();
        for (Bereich b : bereiche.values()) if (b.frame != null) offen.add(b);
        // Die Erklaerung kommt zuerst, also auf den linken Bildschirm: Man liest von links nach rechts.
        offen.sort(Comparator.comparingInt(b -> b.id.equals("erklaerung") ? 0 : 1));
        Map<String, Rectangle> ziel = new LinkedHashMap<>();
        if (offen.isEmpty()) return ziel;

        if (andere.isEmpty()) {
            // Nur ein Bildschirm: Hauptfenster links, die anderen rechts uebereinander
            Rectangle s = hauptSchirm;
            int links = s.width * 55 / 100;
            setzeGrenzen(haupt, new Rectangle(s.x, s.y, links, s.height), false);
            int h = s.height / offen.size();
            for (int i = 0; i < offen.size(); i++) {
                Rectangle r = new Rectangle(s.x + links, s.y + i * h, s.width - links, h);
                setzeGrenzen(offen.get(i).frame, r, false);
                ziel.put(offen.get(i).id, r);
            }
        } else {
            // Je Bildschirm ein Fenster; sind es mehr Fenster, teilen sie sich die Bildschirme nebeneinander
            Map<Rectangle, List<Bereich>> proSchirm = new LinkedHashMap<>();
            for (int i = 0; i < offen.size(); i++) {
                proSchirm.computeIfAbsent(andere.get(i % andere.size()), k -> new ArrayList<>()).add(offen.get(i));
            }
            for (Map.Entry<Rectangle, List<Bereich>> e : proSchirm.entrySet()) {
                Rectangle s = e.getKey();
                List<Bereich> liste = e.getValue();
                int w = s.width / liste.size();
                for (int i = 0; i < liste.size(); i++) {
                    Rectangle r = new Rectangle(s.x + i * w, s.y, w, s.height);
                    setzeGrenzen(liste.get(i).frame, r, liste.size() == 1);
                    ziel.put(liste.get(i).id, r);
                }
            }
            if (!unsichtbar) {
                haupt.toFront();
            }
        }
        speichern();
        return ziel;
    }

    private void setzeGrenzen(JFrame f, Rectangle r, boolean maximieren) {
        f.setExtendedState(Frame.NORMAL);
        f.setBounds(r);
        if (maximieren && !unsichtbar) f.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    // ------------------------------------------------------------------ Merken

    private void merkeGrenzen(String id, JFrame f) {
        boolean max = (f.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        Rectangle r = f.getBounds();
        store.put("fenster." + id, r.x + "," + r.y + "," + r.width + "," + r.height + "," + (max ? "max" : "normal"));
    }

    /** Speichert, welche Bereiche ausgelagert sind und wo alle Fenster stehen. */
    void speichern() {
        if (Content.Store.readOnly) return;
        if (haupt.isShowing()) merkeGrenzen("haupt", haupt);
        List<String> offen = new ArrayList<>();
        for (Bereich b : bereiche.values()) {
            if (b.frame == null) continue;
            offen.add(b.id);
            if (b.frame.isShowing()) merkeGrenzen(b.id, b.frame);
        }
        store.put("fenster.offen", String.join(",", offen));
    }

    private String[] gespeichert(String id) {
        String s = store.get("fenster." + id);
        if (s == null) return null;
        String[] t = s.split(",");
        return t.length == 5 ? t : null;
    }

    private String gespeicherterZustand(String id) {
        String[] t = gespeichert(id);
        return t == null ? null : t[4];
    }

    /** @return gespeicherte Lage, aber nur, wenn sie noch auf einem vorhandenen Bildschirm liegt */
    private Rectangle gespeicherteGrenzen(String id) {
        String[] t = gespeichert(id);
        if (t == null) return null;
        try {
            Rectangle r = new Rectangle(Integer.parseInt(t[0]), Integer.parseInt(t[1]),
                    Integer.parseInt(t[2]), Integer.parseInt(t[3]));
            if (r.width < 300 || r.height < 200) return null;
            for (Rectangle s : bildschirme()) {
                Rectangle schnitt = s.intersection(r);
                if (!schnitt.isEmpty() && schnitt.width >= 200 && schnitt.height >= 100) return r;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /** Stellt Hauptfenster und ausgelagerte Fenster so wieder her, wie sie beim letzten Mal standen. */
    void wiederherstellen() {
        Rectangle h = gespeicherteGrenzen("haupt");
        if (h != null) {
            haupt.setExtendedState(Frame.NORMAL);
            haupt.setBounds(h);
            haupt.setExtendedState("max".equals(gespeicherterZustand("haupt")) ? Frame.MAXIMIZED_BOTH : Frame.NORMAL);
        }
    }

    void offeneWiederOeffnen() {
        String offen = store.get("fenster.offen");
        if (offen == null || offen.isBlank()) return;
        for (String id : offen.split(",")) if (bereiche.containsKey(id.trim())) auslagern(id.trim());
        haupt.toFront();
    }
}
