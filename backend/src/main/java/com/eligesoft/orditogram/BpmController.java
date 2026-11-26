package com.eligesoft.orditogram;

import com.eligesoft.orditogram.accesso.PermessiProcessoService;
import com.eligesoft.orditogram.accesso.PraticaStruttura;
import com.eligesoft.orditogram.accesso.PraticaStrutturaRepository;
import com.eligesoft.orditogram.documento.DocumentoService;
import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.process.BpmnResourceService;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.ScadenzeService;
import com.eligesoft.orditogram.storico.StoricoService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.runtime.TaskRuntime;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * API generiche su processi e attività: funzionano per qualunque BPMN deployato.
 * Il comportamento si configura nel BPMN (gruppi, form) e nei form (campi, documenti).
 */
@RestController
@RequestMapping("/api")
public class BpmController {

    private final TaskRuntime taskRuntime;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final CurrentUserService currentUser;
    private final FormService forms;
    private final BpmnResourceService bpmn;
    private final StoricoService storico;
    private final DocumentoService documenti;
    private final ScadenzeService scadenze;
    private final ObjectMapper mapper;
    private final PermessiProcessoService permessiProcesso;
    private final PraticaStrutturaRepository praticaStruttura;
    private final com.eligesoft.orditogram.accesso.PartiService parti;
    private final com.eligesoft.orditogram.storico.CampiVisibili campiVisibili;
    private final com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche anagrafiche;

    public BpmController(TaskRuntime taskRuntime,
                         RepositoryService repositoryService, RuntimeService runtimeService,
                         CurrentUserService currentUser, FormService forms, BpmnResourceService bpmn,
                         StoricoService storico, DocumentoService documenti, ScadenzeService scadenze,
                         ObjectMapper mapper, PermessiProcessoService permessiProcesso,
                         PraticaStrutturaRepository praticaStruttura,
                         com.eligesoft.orditogram.accesso.PartiService parti,
                         com.eligesoft.orditogram.storico.CampiVisibili campiVisibili,
                         com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche anagrafiche) {
        this.anagrafiche = anagrafiche;
        this.parti = parti;
        this.campiVisibili = campiVisibili;
        this.permessiProcesso = permessiProcesso;
        this.praticaStruttura = praticaStruttura;
        this.taskRuntime = taskRuntime;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.currentUser = currentUser;
        this.forms = forms;
        this.bpmn = bpmn;
        this.storico = storico;
        this.documenti = documenti;
        this.scadenze = scadenze;
        this.mapper = mapper;
    }

    // ================================================================= processi

