package com.eligesoft.orditogram.accesso;

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
 * Ruoli e permessi applicativi con chiamate HTTP vere: sezioni dello Studio per permesso, ruoli dai gruppi,
 * nessun aumento dei privilegi, almeno un amministratore. Ogni test usa utenti e ruoli propri.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RuoliPermessiIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

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

    private HttpStatusCode stato(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), String.class).getStatusCode();
    }

    private static String nuovoNome(String prefisso) {
        return prefisso + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> utente(String username, Collection<String> ruoli, Collection<String> gruppi) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("password", "password");
        u.put("enabled", true);
        u.put("ruoli", ruoli);
        u.put("groups", gruppi);
        return u;
    }

    private Map<String, Object> creaUtente(String chi, String username, Collection<String> ruoli, Collection<String> gruppi) {
        ResponseEntity<Map<String, Object>> r = come(chi).exchange("/api/admin/users", HttpMethod.POST,
            new HttpEntity<>(utente(username, ruoli, gruppi), xhr()), MAPPA);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> creaRuolo(String codice, Collection<String> permessi, Collection<String> gruppi) {
        ResponseEntity<Map<String, Object>> r = come("admin").exchange("/api/admin/ruoli", HttpMethod.POST,
            new HttpEntity<>(Map.of("codice", codice, "nome", "Ruolo " + codice, "permessi", permessi, "gruppi", gruppi), xhr()), MAPPA);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> ruolo(String codice) {
        return come("admin").exchange("/api/admin/ruoli", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA).getBody()
            .stream().filter(r -> codice.equals(r.get("codice"))).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ test

    @Test
    void ognunoVedeSoloLeSezioniDeiSuoiPermessi() {
        // paolo, nei dati di prova, è analista: processi, form e integrazioni sì, utenti e registro no
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/processes", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/integrazioni/chiavi", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/forms", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/gruppi", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/users", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/ruoli", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/registro", null)).isEqualTo(HttpStatus.FORBIDDEN);
        // chi non ha ruoli non entra nello Studio
        assertThat(stato("mario", HttpMethod.GET, "/api/admin/processes", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("mario", HttpMethod.GET, "/api/admin/forms", null)).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> paolo = come("paolo").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody();
        assertThat((List<String>) paolo.get("permessi")).containsExactlyInAnyOrder("studio.processi", "studio.form", "studio.pubblica", "integrazioni.gestisci", "anagrafiche.definisci");
        assertThat(paolo.get("admin")).isEqualTo(false);
        Map<String, Object> admin = come("admin").exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody();
        assertThat(admin.get("admin")).isEqualTo(true);
        assertThat((List<String>) admin.get("permessi")).hasSize(Permesso.values().length);
    }

    @Test
    void unRuoloLegatoAUnGruppoValeSubitoEsiToglieSubito() {
        String gruppo = nuovoNome("revisori");
        String codice = nuovoNome("lettori-registro");
        Map<String, Object> r = creaRuolo(codice, List.of("registro.leggi"), List.of(gruppo));
        String utente = nuovoNome("rev");
        Map<String, Object> creato = creaUtente("admin", utente, List.of(), List.of(gruppo));
        assertThat((List<String>) creato.get("ruoliDaGruppi")).containsExactly(codice);

        assertThat(stato(utente, HttpMethod.GET, "/api/admin/registro", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato(utente, HttpMethod.GET, "/api/admin/processes", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // tolto il gruppo dal ruolo, il permesso sparisce alla richiesta successiva
        Map<String, Object> modifica = new HashMap<>(r);
        modifica.put("gruppi", List.of());
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/ruoli/" + r.get("id"), modifica)).isEqualTo(HttpStatus.OK);
        assertThat(stato(utente, HttpMethod.GET, "/api/admin/registro", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // il registro ha le azioni sui ruoli
        Map<String, Object> pagina = come("admin").exchange("/api/admin/registro?q=" + codice, HttpMethod.GET,
            new HttpEntity<>(xhr()), MAPPA).getBody();
        assertThat(pagina.toString()).contains("RUOLO_CREATO").contains("RUOLO_MODIFICATO");
    }

    @Test
    void ilGestoreUtentiNonPuoDarePermessiCheNonHa() {
        String gestore = nuovoNome("gu");
        creaUtente("admin", gestore, List.of("gestore-utenti"), List.of());
        assertThat(stato(gestore, HttpMethod.GET, "/api/admin/users", null)).isEqualTo(HttpStatus.OK);

        // ruoli più potenti del suo: no, né direttamente né tramite un gruppo
        assertThat(stato(gestore, HttpMethod.POST, "/api/admin/users", utente(nuovoNome("x"), List.of("amministratore"), List.of())))
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(gestore, HttpMethod.POST, "/api/admin/users", utente(nuovoNome("x"), List.of("analista"), List.of())))
            .isEqualTo(HttpStatus.FORBIDDEN);
        String gruppo = nuovoNome("auditor");
        creaRuolo(nuovoNome("auditor"), List.of("registro.leggi"), List.of(gruppo));
        assertThat(stato(gestore, HttpMethod.POST, "/api/admin/users", utente(nuovoNome("x"), List.of(), List.of(gruppo))))
            .isEqualTo(HttpStatus.FORBIDDEN);
        // non tocca chi ha più permessi di lui (es. cambiare la password dell'amministratore)
        Map<String, Object> admin = come("admin").exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA)
            .getBody().stream().filter(u -> "admin".equals(u.get("username"))).findFirst().orElseThrow();
        Map<String, Object> presa = new HashMap<>(admin);
        presa.put("password", "rubata");
        assertThat(stato(gestore, HttpMethod.PUT, "/api/admin/users/" + admin.get("id"), presa)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(gestore, HttpMethod.DELETE, "/api/admin/users/" + admin.get("id"), null)).isEqualTo(HttpStatus.FORBIDDEN);
        // non allarga un ruolo con permessi che non ha
        Map<String, Object> analista = new HashMap<>(ruolo("analista"));
        analista.put("permessi", List.of("studio.processi", "studio.form", "utenti.gestisci"));
        assertThat(stato(gestore, HttpMethod.PUT, "/api/admin/ruoli/" + analista.get("id"), analista)).isEqualTo(HttpStatus.FORBIDDEN);
        Map<String, Object> gestori = new HashMap<>(ruolo("gestore-utenti"));
        gestori.put("permessi", List.of("utenti.gestisci", "registro.leggi"));
        assertThat(stato(gestore, HttpMethod.PUT, "/api/admin/ruoli/" + gestori.get("id"), gestori)).isEqualTo(HttpStatus.FORBIDDEN);

        // ruoli entro i suoi permessi: sì
        creaUtente(gestore, nuovoNome("collega"), List.of("gestore-utenti"), List.of());
        assertThat(stato(gestore, HttpMethod.GET, "/api/admin/processes", null)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void senzaPubblicazioneSiPreparanoSoloLeBozze() {
        String codice = nuovoNome("disegnatori");
        creaRuolo(codice, List.of("studio.processi"), List.of());
        String utente = nuovoNome("dis");
        creaUtente("admin", utente, List.of(codice), List.of());
        assertThat(stato(utente, HttpMethod.GET, "/api/admin/bozze", null)).isEqualTo(HttpStatus.OK);
        org.springframework.util.LinkedMultiValueMap<String, Object> corpo = new org.springframework.util.LinkedMultiValueMap<>();
        corpo.add("bpmn", new org.springframework.core.io.ByteArrayResource("<x/>".getBytes()) {
            @Override public String getFilename() { return "p.bpmn"; }
        });
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(come(utente).exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restaSempreUnAmministratoreEIlSuoRuoloNonCambia() {
        Map<String, Object> admin = come("admin").exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA)
            .getBody().stream().filter(u -> "admin".equals(u.get("username"))).findFirst().orElseThrow();
        Map<String, Object> senzaRuoli = new HashMap<>(admin);
        senzaRuoli.put("ruoli", List.of());
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/users/" + admin.get("id"), senzaRuoli)).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> amministratore = ruolo("amministratore");
        assertThat(stato("admin", HttpMethod.DELETE, "/api/admin/ruoli/" + amministratore.get("id"), null)).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> ridotto = new HashMap<>(amministratore);
        ridotto.put("permessi", List.of());
        ridotto.put("nome", "Ridotto");
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/ruoli/" + amministratore.get("id"), ridotto)).isEqualTo(HttpStatus.OK);
        Map<String, Object> dopo = ruolo("amministratore");
        assertThat(dopo.get("nome")).isEqualTo("Amministratore");
        assertThat((List<String>) dopo.get("permessi")).hasSize(Permesso.values().length);
        assertThat(stato("admin", HttpMethod.GET, "/api/admin/users", null)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ruoliNonValidiSonoRifiutati() {
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/ruoli",
            Map.of("codice", "Con Spazi", "nome", "x"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/ruoli",
            Map.of("codice", nuovoNome("r"), "nome", "x", "permessi", List.of("inventato")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.POST, "/api/admin/users",
            utente(nuovoNome("x"), List.of("ruolo-che-non-esiste"), List.of()))).isEqualTo(HttpStatus.BAD_REQUEST);
        // un ruolo creato si elimina e sparisce dagli utenti
        String codice = nuovoNome("temporaneo");
        Map<String, Object> r = creaRuolo(codice, List.of("registro.leggi"), List.of());
        String utente = nuovoNome("tmp");
        creaUtente("admin", utente, List.of(codice), List.of());
        assertThat(stato(utente, HttpMethod.GET, "/api/admin/registro", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("admin", HttpMethod.DELETE, "/api/admin/ruoli/" + r.get("id"), null)).isEqualTo(HttpStatus.OK);
        assertThat(stato(utente, HttpMethod.GET, "/api/admin/registro", null)).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
