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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permessi per processo e strutture, con chiamate HTTP vere: chi avvia, chi vede tutte le pratiche entro il suo
 * ambito (tutte, propria struttura, struttura e sotto), chi esporta. Ogni test pubblica un processo suo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermessiProcessoIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;
    private String processo;
    private String sportello;

    @BeforeEach
    void pubblicaProcesso() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        processo = "segnalazione" + sigla;
        sportello = "sportello-" + sigla;
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
              <process id="%s" name="Segnalazione %s" isExecutable="true" activiti:candidateStarterGroups="%s">
                <startEvent id="inizio" activiti:formKey="pp-avvio"/>
                <sequenceFlow id="f1" sourceRef="inizio" targetRef="esame"/>
                <userTask id="esame" name="Esamina" activiti:candidateGroups="%s"/>
                <sequenceFlow id="f2" sourceRef="esame" targetRef="fine"/>
                <endEvent id="fine"/>
              </process>
            </definitions>
            """.formatted(processo, sigla, sportello, sportello);
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file(processo + ".bpmn20.xml", xml));
        corpo.add("forms", file("pp-avvio.json", """
            { "title": "Segnalazione", "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true } ] }"""));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
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

    private Long struttura(String nome, Long padre) {
        Map<String, Object> s = new HashMap<>();
        s.put("codice", nome + "-" + sigla);
        s.put("nome", nome + " " + sigla);
        s.put("padreId", padre);
        s.put("attiva", true);
        ResponseEntity<Map<String, Object>> r = chiama("admin", HttpMethod.POST, "/api/admin/strutture", s);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private String utente(String nome, List<String> gruppi, List<Long> strutture) {
        String username = nome + "-" + sigla;
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("password", "password");
        u.put("enabled", true);
        u.put("groups", gruppi);
        u.put("ruoli", List.of());
        u.put("organizzazioni", strutture);
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/users", u)).isEqualTo(HttpStatus.OK);
        return username;
    }

    private void regole(Object... terne) {
        List<Map<String, String>> lista = new ArrayList<>();
        for (int i = 0; i < terne.length; i += 4)
            lista.add(Map.of("azione", (String) terne[i], "tipoSoggetto", (String) terne[i + 1],
                "soggetto", (String) terne[i + 2], "ambito", (String) terne[i + 3]));
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/processes/" + processo + "/permessi", lista)).isEqualTo(HttpStatus.OK);
    }

    private String avvia(String utente, String oggetto) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.POST, "/api/processes/" + processo + "/start",
            Map.of("oggetto", oggetto));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return (String) r.getBody().get("id");
    }

    private long quante(String utente, String extra) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.GET, "/api/pratiche?processo=" + processo + extra, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("totale")).longValue();
    }

    // ------------------------------------------------------------ test

    @Test
    void ogniUtenteVedeLePraticheDelSuoAmbito() {
        Long regione = struttura("regione", null);
        Long ufficioA = struttura("ufficio-a", regione);
        Long ufficioB = struttura("ufficio-b", regione);
        String richA = utente("rich-a", List.of(sportello), List.of(ufficioA));
        String richB = utente("rich-b", List.of(sportello), List.of(ufficioB));
        String sup = utente("sup", List.of("supervisori-" + sigla), List.of());
        String capoA = utente("capo-a", List.of("capi-" + sigla), List.of(ufficioA));
        String regionale = utente("regionale", List.of("regionali-" + sigla), List.of(regione));
        String estraneo = utente("estraneo", List.of(), List.of(ufficioA));

        String praticaA = avvia(richA, "Buca in via Roma");
        String praticaB = avvia(richB, "Lampione spento");
        assertThat(chiama(sup, HttpMethod.GET, "/api/pratiche/" + praticaA, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        regole("VEDE", "GRUPPO", "supervisori-" + sigla, "TUTTE",
               "VEDE", "GRUPPO", "capi-" + sigla, "STRUTTURA",
               "VEDE", "GRUPPO", "regionali-" + sigla, "STRUTTURA_E_SOTTO");

        // la struttura competente è quella di chi ha avviato
        Map<String, Object> dettaglio = chiama(sup, HttpMethod.GET, "/api/pratiche/" + praticaA, null).getBody();
        assertThat(((Map<String, Object>) dettaglio.get("struttura")).get("id")).isEqualTo(ufficioA.intValue());

        assertThat(quante(sup, "")).isEqualTo(2);
        assertThat(quante(capoA, "")).isEqualTo(1);
        assertThat(quante(regionale, "")).isEqualTo(2);
        assertThat(quante(estraneo, "")).isZero();
        assertThat(quante(richA, "")).isEqualTo(2);   // vede anche la pratica di B: ha un'attività del suo gruppo

        assertThat(stato(capoA, HttpMethod.GET, "/api/pratiche/" + praticaA, null)).isEqualTo(HttpStatus.OK);
        assertThat(stato(capoA, HttpMethod.GET, "/api/pratiche/" + praticaB, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(capoA, HttpMethod.GET, "/api/pratiche/" + praticaB + "/diagramma", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(regionale, HttpMethod.GET, "/api/pratiche/" + praticaB, null)).isEqualTo(HttpStatus.OK);
        assertThat(stato(estraneo, HttpMethod.GET, "/api/pratiche/" + praticaA, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(estraneo, HttpMethod.GET, "/api/pratiche/" + praticaA + "/documenti", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // tolte le regole, chi non partecipa non vede più nulla
        regole();
        assertThat(quante(sup, "")).isZero();
        assertThat(stato(regionale, HttpMethod.GET, "/api/pratiche/" + praticaB, null)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void leRegoleDiAvvioSostituisconoQuelleDelBpmn() {
        String allo = utente("allo", List.of(sportello), List.of());
        String altro = utente("altro", List.of("altri-" + sigla), List.of());
        assertThat(stato(altro, HttpMethod.POST, "/api/processes/" + processo + "/start", Map.of("oggetto", "x")))
            .isEqualTo(HttpStatus.FORBIDDEN);

        regole("AVVIA", "GRUPPO", "altri-" + sigla, "TUTTE");
        String pratica = avvia(altro, "Dalle regole dello Studio");
        assertThat(stato(allo, HttpMethod.POST, "/api/processes/" + processo + "/start", Map.of("oggetto", "x")))
            .isEqualTo(HttpStatus.FORBIDDEN);
        // chi avvia resta l'iniziatore e vede la sua pratica
        assertThat(stato(altro, HttpMethod.GET, "/api/pratiche/" + pratica, null)).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> avviabili = come(altro).exchange("/api/processes", HttpMethod.GET, new HttpEntity<>(xhr()),
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
        assertThat(avviabili).anyMatch(p -> processo.equals(p.get("key")));

        // per ruolo: l'analista (paolo) può avviare
        regole("AVVIA", "RUOLO", "analista", "TUTTE");
        avvia("paolo", "Dal ruolo");
        assertThat(stato(altro, HttpMethod.POST, "/api/processes/" + processo + "/start", Map.of("oggetto", "x")))
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void lEsportazioneSegueLeRegoleEFiniscenelRegistro() {
        String rich = utente("rich", List.of(sportello), List.of());
        avvia(rich, "Da esportare");
        regole("ESPORTA", "GRUPPO", "esportatori-" + sigla, "TUTTE");
        String esportatore = utente("esp", List.of(sportello, "esportatori-" + sigla), List.of());

        assertThat(quante(rich, "")).isEqualTo(1);
        assertThat(quante(rich, "&esporta=true&dimensione=1000")).isZero();
        assertThat(quante(esportatore, "&esporta=true&dimensione=1000")).isEqualTo(1);
        // senza esportazione le pagine restano piccole
        Map<String, Object> pagina = chiama(rich, HttpMethod.GET, "/api/pratiche?processo=" + processo + "&dimensione=1000", null).getBody();
        assertThat(pagina.get("dimensione")).isEqualTo(100);

        String registro = chiama("admin", HttpMethod.GET, "/api/admin/registro?q=" + processo, null).getBody().toString();
        assertThat(registro).contains("PRATICHE_ESPORTATE").contains("PERMESSI_PROCESSO_MODIFICATI");
    }

    @Test
    void iPermessiViaggianoNelPacchetto() throws Exception {
        regole("VEDE", "GRUPPO", "supervisori-" + sigla, "TUTTE", "AVVIA", "RUOLO", "analista", "TUTTE");
        byte[] zip = come("admin").exchange("/api/admin/pacchetti/" + processo + "?fonte=produzione", HttpMethod.GET,
            new HttpEntity<>(xhr()), byte[].class).getBody();
        String permessi = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; )
                if ("permessi.json".equals(e.getName())) permessi = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(permessi).contains("supervisori-" + sigla).contains("analista");
    }

    @Test
    void regoleEStruttureNonValideSonoRifiutate() {
        String url = "/api/admin/processes/" + processo + "/permessi";
        assertThat(stato("admin", HttpMethod.PUT, url, List.of(Map.of("azione", "CANCELLA", "tipoSoggetto", "GRUPPO", "soggetto", "x"))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.PUT, url, List.of(Map.of("azione", "AVVIA", "tipoSoggetto", "GRUPPO", "soggetto", "x", "ambito", "STRUTTURA"))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.PUT, url, List.of(Map.of("azione", "VEDE", "tipoSoggetto", "RUOLO", "soggetto", "inesistente"))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        // solo chi gestisce i processi
        assertThat(stato("mario", HttpMethod.GET, url, null)).isEqualTo(HttpStatus.FORBIDDEN);

        Long sopra = struttura("sopra", null);
        Long sotto = struttura("sotto", sopra);
        Map<String, Object> giro = Map.of("nome", "sopra", "padreId", sotto, "attiva", true);
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/strutture/" + sopra, giro)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.DELETE, "/api/admin/strutture/" + sopra, null)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/strutture", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // chi gestisce gli utenti non si mette da solo in gruppi o strutture
        Map<String, Object> g = new HashMap<>();
        g.put("username", "gu-" + sigla); g.put("password", "password"); g.put("enabled", true);
        g.put("ruoli", List.of("gestore-utenti")); g.put("groups", List.of()); g.put("organizzazioni", List.of());
        Map<String, Object> gestore = chiama("admin", HttpMethod.POST, "/api/admin/users", g).getBody();
        Map<String, Object> allargato = new HashMap<>(gestore);
        allargato.put("organizzazioni", List.of(sopra));
        assertThat(stato("gu-" + sigla, HttpMethod.PUT, "/api/admin/users/" + gestore.get("id"), allargato)).isEqualTo(HttpStatus.FORBIDDEN);
        allargato.put("organizzazioni", List.of());
        allargato.put("groups", List.of(sportello));
        assertThat(stato("gu-" + sigla, HttpMethod.PUT, "/api/admin/users/" + gestore.get("id"), allargato)).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
