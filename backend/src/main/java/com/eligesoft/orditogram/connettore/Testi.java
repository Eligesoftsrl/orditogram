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
            String s = testo(dati.get(m.group(1)));
            m.appendReplacement(sb, Matcher.quoteReplacement(escape.apply(s)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Il valore di un dato come testo. Le date salvate come {@link java.util.Date} (pratiche create prima che le
     * attività salvassero le date come testo) diventano ISO: {@code 2010-03-01} se a mezzanotte, altrimenti con l'ora.
     */
    public static String testo(Object v) {
        if (v == null) return "";
        if (v instanceof java.util.Date d) return iso(d);
        return v.toString();
    }

    /** Una data salvata come {@link java.util.Date} in formato ISO, nel fuso dell'installazione. */
    public static String iso(java.util.Date d) {
        java.time.ZonedDateTime z = d.toInstant().atZone(java.time.ZoneId.systemDefault());
        return z.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? z.toLocalDate().toString()
            : z.toOffsetDateTime().toString();
    }

    /** Il valore con le date {@link java.util.Date} in ISO, gli altri invariati (per l'interfaccia e l'API). */
    public static Object leggibile(Object v) {
        return v instanceof java.util.Date d ? iso(d) : v;
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
