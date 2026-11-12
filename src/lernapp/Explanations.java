package lernapp;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deutsche Erklaerungen fuer Compilerfehler, Laufzeitfehler und Schluesselwoerter. */
final class Explanations {

    private Explanations() {}

    static String compiler(String code, String msg) {
        String c = code == null ? "" : code;
        String m = msg == null ? "" : msg.toLowerCase();
        if (c.contains("cant.resolve"))
            return "Java kennt diesen Namen nicht. Häufige Ursachen: Tippfehler, falsche Groß-/Kleinschreibung "
                    + "(Java unterscheidet 'string' und 'String'!), die Variable wurde nicht oder in einem anderen "
                    + "Block { } deklariert, oder ein import fehlt (z. B. import java.util.*;).";
        if (c.contains("premature.eof"))
            return "Die Datei endet, bevor alle Blöcke geschlossen sind – es fehlt mindestens eine schließende "
                    + "geschweifte Klammer }. Zähle die { und } durch.";
        if (c.startsWith("compiler.err.expected") && m.contains("class, interface"))
            return "Hier steht Code außerhalb einer Klasse – oft eine schließende } zu viel weiter oben.";
        if (c.startsWith("compiler.err.expected"))
            return "Hier fehlt ein Zeichen – meistens ein Semikolon ; am Ende der Anweisung oder eine Klammer ) / }. "
                    + "Tipp: Der Fehler wird oft in der Zeile NACH der eigentlichen Stelle gemeldet.";
        if (c.contains("prob.found.req") && (m.contains("lossy") || m.contains("verlust")))
            return "Mögliche verlustbehaftete Umwandlung: du willst z. B. einen double in einer int-Variable speichern. "
                    + "Java macht das nicht automatisch, weil Nachkommastellen verloren gehen. Lösung: Cast, z. B. (int) wert.";
        if (c.contains("prob.found.req") || c.contains("incompatible"))
            return "Die Typen passen nicht zusammen, z. B. ein String in einer int-Variable. Prüfe den Datentyp links "
                    + "vom = und den Wert rechts. String -> int: Integer.parseInt(text). int -> String: String.valueOf(zahl).";
        if (c.contains("might.not.have.been.initialized"))
            return "Die Variable hat noch keinen Wert. Lokale Variablen müssen vor der ersten Benutzung "
                    + "einen Wert bekommen, z. B. int summe = 0;";
        if (c.contains("missing.ret.stmt"))
            return "Die Methode verspricht einen Rückgabewert (sie ist nicht void), aber nicht jeder Weg endet mit return. "
                    + "Auch hinter if/else muss in jedem Fall etwas zurückgegeben werden.";
        if (c.contains("already.defined"))
            return "Dieser Name ist im selben Bereich schon vergeben – zwei Variablen (oder Methoden mit gleichen "
                    + "Parametern) dürfen nicht gleich heißen.";
        if (c.contains("class.public.should.be.in.file"))
            return "Pro Datei darf nur EINE Klasse public sein (die, nach der die Datei benannt ist). "
                    + "Entferne das 'public' bei den anderen Klassen.";
        if (c.contains("unreported.exception"))
            return "Checked Exception! Diese Methode kann eine geprüfte Exception werfen. Du musst sie entweder mit "
                    + "try { ... } catch (...) { ... } behandeln oder mit 'throws ...' in der Methodensignatur weitergeben.";
        if (c.contains("illegal.start"))
            return "Java versteht die Struktur an dieser Stelle nicht. Oft: Anweisung außerhalb einer Methode, "
                    + "eine Methode innerhalb einer Methode, oder eine fehlende/überzählige Klammer davor.";
        if (c.contains("not.stmt"))
            return "Das ist keine vollständige Anweisung – z. B. nur ein Wert ohne Zuweisung (x + 1;) "
                    + "oder ein Vergleich == wo eine Zuweisung = gemeint war.";
        if (c.contains("unclosed.str.lit"))
            return "Ein String wurde nicht geschlossen – das schließende Anführungszeichen \" fehlt. "
                    + "Normale Strings dürfen nicht über mehrere Zeilen gehen.";
        if (c.contains("char.lit"))
            return "char-Werte stehen in einfachen Anführungszeichen und enthalten genau EIN Zeichen: 'a'. "
                    + "Für Text nimmst du doppelte Anführungszeichen: \"Hallo\" (String).";
        if (c.contains("non-static.cant.be.ref"))
            return "Du greifst aus einer static-Methode (z. B. main) auf etwas zu, das zu einem Objekt gehört. "
                    + "Entweder Feld/Methode auch static machen oder erst ein Objekt erzeugen: new Klasse().methode().";
        if (c.contains("cant.apply.symbol") || c.contains("cant.apply.symbols"))
            return "Die Methode bzw. der Konstruktor wird mit den falschen Argumenten aufgerufen – Anzahl oder Typen "
                    + "passen nicht zur Definition. Vergleiche Aufruf und Methodenkopf.";
        if (c.contains("does.not.override.abstract"))
            return "Die Klasse implementiert nicht alle abstrakten Methoden ihres Interfaces bzw. ihrer abstrakten "
                    + "Oberklasse. Implementiere die fehlende Methode (gleicher Name, gleiche Parameter) – oder mach die Klasse abstract.";
        if (c.contains("report.access") || c.contains("not.def.public"))
            return "Zugriff verweigert: Das Element ist private (oder sonst nicht sichtbar). Von außen greift man über "
                    + "öffentliche Methoden wie Getter/Setter darauf zu – das ist Kapselung.";
        if (c.contains("unreachable.stmt"))
            return "Dieser Code kann nie ausgeführt werden, z. B. weil direkt davor return, break oder throw steht.";
        if (c.contains("cant.assign.val"))
            return "Einer final-Variable kann nach der ersten Zuweisung kein neuer Wert mehr gegeben werden.";
        if (c.contains("incomparable.types"))
            return "Diese beiden Typen kann man nicht mit == vergleichen.";
        if (c.contains("operator.cant.be.applied"))
            return "Dieser Operator passt nicht zu den Typen (z. B. String - int oder boolean > int). "
                    + "Strings vergleicht man mit equals() bzw. compareTo(), nicht mit < oder >.";
        if (c.contains("else.without.if"))
            return "Zu diesem else gibt es kein passendes if – oft ein Semikolon direkt nach if (...); "
                    + "oder eine falsch gesetzte Klammer.";
        if (c.contains("abstract.cant.be.instantiated"))
            return "Von abstrakten Klassen und Interfaces kann man mit new kein Objekt erzeugen – nur von "
                    + "konkreten Unterklassen, die alle Methoden implementieren.";
        if (c.contains("missing.meth.body"))
            return "Die Methode hat keinen Rumpf { ... }. Oft steht ein Semikolon direkt hinter dem Methodenkopf.";
        if (c.contains("except.already.caught"))
            return "Reihenfolge der catch-Blöcke: speziellere Exceptions (Unterklassen) müssen VOR den allgemeineren "
                    + "stehen, sonst wäre der spätere catch-Block nie erreichbar.";
        if (c.contains("except.never.thrown"))
            return "Diese (checked) Exception kann im try-Block gar nicht auftreten, deshalb ist der catch-Block unnötig.";
        if (c.contains("call.must.be.first") || m.contains("must be first statement"))
            return "super(...) bzw. this(...) muss die allererste Anweisung im Konstruktor sein.";
        if (c.contains("cant.deref"))
            return "Auf primitive Typen (int, double, boolean ...) kann man keine Methoden mit . aufrufen. "
                    + "Nutze z. B. String.valueOf(x) oder die Wrapper-Klasse Integer.";
        if (c.contains("invalid.meth.decl.ret.type.req"))
            return "Methoden brauchen einen Rückgabetyp (void, int, String ...). Falls es ein Konstruktor sein soll, "
                    + "muss er exakt wie die Klasse heißen.";
        if (c.contains("var.not.initialized.in.default.constructor") || c.contains("var.might.already.be.assigned"))
            return "Eine final-Variable muss genau einmal einen Wert bekommen – meistens im Konstruktor.";
        if (c.contains("does.not.override.superclass") || c.contains("method.does.not.override"))
            return "@Override steht an einer Methode, die gar nichts überschreibt. Prüfe Name und Parameter "
                    + "auf Tippfehler – genau dafür ist @Override da!";
        if (c.contains("no.compiler"))
            return "Bitte die App mit einem JDK starten (start.bat benutzt das installierte JDK).";
        return "Lies die Meldung genau: Sie nennt die Zeile (rot markiert). Schau auch in die Zeile davor – "
                + "der eigentliche Fehler liegt oft ein Stück früher.";
    }

