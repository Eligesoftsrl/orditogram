package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.process.BpmnResourceService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.runtime.TaskRuntime;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.task.IdentityLink;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Le pratiche viste dall'utente: elenco (in corso / concluse), dettaglio con percorso,
 * dati e storico, diagramma con lo stato. Ognuno vede solo le pratiche a cui partecipa (admin tutte).
 */
@RestController
@RequestMapping("/api/pratiche")
public class PraticaController {

    /** Variabili tecniche che non si mostrano tra i "dati della pratica". */
    private static final Set<String> NASCOSTE = Set.of("chiusaDa", "chiusaIl", "initiator", "avviataDaUtente");

    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final TaskRuntime taskRuntime;
    private final StoricoService storico;
    private final PraticaAccessService access;
    private final CurrentUserService currentUser;
    private final FormService forms;
    private final BpmnResourceService bpmn;
    private final ScadenzeService scadenze;
    private final com.eligesoft.orditogram.messaggio.MessaggiService messaggi;

    public PraticaController(HistoryService historyService, RuntimeService runtimeService,
                             RepositoryService repositoryService, TaskService taskService,
                             TaskRuntime taskRuntime, StoricoService storico, PraticaAccessService access,
                             CurrentUserService currentUser, FormService forms, BpmnResourceService bpmn, ScadenzeService scadenze,
                             com.eligesoft.orditogram.messaggio.MessaggiService messaggi) {
        this.historyService = historyService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.taskService = taskService;
        this.taskRuntime = taskRuntime;
        this.storico = storico;
        this.access = access;
        this.currentUser = currentUser;
        this.forms = forms;
        this.bpmn = bpmn;
        this.scadenze = scadenze;
        this.messaggi = messaggi;
    }

    /**
     * stato: "in-corso" | "concluse" | "tutte"; q: testo da cercare nel titolo.
     * Restituisce al massimo 200 pratiche, le più recenti prima.
     */
    /** Dimensione massima di una pagina (anche per le esportazioni, che chiedono pagine grandi). */
    private static final int MAX_PAGINA = 1000;

