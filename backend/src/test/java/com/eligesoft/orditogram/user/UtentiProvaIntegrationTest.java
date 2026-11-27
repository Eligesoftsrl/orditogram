package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.glossario.TermineGlossarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Una soluzione porta nel pacchetto le sue parole (glossario.json) e gli attori della demo (utenti-prova.json):
 * l'importazione dell'amministratore crea gli utenti e la pagina di accesso, su localhost, li offre con un clic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UtentiProvaIntegrationTest {

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
          <process id="bandoProva" name="Bando di prova" isExecutable="true" activiti:candidateStarterGroups="esterni">
            <startEvent id="inizio"/>
            <sequenceFlow id="f1" sourceRef="inizio" targetRef="esame"/>
            <userTask id="esame" name="Esamina la domanda" activiti:candidateGroups="istruttori-prova"/>
            <sequenceFlow id="f2" sourceRef="esame" targetRef="fine"/>
            <endEvent id="fine"/>
          </process>
        </definitions>
        """;

    private static final String ATTORI = """
        [ { "username": "impresa.prova", "nome": "Ada", "cognome": "Rossi", "ruolo": "Impresa richiedente", "esterno": true },
          { "username": "istruttore.prova", "nome": "Bruno", "cognome": "Galli", "ruolo": "Istruttore", "gruppi": ["istruttori-prova"] },
          { "username": "mario", "nome": "Altro", "cognome": "Nome", "ruolo": "Operatore della sede", "gruppi": ["admin-finto"] } ]""";

    @Autowired TestRestTemplate rest;
    @Autowired AppUserRepository utenti;
    @Autowired UtenteProvaRepository attori;
    @Autowired TermineGlossarioRepository glossario;

    @AfterEach
    void pulisci() {
        attori.deleteAll();
        glossario.deleteAll();
        utenti.findByUsername("impresa.prova").ifPresent(utenti::delete);
        utenti.findByUsername("istruttore.prova").ifPresent(utenti::delete);
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    private static byte[] pacchetto(String attori) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (var f : Map.of("processo.bpmn20.xml", BPMN, "utenti-prova.json", attori,
                    "glossario.json", "{\"pratica\": {\"singolare\": \"domanda\", \"plurale\": \"domande\", \"genere\": \"F\"}}").entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> importa(String utente, String password, String attori) throws Exception {
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", new ByteArrayResource(pacchetto(attori)) { @Override public String getFilename() { return "bando.orditogram.zip"; } });
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.withBasicAuth(utente, password).postForEntity("/api/admin/pacchetti", new HttpEntity<>(corpo, h), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gliAttoriDelPacchettoDiventanoAccessiRapidi() throws Exception {
        var esito = importa("admin", "admin", ATTORI);
        assertEquals(HttpStatus.OK, esito.getStatusCode(), () -> String.valueOf(esito.getBody()));
        assertTrue(String.valueOf(esito.getBody().get("utentiProva")).contains("impresa.prova, istruttore.prova"));
        assertTrue(String.valueOf(esito.getBody().get("glossario")).contains("pratica = domanda"));

        AppUser impresa = utenti.findByUsername("impresa.prova").orElseThrow();
        assertTrue(impresa.isEsterno());
        assertEquals(List.of("istruttori-prova"), List.copyOf(utenti.findByUsername("istruttore.prova").orElseThrow().getGroups()));
        assertFalse(utenti.findByUsername("mario").orElseThrow().getGroups().contains("admin-finto"),
            "un utente che esiste già resta com'è");

        // la pagina di accesso (il test chiama da localhost): gli attori nell'ordine del pacchetto, poi l'amministratore
        Map<String, Object> metodi = rest.getForObject("/api/auth/metodi", Map.class);
        assertEquals(true, metodi.get("utentiProva"));
        List<Map<String, String>> rapidi = (List<Map<String, String>>) metodi.get("accessiRapidi");
        assertEquals(List.of("impresa.prova", "istruttore.prova", "mario", "admin"), rapidi.stream().map(r -> r.get("username")).toList());
        assertEquals("Impresa richiedente", rapidi.get(0).get("ruolo"));
        assertEquals("Ada Rossi", rapidi.get(0).get("nome"));

        // gli attori si esportano con la soluzione
        byte[] zip = rest.withBasicAuth("admin", "admin").exchange("/api/admin/pacchetti/bandoProva", HttpMethod.GET,
            new HttpEntity<>(xhr()), byte[].class).getBody();
        assertNotNull(zip);
    }

    @Test
    @SuppressWarnings("unchecked")
    void soloLAmministratoreCreaUtentiEGruppiRiservatiRifiutati() throws Exception {
        var analista = importa("paolo", "password", ATTORI);
        if (analista.getStatusCode() == HttpStatus.OK)
            assertTrue(String.valueOf(analista.getBody().get("utentiProva")).startsWith("non creati"));
        assertTrue(utenti.findByUsername("impresa.prova").isEmpty());

        var riservato = importa("admin", "admin",
            "[ { \"username\": \"furbo\", \"ruolo\": \"Finto esterno\", \"gruppi\": [\"esterni\"] } ]");
        assertEquals(HttpStatus.BAD_REQUEST, riservato.getStatusCode());
        assertTrue(utenti.findByUsername("furbo").isEmpty());
    }

    @Test
    void accessiRapidiSoloSuLocalhostEFuoriDallaProduzione() {
        UtentiProvaService s = new UtentiProvaService(utenti, attori, null,
            new org.springframework.mock.env.MockEnvironment(), true, true);
        assertTrue(s.accessiRapidi("localhost"));
        assertTrue(s.accessiRapidi("127.0.0.1"));
        assertFalse(s.accessiRapidi("orditogram.inail.it"));
        assertFalse(s.accessiRapidi(null));

        var produzione = new org.springframework.mock.env.MockEnvironment();
        produzione.setActiveProfiles("produzione");
        assertFalse(new UtentiProvaService(utenti, attori, null, produzione, true, true).accessiRapidi("localhost"));
        assertFalse(new UtentiProvaService(utenti, attori, null, new org.springframework.mock.env.MockEnvironment(), false, true)
            .accessiRapidi("localhost"));
    }
}
