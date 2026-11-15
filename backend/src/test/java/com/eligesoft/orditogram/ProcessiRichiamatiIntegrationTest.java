package com.eligesoft.orditogram;

import org.activiti.engine.HistoryService;
import org.activiti.engine.history.HistoricProcessInstance;
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

/**
 * Processo richiamato (call activity): la pratica madre apre una pratica figlia, le passa i suoi dati,
 * aspetta che finisca e riprende con i dati raccolti dalla figlia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProcessiRichiamatiIntegrationTest {

    private static final String INTESTAZIONE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
        """;

    private static final String FIGLIO = INTESTAZIONE + """
          <process id="verificaTecnica" name="Verifica tecnica" isExecutable="true">
            <startEvent id="vInizio"/>
            <sequenceFlow id="v1" sourceRef="vInizio" targetRef="verifica"/>
            <userTask id="verifica" name="Esegui la verifica tecnica" activiti:candidateGroups="operatori" activiti:formKey="vt-verifica"/>
            <sequenceFlow id="v2" sourceRef="verifica" targetRef="vFine"/>
            <endEvent id="vFine"/>
          </process>
        </definitions>
        """;

    private static final String MADRE = INTESTAZIONE + """
          <process id="richiestaConVerifica" name="Richiesta con verifica" isExecutable="true">
            <startEvent id="inizio" activiti:formKey="rv-avvio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="chiamaVerifica"/>
            <callActivity id="chiamaVerifica" name="Verifica tecnica" calledElement="verificaTecnica"/>
            <sequenceFlow id="f2" sourceRef="chiamaVerifica" targetRef="decisione"/>
            <userTask id="decisione" name="Decidi" activiti:candidateGroups="operatori" activiti:formKey="rv-decisione"/>
            <sequenceFlow id="f3" sourceRef="decisione" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    private static final Map<String, String> FORM = Map.of(
        "vt-verifica", """
            { "title": "Verifica tecnica", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "readonly": true },
              { "name": "esitoTecnico", "label": "Esito tecnico", "type": "select", "required": true,
                "options": ["Favorevole", "Contrario"] } ] }""",
        "rv-avvio", """
            { "title": "Nuova richiesta", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true } ] }""",
        "rv-decisione", """
            { "title": "Decisione", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "readonly": true },
              { "name": "esitoTecnico", "label": "Esito tecnico", "type": "text", "readonly": true },
              { "name": "decisione", "label": "Decisione", "type": "text", "required": true } ] }""");

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

    private ResponseEntity<Map> pubblica(String nomeFile, String xml, String... form) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file(nomeFile, xml));
        for (String f : form) corpo.add("forms", file(f + ".json", FORM.get(f)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attivita(String pratica, String nome) {
        ResponseEntity<List> r = come("mario").exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        return ((List<Map<String, Object>>) r.getBody()).stream()
            .filter(t -> pratica.equals(t.get("processInstanceId")) && nome.equals(t.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("mario non vede l'attività \"" + nome + "\" della pratica " + pratica));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pratica(String id) {
        ResponseEntity<Map> r = come("mario").exchange("/api/pratiche/" + id, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "pratica: " + r.getBody());
        return r.getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void laPraticaMadreAspettaLaFigliaERiceveISuoiDati() {
        // il processo richiamato deve essere pubblicato prima
        ResponseEntity<Map> rifiutata = pubblica("richiesta-con-verifica.bpmn20.xml", MADRE, "rv-avvio", "rv-decisione");
        assertEquals(HttpStatus.BAD_REQUEST, rifiutata.getStatusCode());
        assertTrue(String.valueOf(rifiutata.getBody()).contains("non è pubblicato"), () -> String.valueOf(rifiutata.getBody()));

        assertEquals(HttpStatus.OK, pubblica("verifica-tecnica.bpmn20.xml", FIGLIO, "vt-verifica").getStatusCode());
        ResponseEntity<Map> ok = pubblica("richiesta-con-verifica.bpmn20.xml", MADRE, "rv-avvio", "rv-decisione");
        assertEquals(HttpStatus.OK, ok.getStatusCode(), () -> "pubblicazione: " + ok.getBody());

        // mario avvia la pratica madre
        ResponseEntity<Map> avvio = come("mario").postForEntity("/api/processes/richiestaConVerifica/start",
            new HttpEntity<>(Map.of("oggetto", "Ponte di via Roma"), xhr()), Map.class);
        assertEquals(HttpStatus.OK, avvio.getStatusCode(), () -> "avvio: " + avvio.getBody());
        String madre = (String) avvio.getBody().get("id");

        // si apre la pratica figlia, con i dati della madre e il suo titolo
        List<HistoricProcessInstance> figlie = historyService.createHistoricProcessInstanceQuery()
            .superProcessInstanceId(madre).includeProcessVariables().list();
        assertEquals(1, figlie.size());
        String figlia = figlie.get(0).getId();
        assertEquals("Ponte di via Roma", figlie.get(0).getProcessVariables().get("oggetto"));
        Map<String, Object> verifica = attivita(figlia, "Esegui la verifica tecnica");
        assertEquals("Ponte di via Roma", verifica.get("pratica"));

        // collegamenti visibili nelle due pratiche
        assertEquals(madre, ((Map<String, Object>) pratica(figlia).get("praticaMadre")).get("id"));
        List<Map<String, Object>> collegate = (List<Map<String, Object>>) pratica(madre).get("praticheFiglie");
        assertEquals(List.of(figlia), collegate.stream().map(c -> c.get("id")).toList());
        assertEquals("in corso", collegate.get(0).get("stato"));

        // la figlia finisce: la madre riprende con l'esito raccolto dalla figlia
        ResponseEntity<Map> fatto = come("mario").postForEntity("/api/tasks/" + verifica.get("id") + "/complete",
            new HttpEntity<>(Map.of("esitoTecnico", "Favorevole"), xhr()), Map.class);
        assertEquals(HttpStatus.OK, fatto.getStatusCode(), () -> "completamento: " + fatto.getBody());
        assertEquals(1, historyService.createHistoricProcessInstanceQuery().processInstanceId(figlia).finished().count());

        attivita(madre, "Decidi");
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(madre).includeProcessVariables().singleResult();
        assertEquals("Favorevole", h.getProcessVariables().get("esitoTecnico"));
        assertEquals("conclusa", ((List<Map<String, Object>>) pratica(madre).get("praticheFiglie")).get(0).get("stato"));
    }

    @Test
    void unProcessoNonPuoRichiamareSeStesso() {
        String xml = INTESTAZIONE + """
              <process id="giroInfinito" name="Giro infinito" isExecutable="true">
                <startEvent id="i"/>
                <sequenceFlow id="a" sourceRef="i" targetRef="c"/>
                <callActivity id="c" name="Ancora" calledElement="giroInfinito"/>
                <sequenceFlow id="b" sourceRef="c" targetRef="f"/>
                <endEvent id="f"/>
              </process>
            </definitions>
            """;
        ResponseEntity<Map> r = pubblica("giro.bpmn20.xml", xml);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertTrue(String.valueOf(r.getBody()).contains("non può richiamare sé stesso"), () -> String.valueOf(r.getBody()));
    }
}
