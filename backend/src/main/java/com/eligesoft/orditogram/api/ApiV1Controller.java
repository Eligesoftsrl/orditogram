package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.api.ErroriApi.ErroreApi;
import com.eligesoft.orditogram.documento.Documento;
import com.eligesoft.orditogram.documento.DocumentoService;
import com.eligesoft.orditogram.documento.DocumentoVersione;
import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.messaggio.MessaggiService;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.ScadenzeService;
import com.eligesoft.orditogram.storico.StoricoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.IdentityLink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * API pubblica, versione 1: stabile, per i sistemi esterni. Si autentica con una chiave (api/FiltroChiaveApi),
 * ogni operazione richiede il suo permesso e vede solo i processi consentiti alla chiave.
 * Documentazione generata: GET /api/v1/openapi.json (api/OpenApiGeneratore).
 *
 * Le regole sui dati sono quelle dei form disegnati nello Studio (form/ValoriForm), in versione rigorosa:
 * campi sconosciuti, tipi sbagliati e scelte non ammesse sono errori, restituiti tutti insieme.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiV1Controller {

    /** Variabili tecniche che non fanno parte dei dati della pratica. */
    private static final Set<String> NASCOSTE = Set.of("chiusaDa", "chiusaIl", "initiator", "avviataDaUtente");
    private static final int MAX_DIMENSIONE = 200;

    private final DescrizioneProcessi descrizioni;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final StoricoService storico;
    private final DocumentoService documenti;
    private final ScadenzeService scadenze;
    private final MessaggiService messaggi;
    private final ObjectMapper mapper;
    private final ObjectProvider<BuildProperties> build;

    public ApiV1Controller(DescrizioneProcessi descrizioni, RuntimeService runtimeService, HistoryService historyService,
                           RepositoryService repositoryService, TaskService taskService, StoricoService storico,
                           DocumentoService documenti, ScadenzeService scadenze, MessaggiService messaggi,
                           ObjectMapper mapper, ObjectProvider<BuildProperties> build) {
        this.descrizioni = descrizioni;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.taskService = taskService;
        this.storico = storico;
        this.documenti = documenti;
        this.scadenze = scadenze;
        this.messaggi = messaggi;
        this.mapper = mapper;
        this.build = build;
    }

    // ================================================================= sistema

    @GetMapping("/io")
    public Map<String, Object> io() {
        IdentitaApi id = identita();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sistema", id.sistema());
        m.put("permessi", new ArrayList<>(id.ambiti()));
        m.put("processi", new ArrayList<>(id.processi()));
        return m;
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openapi() {
        IdentitaApi id = identita();
        List<ProcessoPubblicato> processi = descrizioni.pubblicati().stream()
            .filter(p -> id.processoConsentito(p.chiave())).toList();
        return OpenApiGeneratore.genera(processi, indirizzoBase(), versione(), null);
    }

    public String versione() {
        BuildProperties b = build.getIfAvailable();
        return b != null ? b.getVersion() : "sviluppo";
    }

    static String indirizzoBase() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    // ================================================================= processi

    @GetMapping("/processi")
    public List<Map<String, Object>> processi() {
        IdentitaApi id = identita();
        return descrizioni.pubblicati().stream()
            .filter(p -> id.processoConsentito(p.chiave()))
            .map(ApiV1Controller::processoJson)
            .toList();
    }

    @GetMapping("/processi/{chiave}")
    public Map<String, Object> processo(@PathVariable String chiave) {
        IdentitaApi id = identita();
        if (!id.processoConsentito(chiave)) throw ErroriApi.nonTrovata("Processo");
        return descrizioni.processo(chiave).map(ApiV1Controller::processoJson)
            .orElseThrow(() -> ErroriApi.nonTrovata("Processo"));
    }

    static Map<String, Object> processoJson(ProcessoPubblicato p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chiave", p.chiave());
        m.put("nome", p.nome());
        m.put("descrizione", p.descrizione());
        m.put("versione", p.versione());
        m.put("campi", p.campi().stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("nome", c.nome());
            x.put("etichetta", c.etichetta());
            x.put("tipo", c.tipo());
            x.put("obbligatorio", c.obbligatorio());
            if (!c.opzioni().isEmpty()) x.put("opzioni", c.opzioni());
            if (c.aiuto() != null) x.put("aiuto", c.aiuto());
            return x;
        }).toList());
        m.put("allegati", p.allegati());
        m.put("messaggi", p.messaggi().stream().map(x -> Map.of("nome", x.nome(), "dati", x.dati())).toList());
        m.put("avvio", "/api/v1/processi/" + p.chiave() + "/pratiche");
        return m;
    }

    // ================================================================= avvio

    public record RichiestaAvvio(String processo, String riferimento, Map<String, Object> dati) {}

    @PostMapping(value = "/processi/{chiave}/pratiche", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> avviaProcesso(@PathVariable String chiave,
                                                             @RequestBody(required = false) RichiestaAvvio r) {
        RichiestaAvvio req = r != null ? r : new RichiestaAvvio(null, null, null);
        return avvia(chiave, req.riferimento(), req.dati(), List.of());
    }

    @PostMapping(value = "/processi/{chiave}/pratiche", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> avviaProcessoConDocumenti(
            @PathVariable String chiave,
            @RequestPart("richiesta") String richiesta,
            @RequestParam(value = "documenti", required = false) List<MultipartFile> files) {
        RichiestaAvvio req;
        try {
            req = mapper.readValue(richiesta, RichiestaAvvio.class);
        } catch (IOException e) {
            throw ErroriApi.richiesta("La parte \"richiesta\" non è un JSON valido: " + e.getOriginalMessage());
        }
        return avvia(chiave, req.riferimento(), req.dati(), files != null ? files : List.of());
    }

    @PostMapping(value = "/pratiche", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> avviaPratica(@RequestBody(required = false) RichiestaAvvio r) {
        if (r == null || r.processo() == null || r.processo().isBlank())
            throw ErroriApi.richiesta("Indica il processo (\"processo\": chiave del processo, vedi GET /api/v1/processi)");
        return avvia(r.processo().trim(), r.riferimento(), r.dati(), List.of());
    }

    private ResponseEntity<Map<String, Object>> avvia(String chiave, String riferimento, Map<String, Object> dati,
                                                      List<MultipartFile> files) {
        IdentitaApi id = richiedi(Ambiti.PRATICHE_AVVIA);
        if (!id.processoConsentito(chiave)) throw ErroriApi.nonTrovata("Processo");
        ProcessDefinition def = descrizioni.definizione(chiave);
        if (def == null || def.isSuspended()) throw ErroriApi.nonTrovata("Processo");

        String rif = riferimento == null || riferimento.isBlank() ? null : riferimento.trim();
        if (rif != null && rif.length() > 255) throw ErroriApi.richiesta("Il riferimento può avere al massimo 255 caratteri");
        if (rif != null) {
            // stessa richiesta ripetuta (es. dopo un timeout): si restituisce la pratica già creata
            List<HistoricProcessInstance> esistenti = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(chiave).processInstanceBusinessKey(rif)
                .orderByProcessInstanceStartTime().asc().listPage(0, 1);
            if (!esistenti.isEmpty()) {
                Map<String, Object> m = pratica(esistenti.get(0).getId());
                m.put("creata", false);
                return ResponseEntity.ok(m);
            }
        }

        JsonNode form = descrizioni.formAvvio(def.getId());
        ValoriForm.Esito esito = ValoriForm.convalida(form, dati);
        List<Map<String, String>> errori = new ArrayList<>();
        esito.errori().forEach(e -> errori.add(Map.of("campo", e.campo(), "messaggio", e.messaggio())));

        List<MultipartFile> allegati = files.stream().filter(f -> !f.isEmpty()).toList();
        String modo = ValoriForm.allegatiAvvio(form);
        if (!allegati.isEmpty() && "nessuno".equals(modo))
            errori.add(Map.of("campo", "documenti", "messaggio", "questo processo non prevede documenti all'avvio"));
        if (allegati.isEmpty() && "obbligatori".equals(modo))
            errori.add(Map.of("campo", "documenti", "messaggio", "allega almeno un documento (richiesta multipart)"));
        if (!errori.isEmpty())
            throw new ErroreApi(422, "dati_non_validi", "Dati non validi: " + errori.size()
                + (errori.size() == 1 ? " problema" : " problemi") + ", dettagli in \"campi\"", errori);

        Map<String, Object> vars = new HashMap<>(esito.valori());
        vars.put("avviataDa", id.sistema() + " (sistema esterno)");
        String nomeProcesso = def.getName() != null ? def.getName() : def.getKey();

        ProcessInstance pi;
        // chi avvia, per il motore (startUserId): il sistema esterno
        Authentication.setAuthenticatedUserId(id.getName());
        try {
            pi = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(def.getId())
                .name(ValoriForm.titolo(form, vars, nomeProcesso, LocalDate.now()))
                .businessKey(rif)
                .variables(vars)
                .start();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }

        List<Map<String, Object>> dettagli = new ArrayList<>();
        dettagli.add(Map.of("label", "Da", "value", "API, sistema " + id.sistema()));
        if (rif != null) dettagli.add(Map.of("label", "Riferimento", "value", rif));
        dettagli.addAll(storico.dettagliDaForm(form, vars));
        storico.registra(pi.getId(), PraticaEvento.Tipo.AVVIO, nomeProcesso, dettagli);
        for (MultipartFile f : allegati) documenti.aggiungi(pi.getId(), f, null, "Avvio");

        Map<String, Object> m = pratica(pi.getId());
        m.put("creata", true);
        return ResponseEntity.status(201)
            .location(ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/pratiche/{id}").buildAndExpand(pi.getId()).toUri())
            .body(m);
    }

    // ================================================================= pratiche

    @GetMapping("/pratiche")
    public Map<String, Object> cerca(@RequestParam(required = false) String processo,
                                     @RequestParam(required = false) String stato,
                                     @RequestParam(required = false) String riferimento,
                                     @RequestParam(required = false) String dal,
                                     @RequestParam(required = false) String al,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "50") int dimensione) {
        IdentitaApi id = richiedi(Ambiti.PRATICHE_LEGGI);
        int dim = Math.max(1, Math.min(MAX_DIMENSIONE, dimensione));
        int pag = Math.max(0, pagina);
        HistoricProcessInstanceQuery q = historyService.createHistoricProcessInstanceQuery()
            .orderByProcessInstanceStartTime().desc();
        if (processo != null && !processo.isBlank()) {
            if (!id.processoConsentito(processo.trim())) return paginaVuota(pag, dim);
            q.processDefinitionKey(processo.trim());
        } else if (!id.processi().isEmpty()) {
            q.processDefinitionKeyIn(new ArrayList<>(id.processi()));
        }
        if (stato != null && !stato.isBlank()) {
            switch (stato) {
                case "in_corso" -> q.unfinished();
                case "conclusa" -> q.finished();
                default -> throw ErroriApi.richiesta("stato: usa in_corso oppure conclusa");
            }
        }
        if (riferimento != null && !riferimento.isBlank()) q.processInstanceBusinessKey(riferimento.trim());
        ZoneId zona = ZoneId.systemDefault();
        if (dal != null && !dal.isBlank()) q.startedAfter(Date.from(data(dal, "dal").atStartOfDay(zona).toInstant()));
        if (al != null && !al.isBlank()) q.startedBefore(Date.from(data(al, "al").plusDays(1).atStartOfDay(zona).toInstant()));

        long totale = q.count();
        Map<String, String> nomi = new HashMap<>();
        List<Map<String, Object>> righe = q.listPage(pag * dim, dim).stream().map(h -> riepilogo(h, nomi)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("righe", righe);
        m.put("totale", totale);
        m.put("pagina", pag);
        m.put("dimensione", dim);
        return m;
    }

    @GetMapping("/pratiche/{id}")
    public Map<String, Object> dettaglio(@PathVariable String id) {
        richiedi(Ambiti.PRATICHE_LEGGI);
        return pratica(id);
    }

    @GetMapping("/pratiche/{id}/storico")
    public List<Map<String, Object>> storico(@PathVariable String id) {
        richiedi(Ambiti.PRATICHE_LEGGI);
        accessibile(id);
        return storico.eventi(id);
    }

    // ================================================================= documenti

    @GetMapping("/pratiche/{id}/documenti")
    public List<Map<String, Object>> documenti(@PathVariable String id) {
        richiedi(Ambiti.DOCUMENTI_LEGGI);
        accessibile(id);
        return documenti.elenco(id);
    }

    @GetMapping("/pratiche/{id}/documenti/versioni/{versione}")
    public ResponseEntity<byte[]> scarica(@PathVariable String id, @PathVariable Long versione) {
        richiedi(Ambiti.DOCUMENTI_LEGGI);
        accessibile(id);
        DocumentoVersione v;
        try {
            v = documenti.versione(versione);
        } catch (RuntimeException e) {
            throw ErroriApi.nonTrovata("Documento");
        }
        if (!id.equals(v.getDocumento().getProcessInstanceId())) throw ErroriApi.nonTrovata("Documento");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(v.getFilename(), StandardCharsets.UTF_8).build().toString())
            .contentType(v.getContentType() != null ? MediaType.parseMediaType(v.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .body(documenti.contenuto(v));
    }

    @PostMapping(value = "/pratiche/{id}/documenti", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> aggiungiDocumento(@PathVariable String id,
                                                                 @RequestParam("file") MultipartFile file,
                                                                 @RequestParam(required = false) String nota,
                                                                 @RequestParam(required = false) Long documento) {
        richiedi(Ambiti.DOCUMENTI_SCRIVI);
        accessibile(id);
        if (runtimeService.createProcessInstanceQuery().processInstanceId(id).count() == 0)
            throw new ErroreApi(409, "conflitto", "La pratica è conclusa: non si possono aggiungere documenti");
        if (file == null || file.isEmpty()) throw ErroriApi.richiesta("Il file è vuoto");
        DocumentoVersione v;
        if (documento != null) {
            Documento d;
            try {
                d = documenti.documento(documento);
            } catch (RuntimeException e) {
                throw ErroriApi.nonTrovata("Documento");
            }
            if (!id.equals(d.getProcessInstanceId())) throw ErroriApi.nonTrovata("Documento");
            v = documenti.nuovaVersione(documento, file, nota, "API");
        } else {
            v = documenti.aggiungi(id, file, nota, "API");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("documento", v.getDocumento().getId());
        m.put("numero", v.getNumero());
        m.put("filename", v.getFilename());
        m.put("dimensione", v.getDimensione());
        m.put("sha256", v.getSha256());
        m.put("firmaTipo", v.getFirmaTipo());
        m.put("firmaEsito", v.getFirmaEsito());
        return ResponseEntity.status(201).body(m);
    }

    // ================================================================= messaggi

    public record RichiestaMessaggio(String pratica, Map<String, Object> cerca, Map<String, Object> dati) {}

    @PostMapping("/messaggi/{nome}")
    public Map<String, Object> messaggio(@PathVariable String nome, @RequestBody(required = false) RichiestaMessaggio r) {
        IdentitaApi id = richiedi(Ambiti.MESSAGGI_INVIA);
        RichiestaMessaggio req = r != null ? r : new RichiestaMessaggio(null, null, null);
        if (req.pratica() != null && !req.pratica().isBlank()) accessibile(req.pratica());
        List<String> pratiche = messaggi.consegna(nome, req.pratica(), req.cerca(), req.dati(),
            "Sistema " + id.sistema() + " (API)", id.processi());
        return Map.of("consegnato", true, "pratiche", pratiche);
    }

    @PostMapping("/pratiche/{id}/messaggi/{nome}")
    public Map<String, Object> messaggioAllaPratica(@PathVariable String id, @PathVariable String nome,
                                                    @RequestBody(required = false) Map<String, Object> corpo) {
        IdentitaApi chi = richiedi(Ambiti.MESSAGGI_INVIA);
        accessibile(id);
        Map<String, Object> dati = null;
        if (corpo != null && corpo.get("dati") instanceof Map<?, ?> d)
            dati = mapper.convertValue(d, new TypeReference<Map<String, Object>>() {});
        List<String> pratiche = messaggi.consegna(nome, id, null, dati, "Sistema " + chi.sistema() + " (API)", chi.processi());
        return Map.of("consegnato", true, "pratiche", pratiche);
    }

    // ================================================================= costruzione risposte

    /** La pratica completa: stato, attività in corso, scadenze, messaggi attesi, dati. */
    private Map<String, Object> pratica(String id) {
        HistoricProcessInstance h = accessibile(id);
        Map<String, Object> m = riepilogo(h, new HashMap<>());
        boolean conclusa = h.getEndTime() != null;
        m.put("attivitaCorrenti", conclusa ? List.of() : attivita(id));
        m.put("scadenze", conclusa ? List.of() : scadenze.perPratica(id).stream().map(ScadenzeService::comeMappa).toList());
        m.put("messaggiAttesi", conclusa ? List.of() : messaggi.attesi(id, h.getProcessDefinitionId()).stream()
            .map(x -> Map.of("nome", x.nome(), "elemento", x.elemento())).toList());
        m.put("dati", dati(h));
        return m;
    }

    private Map<String, Object> riepilogo(HistoricProcessInstance h, Map<String, String> cacheNomi) {
        Map<String, Object> m = new LinkedHashMap<>();
        ProcessDefinition def = repositoryService.getProcessDefinition(h.getProcessDefinitionId());
        String nome = cacheNomi.computeIfAbsent(def.getId(), k -> def.getName() != null ? def.getName() : def.getKey());
        m.put("id", h.getId());
        m.put("titolo", h.getName() != null ? h.getName() : nome);
        m.put("riferimento", h.getBusinessKey());
        m.put("processo", Map.of("chiave", def.getKey(), "nome", nome, "versione", def.getVersion()));
        m.put("stato", h.getEndTime() == null ? "in_corso" : "conclusa");
        m.put("avviata", h.getStartTime());
        m.put("conclusa", h.getEndTime());
        return m;
    }

    private List<Map<String, Object>> attivita(String pi) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (org.activiti.engine.task.Task t : taskService.createTaskQuery().processInstanceId(pi).list()) {
            List<String> gruppi = new ArrayList<>();
            for (IdentityLink l : taskService.getIdentityLinksForTask(t.getId()))
                if ("candidate".equals(l.getType()) && l.getGroupId() != null) gruppi.add(l.getGroupId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("nome", t.getName());
            m.put("chiave", t.getTaskDefinitionKey());
            m.put("assegnatario", t.getAssignee());
            m.put("gruppi", gruppi);
            m.put("dal", t.getCreateTime());
            out.add(m);
        }
        return out;
    }

    /** Tutti i dati della pratica come valori semplici (testo, numeri, sì/no, date). */
    private Map<String, Object> dati(HistoricProcessInstance h) {
        HistoricProcessInstance conDati = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(h.getId()).includeProcessVariables().singleResult();
        Map<String, Object> vars = conDati != null && conDati.getProcessVariables() != null ? conDati.getProcessVariables() : Map.of();
        Map<String, Object> out = new TreeMap<>();
        vars.forEach((k, x) -> {
            if (x == null || NASCOSTE.contains(k)) return;
            if (x instanceof String || x instanceof Number || x instanceof Boolean || x instanceof Date
                || x instanceof java.time.temporal.Temporal || x instanceof JsonNode
                || x instanceof Map || x instanceof Collection) out.put(k, x);
            else out.put(k, x.toString());
        });
        return out;
    }

    // ================================================================= supporto

    private static IdentitaApi identita() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof IdentitaApi id) return id;
        throw new ErroreApi(401, "chiave_mancante", "Serve una chiave API");
    }

    private static IdentitaApi richiedi(String ambito) {
        IdentitaApi id = identita();
        if (!id.puo(ambito)) throw ErroriApi.permesso(ambito);
        return id;
    }

    /** La pratica, se esiste e appartiene a un processo consentito alla chiave (altrimenti 404, senza dire perché). */
    private HistoricProcessInstance accessibile(String id) {
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
        if (h == null || !identita().processoConsentito(h.getProcessDefinitionKey())) throw ErroriApi.nonTrovata("Pratica");
        return h;
    }

    private static LocalDate data(String s, String nome) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw ErroriApi.richiesta(nome + ": data non valida, usa il formato AAAA-MM-GG");
        }
    }

    private static Map<String, Object> paginaVuota(int pagina, int dimensione) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("righe", List.of());
        m.put("totale", 0);
        m.put("pagina", pagina);
        m.put("dimensione", dimensione);
        return m;
    }
}