    private static final Pattern EXC = Pattern.compile(
            "Exception in thread \"main\" ([\\w.$]+)(?::\\s?([^\\n]*))?");

    /** @return {Name der Exception, Erklaerung} oder null */
    static String[] runtime(String stderr) {
        Matcher m = EXC.matcher(stderr);
        if (!m.find()) return null;
        String full = m.group(1);
        String detail = m.group(2) == null ? "" : m.group(2);
        String name = full.substring(full.lastIndexOf('.') + 1);
        String text = switch (name) {
            case "ArithmeticException" -> detail.contains("zero")
                    ? "Division durch 0! Bei Ganzzahlen (int, long) ist das verboten. (Bei double ergibt x/0.0 übrigens Infinity.)"
                    : "Ein Rechenfehler ist aufgetreten.";
            case "ArrayIndexOutOfBoundsException" ->
                    "Du greifst auf einen Array-Index zu, den es nicht gibt. Indizes gehen von 0 bis länge-1! "
                            + "Klassiker: for (int i = 0; i <= arr.length; i++) – richtig wäre i < arr.length.";
            case "StringIndexOutOfBoundsException" ->
                    "Index außerhalb des Strings (z. B. bei charAt oder substring). Gültig sind 0 bis length()-1.";
            case "IndexOutOfBoundsException" ->
                    "Index außerhalb der Liste. Gültig sind 0 bis size()-1.";
            case "NullPointerException" ->
                    "Du benutzt eine Referenz, die null ist (zeigt auf kein Objekt) – z. B. Methode auf einem null-String "
                            + "aufgerufen oder Objekt nie mit new erzeugt. Die Meldung oben verrät, welche Variable null war.";
            case "NumberFormatException" ->
                    "Der Text lässt sich nicht in eine Zahl umwandeln (z. B. Integer.parseInt(\"abc\")). "
                            + "Prüfe die Eingabe oder fange die Exception mit try/catch ab.";
            case "ClassCastException" ->
                    "Ungültiger Cast: Das Objekt ist nicht vom Typ, in den du es umwandeln willst. Prüfe vorher mit instanceof.";
            case "StackOverflowError" ->
                    "Der Aufruf-Stack ist übergelaufen – fast immer eine Rekursion ohne (erreichbare) Abbruchbedingung.";
            case "OutOfMemoryError" ->
                    "Der Speicher ist voll (Sandbox-Limit 256 MB). Erzeugst du in einer Schleife endlos Objekte?";
            case "InputMismatchException" ->
                    "Der Scanner hat einen anderen Typ erwartet, z. B. nextInt(), aber im Eingabefeld steht Text.";
            case "NoSuchElementException" -> detail.contains("No line found") || full.contains("util")
                    ? "Es gibt nichts (mehr) zu lesen – der Scanner wollte eine Eingabe, aber der Eingabestrom ist zu Ende."
                    : "Das gesuchte Element existiert nicht.";
            case "ConcurrentModificationException" ->
                    "Du veränderst eine Collection, während du mit for-each darüber läufst. Nutze stattdessen "
                            + "removeIf(...) oder einen Iterator mit it.remove().";
            case "UnsupportedOperationException" ->
                    "Diese Operation ist nicht erlaubt – z. B. add/remove auf einer unveränderlichen Liste aus List.of(...) "
                            + "oder Arrays.asList(...). Lösung: new ArrayList<>(List.of(...)).";
            case "IllegalArgumentException" -> "Einer Methode wurde ein ungültiges Argument übergeben.";
            case "IllegalStateException" -> "Das Objekt ist für diese Operation im falschen Zustand.";
            case "AccessControlException", "SecurityException" ->
                    "Die Sandbox hat das verhindert: Dateizugriff, Netzwerk und Programme starten sind hier "
                            + "zu deinem Schutz gesperrt. Das ist kein Fehler in deinem Java-Wissen!";
            default -> "Ein Laufzeitfehler (Exception) ist aufgetreten. Lies die erste Zeile: Name der Exception + Meldung. "
                    + "Darunter steht der Stack-Trace – die erste Zeile mit deiner Datei zeigt, wo es passiert ist.";
        };
        return new String[]{name, text};
    }

