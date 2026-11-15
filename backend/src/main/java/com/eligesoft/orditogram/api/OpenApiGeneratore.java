package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.webhook.TipiEvento;

import java.util.*;

/**
 * Documento OpenAPI 3.1 dell'API pubblica, generato dai processi pubblicati: ogni processo ha il suo
 * percorso di avvio con lo schema esatto dei dati (presi dal form di avvio), ogni messaggio il suo schema.
 * Quando l'analista pubblica una nuova versione, la documentazione si aggiorna da sola.
 *
 * Classe senza dipendenze da Spring o dal motore: riceve la descrizione dei processi e restituisce mappe
 * pronte per Jackson (così si prova con un test unitario).
 */
public final class OpenApiGeneratore {

    private OpenApiGeneratore() {}

    private static final String JSON = "application/json";
    private static final String MULTIPART = "multipart/form-data";

    public static Map<String, Object> genera(List<ProcessoPubblicato> processi, String indirizzoBase,
                                             String versioneOrditogram, String nomeInstallazione) {
        Map<String, Object> doc = obj("openapi", "3.1.0");
        doc.put("info", obj(
            "title", (nomeInstallazione == null || nomeInstallazione.isBlank() ? "Orditogram" : nomeInstallazione) + " – API pubblica",
            "version", "1 (Orditogram " + versioneOrditogram + ")",
            "description", """
                API per i sistemi esterni (portali, gestionali, app): avviare pratiche, seguirne lo stato, \
                scambiare documenti e messaggi. I percorsi di avvio e i messaggi sono generati dai processi \
                pubblicati: i dati richiesti sono quelli dei form disegnati nello Studio.

                **Autenticazione**: una chiave creata in Studio → Integrazioni, nell'intestazione `X-API-Key` \
                oppure come `Authorization: Bearer <chiave>`. Ogni chiave ha i suoi permessi e può essere \
                limitata ad alcuni processi.

                **Errori**: sempre nel formato `{"errore": {"codice", "messaggio", "campi"}}`; il codice è stabile.

                **Avvisi (webhook)**: Orditogram può chiamare un tuo indirizzo quando succede qualcosa \
                (vedi la sezione webhooks). Ogni avviso è firmato con HMAC-SHA256 nell'intestazione `X-Orditogram-Firma`."""));
        doc.put("servers", List.of(obj("url", indirizzoBase + "/api/v1", "description", "Questa installazione")));
        doc.put("security", List.of(obj("chiaveApi", List.of()), obj("bearer", List.of())));
        doc.put("tags", List.of(
            obj("name", "Processi", "description", "Le tipologie di pratica che si possono avviare"),
            obj("name", "Pratiche", "description", "Avvio, ricerca, stato e storico"),
            obj("name", "Documenti", "description", "Documenti delle pratiche"),
            obj("name", "Messaggi", "description", "Messaggi attesi dalle pratiche (es. integrazioni, pagamenti, rinunce)"),
            obj("name", "Sistema", "description", "Verifica della chiave e documentazione")));

        Map<String, Object> schemi = new LinkedHashMap<>();
        schemiComuni(schemi);
        Map<String, Object> paths = new LinkedHashMap<>();

        // ------------------------------------------------------------ processi
        paths.put("/processi", obj("get", op("Processi", "elencoProcessi", "Processi che la chiave può usare",
            "Ultima versione di ogni processo pubblicato, con i dati da inviare all'avvio e i messaggi che riceve.",
            null, null, risposte("200", "Elenco dei processi", arrayDi("Processo")))));
        paths.put("/processi/{chiave}", obj("get", op("Processi", "processo", "Un processo",
            null, List.of(parPercorso("chiave", "Chiave del processo (es. accessoAtti)")), null,
            risposte("200", "Il processo", ref("Processo"), "404", "Processo non trovato", ref("Errore")))));

        // ------------------------------------------------------------ avvio, uno per processo
        List<Object> varianti = new ArrayList<>();
        Map<String, Object> mappaDiscriminatore = new LinkedHashMap<>();
        for (ProcessoPubblicato p : processi) {
            String id = nomeSchema(p.chiave());
            schemi.put("Dati_" + id, schemaDati(p));
            Map<String, Object> avvio = obj("type", "object", "title", "Avvio di " + p.nome(),
                "additionalProperties", false,
                "properties", obj(
                    "riferimento", obj("type", "string", "maxLength", 255,
                        "description", "Il tuo identificativo (es. numero della domanda sul portale). Se esiste già una pratica "
                            + "di questo processo con lo stesso riferimento, Orditogram restituisce quella invece di crearne un'altra."),
                    "dati", ref("Dati_" + id)),
                "required", List.of("dati"));
            schemi.put("Avvio_" + id, avvio);
            Map<String, Object> generica = obj("allOf", List.of(ref("Avvio_" + id),
                obj("type", "object", "properties", obj("processo", obj("const", p.chiave())), "required", List.of("processo"))));
            schemi.put("AvvioGenerico_" + id, generica);
            varianti.add(ref("AvvioGenerico_" + id));
            mappaDiscriminatore.put(p.chiave(), "#/components/schemas/AvvioGenerico_" + id);

            Map<String, Object> corpo = obj(JSON, obj("schema", ref("Avvio_" + id),
                "example", obj("riferimento", "DOM-2026-0042", "dati", esempioDati(p))));
            if (!"nessuno".equals(p.allegati())) {
                corpo.put(MULTIPART, obj("schema", obj("type", "object",
                    "properties", obj(
                        "richiesta", obj("type", "string", "contentMediaType", JSON,
                            "description", "Il JSON di avvio (stesso formato del corpo JSON)"),
                        "documenti", obj("type", "array", "items", obj("type", "string", "contentMediaType", "application/octet-stream"),
                            "description", "obbligatori".equals(p.allegati()) ? "Almeno un documento" : "Documenti facoltativi")),
                    "required", "obbligatori".equals(p.allegati()) ? List.of("richiesta", "documenti") : List.of("richiesta"))));
            }
            String descr = (p.descrizione() != null && !p.descrizione().isBlank() ? p.descrizione() + "\n\n" : "")
                + "Versione " + p.versione() + ". Allegati all'avvio: " + p.allegati() + "."
                + (p.campi().isEmpty() ? "" : "\n\nDati: " + p.campi().stream()
                    .map(c -> "`" + c.nome() + "`" + (c.obbligatorio() ? " (obbligatorio)" : "")).reduce((a, b) -> a + ", " + b).orElse(""));
            paths.put("/processi/" + p.chiave() + "/pratiche", obj("post", op("Pratiche", "avvia_" + id,
                "Avvia: " + p.nome(), descr, null,
                obj("required", true, "content", corpo),
                risposteAvvio())));
        }

        // ------------------------------------------------------------ pratiche
        Map<String, Object> corpoGenerico = varianti.isEmpty()
            ? obj("type", "object", "properties", obj("processo", obj("type", "string"), "riferimento", obj("type", "string"),
                "dati", obj("type", "object")), "required", List.of("processo"))
            : obj("oneOf", varianti, "discriminator", obj("propertyName", "processo", "mapping", mappaDiscriminatore));
        paths.put("/pratiche", obj(
            "get", op("Pratiche", "cercaPratiche", "Cerca pratiche",
                "Pratiche dei processi consentiti alla chiave, le più recenti prima.",
                List.of(
                    parQuery("processo", "Chiave del processo", obj("type", "string")),
                    parQuery("stato", "Stato", obj("type", "string", "enum", List.of("in_corso", "conclusa"))),
                    parQuery("riferimento", "Il tuo riferimento, indicato all'avvio", obj("type", "string")),
                    parQuery("dal", "Avviate da questa data (inclusa)", obj("type", "string", "format", "date")),
                    parQuery("al", "Avviate fino a questa data (inclusa)", obj("type", "string", "format", "date")),
                    parQuery("pagina", "Numero di pagina, da 0", obj("type", "integer", "minimum", 0, "default", 0)),
                    parQuery("dimensione", "Pratiche per pagina", obj("type", "integer", "minimum", 1, "maximum", 200, "default", 50))),
                null, risposte("200", "Una pagina di pratiche", ref("PaginaPratiche"))),
            "post", op("Pratiche", "avviaPratica", "Avvia una pratica (qualunque processo)",
                "Come i percorsi /processi/{chiave}/pratiche, con il processo indicato nel corpo. Per allegare documenti usa il percorso del processo.",
                null, obj("required", true, "content", obj(JSON, obj("schema", corpoGenerico))), risposteAvvio())));
        paths.put("/pratiche/{id}", obj("get", op("Pratiche", "pratica", "Stato e dati di una pratica",
            "Attività in corso, scadenze, messaggi attesi e tutti i dati raccolti finora.",
            List.of(parId()), null,
            risposte("200", "La pratica", ref("Pratica"), "404", "Pratica non trovata", ref("Errore")))));
        paths.put("/pratiche/{id}/storico", obj("get", op("Pratiche", "storico", "Storico della pratica",
            "Chi ha fatto cosa e quando, in ordine cronologico.", List.of(parId()), null,
            risposte("200", "Eventi", arrayDi("EventoStorico")))));

        // ------------------------------------------------------------ documenti
        paths.put("/pratiche/{id}/documenti", obj(
            "get", op("Documenti", "documenti", "Documenti della pratica", "Con tutte le versioni e l'esito della verifica della firma.",
                List.of(parId()), null, risposte("200", "Documenti", arrayDi("Documento"))),
            "post", op("Documenti", "aggiungiDocumento", "Aggiungi un documento",
                "Solo per pratiche in corso. Con `documento` si carica una nuova versione di un documento esistente.",
                List.of(parId()),
                obj("required", true, "content", obj(MULTIPART, obj("schema", obj("type", "object",
                    "properties", obj(
                        "file", obj("type", "string", "contentMediaType", "application/octet-stream"),
                        "nota", obj("type", "string"),
                        "documento", obj("type", "integer", "description", "Id del documento di cui caricare una nuova versione")),
                    "required", List.of("file"))))),
                risposte("201", "Versione caricata", ref("VersioneDocumento")))));
        paths.put("/pratiche/{id}/documenti/versioni/{versione}", obj("get", op("Documenti", "scaricaDocumento",
            "Scarica una versione", null,
            List.of(parId(), parPercorso("versione", "Id della versione")), null,
            obj("200", obj("description", "Il file", "content", obj("application/octet-stream", obj("schema",
                obj("type", "string", "contentMediaType", "application/octet-stream"))))))));

        // ------------------------------------------------------------ messaggi
        Map<String, Set<String>> messaggi = new TreeMap<>();
        Map<String, Set<String>> processiDelMessaggio = new TreeMap<>();
        for (ProcessoPubblicato p : processi)
            for (ProcessoPubblicato.Messaggio m : p.messaggi()) {
                messaggi.computeIfAbsent(m.nome(), k -> new TreeSet<>()).addAll(m.dati());
                processiDelMessaggio.computeIfAbsent(m.nome(), k -> new TreeSet<>()).add(p.nome());
            }
        for (var e : messaggi.entrySet()) {
            String id = nomeSchema(e.getKey());
            Map<String, Object> props = new LinkedHashMap<>();
            for (String d : e.getValue()) props.put(d, obj("type", List.of("string", "number", "boolean")));
            schemi.put("DatiMessaggio_" + id, obj("type", "object", "additionalProperties", false, "properties", props));
            Map<String, Object> esempioDati = new LinkedHashMap<>();
            for (String d : e.getValue()) esempioDati.put(d, "…");
            paths.put("/messaggi/" + e.getKey(), obj("post", op("Messaggi", "messaggio_" + id,
                "Messaggio: " + e.getKey(),
                "Atteso da: " + String.join(", ", processiDelMessaggio.get(e.getKey()))
                    + ". Indica la pratica con `pratica` (id) oppure con `cerca` (dati della pratica, es. il numero di protocollo)."
                    + (e.getValue().isEmpty() ? " Il messaggio non porta dati." : " Dati ammessi: " + String.join(", ", e.getValue()) + "."),
                null,
                obj("required", true, "content", obj(JSON, obj(
                    "schema", obj("type", "object", "additionalProperties", false, "properties", obj(
                        "pratica", obj("type", "string", "description", "Id della pratica"),
                        "cerca", obj("type", "object", "description", "Dati con cui trovare la pratica, es. {\"numeroProtocollo\": \"2026/000123\"}",
                            "additionalProperties", obj("type", List.of("string", "number", "boolean"))),
                        "dati", ref("DatiMessaggio_" + id))),
                    "example", obj("cerca", obj("riferimento", "DOM-2026-0042"), "dati", esempioDati)))),
                risposteMessaggio())));
        }
        paths.put("/pratiche/{id}/messaggi/{nome}", obj("post", op("Messaggi", "messaggioAllaPratica",
            "Messaggio a una pratica precisa", "Per qualunque messaggio che la pratica sta aspettando (vedi `messaggiAttesi` nella pratica).",
            List.of(parId(), parPercorso("nome", "Nome del messaggio")),
            obj("content", obj(JSON, obj("schema", obj("type", "object", "properties", obj(
                "dati", obj("type", "object", "additionalProperties", obj("type", List.of("string", "number", "boolean")))))))),
            risposteMessaggio())));

        // ------------------------------------------------------------ sistema
        paths.put("/io", obj("get", op("Sistema", "io", "Chi sono",
            "Per verificare la chiave: nome del sistema, permessi, processi consentiti.", null, null,
            risposte("200", "La chiave", ref("Identita")))));
        paths.put("/openapi.json", obj("get", op("Sistema", "openapi", "Questo documento",
            "Limitato ai processi consentiti alla chiave. Importalo in Postman, Swagger o in un generatore di client.", null, null,
            obj("200", obj("description", "Documento OpenAPI 3.1", "content", obj(JSON, obj("schema", obj("type", "object"))))))));

        doc.put("paths", paths);

        // ------------------------------------------------------------ webhook
        Map<String, Object> webhooks = new LinkedHashMap<>();
        for (var t : TipiEvento.TUTTI.entrySet()) {
            webhooks.put(t.getKey(), obj("post", obj(
                "summary", t.getValue(),
                "description", "Orditogram invia l'evento con una POST al tuo indirizzo. Rispondi con un codice 2xx entro 10 secondi: "
                    + "altrimenti Orditogram riprova dopo 1 minuto, 5 minuti, 30 minuti, 2, 6, 12 e 24 ore. "
                    + "Lo stesso evento può arrivare più di una volta: usa `id` per riconoscere i duplicati.",
                "parameters", List.of(
                    obj("name", "X-Orditogram-Firma", "in", "header", "required", true, "schema", obj("type", "string"),
                        "description", "t=<secondi>,v1=<HMAC-SHA256 esadecimale di \"t.corpo\" con il segreto del webhook>"),
                    obj("name", "X-Orditogram-Evento", "in", "header", "required", true, "schema", obj("type", "string", "const", t.getKey())),
                    obj("name", "X-Orditogram-Consegna", "in", "header", "required", true, "schema", obj("type", "string"),
                        "description", "Id della consegna (cambia a ogni evento, resta uguale nei tentativi)")),
                "requestBody", obj("content", obj(JSON, obj("schema", ref("Evento")))),
                "responses", obj("2XX", obj("description", "Evento ricevuto")))));
        }
        doc.put("webhooks", webhooks);

        doc.put("components", obj(
            "securitySchemes", obj(
                "chiaveApi", obj("type", "apiKey", "in", "header", "name", "X-API-Key"),
                "bearer", obj("type", "http", "scheme", "bearer", "description", "La stessa chiave, come token Bearer")),
            "schemas", schemi));
        return doc;
    }

