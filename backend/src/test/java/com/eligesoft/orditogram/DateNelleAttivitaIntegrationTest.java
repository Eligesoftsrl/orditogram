package com.eligesoft.orditogram;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le date scritte in un'attività restano testo ISO ("2010-03-01"): prima diventavano java.util.Date e la scheda della
 * pratica riceveva "Mon Mar 01 00:00:00 CET 2010", che l'interfaccia non sa leggere. Le date già salvate come Date
 * (pratiche vecchie) escono in ISO.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DateNelleAttivitaIntegrationTest {

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
          <process id="dateAttivita" name="Date nelle attivita" isExecutable="true">
            <startEvent id="inizio" activiti:formKey="da-avvio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="dati"/>
            <userTask id="dati" name="Scrivi le date" activiti:candidateGroups="operatori" activiti:formKey="da-dati"/>
            <sequenceFlow id="f2" sourceRef="dati" targetRef="controllo"/>
            <userTask id="controllo" name="Controlla" activiti:candidateGroups="operatori" activiti:formKey="da-controllo"/>
            <sequenceFlow id="f3" sourceRef="controllo" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    private static final Map<String, String> FORM = Map.of(
        "da-avvio", """
            { "title": "Avvio", "fields": [ { "name": "oggetto", "label": "Oggetto", "type": "text", "titolo": true } ] }""",
        "da-dati", """
            { "title": "Date", "fields": [ { "name": "costituzione", "label": "Data di costituzione", "type": "date" },
              { "name": "oggetto", "label": "Oggetto", "type": "text", "titolo": true } ] }""",
        "da-controllo", """
            { "title": "Controllo", "fields": [
              { "name": "costituzione", "label": "Data di costituzione", "type": "date", "readonly": true },
              { "name": "vecchia", "label": "Data vecchia", "type": "date", "readonly": true },
              { "name": "nota", "label": "Nota", "type": "text" } ] }""");

    @Autowired TestRestTemplate rest;
    @Autowired HistoryService historyService;
    @Autowired RuntimeService runtimeService;

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> attivita(String pratica) {
        ResponseEntity<List> elenco = come("mario").exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        return ((List<Map<String, Object>>) elenco.getBody()).stream()
            .filter(t -> pratica.equals(t.get("processInstanceId"))).findFirst().orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void leDateRestanoIso() {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("date-attivita.bpmn20.xml", BPMN));
        FORM.forEach((k, v) -> corpo.add("forms", file(k + ".json", v)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> pubblicato = come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, pubblicato.getStatusCode(), () -> "pubblicazione: " + pubblicato.getBody());

        ResponseEntity<Map> avvio = come("mario").postForEntity("/api/processes/dateAttivita/start",
            new HttpEntity<>(Map.of("oggetto", "Impresa di prova"), xhr()), Map.class);
        assertEquals(HttpStatus.OK, avvio.getStatusCode(), () -> "avvio: " + avvio.getBody());
        String pratica = (String) avvio.getBody().get("id");

        // una pratica vecchia, con la data salvata come java.util.Date
        runtimeService.setVariable(pratica, "vecchia",
            Date.from(LocalDate.of(2005, 1, 4).atStartOfDay(ZoneId.systemDefault()).toInstant()));

        ResponseEntity<Map> fatto = come("mario").postForEntity("/api/tasks/" + attivita(pratica).get("id") + "/complete",
            new HttpEntity<>(Map.of("costituzione", "2010-03-01", "oggetto", "Impresa di prova, titolo nuovo"), xhr()), Map.class);
        assertEquals(HttpStatus.OK, fatto.getStatusCode(), () -> "completamento: " + fatto.getBody());

        Object salvata = historyService.createHistoricProcessInstanceQuery().processInstanceId(pratica)
            .includeProcessVariables().singleResult().getProcessVariables().get("costituzione");
        assertEquals("2010-03-01", salvata, "la data resta testo ISO");
        // il campo "titolo" del form dell'attività aggiorna il titolo della pratica, anche nello storico
        assertEquals("Impresa di prova, titolo nuovo", historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(pratica).singleResult().getName());

        Map<String, Object> controllo = come("mario").exchange("/api/tasks/" + attivita(pratica).get("id"), HttpMethod.GET,
            new HttpEntity<>(xhr()), Map.class).getBody();
        Map<String, Object> variabili = (Map<String, Object>) controllo.get("variables");
        assertEquals("2010-03-01", variabili.get("costituzione"));
        assertEquals("2005-01-04", variabili.get("vecchia"), () -> "variabili: " + variabili);

        ResponseEntity<Map> scheda = come("mario").exchange("/api/pratiche/" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.OK, scheda.getStatusCode(), () -> "scheda: " + scheda.getBody());
        List<Map<String, Object>> dati = (List<Map<String, Object>>) scheda.getBody().get("dati");
        Map<String, String> valori = new java.util.HashMap<>();
        dati.forEach(d -> valori.put((String) d.get("nome"), (String) d.get("value")));
        assertEquals("2010-03-01", valori.get("costituzione"));
        assertEquals("2005-01-04", valori.get("vecchia"));
    }
}
