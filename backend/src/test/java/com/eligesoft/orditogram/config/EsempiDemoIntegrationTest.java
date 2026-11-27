package com.eligesoft.orditogram.config;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Processi di esempio: ci sono solo con app.demo.processi; gli utenti esterni non vedono i processi non aperti a loro. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EsempiDemoIntegrationTest {

    @Autowired RepositoryService repository;
    @Autowired HistoryService storia;
    @Autowired RuntimeService runtime;
    @Autowired TestRestTemplate rest;

    /** I test che seguono usano gli esempi: si rimettono come li vuole il profilo di test. */
    @AfterEach
    void rimetti() throws Exception {
        new EsempiDemo(repository, storia, true).run(null);
    }

    @SuppressWarnings("unchecked")
    private List<String> avviabiliDa(String utente) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        List<Map<String, Object>> elenco = rest.withBasicAuth(utente, "password")
            .exchange("/api/processes", HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
        return elenco.stream().map(m -> (String) m.get("key")).toList();
    }

    private long avviabili(String chiave) {
        return repository.createProcessDefinitionQuery().processDefinitionKey(chiave).active().count();
    }

    @Test
    void gliEsterniNonVedonoIProcessiDelPersonale() {
        assertTrue(avviabiliDa("mario").contains("hello"));
        assertFalse(avviabiliDa("marco").contains("hello"), "un processo senza regole non è aperto agli esterni");
        assertFalse(avviabiliDa("marco").contains("pratica"));
    }

    @Test
    void senzaEsempiIProcessiSiTolgonoOSiFermano() throws Exception {
        assertTrue(avviabili("hello") > 0);
        // una pratica di esempio aperta: il processo resta, ma non si avvia più
        String aperta = runtime.startProcessInstanceByKey("pratica").getId();

        new EsempiDemo(repository, storia, false).run(null);

        assertEquals(0, avviabili("hello"));
        assertEquals(0, avviabili("pratica"));
        assertFalse(avviabiliDa("mario").contains("pratica"));
        assertNotNull(runtime.createProcessInstanceQuery().processInstanceId(aperta).singleResult(),
            "le pratiche già aperte proseguono");

        new EsempiDemo(repository, storia, true).run(null);
        assertTrue(avviabili("pratica") > 0, "con gli esempi accesi tornano avviabili");
        runtime.deleteProcessInstance(aperta, "fine del test");
    }
}