    // ================================================================ schemi

    static Map<String, Object> schemaDati(ProcessoPubblicato p) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> obbligatori = new ArrayList<>();
        for (ValoriForm.Campo c : p.campi()) {
            props.put(c.nome(), schemaCampo(c));
            if (c.obbligatorio()) obbligatori.add(c.nome());
        }
        Map<String, Object> s = obj("type", "object", "title", "Dati di avvio: " + p.nome(),
            "additionalProperties", false, "properties", props);
        if (p.titoloForm() != null) s.put("description", "Dal form \"" + p.titoloForm() + "\"");
        if (!obbligatori.isEmpty()) s.put("required", obbligatori);
        return s;
    }

    static Map<String, Object> schemaCampo(ValoriForm.Campo c) {
        Map<String, Object> s = switch (c.tipo()) {
            case "number" -> obj("type", "number");
            case "date" -> obj("type", "string", "format", "date");
            case "checkbox" -> obj("type", "boolean");
            case "select" -> obj("type", "string", "enum", c.opzioni());
            default -> obj("type", "string");
        };
        s.put("title", c.etichetta());
        if (c.aiuto() != null && !c.aiuto().isBlank()) s.put("description", c.aiuto());
        if ("checkbox".equals(c.tipo()) && c.obbligatorio()) s.put("const", true);
        return s;
    }

    static Map<String, Object> esempioDati(ProcessoPubblicato p) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ValoriForm.Campo c : p.campi()) out.put(c.nome(), esempio(c));
        return out;
    }

    static Object esempio(ValoriForm.Campo c) {
        String n = c.nome().toLowerCase(Locale.ROOT);
        return switch (c.tipo()) {
            case "number" -> 100;
            case "date" -> "2026-10-15";
            case "checkbox" -> true;
            case "select" -> c.opzioni().isEmpty() ? "" : c.opzioni().get(0);
            default -> n.contains("email") || n.contains("pec") ? "mario.rossi@example.com"
                : n.contains("codicefiscale") ? "RSSMRA80A01H501U"
                : n.contains("telefono") ? "+39 06 1234567"
                : c.etichetta();
        };
    }

    private static void schemiComuni(Map<String, Object> s) {
        Map<String, Object> stringa = obj("type", "string");
        Map<String, Object> dataOra = obj("type", "string", "format", "date-time");
        s.put("Errore", obj("type", "object", "properties", obj("errore", obj("type", "object",
            "properties", obj(
                "codice", obj("type", "string", "examples", List.of("dati_non_validi", "non_trovato", "permesso_mancante",
                    "chiave_non_valida", "troppe_richieste", "conflitto")),
                "messaggio", stringa,
                "campi", obj("type", "array", "items", obj("type", "object",
                    "properties", obj("campo", stringa, "messaggio", stringa)))),
            "required", List.of("codice", "messaggio"))), "required", List.of("errore")));
        s.put("Campo", obj("type", "object", "properties", obj(
            "nome", stringa, "etichetta", stringa,
            "tipo", obj("type", "string", "enum", List.of("text", "textarea", "number", "date", "select", "checkbox")),
            "obbligatorio", obj("type", "boolean"), "opzioni", obj("type", "array", "items", stringa), "aiuto", stringa)));
        s.put("Processo", obj("type", "object", "properties", obj(
            "chiave", stringa, "nome", stringa, "descrizione", stringa, "versione", obj("type", "integer"),
            "campi", arrayDi("Campo"),
            "allegati", obj("type", "string", "enum", List.of("nessuno", "facoltativi", "obbligatori")),
            "messaggi", obj("type", "array", "items", obj("type", "object", "properties", obj(
                "nome", stringa, "dati", obj("type", "array", "items", stringa)))),
            "avvio", obj("type", "string", "description", "Percorso per avviare una pratica di questo processo")),
            "required", List.of("chiave", "nome", "versione", "campi", "allegati")));
        s.put("Attivita", obj("type", "object", "properties", obj(
            "id", stringa, "nome", stringa, "chiave", obj("type", "string", "description", "Id dell'attività nel BPMN"),
            "assegnatario", stringa, "gruppi", obj("type", "array", "items", stringa), "dal", dataOra)));
        s.put("Scadenza", obj("type", "object", "properties", obj(
            "tipo", obj("type", "string", "enum", List.of("scadenza", "sollecito", "attesa")),
            "nome", stringa, "attivita", stringa, "quando", dataOra)));
        s.put("Pratica", obj("type", "object", "properties", obj(
            "id", stringa, "titolo", stringa,
            "riferimento", obj("type", "string", "description", "Il riferimento indicato all'avvio"),
            "processo", obj("type", "object", "properties", obj("chiave", stringa, "nome", stringa, "versione", obj("type", "integer"))),
            "stato", obj("type", "string", "enum", List.of("in_corso", "conclusa")),
            "avviata", dataOra, "conclusa", dataOra, "avviataDa", stringa,
            "attivitaCorrenti", arrayDi("Attivita"),
            "scadenze", arrayDi("Scadenza"),
            "messaggiAttesi", obj("type", "array", "items", obj("type", "object", "properties", obj("nome", stringa, "elemento", stringa))),
            "dati", obj("type", "object", "description", "Tutti i dati della pratica: avvio, attività, servizi esterni, messaggi",
                "additionalProperties", true)),
            "required", List.of("id", "titolo", "processo", "stato", "avviata")));
        s.put("PraticaCreata", obj("allOf", List.of(ref("Pratica"), obj("type", "object", "properties", obj(
            "creata", obj("type", "boolean", "description", "false se esisteva già una pratica con lo stesso riferimento"))))));
        s.put("PaginaPratiche", obj("type", "object", "properties", obj(
            "righe", arrayDi("Pratica"), "totale", obj("type", "integer"),
            "pagina", obj("type", "integer"), "dimensione", obj("type", "integer"))));
        s.put("EventoStorico", obj("type", "object", "properties", obj(
            "tipo", stringa, "titolo", stringa, "utente", stringa, "utenteNome", stringa, "quando", dataOra,
            "dettagli", obj("type", "array", "items", obj("type", "object", "properties", obj("label", stringa, "value", stringa))))));
        s.put("VersioneDocumento", obj("type", "object", "properties", obj(
            "id", obj("type", "integer"), "numero", obj("type", "integer"), "filename", stringa,
            "dimensione", obj("type", "integer"), "sha256", stringa, "caricatoDa", stringa, "caricatoIl", dataOra,
            "firmaTipo", stringa, "firmaEsito", stringa)));
        s.put("Documento", obj("type", "object", "properties", obj(
            "id", obj("type", "integer"), "nome", stringa, "versioneCorrente", obj("type", "integer"),
            "versioni", arrayDi("VersioneDocumento"))));
        s.put("Identita", obj("type", "object", "properties", obj(
            "sistema", stringa, "permessi", obj("type", "array", "items", stringa),
            "processi", obj("type", "array", "items", stringa, "description", "Vuoto = tutti"))));
        s.put("Evento", obj("type", "object", "properties", obj(
            "id", obj("type", "string", "description", "Univoco: serve a riconoscere gli invii ripetuti"),
            "tipo", obj("type", "string", "enum", new ArrayList<>(TipiEvento.TUTTI.keySet())),
            "quando", dataOra,
            "pratica", obj("type", "object", "properties", obj(
                "id", stringa, "titolo", stringa, "processo", stringa, "processoNome", stringa, "riferimento", stringa)),
            "attivita", obj("type", "object", "description", "Solo per gli eventi attivita.*", "properties", obj(
                "id", stringa, "nome", stringa, "chiave", stringa, "assegnatario", stringa,
                "gruppi", obj("type", "array", "items", stringa))),
            "dettagli", obj("type", "array", "items", obj("type", "object", "properties", obj("etichetta", stringa, "valore", stringa)))),
            "required", List.of("id", "tipo", "quando")));
    }

    // ================================================================ supporto

    private static Map<String, Object> risposteAvvio() {
        return risposte(
            "201", "Pratica avviata", ref("PraticaCreata"),
            "200", "Esisteva già una pratica con lo stesso riferimento: è quella", ref("PraticaCreata"),
            "422", "Dati non validi (dettagli in errore.campi)", ref("Errore"),
            "403", "La chiave non può avviare questo processo", ref("Errore"));
    }

    private static Map<String, Object> risposteMessaggio() {
        return risposte(
            "200", "Messaggio consegnato", obj("type", "object", "properties", obj(
                "consegnato", obj("type", "boolean"), "pratiche", obj("type", "array", "items", obj("type", "string")))),
            "404", "Nessuna pratica sta aspettando il messaggio", ref("Errore"),
            "409", "Il criterio corrisponde a più pratiche", ref("Errore"));
    }

    private static Map<String, Object> op(String tag, String id, String sommario, String descrizione,
                                          List<Object> parametri, Map<String, Object> corpo, Map<String, Object> risposte) {
        Map<String, Object> o = obj("tags", List.of(tag), "operationId", id, "summary", sommario);
        if (descrizione != null) o.put("description", descrizione);
        if (parametri != null) o.put("parameters", parametri);
        if (corpo != null) o.put("requestBody", corpo);
        Map<String, Object> r = new LinkedHashMap<>(risposte);
        r.putIfAbsent("401", obj("description", "Chiave mancante o non valida", "content", obj(JSON, obj("schema", ref("Errore")))));
        r.putIfAbsent("429", obj("description", "Troppe richieste: riprova dopo i secondi indicati in Retry-After"));
        o.put("responses", r);
        return o;
    }

    /** risposte("200", "descrizione", schema, "404", ...). */
    private static Map<String, Object> risposte(Object... v) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 2 < v.length; i += 3)
            m.put((String) v[i], obj("description", v[i + 1], "content", obj(JSON, obj("schema", v[i + 2]))));
        return m;
    }

    private static Object parId() {
        return parPercorso("id", "Id della pratica");
    }

    private static Object parPercorso(String nome, String descrizione) {
        return obj("name", nome, "in", "path", "required", true, "description", descrizione, "schema", obj("type", "string"));
    }

    private static Object parQuery(String nome, String descrizione, Map<String, Object> schema) {
        return obj("name", nome, "in", "query", "required", false, "description", descrizione, "schema", schema);
    }

    private static Map<String, Object> ref(String schema) {
        return obj("$ref", "#/components/schemas/" + schema);
    }

    private static Map<String, Object> arrayDi(String schema) {
        return obj("type", "array", "items", ref(schema));
    }

    /** I nomi degli schemi ammettono solo lettere, numeri, punto, trattino e trattino basso. */
    static String nomeSchema(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
