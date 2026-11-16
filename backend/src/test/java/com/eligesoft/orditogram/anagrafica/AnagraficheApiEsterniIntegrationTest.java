package com.eligesoft.orditogram.anagrafica;

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
 * Anagrafiche viste da fuori: API pubblica /api/v1/anagrafiche con chiavi limitate ai tipi indicati, campi anagrafica
 * nell'avvio di una pratica dall'API, regola "sceglie" (solo il titolo, per scegliere nei form) per il personale e per
 * gli utenti esterni. Utenti di prova: mario negli operatori (modifica i fornitori), laura nei dirigenti (li sceglie
 * soltanto), paolo analista (gestisce le integrazioni ma non vede le schede), un cittadino esterno.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnagraficheApiEsterniIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;
    private String fornitori;
    private String altra;
    private String processo;
    private Number alfa;

    @BeforeEach
    void prepara() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        fornitori = "fornitori-" + sigla;
        altra = "altra-" + sigla;
        processo = "segnalazione" + sigla;
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of(
            "codice", fornitori, "nome", "Fornitori " + sigla,
            "definizione", Map.of("fields", List.of(
                Map.of("name", "denominazione", "label", "Denominazione", "type", "text", "required", true, "titolo", true),
                Map.of("name", "piva", "label", "Partita IVA", "type", "text", "chiave", true),
                Map.of("name", "iban", "label", "IBAN", "type", "text", "ruoliVisibili", List.of("analista"))))))).isEqualTo(HttpStatus.CREATED);
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", altra, "nome", "Altra " + sigla,
            "definizione", Map.of("fields", List.of(Map.of("name", "nome", "type", "text", "titolo", true)))))).isEqualTo(HttpStatus.CREATED);
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + fornitori + "/permessi", List.of(
            Map.of("azione", "MODIFICA", "tipoSoggetto", "GRUPPO", "soggetto", "operatori"),
            Map.of("azione", "SCEGLIE", "tipoSoggetto", "GRUPPO", "soggetto", "dirigenti"),
            Map.of("azione", "SCEGLIE", "tipoSoggetto", "GRUPPO", "soggetto", "esterni")))).isEqualTo(HttpStatus.OK);
        alfa = (Number) crea("admin", Map.of("denominazione", "Alfa S.p.A.", "piva", "01234567890", "iban", "IT60X0542811101000000123456")).get("id");

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
              <process id="%1$s" name="Segnalazione %2$s" isExecutable="true" activiti:candidateStarterGroups="esterni,operatori,dirigenti">
                <startEvent id="inizio" activiti:formKey="avvio-%2$s"/>
                <sequenceFlow id="f1" sourceRef="inizio" targetRef="esame"/>
                <userTask id="esame" name="Esame" activiti:candidateGroups="operatori"/>
                <sequenceFlow id="f2" sourceRef="esame" targetRef="fine"/>
                <endEvent id="fine"/>
              </process>
            </definitions>
            """.formatted(processo, sigla);
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file(processo + ".bpmn20.xml", xml));
        corpo.add("forms", file("avvio-" + sigla + ".json", """
            { "title": "Segnalazione", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true },
              { "name": "fornitore", "label": "Fornitore", "type": "anagrafica", "tipoAnagrafica": "%s", "required": true } ] }"""
            .formatted(fornitori)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------ API pubblica

    @Test
    void leChiaviVedonoSoloLeAnagraficheIndicate() {
        // senza elenco di anagrafiche la chiave non si crea; nemmeno con un tipo che non esiste
        assertThat(chiaveStato("admin", List.of("anagrafiche:leggi"), List.of())).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(chiaveStato("admin", List.of("anagrafiche:leggi"), List.of("inesistente-" + sigla))).isEqualTo(HttpStatus.BAD_REQUEST);
        // anti-escalation: paolo gestisce le integrazioni ma non vede i fornitori, quindi non li dà a una chiave
        assertThat(chiaveStato("paolo", List.of("anagrafiche:leggi"), List.of(fornitori))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chiaveStato("paolo", List.of("anagrafiche:leggi"), List.of(altra))).isEqualTo(HttpStatus.FORBIDDEN);

        String lettura = chiave(List.of("anagrafiche:leggi"), List.of(fornitori));
        // elenco dei tipi: solo quelli della chiave
        List<Map<String, Object>> tipi = rest.exchange("/api/v1/anagrafiche", HttpMethod.GET, new HttpEntity<>(conChiave(lettura)), LISTA).getBody();
        assertThat(tipi).extracting(t -> String.valueOf(t.get("codice"))).containsExactly(fornitori);
        assertThat(String.valueOf(v1(lettura, HttpMethod.GET, "/api/v1/io", null).getBody().get("anagrafiche"))).contains(fornitori);

        // ricerca e lettura, senza i campi riservati ai ruoli
        ResponseEntity<Map<String, Object>> pagina = v1(lettura, HttpMethod.GET, "/api/v1/anagrafiche/" + fornitori + "/schede?testo=alfa", null);
        assertThat(pagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagina.getBody().get("totale")).isEqualTo(1);
        ResponseEntity<Map<String, Object>> letta = v1(lettura, HttpMethod.GET, "/api/v1/anagrafiche/" + fornitori + "/schede/" + alfa, null);
        assertThat(letta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> dati = (Map<?, ?>) letta.getBody().get("dati");
        assertThat(dati.get("denominazione")).isEqualTo("Alfa S.p.A.");
        assertThat(dati.containsKey("iban")).isFalse();
        assertThat(String.valueOf(letta.getBody())).doesNotContain("IT60X");
        // per ricerca con campo chiave
        assertThat(v1(lettura, HttpMethod.GET, "/api/v1/anagrafiche/" + fornitori + "/schede?campo=piva&valore=01234567890", null)
            .getBody().get("totale")).isEqualTo(1);

        // un tipo che la chiave non elenca non esiste
        ResponseEntity<Map<String, Object>> fuori = v1(lettura, HttpMethod.GET, "/api/v1/anagrafiche/" + altra + "/schede", null);
        assertThat(fuori.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codice(fuori)).isEqualTo("non_trovato");
        // in sola lettura non si scrive
        ResponseEntity<Map<String, Object>> vietata = v1(lettura, HttpMethod.POST, "/api/v1/anagrafiche/" + fornitori + "/schede",
            Map.of("dati", Map.of("denominazione", "Beta")));
        assertThat(vietata.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // una chiave senza permessi sulle anagrafiche non le vede
        String pratiche = chiave(List.of("pratiche:leggi"), List.of());
        assertThat(v1(pratiche, HttpMethod.GET, "/api/v1/anagrafiche/" + fornitori + "/schede", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unSistemaCreaEModificaLeSchede() {
        String scrittura = chiave(List.of("anagrafiche:scrivi"), List.of(fornitori));
        String url = "/api/v1/anagrafiche/" + fornitori + "/schede";

        // dati non validi: errore 422 con i campi
        ResponseEntity<Map<String, Object>> r422 = v1(scrittura, HttpMethod.POST, url, Map.of("dati", Map.of("piva", "999")));
        assertThat(r422.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codice(r422)).isEqualTo("dati_non_validi");
        assertThat(String.valueOf(((Map<?, ?>) r422.getBody().get("errore")).get("campi"))).contains("denominazione");
        // un campo riservato non si scrive dall'API
        assertThat(v1(scrittura, HttpMethod.POST, url, Map.of("dati", Map.of("denominazione", "Gamma", "iban", "IT00")))
            .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // chiave già usata da un'altra scheda
        assertThat(v1(scrittura, HttpMethod.POST, url, Map.of("dati", Map.of("denominazione", "Doppia", "piva", "01234567890")))
            .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map<String, Object>> creata = v1(scrittura, HttpMethod.POST, url, Map.of("dati", Map.of("denominazione", "Beta S.r.l.", "piva", "09876543210")));
        assertThat(creata.getStatusCode()).as(String.valueOf(creata.getBody())).isEqualTo(HttpStatus.CREATED);
        assertThat(creata.getHeaders().getLocation()).isNotNull();
        Object id = creata.getBody().get("id");
        Object versione = creata.getBody().get("versione");

        ResponseEntity<Map<String, Object>> modificata = v1(scrittura, HttpMethod.PUT, url + "/" + id,
            Map.of("versione", versione, "dati", Map.of("denominazione", "Beta Group S.r.l.", "piva", "09876543210"), "nota", "dal gestionale"));
        assertThat(modificata.getStatusCode()).as(String.valueOf(modificata.getBody())).isEqualTo(HttpStatus.OK);
        assertThat(modificata.getBody().get("titolo")).isEqualTo("Beta Group S.r.l.");
        // con la versione vecchia: conflitto
        ResponseEntity<Map<String, Object>> vecchia = v1(scrittura, HttpMethod.PUT, url + "/" + id,
            Map.of("versione", versione, "dati", Map.of("denominazione", "Beta Altro")));
        assertThat(vecchia.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codice(vecchia)).isEqualTo("conflitto");

        // la modifica dall'API non tocca i campi riservati già presenti
        Map<String, Object> a = v1(scrittura, HttpMethod.GET, url + "/" + alfa, null).getBody();
        assertThat(v1(scrittura, HttpMethod.PUT, url + "/" + alfa, Map.of("versione", a.get("versione"),
            "dati", Map.of("denominazione", "Alfa S.p.A.", "piva", "01234567890"))).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> vistaAdmin = chiama("admin", HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/schede/" + alfa, null).getBody();
        assertThat(String.valueOf(vistaAdmin.get("dati"))).contains("IT60X");
    }

    @Test
    void unSistemaAvviaUnaPraticaConUnaSchedaDelleSueAnagrafiche() {
        String url = "/api/v1/processi/" + processo + "/pratiche";
        Map<String, Object> dati = Map.of("oggetto", "Ritardo nella consegna", "fornitore", alfa.toString());

        // la chiave non ha i fornitori: la scheda, per lei, non esiste
        String senza = chiave(List.of("pratiche:avvia"), List.of());
        ResponseEntity<Map<String, Object>> rifiutata = v1(senza, HttpMethod.POST, url, Map.of("dati", dati));
        assertThat(rifiutata.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(String.valueOf(((Map<?, ?>) rifiutata.getBody().get("errore")).get("campi"))).contains("fornitore");

        String portale = chiave(List.of("pratiche:avvia", "pratiche:leggi", "anagrafiche:leggi"), List.of(fornitori));
        ResponseEntity<Map<String, Object>> creata = v1(portale, HttpMethod.POST, url, Map.of("dati", dati));
        assertThat(creata.getStatusCode()).as(String.valueOf(creata.getBody())).isEqualTo(HttpStatus.CREATED);
        String pi = String.valueOf(creata.getBody().get("id"));
        // la pratica ha la fotografia della scheda, vista dall'ufficio
        List<?> collegate = (List<?>) chiama("mario", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody().get("anagrafiche");
        assertThat(collegate).hasSize(1);
        assertThat(((Map<?, ?>) collegate.get(0)).get("titolo")).isEqualTo("Alfa S.p.A.");
        // il sistema non riceve la fotografia tra i dati della pratica
        assertThat(String.valueOf(v1(portale, HttpMethod.GET, "/api/v1/pratiche/" + pi, null).getBody())).doesNotContain("fornitore_dati");
    }

    @Test
    void openApiDescriveLeAnagrafiche() {
        String chiave = chiave(List.of("anagrafiche:leggi", "pratiche:avvia"), List.of(fornitori));
        Map<String, Object> openapi = v1(chiave, HttpMethod.GET, "/api/v1/openapi.json", null).getBody();
        Map<?, ?> paths = (Map<?, ?>) openapi.get("paths");
        assertThat(paths.keySet()).extracting(String::valueOf)
            .contains("/anagrafiche", "/anagrafiche/{tipo}/schede", "/anagrafiche/{tipo}/schede/{id}");
        assertThat(String.valueOf(openapi)).contains(fornitori);
    }

    // ------------------------------------------------------------ regola "sceglie"

    @Test
    void chiSceglieVedeSoloIlTitolo() {
        // laura sceglie i fornitori: trova le schede per nome, ma non le apre e non vede l'anagrafica
        List<Map<String, Object>> trovate = lista("laura", "/api/anagrafiche/" + fornitori + "/scelta?testo=alf");
        assertThat(trovate).hasSize(1);
        assertThat(trovate.get(0).keySet()).containsExactlyInAnyOrder("id", "titolo");
        assertThat(trovate.get(0).get("titolo")).isEqualTo("Alfa S.p.A.");
        assertThat(stato("laura", HttpMethod.GET, "/api/anagrafiche/" + fornitori, null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato("laura", HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/schede", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato("laura", HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/schede/" + alfa, null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(lista("laura", "/api/anagrafiche")).extracting(t -> String.valueOf(t.get("codice"))).doesNotContain(fornitori);
        // ma può avviare una pratica che la usa
        assertThat(chiama("laura", HttpMethod.POST, "/api/processes/" + processo + "/start",
            Map.of("oggetto", "Fattura doppia", "fornitore", alfa.toString())).getStatusCode()).isEqualTo(HttpStatus.OK);

        // chi non ha nessuna regola non trova nulla
        assertThat(stato("paolo", HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/scelta", null)).isEqualTo(HttpStatus.NOT_FOUND);
        // chi vede le schede le sceglie anche
        assertThat(lista("mario", "/api/anagrafiche/" + fornitori + "/scelta")).hasSize(1);
        // le schede cessate non si scelgono
        Map<String, Object> s = chiama("mario", HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/schede/" + alfa, null).getBody();
        assertThat(stato("mario", HttpMethod.POST, "/api/anagrafiche/" + fornitori + "/schede/" + alfa + "/cessa",
            Map.of("versione", s.get("versione")))).isEqualTo(HttpStatus.OK);
        assertThat(lista("laura", "/api/anagrafiche/" + fornitori + "/scelta")).isEmpty();
    }

    @Test
    void unUtenteEsternoSceglieUnaSchedaNelForm() {
        String cittadino = esterno("cittadino");
        List<Map<String, Object>> trovate = lista(cittadino, "/api/anagrafiche/" + fornitori + "/scelta?testo=alfa");
        assertThat(trovate).extracting(t -> String.valueOf(t.get("titolo"))).containsExactly("Alfa S.p.A.");
        // il resto delle anagrafiche non è per gli esterni
        assertThat(stato(cittadino, HttpMethod.GET, "/api/anagrafiche", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(cittadino, HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/schede/" + alfa, null)).isEqualTo(HttpStatus.FORBIDDEN);
        // un tipo senza la regola per gli esterni non si sceglie
        assertThat(stato(cittadino, HttpMethod.GET, "/api/anagrafiche/" + altra + "/scelta", null)).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> avviata = chiama(cittadino, HttpMethod.POST, "/api/processes/" + processo + "/start",
            Map.of("oggetto", "Consegna in ritardo", "fornitore", alfa.toString()));
        assertThat(avviata.getStatusCode()).as(String.valueOf(avviata.getBody())).isEqualTo(HttpStatus.OK);
        String pi = String.valueOf(avviata.getBody().get("id"));
        // l'ufficio vede il collegamento; l'esterno non riceve la fotografia della scheda
        assertThat((List<?>) chiama("mario", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody().get("anagrafiche")).hasSize(1);
        assertThat(String.valueOf(chiama(cittadino, HttpMethod.GET, "/api/pratiche/" + pi, null).getBody())).doesNotContain("01234567890");
    }

    @Test
    void laRegolaSceglieSiSalvaEDisattivaLeAltre() {
        // "sceglie" è un'azione valida delle regole; per gli esterni contano solo quelle
        List<Map<String, Object>> regole = lista("paolo", "/api/admin/anagrafiche/" + fornitori + "/permessi");
        assertThat(regole).extracting(r -> String.valueOf(r.get("azione"))).contains("SCEGLIE");
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + fornitori + "/permessi",
            List.of(Map.of("azione", "VEDE", "tipoSoggetto", "GRUPPO", "soggetto", "esterni")))).isEqualTo(HttpStatus.OK);
        String cittadino = esterno("cittadino2");
        assertThat(stato(cittadino, HttpMethod.GET, "/api/anagrafiche/" + fornitori + "/scelta", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + fornitori + "/permessi",
            List.of(Map.of("azione", "TUTTO", "tipoSoggetto", "GRUPPO", "soggetto", "esterni")))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------ supporto

    private Map<String, Object> crea(String utente, Map<String, Object> dati) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.POST, "/api/anagrafiche/" + fornitori + "/schede", Map.of("dati", dati));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private HttpStatusCode chiaveStato(String utente, List<String> permessi, List<String> anagrafiche) {
        return come(utente).exchange("/api/admin/integrazioni/chiavi", HttpMethod.POST,
            new HttpEntity<>(datiChiave(permessi, anagrafiche), xhr()), String.class).getStatusCode();
    }

    private String chiave(List<String> permessi, List<String> anagrafiche) {
        ResponseEntity<Map<String, Object>> r = come("admin").exchange("/api/admin/integrazioni/chiavi", HttpMethod.POST,
            new HttpEntity<>(datiChiave(permessi, anagrafiche), xhr()), MAPPA);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return String.valueOf(r.getBody().get("valore"));
    }

    private Map<String, Object> datiChiave(List<String> permessi, List<String> anagrafiche) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "chiave-" + UUID.randomUUID().toString().substring(0, 8));
        d.put("permessi", permessi);
        d.put("processi", List.of());
        d.put("anagrafiche", anagrafiche);
        return d;
    }

    private ResponseEntity<Map<String, Object>> v1(String chiave, HttpMethod metodo, String url, Object corpo) {
        return rest.exchange(url, metodo, new HttpEntity<>(corpo, conChiave(chiave)), MAPPA);
    }

    private static HttpHeaders conChiave(String chiave) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", chiave);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static String codice(ResponseEntity<Map<String, Object>> r) {
        return String.valueOf(((Map<?, ?>) r.getBody().get("errore")).get("codice"));
    }

    private String esterno(String nome) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", nome + "-" + sigla);
        u.put("password", "password");
        u.put("enabled", true);
        u.put("tipo", "ESTERNO");
        u.put("groups", List.of());
        u.put("ruoli", List.of());
        u.put("organizzazioni", List.of());
        ResponseEntity<String> r = come("admin").exchange("/api/admin/users", HttpMethod.POST, new HttpEntity<>(u, xhr()), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
        return nome + "-" + sigla;
    }

    private static ByteArrayResource file(String nome, String contenuto) {
        return new ByteArrayResource(contenuto.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return nome; }
        };
    }

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private ResponseEntity<Map<String, Object>> chiama(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), MAPPA);
    }

    private List<Map<String, Object>> lista(String utente, String url) {
        ResponseEntity<List<Map<String, Object>>> r = come(utente).exchange(url, HttpMethod.GET, new HttpEntity<>(xhr()), LISTA);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private HttpStatusCode stato(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), String.class).getStatusCode();
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
