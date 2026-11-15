package com.eligesoft.orditogram.connettore;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sostituzione dei ${dati} nei testi scritti dall'analista. Nessuna dipendenza: si prova da sola. */
public final class Testi {

    public static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z][\\w-]*)}");

    private Testi() {}

    /** Sostituisce ${nome} con il valore del dato; escape adatta il valore al contesto (URL, JSON...). */
    public static String sostituisci(String testo, Map<String, Object> dati, UnaryOperator<String> escape) {
        if (testo == null) return null;
        Matcher m = VAR.matcher(testo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = dati.get(m.group(1));
            String s = v == null ? "" : v.toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(escape.apply(s)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String sostituisci(String testo, Map<String, Object> dati) {
        return sostituisci(testo, dati, UnaryOperator.identity());
    }

    /** Escape per inserire un valore dentro una stringa JSON (senza le virgolette esterne). */
    public static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
