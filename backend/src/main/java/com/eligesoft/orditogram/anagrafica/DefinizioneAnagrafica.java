package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.form.ValoriForm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Pattern;

/**
 * La definizione di un tipo di anagrafica: lo stesso JSON dei form ({ "fields": [ ... ] }, con sezioni, tabelle,
 * campi condizionali e regole), più tre proprietà dei campi:
 *  - "chiave": true    il valore identifica la scheda (es. codice fiscale): due schede dello stesso tipo non
 *                      possono averlo uguale (maiuscole e spazi non contano);
 *  - "cercabile": true il valore si trova con la ricerca, oltre al titolo;
 *  - "type": "anagrafica" con "tipoAnagrafica": "codice"  relazione con una scheda di un altro tipo (o dello stesso).
 * Il campo con "titolo": true dà il nome alla scheda nelle ricerche e negli elenchi.
 * "ruoliVisibili" riserva un campo a chi ha uno di quei ruoli, come nei form.
 */
public final class DefinizioneAnagrafica {

    /** Codice di un tipo: minuscole, numeri e trattini. */
    public static final Pattern CODICE = Pattern.compile("[a-z][a-z0-9-]{1,59}");
    /** Limiti, per non far crescere a dismisura definizioni e indice. */
    public static final int MAX_CAMPI = 150;
    public static final int MAX_DEFINIZIONE = 200_000;

    /**
     * Tipi di campo ammessi in un'anagrafica: quelli dei form più la relazione. Il campo Organizzazione resta dei
     * form (le organizzazioni esterne sono legate agli accessi): si unirà alle anagrafiche più avanti.
     */
    static final Set<String> TIPI;
    static {
        Set<String> t = new HashSet<>(FormService.TIPI_CAMPO);
        t.remove(ValoriForm.ORGANIZZAZIONE);
        t.add(ValoriForm.ANAGRAFICA);
        TIPI = Set.copyOf(t);
    }
    /** Tipi che possono essere chiave o ricercabili. */
    static final Set<String> TIPI_INDICIZZABILI = Set.of("text", "number", "date", "select");

    /** Un campo che va nell'indice: chiave, ricercabile o relazione. */
    public record CampoIndice(String nome, String etichetta, boolean chiave, String tipoAnagrafica) {
        public boolean relazione() { return tipoAnagrafica != null; }
    }

    private final JsonNode json;
    private final List<ValoriForm.Campo> campi;
    private final Map<String, JsonNode> nodi = new LinkedHashMap<>();
    private final List<CampoIndice> indice = new ArrayList<>();

    private DefinizioneAnagrafica(JsonNode json) {
        this.json = json;
        this.campi = ValoriForm.campi(json);
        for (JsonNode f : json.path("fields")) {
            String nome = f.path("name").asText("");
            if (nome.isBlank() || ValoriForm.SEZIONE.equals(f.path("type").asText(""))) continue;
            nodi.put(nome, f);
            String tipo = f.path("type").asText("text");
            if (ValoriForm.ANAGRAFICA.equals(tipo))
                indice.add(new CampoIndice(nome, f.path("label").asText(nome), false, f.path("tipoAnagrafica").asText()));
            else if (f.path("chiave").asBoolean(false) || f.path("cercabile").asBoolean(false))
                indice.add(new CampoIndice(nome, f.path("label").asText(nome), f.path("chiave").asBoolean(false), null));
        }
    }

    /** Legge una definizione già controllata (quella salvata nel database). */
    public static DefinizioneAnagrafica di(JsonNode json) {
        return new DefinizioneAnagrafica(json);
    }

