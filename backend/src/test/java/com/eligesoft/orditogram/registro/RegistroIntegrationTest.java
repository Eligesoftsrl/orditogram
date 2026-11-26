package com.eligesoft.orditogram.registro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registro delle letture (chi apre una pratica, anche quando è negato), degli accessi con utente e password e degli
 * errori imprevisti (codice per l'assistenza, nessun dettaglio tecnico a chi usa l'applicazione).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RegistroIntegrationTest.Guasto.class)
class RegistroIntegrationTest {

    /** Un endpoint che fallisce in modo imprevisto, solo per questo test. */
    @TestConfiguration
    @RestController
    static class Guasto {
        @GetMapping("/api/prova-registro/guasto")
        public Map<String, Object> guasto() {
            throw new UnsupportedOperationException("dettaglio interno che non deve uscire");
        }
    }

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
          <process id="segnalazioneRegistro" name="Segnalazione" isExecutable="true" activiti:candidateStarterGroups="operatori">
            <startEvent id="inizio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="esame"/>
            <userTask id="esame" name="Esamina" activiti:candidateGroups="operatori"/>
            <sequenceFlow id="f2" sourceRef="esame" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    @Autowired TestRestTemplate rest;
    @Autowired RegistroLetturaRepository letture;
    @Autowired RegistroAzioneRepository azioni;

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    private static ByteArrayResource file(String nome, String contenuto) {
        return new ByteArrayResource(contenuto.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return nome; }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void lettureConsentiteENegate() {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("segnalazione-registro.bpmn20.xml", BPMN));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertEquals(HttpStatus.OK, come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class).getStatusCode());
        ResponseEntity<Map> avvio = come("mario").postForEntity("/api/processes/segnalazioneRegistro/start",
            new HttpEntity<>(Map.of(), xhr()), Map.class);
        assertEquals(HttpStatus.OK, avvio.getStatusCode(), () -> "avvio: " + avvio.getBody());
        String pratica = (String) avvio.getBody().get("id");

        assertEquals(HttpStatus.OK, come("mario").exchange("/api/pratiche/" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getStatusCode());
        // giulia (revisori) non la vede: la lettura negata resta nel registro
        assertEquals(HttpStatus.FORBIDDEN, come("giulia").exchange("/api/pratiche/" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getStatusCode());

        ResponseEntity<Map> chi = come("admin").exchange("/api/admin/registro/letture?oggetto=" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.OK, chi.getStatusCode());
        List<Map<String, Object>> righe = (List<Map<String, Object>>) chi.getBody().get("righe");
        assertEquals(2, righe.size(), () -> String.valueOf(righe));
        assertEquals(Map.of("utente", "giulia", "azione", "PRATICA_APERTA", "esito", RegistroLettura.NEGATA),
            Map.of("utente", righe.get(0).get("utente"), "azione", righe.get(0).get("azione"), "esito", righe.get(0).get("esito")));
        assertEquals(Map.of("utente", "mario", "esito", RegistroLettura.CONSENTITA),
            Map.of("utente", righe.get(1).get("utente"), "esito", righe.get(1).get("esito")));
        assertNotNull(righe.get(1).get("indirizzo"));

        // filtro per utente
        ResponseEntity<Map> diGiulia = come("admin").exchange("/api/admin/registro/letture?utente=GIULIA&esito=NEGATA", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertTrue(((Number) diGiulia.getBody().get("totale")).longValue() >= 1);

        // chi non ha il permesso del registro non lo legge
        assertEquals(HttpStatus.FORBIDDEN, come("mario").exchange("/api/admin/registro/letture", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getStatusCode());
    }

    @Test
    void accessoConPasswordRegistratoUnaVoltaPerFinestra() {
        long prima = azioni.findAll().stream().filter(a -> "ACCESSO".equals(a.getAzione()) && "laura".equals(a.getUtente())).count();
        come("laura").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        come("laura").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        long dopo = azioni.findAll().stream().filter(a -> "ACCESSO".equals(a.getAzione()) && "laura".equals(a.getUtente())).count();
        assertTrue(dopo - prima <= 1 && dopo >= 1, "un accesso nella finestra: prima " + prima + ", dopo " + dopo);

        // password sbagliata: accesso negato
        rest.withBasicAuth("laura", "sbagliata").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertTrue(azioni.findAll().stream().anyMatch(a -> "ACCESSO_NEGATO".equals(a.getAzione()) && "laura".equals(a.getOggetto())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void erroreImprevistoConCodiceSenzaDettagli() {
        ResponseEntity<Map> risposta = come("mario").exchange("/api/prova-registro/guasto", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, risposta.getStatusCode());
        String codice = (String) risposta.getBody().get("codice");
        assertNotNull(codice);
        assertTrue(codice.matches("E-[0-9A-F]{8}"), codice);
        assertTrue(((String) risposta.getBody().get("message")).contains(codice));
        assertFalse(String.valueOf(risposta.getBody()).contains("dettaglio interno"), "nessun dettaglio tecnico a chi usa l'app");

        ResponseEntity<Map> trovato = come("admin").exchange("/api/admin/registro/errori?q=" + codice, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        List<Map<String, Object>> righe = (List<Map<String, Object>>) trovato.getBody().get("righe");
        assertEquals(1, righe.size());
        Map<String, Object> errore = righe.get(0);
        assertEquals("mario", errore.get("utente"));
        assertEquals("GET /api/prova-registro/guasto", errore.get("richiesta"));
        assertEquals(UnsupportedOperationException.class.getName(), errore.get("tipo"));
        assertTrue(((String) errore.get("messaggio")).contains("dettaglio interno"));
        assertTrue(((String) errore.get("traccia")).contains("Guasto.guasto"));
    }
}
