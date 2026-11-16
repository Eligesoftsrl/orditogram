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
 */
@Service
public class FormService {

    private static final String CLASSPATH_PATTERN = "classpath*:forms/*.json";
    public static final Set<String> TIPI_CAMPO = Set.of("text", "number", "date", "textarea", "select", "checkbox", ValoriForm.TABELLA,
        ValoriForm.ORGANIZZAZIONE);
    public static final Set<String> TIPI_DOCUMENTI = Set.of("carica", "lettura", "modifica");

    private final FormDefinitionRepository repo;
    private final ObjectMapper mapper;

    public FormService(FormDefinitionRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
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
        f.setFormKey(formKey);
        f.setJson(node.toPrettyString());
        f.setUpdatedAt(OffsetDateTime.now());
        f.setUpdatedBy(user);
        repo.save(f);
        return node;
    }

    /** Elimina la versione su database: se esiste nel progetto, torna attiva quella. */
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
        return validate(formKey, json, TIPI_CAMPO);
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
            if (ValoriForm.TABELLA.equals(type)) validaTabella(name, f);
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
