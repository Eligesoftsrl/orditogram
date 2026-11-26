package com.eligesoft.soluzioni.isi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static com.eligesoft.soluzioni.isi.Prova.domanda;
import static org.assertj.core.api.Assertions.assertThat;

/** Il servizio avviato davvero: credenziale, calcolo, anagrafica, errori nel formato comune. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "isi.token=segreto-di-prova", "spring.datasource.url=jdbc:h2:mem:http;DB_CLOSE_DELAY=-1"})
class ServizioHttpTest {
    @Autowired
    TestRestTemplate http;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map> chiama(HttpMethod metodo, String percorso, Object corpo, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return http.exchange(percorso, metodo, new HttpEntity<>(corpo, h), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calcolaAnagraficaECredenziale() {
        Map<String, Object> d = domanda("asse", "2", "tipologia2", "a", "tassoMedio", "60", "speseProgetto", "100000",
                "speseTecniche", "8000");
        var r = chiama(HttpMethod.POST, "/calcola", d, "segreto-di-prova");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().get("contributoTotaleTesto")).isEqualTo("70.200,00 €");
        assertThat(r.getBody().get("modelloPerizia")).isEqualTo("B2");

        r = chiama(HttpMethod.GET, "/impresa?codiceFiscale=01234560789", null, "segreto-di-prova");
        assertThat(r.getBody().get("ragioneSociale")).isEqualTo("Carpenterie Rossi S.r.l.");

        r = chiama(HttpMethod.POST, "/calcola", d, "sbagliato");
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat((Map<String, Object>) r.getBody().get("errore")).containsEntry("codice", "NON_AUTORIZZATO");

        r = chiama(HttpMethod.GET, "/calendario", null, "segreto-di-prova");
        assertThat(r.getBody().get("compilazioneAperta")).isEqualTo("No");
        assertThat((String) r.getBody().get("messaggioCalendario")).contains("oggi non si possono creare domande");

        assertThat(chiama(HttpMethod.GET, "/salute", null, null).getStatusCode().value()).isEqualTo(200);
        assertThat(chiama(HttpMethod.POST, "/calcola?simula=errore", d, "segreto-di-prova").getStatusCode().value()).isEqualTo(503);
        r = chiama(HttpMethod.POST, "/codice", Map.of("numeroDomanda", "nessuna"), "segreto-di-prova");
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat((Map<String, Object>) r.getBody().get("errore")).containsEntry("codice", "DOMANDA_NON_TROVATA");
    }
}
