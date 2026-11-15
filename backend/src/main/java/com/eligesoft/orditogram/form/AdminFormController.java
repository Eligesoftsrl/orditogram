package com.eligesoft.orditogram.form;

import com.eligesoft.orditogram.bozza.ProcessoBozza;
import com.eligesoft.orditogram.bozza.ProcessoBozzaRepository;
import com.eligesoft.orditogram.process.BpmnResourceService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Gestione form da interfaccia (solo admin, vedi SecurityConfig). */
@RestController
@RequestMapping("/api/admin/forms")
public class AdminFormController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminFormController.class);

    private final com.eligesoft.orditogram.registro.RegistroService registro;

    private final FormService forms;
    private final CurrentUserService currentUser;
    private final RepositoryService repositoryService;
    private final ProcessoBozzaRepository bozze;
    private final BpmnResourceService bpmn;

    public AdminFormController(FormService forms, CurrentUserService currentUser, RepositoryService repositoryService,
                               ProcessoBozzaRepository bozze, BpmnResourceService bpmn,
            com.eligesoft.orditogram.registro.RegistroService registro) {
        this.registro = registro;
        this.forms = forms;
        this.currentUser = currentUser;
        this.repositoryService = repositoryService;
        this.bozze = bozze;
        this.bpmn = bpmn;
    }

    /**
     * Elenco dei form con titolo, numero di campi e dove sono usati:
     * uso = [{ chiave, nome, passo, bozza }] per ogni processo (bozza se c'è, altrimenti la versione in produzione).
     */
    @GetMapping
    public List<Map<String, Object>> list() throws IOException {
        Map<String, List<Map<String, Object>>> uso = usoNeiProcessi();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : forms.list()) {
            Map<String, Object> m = new LinkedHashMap<>(f);
            String k = (String) f.get("formKey");
            JsonNode def = forms.load(k);
            m.put("titolo", def != null ? def.path("title").asText("") : "");
            m.put("campi", def != null && def.has("fields") ? def.get("fields").size() : 0);
            m.put("uso", uso.getOrDefault(k, List.of()));
            out.add(m);
        }
        return out;
    }

    private Map<String, List<Map<String, Object>>> usoNeiProcessi() {
        Map<String, Process> processi = new LinkedHashMap<>();
        Set<String> inBozza = new HashSet<>();
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery().latestVersion().list()) {
            try { processi.put(d.getKey(), repositoryService.getBpmnModel(d.getId()).getMainProcess()); }
            catch (Exception e) { log.debug("Definizione illeggibile, saltata: {}", e.getMessage()); }
        }
        for (ProcessoBozza b : bozze.findAll()) {
            try {
                Process p = bpmn.parse(b.getXml().getBytes(StandardCharsets.UTF_8)).getMainProcess();
                if (p != null) { processi.put(b.getChiave(), p); inBozza.add(b.getChiave()); }
            } catch (Exception e) { log.debug("Bozza {} non valida, saltata: {}", b.getChiave(), e.getMessage()); }
        }
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        processi.forEach((chiave, p) -> {
            String nome = p.getName() != null && !p.getName().isBlank() ? p.getName() : chiave;
            for (StartEvent s : p.findFlowElementsOfType(StartEvent.class, false))
                if (s.getFormKey() != null) aggiungi(out, s.getFormKey(), chiave, nome, "Avvio", inBozza.contains(chiave));
            for (UserTask t : p.findFlowElementsOfType(UserTask.class, true))
                if (t.getFormKey() != null)
                    aggiungi(out, t.getFormKey(), chiave, nome, t.getName() != null ? t.getName() : t.getId(), inBozza.contains(chiave));
        });
        return out;
    }

    private static void aggiungi(Map<String, List<Map<String, Object>>> out, String formKey, String chiave, String nome,
                                 String passo, boolean bozza) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chiave", chiave);
        m.put("nome", nome);
        m.put("passo", passo);
        m.put("bozza", bozza);
        out.computeIfAbsent(formKey, k -> new ArrayList<>()).add(m);
    }

    @GetMapping("/{formKey}")
    public JsonNode get(@PathVariable String formKey) {
        JsonNode f = forms.load(formKey);
        if (f == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Form non trovato");
        return f;
    }

    /** Body: il JSON del form così com'è. */
    @PutMapping("/{formKey}")
    public JsonNode save(@PathVariable String formKey, @RequestBody String json) {
        JsonNode salvato = forms.save(formKey, json, currentUser.username());
        registro.registra("FORM_SALVATO", formKey, null);
        return salvato;
    }

    @DeleteMapping("/{formKey}")
    public void delete(@PathVariable String formKey) {
        forms.delete(formKey);
        registro.registra("FORM_ELIMINATO", formKey, null);
    }
}
