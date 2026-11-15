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
    public static final Set<String> TIPI_CAMPO = Set.of("text", "number", "date", "textarea", "select", "checkbox");
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
            out.put(name, "checkbox".equals(f.path("type").asText()) ? "boolean" : "string");
        }
        return out;
    }

    // ---------------------------------------------------------------- validazione

    public JsonNode validate(String formKey, String json) {
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
        Set<String> names = new HashSet<>();
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
            if (!TIPI_CAMPO.contains(type))
                throw bad("Campo \"" + name + "\": tipo \"" + type + "\" non supportato");
            if ("select".equals(type) && !f.path("options").isArray())
                throw bad("Campo \"" + name + "\": un select deve avere \"options\"");
            List<String> regole = RegoleCampo.problemi(f, type);
            if (!regole.isEmpty()) throw bad("Campo \"" + name + "\": " + regole.get(0));
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
            if (c.valori().isEmpty() || c.valori().stream().anyMatch(String::isBlank))
                throw bad("Campo \"" + name + "\": la condizione deve indicare almeno un valore");
        }
        return node;
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
