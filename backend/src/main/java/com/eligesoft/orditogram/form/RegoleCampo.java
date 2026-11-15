package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regole di validazione di un campo del form, uguali per interfaccia, completamento delle attività e API pubblica
 * (le stesse regole sono in frontend/src/lib/formRegole.js: tienile allineate).
 *
 * Proprietà del campo:
 *   "min", "max"                      numero: valori ammessi; data: "AAAA-MM-GG" oppure "oggi"
 *   "minLunghezza", "maxLunghezza"    testo: numero di caratteri
 *   "formato"                         email, codiceFiscale, partitaIva, cap, telefono, iban, espressione
 *   "espressione", "messaggioFormato" con formato "espressione": espressione regolare e messaggio per chi compila
 */
public final class RegoleCampo {

    private RegoleCampo() {}

    public static final Set<String> FORMATI = Set.of("email", "codiceFiscale", "partitaIva", "cap", "telefono", "iban", "espressione");

    /** Lunghezza massima di un'espressione scritta dall'analista e di un testo su cui la si prova. */
    static final int MAX_ESPRESSIONE = 300;
    static final int MAX_TESTO_PER_ESPRESSIONE = 10_000;
    /** Passi massimi dell'espressione regolare su un valore: oltre, il valore si considera non valido (difesa da ReDoS). */
    static final int MAX_PASSI = 1_000_000;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern CF_PERSONA = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
    private static final Pattern UNDICI_CIFRE = Pattern.compile("^[0-9]{11}$");
    private static final Pattern CAP = Pattern.compile("^[0-9]{5}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9][0-9 ./-]{5,19}$");
    private static final Pattern IBAN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    /**
     * Il messaggio d'errore per il valore (già normalizzato, non vuoto), oppure null se va bene.
     * Il campo è il nodo JSON del form; oggi serve per le date "oggi".
     */
    public static String controlla(JsonNode campo, String tipo, Object valore, LocalDate oggi) {
        if (valore == null) return null;
        String s = valore.toString().trim();
        if (s.isEmpty()) return null;
        switch (tipo) {
            case "number" -> {
                BigDecimal n;
                try { n = new BigDecimal(s); } catch (NumberFormatException e) { return null; }   // il tipo lo controlla già ValoriForm
                BigDecimal min = numero(campo.get("min")), max = numero(campo.get("max"));
                if (min != null && n.compareTo(min) < 0) return "deve essere almeno " + testo(min);
                if (max != null && n.compareTo(max) > 0) return "deve essere al massimo " + testo(max);
            }
            case "date" -> {
                LocalDate d;
                try { d = LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
                LocalDate min = data(campo.get("min"), oggi), max = data(campo.get("max"), oggi);
                if (min != null && d.isBefore(min)) return "non può essere prima del " + giorno(min, campo.get("min"));
                if (max != null && d.isAfter(max)) return "non può essere dopo il " + giorno(max, campo.get("max"));
            }
            case "text", "textarea" -> {
                int lunghezza = s.codePointCount(0, s.length());
                Integer min = intero(campo.get("minLunghezza")), max = intero(campo.get("maxLunghezza"));
                if (min != null && lunghezza < min) return "deve avere almeno " + min + (min == 1 ? " carattere" : " caratteri");
                if (max != null && lunghezza > max) return "può avere al massimo " + max + (max == 1 ? " carattere" : " caratteri");
                String formato = campo.path("formato").asText("");
                if (!formato.isEmpty()) return formato(formato, s, campo);
            }
            default -> { }
        }
        return null;
    }

    private static String formato(String formato, String s, JsonNode campo) {
        String compatto = s.replace(" ", "").toUpperCase(Locale.ROOT);
        return switch (formato) {
            case "email" -> EMAIL.matcher(s).matches() ? null : "deve essere un indirizzo email valido";
            case "codiceFiscale" -> codiceFiscaleValido(compatto) ? null : "deve essere un codice fiscale valido";
            case "partitaIva" -> partitaIvaValida(compatto) ? null : "deve essere una partita IVA valida (11 cifre)";
            case "cap" -> CAP.matcher(s).matches() ? null : "deve essere un CAP di 5 cifre";
            case "telefono" -> TELEFONO.matcher(s).matches() ? null : "deve essere un numero di telefono valido";
            case "iban" -> ibanValido(compatto) ? null : "deve essere un IBAN valido";
            case "espressione" -> {
                String espr = campo.path("espressione").asText("");
                if (espr.isEmpty()) yield null;
                String msg = campo.path("messaggioFormato").asText("");
                yield corrisponde(espr, s) ? null : (msg.isBlank() ? "non è nel formato richiesto" : msg);
            }
            default -> null;
        };
    }

    // ------------------------------------------------------------ formati italiani

    /** Codice fiscale di persona (16 caratteri, con carattere di controllo) o di ente (11 cifre). */
    static boolean codiceFiscaleValido(String cf) {
        if (UNDICI_CIFRE.matcher(cf).matches()) return partitaIvaValida(cf);
        if (!CF_PERSONA.matcher(cf).matches()) return false;
        int[] dispari = {1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23};
        int somma = 0;
        for (int i = 0; i < 15; i++) {
            char c = cf.charAt(i);
            int v = Character.isDigit(c) ? c - '0' : c - 'A';
            somma += (i % 2 == 0) ? dispari[v] : v;
        }
        return cf.charAt(15) == (char) ('A' + somma % 26);
    }

    static boolean partitaIvaValida(String piva) {
        if (!UNDICI_CIFRE.matcher(piva).matches()) return false;
        int somma = 0;
        for (int i = 0; i < 10; i++) {
            int c = piva.charAt(i) - '0';
            if (i % 2 == 1) { c *= 2; if (c > 9) c -= 9; }
            somma += c;
        }
        return (10 - somma % 10) % 10 == piva.charAt(10) - '0';
    }

    static boolean ibanValido(String iban) {
        if (!IBAN.matcher(iban).matches()) return false;
        if (iban.startsWith("IT") && iban.length() != 27) return false;
        String riordinato = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeri = new StringBuilder();
        for (char c : riordinato.toCharArray())
            numeri.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        return new BigInteger(numeri.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    // ------------------------------------------------------------ espressioni dell'analista

    /** Prova l'espressione con un limite di passi: un'espressione scritta male non può bloccare il server. */
    static boolean corrisponde(String espressione, String valore) {
        if (espressione.length() > MAX_ESPRESSIONE || valore.length() > MAX_TESTO_PER_ESPRESSIONE) return false;
        try {
            return Pattern.compile(espressione).matcher(new TestoLimitato(valore, MAX_PASSI)).matches();
        } catch (PatternSyntaxException | TestoLimitato.TroppiPassi e) {
            return false;
        }
    }

    /** Un testo che smette di rispondere dopo troppe letture: interrompe le espressioni con backtracking esplosivo. */
    static final class TestoLimitato implements CharSequence {
        static final class TroppiPassi extends RuntimeException {
            TroppiPassi() { super(null, null, false, false); }
        }
        private final CharSequence testo;
        private final int[] passi;
        private final int limite;

        TestoLimitato(CharSequence testo, int limite) { this(testo, limite, new int[1]); }
        private TestoLimitato(CharSequence testo, int limite, int[] passi) { this.testo = testo; this.limite = limite; this.passi = passi; }

        @Override public char charAt(int i) {
            if (++passi[0] > limite) throw new TroppiPassi();
            return testo.charAt(i);
        }
        @Override public int length() { return testo.length(); }
        @Override public CharSequence subSequence(int a, int b) { return new TestoLimitato(testo.subSequence(a, b), limite, passi); }
        @Override public String toString() { return testo.toString(); }
    }

    // ------------------------------------------------------------ controllo della definizione (Studio)

    /** Problemi nelle regole di un campo, per il salvataggio del form nello Studio. */
    public static List<String> problemi(JsonNode campo, String tipo) {
        List<String> out = new ArrayList<>();
        if ("number".equals(tipo)) {
            BigDecimal min = numero(campo.get("min")), max = numero(campo.get("max"));
            if (campo.has("min") && min == null) out.add("il minimo deve essere un numero");
            if (campo.has("max") && max == null) out.add("il massimo deve essere un numero");
            if (min != null && max != null && min.compareTo(max) > 0) out.add("il minimo è più grande del massimo");
        }
        if ("date".equals(tipo)) {
            LocalDate oggi = LocalDate.now();
            LocalDate min = data(campo.get("min"), oggi), max = data(campo.get("max"), oggi);
            if (campo.has("min") && min == null) out.add("la data minima deve essere AAAA-MM-GG oppure \"oggi\"");
            if (campo.has("max") && max == null) out.add("la data massima deve essere AAAA-MM-GG oppure \"oggi\"");
            if (min != null && max != null && min.isAfter(max)) out.add("la data minima è dopo la massima");
        }
        if ("text".equals(tipo) || "textarea".equals(tipo)) {
            Integer min = intero(campo.get("minLunghezza")), max = intero(campo.get("maxLunghezza"));
            if (campo.has("minLunghezza") && (min == null || min < 0)) out.add("la lunghezza minima deve essere un numero intero");
            if (campo.has("maxLunghezza") && (max == null || max < 1)) out.add("la lunghezza massima deve essere un numero intero positivo");
            if (min != null && max != null && min > max) out.add("la lunghezza minima è più grande della massima");
            String formato = campo.path("formato").asText("");
            if (!formato.isEmpty() && !FORMATI.contains(formato)) out.add("formato \"" + formato + "\" non previsto");
            if ("espressione".equals(formato)) {
                String espr = campo.path("espressione").asText("");
                if (espr.isBlank()) out.add("scrivi l'espressione del formato");
                else if (espr.length() > MAX_ESPRESSIONE) out.add("l'espressione è troppo lunga (al massimo " + MAX_ESPRESSIONE + " caratteri)");
                else try { Pattern.compile(espr); } catch (PatternSyntaxException e) { out.add("l'espressione non è valida: " + e.getDescription()); }
            }
        } else if (campo.has("formato")) {
            out.add("il formato si usa solo con i campi di testo");
        }
        return out;
    }

    // ------------------------------------------------------------ supporto

    static BigDecimal numero(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try { return new BigDecimal(n.asText().trim()); } catch (NumberFormatException e) { return null; }
    }

    static Integer intero(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try { return Integer.valueOf(n.asText().trim()); } catch (NumberFormatException e) { return null; }
    }

    static LocalDate data(JsonNode n, LocalDate oggi) {
        if (n == null || n.isNull()) return null;
        String s = n.asText().trim();
        if ("oggi".equalsIgnoreCase(s)) return oggi;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }

    private static String testo(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String giorno(LocalDate d, JsonNode origine) {
        return "oggi".equalsIgnoreCase(origine.asText().trim()) ? "giorno di oggi" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
