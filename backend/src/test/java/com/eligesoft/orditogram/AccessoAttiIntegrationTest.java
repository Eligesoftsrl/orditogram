package com.eligesoft.orditogram;

import com.eligesoft.orditogram.calendario.CalendarioLavorativo;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Il processo dimostrativo "Accesso agli atti" eseguito davvero: applicazione, motore Activiti,
 * chiamate HTTP con utenti diversi, connettori, timer, messaggi. Database H2 in memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccessoAttiIntegrationTest {

    private static final Path ESEMPIO = Path.of("..", "esempi", "accesso-atti");
    private static final String[] FORM = {"aa-istanza", "aa-protocollo", "aa-esame", "aa-integrazioni", "aa-decisione"};

    @LocalServerPort int porta;
    @Autowired TestRestTemplate rest;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired ManagementService managementService;

    private String xmlOriginale;

    @BeforeEach
    void leggiEsempio() throws Exception {
        xmlOriginale = Files.readString(ESEMPIO.resolve("accesso-atti.bpmn20.xml"));
    }

    // ------------------------------------------------------------ supporto

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    private static ByteArrayResource file(String nome, byte[] contenuto) {
        return new ByteArrayResource(contenuto) {
            @Override public String getFilename() { return nome; }
        };
    }

    /** Pubblica il processo (con l'indirizzo del protocollo indicato) e i suoi form. */
    private void pubblica(String indirizzoProtocollo) throws Exception {
        String xml = xmlOriginale.replace("http://localhost:8080/api/demo/protocollo", indirizzoProtocollo);
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("accesso-atti.bpmn20.xml", xml.getBytes(StandardCharsets.UTF_8)));
        for (String f : FORM) corpo.add("forms", file(f + ".json", Files.readAllBytes(ESEMPIO.resolve(f + ".json"))));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> r = come("admin").postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "pubblicazione: " + r.getBody());
    }

    private String protocolloDemo() {
        return "http://localhost:" + porta + "/api/demo/protocollo";
    }

    /** mario registra una richiesta, con un documento allegato. Restituisce l'id della pratica. */
    private String avvia(String oggetto) {
        String dati = """
            {"oggetto": "%s", "richiedente": "Giuseppe Verdi", "emailRichiedente": "giuseppe@example.com",
             "documentiRichiesti": "Verbale del 3 marzo", "motivazione": "Ricorso in corso", "tipoAccesso": "Visione"}
            """.formatted(oggetto);
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("data", dati);
        corpo.add("files", file("istanza.txt", "Istanza di accesso".getBytes(StandardCharsets.UTF_8)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> r = come("mario").postForEntity("/api/processes/accessoAtti/start", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "avvio: " + r.getBody());
        return (String) r.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attivita(String utente, String pratica, String nome) {
        ResponseEntity<List> r = come(utente).exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), List.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        return ((List<Map<String, Object>>) r.getBody()).stream()
            .filter(t -> pratica.equals(t.get("processInstanceId")) && nome.equals(t.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(utente + " non vede l'attività \"" + nome + "\" della pratica " + pratica));
    }

    private void completa(String utente, Map<String, Object> attivita, Map<String, Object> valori) {
        ResponseEntity<Map> r = come(utente).postForEntity("/api/tasks/" + attivita.get("id") + "/complete",
            new HttpEntity<>(valori, xhr()), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "completamento: " + r.getBody());
    }

    private boolean passataDa(String pratica, String elemento) {
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(pratica).activityId(elemento).count() > 0;
    }

    private boolean conclusa(String pratica) {
        return historyService.createHistoricProcessInstanceQuery().processInstanceId(pratica).finished().count() == 1;
    }

    @SuppressWarnings("unchecked")
    private List<String> tipiEventi(String pratica) {
        Map<String, Object> p = come("admin").exchange("/api/pratiche/" + pratica, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        return ((List<Map<String, Object>>) p.get("eventi")).stream().map(e -> (String) e.get("tipo")).toList();
    }

    // ------------------------------------------------------------ scenari

    @Test
    void accessoEPermessi() {
        assertEquals(HttpStatus.OK, come("admin").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.withBasicAuth("admin", "sbagliata")
            .exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, come("mario").exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(xhr()), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.getForEntity("/actuator/health", String.class).getStatusCode());
    }

    @Test
    void percorsoCompletoConAccoglimento() throws Exception {
        pubblica(protocolloDemo());
        String pi = avvia("Verbali commissione");

        // il protocollo di prova ha risposto: numero e data sono dati della pratica
        Object numero = runtimeService.getVariable(pi, "numeroProtocollo");
        assertNotNull(numero, "numero di protocollo");
        assertTrue(numero.toString().matches("\\d{4}/\\d{6}"), "formato del protocollo: " + numero);
        List<String> eventi = tipiEventi(pi);
        assertTrue(eventi.contains("CHIAMATA_API"), "chiamata nello storico: " + eventi);
        assertTrue(eventi.contains("EMAIL"), "email (non inviata, niente SMTP) nello storico: " + eventi);

        Map<String, Object> esame = attivita("anna", pi, "Esamina la richiesta");
        assertFalse(((List<?>) esame.get("scadenze")).isEmpty(), "il sollecito settimanale è visibile sull'attività");
        completa("anna", esame, Map.of("esito", "Accoglibile", "noteIstruttoria", "Tutto in ordine"));
        completa("anna", attivita("anna", pi, "Decidi sulla richiesta"),
            Map.of("decisione", "Accolta", "motivazioneDecisione", "Interesse dimostrato"));

        assertTrue(conclusa(pi));
        assertTrue(passataDa(pi, "comunicaEsito"));
        assertTrue(passataDa(pi, "fine"));
    }

    @Test
    void protocolloNonDisponibileSegueLaStradaDiErrore() throws Exception {
        pubblica("http://127.0.0.1:1/protocollo");   // nessuno risponde
        String pi = avvia("Prova protocollo giù");
        assertNull(runtimeService.getVariable(pi, "numeroProtocollo"));
        assertTrue(tipiEventi(pi).contains("ERRORE"));
        completa("mario", attivita("mario", pi, "Protocolla a mano"),
            Map.of("numeroProtocollo", "2026/999999", "dataProtocollo", "2026-10-05"));
        attivita("anna", pi, "Esamina la richiesta");
    }

    @Test
    void terminiDiTrentaGiorniESilenzioRigetto() throws Exception {
        pubblica(protocolloDemo());
        String pi = avvia("Prova termine");
        Job termine = managementService.createTimerJobQuery().processInstanceId(pi).list().stream()
            .filter(j -> j.getJobHandlerConfiguration() != null && j.getJobHandlerConfiguration().contains("termine30"))
            .findFirst().orElseThrow(() -> new AssertionError("timer del termine di 30 giorni"));

        // calendario "proroga": la scadenza cade sempre in un giorno lavorativo
        assertTrue(CalendarioLavorativo.standard().lavorativo(termine.getDuedate().toInstant().atZone(ZoneId.of("Europe/Rome")).toLocalDate()),
            "scadenza in giorno lavorativo: " + termine.getDuedate());

        // la scadenza in scrivania e nell'elenco
        @SuppressWarnings("unchecked")
        Map<String, Object> riepilogo = come("admin").exchange("/api/pratiche/" + pi, HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertNotNull(riepilogo.get("prossimaScadenza"));

        Job eseguibile = managementService.moveTimerToExecutableJob(termine.getId());
        managementService.executeJob(eseguibile.getId());

        assertTrue(conclusa(pi));
        assertTrue(passataDa(pi, "fineSilenzio"));
        assertTrue(tipiEventi(pi).contains("SCADENZA"));
    }

    @Test
    void rinunciaDalPortaleConMessaggio() throws Exception {
        pubblica(protocolloDemo());
        String pi = avvia("Prova rinuncia");
        String numero = (String) runtimeService.getVariable(pi, "numeroProtocollo");

        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "chiave-test");
        h.setContentType(MediaType.APPLICATION_JSON);

        // chiave sbagliata e dati non dichiarati vengono rifiutati
        HttpHeaders sbagliata = new HttpHeaders();
        sbagliata.set("X-API-Key", "no");
        sbagliata.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.UNAUTHORIZED, rest.postForEntity("/api/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("pratica", pi), sbagliata), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity("/api/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("pratica", pi, "dati", Map.of("avviataDa", "intruso")), h), String.class).getStatusCode());

        // la pratica si trova dal numero di protocollo
        ResponseEntity<Map> r = rest.postForEntity("/api/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("cerca", Map.of("numeroProtocollo", numero), "dati", Map.of("motivoRinuncia", "Documenti già ottenuti")), h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "messaggio: " + r.getBody());

        assertTrue(conclusa(pi));
        assertTrue(passataDa(pi, "archiviataRinuncia"));
        assertEquals("Documenti già ottenuti", historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi).variableName("motivoRinuncia").singleResult().getValue());
        assertTrue(tipiEventi(pi).contains("MESSAGGIO"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elencoPaginatoERicerca() throws Exception {
        pubblica(protocolloDemo());
        // una parola che nessun altro test usa: il database H2 è comune a tutte le classi di test e altre pratiche
        // contengono "Alfa" (es. le anagrafiche), quindi l'ordine delle classi non deve cambiare il conteggio
        avvia("Ricerca uno zibaldone");
        avvia("Ricerca due beta");
        Map<String, Object> pagina = come("admin").exchange("/api/pratiche?stato=tutte&dimensione=1", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals(1, ((List<?>) pagina.get("righe")).size());
        assertTrue(((Number) pagina.get("totale")).intValue() >= 2);

        Map<String, Object> trovate = come("admin").exchange("/api/pratiche?stato=tutte&q=ZIBALDONE", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals(1, ((Number) trovate.get("totale")).intValue(), "ricerca senza distinzione di maiuscole");

        // mario vede le sue pratiche, non tutte quelle dell'ente
        Map<String, Object> diMario = come("mario").exchange("/api/pratiche?stato=tutte&dimensione=100", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertTrue(((Number) diMario.get("totale")).intValue() >= 2);
        Map<String, Object> diGiulia = come("giulia").exchange("/api/pratiche?stato=tutte", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals(0, ((Number) diGiulia.get("totale")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void indicatoriDellaScrivania() throws Exception {
        pubblica(protocolloDemo());
        String pi = avvia("Indicatori uno");
        avvia("Indicatori due");
        completa("anna", attivita("anna", pi, "Esamina la richiesta"), Map.of("esito", "Accoglibile", "noteIstruttoria", "Ok"));

        Map<String, Object> ind = come("anna").exchange("/api/pratiche/indicatori", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertTrue(((Number) ind.get("inCorso")).intValue() >= 2, () -> "pratiche in corso: " + ind);
        List<Map<String, Object>> processi = (List<Map<String, Object>>) ind.get("processi");
        Map<String, Object> atti = processi.stream().filter(p -> "accessoAtti".equals(p.get("chiave"))).findFirst().orElseThrow();
        List<Map<String, Object>> passi = (List<Map<String, Object>>) atti.get("passi");
        assertTrue(passi.stream().anyMatch(p -> "Decidi sulla richiesta".equals(p.get("nome"))), () -> "dove si trovano: " + passi);
        assertTrue(passi.stream().anyMatch(p -> "Esamina la richiesta".equals(p.get("nome"))), () -> "dove si trovano: " + passi);
        int media = ((Number) atti.get("avanzamentoMedio")).intValue();
        assertTrue(media > 0 && media <= 95, "avanzamento stimato: " + media);
        assertEquals(8, ((List<?>) ind.get("settimane")).size());
        long aperteSettimana = ((List<Map<String, Object>>) ind.get("settimane")).stream().mapToLong(s -> ((Number) s.get("aperte")).longValue()).sum();
        assertTrue(aperteSettimana >= 2, "aperte nelle ultime settimane");
        assertEquals(15, ((List<?>) ind.get("termini")).size());

        // chi non vede le pratiche non vede nemmeno i loro numeri
        Map<String, Object> diGiulia = come("giulia").exchange("/api/pratiche/indicatori", HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals(0, ((Number) diGiulia.get("inCorso")).intValue());
        assertTrue(((List<?>) diGiulia.get("processi")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void controlloDelProcessoERegistro() throws Exception {
        Map<String, Object> esito = come("admin").postForEntity("/api/admin/processes/valida",
            new HttpEntity<>(Map.of("xml", xmlOriginale), xhr()), Map.class).getBody();
        assertEquals(List.of(), esito.get("errors"));

        pubblica(protocolloDemo());
        Map<String, Object> registro = come("admin").exchange("/api/admin/registro?azione=PROCESSO_PUBBLICATO",
            HttpMethod.GET, new HttpEntity<>(xhr()), Map.class).getBody();
        assertTrue(((Number) registro.get("totale")).intValue() >= 1);
    }
}
