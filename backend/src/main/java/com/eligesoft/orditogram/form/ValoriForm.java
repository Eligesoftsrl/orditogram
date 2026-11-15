package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Regole sui valori inviati con un form, uguali per l'interfaccia di Orditogram e per l'API pubblica:
 * quali campi si accettano, quali sono obbligatori, che tipo devono avere, come si ricava il titolo della pratica.
 *
 * Il form è il JSON salvato nello Studio: { "fields": [ { "name", "label", "type", "required", "readonly",
 * "options", "titolo" } ], "documenti": "carica", "documentiObbligatori": true }.
 */
public final class ValoriForm {

    private ValoriForm() {}

    /** Un campo modificabile del form, come lo vede chi lo compila (o chi chiama l'API). */
    public record Campo(String nome, String etichetta, String tipo, boolean obbligatorio, List<String> opzioni,
                        String aiuto) {}

    /** Un valore non valido, con il nome del campo e il motivo. */
    public record Errore(String campo, String messaggio) {}

    public static final Set<String> TIPI = Set.of("text", "textarea", "number", "date", "select", "checkbox");

    /** Campi che si possono compilare (esclusi quelli in sola lettura), nell'ordine del form. */
    public static List<Campo> campi(JsonNode form) {
        List<Campo> out = new ArrayList<>();
        if (form == null || !form.has("fields")) return out;
        for (JsonNode f : form.get("fields")) {
            if (f.path("readonly").asBoolean(false)) continue;
            String nome = f.path("name").asText("");
            if (nome.isBlank()) continue;
            String tipo = f.path("type").asText("text");
            if (!TIPI.contains(tipo)) tipo = "text";
            List<String> opzioni = new ArrayList<>();
            if ("select".equals(tipo)) f.path("options").forEach(o -> opzioni.add(o.asText()));
            String aiuto = f.hasNonNull("help") ? f.get("help").asText() : f.hasNonNull("aiuto") ? f.get("aiuto").asText() : null;
            out.add(new Campo(nome, f.path("label").asText(nome), tipo, f.path("required").asBoolean(false),
                List.copyOf(opzioni), aiuto));
        }
        return out;
    }

    /**
     * Valori accettati: solo i campi modificabili dichiarati nel form.
     * Così nessuno può impostare dati della pratica che il form non prevede.
     * Senza form (processi semplici) si accetta il corpo così com'è.
     */
    public static Map<String, Object> ammessi(JsonNode form, Map<String, Object> body) {
        Map<String, Object> in = body != null ? body : Map.of();
        if (form == null || !form.has("fields")) return new HashMap<>(in);
        Map<String, Object> out = new HashMap<>();
        for (Campo c : campi(form))
            if (in.containsKey(c.nome())) out.put(c.nome(), in.get(c.nome()));
        return out;
    }

    /** Etichette dei campi obbligatori non compilati (un sì/no obbligatorio deve essere "sì"). */
    public static List<String> obbligatoriMancanti(JsonNode form, Map<String, Object> vars) {
        List<String> mancanti = new ArrayList<>();
        for (Campo c : campi(form)) {
            if (!c.obbligatorio()) continue;
            Object v = vars.get(c.nome());
            boolean vuoto = v == null || v.toString().isBlank()
                || ("checkbox".equals(c.tipo()) && !Boolean.TRUE.equals(v));
            if (vuoto) mancanti.add(c.etichetta());
        }
        return mancanti;
    }

    /**
     * Controllo rigoroso per l'API: campi sconosciuti o in sola lettura, tipi, scelte ammesse, obbligatori.
     * Restituisce i valori normalizzati (sì/no come booleani, numeri come numeri, testo senza spazi ai lati)
     * oppure gli errori trovati, tutti insieme, così chi integra li corregge in una volta.
     */
    public static Esito convalida(JsonNode form, Map<String, Object> body) {
        Map<String, Object> in = body != null ? body : Map.of();
        List<Errore> errori = new ArrayList<>();
        Map<String, Object> valori = new LinkedHashMap<>();
        if (form == null || !form.has("fields")) {
            // processo senza form di avvio: si accettano solo valori semplici
            for (var e : in.entrySet()) {
                Object v = e.getValue();
                if (v == null) continue;
                if (v instanceof String || v instanceof Number || v instanceof Boolean) valori.put(e.getKey(), v);
                else errori.add(new Errore(e.getKey(), "deve essere testo, numero o vero/falso"));
            }
            return new Esito(valori, errori);
        }
        Map<String, Campo> perNome = new LinkedHashMap<>();
        campi(form).forEach(c -> perNome.put(c.nome(), c));
        Set<String> solaLettura = new HashSet<>();
        form.get("fields").forEach(f -> { if (f.path("readonly").asBoolean(false)) solaLettura.add(f.path("name").asText()); });

        for (var e : in.entrySet()) {
            String nome = e.getKey();
            Campo c = perNome.get(nome);
            if (c == null) {
                errori.add(new Errore(nome, solaLettura.contains(nome) ? "il campo è in sola lettura"
                    : "campo non previsto dal form" + suggerimento(nome, perNome.keySet())));
                continue;
            }
            Object v = e.getValue();
            if (v == null || (v instanceof String s && s.isBlank())) continue;
            try {
                valori.put(nome, normalizza(c, v));
            } catch (IllegalArgumentException ex) {
                errori.add(new Errore(nome, ex.getMessage()));
            }
        }
        for (Campo c : perNome.values()) {
            if (!c.obbligatorio() || errori.stream().anyMatch(x -> x.campo().equals(c.nome()))) continue;
            Object v = valori.get(c.nome());
            if (v == null) errori.add(new Errore(c.nome(), "obbligatorio"));
            else if ("checkbox".equals(c.tipo()) && !Boolean.TRUE.equals(v)) errori.add(new Errore(c.nome(), "deve essere true"));
        }
        return new Esito(valori, errori);
    }

