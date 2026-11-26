package com.eligesoft.orditogram.glossario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Glossario dell'installazione: predefiniti, modifica dell'amministratore, lettura pubblica da /api/info,
 * parole non valide rifiutate, ritorno al predefinito. Alla fine rimette i predefiniti per gli altri test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GlossarioIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired TermineGlossarioRepository repo;

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<List<Map<String, Object>>> salva(String utente, Object corpo) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password")
            .exchange("/api/admin/glossario", HttpMethod.PUT, new HttpEntity<>(corpo, xhr()), LISTA);
    }

    /** Solo lo stato: le risposte d'errore non sono una lista. */
    private HttpStatusCode stato(String utente, Object corpo) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password")
            .exchange("/api/admin/glossario", HttpMethod.PUT, new HttpEntity<>(corpo, xhr()), String.class).getStatusCode();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> glossarioPubblico() {
        Map<String, Object> info = rest.exchange("/api/info", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody();
        return (Map<String, Map<String, String>>) info.get("glossario");
    }

    @AfterEach
    void predefiniti() {
        repo.deleteAll();
    }

    @Test
    void predefinitiLeggibiliDaTutti() {
        Map<String, Map<String, String>> g = glossarioPubblico();
        assertThat(g).containsKeys("ente", "ufficio", "esterno", "pratica");
        assertThat(g.get("pratica")).containsEntry("singolare", "pratica").containsEntry("genere", "F");
        assertThat(g.get("ente")).containsEntry("singolare", "ente").containsEntry("genere", "M");
        assertThat(g.get("ufficio")).containsEntry("singolare", "Ufficio");
        assertThat(g.get("esterno")).containsEntry("plurale", "cittadini");
    }

    @Test
    void amministratoreCambiaLeParoleETornaAlPredefinito() {
        ResponseEntity<List<Map<String, Object>>> r = salva("admin", Map.of(
            "ente", Map.of("singolare", "azienda", "plurale", "aziende", "genere", "F"),
            "esterno", Map.of("singolare", "cliente", "plurale", "clienti", "genere", "M")));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).filteredOn(t -> "ente".equals(t.get("chiave")))
            .singleElement().satisfies(t -> {
                assertThat(t).containsEntry("singolare", "azienda").containsEntry("predefinito", false);
                assertThat(t).containsEntry("singolarePredefinito", "ente");
            });

        Map<String, Map<String, String>> g = glossarioPubblico();
        assertThat(g.get("ente")).containsEntry("singolare", "azienda").containsEntry("genere", "F");
        assertThat(g.get("esterno")).containsEntry("singolare", "cliente");
        assertThat(g.get("ufficio")).containsEntry("singolare", "Ufficio");

        // parole vuote: il termine torna predefinito
        assertThat(stato("admin", Map.of("ente", Map.of("singolare", "", "plurale", "")))).isEqualTo(HttpStatus.OK);
        assertThat(glossarioPubblico().get("ente")).containsEntry("singolare", "ente");
        assertThat(repo.existsById("ente")).isFalse();
    }

    @Test
    void paroleNonValideOTerminiSconosciutiRifiutati() {
        assertThat(stato("admin", Map.of("ente", Map.of("singolare", "<b>ente</b>", "plurale", "enti", "genere", "M")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", Map.of("ente", Map.of("singolare", "ditta", "plurale", "ditte", "genere", "X")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", Map.of("fattura", Map.of("singolare", "fattura", "plurale", "fatture", "genere", "F")))).isEqualTo(HttpStatus.BAD_REQUEST);
        // tutto o niente: con un termine sbagliato non si salva neanche quello giusto
        assertThat(stato("admin", Map.of(
            "esterno", Map.of("singolare", "socio", "plurale", "soci", "genere", "M"),
            "ufficio", Map.of("singolare", "", "plurale", "x", "genere", "M")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(glossarioPubblico().get("esterno")).containsEntry("singolare", "cittadino");
    }

    @Test
    void iMessaggiDelServerUsanoLaParolaScelta() {
        String url = "/api/pratiche/non-esiste";
        String prima = rest.withBasicAuth("admin", "admin").exchange(url, HttpMethod.GET, new HttpEntity<>(xhr()), String.class).getBody();
        assertThat(prima).contains("Pratica non trovata");

        assertThat(salva("admin", Map.of("pratica", Map.of("singolare", "ticket", "plurale", "ticket", "genere", "M")))
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> dopo = rest.withBasicAuth("admin", "admin").exchange(url, HttpMethod.GET, new HttpEntity<>(xhr()), String.class);
        assertThat(dopo.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(dopo.getBody()).contains("Ticket non trovato");
    }

    @Test
    void soloLAmministratoreCambiaIlGlossario() {
        assertThat(stato("mario", Map.of("ente", Map.of("singolare", "azienda", "plurale", "aziende", "genere", "F")))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(glossarioPubblico().get("ente")).containsEntry("singolare", "ente");
    }
}
