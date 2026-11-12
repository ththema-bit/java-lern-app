package lernapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini-Markdown nach HTML (fuer Swing-HTML):
 * ## / ### Ueberschriften, - Listen, | Tabellen |, > Tipp-Box, ```java / ```run Codebloecke,
 * `code` und **fett** im Text. ```run-Bloecke bekommen einen "Im Editor ausprobieren"-Link.
 */
final class Markdown {

    final List<String> snippets = new ArrayList<>();

    String toHtml(String md) {
        if (md == null) return "";
        StringBuilder html = new StringBuilder();
        String[] lines = md.replace("\r\n", "\n").split("\n", -1);
        List<String> para = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String t = lines[i].trim();
            if (t.startsWith("```")) {
                flush(html, para);
                boolean run = t.substring(3).trim().equals("run");
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) code.append(lines[i++]).append('\n');
                i++;
                String c = code.toString().stripTrailing();
                html.append("<pre>").append(esc(c)).append("</pre>");
                if (run) {
                    snippets.add(c);
                    html.append("<p class=run><a href=\"snippet:").append(snippets.size() - 1)
                            .append("\">&#9654; Im Editor ausprobieren</a></p>");
                }
                continue;
            }
            if (t.isEmpty()) {
                flush(html, para);
                i++;
                continue;
            }
            if (t.startsWith("### ")) {
                flush(html, para);
                html.append("<h3>").append(inline(t.substring(4))).append("</h3>");
                i++;
                continue;
            }
            if (t.startsWith("# ")) {
                flush(html, para);
                html.append("<h1>").append(inline(t.substring(2))).append("</h1>");
                i++;
                continue;
            }
            if (t.startsWith("## ")) {
                flush(html, para);
                html.append("<h2>").append(inline(t.substring(3))).append("</h2>");
                i++;
                continue;
            }
            if (t.startsWith("- ")) {
                flush(html, para);
                html.append("<ul>");
                while (i < lines.length && lines[i].trim().startsWith("- ")) {
                    html.append("<li>").append(inline(lines[i].trim().substring(2))).append("</li>");
                    i++;
                }
                html.append("</ul>");
                continue;
            }
            if (t.matches("\\d+\\. .*")) {
                flush(html, para);
                html.append("<ol>");
                while (i < lines.length && lines[i].trim().matches("\\d+\\. .*")) {
                    String item = lines[i].trim();
                    html.append("<li>").append(inline(item.substring(item.indexOf(' ') + 1))).append("</li>");
                    i++;
                }
                html.append("</ol>");
                continue;
            }
            if (t.startsWith("|")) {
                flush(html, para);
                html.append("<table cellspacing=1 cellpadding=5 bgcolor=" + Theme.H_LINIE + ">");
                boolean header = true;
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    String row = lines[i].trim();
                    i++;
                    if (row.matches("\\|[\\s\\-:|]+")) continue;
                    row = row.replace("\\|", "\u0000");   // \| = echtes | in einer Zelle
                    String inner = row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length());
                    html.append("<tr>");
                    for (String cell : inner.split("\\|")) {
                        html.append(header ? "<td bgcolor=" + Theme.H_ERHOEHT + "><b>" : "<td bgcolor=" + Theme.H_FLAECHE + ">")
                                .append(inline(cell.trim().replace('\u0000', '|')))
                                .append(header ? "</b></td>" : "</td>");
                    }
                    html.append("</tr>");
                    header = false;
                }
                html.append("</table>");
                continue;
            }
            if (t.startsWith(">")) {
                flush(html, para);
                StringBuilder box = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    String l = lines[i].trim().substring(1).trim();
                    box.append(l.isEmpty() ? "<br><br>" : inline(l) + " ");
                    i++;
                }
                html.append("<table width=100% cellpadding=8 cellspacing=0><tr><td bgcolor=" + Theme.H_BOX_GELB + ">")
                        .append(box).append("</td></tr></table>");
                continue;
            }
            para.add(t);
            i++;
        }
        flush(html, para);
        return html.toString();
    }

    private void flush(StringBuilder html, List<String> para) {
        if (para.isEmpty()) return;
        html.append("<p>").append(inline(String.join(" ", para))).append("</p>");
        para.clear();
    }

    static String inline(String s) {
        StringBuilder sb = new StringBuilder();
        String[] parts = s.split("`", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) sb.append("<code>").append(esc(parts[i])).append("</code>");
            else sb.append(esc(parts[i]).replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>"));
        }
        return sb.toString();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