    public record Esito(Map<String, Object> valori, List<Errore> errori) {
        public boolean valido() { return errori.isEmpty(); }
    }

    static Object normalizza(Campo c, Object v) {
        switch (c.tipo()) {
            case "number" -> {
                if (v instanceof Number) return v;
                if (v instanceof String s) {
                    try {
                        new BigDecimal(s.trim());
                        return s.trim();   // come l'interfaccia, che invia i numeri come testo
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("deve essere un numero (es. 1250.50)");
                    }
                }
                throw new IllegalArgumentException("deve essere un numero");
            }
            case "checkbox" -> {
                if (v instanceof Boolean) return v;
                if ("true".equals(v) || "false".equals(v)) return Boolean.valueOf((String) v);
                throw new IllegalArgumentException("deve essere true o false");
            }
            case "date" -> {
                if (!(v instanceof String s)) throw new IllegalArgumentException("deve essere una data nel formato AAAA-MM-GG");
                try {
                    return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE).toString();
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("deve essere una data nel formato AAAA-MM-GG");
                }
            }
            case "select" -> {
                String s = v instanceof String x ? x.trim() : (v instanceof Number || v instanceof Boolean) ? v.toString() : null;
                if (s == null || !c.opzioni().contains(s))
                    throw new IllegalArgumentException("valore non ammesso; scegli tra: " + String.join(", ", c.opzioni()));
                return s;
            }
            default -> {
                if (v instanceof String s) {
                    if (s.length() > 100_000) throw new IllegalArgumentException("testo troppo lungo");
                    return s.trim();
                }
                if (v instanceof Number || v instanceof Boolean) return v.toString();
                throw new IllegalArgumentException("deve essere un testo");
            }
        }
    }

    /** " (forse intendevi emailRichiedente?)" se c'è un campo dal nome simile. */
    static String suggerimento(String nome, Collection<String> nomi) {
        String migliore = null;
        int minima = Integer.MAX_VALUE;
        for (String n : nomi) {
            int d = distanza(nome.toLowerCase(Locale.ROOT), n.toLowerCase(Locale.ROOT));
            if (d < minima) { minima = d; migliore = n; }
        }
        return migliore != null && minima <= Math.max(2, nome.length() / 4) ? " (forse intendevi " + migliore + "?)" : "";
    }

    private static int distanza(String a, String b) {
        int[] prec = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prec[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prec[j] + 1), prec[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            prec = cur;
        }
        return prec[b.length()];
    }

    /**
     * Titolo della pratica: il campo marcato "titolo": true, altrimenti "oggetto", altrimenti il primo campo
     * di testo compilato, altrimenti nome del processo + data.
     */
    public static String titolo(JsonNode form, Map<String, Object> vars, String nomeProcesso, LocalDate oggi) {
        if (form != null && form.has("fields")) {
            for (JsonNode f : form.get("fields"))
                if (f.path("titolo").asBoolean(false) && pieno(vars.get(f.path("name").asText())))
                    return vars.get(f.path("name").asText()).toString();
        }
        if (pieno(vars.get("oggetto"))) return vars.get("oggetto").toString();
        if (form != null && form.has("fields")) {
            for (JsonNode f : form.get("fields"))
                if ("text".equals(f.path("type").asText("text")) && pieno(vars.get(f.path("name").asText())))
                    return vars.get(f.path("name").asText()).toString();
        }
        return nomeProcesso + " del " + oggi.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /** Modalità dei documenti all'avvio: "nessuno", "facoltativi", "obbligatori". */
    public static String allegatiAvvio(JsonNode form) {
        if (form == null || !"carica".equals(form.path("documenti").asText(""))) return "nessuno";
        return form.path("documentiObbligatori").asBoolean(false) ? "obbligatori" : "facoltativi";
    }

    private static boolean pieno(Object o) {
        return o != null && !o.toString().isBlank();
    }
}
