# Java-Spickzettel

## Grundgerüst
```java
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hallo");
    }
}
```

## Datentypen
| Typ | Beispiel | Hinweis |
|---|---|---|
| `int` | `int x = 42;` | Standard-Ganzzahl (32 Bit) |
| `long` | `long l = 5_000_000_000L;` | große Ganzzahl |
| `double` | `double d = 3.14;` | Kommazahl (ungenau → Geld: BigDecimal) |
| `boolean` | `boolean ok = true;` | true / false |
| `char` | `char c = 'A';` | einfache Anführungszeichen |
| `String` | `String s = "Text";` | Objekt, immutable, Vergleich mit equals |
| Array | `int[] a = {1, 2, 3};` | feste Größe, `a.length` |

## Kontrollstrukturen
```java
if (x > 0) { ... } else if (x == 0) { ... } else { ... }
String r = x > 0 ? "positiv" : "nicht positiv";

for (int i = 0; i < n; i++) { ... }
for (String s : liste) { ... }
while (bedingung) { ... }
do { ... } while (bedingung);

String tag = switch (nr) {
    case 1, 7 -> "Wochenende";
    default -> "Werktag";
};
```

## Strings
| Methode | Ergebnis für s = "Hallo" |
|---|---|
| `s.length()` | 5 |
| `s.charAt(1)` | 'a' |
| `s.substring(1, 3)` | "al" |
| `s.indexOf("l")` | 2 |
| `s.toUpperCase()` | "HALLO" |
| `s.contains("al")` | true |
| `s.equals("hallo")` / `equalsIgnoreCase` | false / true |
| `s.split(",")` | String-Array |
| `s.trim()` / `s.strip()` | ohne Leerzeichen am Rand |
| `String.valueOf(42)` / `Integer.parseInt("42")` | Zahl ↔ Text |

## Collections
```java
List<String> list = new ArrayList<>();      // add, get, set, remove, size, contains
Set<String> set = new HashSet<>();          // add, contains, remove – keine Duplikate
Map<String, Integer> map = new HashMap<>(); // put, get, getOrDefault, containsKey, entrySet

for (Map.Entry<String, Integer> e : map.entrySet()) {
    System.out.println(e.getKey() + " = " + e.getValue());
}
Collections.sort(list);
list.sort(Comparator.comparing(String::length));
List<Integer> fix = List.of(1, 2, 3);       // unveränderlich!
```

| Interface | Implementierungen | Merkmal |
|---|---|---|
| `List` | ArrayList, LinkedList | Reihenfolge, Index, Duplikate |
| `Set` | HashSet, LinkedHashSet, TreeSet | keine Duplikate |
| `Map` | HashMap, LinkedHashMap, TreeMap | Key → Value |
| `Queue`/`Deque` | ArrayDeque, PriorityQueue | Warteschlange / Stack |

## OOP
```java
public interface Form { double flaeche(); }

public abstract class Basis implements Form {
    protected final String name;
    protected Basis(String name) { this.name = name; }
}

public class Kreis extends Basis {
    private final double r;
    public Kreis(double r) { super("Kreis"); this.r = r; }
    @Override public double flaeche() { return Math.PI * r * r; }
}

public record Punkt(int x, int y) {}   // equals/hashCode/toString gratis
```

## Streams
```java
List<String> r = namen.stream()
        .filter(n -> n.length() > 3)
        .map(String::toUpperCase)
        .sorted()
        .collect(Collectors.toList());
int summe = zahlen.stream().mapToInt(Integer::intValue).sum();
Map<Integer, List<String>> g = namen.stream().collect(Collectors.groupingBy(String::length));
```

## Exceptions
```java
try {
    riskant();
} catch (NumberFormatException e) {       // speziellere zuerst
    ...
} catch (IllegalArgumentException | IllegalStateException e) {
    ...
} finally {
    // läuft immer
}
throw new IllegalArgumentException("Grund");
```

## Laufzeiten (Big-O)
| Operation | ArrayList | LinkedList | HashMap/HashSet | TreeMap/TreeSet |
|---|---|---|---|---|
| Zugriff per Index / Key | O(1) | O(n) | O(1) | O(log n) |
| Einfügen am Ende | O(1)* | O(1) | O(1) | O(log n) |
| Suchen (contains) | O(n) | O(n) | O(1) | O(log n) |
| Sortieren | O(n log n) | | | schon sortiert |

## Dein eigener Spickzettel
Der beste Spickzettel ist der, den du **selbst von Hand** schreibst. Mach dir für jedes große Thema eine Seite aus dem Kopf und vergleiche sie danach mit dieser Übersicht.

## Tipps fürs Gespräch
- **Laut denken** – der Lösungsweg zählt mehr als die perfekte Syntax.
- **Rückfragen stellen**, bevor du programmierst (Randfälle, Eingaben).
- Erst eine **funktionierende** Lösung, dann optimieren und **Big-O** nennen.
- Ehrlich sein: "Das habe ich noch nicht benutzt, aber ich würde es so angehen ..."
- Ein eigenes **Projekt** vorbereiten, über das du 3 Minuten erzählen kannst (Was? Welche Technik? Welches Problem hast du gelöst?).
- **Eigene Fragen** an das Unternehmen mitbringen (Team, Tech-Stack, Einarbeitung).
