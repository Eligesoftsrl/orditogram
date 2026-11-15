package com.eligesoft.orditogram.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consegna degli eventi ai webhook: ogni pochi secondi prende le consegne pronte, le invia firmate
 * e registra l'esito. Se il destinatario non risponde con 2xx riprova più tardi (webhook/FirmaWebhook.attesaDopo),
 * dopo l'ultimo tentativo la consegna risulta fallita e si può ripetere a mano dallo Studio.
 *
 * Le chiamate HTTP avvengono fuori dalle transazioni: un destinatario lento non blocca il database.
 */
@Component
public class InvioWebhook {

    private static final Logger log = LoggerFactory.getLogger(InvioWebhook.class);
    private static final int LOTTO = 20;
    private static final int MAX_PER_CICLO = 200;
    private static final int MAX_RISPOSTA = 2000;

    private final ConsegnaWebhookRepository consegne;
    private final WebhookRepository webhook;
    private final WebhookService servizio;
    private final TransactionTemplate tx;
    private final HttpClient http;
    private final boolean automatico;
    private final int conservazioneGiorni;
    private final String agente;
    private final AtomicBoolean inCorso = new AtomicBoolean(false);

    public InvioWebhook(ConsegnaWebhookRepository consegne, WebhookRepository webhook, WebhookService servizio,
                        PlatformTransactionManager transazioni, ObjectProvider<BuildProperties> build,
                        @Value("${app.webhook.invio-automatico:true}") boolean automatico,
                        @Value("${app.webhook.conservazione-giorni:30}") int conservazioneGiorni,
                        @Value("${app.webhook.timeout-secondi:10}") int timeoutSecondi) {
        this.consegne = consegne;
        this.webhook = webhook;
        this.servizio = servizio;
        this.tx = new TransactionTemplate(transazioni);
        this.automatico = automatico;
        this.conservazioneGiorni = conservazioneGiorni;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSecondi));
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)   // un redirect potrebbe portare l'avviso altrove
            .build();
        BuildProperties b = build.getIfAvailable();
        this.agente = "Orditogram-Webhook/" + (b != null ? b.getVersion() : "sviluppo");
    }

    private final Duration timeout;

    /** Ciclo periodico (si spegne con app.webhook.invio-automatico=false, es. nei test). */
    @Scheduled(fixedDelayString = "${app.webhook.intervallo-ms:5000}", initialDelayString = "${app.webhook.intervallo-ms:5000}")
    public void ciclo() {
        if (automatico) eseguiCiclo();
    }

    /** Invia tutte le consegne pronte. Restituisce quante ne ha tentate. */
    public int eseguiCiclo() {
        if (!inCorso.compareAndSet(false, true)) return 0;
        int tentate = 0;
        try {
            OffsetDateTime ora = OffsetDateTime.now();
            tx.executeWithoutResult(s -> consegne.sblocca(ora.minusMinutes(5), ora));
            while (tentate < MAX_PER_CICLO) {
                List<Long> lotto = tx.execute(s -> {
                    List<ConsegnaWebhook> pronte = consegne.daInviare(OffsetDateTime.now(), PageRequest.of(0, LOTTO));
                    OffsetDateTime adesso = OffsetDateTime.now();
                    pronte.forEach(c -> { c.setStato(ConsegnaWebhook.IN_INVIO); c.setUltimoTentativo(adesso); });
                    return pronte.stream().map(ConsegnaWebhook::getId).toList();
                });
                if (lotto == null || lotto.isEmpty()) break;
                for (Long id : lotto) {
                    invia(id);
                    tentate++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Ciclo di invio dei webhook interrotto: {}", e.getMessage());
        } finally {
            inCorso.set(false);
        }
        return tentate;
    }

    /** Invia subito una consegna (prova, ripetizione a mano) e ne restituisce lo stato aggiornato. */
    public ConsegnaWebhook inviaOra(Long consegnaId) {
        tx.executeWithoutResult(s -> consegne.findById(consegnaId).ifPresent(c -> {
            c.setStato(ConsegnaWebhook.IN_INVIO);
            c.setUltimoTentativo(OffsetDateTime.now());
        }));
        invia(consegnaId);
        return consegne.findById(consegnaId).orElseThrow();
    }

    public record Esito(Integer codice, String risposta, String errore, long durataMs) {
        boolean riuscito() { return codice != null && codice >= 200 && codice < 300; }
    }

    private void invia(Long consegnaId) {
        Optional<ConsegnaWebhook> oc = consegne.findById(consegnaId);
        if (oc.isEmpty()) return;
        ConsegnaWebhook c = oc.get();
        Optional<Webhook> ow = webhook.findById(c.getWebhookId());
        Esito esito;
        if (ow.isEmpty()) {
            esito = new Esito(null, null, "Webhook eliminato", 0);
        } else {
            esito = chiama(ow.get(), c);
        }
        registraEsito(consegnaId, esito);
    }

    Esito chiama(Webhook w, ConsegnaWebhook c) {
        long inizio = System.nanoTime();
        try {
            long t = System.currentTimeMillis() / 1000;
            HttpRequest richiesta = HttpRequest.newBuilder(URI.create(w.getUrl()))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", agente)
                .header("X-Orditogram-Evento", c.getTipo())
                .header("X-Orditogram-Consegna", String.valueOf(c.getId()))
                .header(FirmaWebhook.INTESTAZIONE, FirmaWebhook.firma(servizio.segretoInChiaro(w), t, c.getPayload()))
                .POST(HttpRequest.BodyPublishers.ofString(c.getPayload(), StandardCharsets.UTF_8))
                .build();
            HttpResponse<InputStream> r = http.send(richiesta, HttpResponse.BodyHandlers.ofInputStream());
            String corpo;
            try (InputStream in = r.body()) {
                corpo = new String(in.readNBytes(MAX_RISPOSTA), StandardCharsets.UTF_8);
            }
            return new Esito(r.statusCode(), corpo, r.statusCode() >= 300 && r.statusCode() < 400
                ? "Il destinatario ha risposto con un redirect: Orditogram non lo segue, aggiorna l'indirizzo" : null, millis(inizio));
        } catch (java.net.http.HttpTimeoutException e) {
            return new Esito(null, null, "Nessuna risposta entro " + timeout.toSeconds() + " secondi", millis(inizio));
        } catch (java.net.ConnectException e) {
            return new Esito(null, null, "Connessione rifiutata o server irraggiungibile", millis(inizio));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Esito(null, null, "Invio interrotto", millis(inizio));
        } catch (Exception e) {
            return new Esito(null, null, e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""), millis(inizio));
        }
    }

    private void registraEsito(Long consegnaId, Esito e) {
        tx.executeWithoutResult(s -> consegne.findById(consegnaId).ifPresent(c -> {
            c.setTentativi(c.getTentativi() + 1);
            c.setUltimoTentativo(OffsetDateTime.now());
            c.setCodiceRisposta(e.codice());
            c.setRisposta(taglia(e.risposta(), MAX_RISPOSTA));
            c.setErrore(taglia(e.errore(), 1000));
            c.setDurataMs(e.durataMs());
            if (e.riuscito()) {
                c.setStato(ConsegnaWebhook.CONSEGNATA);
                c.setErrore(null);
            } else {
                Duration attesa = FirmaWebhook.attesaDopo(c.getTentativi());
                if (attesa == null || "Webhook eliminato".equals(e.errore())) {
                    c.setStato(ConsegnaWebhook.FALLITA);
                    log.warn("Webhook: consegna {} ({}) fallita dopo {} tentativi", c.getId(), c.getTipo(), c.getTentativi());
                } else {
                    c.setStato(ConsegnaWebhook.DA_RIPETERE);
                    c.setProssimoTentativo(OffsetDateTime.now().plus(attesa));
                }
                if (c.getErrore() == null && e.codice() != null) c.setErrore("Risposta " + e.codice());
            }
        }));
    }

    /** Ripete a mano una consegna fallita o in attesa di nuovo tentativo. */
    public ConsegnaWebhook ripeti(Long consegnaId) {
        tx.executeWithoutResult(s -> {
            ConsegnaWebhook c = consegne.findById(consegnaId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Consegna non trovata"));
            if (ConsegnaWebhook.CONSEGNATA.equals(c.getStato()) || ConsegnaWebhook.IN_INVIO.equals(c.getStato()))
                throw new IllegalStateException("La consegna è già " + (ConsegnaWebhook.CONSEGNATA.equals(c.getStato()) ? "riuscita" : "in corso"));
            c.setTentativi(Math.min(c.getTentativi(), FirmaWebhook.MAX_TENTATIVI - 1));
        });
        return inviaOra(consegnaId);
    }

    /** Ogni notte: si tolgono le consegne concluse più vecchie del periodo di conservazione. */
    @Scheduled(cron = "${app.webhook.pulizia-cron:0 17 3 * * *}")
    public void pulizia() {
        if (conservazioneGiorni <= 0) return;
        Integer n = tx.execute(s -> consegne.eliminaVecchie(OffsetDateTime.now().minusDays(conservazioneGiorni)));
        if (n != null && n > 0) log.info("Webhook: eliminate {} consegne più vecchie di {} giorni", n, conservazioneGiorni);
    }

    private static long millis(long inizioNano) {
        return (System.nanoTime() - inizioNano) / 1_000_000;
    }

    private static String taglia(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
