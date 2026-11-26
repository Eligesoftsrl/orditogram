package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Gestione dei form.
 * Ordine di ricerca: prima Postgres (tabella form_definition), poi classpath (resources/forms/*.json).
 * Così i form forniti col progetto fanno da default e quelli salvati da interfaccia li sostituiscono.
 *
 * Versioni: ogni salvataggio che cambia il form aggiunge una riga a form_versione. Una pratica legge i form con
 * {@link #perPratica(String, String)}: la versione fissata al suo avvio (pratica_form, vedi VersioniForm), oppure
 * l'ultima se non ne ha una (form del progetto, pratiche avviate prima delle versioni).
 */
@Service
public class FormService {

    private static final String CLASSPATH_PATTERN = "classpath*:forms/*.json";
    public static final Set<String> TIPI_CAMPO = Set.of("text", "number", "date", "textarea", "select", "checkbox", ValoriForm.TABELLA,
        ValoriForm.ORGANIZZAZIONE, ValoriForm.ANAGRAFICA);
    public static final Set<String> TIPI_DOCUMENTI = Set.of("carica", "lettura", "modifica");

    private final FormDefinitionRepository repo;
    private final ObjectMapper mapper;

    /** Dice se un tipo di anagrafica esiste (per i campi anagrafica dei form); senza database, sempre sì. */
    private java.util.function.Predicate<String> anagraficaEsiste = codice -> true;

    public FormService(FormDefinitionRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    private FormVersioneRepository versioni;
    private PraticaFormRepository fissate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setVersioni(FormVersioneRepository versioni, PraticaFormRepository fissate) {
        this.versioni = versioni;
        this.fissate = fissate;
    }

    /** Formati di paese ammessi nei form nuovi o modificati (codice fiscale & co. solo con il modulo Italia). */
    private java.util.function.Predicate<String> formatoAmmesso = formato -> true;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setModuli(com.eligesoft.orditogram.modulo.ModuliService moduli) {
        this.formatoAmmesso = formato -> !RegoleCampo.FORMATI_ITALIA.contains(formato)
            || moduli.acceso(com.eligesoft.orditogram.modulo.Modulo.ITALIA);
    }

    /** Il formato del campo richiede un modulo spento: messaggio, oppure null. */
    private String formatoNonAmmesso(JsonNode campo) {
        String formato = campo.path("formato").asText("");
        return formato.isEmpty() || formatoAmmesso.test(formato) ? null
            : "il formato \"" + formato + "\" richiede il modulo Italia, spento in questa installazione (Studio → Moduli)";
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTipiAnagrafica(com.eligesoft.orditogram.anagrafica.TipoAnagraficaRepository tipi) {
        this.anagraficaEsiste = tipi::existsByCodice;
    }

    /** Form come oggetto JSON pronto per il frontend, oppure null se non esiste. */
    public JsonNode load(String formKey) {
        if (formKey == null || formKey.isBlank()) return null;
        try {
            Optional<FormDefinition> db = repo.findByFormKey(formKey);
            if (db.isPresent()) return mapper.readTree(db.get().getJson());
            String cp = readClasspath(formKey);
            return cp != null ? mapper.readTree(cp) : null;
        } catch (IOException e) {
            throw new IllegalStateException("Form non leggibile: " + formKey, e);
        }
    }

    /**
     * Il form come lo vede una pratica: la versione fissata al suo avvio, altrimenti l'ultima.
     * Usalo per tutto ciò che riguarda una pratica esistente (attività, scheda, documenti, chi vede cosa);
     * {@link #load(String)} resta per lo Studio e per le pratiche che stanno per partire.
     */
    public JsonNode perPratica(String processInstanceId, String formKey) {
        if (processInstanceId == null || formKey == null || formKey.isBlank() || fissate == null) return load(formKey);
        Optional<PraticaForm> fissata = fissate.findByProcessInstanceIdAndFormKey(processInstanceId, formKey);
        if (fissata.isEmpty()) return load(formKey);
        try {
            Optional<FormVersione> v = versioni.findByFormKeyAndVersione(formKey, fissata.get().getVersione());
            return v.isPresent() ? mapper.readTree(v.get().getJson()) : load(formKey);
        } catch (IOException e) {
            throw new IllegalStateException("Form non leggibile: " + formKey, e);
        }
    }

    /**
     * Fissa per la pratica l'ultima versione dei form indicati (quelli salvati nello Studio; i form del progetto non
     * hanno versioni). Non cambia quelle già fissate.
     */
    @Transactional
    public void fissa(String processInstanceId, Collection<String> formKeys) {
        if (fissate == null || processInstanceId == null) return;
        for (String fk : new LinkedHashSet<>(formKeys)) {
            if (fk == null || fk.isBlank() || fissate.findByProcessInstanceIdAndFormKey(processInstanceId, fk).isPresent()) continue;
            repo.findByFormKey(fk).ifPresent(f -> fissate.save(new PraticaForm(processInstanceId, fk, f.getVersione())));
        }
    }

    /** Le versioni salvate di un form, dalla più recente. */
    public List<FormVersione> versioni(String formKey) {
        return versioni == null ? List.of() : versioni.findByFormKeyOrderByVersioneDesc(formKey);
    }

    public boolean exists(String formKey) {
        return load(formKey) != null;
    }

    /** Elenco di tutti i formKey disponibili, con l'origine (db / classpath). */
    public List<Map<String, Object>> list() throws IOException {
        Map<String, Map<String, Object>> out = new TreeMap<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources(CLASSPATH_PATTERN)) {
            String name = Objects.requireNonNull(r.getFilename());
            String key = name.substring(0, name.length() - ".json".length());
            out.put(key, new LinkedHashMap<>(Map.of("formKey", key, "source", "progetto")));
        }
        for (FormDefinition f : repo.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("formKey", f.getFormKey());
            m.put("source", "database");
            m.put("versione", f.getVersione());
            m.put("updatedAt", f.getUpdatedAt());
            m.put("updatedBy", f.getUpdatedBy());
            out.put(f.getFormKey(), m);
        }
        return new ArrayList<>(out.values());
    }

    /** Salva (crea o sostituisce) un form su Postgres, dopo averlo validato. */
    @Transactional
    public JsonNode save(String formKey, String json, String user) {
        validateKey(formKey);
        JsonNode node = validate(formKey, json);
        FormDefinition f = repo.findByFormKey(formKey).orElseGet(FormDefinition::new);
        String testo = node.toPrettyString();
        // stesso contenuto (es. un pacchetto importato di nuovo): nessuna versione nuova
        if (f.getId() != null && testo.equals(f.getJson())) return node;
        OffsetDateTime adesso = OffsetDateTime.now();
        f.setFormKey(formKey);
        f.setJson(testo);
        f.setUpdatedAt(adesso);
        f.setUpdatedBy(user);
        if (versioni != null) {
            // dopo l'ultima mai salvata, anche se il form era stato eliminato: le pratiche vecchie la usano ancora
            int nuova = versioni.findTopByFormKeyOrderByVersioneDesc(formKey).map(v -> v.getVersione() + 1).orElse(1);
            f.setVersione(nuova);
            versioni.save(new FormVersione(formKey, nuova, testo, adesso, user));
        }
        repo.save(f);
        return node;
    }

    /**
     * Elimina il form su database: se esiste nel progetto, torna attivo quello. Le versioni restano, perché le
     * pratiche avviate con loro continuano a usarle.
     */
    @Transactional
    public void delete(String formKey) {
        repo.findByFormKey(formKey).ifPresent(repo::delete);
    }

    /** Nomi dei campi del form, con il tipo (usati per generare le extensions del processo). */
    public Map<String, String> fieldTypes(JsonNode form) {
        Map<String, String> out = new LinkedHashMap<>();
        if (form == null || !form.has("fields")) return out;
        for (JsonNode f : form.get("fields")) {
            String name = f.path("name").asText(null);
            if (name == null || name.isBlank()) continue;
            String tipo = f.path("type").asText();
            out.put(name, "checkbox".equals(tipo) ? "boolean" : ValoriForm.TABELLA.equals(tipo) ? "json" : "string");
        }
        return out;
    }

    // ---------------------------------------------------------------- validazione

    public JsonNode validate(String formKey, String json) {
        JsonNode node = validate(formKey, json, TIPI_CAMPO);
        // un campo anagrafica di un form punta a un tipo che esiste già (le anagrafiche controllano da sé le loro relazioni)
        for (JsonNode f : node.get("fields"))
            if (ValoriForm.ANAGRAFICA.equals(f.path("type").asText()) && !anagraficaEsiste.test(f.path("tipoAnagrafica").asText()))
                throw bad("Campo \"" + f.path("name").asText() + "\": l'anagrafica \"" + f.path("tipoAnagrafica").asText() + "\" non esiste");
        return node;
    }

    /**
     * Come sopra, con i tipi di campo ammessi indicati: le anagrafiche (anagrafica/DefinizioneAnagrafica) usano lo
     * stesso formato dei form e aggiungono il tipo "anagrafica" (relazione con un'altra scheda).
     */
    public JsonNode validate(String formKey, String json, Set<String> tipiAmmessi) {
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw bad("Il form \"" + formKey + "\" non è un JSON valido: " + e.getOriginalMessage());
        }
        if (node == null || !node.isObject()) throw bad("Il form \"" + formKey + "\" deve essere un oggetto JSON");
        JsonNode fields = node.get("fields");
        if (fields == null || !fields.isArray()) throw bad("Il form \"" + formKey + "\" deve avere un array \"fields\"");
        String documenti = node.path("documenti").asText("");
        if (!documenti.isEmpty() && !TIPI_DOCUMENTI.contains(documenti))
            throw bad("Nel form \"" + formKey + "\" \"documenti\" deve essere: carica, lettura o modifica");
        String visibilitaDocumenti = node.path("documentiVisibilita").asText("");
        if (!visibilitaDocumenti.isEmpty() && !Set.of("interna", "parti").contains(visibilitaDocumenti))
            throw bad("Nel form \"" + formKey + "\" \"documentiVisibilita\" deve essere: interna o parti");
        Set<String> names = new HashSet<>();
        Set<String> parti = new HashSet<>();
        for (JsonNode f : fields) {
            if (ValoriForm.sezione(f)) {
                if (f.path("label").asText("").isBlank())
                    throw bad("Nel form \"" + formKey + "\" c'è una sezione senza titolo");
                continue;
            }
            String name = f.path("name").asText("");
            if (name.isBlank()) throw bad("Nel form \"" + formKey + "\" c'è un campo senza nome");
            if (!name.matches("[a-zA-Z][\\w-]*"))
                throw bad("Campo \"" + name + "\": il nome deve iniziare con una lettera e contenere solo lettere, numeri, - e _");
            if (!names.add(name)) throw bad("Nel form \"" + formKey + "\" il campo \"" + name + "\" è duplicato");
            String type = f.path("type").asText("text");
            if (!tipiAmmessi.contains(type))
                throw bad("Campo \"" + name + "\": tipo \"" + type + "\" non supportato");
            if ("select".equals(type) && !f.path("options").isArray())
                throw bad("Campo \"" + name + "\": un select deve avere \"options\"");
            List<String> regole = RegoleCampo.problemi(f, type);
            if (!regole.isEmpty()) throw bad("Campo \"" + name + "\": " + regole.get(0));
            if (formatoNonAmmesso(f) != null) throw bad("Campo \"" + name + "\": " + formatoNonAmmesso(f));
            if (ValoriForm.TABELLA.equals(type)) validaTabella(name, f);
            if (ValoriForm.ANAGRAFICA.equals(type) && !f.path("tipoAnagrafica").asText("").matches("[a-z][a-z0-9-]{1,59}"))
                throw bad("Campo \"" + name + "\": indica il codice dell'anagrafica da cui scegliere (tipoAnagrafica)");
            validaAccesso(name, f, type, parti);
        }
        // condizioni: devono guardare un altro campo dello stesso form e indicare almeno un valore
        for (JsonNode f : fields) {
            if (!f.has("visibileSe")) continue;
            String name = f.path("name").asText("");
            ValoriForm.Condizione c = ValoriForm.condizione(f);
            if (ValoriForm.sezione(f))
                throw bad("Nel form \"" + formKey + "\" una sezione non può avere una condizione");
            if (c == null)
                throw bad("Campo \"" + name + "\": in \"visibileSe\" indica il \"campo\" da cui dipende");
            if (c.campo().equals(name))
                throw bad("Campo \"" + name + "\": la condizione non può dipendere dal campo stesso");
            if (!names.contains(c.campo()))
                throw bad("Campo \"" + name + "\": la condizione dipende da \"" + c.campo() + "\", che non è nel form");
            for (JsonNode o : fields)
                if (c.campo().equals(o.path("name").asText()) && ValoriForm.TABELLA.equals(o.path("type").asText()))
                    throw bad("Campo \"" + name + "\": la condizione non può dipendere da una tabella");
            if (c.valori().isEmpty() || c.valori().stream().anyMatch(String::isBlank))
                throw bad("Campo \"" + name + "\": la condizione deve indicare almeno un valore");
        }
        return node;
    }

    /**
     * Chi vede il campo: "visibileAlleParti" (anche gli utenti esterni), "ruoliVisibili" (solo chi ha uno di questi
     * ruoli, tra il personale) e, per un campo Organizzazione, "parte" (il ruolo della parte che indica).
     */
    private void validaAccesso(String name, JsonNode f, String type, Set<String> parti) {
        String campo = "Campo \"" + name + "\": ";
        if (f.has("visibileAlleParti") && !f.get("visibileAlleParti").isBoolean())
            throw bad(campo + "\"visibileAlleParti\" deve essere true o false");
        if (f.has("ruoliVisibili")) {
            JsonNode r = f.get("ruoliVisibili");
            if (!r.isArray()) throw bad(campo + "\"ruoliVisibili\" deve essere un elenco di codici di ruolo");
            for (JsonNode x : r)
                if (!x.isTextual() || !x.asText().matches("[a-z0-9][a-z0-9-]{1,59}"))
                    throw bad(campo + "in \"ruoliVisibili\" ci sono codici di ruolo non validi");
        }
        if (f.has("parte")) {
            if (!ValoriForm.ORGANIZZAZIONE.equals(type))
                throw bad(campo + "solo un campo Organizzazione può indicare una parte della pratica");
            String parte = f.path("parte").asText("");
            if (!com.eligesoft.orditogram.accesso.PartiService.RUOLO.matcher(parte).matches())
                throw bad(campo + "il ruolo della parte deve iniziare con una lettera e contenere solo lettere, numeri e _");
            if (!parti.add(parte)) throw bad(campo + "la parte \"" + parte + "\" è già indicata da un altro campo");
        }
    }

    /** Una tabella deve avere colonne con nome e tipo validi, regole sensate e un numero di righe coerente. */
    private void validaTabella(String name, JsonNode f) {
        String campo = "Campo \"" + name + "\": ";
        JsonNode colonne = f.get("colonne");
        if (colonne == null || !colonne.isArray() || colonne.isEmpty()) throw bad(campo + "una tabella deve avere almeno una colonna");
        if (colonne.size() > 20) throw bad(campo + "una tabella può avere al massimo 20 colonne");
        if (f.path("titolo").asBoolean(false)) throw bad(campo + "una tabella non può fare da titolo della pratica");
        Set<String> nomi = new HashSet<>();
        for (JsonNode c : colonne) {
            String nome = c.path("name").asText("");
            if (!nome.matches("[a-zA-Z][\\w-]*"))
                throw bad(campo + "ogni colonna ha un nome che inizia con una lettera e contiene solo lettere, numeri, - e _");
            if (!nomi.add(nome)) throw bad(campo + "la colonna \"" + nome + "\" è duplicata");
            String tipo = c.path("type").asText("text");
            if (!ValoriForm.TIPI_COLONNA.contains(tipo))
                throw bad(campo + "colonna \"" + nome + "\": tipo \"" + tipo + "\" non ammesso in una tabella");
            if ("select".equals(tipo) && (!c.path("options").isArray() || c.get("options").isEmpty()))
                throw bad(campo + "colonna \"" + nome + "\": una scelta deve avere le opzioni");
            List<String> regole = RegoleCampo.problemi(c, tipo);
            if (!regole.isEmpty()) throw bad(campo + "colonna \"" + nome + "\": " + regole.get(0));
            if (formatoNonAmmesso(c) != null) throw bad(campo + "colonna \"" + nome + "\": " + formatoNonAmmesso(c));
        }
        Integer min = RegoleCampo.intero(f.get("minRighe")), max = RegoleCampo.intero(f.get("maxRighe"));
        if (f.has("minRighe") && (min == null || min < 0)) throw bad(campo + "le righe minime devono essere un numero intero");
        if (f.has("maxRighe") && (max == null || max < 1 || max > ValoriForm.MAX_RIGHE))
            throw bad(campo + "le righe massime devono essere un numero da 1 a " + ValoriForm.MAX_RIGHE);
        if (min != null && max != null && min > max) throw bad(campo + "le righe minime sono più delle massime");
    }

    private void validateKey(String formKey) {
        if (formKey == null || !formKey.matches("[a-zA-Z0-9_-]{1,200}"))
            throw bad("formKey non valido: usa solo lettere, numeri, - e _");
    }

    private String readClasspath(String formKey) throws IOException {
        if (!formKey.matches("[a-zA-Z0-9_-]+")) return null;
        Resource r = new PathMatchingResourcePatternResolver().getResource("classpath:forms/" + formKey + ".json");
        if (!r.exists()) return null;
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
