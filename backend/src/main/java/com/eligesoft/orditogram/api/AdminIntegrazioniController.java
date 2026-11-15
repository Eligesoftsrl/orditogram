package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.config.Cifratura;
import com.eligesoft.orditogram.webhook.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Studio → Integrazioni (solo amministratori): chiavi dell'API pubblica, webhook e loro consegne,
 * documentazione OpenAPI completa, ricevitore di prova.
 */
@RestController
@RequestMapping("/api/admin/integrazioni")
public class AdminIntegrazioniController {

    private final ChiaviApiService chiavi;
    private final WebhookService webhook;
    private final InvioWebhook invio;
    private final ConsegnaWebhookRepository consegne;
    private final DescrizioneProcessi descrizioni;
    private final RicevitoreProva ricevitore;
    private final Cifratura cifratura;
    private final ApiV1Controller apiV1;
    private final ObjectMapper mapper;
    private final boolean demo;

    public AdminIntegrazioniController(ChiaviApiService chiavi, WebhookService webhook, InvioWebhook invio,
                                       ConsegnaWebhookRepository consegne, DescrizioneProcessi descrizioni,
                                       RicevitoreProva ricevitore, Cifratura cifratura, ApiV1Controller apiV1,
                                       ObjectMapper mapper, @Value("${app.demo.servizi:true}") boolean demo) {
        this.chiavi = chiavi;
        this.webhook = webhook;
        this.invio = invio;
        this.consegne = consegne;
        this.descrizioni = descrizioni;
        this.ricevitore = ricevitore;
        this.cifratura = cifratura;
        this.apiV1 = apiV1;
        this.mapper = mapper;
        this.demo = demo;
    }

    /** Tutto quello che serve alla pagina: permessi, eventi, processi, stato della configurazione. */
    @GetMapping("/panoramica")
    public Map<String, Object> panoramica() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("permessi", Ambiti.TUTTI);
        m.put("eventi", TipiEvento.TUTTI);
        m.put("processi", descrizioni.pubblicati().stream().map(p -> Map.of("chiave", p.chiave(), "nome", p.nome())).toList());
        m.put("cifratura", cifratura.attiva());
        m.put("sistemiConfigurati", chiavi.sistemiConfigurati());
        m.put("indirizzoBase", ApiV1Controller.indirizzoBase());
        m.put("ricevitoreProva", demo ? ApiV1Controller.indirizzoBase() + "/api/demo/webhook" : null);
        m.put("versione", apiV1.versione());
        return m;
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openapi() {
        return OpenApiGeneratore.genera(descrizioni.pubblicati(), ApiV1Controller.indirizzoBase(), apiV1.versione(), null);
    }

    // ================================================================= chiavi

    public record DatiChiave(String nome, String descrizione, List<String> permessi, List<String> processi,
                             LocalDate scadenza, Boolean attiva) {}

    @GetMapping("/chiavi")
    public List<Map<String, Object>> elencoChiavi() {
        return chiavi.elenco().stream().map(AdminIntegrazioniController::chiave).toList();
    }

    @PostMapping("/chiavi")
    public Map<String, Object> creaChiave(@RequestBody DatiChiave d) {
        ChiaviApiService.Creata c = chiavi.crea(d.nome(), d.descrizione(), d.permessi(), d.processi(), d.scadenza());
        return Map.of("chiave", chiave(c.chiave()), "valore", c.valore());
    }

    @PutMapping("/chiavi/{id}")
    public Map<String, Object> modificaChiave(@PathVariable Long id, @RequestBody DatiChiave d) {
        return chiave(chiavi.modifica(id, d.descrizione(), d.permessi(), d.processi(), d.scadenza(),
            d.attiva() == null || d.attiva()));
    }

    @PostMapping("/chiavi/{id}/ruota")
    public Map<String, Object> ruotaChiave(@PathVariable Long id) {
        ChiaviApiService.Creata c = chiavi.ruota(id);
        return Map.of("chiave", chiave(c.chiave()), "valore", c.valore());
    }

    @PostMapping("/chiavi/{id}/revoca")
    public Map<String, Object> revocaChiave(@PathVariable Long id) {
        return chiave(chiavi.revoca(id));
    }

    static Map<String, Object> chiave(ChiaveApi c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("nome", c.getNome());
        m.put("descrizione", c.getDescrizione());
        m.put("prefisso", c.getPrefisso());
        m.put("permessi", new ArrayList<>(c.ambitiSet()));
        m.put("processi", new ArrayList<>(c.processiSet()));
        m.put("scadenza", c.getScadenza());
        m.put("attiva", c.isAttiva());
        m.put("valida", c.valida(LocalDate.now()));
        m.put("revocata", c.getRevocata());
        m.put("creata", c.getCreata());
        m.put("creataDa", c.getCreataDa());
        m.put("ultimoUso", c.getUltimoUso());
        m.put("ultimoIndirizzo", c.getUltimoIndirizzo());
        return m;
    }

    // ================================================================= webhook

    @GetMapping("/webhook")
    public List<Map<String, Object>> elencoWebhook() {
        return webhook.elenco().stream().map(this::webhook).toList();
    }

