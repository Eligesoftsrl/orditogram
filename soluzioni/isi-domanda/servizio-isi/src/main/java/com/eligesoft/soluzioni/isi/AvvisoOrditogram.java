package com.eligesoft.soluzioni.isi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;

/** Consegna l'esito di un elenco alla pratica che lo aspetta: messaggio {@code esitoElenco} di Orditogram. */
@Component
public class AvvisoOrditogram {
    private static final Logger LOG = LoggerFactory.getLogger(AvvisoOrditogram.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Impostazioni impostazioni;

    public AvvisoOrditogram(Impostazioni impostazioni) {
        this.impostazioni = impostazioni;
    }

    /** Null se Orditogram non è configurato: la pubblicazione non avvisa nessuno. */
    public BiFunction<Registro.Riga, String, Boolean> avvisatore() {
        if (Configurazione.vuoto(impostazioni.orditogramUrl()) || Configurazione.vuoto(impostazioni.orditogramChiave())) {
            return null;
        }
        return this::invia;
    }

    boolean invia(Registro.Riga riga, String termine) {
        try {
            String corpo = Regole.JSON.writeValueAsString(Map.of(
                    "cerca", Map.of("numeroDomanda", riga.numero()),
                    "dati", Map.of("esitoElenco", riga.esito(), "posizioneElenco", String.valueOf(riga.posizione()),
                            "termineDocumenti", termine)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(impostazioni.orditogramUrl().replaceAll("/+$", "")
                            + "/api/messaggi/esitoElenco"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", impostazioni.orditogramChiave())
                    .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
            HttpResponse<Void> r = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() / 100 == 2) return true;
            LOG.warn("Avviso non consegnato a {}: risposta {}", riga.numero(), r.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) { // la pratica può non aspettare ancora (es. rinunciata): si annota e si prosegue
            LOG.warn("Avviso non consegnato a {}: {}", riga.numero(), e.toString());
        }
        return false;
    }
}
