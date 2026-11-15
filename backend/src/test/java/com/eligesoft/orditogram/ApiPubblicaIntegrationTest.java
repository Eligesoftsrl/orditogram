package com.eligesoft.orditogram;

import com.eligesoft.orditogram.webhook.InvioWebhook;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API pubblica (/api/v1) e webhook, dal punto di vista di un sistema esterno: chiavi e permessi,
 * avvio con convalida e riferimento, lettura, documenti, messaggi, avvisi firmati e ripetizioni.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class ApiPubblicaIntegrationTest {

    private static final Path ESEMPIO = Path.of("..", "esempi", "accesso-atti");
    private static final String[] FORM = {"aa-istanza", "aa-protocollo", "aa-esame", "aa-integrazioni", "aa-decisione"};

    @LocalServerPort int porta;
    @Autowired TestRestTemplate rest;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired InvioWebhook invio;

    // ------------------------------------------------------------ supporto

    private TestRestTemplate admin() {
        return rest.withBasicAuth("admin", "admin");
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

    private void pubblica() throws Exception {
        String xml = Files.readString(ESEMPIO.resolve("accesso-atti.bpmn20.xml"))
            .replace("http://localhost:8080/api/demo/protocollo", "http://localhost:" + porta + "/api/demo/protocollo");
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("accesso-atti.bpmn20.xml", xml.getBytes(StandardCharsets.UTF_8)));
        for (String f : FORM) corpo.add("forms", file(f + ".json", Files.readAllBytes(ESEMPIO.resolve(f + ".json"))));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> r = admin().postForEntity("/api/admin/processes/deploy", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "pubblicazione: " + r.getBody());
    }

    /** Crea una chiave dallo Studio e ne restituisce il valore (visibile solo ora). */
    private String chiave(String nome, List<String> permessi, List<String> processi) {
        Map<String, Object> dati = new HashMap<>();
        dati.put("nome", nome + "-" + UUID.randomUUID().toString().substring(0, 6));
        dati.put("permessi", permessi);
        dati.put("processi", processi);
        ResponseEntity<Map> r = admin().postForEntity("/api/admin/integrazioni/chiavi", new HttpEntity<>(dati, xhr()), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "chiave: " + r.getBody());
        String valore = (String) r.getBody().get("valore");
        assertTrue(valore.startsWith("iter_") && valore.length() == 45, valore);
        return valore;
    }

    private static HttpHeaders conChiave(String chiave) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", chiave);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> get(String chiave, String url) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(conChiave(chiave)), Map.class);
    }

    private static Map<String, Object> datiValidi(String oggetto) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("oggetto", oggetto);
        d.put("richiedente", "Giuseppe Verdi");
        d.put("emailRichiedente", "giuseppe@example.com");
        d.put("documentiRichiesti", "Verbale del 3 marzo");
        d.put("motivazione", "Ricorso in corso");
        d.put("tipoAccesso", "Visione");
        return d;
    }

    /** Avvio dal "portale" con un documento allegato (multipart). */
    private ResponseEntity<Map> avviaConDocumento(String chiave, String riferimento, Map<String, Object> dati) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String richiesta = "{\"riferimento\": \"" + riferimento + "\", \"dati\": " + toJson(dati) + "}";
        corpo.add("richiesta", new HttpEntity<>(richiesta, json));
        corpo.add("documenti", file("istanza.txt", "Istanza firmata".getBytes(StandardCharsets.UTF_8)));
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", chiave);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/v1/processi/accessoAtti/pratiche", new HttpEntity<>(corpo, h), Map.class);
    }

    private static String toJson(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String codiceErrore(ResponseEntity<Map> r) {
        return (String) ((Map<String, Object>) r.getBody().get("errore")).get("codice");
    }

    @AfterEach
    void togliWebhook() {
        List<Map<String, Object>> elenco = admin().exchange("/api/admin/integrazioni/webhook", HttpMethod.GET,
            new HttpEntity<>(xhr()), List.class).getBody();
        if (elenco != null) for (Map<String, Object> w : elenco)
            admin().exchange("/api/admin/integrazioni/webhook/" + w.get("id"), HttpMethod.DELETE, new HttpEntity<>(xhr()), Void.class);
    }

    // ------------------------------------------------------------ scenari

    @Test
    void chiaviEPermessi() {
        ResponseEntity<Map> senza = rest.getForEntity("/api/v1/io", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, senza.getStatusCode());
        assertEquals("chiave_mancante", codiceErrore(senza));
        assertEquals("chiave_non_valida", codiceErrore(get("iter_" + "x".repeat(40), "/api/v1/io")));
        // l'API pubblica non accetta utente e password: solo chiavi
        assertEquals(HttpStatus.UNAUTHORIZED, admin().getForEntity("/api/v1/io", Map.class).getStatusCode());

        String sola = chiave("sola-lettura", List.of("pratiche:leggi"), List.of());
        ResponseEntity<Map> io = get(sola, "/api/v1/io");
        assertEquals(HttpStatus.OK, io.getStatusCode());
        assertEquals(List.of("pratiche:leggi"), io.getBody().get("permessi"));
        // anche come Bearer
        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(sola);
        assertEquals(HttpStatus.OK, rest.exchange("/api/v1/io", HttpMethod.GET, new HttpEntity<>(bearer), Map.class).getStatusCode());

        ResponseEntity<Map> vietato = rest.postForEntity("/api/v1/pratiche",
            new HttpEntity<>(Map.of("processo", "accessoAtti", "dati", Map.of()), conChiave(sola)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, vietato.getStatusCode());
        assertEquals("permesso_mancante", codiceErrore(vietato));

        // revocata: smette subito di funzionare
        List<Map<String, Object>> chiavi = admin().exchange("/api/admin/integrazioni/chiavi", HttpMethod.GET, new HttpEntity<>(xhr()), List.class).getBody();
        Object id = chiavi.stream().filter(c -> sola.startsWith((String) c.get("prefisso"))).findFirst().orElseThrow().get("id");
        admin().postForEntity("/api/admin/integrazioni/chiavi/" + id + "/revoca", new HttpEntity<>(xhr()), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, get(sola, "/api/v1/io").getStatusCode());

        // la gestione delle chiavi è solo per l'amministratore
        assertEquals(HttpStatus.FORBIDDEN, rest.withBasicAuth("mario", "password")
            .exchange("/api/admin/integrazioni/chiavi", HttpMethod.GET, new HttpEntity<>(xhr()), String.class).getStatusCode());
    }

    @Test
    void avvioConvalidaRiferimentoELettura() throws Exception {
        pubblica();
        String portale = chiave("portale", List.of("pratiche:avvia", "pratiche:leggi", "documenti:leggi", "documenti:scrivi"), List.of());

        // il processo si descrive da solo, con i campi del form di avvio
        List<Map<String, Object>> processi = rest.exchange("/api/v1/processi", HttpMethod.GET,
            new HttpEntity<>(conChiave(portale)), List.class).getBody();
        Map<String, Object> aa = processi.stream().filter(p -> "accessoAtti".equals(p.get("chiave"))).findFirst().orElseThrow();
        assertEquals("obbligatori", aa.get("allegati"));
        assertTrue(((List<Map<String, Object>>) aa.get("campi")).stream().anyMatch(c -> "tipoAccesso".equals(c.get("nome"))));

        // dati sbagliati: tutti gli errori insieme
        Map<String, Object> sbagliati = datiValidi("Prova API");
        sbagliati.put("tipoAccesso", "Fotocopia");
        sbagliati.put("emailRichiedent", "x@example.com");
        sbagliati.remove("emailRichiedente");
        ResponseEntity<Map> r422 = rest.postForEntity("/api/v1/processi/accessoAtti/pratiche",
            new HttpEntity<>(Map.of("dati", sbagliati), conChiave(portale)), Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, r422.getStatusCode());
        List<Map<String, Object>> campi = (List<Map<String, Object>>) ((Map<String, Object>) r422.getBody().get("errore")).get("campi");
        Set<String> conErrore = new HashSet<>();
        campi.forEach(c -> conErrore.add((String) c.get("campo")));
        assertEquals(Set.of("tipoAccesso", "emailRichiedent", "emailRichiedente", "documenti"), conErrore);

        // avvio corretto con documento e riferimento del portale
        String rif = "DOM-" + UUID.randomUUID();
        ResponseEntity<Map> creata = avviaConDocumento(portale, rif, datiValidi("Verbali del consiglio"));
        assertEquals(HttpStatus.CREATED, creata.getStatusCode(), () -> "avvio: " + creata.getBody());
        String id = (String) creata.getBody().get("id");
        assertEquals(true, creata.getBody().get("creata"));
        assertEquals(rif, creata.getBody().get("riferimento"));
        assertNotNull(creata.getHeaders().getLocation());

        // stessa richiesta ripetuta (es. dopo un timeout): stessa pratica, nessun doppione
        ResponseEntity<Map> ripetuta = avviaConDocumento(portale, rif, datiValidi("Verbali del consiglio"));
        assertEquals(HttpStatus.OK, ripetuta.getStatusCode());
        assertEquals(id, ripetuta.getBody().get("id"));
        assertEquals(false, ripetuta.getBody().get("creata"));
        assertEquals(1, historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(rif).count());

        // ricerca per riferimento e dettaglio
        Map<String, Object> pagina = get(portale, "/api/v1/pratiche?riferimento=" + rif).getBody();
        assertEquals(1, ((Number) pagina.get("totale")).intValue());
        Map<String, Object> p = get(portale, "/api/v1/pratiche/" + id).getBody();
        assertEquals("in_corso", p.get("stato"));
        assertEquals("Verbali del consiglio", p.get("titolo"));
        assertNotNull(((Map<String, Object>) p.get("dati")).get("numeroProtocollo"), "il protocollo è stato chiamato");
        assertTrue(((List<Map<String, Object>>) p.get("attivitaCorrenti")).stream().anyMatch(a -> "Esamina la richiesta".equals(a.get("nome"))));
        assertTrue(((List<Map<String, Object>>) p.get("messaggiAttesi")).stream().anyMatch(m -> "rinunciaRichiedente".equals(m.get("nome"))));

        // lo storico dice quale sistema ha avviato la pratica
        List<Map<String, Object>> storico = rest.exchange("/api/v1/pratiche/" + id + "/storico", HttpMethod.GET,
            new HttpEntity<>(conChiave(portale)), List.class).getBody();
        Map<String, Object> avvio = storico.stream().filter(e -> "AVVIO".equals(e.get("tipo"))).findFirst().orElseThrow();
        assertTrue(((String) avvio.get("utente")).startsWith("api:portale"));
        assertTrue(((String) avvio.get("utenteNome")).endsWith("(sistema esterno)"));

        // documenti: elenco, scaricamento, aggiunta
        List<Map<String, Object>> documenti = rest.exchange("/api/v1/pratiche/" + id + "/documenti", HttpMethod.GET,
            new HttpEntity<>(conChiave(portale)), List.class).getBody();
        assertEquals(1, documenti.size());
        Object versione = ((List<Map<String, Object>>) documenti.get(0).get("versioni")).get(0).get("id");
        ResponseEntity<byte[]> contenuto = rest.exchange("/api/v1/pratiche/" + id + "/documenti/versioni/" + versione,
            HttpMethod.GET, new HttpEntity<>(conChiave(portale)), byte[].class);
        assertEquals("Istanza firmata", new String(contenuto.getBody(), StandardCharsets.UTF_8));

        MultiValueMap<String, Object> doc = new LinkedMultiValueMap<>();
        doc.add("file", file("integrazione.txt", "Delega".getBytes(StandardCharsets.UTF_8)));
        doc.add("nota", "Delega del richiedente");
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", portale);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/v1/pratiche/" + id + "/documenti", new HttpEntity<>(doc, h), Map.class).getStatusCode());

        // la documentazione generata contiene il percorso del processo
        Map<String, Object> openapi = get(portale, "/api/v1/openapi.json").getBody();
        assertTrue(((Map<String, Object>) openapi.get("paths")).containsKey("/processi/accessoAtti/pratiche"));
    }

    @Test
    void chiaveLimitataAiSuoiProcessi() throws Exception {
        pubblica();
        String portale = chiave("portale-completo", List.of("pratiche:avvia", "pratiche:leggi"), List.of());
        String id = (String) avviaConDocumento(portale, "LIM-" + UUID.randomUUID(), datiValidi("Pratica riservata")).getBody().get("id");

        String altra = chiave("altro-ufficio", List.of("pratiche:avvia", "pratiche:leggi", "messaggi:invia"), List.of("altroProcesso"));
        assertEquals(HttpStatus.NOT_FOUND, get(altra, "/api/v1/pratiche/" + id).getStatusCode());
        assertEquals(0, ((List<?>) rest.exchange("/api/v1/processi", HttpMethod.GET, new HttpEntity<>(conChiave(altra)), List.class).getBody()).size());
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/v1/pratiche",
            new HttpEntity<>(Map.of("processo", "accessoAtti", "dati", datiValidi("x")), conChiave(altra)), Map.class).getStatusCode());
        // anche i messaggi: la pratica di un altro processo non si trova
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/v1/pratiche/" + id + "/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("dati", Map.of()), conChiave(altra)), Map.class).getStatusCode());
        Map<String, Object> openapi = get(altra, "/api/v1/openapi.json").getBody();
        assertFalse(((Map<String, Object>) openapi.get("paths")).containsKey("/processi/accessoAtti/pratiche"));
    }

    @Test
    void messaggioDalPortaleConLaChiaveDelloStudio() throws Exception {
        pubblica();
        String portale = chiave("portale-messaggi", List.of("pratiche:avvia", "pratiche:leggi", "messaggi:invia"), List.of("accessoAtti"));
        String rif = "MSG-" + UUID.randomUUID();
        String id = (String) avviaConDocumento(portale, rif, datiValidi("Rinuncia via API")).getBody().get("id");

        ResponseEntity<Map> r = rest.postForEntity("/api/v1/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("pratica", id, "dati", Map.of("motivoRinuncia", "Non serve più")), conChiave(portale)), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), () -> "messaggio: " + r.getBody());
        Map<String, Object> p = get(portale, "/api/v1/pratiche/" + id).getBody();
        assertEquals("conclusa", p.get("stato"));
        assertEquals("Non serve più", ((Map<String, Object>) p.get("dati")).get("motivoRinuncia"));

        // la stessa chiave vale anche sul vecchio indirizzo dei messaggi
        String id2 = (String) avviaConDocumento(portale, "MSG2-" + UUID.randomUUID(), datiValidi("Rinuncia vecchio indirizzo")).getBody().get("id");
        assertEquals(HttpStatus.OK, rest.postForEntity("/api/messaggi/rinunciaRichiedente",
            new HttpEntity<>(Map.of("pratica", id2), conChiave(portale)), Map.class).getStatusCode());
    }

    @Test
    void webhookFirmatiRipetutiEProvati() throws Exception {
        pubblica();
        admin().exchange("/api/admin/integrazioni/ricevuti", HttpMethod.DELETE, new HttpEntity<>(xhr()), Void.class);
        String ricevitore = "http://localhost:" + porta + "/api/demo/webhook";

        Map<String, Object> dati = new HashMap<>();
        dati.put("nome", "Gestionale");
        dati.put("url", ricevitore);
        dati.put("eventi", List.of("pratica.avviata", "attivita.creata", "documento.aggiunto"));
        ResponseEntity<Map> creato = admin().postForEntity("/api/admin/integrazioni/webhook", new HttpEntity<>(dati, xhr()), Map.class);
        assertEquals(HttpStatus.OK, creato.getStatusCode(), () -> "webhook: " + creato.getBody());
        assertTrue(((String) creato.getBody().get("segreto")).startsWith("whsec_"));

        Map<String, Object> rotto = new HashMap<>(dati);
        rotto.put("nome", "Sistema spento");
        rotto.put("url", ricevitore + "?simula=errore");
        rotto.put("eventi", List.of("pratica.avviata"));
        Object idRotto = admin().postForEntity("/api/admin/integrazioni/webhook", new HttpEntity<>(rotto, xhr()), Map.class).getBody().get("id");

        String portale = chiave("portale-webhook", List.of("pratiche:avvia"), List.of());
        String id = (String) avviaConDocumento(portale, "WH-" + UUID.randomUUID(), datiValidi("Pratica con avvisi")).getBody().get("id");

        assertTrue(invio.eseguiCiclo() >= 4);

        List<Map<String, Object>> ricevuti = admin().exchange("/api/admin/integrazioni/ricevuti", HttpMethod.GET,
            new HttpEntity<>(xhr()), List.class).getBody();
        List<String> eventi = ricevuti.stream().map(x -> (String) x.get("evento")).toList();
        assertTrue(eventi.containsAll(List.of("pratica.avviata", "attivita.creata", "documento.aggiunto")), eventi.toString());
        assertTrue(ricevuti.stream().allMatch(x -> ((String) x.get("esitoFirma")).startsWith("Firma valida")),
            () -> ricevuti.toString());
        assertTrue(ricevuti.stream().anyMatch(x -> ((String) x.get("corpo")).contains(id)));

        // il sistema spento risponde 503: la consegna si riproverà più tardi
        Map<String, Object> pagina = admin().exchange("/api/admin/integrazioni/consegne?webhook=" + idRotto, HttpMethod.GET,
            new HttpEntity<>(xhr()), Map.class).getBody();
        Map<String, Object> consegna = ((List<Map<String, Object>>) pagina.get("righe")).get(0);
        assertEquals("DA_RIPETERE", consegna.get("stato"));
        assertEquals(503, consegna.get("codiceRisposta"));
        assertEquals(1, consegna.get("tentativi"));
        assertNotNull(consegna.get("prossimoTentativo"));

        // sistemato l'indirizzo, la ripetizione a mano va a buon fine
        admin().exchange("/api/admin/integrazioni/webhook/" + idRotto, HttpMethod.PUT,
            new HttpEntity<>(Map.of("url", ricevitore), xhr()), Map.class);
        Map<String, Object> ripetuta = admin().postForEntity("/api/admin/integrazioni/consegne/" + consegna.get("id") + "/ripeti",
            new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals("CONSEGNATA", ripetuta.get("stato"));
        assertEquals(2, ripetuta.get("tentativi"));

        // il pulsante "Invia una prova"
        Map<String, Object> prova = admin().postForEntity("/api/admin/integrazioni/webhook/" + creato.getBody().get("id") + "/prova",
            new HttpEntity<>(xhr()), Map.class).getBody();
        assertEquals("CONSEGNATA", prova.get("stato"));
        assertEquals("prova", prova.get("tipo"));
    }
}