    @PostMapping("/webhook")
    public Map<String, Object> creaWebhook(@RequestBody WebhookService.Dati d) {
        Webhook w = webhook.crea(d);
        Map<String, Object> m = webhook(w);
        m.put("segreto", webhook.segretoInChiaro(w));   // si mostra subito, per configurare il ricevente
        return m;
    }

    @PutMapping("/webhook/{id}")
    public Map<String, Object> modificaWebhook(@PathVariable Long id, @RequestBody WebhookService.Dati d) {
        return webhook(webhook.modifica(id, d));
    }

    @DeleteMapping("/webhook/{id}")
    public void eliminaWebhook(@PathVariable Long id) {
        webhook.elimina(id);
    }

    @GetMapping("/webhook/{id}/segreto")
    public Map<String, Object> segreto(@PathVariable Long id) {
        return Map.of("segreto", webhook.segreto(id));
    }

    @PostMapping("/webhook/{id}/segreto")
    public Map<String, Object> rigeneraSegreto(@PathVariable Long id) {
        return Map.of("segreto", webhook.rigeneraSegreto(id));
    }

    /** Invia subito un evento di prova e restituisce com'è andata. */
    @PostMapping("/webhook/{id}/prova")
    public Map<String, Object> prova(@PathVariable Long id) {
        ConsegnaWebhook c = webhook.accodaProva(id);
        return consegna(invio.inviaOra(c.getId()), true);
    }

    private Map<String, Object> webhook(Webhook w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("nome", w.getNome());
        m.put("url", w.getUrl());
        m.put("descrizione", w.getDescrizione());
        m.put("eventi", new ArrayList<>(w.eventiSet()));
        m.put("processi", new ArrayList<>(w.processiSet()));
        m.put("attivo", w.isAttivo());
        m.put("creato", w.getCreato());
        if (w.getId() != null) {
            m.put("inCoda", consegne.countByWebhookIdAndStato(w.getId(), ConsegnaWebhook.IN_CODA)
                + consegne.countByWebhookIdAndStato(w.getId(), ConsegnaWebhook.DA_RIPETERE));
            m.put("fallite", consegne.countByWebhookIdAndStato(w.getId(), ConsegnaWebhook.FALLITA));
            ConsegnaWebhook ultima = consegne.findFirstByWebhookIdAndUltimoTentativoNotNullOrderByUltimoTentativoDesc(w.getId());
            m.put("ultima", ultima == null ? null : consegna(ultima, false));
        }
        return m;
    }

    // ================================================================= consegne

    @GetMapping("/consegne")
    public Map<String, Object> elencoConsegne(@RequestParam(required = false) Long webhook,
                                              @RequestParam(required = false) String stato,
                                              @RequestParam(required = false) String tipo,
                                              @RequestParam(required = false) String pratica,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "25") int dimensione) {
        Page<ConsegnaWebhook> p = consegne.cerca(webhook, vuoto(stato), vuoto(tipo), vuoto(pratica),
            PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(1000, dimensione))));
        Map<String, String> nomi = new HashMap<>();
        this.webhook.elenco().forEach(w -> nomi.put(String.valueOf(w.getId()), w.getNome()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("righe", p.getContent().stream().map(c -> {
            Map<String, Object> x = consegna(c, false);
            x.put("webhookNome", nomi.getOrDefault(String.valueOf(c.getWebhookId()), "(eliminato)"));
            return x;
        }).toList());
        m.put("totale", p.getTotalElements());
        m.put("pagina", p.getNumber());
        m.put("dimensione", p.getSize());
        return m;
    }

    @GetMapping("/consegne/{id}")
    public Map<String, Object> dettaglioConsegna(@PathVariable Long id) {
        return consegna(consegne.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consegna non trovata")), true);
    }

    @PostMapping("/consegne/{id}/ripeti")
    public Map<String, Object> ripeti(@PathVariable Long id) {
        return consegna(invio.ripeti(id), true);
    }

    private Map<String, Object> consegna(ConsegnaWebhook c, boolean completa) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("webhook", c.getWebhookId());
        m.put("evento", c.getEventoId());
        m.put("tipo", c.getTipo());
        m.put("pratica", c.getPratica());
        m.put("stato", c.getStato());
        m.put("tentativi", c.getTentativi());
        m.put("creata", c.getCreata());
        m.put("ultimoTentativo", c.getUltimoTentativo());
        m.put("prossimoTentativo", ConsegnaWebhook.DA_RIPETERE.equals(c.getStato()) ? c.getProssimoTentativo() : null);
        m.put("codiceRisposta", c.getCodiceRisposta());
        m.put("errore", c.getErrore());
        m.put("durataMs", c.getDurataMs());
        if (completa) {
            try {
                m.put("payload", mapper.readTree(c.getPayload()));
            } catch (Exception e) {
                m.put("payload", c.getPayload());
            }
            m.put("risposta", c.getRisposta());
        }
        return m;
    }

    // ================================================================= ricevitore di prova

    @GetMapping("/ricevuti")
    public List<RicevitoreProva.Ricevuto> ricevuti() {
        return ricevitore.elenco();
    }

    @DeleteMapping("/ricevuti")
    public void svuotaRicevuti() {
        ricevitore.svuota();
    }

    private static String vuoto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
