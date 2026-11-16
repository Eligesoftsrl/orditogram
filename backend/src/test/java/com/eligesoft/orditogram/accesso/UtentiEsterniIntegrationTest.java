package com.eligesoft.orditogram.accesso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utenti esterni, organizzazioni e parti della pratica, con chiamate HTTP vere. Un cittadino avvia una pratica,
 * l'ufficio indica la controparte (un'organizzazione esterna), i suoi utenti lavorano l'attività assegnata a
 * quella parte. Si controlla la matrice degli accessi: chi vede cosa (pratiche, dati, storico, documenti) e che
 * nessuno arrivi alle pratiche degli altri cambiando l'id nella richiesta (IDOR).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UtentiEsterniIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;
    private String processo;
    private String sportello;

    @BeforeEach
    void pubblicaProcesso() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        processo = "reclamo" + sigla;
        sportello = "sportello-" + sigla;
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
              <process id="%1$s" name="Reclamo %2$s" isExecutable="true" activiti:candidateStarterGroups="esterni,%3$s">
                <startEvent id="inizio" activiti:formKey="ue-avvio-%2$s"/>
                <sequenceFlow id="f1" sourceRef="inizio" targetRef="istruttoria"/>
                <userTask id="istruttoria" name="Istruttoria" activiti:candidateGroups="%3$s" activiti:formKey="ue-istruttoria-%2$s"/>
                <sequenceFlow id="f2" sourceRef="istruttoria" targetRef="risposta"/>
                <userTask id="risposta" name="Risposta della controparte" activiti:candidateGroups="parte:controparte" activiti:formKey="ue-risposta-%2$s"/>
                <sequenceFlow id="f3" sourceRef="risposta" targetRef="fine"/>
                <endEvent id="fine"/>
              </process>
            </definitions>
            """.formatted(processo, sigla, sportello);
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file(processo + ".bpmn20.xml", xml));
        corpo.add("forms", file("ue-avvio-" + sigla + ".json", """
            { "title": "Reclamo", "documenti": "carica", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true, "visibileAlleParti": true },
              { "name": "descrizione", "label": "Descrizione", "type": "textarea" } ] }"""));
        corpo.add("forms", file("ue-istruttoria-" + sigla + ".json", """
            { "title": "Istruttoria", "documenti": "modifica", "fields": [
              { "name": "controparte", "label": "Controparte", "type": "organizzazione", "parte": "controparte", "required": true },
              { "name": "valutazione", "label": "Valutazione interna", "type": "textarea", "ruoliVisibili": ["auditor"] },
              { "name": "esito", "label": "Esito comunicato", "type": "text", "visibileAlleParti": true } ] }"""));
        corpo.add("forms", file("ue-risposta-" + sigla + ".json", """
            { "title": "Risposta", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "readonly": true },
              { "name": "risposta", "label": "Risposta", "type": "textarea", "required": true } ] }"""));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------ test

    @Test
    @SuppressWarnings("unchecked")
    void matriceDegliAccessiDiEsterniEParti() {
        Long studioA = organizzazione("studio-a", "Studio A");
        Long studioB = organizzazione("studio-b", "Studio B");
        String cittadino = utente("cittadino", "ESTERNO", List.of(), List.of());
        String legaleA = utente("legale-a", "ESTERNO", List.of(), List.of(studioA));
        String collegaA = utente("collega-a", "ESTERNO", List.of(), List.of(studioA));
        String legaleB = utente("legale-b", "ESTERNO", List.of(), List.of(studioB));
        String operatore = utente("operatore", "INTERNO", List.of(sportello), List.of());
        String lettore = utente("lettore", "INTERNO", List.of("lettori-" + sigla), List.of());
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/processes/" + processo + "/permessi",
            List.of(Map.of("azione", "VEDE", "tipoSoggetto", "GRUPPO", "soggetto", "lettori-" + sigla, "ambito", "TUTTE"))))
            .isEqualTo(HttpStatus.OK);

        // il cittadino avvia dal portale (gruppo automatico "esterni") con un allegato: lo vedranno le parti
        LinkedMultiValueMap<String, Object> avvio = new LinkedMultiValueMap<>();
        avvio.add("data", "{\"oggetto\":\"Bolletta errata\",\"descrizione\":\"Importo doppio\"}");
        avvio.add("files", file("reclamo.pdf", "%PDF-1.4 reclamo"));
        HttpHeaders mp = xhr();
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> avviata = come(cittadino).exchange("/api/processes/" + processo + "/start",
            HttpMethod.POST, new HttpEntity<>(avvio, mp), MAPPA);
        assertThat(avviata.getStatusCode()).as(String.valueOf(avviata.getBody())).isEqualTo(HttpStatus.OK);
        String pi = (String) avviata.getBody().get("id");

        Map<String, Object> vistaCittadino = chiama(cittadino, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody();
        assertThat((List<Map<String, Object>>) vistaCittadino.get("parti")).extracting(p -> p.get("ruolo")).containsExactly("richiedente");
        assertThat(nomiDati(vistaCittadino)).contains("oggetto", "descrizione");
        assertThat(quante(cittadino)).isEqualTo(1);

        // nessun altro esterno la vede, né per id né nell'elenco
        assertThat(stato(legaleB, HttpMethod.GET, "/api/pratiche/" + pi, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(legaleA, HttpMethod.GET, "/api/pratiche/" + pi + "/documenti", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(quante(legaleA)).isZero();

        // negazione per default: fuori dal portale gli esterni non arrivano
        for (String url : List.of("/api/pratiche/" + pi + "/diagramma", "/api/pratiche/tipologie", "/api/organizzazioni",
                "/api/admin/users", "/api/admin/organizzazioni"))
            assertThat(stato(cittadino, HttpMethod.GET, url, null)).as(url).isEqualTo(HttpStatus.FORBIDDEN);

        // l'ufficio istruisce: la controparte deve essere un'organizzazione esterna attiva
        String istruttoria = attivita(operatore, pi);
        assertThat(stato(operatore, HttpMethod.POST, "/api/tasks/" + istruttoria + "/complete",
            Map.of("controparte", "999999999"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato(operatore, HttpMethod.POST, "/api/tasks/" + istruttoria + "/claim", null)).isEqualTo(HttpStatus.OK);
        LinkedMultiValueMap<String, Object> interno = new LinkedMultiValueMap<>();
        interno.add("file", file("nota-interna.pdf", "%PDF-1.4 riservato"));
        assertThat(come(operatore).exchange("/api/pratiche/" + pi + "/documenti", HttpMethod.POST,
            new HttpEntity<>(interno, mp), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stato(operatore, HttpMethod.POST, "/api/tasks/" + istruttoria + "/complete",
            Map.of("controparte", studioA.toString(), "valutazione", "Il cliente ha ragione", "esito", "Reclamo ammesso")))
            .isEqualTo(HttpStatus.OK);

        // l'attività "parte:controparte" va agli utenti dello Studio A, a nessun altro
        String risposta = attivita(legaleA, pi);
        assertThat(attivitaDi(collegaA)).contains(risposta);
        assertThat(attivitaDi(legaleB)).doesNotContain(risposta);
        assertThat(attivitaDi(cittadino)).doesNotContain(risposta);
        assertThat(stato(legaleB, HttpMethod.GET, "/api/tasks/" + risposta, null)).isNotEqualTo(HttpStatus.OK);
        assertThat(stato(legaleB, HttpMethod.POST, "/api/tasks/" + risposta + "/complete", Map.of("risposta", "x")))
            .isNotEqualTo(HttpStatus.OK);

        // la controparte vede la pratica, ma solo i dati per le parti e niente dell'organizzazione interna
        Map<String, Object> vistaA = chiama(collegaA, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody();
        assertThat(nomiDati(vistaA)).contains("oggetto", "esito").doesNotContain("valutazione", "descrizione", "avviataDa");
        assertThat(vistaA.get("struttura")).isNull();
        List<Map<String, Object>> eventi = (List<Map<String, Object>>) vistaA.get("eventi");
        assertThat(eventi).extracting(e -> e.get("tipo")).doesNotContain("DOCUMENTO", "PRESA_IN_CARICO");
        assertThat(eventi).allMatch(e -> "Ufficio".equals(e.get("utenteNome")) && ((List<?>) e.get("dettagli")).isEmpty());
        assertThat(vistaA.toString()).doesNotContain("Il cliente ha ragione").doesNotContain(operatore);
        assertThat((List<Map<String, Object>>) vistaA.get("parti")).extracting(p -> p.get("organizzazione")).contains("Studio A " + sigla);

        // documenti: solo quelli per le parti; la nota interna non esiste per loro
        Map<String, Object> documentiA = chiama(legaleA, HttpMethod.GET, "/api/pratiche/" + pi + "/documenti", null).getBody();
        assertThat(documentiA.toString()).contains("reclamo.pdf").doesNotContain("nota-interna.pdf");
        Map<String, Object> documentiUfficio = chiama(operatore, HttpMethod.GET, "/api/pratiche/" + pi + "/documenti", null).getBody();
        Number versioneInterna = versione(documentiUfficio, "nota-interna.pdf");
        Number versionePubblica = versione(documentiUfficio, "reclamo.pdf");
        assertThat(stato(legaleA, HttpMethod.GET, "/api/documenti/versioni/" + versioneInterna + "/file", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato(legaleA, HttpMethod.GET, "/api/documenti/versioni/" + versionePubblica + "/file", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato(legaleB, HttpMethod.GET, "/api/documenti/versioni/" + versionePubblica + "/file", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // nell'attività vede i dati del suo form e quelli per le parti, non la valutazione interna
        Map<String, Object> dettaglioAttivita = chiama(legaleA, HttpMethod.GET, "/api/tasks/" + risposta, null).getBody();
        assertThat(((Map<String, Object>) dettaglioAttivita.get("variables")).keySet()).contains("oggetto").doesNotContain("valutazione", "descrizione");
        assertThat(stato(legaleA, HttpMethod.GET, "/api/tasks/" + risposta + "/diagram", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(legaleA, HttpMethod.POST, "/api/tasks/" + risposta + "/complete", Map.of("risposta", "Rimborseremo")))
            .isEqualTo(HttpStatus.OK);

        // chi ha risposto vede la sua risposta; il cittadino no (non è un dato per le parti)
        assertThat(nomiDati(chiama(legaleA, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody())).contains("risposta");
        assertThat(nomiDati(chiama(cittadino, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody())).doesNotContain("risposta", "valutazione");

        // personale: chi vede per regola non vede il campo riservato agli auditor, l'amministratore sì
        Map<String, Object> vistaLettore = chiama(lettore, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody();
        assertThat(nomiDati(vistaLettore)).contains("oggetto", "descrizione", "risposta").doesNotContain("valutazione");
        assertThat(vistaLettore.toString()).doesNotContain("Il cliente ha ragione");
        assertThat(nomiDati(chiama("admin", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody())).contains("valutazione");
    }

    @Test
    void utentiEsterniEOrganizzazioniSeguonoLeRegole() {
        Long studio = organizzazione("studio", "Studio");
        Map<String, Object> struttura = chiama("admin", HttpMethod.POST, "/api/admin/strutture",
            Map.of("codice", "uff-" + sigla, "nome", "Ufficio " + sigla, "attiva", true)).getBody();
        Long ufficio = ((Number) struttura.get("id")).longValue();

        // un esterno non ha ruoli e sta solo in organizzazioni esterne; un interno solo in strutture
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/users", nuovoUtente("x1", "ESTERNO", List.of("analista"), List.of())))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/users", nuovoUtente("x2", "ESTERNO", List.of(), List.of(ufficio))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/users", nuovoUtente("x3", "INTERNO", List.of(), List.of(studio))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        String esterno = utente("x4", "ESTERNO", List.of(), List.of(studio));
        Map<String, Object> me = chiama(esterno, HttpMethod.GET, "/api/me", null).getBody();
        assertThat(me.get("esterno")).isEqualTo(true);
        assertThat((List<?>) me.get("permessi")).isEmpty();

        // le organizzazioni le gestisce chi ha il permesso (il gestore utenti sì, un operatore no)
        assertThat(stato("mario", HttpMethod.GET, "/api/admin/organizzazioni", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("admin", HttpMethod.DELETE, "/api/admin/organizzazioni/" + studio, null)).isEqualTo(HttpStatus.BAD_REQUEST);
        // e il personale le cerca per i campi Organizzazione
        List<Map<String, Object>> trovate = come("mario").exchange("/api/organizzazioni?q=" + sigla, HttpMethod.GET,
            new HttpEntity<>(xhr()), LISTA).getBody();
        assertThat(trovate).extracting(o -> o.get("nome")).contains("Studio " + sigla).doesNotContain("Ufficio " + sigla);

        // importazione da CSV: nuove, aggiornate e righe scartate con il motivo
        String csv = "codice;nome;codiceFiscale\n"
            + "studio-" + sigla + ";Studio rinominato " + sigla + ";\n"
            + "nuova-" + sigla + ";Nuova " + sigla + ";01234567890\n"
            + "uff-" + sigla + ";Ufficio;\n"
            + "X;senza codice valido;\n";
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", file("organizzazioni.csv", csv));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        Map<String, Object> esito = come("admin").exchange("/api/admin/organizzazioni/importa", HttpMethod.POST,
            new HttpEntity<>(corpo, h), MAPPA).getBody();
        assertThat(esito.get("create")).isEqualTo(1);
        assertThat(esito.get("aggiornate")).isEqualTo(1);
        assertThat((List<?>) esito.get("scartate")).hasSize(2);
        String registro = chiama("admin", HttpMethod.GET, "/api/admin/registro?q=" + sigla, null).getBody().toString();
        assertThat(registro).contains("ORGANIZZAZIONE_CREATA");
    }

    @Test
    void unAttivitaPerUnaParteNonDichiarataNonSiPubblica() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
              <process id="sbagliato%1$s" name="Sbagliato" isExecutable="true">
                <startEvent id="inizio"/>
                <sequenceFlow id="f1" sourceRef="inizio" targetRef="t"/>
                <userTask id="t" name="Perizia" activiti:candidateGroups="parte:perito"/>
                <sequenceFlow id="f2" sourceRef="t" targetRef="fine"/>
                <endEvent id="fine"/>
              </process>
            </definitions>
            """.formatted(sigla);
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file("sbagliato" + sigla + ".bpmn20.xml", xml));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("perito");
    }

    // ------------------------------------------------------------ supporto

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static ByteArrayResource file(String nome, String contenuto) {
        return new ByteArrayResource(contenuto.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return nome; }
        };
    }

    private ResponseEntity<Map<String, Object>> chiama(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), MAPPA);
    }

    private HttpStatusCode stato(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), String.class).getStatusCode();
    }

    private Long organizzazione(String codice, String nome) {
        ResponseEntity<Map<String, Object>> r = chiama("admin", HttpMethod.POST, "/api/admin/organizzazioni",
            Map.of("codice", codice + "-" + sigla, "nome", nome + " " + sigla, "attiva", true));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private Map<String, Object> nuovoUtente(String nome, String tipo, List<String> ruoli, List<Long> organizzazioni) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", nome + "-" + sigla);
        u.put("password", "password");
        u.put("enabled", true);
        u.put("tipo", tipo);
        u.put("groups", List.of());
        u.put("ruoli", ruoli);
        u.put("organizzazioni", organizzazioni);
        return u;
    }

    private String utente(String nome, String tipo, List<String> gruppi, List<Long> organizzazioni) {
        Map<String, Object> u = nuovoUtente(nome, tipo, List.of(), organizzazioni);
        u.put("groups", gruppi);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/users", HttpMethod.POST, new HttpEntity<>(u, xhr()), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
        return nome + "-" + sigla;
    }

    private long quante(String utente) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.GET, "/api/pratiche?processo=" + processo, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("totale")).longValue();
    }

    private List<String> attivitaDi(String utente) {
        return come(utente).exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA).getBody().stream()
            .map(t -> (String) t.get("id")).toList();
    }

    /** L'attività della pratica disponibile per l'utente. */
    private String attivita(String utente, String pi) {
        return come(utente).exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA).getBody().stream()
            .filter(t -> pi.equals(t.get("processInstanceId"))).map(t -> (String) t.get("id"))
            .findFirst().orElseThrow(() -> new AssertionError(utente + " non ha attività della pratica " + pi));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> nomiDati(Map<String, Object> pratica) {
        return ((List<Map<String, Object>>) pratica.get("dati")).stream().map(d -> d.get("nome")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Number versione(Map<String, Object> documenti, String nome) {
        return ((List<Map<String, Object>>) documenti.get("documenti")).stream()
            .filter(d -> nome.equals(d.get("nome")))
            .map(d -> (Number) ((List<Map<String, Object>>) d.get("versioni")).get(0).get("id"))
            .findFirst().orElseThrow();
    }
}
