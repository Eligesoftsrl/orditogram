package com.eligesoft.orditogram;

import org.activiti.engine.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tabelle di righe: si compilano all'avvio, si salvano nella pratica, si rileggono nelle attività e nella scheda. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TabelleIntegrationTest {

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
          <process id="rimborsoSpese" name="Rimborso spese" isExecutable="true">
            <startEvent id="inizio" activiti:formKey="rs-avvio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="controllo"/>
            <userTask id="controllo" name="Controlla le spese" activiti:candidateGroups="operatori" activiti:formKey="rs-controllo"/>
            <sequenceFlow id="f2" sourceRef="controllo" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    private static final String COLONNE = """
        [ { "name": "descrizione", "label": "Descrizione", "type": "text", "required": true },
          { "name": "importo", "label": "Importo", "type": "number", "required": true, "min": 0.01 } ]""";

    private static final Map<String, String> FORM = Map.of(
        "rs-avvio", """
            { "title": "Richiesta di rimborso", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true },
              { "name": "voci", "label": "Voci di spesa", "type": "tabella", "required": true, "colonne": %s } ] }"""
            .formatted(COLONNE),
        "rs-controllo", """
            { "title": "Controllo", "fields": [
              { "name": "voci", "label": "Voci di spesa", "type": "tabella", "readonly": true, "colonne": %s },
              { "name": "approvate", "label": "Voci approvate", "type": "tabella", "colonne": %s } ] }"""
            .formatted(COLONNE, COLONNE));

    @Autowired TestRestTemplate rest;
    @Autowired HistoryService historyService;

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
    void leRigheArrivanoFinoAllaSchedaDellaPratica() {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("rimborso-spese.bpmn20.xml", BPMN));
        FORM.forEach((k, v) -> corpo.add("forms", file(k + ".json", v)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> pubblicato = come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, pubblicato.getStatusCode(), () -> "pubblicazione: " + pubblicato.getBody());

        // una riga che non rispetta le regole: la pratica non parte
        ResponseEntity<Map> rifiutata = come("mario").postForEntity("/api/processes/rimborsoSpese/start",
            new HttpEntity<>(Map.of("oggetto", "Trasferta a Milano", "voci", List.of(Map.of("descrizione", "Treno", "importo", "0"))), xhr()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, rifiutata.getStatusCode());
        assertTrue(String.valueOf(rifiutata.getBody()).contains("riga 1, Importo: deve essere almeno 0,01"), () -> String.valueOf(rifiutata.getBody()));

        // le righe vuote lasciate dall'interfaccia si tolgono
        ResponseEntity<Map> avvio = come("mario").postForEntity("/api/processes/rimborsoSpese/start",
            new HttpEntity<>(Map.of("oggetto", "Trasferta a Milano", "voci", List.of(
                Map.of("descrizione", "Treno", "importo", "45.50"),
                Map.of("descrizione", "Pranzo", "importo", "18"),
                Map.of("descrizione", "", "importo", ""))), xhr()), Map.class);
        assertEquals(HttpStatus.OK, avvio.getStatusCode(), () -> "avvio: " + avvio.getBody());
        String pratica = (String) avvio.getBody().get("id");

        Object salvate = historyService.createHistoricProcessInstanceQuery().processInstanceId(pratica)
            .includeProcessVariables().singleResult().getProcessVariables().get("voci");
        assertEquals(List.of(Map.of("descrizione", "Treno", "importo", "45.50"), Map.of("descrizione", "Pranzo", "importo", "18")), salvate);

        // l'attività riceve le righe
        ResponseEntity<List> attivita = come("mario").exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        Map<String, Object> controllo = ((List<Map<String, Object>>) attivita.getBody()).stream()
            .filter(t -> pratica.equals(t.get("processInstanceId"))).findFirst().orElseThrow();
        ResponseEntity<Map> dettaglio = come("mario").exchange("/api/tasks/" + controllo.get("id"), HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(2, ((List<?>) ((Map<String, Object>) dettaglio.getBody().get("variables")).get("voci")).size());

        // completamento con un'altra tabella
        ResponseEntity<Map> fatto = come("mario").postForEntity("/api/tasks/" + controllo.get("id") + "/complete",
            new HttpEntity<>(Map.of("approvate", List.of(Map.of("descrizione", "Treno", "importo", "45.50"))), xhr()), Map.class);
        assertEquals(HttpStatus.OK, fatto.getStatusCode(), () -> "completamento: " + fatto.getBody());

        // scheda della pratica: riassunto, righe e colonne
        ResponseEntity<Map> scheda = come("mario").exchange("/api/pratiche/" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.OK, scheda.getStatusCode(), () -> "scheda: " + scheda.getBody());
        List<Map<String, Object>> dati = (List<Map<String, Object>>) scheda.getBody().get("dati");
        Map<String, Object> voci = dati.stream().filter(d -> "voci".equals(d.get("nome"))).findFirst().orElseThrow();
        assertEquals("tabella", voci.get("tipo"));
        assertEquals("2 righe", voci.get("value"));
        assertEquals(2, ((List<?>) voci.get("righe")).size());
        assertEquals(List.of("descrizione", "importo"),
            ((List<Map<String, Object>>) voci.get("colonne")).stream().map(c -> c.get("nome")).toList());
        Map<String, Object> approvate = dati.stream().filter(d -> "approvate".equals(d.get("nome"))).findFirst().orElseThrow();
        assertEquals("1 riga", approvate.get("value"));
    }
}
