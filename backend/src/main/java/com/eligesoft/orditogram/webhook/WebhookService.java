package com.eligesoft.orditogram.webhook;

import com.eligesoft.orditogram.config.Cifratura;
import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.storico.EventoStorico;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Webhook: configurazione (Studio → Integrazioni) e accodamento degli eventi.
 * Gli eventi arrivano dal motore (webhook/AscoltatoreEventiWebhook) e dallo storico delle pratiche
 * (messaggi, documenti, scadenze, errori); la consegna la fa webhook/InvioWebhook.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final long RICARICA_MS = 60_000;

    /** Configurazione essenziale dei webhook attivi, tenuta in memoria: la maggior parte degli eventi non interessa a nessuno. */
    record Attivo(Long id, Set<String> eventi, Set<String> processi) {
        boolean vuole(String tipo, String processo) {
            if (TipiEvento.PROVA.equals(tipo)) return false;   // la prova va solo al webhook scelto
            return (eventi.isEmpty() || eventi.contains(tipo))
                && (processi.isEmpty() || (processo != null && processi.contains(processo)));
        }
    }

    private final WebhookRepository webhook;
    private final ConsegnaWebhookRepository consegne;
    private final Cifratura cifratura;
    private final CurrentUserService currentUser;
    private final RegistroService registro;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ObjectMapper mapper;

    private volatile List<Attivo> attivi;
    private volatile long caricatiIl;

    public WebhookService(WebhookRepository webhook, ConsegnaWebhookRepository consegne, Cifratura cifratura,
                          CurrentUserService currentUser, RegistroService registro, HistoryService historyService,
                          RepositoryService repositoryService, ObjectMapper mapper) {
        this.webhook = webhook;
        this.consegne = consegne;
        this.cifratura = cifratura;
        this.currentUser = currentUser;
        this.registro = registro;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.mapper = mapper;
    }

    // ================================================================= accodamento

    /** C'è almeno un webhook attivo interessato a questo tipo di evento? (veloce, senza database) */
    public boolean interessa(String tipo) {
        return attivi().stream().anyMatch(a -> a.eventi().isEmpty() || a.eventi().contains(tipo));
    }

    /**
     * Accoda l'evento per i webhook interessati, nella transazione in corso.
     * pratica: { id, titolo, processo, processoNome, riferimento }; attivita e dettagli facoltativi.
     */
    @Transactional
    public void accoda(String tipo, Map<String, Object> pratica, Map<String, Object> attivita,
                       List<Map<String, Object>> dettagli) {
        String processo = pratica != null ? (String) pratica.get("processo") : null;
        List<Attivo> destinatari = attivi().stream().filter(a -> a.vuole(tipo, processo)).toList();
        if (destinatari.isEmpty()) return;
        String eventoId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String payload = payload(eventoId, tipo, pratica, attivita, dettagli);
        String pi = pratica != null ? (String) pratica.get("id") : null;
        for (Attivo a : destinatari) consegne.save(nuovaConsegna(a.id(), eventoId, tipo, pi, payload));
    }

    /** Eventi dallo storico delle pratiche. */
    @EventListener
    @Transactional
    public void daStorico(EventoStorico e) {
        String tipo = switch (e.tipo()) {
            case MESSAGGIO -> TipiEvento.MESSAGGIO_RICEVUTO;
            case DOCUMENTO -> TipiEvento.DOCUMENTO_AGGIUNTO;
            case SCADENZA -> TipiEvento.SCADENZA_SCATTATA;
            case ERRORE -> TipiEvento.PRATICA_ERRORE;
            default -> null;
        };
        if (tipo == null || !interessa(tipo)) return;
        try {
            List<Map<String, Object>> dettagli = new ArrayList<>();
            dettagli.add(Map.of("etichetta", "Cosa", "valore", Objects.toString(e.titolo(), "")));
            if (e.utenteNome() != null) dettagli.add(Map.of("etichetta", "Chi", "valore", e.utenteNome()));
            for (Map<String, Object> d : e.dettagli())
                dettagli.add(Map.of("etichetta", Objects.toString(d.get("label"), ""), "valore", Objects.toString(d.get("value"), "")));
            accoda(tipo, pratica(e.processInstanceId()), null, dettagli);
        } catch (RuntimeException ex) {
            // un avviso non accodato non deve mai bloccare il lavoro sulla pratica
            log.warn("Evento {} della pratica {} non accodato per i webhook: {}", tipo, e.processInstanceId(), ex.getMessage());
        }
    }

    /** Riferimenti della pratica letti dallo storico del motore. */
    public Map<String, Object> pratica(String processInstanceId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", processInstanceId);
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (h != null) {
            var def = repositoryService.getProcessDefinition(h.getProcessDefinitionId());
            m.put("titolo", h.getName());
            m.put("processo", def.getKey());
            m.put("processoNome", def.getName() != null ? def.getName() : def.getKey());
            m.put("riferimento", h.getBusinessKey());
        }
        return m;
    }

    String payload(String eventoId, String tipo, Map<String, Object> pratica, Map<String, Object> attivita,
                   List<Map<String, Object>> dettagli) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", eventoId);
        m.put("tipo", tipo);
        m.put("quando", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (pratica != null) m.put("pratica", pratica);
        if (attivita != null) m.put("attivita", attivita);
        if (dettagli != null && !dettagli.isEmpty()) m.put("dettagli", dettagli);
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ConsegnaWebhook nuovaConsegna(Long webhookId, String eventoId, String tipo, String pratica, String payload) {
        ConsegnaWebhook c = new ConsegnaWebhook();
        c.setWebhookId(webhookId);
        c.setEventoId(eventoId);
        c.setTipo(tipo);
        c.setPratica(pratica);
        c.setPayload(payload);
        c.setStato(ConsegnaWebhook.IN_CODA);
        OffsetDateTime ora = OffsetDateTime.now();
        c.setCreata(ora);
        c.setProssimoTentativo(ora);
        return c;
    }

    private List<Attivo> attivi() {
        List<Attivo> a = attivi;
        if (a == null || System.currentTimeMillis() - caricatiIl > RICARICA_MS) a = ricarica();
        return a;
    }

    /** Rilegge i webhook attivi (dopo ogni modifica e comunque ogni minuto, per le altre istanze di Orditogram). */
    public synchronized List<Attivo> ricarica() {
        try {
            List<Attivo> nuovi = webhook.findByAttivoTrue().stream()
                .map(w -> new Attivo(w.getId(), w.eventiSet(), w.processiSet())).toList();
            attivi = nuovi;
            caricatiIl = System.currentTimeMillis();
            return nuovi;
        } catch (RuntimeException e) {
            log.warn("Webhook non caricati: {}", e.getMessage());
            return attivi != null ? attivi : List.of();
        }
    }

    // ================================================================= gestione (Studio)

    public record Dati(String nome, String url, String descrizione, List<String> eventi, List<String> processi, Boolean attivo) {}

    public List<Webhook> elenco() {
        return webhook.findAllByOrderByNomeAsc();
    }

    public Webhook trova(Long id) {
        return webhook.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook non trovato"));
    }

    @Transactional
    public Webhook crea(Dati d) {
        String nome = d.nome() == null ? "" : d.nome().trim();
        if (nome.isEmpty() || nome.length() > 80) throw bad("Il nome è obbligatorio (al massimo 80 caratteri)");
        if (webhook.findByNome(nome).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste già un webhook con questo nome");
        Webhook w = new Webhook();
        w.setNome(nome);
        w.setCreato(OffsetDateTime.now());
        w.setCreatoDa(currentUser.username());
        w.setSegreto(cifratura.cifra(FirmaWebhook.nuovoSegreto()));
        applica(w, d);
        webhook.save(w);
        registro.registra("WEBHOOK_CREATO", nome, w.getUrl() + " · eventi: " + (w.getEventi() == null ? "tutti" : w.getEventi()));
        ricarica();
        return w;
    }

    @Transactional
    public Webhook modifica(Long id, Dati d) {
        Webhook w = trova(id);
        if (d.nome() != null && !d.nome().isBlank() && !d.nome().trim().equals(w.getNome())) {
            String nome = d.nome().trim();
            if (nome.length() > 80) throw bad("Il nome può avere al massimo 80 caratteri");
            if (webhook.findByNome(nome).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste già un webhook con questo nome");
            w.setNome(nome);
        }
        applica(w, d);
        registro.registra("WEBHOOK_MODIFICATO", w.getNome(), (w.isAttivo() ? "Attivo" : "Sospeso") + " · " + w.getUrl());
        ricarica();
        return w;
    }

    @Transactional
    public void elimina(Long id) {
        Webhook w = trova(id);
        consegne.eliminaDelWebhook(id);
        webhook.delete(w);
        registro.registra("WEBHOOK_ELIMINATO", w.getNome(), w.getUrl());
        ricarica();
    }

    /** Il segreto in chiaro, per configurare chi riceve (lo vede solo l'amministratore; resta nel registro). */
    @Transactional
    public String segreto(Long id) {
        Webhook w = trova(id);
        registro.registra("WEBHOOK_SEGRETO_LETTO", w.getNome(), null);
        return cifratura.decifra(w.getSegreto());
    }

    @Transactional
    public String rigeneraSegreto(Long id) {
        Webhook w = trova(id);
        String s = FirmaWebhook.nuovoSegreto();
        w.setSegreto(cifratura.cifra(s));
        registro.registra("WEBHOOK_SEGRETO_RIGENERATO", w.getNome(), null);
        return s;
    }

    public String segretoInChiaro(Webhook w) {
        return cifratura.decifra(w.getSegreto());
    }

    /** Accoda un evento di prova solo per questo webhook (anche se sospeso: si consegna subito, a mano). */
    @Transactional
    public ConsegnaWebhook accodaProva(Long id) {
        Webhook w = trova(id);
        String eventoId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> pratica = new LinkedHashMap<>();
        pratica.put("id", "0");
        pratica.put("titolo", "Pratica di prova");
        pratica.put("processo", "prova");
        pratica.put("processoNome", "Prova del webhook");
        pratica.put("riferimento", null);
        String payload = payload(eventoId, TipiEvento.PROVA, pratica, null,
            List.of(Map.of("etichetta", "Messaggio", "valore", "Se leggi questo, il webhook \"" + w.getNome() + "\" funziona.")));
        return consegne.save(nuovaConsegna(w.getId(), eventoId, TipiEvento.PROVA, null, payload));
    }

    private void applica(Webhook w, Dati d) {
        if (d.url() != null || w.getUrl() == null) w.setUrl(controllaUrl(d.url()));
        if (d.descrizione() != null) w.setDescrizione(d.descrizione().isBlank() ? null : d.descrizione().trim());
        if (d.eventi() != null) {
            Set<String> e = new LinkedHashSet<>();
            for (String t : d.eventi()) {
                if (!TipiEvento.TUTTI.containsKey(t)) throw bad("Evento sconosciuto: " + t);
                e.add(t);
            }
            w.setEventi(e.isEmpty() ? null : String.join(",", e));
        }
        if (d.processi() != null) {
            Set<String> p = new LinkedHashSet<>();
            for (String x : d.processi()) {
                if (x == null || x.isBlank()) continue;
                if (!x.trim().matches("[\\w.-]+")) throw bad("Chiave di processo non valida: " + x);
                p.add(x.trim());
            }
            w.setProcessi(p.isEmpty() ? null : String.join(",", p));
        }
        if (d.attivo() != null) w.setAttivo(d.attivo());
    }

    static String controllaUrl(String url) {
        if (url == null || url.isBlank()) throw bad("L'indirizzo è obbligatorio");
        String u = url.trim();
        URI uri;
        try {
            uri = URI.create(u);
        } catch (IllegalArgumentException e) {
            throw bad("Indirizzo non valido");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            throw bad("L'indirizzo deve iniziare con https:// (o http:// per le prove)");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw bad("Indirizzo senza nome del server");
        if (uri.getUserInfo() != null) throw bad("Non mettere utente e password nell'indirizzo: usa la firma del webhook");
        if (u.length() > 1000) throw bad("Indirizzo troppo lungo");
        return u;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