    /**
     * Processi che l'utente può avviare (ultima versione di ciascuno), con il form di avvio.
     * Se il BPMN ha activiti:candidateStarterGroups/Users, solo quei gruppi/utenti li vedono.
     */
    @GetMapping("/processes")
    public List<Map<String, Object>> processes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery().latestVersion().list()) {
            if (d.isSuspended() || !canStart(d.getId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", d.getKey());
            m.put("name", d.getName() != null ? d.getName() : d.getKey());
            m.put("description", d.getDescription());
            m.put("version", d.getVersion());
            m.put("startForm", forms.load(startFormKey(d.getId())));
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.get("name").toString().toLowerCase()));
        return out;
    }

    /** Avvio senza allegati (body JSON con i valori del form di avvio). */
    @PostMapping(value = "/processes/{key}/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public Map<String, Object> start(@PathVariable String key,
                                     @RequestBody(required = false) Map<String, Object> body) {
        return avvia(key, body, List.of());
    }

    /** Avvio con allegati (multipart: "data" = JSON dei valori, "files" = documenti). */
    @PostMapping(value = "/processes/{key}/start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startWithFiles(@PathVariable String key,
                                              @RequestParam("data") String data,
                                              @RequestParam(value = "files", required = false) List<MultipartFile> files)
            throws IOException {
        Map<String, Object> body = mapper.readValue(data, new TypeReference<>() {});
        return avvia(key, body, files != null ? files : List.of());
    }

    private Map<String, Object> avvia(String key, Map<String, Object> body, List<MultipartFile> files) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key).latestVersion().singleResult();
        if (def == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo non trovato: " + key);
        if (!canStart(def.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non sei autorizzato ad avviare questo processo");

        JsonNode form = forms.load(startFormKey(def.getId()));
        Map<String, Object> vars = valoriAmmessi(form, body);
        controllaObbligatori(form, vars);

        List<MultipartFile> allegati = files.stream().filter(f -> !f.isEmpty()).toList();
        String modoDocumenti = form != null ? form.path("documenti").asText("") : "";
        if (!allegati.isEmpty() && !"carica".equals(modoDocumenti))
            throw bad("Questo processo non prevede allegati all'avvio");
        if (form != null && "carica".equals(modoDocumenti) && form.path("documentiObbligatori").asBoolean(false) && allegati.isEmpty())
            throw bad("Allega almeno un documento");

        // parti della pratica: chi avvia, se è un utente esterno, e le organizzazioni indicate nel form
        var io = currentUser.current();
        // schede di anagrafica scelte nel form: controllate per chi avvia, con la fotografia nelle variabili
        var schedeScelte = anagrafiche.daForm(form, vars, io);
        vars.putAll(com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche.variabili(schedeScelte));
        List<com.eligesoft.orditogram.accesso.PartiService.Parte> partiNuove = new ArrayList<>();
        parti.richiedente(io).ifPresent(partiNuove::add);
        partiNuove.addAll(parti.daForm(form, vars));
        vars.putAll(com.eligesoft.orditogram.accesso.PartiService.variabili(partiNuove));

        vars.put("avviataDa", io.getFullName());
        // username di chi avvia: serve ai connettori che partono subito (es. email a "chi ha avviato la pratica")
        vars.put("avviataDaUtente", currentUser.username());

        // l'autorizzazione l'ha già data canStart (BPMN o regole "avvia" dello Studio): si avvia dal motore,
        // con l'utente come iniziatore, così le regole dello Studio possono anche allargare quelle del BPMN
        ProcessInstance instance;
        org.activiti.engine.impl.identity.Authentication.setAuthenticatedUserId(currentUser.username());
        try {
            instance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(def.getId())
                .name(titoloPratica(form, vars, def))
                .variables(vars)
                .start();
        } finally {
            org.activiti.engine.impl.identity.Authentication.setAuthenticatedUserId(null);
        }
        // struttura competente: quella di chi avvia, se ne ha una sola (le organizzazioni degli esterni non lo sono)
        Set<Long> struttureMie = io.getOrganizzazioni();
        if (!io.isEsterno() && struttureMie.size() == 1)
            praticaStruttura.save(new PraticaStruttura(instance.getId(), struttureMie.iterator().next()));

        storico.registra(instance.getId(), PraticaEvento.Tipo.AVVIO,
            def.getName() != null ? def.getName() : key, storico.dettagliDaForm(form, vars));
        parti.registra(instance.getId(), partiNuove);
        anagrafiche.registra(instance.getId(), schedeScelte, currentUser.username());
        boolean perLeParti = io.isEsterno() || "parti".equals(form != null ? form.path("documentiVisibilita").asText() : "");
        for (MultipartFile f : allegati) documenti.aggiungi(instance.getId(), f, null, "Avvio", perLeParti);

        return Map.of("id", instance.getId(), "name", Objects.toString(instance.getName(), ""));
    }

    // ================================================================= attività

    /** Attività in carico all'utente o disponibili per i suoi gruppi. */
    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks() {
        // le scadenze si leggono una volta per pratica, anche se la pratica ha più attività aperte
        Map<String, List<ScadenzeService.Scadenza>> cache = new HashMap<>();
        return taskRuntime.tasks(Pageable.of(0, 200)).getContent().stream()
            .map(t -> toMap(t, cache))
            .toList();
    }

    /** Dettaglio: valori correnti + form. */
    @com.eligesoft.orditogram.registro.RegistraLettura(value = "ATTIVITA_APERTA", oggetto = "id")
    @GetMapping("/tasks/{id}")
    public Map<String, Object> task(@PathVariable String id) {
        Task task = taskRuntime.task(id);
        Map<String, Object> variables = new HashMap<>();
        taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(id).build())
            .forEach(v -> variables.put(v.getName(), v.getValue()));

        JsonNode form = forms.perPratica(task.getProcessInstanceId(), task.getFormKey());
        // solo i dati che l'utente può vedere, più quelli del form dell'attività (vedi storico/CampiVisibili)
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
        if (pi != null) {
            var visibile = campiVisibili.perAttivita(currentUser.current(), pi.getProcessDefinitionId(), pi.getId(), form);
            variables.keySet().removeIf(nome -> !visibile.test(nome));
        }
        Map<String, Object> result = new LinkedHashMap<>(toMap(task));
        result.put("variables", variables);
        result.put("form", form);
        // i nomi delle organizzazioni scelte nei campi Organizzazione, per mostrarli senza sfogliare l'anagrafica
        Map<String, String> nomi = new HashMap<>();
        if (form != null) form.path("fields").forEach(f -> {
            Object v = variables.get(f.path("name").asText());
            if (ValoriForm.ORGANIZZAZIONE.equals(f.path("type").asText()) && v != null)
                parti.esternaAttiva(v.toString()).ifPresent(o -> nomi.put(v.toString(), o.getNome()));
        });
        result.put("organizzazioni", nomi);
        return result;
    }

    /** Diagramma BPMN con gli step attivi in questo momento. */
    @GetMapping("/tasks/{id}/diagram")
    public Map<String, Object> diagram(@PathVariable String id) {
        Task task = taskRuntime.task(id);   // verifica anche che l'utente possa vedere il task
        String processDefinitionId = runtimeService.createProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId()).singleResult().getProcessDefinitionId();
        return Map.of(
            "xml", bpmn.xml(processDefinitionId),
            "activeIds", runtimeService.getActiveActivityIds(task.getProcessInstanceId()));
    }

    @PostMapping("/tasks/{id}/claim")
    @Transactional
    public Map<String, Object> claim(@PathVariable String id) {
        Task task = taskRuntime.task(id);
        checkNotTakenByOthers(task);
        if (task.getAssignee() == null) {
            taskRuntime.claim(TaskPayloadBuilder.claim().withTaskId(id).build());
            storico.registra(task.getProcessInstanceId(), PraticaEvento.Tipo.PRESA_IN_CARICO, task.getName(), null);
        }
        return toMap(taskRuntime.task(id));
    }

    @PostMapping("/tasks/{id}/release")
    @Transactional
    public Map<String, Object> release(@PathVariable String id) {
        Task task = taskRuntime.task(id);
        if (!currentUser.username().equals(task.getAssignee()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Puoi rilasciare solo le attività in carico a te");
        taskRuntime.release(TaskPayloadBuilder.release().withTaskId(id).build());
        storico.registra(task.getProcessInstanceId(), PraticaEvento.Tipo.RILASCIO, task.getName(), null);
        return toMap(taskRuntime.task(id));
    }

    /**
     * Completa l'attività. Accetta solo i campi modificabili del form e controlla gli obbligatori.
     * Se l'attività è ancora libera la prende in carico prima. Registra tutto nello storico.
     */
    @PostMapping("/tasks/{id}/complete")
    @Transactional
    public Map<String, Object> complete(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Task task = taskRuntime.task(id);
        checkNotTakenByOthers(task);
        JsonNode form = forms.perPratica(task.getProcessInstanceId(), task.getFormKey());
        // i dati già nella pratica servono alle condizioni che guardano campi in sola lettura
        Map<String, Object> pratica = new HashMap<>();
        taskRuntime.variables(TaskPayloadBuilder.variables().withTaskId(id).build())
            .forEach(v -> pratica.put(v.getName(), v.getValue()));
        Map<String, Object> vars = ValoriForm.ammessi(form, body, pratica);
        List<String> mancanti = ValoriForm.obbligatoriMancanti(form, vars, pratica);
        if (!mancanti.isEmpty()) throw bad("Compila i campi obbligatori: " + String.join(", ", mancanti));
        controllaRegole(form, vars, pratica);
        // un campo Organizzazione marcato come parte aggiunge (o cambia) la parte prima che il processo prosegua:
        // così l'attività successiva assegnata a quella parte la trova già
        var partiNuove = parti.daForm(form, vars);
        vars.putAll(com.eligesoft.orditogram.accesso.PartiService.variabili(partiNuove));
        var schedeScelte = anagrafiche.daForm(form, vars, currentUser.current(), pratica);
        vars.putAll(com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche.variabili(schedeScelte));

        if (task.getAssignee() == null) {
            taskRuntime.claim(TaskPayloadBuilder.claim().withTaskId(id).build());
        }
        // lo storico prima del complete: se il processo prosegue con una notifica, l'ordine resta corretto
        storico.registra(task.getProcessInstanceId(), PraticaEvento.Tipo.COMPLETAMENTO, task.getName(),
            storico.dettagliDaForm(form, vars));
        parti.registra(task.getProcessInstanceId(), partiNuove);
        anagrafiche.registra(task.getProcessInstanceId(), schedeScelte, currentUser.username());

        vars.put("chiusaDa", currentUser.current().getFullName());
        vars.put("chiusaIl", OffsetDateTime.now().toString());
        taskRuntime.complete(TaskPayloadBuilder.complete().withTaskId(id).withVariables(vars).build());
        return Map.of("completed", id, "chiusaDa", vars.get("chiusaDa"));
    }

    // ================================================================= supporto

    /**
     * Chi può avviare: le regole "avvia" dello Studio, se il processo ne ha; altrimenti candidateStarterGroups/Users
     * del BPMN e, se non indicati, tutti. Nessuna eccezione per l'amministratore.
     */
    private boolean canStart(String processDefinitionId) {
        Process p = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
        return permessiProcesso.puoAvviare(currentUser.username(), p.getId(), () -> canStartBpmn(p));
    }

    private boolean canStartBpmn(Process p) {
        List<String> groups = p.getCandidateStarterGroups();
        List<String> users = p.getCandidateStarterUsers();
        if (groups.isEmpty() && users.isEmpty()) return true;
        if (users.contains(currentUser.username())) return true;
        Set<String> mine = currentUser.groups();
        return groups.stream().anyMatch(mine::contains);
    }

    /** Solo i campi modificabili dichiarati nel form (regole comuni in form/ValoriForm). */
    private Map<String, Object> valoriAmmessi(JsonNode form, Map<String, Object> body) {
        return ValoriForm.ammessi(form, body);
    }

    private void controllaObbligatori(JsonNode form, Map<String, Object> vars) {
        List<String> mancanti = ValoriForm.obbligatoriMancanti(form, vars);
        if (!mancanti.isEmpty()) throw bad("Compila i campi obbligatori: " + String.join(", ", mancanti));
        controllaRegole(form, vars, Map.of());
    }

    /** Regole dei campi (min/max, lunghezza, formato), uguali a quelle dell'interfaccia e dell'API. */
    private void controllaRegole(JsonNode form, Map<String, Object> vars, Map<String, Object> pratica) {
        List<ValoriForm.Errore> errori = ValoriForm.erroriRegole(form, vars, pratica, LocalDate.now());
        if (!errori.isEmpty()) throw bad("Controlla i campi: " + ValoriForm.descriviErrori(form, errori));
    }

    private String titoloPratica(JsonNode form, Map<String, Object> vars, ProcessDefinition def) {
        return ValoriForm.titolo(form, vars, def.getName() != null ? def.getName() : def.getKey(), LocalDate.now());
    }

    private void checkNotTakenByOthers(Task task) {
        String assignee = task.getAssignee();
        if (assignee != null && !assignee.equals(currentUser.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "L'attività è già in carico a " + currentUser.fullNameOf(assignee));
        }
    }

    private Map<String, Object> toMap(Task t) {
        return toMap(t, new HashMap<>());
    }

    private Map<String, Object> toMap(Task t, Map<String, List<ScadenzeService.Scadenza>> cacheScadenze) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("formKey", t.getFormKey());
        m.put("assignee", t.getAssignee());
        m.put("assigneeName", currentUser.fullNameOf(t.getAssignee()));
        m.put("mine", t.getAssignee() != null && t.getAssignee().equals(currentUser.username()));
        m.put("processInstanceId", t.getProcessInstanceId());
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
            .processInstanceId(t.getProcessInstanceId()).singleResult();
        m.put("pratica", pi != null ? pi.getName() : null);
        if (pi != null) {
            ProcessDefinition def = repositoryService.getProcessDefinition(pi.getProcessDefinitionId());
            m.put("processo", def.getName() != null ? def.getName() : def.getKey());
        } else {
            m.put("processo", null);
        }
        m.put("createdDate", t.getCreatedDate());
        // scadenze e solleciti agganciati a questa attività (timer sul bordo), dal più vicino
        List<ScadenzeService.Scadenza> tutte = t.getProcessInstanceId() == null ? List.of()
            : cacheScadenze.computeIfAbsent(t.getProcessInstanceId(), scadenze::perPratica);
        m.put("scadenze", tutte.stream()
            .filter(s -> t.getTaskDefinitionKey() != null && t.getTaskDefinitionKey().equals(s.attivita()))
            .map(ScadenzeService::comeMappa)
            .toList());
        return m;
    }

    private String startFormKey(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId).getMainProcess()
            .findFlowElementsOfType(StartEvent.class).stream()
            .map(StartEvent::getFormKey)
            .filter(Objects::nonNull)
            .findFirst().orElse(null);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