    /**
     * Pratiche visibili all'utente, cercate e paginate sul database.
     * q: parole nel titolo della pratica o nel nome della tipologia (senza distinzione di maiuscole).
     * processo: chiave della tipologia. Risposta: { righe, totale, pagina, dimensione }.
     */
    @GetMapping
    public Map<String, Object> elenco(@RequestParam(defaultValue = "tutte") String stato,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String processo,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "25") int dimensione) {
        int dim = Math.max(1, Math.min(MAX_PAGINA, dimensione));
        int pag = Math.max(0, pagina);
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
            .includeProcessVariables()
            .orderByProcessInstanceStartTime().desc();
        if ("in-corso".equals(stato)) query.unfinished();
        if ("concluse".equals(stato)) query.finished();
        if (processo != null && !processo.isBlank()) query.processDefinitionKey(processo.trim());

        if (!currentUser.isAdmin()) {
            Set<String> mie = new HashSet<>(storico.praticheDi(currentUser.username()));
            // anche quelle con un'attività disponibile per i miei gruppi
            taskRuntime.tasks(Pageable.of(0, 500)).getContent()
                .forEach(t -> mie.add(t.getProcessInstanceId()));
            if (mie.isEmpty()) return pagina(List.of(), 0, pag, dim);
            query.processInstanceIds(mie);
        }

        // ricerca sul database: titolo della pratica OPPURE tipologia il cui nome contiene il testo
        String cerca = q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        if (!cerca.isEmpty()) {
            List<String> tipologie = repositoryService.createProcessDefinitionQuery().latestVersion().list().stream()
                .filter(d -> Objects.toString(d.getName(), d.getKey()).toLowerCase(Locale.ROOT).contains(cerca))
                .map(d -> d.getKey()).distinct().toList();
            query.or().processInstanceNameLikeIgnoreCase("%" + cerca + "%");
            if (!tipologie.isEmpty()) query.processDefinitionKeyIn(tipologie);
            query.endOr();
        }

        long totale = query.count();
        Map<String, String> nomi = new HashMap<>();
        List<Map<String, Object>> righe = query.listPage(pag * dim, dim).stream()
            .map(h -> riepilogo(h, nomi))
            .toList();
        return pagina(righe, totale, pag, dim);
    }

    /** Tipologie di pratica (ultima versione di ogni processo), per il filtro dell'elenco. */
    @GetMapping("/tipologie")
    public List<Map<String, String>> tipologie() {
        return repositoryService.createProcessDefinitionQuery().latestVersion().list().stream()
            .map(d -> Map.of("chiave", d.getKey(), "nome", d.getName() != null ? d.getName() : d.getKey()))
            .sorted(Comparator.comparing(m -> m.get("nome")))
            .toList();
    }

    private static Map<String, Object> pagina(List<Map<String, Object>> righe, long totale, int pagina, int dimensione) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("righe", righe);
        m.put("totale", totale);
        m.put("pagina", pagina);
        m.put("dimensione", dimensione);
        return m;
    }

    @GetMapping("/{pi}")
    public Map<String, Object> dettaglio(@PathVariable String pi) {
        access.checkView(pi);
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(pi).includeProcessVariables().singleResult();
        if (h == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pratica non trovata");

        Map<String, Object> m = riepilogo(h, new HashMap<>());
        m.put("dati", dati(h));
        m.put("eventi", storico.eventi(pi));
        // l'attività che l'utente può lavorare su questa pratica, se c'è
        var mie = taskRuntime.tasks(Pageable.of(0, 10),
            TaskPayloadBuilder.tasks().withProcessInstanceId(pi).build()).getContent();
        m.put("taskId", mie.isEmpty() ? null : mie.get(0).getId());
        return m;
    }

    /** Diagramma con step attivi (in corso) e step già percorsi. */
    @GetMapping("/{pi}/diagramma")
    public Map<String, Object> diagramma(@PathVariable String pi) {
        access.checkView(pi);
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(pi).singleResult();
        if (h == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pratica non trovata");
        List<String> attivi = h.getEndTime() == null ? runtimeService.getActiveActivityIds(pi) : List.of();
        Set<String> percorsi = new LinkedHashSet<>();
        for (HistoricActivityInstance a : historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(pi).finished().list()) {
            percorsi.add(a.getActivityId());
        }
        return Map.of("xml", bpmn.xml(h.getProcessDefinitionId()), "activeIds", attivi,
            "completedIds", percorsi);
    }

    // ------------------------------------------------------------ costruzione risposta

    /** Nome leggibile del processo (con cache per richiesta). */
    private String nomeProcesso(HistoricProcessInstance h, Map<String, String> cache) {
        return cache.computeIfAbsent(h.getProcessDefinitionId(), id -> {
            var def = repositoryService.getProcessDefinition(id);
            return def.getName() != null ? def.getName() : def.getKey();
        });
    }

    private Map<String, Object> riepilogo(HistoricProcessInstance h, Map<String, String> nomi) {
        Map<String, Object> vars = h.getProcessVariables() != null ? h.getProcessVariables() : Map.of();
        boolean conclusa = h.getEndTime() != null;
        List<Map<String, Object>> correnti = conclusa ? List.of() : stepCorrenti(h.getId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("titolo", h.getName() != null ? h.getName() : h.getProcessDefinitionKey());
        m.put("processo", nomeProcesso(h, nomi));
        m.put("processoKey", h.getProcessDefinitionKey());
        m.put("stato", conclusa ? "conclusa" : "in corso");
        m.put("avviataDa", vars.get("avviataDa"));
        m.put("inizio", h.getStartTime());
        m.put("fine", h.getEndTime());
        m.put("stepCorrenti", correnti);
        m.put("percorso", percorso(h.getId(), correnti, conclusa, h.getEndTime()));
        // scadenze, solleciti e attese ancora programmati; la prima è la più vicina
        List<Map<String, Object>> sc = conclusa ? List.of()
            : scadenze.perPratica(h.getId()).stream().map(ScadenzeService::comeMappa).toList();
        m.put("scadenze", sc);
        m.put("prossimaScadenza", sc.stream().filter(x -> !"attesa".equals(x.get("tipo"))).findFirst().orElse(null));
        // messaggi che la pratica aspetta da sistemi esterni o da un operatore
        m.put("messaggiAttesi", conclusa ? List.of() : messaggi.attesi(h.getId(), h.getProcessDefinitionId()).stream()
            .map(x -> Map.of("nome", x.nome(), "elemento", x.elemento(), "tipo", x.tipo(),
                "attivita", x.attivita() == null ? "" : x.attivita()))
            .toList());
        return m;
    }

    /**
     * Il "binario" della pratica: le fermate realmente percorse, compresi i ritorni.
     * Avvio → attività completate (in ordine) → attività correnti oppure "Conclusa".
     */
    private List<Map<String, Object>> percorso(String pi, List<Map<String, Object>> correnti,
                                               boolean conclusa, Date fine) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : storico.eventi(pi)) {
            String tipo = (String) e.get("tipo");
            if (!"AVVIO".equals(tipo) && !"COMPLETAMENTO".equals(tipo)) continue;
            out.add(fermata("AVVIO".equals(tipo) ? "Avvio" : (String) e.get("titolo"), "fatto",
                (String) e.get("utenteNome"), e.get("quando")));
        }
        if (out.isEmpty()) out.add(fermata("Avvio", "fatto", null, null));   // pratiche precedenti allo storico
        for (Map<String, Object> c : correnti)
            out.add(fermata((String) c.get("nome"), "corrente", (String) c.get("assegnatario"), null));
        if (conclusa) out.add(fermata("Conclusa", "fine", null, fine));
        return out;
    }

    private static Map<String, Object> fermata(String nome, String stato, String chi, Object quando) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("stato", stato);
        m.put("chi", chi);
        m.put("quando", quando);
        return m;
    }

    private List<Map<String, Object>> stepCorrenti(String pi) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (org.activiti.engine.task.Task t : taskService.createTaskQuery().processInstanceId(pi).list()) {
            List<String> gruppi = new ArrayList<>();
            for (IdentityLink l : taskService.getIdentityLinksForTask(t.getId()))
                if ("candidate".equals(l.getType()) && l.getGroupId() != null) gruppi.add(l.getGroupId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", t.getName());
            m.put("chiave", t.getTaskDefinitionKey());
            m.put("assegnatario", currentUser.fullNameOf(t.getAssignee()));
            m.put("gruppi", gruppi);
            m.put("dal", t.getCreateTime());
            out.add(m);
        }
        return out;
    }

    /**
     * Dati della pratica con le etichette prese dai form del processo
     * (avvio e attività). Le variabili senza etichetta mostrano il nome tecnico.
     */
    private List<Map<String, Object>> dati(HistoricProcessInstance h) {
        Map<String, String> etichette = new LinkedHashMap<>();
        Map<String, String> tipi = new HashMap<>();
        Process p = repositoryService.getBpmnModel(h.getProcessDefinitionId()).getMainProcess();
        List<String> formKeys = new ArrayList<>();
        p.findFlowElementsOfType(StartEvent.class).forEach(s -> { if (s.getFormKey() != null) formKeys.add(s.getFormKey()); });
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (t.getFormKey() != null) formKeys.add(t.getFormKey()); });
        for (String fk : formKeys) {
            JsonNode form = forms.load(fk);
            if (form == null || !form.has("fields")) continue;
            for (JsonNode f : form.get("fields")) {
                etichette.putIfAbsent(f.path("name").asText(), f.path("label").asText(f.path("name").asText()));
                tipi.putIfAbsent(f.path("name").asText(), f.path("type").asText("text"));
            }
        }
        etichette.putIfAbsent("avviataDa", "Avviata da");

        Map<String, Object> vars = h.getProcessVariables() != null ? h.getProcessVariables() : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        // prima nell'ordine dei form, poi le altre
        List<String> ordine = new ArrayList<>(etichette.keySet());
        vars.keySet().stream().sorted().filter(k -> !etichette.containsKey(k)).forEach(ordine::add);
        for (String k : ordine) {
            if (NASCOSTE.contains(k) || !vars.containsKey(k)) continue;
            Object v = vars.get(k);
            if (v == null || v.toString().isBlank()) continue;
            String testo = "checkbox".equals(tipi.get(k)) ? (Boolean.TRUE.equals(v) ? "Sì" : "No") : v.toString();
            out.add(Map.of("nome", k, "label", etichette.getOrDefault(k, k), "value", testo,
                "tipo", tipi.getOrDefault(k, "text")));
        }
        return out;
    }
}