    /**
     * Controlla una definizione nuova: formato del form (FormService), poi le regole proprie delle anagrafiche.
     * esiste dice se un codice di tipo esiste (per le relazioni); codice è il tipo che si sta salvando, che può
     * puntare a sé stesso. Solleva IllegalArgumentException con un messaggio per l'analista.
     */
    public static DefinizioneAnagrafica controlla(FormService forms, String codice, String testo,
                                                  java.util.function.Predicate<String> esiste) {
        if (testo == null || testo.isBlank()) throw new IllegalArgumentException("La definizione dei campi è vuota");
        if (testo.length() > MAX_DEFINIZIONE) throw new IllegalArgumentException("La definizione è troppo grande");
        JsonNode json;
        try {
            json = forms.validate(codice, testo, TIPI);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw new IllegalArgumentException(e.getReason());
        }
        JsonNode fields = json.get("fields");
        if (fields.size() > MAX_CAMPI) throw new IllegalArgumentException("Al massimo " + MAX_CAMPI + " campi");
        int titoli = 0;
        boolean dati = false;
        for (JsonNode f : fields) {
            if (ValoriForm.SEZIONE.equals(f.path("type").asText(""))) continue;
            dati = true;
            String nome = f.path("name").asText();
            String tipo = f.path("type").asText("text");
            String campo = "Campo \"" + nome + "\": ";
            if (nome.length() > 100) throw new IllegalArgumentException(campo + "il nome è troppo lungo (al massimo 100 caratteri)");
            boolean chiave = f.path("chiave").asBoolean(false), cercabile = f.path("cercabile").asBoolean(false);
            boolean riservato = f.path("ruoliVisibili").isArray() && !f.path("ruoliVisibili").isEmpty();
            if (f.has("parte")) throw new IllegalArgumentException(campo + "in un'anagrafica un campo non indica parti della pratica");
            if (f.path("readonly").asBoolean(false) && f.path("required").asBoolean(false))
                throw new IllegalArgumentException(campo + "un campo in sola lettura non può essere obbligatorio");
            if ((chiave || cercabile) && !TIPI_INDICIZZABILI.contains(tipo))
                throw new IllegalArgumentException(campo + "solo testo, numero, data o scelta possono essere chiave o ricercabili");
            if (chiave && f.has("visibileSe")) throw new IllegalArgumentException(campo + "un campo chiave non può essere condizionale");
            if ((chiave || cercabile || f.path("titolo").asBoolean(false)) && riservato)
                throw new IllegalArgumentException(campo + "un campo chiave, ricercabile o titolo non può essere riservato ad alcuni ruoli");
            if (f.path("titolo").asBoolean(false)) {
                titoli++;
                if (!Set.of("text", "number", "date", "select").contains(tipo))
                    throw new IllegalArgumentException(campo + "il titolo deve essere un campo di testo, numero, data o scelta");
            }
            if (ValoriForm.ANAGRAFICA.equals(tipo)) {
                String collegato = f.path("tipoAnagrafica").asText("");
                if (collegato.isBlank()) throw new IllegalArgumentException(campo + "indica l'anagrafica collegata (tipoAnagrafica)");
                if (!collegato.equals(codice) && !esiste.test(collegato))
                    throw new IllegalArgumentException(campo + "l'anagrafica \"" + collegato + "\" non esiste");
            } else if (f.has("tipoAnagrafica")) {
                throw new IllegalArgumentException(campo + "\"tipoAnagrafica\" si usa solo con i campi di tipo anagrafica");
            }
        }
        if (!dati) throw new IllegalArgumentException("Serve almeno un campo");
        if (titoli > 1) throw new IllegalArgumentException("Un solo campo può fare da titolo");
        return new DefinizioneAnagrafica(json);
    }

    public JsonNode json() { return json; }
    public List<ValoriForm.Campo> campi() { return campi; }
    public List<CampoIndice> indice() { return indice; }
    public JsonNode nodo(String nome) { return nodi.get(nome); }
    public Set<String> nomi() { return nodi.keySet(); }

    /** Codici dei tipi collegati con campi relazione (anche nelle versioni future, solo dalla definizione). */
    public Set<String> tipiCollegati() {
        Set<String> out = new TreeSet<>();
        indice.stream().filter(CampoIndice::relazione).forEach(c -> out.add(c.tipoAnagrafica()));
        return out;
    }

    /** Nomi dei campi in sola lettura. */
    public Set<String> solaLettura() {
        Set<String> out = new HashSet<>();
        nodi.forEach((n, f) -> { if (f.path("readonly").asBoolean(false)) out.add(n); });
        return out;
    }

    /** Campi riservati che l'utente non vede: hanno ruoliVisibili e lui non ha nessuno di quei ruoli. */
    public Set<String> riservati(Set<String> ruoliUtente, boolean amministratore) {
        Set<String> out = new HashSet<>();
        if (amministratore) return out;
        nodi.forEach((n, f) -> {
            JsonNode r = f.path("ruoliVisibili");
            if (!r.isArray() || r.isEmpty()) return;
            for (JsonNode x : r) if (ruoliUtente.contains(x.asText())) return;
            out.add(n);
        });
        return out;
    }

    /** La definizione senza i campi indicati (quella che vede chi non ha i ruoli dei campi riservati). */
    public JsonNode senza(Set<String> nascosti) {
        if (nascosti.isEmpty()) return json;
        ObjectNode copia = json.deepCopy();
        ArrayNode fields = copia.putArray("fields");
        for (JsonNode f : json.path("fields"))
            if (!nascosti.contains(f.path("name").asText(""))) fields.add(f);
        return copia;
    }

    /**
     * Titolo della scheda: il campo titolo, altrimenti il primo campo di testo, numero, data o scelta compilato,
     * altrimenti null (chi chiama mette nome del tipo e numero).
     */
    public String titolo(Map<String, Object> dati) {
        for (var e : nodi.entrySet())
            if (e.getValue().path("titolo").asBoolean(false) && pieno(dati.get(e.getKey()))) return taglia(dati.get(e.getKey()).toString());
        for (var e : nodi.entrySet())
            if (TIPI_INDICIZZABILI.contains(e.getValue().path("type").asText("text")) && pieno(dati.get(e.getKey())))
                return taglia(dati.get(e.getKey()).toString());
        return null;
    }

    /** Valore come va nell'indice: senza spazi ai lati, spazi interni ridotti, minuscolo, al massimo 255 caratteri. */
    public static String normalizza(Object valore) {
        if (valore == null) return null;
        String s = valore.toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    private static boolean pieno(Object v) {
        return v != null && !v.toString().isBlank();
    }

    private static String taglia(String s) {
        s = s.trim();
        return s.length() > 500 ? s.substring(0, 497) + "..." : s;
    }
}
