package com.eligesoft.orditogram.webhook;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Ricevitore di prova (POST /api/demo/webhook, solo con app.demo.servizi=true): tiene in memoria gli ultimi
 * avvisi ricevuti e dice se la firma è valida e di quale webhook. Serve a provare i webhook senza un sistema esterno.
 */
@Component
public class RicevitoreProva {

    private static final int MAX = 50;

    public record Ricevuto(OffsetDateTime quando, String evento, String consegna, String firma, String esitoFirma,
                           String corpo) {}

    private final Deque<Ricevuto> ricevuti = new ArrayDeque<>();
    private final WebhookRepository webhook;
    private final WebhookService servizio;

    public RicevitoreProva(WebhookRepository webhook, WebhookService servizio) {
        this.webhook = webhook;
        this.servizio = servizio;
    }

    public synchronized Ricevuto ricevi(String evento, String consegna, String firma, String corpo) {
        String esito = "Firma mancante";
        if (firma != null) {
            esito = "Firma NON valida (nessun webhook ha questo segreto, o l'avviso è troppo vecchio)";
            long ora = System.currentTimeMillis() / 1000;
            for (Webhook w : webhook.findAll()) {
                try {
                    if (FirmaWebhook.verifica(servizio.segretoInChiaro(w), firma, corpo, ora, 300)) {
                        esito = "Firma valida: webhook \"" + w.getNome() + "\"";
                        break;
                    }
                } catch (RuntimeException ignorata) {
                    // segreto non decifrabile: si passa al prossimo
                }
            }
        }
        Ricevuto r = new Ricevuto(OffsetDateTime.now(), evento, consegna, firma, esito,
            corpo == null ? "" : corpo.length() > 20_000 ? corpo.substring(0, 20_000) : corpo);
        ricevuti.addFirst(r);
        while (ricevuti.size() > MAX) ricevuti.removeLast();
        return r;
    }

    public synchronized List<Ricevuto> elenco() {
        return new ArrayList<>(ricevuti);
    }

    public synchronized void svuota() {
        ricevuti.clear();
    }
}
