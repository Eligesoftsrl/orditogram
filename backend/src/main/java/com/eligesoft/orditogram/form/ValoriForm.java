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
 * "options", "titolo", "visibileSe" } ], "documenti": "carica", "documentiObbligatori": true }.
 *
 * Sezioni: un elemento { "type": "sezione", "label", "help" } divide il form in parti; non è un dato.
 * Campi condizionali: "visibileSe": { "campo": "esito", "valori": ["Serve integrazione"] } mostra il campo solo
 * se l'altro campo ha uno dei valori indicati (per un sì/no: "true" o "false"). Un campo nascosto non è
 * obbligatorio e il suo valore non viene salvato.
 */
public final class ValoriForm {

    private ValoriForm() {}

    /** Un campo modificabile del form, come lo vede chi lo compila (o chi chiama l'API). */
    public record Campo(String nome, String etichetta, String tipo, boolean obbligatorio, List<String> opzioni,
                        String aiuto, Condizione visibileSe, Map<String, String> regole) {
        public Campo(String nome, String etichetta, String tipo, boolean obbligatorio, List<String> opzioni, String aiuto) {
            this(nome, etichetta, tipo, obbligatorio, opzioni, aiuto, null, Map.of());
        }
        public Campo(String nome, String etichetta, String tipo, boolean obbligatorio, List<String> opzioni,
                     String aiuto, Condizione visibileSe) {
            this(nome, etichetta, tipo, obbligatorio, opzioni, aiuto, visibileSe, Map.of());
        }
    }

    /** Proprietà del campo che sono regole di validazione (vedi RegoleCampo). */
    static final List<String> REGOLE = List.of("min", "max", "minLunghezza", "maxLunghezza", "formato", "espressione", "messaggioFormato");

    /** Il campo si mostra solo se il campo indicato ha uno di questi valori. */
    public record Condizione(String campo, List<String> valori) {
        public boolean soddisfatta(Object valore) {
            if (valore == null) return false;
            String s = valore.toString().trim();
            return !s.isEmpty() && valori.contains(s);
        }
    }

    /** Tipo dell'elemento che apre una sezione del form (non è un campo). */
    public static final String SEZIONE = "sezione";

    /** Un valore non valido, con il nome del campo e il motivo. */
    public record Errore(String campo, String messaggio) {}

    public static final Set<String> TIPI = Set.of("text", "textarea", "number", "date", "select", "checkbox");

    /** Campi che si possono compilare (esclusi quelli in sola lettura), nell'ordine del form. */
    public static List<Campo> campi(JsonNode form) {
        List<Campo> out = new ArrayList<>();
        if (form == null || !form.has("fields")) return out;
        for (JsonNode f : form.get("fields")) {
            if (f.path("readonly").asBoolean(false) || sezione(f)) continue;
            String nome = f.path("name").asText("");
            if (nome.isBlank()) continue;
            String tipo = f.path("type").asText("text");
            if (!TIPI.contains(tipo)) tipo = "text";
            List<String> opzioni = new ArrayList<>();
            if ("select".equals(tipo)) f.path("options").forEach(o -> opzioni.add(o.asText()));
            String aiuto = f.hasNonNull("help") ? f.get("help").asText() : f.hasNonNull("aiuto") ? f.get("aiuto").asText() : null;
            out.add(new Campo(nome, f.path("label").asText(nome), tipo, f.path("required").asBoolean(false),
                List.copyOf(opzioni), aiuto, condizione(f), regole(f)));
        }
        return out;
    }

