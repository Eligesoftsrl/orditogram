package com.eligesoft.orditogram.form;

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
 * Versioni dei form: un form cambiato nello Studio vale per le pratiche nuove; quelle già avviate continuano con la
 * versione con cui sono partite (un campo obbligatorio aggiunto non blocca le loro attività).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VersioniFormIntegrationTest {

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
          <process id="richiestaMateriale" name="Richiesta di materiale" isExecutable="true">
            <startEvent id="inizio" activiti:formKey="vf-avvio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="esame"/>
            <userTask id="esame" name="Esamina la richiesta" activiti:candidateGroups="operatori" activiti:formKey="vf-esame"/>
            <sequenceFlow id="f2" sourceRef="esame" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    private static final String AVVIO = """
        { "title": "Richiesta", "fields": [ { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true } ] }""";
    private static final String ESAME_V1 = """
        { "title": "Esame", "fields": [ { "name": "nota", "label": "Nota", "type": "text" } ] }""";
    private static final String ESAME_V2 = """
        { "title": "Esame", "fields": [ { "name": "nota", "label": "Nota", "type": "text" },
          { "name": "centroDiCosto", "label": "Centro di costo", "type": "text", "required": true } ] }""";

    @Autowired TestRestTemplate rest;
    @Autowired VersioniForm versioniForm;
    @Autowired PraticaFormRepository fissate;

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

    private String avvia(String oggetto) {
        ResponseEntity<Map> avvio = come("mario").postForEntity("/api/processes/richiestaMateriale/start",
            new HttpEntity<>(Map.of("oggetto", oggetto), xhr()), Map.class);
        assertEquals(HttpStatus.OK, avvio.getStatusCode(), () -> "avvio: " + avvio.getBody());
        return (String) avvio.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String attivita(String pratica) {
        ResponseEntity<List> attivita = come("mario").exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        return (String) ((List<Map<String, Object>>) attivita.getBody()).stream()
            .filter(t -> pratica.equals(t.get("processInstanceId"))).findFirst().orElseThrow().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<String> campiDelForm(String task) {
        ResponseEntity<Map> dettaglio = come("mario").exchange("/api/tasks/" + task, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.OK, dettaglio.getStatusCode(), () -> "attività: " + dettaglio.getBody());
        Map<String, Object> form = (Map<String, Object>) dettaglio.getBody().get("form");
        return ((List<Map<String, Object>>) form.get("fields")).stream().map(f -> (String) f.get("name")).toList();
    }

    private ResponseEntity<Map> completa(String task) {
        return come("mario").postForEntity("/api/tasks/" + task + "/complete", new HttpEntity<>(Map.of("nota", "ok"), xhr()), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lePraticheAvviateRestanoSullaLoroVersione() {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("richiesta-materiale.bpmn20.xml", BPMN));
        corpo.add("forms", file("vf-avvio.json", AVVIO));
        corpo.add("forms", file("vf-esame.json", ESAME_V1));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> pubblicato = come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, pubblicato.getStatusCode(), () -> "pubblicazione: " + pubblicato.getBody());

        String vecchia = avvia("Carta per la stampante");

        // l'analista aggiunge un campo obbligatorio
        HttpHeaders json = xhr();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> salvato = come("admin").exchange("/api/admin/forms/vf-esame", HttpMethod.PUT, new HttpEntity<>(ESAME_V2, json), Map.class);
        assertEquals(HttpStatus.OK, salvato.getStatusCode(), () -> "salvataggio: " + salvato.getBody());
        // salvare di nuovo lo stesso contenuto non crea un'altra versione
        come("admin").exchange("/api/admin/forms/vf-esame", HttpMethod.PUT, new HttpEntity<>(ESAME_V2, json), Map.class);

        ResponseEntity<List> versioni = come("admin").exchange("/api/admin/forms/vf-esame/versioni", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        assertEquals(List.of(2, 1), ((List<Map<String, Object>>) versioni.getBody()).stream().map(v -> v.get("versione")).toList());

        String nuova = avvia("Toner");

        // la pratica vecchia vede il form di allora e si completa senza il campo nuovo
        String taskVecchia = attivita(vecchia);
        assertEquals(List.of("nota"), campiDelForm(taskVecchia));
        ResponseEntity<Map> fatta = completa(taskVecchia);
        assertEquals(HttpStatus.OK, fatta.getStatusCode(), () -> "completamento: " + fatta.getBody());

        // la pratica nuova usa la versione 2: il campo obbligatorio serve
        String taskNuova = attivita(nuova);
        assertEquals(List.of("nota", "centroDiCosto"), campiDelForm(taskNuova));
        assertEquals(HttpStatus.BAD_REQUEST, completa(taskNuova).getStatusCode());

        // primo avvio dopo l'aggiornamento: le pratiche in corso senza versioni fissate le ricevono
        fissate.deleteAll();
        versioniForm.fissaPraticheInCorso();
        assertEquals(2, fissate.findByProcessInstanceIdAndFormKey(nuova, "vf-esame").orElseThrow().getVersione());
    }
}
