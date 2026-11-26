package com.eligesoft.soluzioni.isi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lettura dei dati della domanda (Orditogram li manda come testi) e formati italiani per le risposte. */
public final class Testi {
    public static final List<String> ASSI = List.of("1.1", "1.2", "2", "3", "4", "5.1", "5.2");
    private static final List<String> MESI = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
            "Oct", "Nov", "Dec");

    private Testi() {}

    public static String testo(Object v) {
        return v == null ? "" : String.valueOf(v).strip();
    }

    public static String testo(Map<String, ?> d, String campo) {
        return testo(d.get(campo));
    }

    /** Numero dal testo dell'interfaccia: accetta "1.500,50", "1500.5", "10,44". */
    public static BigDecimal dec(Object v, BigDecimal predefinito) {
        String s = testo(v).replace("€", "").replace(" ", "");
        if (s.isEmpty()) return predefinito;
        if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return predefinito;
        }
    }

    public static BigDecimal dec(Object v) {
        return dec(v, null);
    }

    public static boolean si(Object v) {
        return List.of("true", "sì", "si", "1", "yes", "on").contains(testo(v).toLowerCase(Locale.ROOT));
    }

    /** Data AAAA-MM-GG, oppure come la scrive Orditogram nei testi dei connettori ("Mon Mar 01 00:00:00 UTC 2010"). */
    public static LocalDate data(Object v) {
        String s = testo(v);
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (RuntimeException ignorata) {
                // si prova il formato di Java
            }
        }
        String[] p = s.split("\\s+");
        if (p.length == 6 && MESI.contains(p[1]) && p[2].chars().allMatch(Character::isDigit)
                && p[5].chars().allMatch(Character::isDigit)) {
            return LocalDate.of(Integer.parseInt(p[5]), MESI.indexOf(p[1]) + 1, Integer.parseInt(p[2]));
        }
        return null;
    }

    /** "2 Rischi infortunistici" → "2"; "1.1" → "1.1". */
    public static String codiceAsse(Object v) {
        String s = testo(v).split(" ")[0];
        return ASSI.contains(s) ? s : "";
    }

    /** "b) Sostituzione di macchine" → "b"; "2a – Gasolio..." → "2a". */
    public static String codiceVoce(Object v) {
        String s = testo(v);
        if (s.isEmpty()) return "";
        return s.split("\\)")[0].split(" ")[0].strip().toLowerCase(Locale.ROOT);
    }

    public static BigDecimal centesimi(BigDecimal x) {
        return x.setScale(2, RoundingMode.HALF_UP);
    }

    /** Formato italiano: 70.200,00 €. */
    public static String euro(BigDecimal x) {
        if (x == null) return "—";
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ITALY));
        return f.format(centesimi(x)) + " €";
    }

    /** Punti con la virgola: 201, 10,2. */
    public static String punti(BigDecimal x) {
        return numero(x).replace(".", ",");
    }

    /** Numero con il punto, senza zeri inutili: per le condizioni del BPMN. */
    public static String numero(BigDecimal x) {
        BigDecimal n = x.stripTrailingZeros();
        return (n.scale() < 0 ? n.setScale(0) : n).toPlainString();
    }

    /** Percentuale intera: 0.65 → 65. */
    public static int percento(BigDecimal x) {
        return x.multiply(BigDecimal.valueOf(100)).intValue();
    }

    /** R-CALC-10: negli elenchi l'importo è troncato all'euro. */
    public static long troncaEuro(BigDecimal x) {
        return x.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public static BigDecimal min(BigDecimal... valori) {
        BigDecimal m = valori[0];
        for (BigDecimal v : valori) if (v.compareTo(m) < 0) m = v;
        return m;
    }

    public static boolean zero(BigDecimal x) {
        return x == null || x.signum() == 0;
    }
}