    static Map<String, String> regole(JsonNode f) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String r : REGOLE) if (f.hasNonNull(r) && !f.get(r).asText().isBlank()) out.put(r, f.get(r).asText().trim());
        return Collections.unmodifiableMap(out);
    }

    static boolean sezione(JsonNode f) {
        return SEZIONE.equals(f.path("type").asText(""));
    }

    /** La condizione "visibileSe" del campo, o null. Accetta anche "valore" singolo al posto di "valori". */
    static Condizione condizione(JsonNode f) {
        JsonNode c = f.path("visibileSe");
        if (!c.isObject() || c.path("campo").asText("").isBlank()) return null;
        List<String> valori = new ArrayList<>();
        if (c.path("valori").isArray()) c.get("valori").forEach(v -> valori.add(v.asText().trim()));
        else if (c.has("valore")) valori.add(c.get("valore").asText().trim());
        return new Condizione(c.get("campo").asText().trim(), List.copyOf(valori));
    }

    /**
     * Nomi dei campi nascosti dalle loro condizioni, dati i valori (del form e della pratica).
     * Un campo che dipende da un campo nascosto è nascosto anche lui.
     */
    public static Set<String> nascosti(JsonNode form, Map<String, Object> valori) {
        Set<String> out = new HashSet<>();
        if (form == null || !form.has("fields")) return out;
        Map<String, Condizione> condizioni = new LinkedHashMap<>();
        for (JsonNode f : form.get("fields")) {
            Condizione c = condizione(f);
            String nome = f.path("name").asText("");
            if (c != null && !nome.isBlank()) condizioni.put(nome, c);
        }
        boolean cambiato = true;
        while (cambiato) {
            cambiato = false;
            for (var e : condizioni.entrySet()) {
                if (out.contains(e.getKey())) continue;
                Condizione c = e.getValue();
                Object v = out.contains(c.campo()) ? null : valori.get(c.campo());
                if (!c.soddisfatta(v)) { out.add(e.getKey()); cambiato = true; }
            }
        }
        return out;
    }

    private static Map<String, Object> unisci(Map<String, Object> contesto, Map<String, Object> valori) {
        Map<String, Object> tutti = new HashMap<>(contesto != null ? contesto : Map.of());
        tutti.putAll(valori);
        return tutti;
    }

    /**
     * Valori accettati: solo i campi modificabili dichiarati nel form.
     * Così nessuno può impostare dati della pratica che il form non prevede.
     * Senza form (processi semplici) si accetta il corpo così com'è.
     */
    public static Map<String, Object> ammessi(JsonNode form, Map<String, Object> body) {
        return ammessi(form, body, Map.of());
    }

    /**
     * Come sopra, togliendo i valori dei campi nascosti dalle condizioni.
     * Il contesto sono i dati già presenti nella pratica (servono se la condizione guarda un campo in sola lettura).
     */
    public static Map<String, Object> ammessi(JsonNode form, Map<String, Object> body, Map<String, Object> contesto) {
        Map<String, Object> in = body != null ? body : Map.of();
        if (form == null || !form.has("fields")) return new HashMap<>(in);
        Map<String, Object> out = new HashMap<>();
        for (Campo c : campi(form))
            if (in.containsKey(c.nome())) out.put(c.nome(), in.get(c.nome()));
        out.keySet().removeAll(nascosti(form, unisci(contesto, out)));
        return out;
    }

    /** Etichette dei campi obbligatori non compilati (un sì/no obbligatorio deve essere "sì"). */
    public static List<String> obbligatoriMancanti(JsonNode form, Map<String, Object> vars) {
        return obbligatoriMancanti(form, vars, Map.of());
    }

    /** Come sopra; i campi nascosti dalle condizioni non sono obbligatori. */
    public static List<String> obbligatoriMancanti(JsonNode form, Map<String, Object> vars, Map<String, Object> contesto) {
        List<String> mancanti = new ArrayList<>();
        Set<String> nascosti = nascosti(form, unisci(contesto, vars));
        for (Campo c : campi(form)) {
            if (!c.obbligatorio() || nascosti.contains(c.nome())) continue;
            Object v = vars.get(c.nome());
            boolean vuoto = v == null || v.toString().isBlank()
                || ("checkbox".equals(c.tipo()) && !Boolean.TRUE.equals(v));
            if (vuoto) mancanti.add(c.etichetta());
        }
        return mancanti;
    }

    /**
     * Valori che non rispettano le regole dei campi (min/max, lunghezza, formato): nome del campo e motivo.
     * Si controllano solo i campi modificabili, visibili e compilati.
     */
    public static List<Errore> erroriRegole(JsonNode form, Map<String, Object> vars, Map<String, Object> contesto, LocalDate oggi) {
        List<Errore> out = new ArrayList<>();
        if (form == null || !form.has("fields")) return out;
        Set<String> nascosti = nascosti(form, unisci(contesto, vars));
        for (JsonNode f : form.get("fields")) {
            if (f.path("readonly").asBoolean(false) || sezione(f)) continue;
            String nome = f.path("name").asText("");
            if (nome.isBlank() || nascosti.contains(nome) || !vars.containsKey(nome)) continue;
            String msg = RegoleCampo.controlla(f, f.path("type").asText("text"), vars.get(nome), oggi);
            if (msg != null) out.add(new Errore(nome, msg));
        }
        return out;
    }

    /** "Importo: deve essere almeno 10; Email: deve essere un indirizzo email valido" */
    public static String descriviErrori(JsonNode form, List<Errore> errori) {
        Map<String, String> etichette = new HashMap<>();
        if (form != null && form.has("fields"))
            form.get("fields").forEach(f -> etichette.put(f.path("name").asText(), f.path("label").asText(f.path("name").asText())));
        return String.join("; ", errori.stream().map(e -> etichette.getOrDefault(e.campo(), e.campo()) + ": " + e.messaggio()).toList());
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
        // i campi nascosti dalle condizioni non si salvano e non sono obbligatori
        Set<String> nascosti = nascosti(form, valori);
        valori.keySet().removeAll(nascosti);
        // regole dei campi (min/max, lunghezza, formato)
        for (Errore e : erroriRegole(form, valori, Map.of(), LocalDate.now()))
            if (errori.stream().noneMatch(x -> x.campo().equals(e.campo()))) errori.add(e);
        for (Campo c : perNome.values()) {
            if (!c.obbligatorio() || nascosti.contains(c.nome())
                || errori.stream().anyMatch(x -> x.campo().equals(c.nome()))) continue;
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
