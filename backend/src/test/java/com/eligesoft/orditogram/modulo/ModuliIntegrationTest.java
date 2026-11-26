package com.eligesoft.orditogram.modulo;

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
 * Moduli di paese e di settore: tutti accesi di base, spenti dall'amministratore con effetto immediato su
 * calendario, formati dei form, protocollo di prova. Alla fine li riaccende per gli altri test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModuliIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired ModuloSpentoRepository spenti;

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> chiama(String utente, HttpMethod metodo, String url, Object corpo) {
        TestRestTemplate r = utente == null ? rest : rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
        return r.exchange(url, metodo, new HttpEntity<>(corpo, xhr()), String.class);
    }

    private HttpStatusCode imposta(String utente, String codice, boolean acceso) {
        return chiama(utente, HttpMethod.PUT, "/api/admin/moduli/" + codice, Map.of("acceso", acceso)).getStatusCode();
    }

    @SuppressWarnings("unchecked")
    private List<String> accesi() {
        return (List<String>) rest.exchange("/api/info", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody().get("moduli");
    }

    @AfterEach
    void tuttiAccesi() {
        spenti.deleteAll();
    }

    @Test
    void diBaseSonoTuttiAccesi() {
        assertThat(accesi()).containsExactly("italia", "firma-digitale", "pa");
        assertThat(chiama("admin", HttpMethod.GET, "/api/admin/moduli", null).getBody())
            .contains("\"codice\":\"italia\"").contains("\"acceso\":true");
    }

    @Test
    void conItaliaSpentaNienteFestivitaNazionaliNeFormatiItaliani() {
        String calendario = chiama("admin", HttpMethod.GET, "/api/calendario?anno=2026", null).getBody();
        assertThat(calendario).contains("Pasqua");
        String form = "{ \"fields\": [ { \"name\": \"cf\", \"type\": \"text\", \"formato\": \"codiceFiscale\" } ] }";
        String chiave = "moduli-cf-" + UUID.randomUUID().toString().substring(0, 6);
        assertThat(chiama("admin", HttpMethod.PUT, "/api/admin/forms/" + chiave, form).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(imposta("admin", "italia", false)).isEqualTo(HttpStatus.OK);
        assertThat(accesi()).doesNotContain("italia");
        assertThat(chiama("admin", HttpMethod.GET, "/api/calendario?anno=2026", null).getBody()).doesNotContain("Pasqua");
        ResponseEntity<String> rifiutato = chiama("admin", HttpMethod.PUT, "/api/admin/forms/" + chiave + "-2", form);
        assertThat(rifiutato.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rifiutato.getBody()).contains("modulo Italia");
        // gli altri formati restano
        String email = "{ \"fields\": [ { \"name\": \"e\", \"type\": \"text\", \"formato\": \"email\" } ] }";
        assertThat(chiama("admin", HttpMethod.PUT, "/api/admin/forms/" + chiave + "-3", email).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(imposta("admin", "italia", true)).isEqualTo(HttpStatus.OK);
        assertThat(chiama("admin", HttpMethod.GET, "/api/calendario?anno=2026", null).getBody()).contains("Pasqua");
    }

    private HttpStatusCode protocolla() {
        HttpHeaders h = xhr();
        h.set("X-API-Key", "chiave-di-prova");   // credenziale protocollo-demo di application.yml
        return rest.exchange("/api/demo/protocollo", HttpMethod.POST, new HttpEntity<>(Map.of("oggetto", "prova"), h), String.class)
            .getStatusCode();
    }

    @Test
    void ilProtocolloDiProvaFaParteDelPacchettoPa() {
        assertThat(protocolla()).isEqualTo(HttpStatus.OK);
        assertThat(imposta("admin", "pa", false)).isEqualTo(HttpStatus.OK);
        assertThat(protocolla()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void soloLAmministratoreAccendeESpegne() {
        assertThat(imposta("mario", "italia", false)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(imposta("admin", "sconosciuto", false)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chiama("admin", HttpMethod.PUT, "/api/admin/moduli/italia", Map.of("acceso", "forse")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(accesi()).contains("italia");
    }
}
