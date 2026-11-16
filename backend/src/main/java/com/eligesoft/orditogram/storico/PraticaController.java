package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche;
import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.form.ValoriForm;
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
    private final VisibilitaPratiche visibilita;
    private final com.eligesoft.orditogram.accesso.PraticaStrutturaRepository praticaStruttura;
    private final com.eligesoft.orditogram.accesso.OrganizzazioneRepository organizzazioni;
    private final com.eligesoft.orditogram.accesso.PermessiProcessoService permessiProcesso;
    private final com.eligesoft.orditogram.registro.RegistroService registro;
    private final com.eligesoft.orditogram.accesso.PartiService parti;
    private final AnagraficheNellePratiche anagrafiche;
    private final CampiVisibili campiVisibili;

    /** Chi guarda la pratica: gli utenti esterni ne vedono una versione ridotta (vedi CampiVisibili). */
    private record Vista(com.eligesoft.orditogram.user.AppUser utente) {
        boolean esterno() { return utente.isEsterno(); }
        boolean io(Object username) { return utente.getUsername().equals(username); }
    }

    public PraticaController(HistoryService historyService, RuntimeService runtimeService,
                             RepositoryService repositoryService, TaskService taskService,
                             TaskRuntime taskRuntime, StoricoService storico, PraticaAccessService access,
                             CurrentUserService currentUser, FormService forms, BpmnResourceService bpmn, ScadenzeService scadenze,
                             com.eligesoft.orditogram.messaggio.MessaggiService messaggi, VisibilitaPratiche visibilita,
                             com.eligesoft.orditogram.accesso.PermessiProcessoService permessiProcesso,
                             com.eligesoft.orditogram.registro.RegistroService registro,
                             com.eligesoft.orditogram.accesso.PraticaStrutturaRepository praticaStruttura,
                             com.eligesoft.orditogram.accesso.OrganizzazioneRepository organizzazioni,
                             com.eligesoft.orditogram.accesso.PartiService parti, CampiVisibili campiVisibili,
                             AnagraficheNellePratiche anagrafiche) {
        this.anagrafiche = anagrafiche;
        this.parti = parti;
        this.campiVisibili = campiVisibili;
        this.praticaStruttura = praticaStruttura;
        this.organizzazioni = organizzazioni;
        this.visibilita = visibilita;
        this.permessiProcesso = permessiProcesso;
        this.registro = registro;
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
    /** Dimensione massima di una pagina dell'elenco a video. */
    private static final int MAX_PAGINA_ELENCO = 100;

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
                                      @RequestParam(defaultValue = "25") int dimensione,
                                      @RequestParam(defaultValue = "false") boolean esporta) {
        // pagine grandi solo per le esportazioni, che seguono le regole "esporta" dei processi
        int dim = Math.max(1, Math.min(esporta ? MAX_PAGINA : MAX_PAGINA_ELENCO, dimensione));
        int pag = Math.max(0, pagina);
        String utente = currentUser.username();

        // ricerca: titolo della pratica OPPURE tipologia il cui nome contiene il testo
        String cerca = q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        List<String> tipologie = cerca.isEmpty() ? List.of()
            : repositoryService.createProcessDefinitionQuery().latestVersion().list().stream()
                .filter(d -> Objects.toString(d.getName(), d.getKey()).toLowerCase(Locale.ROOT).contains(cerca))
                .map(d -> d.getKey()).distinct().toList();
        Set<String> esclusi = esporta ? permessiProcesso.nonEsportabili(utente) : Set.of();

        VisibilitaPratiche.Pagina trovate = visibilita.cerca(utente,
            new VisibilitaPratiche.Ricerca(stato, processo, cerca, tipologie, esclusi), pag, dim);
        if (esporta && pag == 0)
            registro.registra("PRATICHE_ESPORTATE", processo == null || processo.isBlank() ? "tutte le tipologie" : processo,
                trovate.totale() + " pratiche" + (cerca.isEmpty() ? "" : ", ricerca \"" + cerca + "\"")
                    + (esclusi.isEmpty() ? "" : ", escluse le tipologie " + new TreeSet<>(esclusi) + " (permesso di esportazione)"));

        // dati completi (con le variabili) solo per le pratiche della pagina, nell'ordine trovato
        Map<String, HistoricProcessInstance> perId = new HashMap<>();
        if (!trovate.id().isEmpty())
            historyService.createHistoricProcessInstanceQuery().processInstanceIds(new HashSet<>(trovate.id()))
                .includeProcessVariables().list().forEach(h -> perId.put(h.getId(), h));
        Map<String, String> nomi = new HashMap<>();
        Vista vista = new Vista(currentUser.current());
        List<Map<String, Object>> righe = trovate.id().stream().map(perId::get).filter(Objects::nonNull)
            .map(h -> riepilogo(h, nomi, vista, eventiPer(h.getId(), vista, Set.of())))
            .toList();
        return pagina(righe, trovate.totale(), pag, dim);
    }

    /** Tipologie di pratica (ultima versione di ogni processo), per il filtro dell'elenco. */
    @GetMapping("/tipologie")
    public List<Map<String, String>> tipologie() {
        // l'elenco dei processi dell'ente non riguarda gli utenti esterni
        if (currentUser.esterno()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operazione non consentita");
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

        Vista vista = new Vista(currentUser.current());
        java.util.function.Predicate<String> visibile = campiVisibili.per(vista.utente(), h.getProcessDefinitionId(), pi);
        List<Map<String, Object>> eventi = eventiPer(pi, vista,
            campiVisibili.etichetteNascoste(h.getProcessDefinitionId(), visibile));
        Map<String, Object> m = riepilogo(h, new HashMap<>(), vista, eventi);
        m.put("dati", dati(h, visibile));
        m.put("eventi", eventi);
        m.put("parti", parti.elenco(pi));
        // schede di anagrafica collegate dai form, con la fotografia (agli esterni solo il titolo, se il campo lo vedono)
        m.put("anagrafiche", anagraficheDellaPratica(pi, h, vista, visibile));
        if (vista.esterno()) {
            // un utente esterno non vede l'organizzazione interna né le pratiche collegate
            m.put("struttura", null);
            m.put("praticaMadre", null);
            m.put("praticheFiglie", List.of());
            var mie = taskRuntime.tasks(Pageable.of(0, 10),
                TaskPayloadBuilder.tasks().withProcessInstanceId(pi).build()).getContent();
            m.put("taskId", mie.isEmpty() ? null : mie.get(0).getId());
            return m;
        }
        m.put("struttura", praticaStruttura.findById(pi).flatMap(ps -> organizzazioni.findById(ps.getOrganizzazioneId()))
            .map(o -> Map.of("id", o.getId(), "nome", o.getNome())).orElse(null));
        // pratiche collegate dai processi richiamati (call activity)
        m.put("praticaMadre", h.getSuperProcessInstanceId() == null ? null
            : collegata(historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(h.getSuperProcessInstanceId()).singleResult()));
        m.put("praticheFiglie", historyService.createHistoricProcessInstanceQuery().superProcessInstanceId(pi)
            .orderByProcessInstanceStartTime().asc().list().stream().map(this::collegata).toList());
        // l'attività che l'utente può lavorare su questa pratica, se c'è
        var mie = taskRuntime.tasks(Pageable.of(0, 10),
            TaskPayloadBuilder.tasks().withProcessInstanceId(pi).build()).getContent();
        m.put("taskId", mie.isEmpty() ? null : mie.get(0).getId());
        return m;
    }

    private List<Map<String, Object>> anagraficheDellaPratica(String pi, HistoricProcessInstance h, Vista vista,
                                                              java.util.function.Predicate<String> visibile) {
        Map<String, String> etichette = new HashMap<>();
        Process p = repositoryService.getBpmnModel(h.getProcessDefinitionId()).getMainProcess();
        List<String> formKeys = new ArrayList<>();
        p.findFlowElementsOfType(StartEvent.class).forEach(s -> { if (s.getFormKey() != null) formKeys.add(s.getFormKey()); });
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (t.getFormKey() != null) formKeys.add(t.getFormKey()); });
        for (String fk : formKeys) {
            JsonNode form = forms.load(fk);
            if (form != null) form.path("fields").forEach(f -> etichette.putIfAbsent(f.path("name").asText(), f.path("label").asText(f.path("name").asText())));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var c : anagrafiche.dellaPratica(pi, vista.utente(), etichette)) {
            if (!visibile.test(c.campo())) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("campo", c.campo());
            x.put("etichetta", c.etichetta());
            x.put("titolo", c.titolo());
            if (!vista.esterno()) {
                x.put("tipo", c.tipo());
                x.put("tipoNome", c.tipoNome());
                x.put("id", c.id());
                x.put("fotografia", c.fotografia());
                x.put("versione", c.versione());
                x.put("collegataIl", c.collegataIl());
                x.put("visibile", c.visibile());
                x.put("titoloAttuale", c.titoloAttuale());
                x.put("statoAttuale", c.statoAttuale());
            }
            out.add(x);
        }
        return out;
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

    /** Una pratica collegata (madre o figlia), in breve. */
    private Map<String, Object> collegata(HistoricProcessInstance x) {
        if (x == null) return null;
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", x.getId());
        c.put("titolo", titolo(x));
        c.put("processo", nomeProcesso(x, new HashMap<>()));
        c.put("stato", x.getEndTime() != null ? "conclusa" : "in corso");
        c.put("inizio", x.getStartTime());
        return c;
    }

    /** Titolo della pratica; una pratica avviata da un processo richiamato prende quello della madre. */
    private String titolo(HistoricProcessInstance h) {
        if (h.getName() != null) return h.getName();
        if (h.getSuperProcessInstanceId() != null) {
            HistoricProcessInstance madre = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(h.getSuperProcessInstanceId()).singleResult();
            if (madre != null && madre.getName() != null) return madre.getName();
        }
        return h.getProcessDefinitionKey();
    }

    /** Nome leggibile del processo (con cache per richiesta). */
    private String nomeProcesso(HistoricProcessInstance h, Map<String, String> cache) {
        return cache.computeIfAbsent(h.getProcessDefinitionId(), id -> {
            var def = repositoryService.getProcessDefinition(id);
            return def.getName() != null ? def.getName() : def.getKey();
        });
    }

    /**
     * Lo storico come lo vede l'utente. Senza i valori dei campi che non vede; per un utente esterno, delle azioni
     * altrui restano solo avvio, attività completate e parti, a nome dell'"Ufficio" e senza dettagli.
     */
    private List<Map<String, Object>> eventiPer(String pi, Vista vista, Set<String> etichetteNascoste) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : storico.eventi(pi)) {
            if (vista.esterno() && !vista.io(e.get("utente"))) {
                if (!Set.of("AVVIO", "COMPLETAMENTO", "PARTE").contains(e.get("tipo"))) continue;
                e.put("utente", null);
                e.put("utenteNome", "Ufficio");
                e.put("dettagli", List.of());
            } else if (!etichetteNascoste.isEmpty() && e.get("dettagli") instanceof List<?> d) {
                e.put("dettagli", d.stream()
                    .filter(x -> !(x instanceof Map<?, ?> r && etichetteNascoste.contains(r.get("label")))).toList());
            }
            out.add(e);
        }
        return out;
    }

    private Map<String, Object> riepilogo(HistoricProcessInstance h, Map<String, String> nomi, Vista vista,
                                          List<Map<String, Object>> eventi) {
        Map<String, Object> vars = h.getProcessVariables() != null ? h.getProcessVariables() : Map.of();
        boolean conclusa = h.getEndTime() != null;
        List<Map<String, Object>> correnti = conclusa ? List.of() : stepCorrenti(h.getId());
        if (vista.esterno()) {
            // dove si trova la pratica sì, chi la sta lavorando e con quali gruppi no
            correnti = correnti.stream().map(c -> {
                Map<String, Object> x = new LinkedHashMap<>(c);
                x.put("assegnatario", null);
                x.put("gruppi", List.of());
                return x;
            }).toList();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("titolo", titolo(h));
        m.put("processo", nomeProcesso(h, nomi));
        m.put("processoKey", h.getProcessDefinitionKey());
        m.put("stato", conclusa ? "conclusa" : "in corso");
        m.put("avviataDa", !vista.esterno() || vista.io(vars.get("avviataDaUtente")) ? vars.get("avviataDa") : null);
        m.put("inizio", h.getStartTime());
        m.put("fine", h.getEndTime());
        m.put("stepCorrenti", correnti);
        m.put("percorso", percorso(eventi, correnti, conclusa, h.getEndTime()));
        // scadenze, solleciti e attese ancora programmati; la prima è la più vicina (scadenze interne: non agli esterni)
        List<Map<String, Object>> sc = conclusa || vista.esterno() ? List.of()
            : scadenze.perPratica(h.getId()).stream().map(ScadenzeService::comeMappa).toList();
        m.put("scadenze", sc);
        m.put("prossimaScadenza", sc.stream().filter(x -> !"attesa".equals(x.get("tipo"))).findFirst().orElse(null));
        // messaggi che la pratica aspetta da sistemi esterni o da un operatore
        m.put("messaggiAttesi", conclusa || vista.esterno() ? List.of() : messaggi.attesi(h.getId(), h.getProcessDefinitionId()).stream()
            .map(x -> Map.of("nome", x.nome(), "elemento", x.elemento(), "tipo", x.tipo(),
                "attivita", x.attivita() == null ? "" : x.attivita()))
            .toList());
        return m;
    }

    /**
     * Il "binario" della pratica: le fermate realmente percorse, compresi i ritorni.
     * Avvio → attività completate (in ordine) → attività correnti oppure "Conclusa".
     */
    private List<Map<String, Object>> percorso(List<Map<String, Object>> eventi, List<Map<String, Object>> correnti,
                                               boolean conclusa, Date fine) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : eventi) {
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
    private List<Map<String, Object>> dati(HistoricProcessInstance h, java.util.function.Predicate<String> visibile) {
        Map<String, String> etichette = new LinkedHashMap<>();
        Map<String, String> tipi = new HashMap<>();
        Map<String, List<Map<String, String>>> colonne = new HashMap<>();
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
                if (ValoriForm.TABELLA.equals(f.path("type").asText())) {
                    List<Map<String, String>> cc = new ArrayList<>();
                    f.path("colonne").forEach(c -> cc.add(Map.of("nome", c.path("name").asText(),
                        "label", c.path("label").asText(c.path("name").asText()), "tipo", c.path("type").asText("text"))));
                    colonne.putIfAbsent(f.path("name").asText(), cc);
                }
            }
        }
        etichette.putIfAbsent("avviataDa", "Avviata da");

        Map<String, Object> vars = h.getProcessVariables() != null ? h.getProcessVariables() : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        // prima nell'ordine dei form, poi le altre
        List<String> ordine = new ArrayList<>(etichette.keySet());
        vars.keySet().stream().sorted().filter(k -> !etichette.containsKey(k)).forEach(ordine::add);
        for (String k : ordine) {
            if (NASCOSTE.contains(k) || k.startsWith("sys_") || k.startsWith(com.eligesoft.orditogram.accesso.PartiService.PREFISSO_VARIABILE)
                || !vars.containsKey(k) || !visibile.test(k)) continue;
            // titolo e fotografia di una scheda di anagrafica: si mostrano con il campo e in "anagrafiche"
            if (derivataDaAnagrafica(k, tipi)) continue;
            Object v = vars.get(k);
            if (v == null || v.toString().isBlank()) continue;
            if (v instanceof List<?> righe && colonne.containsKey(k)) {
                // tabella di righe: il testo è un riassunto, le righe e le colonne servono per mostrarla
                if (righe.isEmpty()) continue;
                out.add(Map.of("nome", k, "label", etichette.getOrDefault(k, k),
                    "value", righe.size() + (righe.size() == 1 ? " riga" : " righe"), "tipo", ValoriForm.TABELLA,
                    "righe", righe, "colonne", colonne.get(k)));
                continue;
            }
            String testo = "checkbox".equals(tipi.get(k)) ? (Boolean.TRUE.equals(v) ? "Sì" : "No")
                : com.eligesoft.orditogram.form.ValoriForm.ORGANIZZAZIONE.equals(tipi.get(k)) ? nomeOrganizzazione(v.toString())
                : ValoriForm.ANAGRAFICA.equals(tipi.get(k)) ? Objects.toString(vars.get(k + AnagraficheNellePratiche.SUFFISSO_TITOLO), v.toString())
                : v.toString();
            out.add(Map.of("nome", k, "label", etichette.getOrDefault(k, k), "value", testo,
                "tipo", tipi.getOrDefault(k, "text")));
        }
        return out;
    }

    /** &lt;campo&gt;_titolo e &lt;campo&gt;_dati di un campo anagrafica. */
    private static boolean derivataDaAnagrafica(String nome, Map<String, String> tipi) {
        for (String suffisso : List.of(AnagraficheNellePratiche.SUFFISSO_DATI, AnagraficheNellePratiche.SUFFISSO_TITOLO))
            if (nome.endsWith(suffisso) && ValoriForm.ANAGRAFICA.equals(tipi.get(nome.substring(0, nome.length() - suffisso.length()))))
                return true;
        return false;
    }

    /** Il nome dell'organizzazione scelta in un campo Organizzazione (anche se nel frattempo è stata disattivata). */
    private String nomeOrganizzazione(String id) {
        try {
            return organizzazioni.findById(Long.valueOf(id.trim())).map(o -> o.getNome()).orElse(id);
        } catch (NumberFormatException e) {
            return id;
        }
    }
}