    static int errorLine(String stderr, String fileName) {
        Matcher m = Pattern.compile("\\(" + Pattern.quote(fileName) + ":(\\d+)\\)").matcher(stderr);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static final Map<String, String> GLOSSAR = new HashMap<>();

    private static void g(String text, String... words) {
        for (String w : words) GLOSSAR.put(w, text);
    }

    static {
        g("Sichtbarkeit: überall sichtbar.", "public");
        g("Sichtbarkeit: nur innerhalb der eigenen Klasse sichtbar (Kapselung).", "private");
        g("Sichtbarkeit: im selben Package und in Unterklassen sichtbar.", "protected");
        g("Gehört zur Klasse, nicht zu einem einzelnen Objekt. Aufruf ohne new: Klasse.methode(). Alle Objekte teilen sich static-Felder.", "static");
        g("Rückgabetyp 'nichts': die Methode gibt keinen Wert zurück.", "void");
        g("Definiert eine Klasse – den Bauplan für Objekte (Felder + Methoden).", "class");
        g("Ein Vertrag: legt fest, WELCHE Methoden eine Klasse haben muss. Klassen 'implementieren' ihn (implements). Mehrere Interfaces pro Klasse möglich.", "interface");
        g("Vererbung: die Klasse übernimmt Felder und Methoden der Oberklasse. In Java nur EINE Oberklasse.", "extends");
        g("Die Klasse erfüllt ein Interface und muss dessen Methoden implementieren.", "implements");
        g("Erzeugt ein neues Objekt auf dem Heap und ruft den Konstruktor auf.", "new");
        g("Beendet die Methode und gibt (optional) einen Wert zurück.", "return");
        g("Variable: nur einmal zuweisbar. Methode: nicht überschreibbar. Klasse: nicht vererbbar (z. B. String).", "final");
        g("Abstrakte Klasse/Methode: unvollständig, kann nicht mit new erzeugt werden – Unterklassen müssen abstrakte Methoden implementieren.", "abstract");
        g("Verweis auf das aktuelle Objekt. this.name = name unterscheidet Feld und Parameter.", "this");
        g("Verweis auf die Oberklasse: super(...) ruft deren Konstruktor, super.methode() deren Methode auf.", "super");
        g("'Kein Objekt'. Eine Referenz, die auf nichts zeigt. Methodenaufruf auf null -> NullPointerException.", "null");
        g("boolean-Wert (wahr/falsch).", "true", "false");
        g("Bedingung: führt Code nur aus, wenn die Bedingung true ist.", "if");
        g("Alternative, wenn die if-Bedingung false ist.", "else");
        g("Zählschleife: for (Start; Bedingung; Schritt). For-each: for (Typ x : sammlung).", "for");
        g("Schleife, die läuft, solange die Bedingung true ist (Prüfung vorher). do { } while (...) prüft nachher.", "while", "do");
        g("Mehrfachverzweigung nach einem Wert. Neu: switch-Ausdruck mit -> (kein break nötig).", "switch", "case");
        g("Bricht die aktuelle Schleife bzw. den switch ab.", "break");
        g("Springt direkt zum nächsten Schleifendurchlauf.", "continue");
        g("Im switch: Fall für alle übrigen Werte. Im Interface: Methode mit Standard-Implementierung.", "default");
        g("try: Code, der eine Exception werfen könnte. catch: behandelt die Exception. finally: läuft IMMER.", "try", "catch", "finally");
        g("Wirft eine Exception: throw new IllegalArgumentException(\"...\");", "throw");
        g("In der Methodensignatur: diese Methode kann die genannte (checked) Exception weitergeben.", "throws");
        g("Ganzzahl, 32 Bit (ca. ±2,1 Milliarden). Standardtyp für ganze Zahlen.", "int");
        g("Ganzzahl, 64 Bit – für sehr große Zahlen. Literal mit L: 10_000_000_000L", "long");
        g("Kommazahl, 64 Bit (doppelte Genauigkeit). Achtung: 0.1 + 0.2 ist nicht exakt 0.3! Für Geld: BigDecimal.", "double");
        g("Kommazahl, 32 Bit. Literal mit f: 3.14f", "float");
        g("Wahrheitswert: true oder false.", "boolean");
        g("Ein einzelnes Zeichen in einfachen Anführungszeichen: 'A'. Intern eine Zahl (Unicode).", "char");
        g("Ganzzahl 8 Bit (-128 bis 127) bzw. 16 Bit.", "byte", "short");
        g("Text. Ein Objekt (kein primitiver Typ) und unveränderlich (immutable). Vergleich mit equals(), nicht mit ==!", "String");
        g("Zugriff auf Systemfunktionen. System.out = Standardausgabe (Konsole).", "System");
        g("Gibt Text aus und macht danach einen Zeilenumbruch. print() ohne Umbruch.", "println", "print");
        g("Formatierte Ausgabe: printf(\"%d Stück, %.2f Euro%n\", anzahl, preis). %d=Ganzzahl, %s=Text, %.2f=2 Nachkommastellen.", "printf", "format");
        g("Einstiegspunkt jedes Programms: public static void main(String[] args).", "main");
        g("Kommandozeilen-Argumente als String-Array.", "args");
        g("Liest Eingaben (hier aus dem Eingabefeld): nextLine(), nextInt(), ...", "Scanner");
        g("Dynamisches Array: wächst automatisch. Schneller Zugriff per Index (O(1)).", "ArrayList");
        g("Interface für geordnete Sammlungen mit Index, Duplikate erlaubt. Implementierungen: ArrayList, LinkedList.", "List");
        g("Schlüssel-Wert-Paare (Key -> Value), Keys sind eindeutig. HashMap (unsortiert, O(1)), TreeMap (sortiert), LinkedHashMap (Einfügereihenfolge).", "Map", "HashMap", "TreeMap", "LinkedHashMap");
        g("Sammlung ohne Duplikate. HashSet (unsortiert), TreeSet (sortiert), LinkedHashSet (Einfügereihenfolge).", "Set", "HashSet", "TreeSet", "LinkedHashSet");
        g("Wrapper-Klasse für int (Objekt). Nötig in Collections: List<Integer>. Integer.parseInt(\"42\") wandelt Text in Zahl.", "Integer");
        g("Wrapper-Klasse für primitive Typen (Autoboxing).", "Double", "Boolean", "Character", "Long");
        g("Mathe-Funktionen: Math.max, Math.min, Math.abs, Math.pow, Math.sqrt, Math.random ...", "Math");
        g("Hilfsmethoden für Arrays: Arrays.toString, Arrays.sort, Arrays.asList, Arrays.fill ...", "Arrays");
        g("Veränderbarer Text – effizient für viele Verkettungen (z. B. in Schleifen). append(), reverse(), toString().", "StringBuilder");
        g("Typ wird vom Compiler aus der rechten Seite abgeleitet (seit Java 10). Nur für lokale Variablen.", "var");
        g("Aufzählungstyp mit festen Konstanten, z. B. enum Farbe { ROT, GRUEN }.", "enum");
        g("Kompakte, unveränderliche Datenklasse (seit Java 16). Erzeugt Konstruktor, Getter, equals, hashCode, toString automatisch.", "record");
        g("Prüft den Typ eines Objekts: if (tier instanceof Hund h) { ... }", "instanceof");
        g("Ordnet Klassen in Namensräume (wie Ordner).", "package");
        g("Macht Klassen aus anderen Packages bekannt, z. B. import java.util.List;", "import");
        g("Annotation: diese Methode überschreibt eine Methode der Oberklasse/des Interfaces. Der Compiler prüft das.", "Override");
        g("Array: arr.length (Feld, ohne Klammern!). String: s.length() (Methode). Liste: list.size().", "length");
        g("Anzahl der Elemente einer Collection.", "size");
        g("Inhaltlicher Vergleich zweier Objekte. == vergleicht nur, ob es DASSELBE Objekt ist.", "equals");
        g("Zahl für Hash-Strukturen (HashMap/HashSet). Regel: a.equals(b) => gleicher hashCode. Immer zusammen mit equals überschreiben!", "hashCode");
        g("Text-Darstellung eines Objekts (wird z. B. von println benutzt).", "toString");
        g("Basisklasse für Fehler. Checked (müssen behandelt werden) vs. Unchecked (RuntimeException).", "Exception", "RuntimeException");
        g("Oberste Klasse – jede Klasse erbt automatisch von Object.", "Object");
        g("Stream-API: Daten deklarativ verarbeiten: filter, map, sorted, collect ...", "stream", "Stream", "Collectors");
        g("Natürliche Sortierreihenfolge einer Klasse: compareTo(other) < 0, == 0 oder > 0.", "Comparable", "compareTo");
        g("Externe Sortierregel: Comparator.comparing(Person::getAlter).", "Comparator");
        g("Behälter für 'Wert oder nichts' – vermeidet null. isPresent(), orElse(...).", "Optional");
        g("Geht eine Collection Element für Element durch; it.remove() löscht sicher.", "Iterator");
        g("Wandelt Text in int um. Wirft NumberFormatException bei ungültigem Text.", "parseInt");
        g("Unveränderliche Liste erzeugen: List.of(1, 2, 3).", "of");
    }

    static Map<String, String> glossar() {
        return new java.util.TreeMap<>(GLOSSAR);
    }

    static String glossary(String word) {
        return GLOSSAR.get(word);
    }
}
