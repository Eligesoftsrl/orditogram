package com.eligesoft.orditogram.process;

import com.eligesoft.orditogram.bozza.ProcessoBozzaRepository;
import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.CallActivity;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Gestione processi da interfaccia (solo admin):
 * elenco, diagramma, validazione senza pubblicare, pubblicazione a caldo di un BPMN con i suoi form.
 */
@RestController
@RequestMapping("/api/admin/processes")
public class AdminProcessController {

    private final com.eligesoft.orditogram.registro.RegistroService registro;

    private final RepositoryService repositoryService;
    private final BpmnResourceService bpmn;
    private final FormService forms;
    private final ProcessValidator validator;
    private final ProcessoBozzaRepository bozze;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;

    public AdminProcessController(RepositoryService repositoryService, BpmnResourceService bpmn,
                                  FormService forms, ProcessValidator validator, ProcessoBozzaRepository bozze,
                                  CurrentUserService currentUser, ObjectMapper mapper,
            com.eligesoft.orditogram.registro.RegistroService registro) {
        this.registro = registro;
        this.repositoryService = repositoryService;
        this.bpmn = bpmn;
        this.forms = forms;
        this.validator = validator;
        this.bozze = bozze;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ consultazione

    /** Ultima versione di ogni processo, con analisi di form, gruppi e problemi. */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery()
                .latestVersion().orderByProcessDefinitionKey().asc().list()) {
            Deployment dep = repositoryService.createDeploymentQuery().deploymentId(d.getDeploymentId()).singleResult();
            ProcessValidator.Analisi a = validator.analizza(repositoryService.getBpmnModel(d.getId()).getMainProcess(), Map.of());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("key", d.getKey());
            m.put("name", d.getName() != null ? d.getName() : d.getKey());
            m.put("description", d.getDescription());
            m.put("version", d.getVersion());
            m.put("versions", repositoryService.createProcessDefinitionQuery().processDefinitionKey(d.getKey()).count());
            m.put("deployedAt", dep != null ? dep.getDeploymentTime() : null);
            m.put("formKeys", a.formKeys());
            m.put("groups", a.gruppi());
            m.put("starterGroups", a.gruppiAvvio());
            m.put("warnings", a.avvisiTesto());
            m.put("errors", a.erroriTesto());
            m.put("bozza", bozze.existsByChiave(d.getKey()));
            out.add(m);
        }
        return out;
    }

    /** XML della versione in produzione (per il visualizzatore, l'editor e il download). */
    @GetMapping(value = "/{id}/xml", produces = MediaType.TEXT_XML_VALUE)
    public String xml(@PathVariable String id) {
        return bpmn.xml(id);
    }

    /** XML dell'ultima versione in produzione, per chiave. */
    @GetMapping(value = "/chiave/{key}/xml", produces = MediaType.TEXT_XML_VALUE)
    public String xmlPerChiave(@PathVariable String key) {
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key).latestVersion().singleResult();
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo non trovato: " + key);
        return bpmn.xml(d.getId());
    }

    // ------------------------------------------------------------ validazione

    /** Controlla un BPMN senza pubblicarlo. Body: { "xml": "..." }. Gli errori indicano l'elemento. */
    @PostMapping("/valida")
    public Map<String, Object> valida(@RequestBody Map<String, String> body) {
        String xml = body.getOrDefault("xml", "");
        Map<String, Object> out = new LinkedHashMap<>();
        Process p;
        BpmnModel modello;
        try {
            modello = bpmn.parse(xml.getBytes(StandardCharsets.UTF_8));
            p = processoEseguibile(modello);
        } catch (ResponseStatusException e) {
            out.put("errors", List.of(new ProcessValidator.Problema(e.getReason(), null)));
            out.put("warnings", List.of());
            return out;
        }
        ProcessValidator.Analisi a = validator.analizza(p, Map.of());
        a.errori().addAll(ProcessValidator.problemiMessaggi(modello));
        out.put("key", a.chiave());
        out.put("name", a.nome());
        out.put("formKeys", a.formKeys());
        out.put("errors", a.errori());
        out.put("warnings", a.avvisi());
        out.put("inProduzione", repositoryService.createProcessDefinitionQuery().processDefinitionKey(a.chiave()).count() > 0);
        return out;
    }

    // ------------------------------------------------------------ pubblicazione

    /**
     * Pubblicazione a caldo.
     *  - bpmn:       file .bpmn / .bpmn20.xml (obbligatorio, un solo processo eseguibile)
     *  - forms:      file <formKey>.json (facoltativi; quelli già presenti si possono omettere)
     *  - extensions: file -extensions.json (facoltativo; se manca viene generato dai campi dei form)
     * Tutto nella stessa transazione: se qualcosa fallisce non resta nulla a metà.
     * Dopo la pubblicazione l'eventuale bozza del processo viene eliminata.
     */
    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deploy(@RequestParam("bpmn") MultipartFile bpmnFile,
                                      @RequestParam(value = "forms", required = false) List<MultipartFile> formFiles,
                                      @RequestParam(value = "extensions", required = false) MultipartFile extFile)
            throws IOException {
        // chi pubblica senza il permesso sui form non li sovrascrive passando di qui
        if (formFiles != null && formFiles.stream().anyMatch(f -> !f.isEmpty())
                && !currentUser.puo(com.eligesoft.orditogram.accesso.Permesso.STUDIO_FORM))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                "Per caricare i form insieme al processo serve il permesso sui form");

        String fileName = Optional.ofNullable(bpmnFile.getOriginalFilename()).orElse("").toLowerCase();
        if (!(fileName.endsWith(".bpmn") || fileName.endsWith(".xml")))
            throw bad("Il file del processo deve avere estensione .bpmn o .bpmn20.xml");

        byte[] bytes = bpmnFile.getBytes();
        BpmnModel modello = bpmn.parse(bytes);
        Process process = processoEseguibile(modello);
        String key = process.getId();

        Map<String, JsonNode> uploaded = new LinkedHashMap<>();
        if (formFiles != null) {
            for (MultipartFile f : formFiles) {
                if (f.isEmpty()) continue;
                String name = Optional.ofNullable(f.getOriginalFilename()).orElse("");
                if (!name.toLowerCase().endsWith(".json")) throw bad("I form devono essere file .json: " + name);
                String formKey = name.substring(0, name.length() - ".json".length());
                uploaded.put(formKey, forms.validate(formKey, new String(f.getBytes(), StandardCharsets.UTF_8)));
            }
        }

        ProcessValidator.Analisi a = validator.analizza(process, uploaded);
        a.errori().addAll(ProcessValidator.problemiMessaggi(modello));
        if (!a.errori().isEmpty())
            throw bad("Il processo non può essere pubblicato:\n- " + String.join("\n- ", a.erroriTesto()));

        for (var e : uploaded.entrySet()) forms.save(e.getKey(), e.getValue().toString(), currentUser.username());

        String extJson;
        boolean generated;
        if (extFile != null && !extFile.isEmpty()) {
            extJson = new String(extFile.getBytes(), StandardCharsets.UTF_8);
            try { mapper.readTree(extJson); } catch (IOException e) { throw bad("Il file extensions non è un JSON valido"); }
            generated = false;
        } else {
            extJson = generaExtensions(process, a, uploaded);
            generated = true;
        }

        Deployment dep = repositoryService.createDeployment()
            .name(key)
            .addBytes(key + ".bpmn20.xml", bytes)
            .addString(key + "-extensions.json", extJson)
            .deploy();
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery().deploymentId(dep.getId()).singleResult();
        bozze.findByChiave(key).ifPresent(bozze::delete);
        registro.registra("PROCESSO_PUBBLICATO", key + " v" + def.getVersion(),
            "Form salvati: " + (uploaded.isEmpty() ? "nessuno" : String.join(", ", uploaded.keySet())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", def.getKey());
        result.put("name", def.getName() != null ? def.getName() : def.getKey());
        result.put("version", def.getVersion());
        result.put("formsSaved", uploaded.keySet());
        result.put("extensionsGenerated", generated);
        result.put("warnings", a.avvisiTesto());
        return result;
    }

    // ------------------------------------------------------------ supporto

    private Process processoEseguibile(BpmnModel model) {
        List<Process> eseguibili = model.getProcesses().stream().filter(Process::isExecutable).toList();
        if (eseguibili.size() != 1)
            throw bad("Il file deve contenere esattamente un processo eseguibile (isExecutable=\"true\"), trovati: " + eseguibili.size());
        return eseguibili.get(0);
    }

    /** File -extensions.json richiesto da Activiti 8: dati dai campi dei form, MAP_ALL su tutte le attività. */
    private String generaExtensions(Process p, ProcessValidator.Analisi a, Map<String, JsonNode> uploaded) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (String fk : a.formKeys()) {
            JsonNode form = uploaded.containsKey(fk) ? uploaded.get(fk) : forms.load(fk);
            forms.fieldTypes(form).forEach(vars::putIfAbsent);
        }
        ProcessValidator.DATI_DI_SISTEMA.forEach(v -> vars.putIfAbsent(v, "string"));
        // dati scritti dai connettori (es. numero di protocollo restituito da un'API)
        validator.datiProdotti(p).forEach(v -> vars.putIfAbsent(v, "string"));
        // dati restituiti dai processi richiamati (call activity)
        validator.datiDaiProcessiRichiamati(p).forEach(vars::putIfAbsent);

        ObjectNode root = mapper.createObjectNode();
        root.put("id", p.getId() + "-extensions");
        root.put("name", p.getId() + "-extensions");
        ObjectNode ext = root.putObject("extensions").putObject(p.getId());
        ObjectNode props = ext.putObject("properties");
        vars.forEach((name, type) -> {
            ObjectNode prop = props.putObject("p-" + name);
            prop.put("id", "p-" + name);
            prop.put("name", name);
            prop.put("type", type);
            prop.put("required", false);
        });
        ObjectNode mappings = ext.putObject("mappings");
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> mappings.putObject(t.getId()).put("mappingType", "MAP_ALL"));
        p.findFlowElementsOfType(ServiceTask.class, true).forEach(t -> mappings.putObject(t.getId()).put("mappingType", "MAP_ALL"));
        // il processo richiamato riceve tutti i dati della pratica e alla fine li restituisce tutti
        p.findFlowElementsOfType(CallActivity.class, true).forEach(c -> mappings.putObject(c.getId()).put("mappingType", "MAP_ALL"));
        ext.putObject("constants");
        return root.toPrettyString();
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
