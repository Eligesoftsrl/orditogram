package com.eligesoft.orditogram.accesso;

import org.junit.jupiter.api.BeforeEach;
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
 * Referente dell'organizzazione, con chiamate HTTP vere: l'ente lo nomina, lui aggiunge un collega (che deve
 * cambiare la password temporanea prima di fare altro), disattiva, riattiva e toglie le persone della sua
 * organizzazione, e non arriva a nient'altro: altre organizzazioni, utenti interni, altri referenti, sé stesso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.organizzazioni.max-aggiunte-ora=3")
@ActiveProfiles("test")
class ReferenteOrganizzazioneIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;

    @BeforeEach
    void sigla() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void referenteGestisceSoloLePersoneDellaSuaOrganizzazione() {
        Long studioA = organizzazione("studio-a");
        Long studioB = organizzazione("studio-b");
        Long referente = utente("referente", "ESTERNO", List.of(studioA));
        Long altroReferente = utente("vice", "ESTERNO", List.of(studioA));
        Long collega = utente("collega", "ESTERNO", List.of(studioA));
        Long condiviso = utente("condiviso", "ESTERNO", List.of(studioA, studioB));
        Long legaleB = utente("legale-b", "ESTERNO", List.of(studioB));
        String ref = "referente-" + sigla;

        // prima della nomina non gestisce nulla
        assertThat(lista(ref, "/api/organizzazione")).isEmpty();
        assertThat(stato(ref, HttpMethod.POST, "/api/organizzazione/" + studioA + "/persone", persona("x"))).isEqualTo(HttpStatus.FORBIDDEN);

        // l'ente lo nomina (insieme a un vice)
        ResponseEntity<String> nomina = come("admin").exchange("/api/admin/organizzazioni/" + studioA + "/referenti",
            HttpMethod.PUT, new HttpEntity<>(Map.of("utenti", List.of(referente, altroReferente)), xhr()), String.class);
        assertThat(nomina.getStatusCode()).as(nomina.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> me = chiama(ref, HttpMethod.GET, "/api/me", null).getBody();
        assertThat((List<Map<String, Object>>) me.get("referenteDi")).extracting(o -> ((Number) o.get("id")).longValue()).containsExactly(studioA);

        List<Map<String, Object>> mie = lista(ref, "/api/organizzazione");
        assertThat(mie).hasSize(1);
        assertThat((List<Map<String, Object>>) mie.get(0).get("persone")).extracting(p -> p.get("username"))
            .contains(ref, "vice-" + sigla, "collega-" + sigla, "condiviso-" + sigla).doesNotContain("legale-b-" + sigla);

        // aggiunge un collega: esterno, nella sua organizzazione, con una password temporanea mostrata una volta
        ResponseEntity<Map<String, Object>> aggiunta = chiama(ref, HttpMethod.POST, "/api/organizzazione/" + studioA + "/persone",
            Map.of("nome", "Nuova", "cognome", "Collega", "email", "Nuova-" + sigla + "@Esempio.it", "ruoli", List.of("amministratore")));
        assertThat(aggiunta.getStatusCode()).as(String.valueOf(aggiunta.getBody())).isEqualTo(HttpStatus.OK);
        String temporanea = (String) aggiunta.getBody().get("passwordTemporanea");
        assertThat(temporanea).hasSizeGreaterThanOrEqualTo(12);
        String nuova = "nuova-" + sigla + "@esempio.it";
        Long idNuova = ((Number) ((Map<String, Object>) aggiunta.getBody().get("collega")).get("id")).longValue();
        Map<String, Object> utenteNuovo = utenteAdmin(nuova);
        assertThat(utenteNuovo.get("tipo")).isEqualTo("ESTERNO");
        assertThat((List<?>) utenteNuovo.get("ruoli")).isEmpty();
        assertThat((List<Object>) utenteNuovo.get("organizzazioni")).extracting(o -> ((Number) o).longValue()).containsExactly(studioA);

        // con la password temporanea può solo cambiarla
        TestRestTemplate nuovoUtente = rest.withBasicAuth(nuova, temporanea);
        Map<String, Object> suoMe = nuovoUtente.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody();
        assertThat(suoMe.get("passwordDaCambiare")).isEqualTo(true);
        assertThat(nuovoUtente.exchange("/api/pratiche", HttpMethod.GET, new HttpEntity<>(xhr()), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cambio(nuovoUtente, "sbagliata", "una-password-lunga")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cambio(nuovoUtente, temporanea, "corta")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cambio(nuovoUtente, temporanea, "x" + nuova)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cambio(nuovoUtente, temporanea, "una-password-lunga")).isEqualTo(HttpStatus.OK);
        TestRestTemplate dopo = rest.withBasicAuth(nuova, "una-password-lunga");
        assertThat(dopo.exchange("/api/pratiche", HttpMethod.GET, new HttpEntity<>(xhr()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dopo.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(xhr()), MAPPA).getBody().get("passwordDaCambiare")).isEqualTo(false);

        // un nome già usato (anche in un'altra organizzazione) dà un errore che non dice dove
        ResponseEntity<Map<String, Object>> doppione = chiama(ref, HttpMethod.POST, "/api/organizzazione/" + studioA + "/persone",
            Map.of("nome", "A", "cognome", "B", "email", nuova));
        assertThat(doppione.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(doppione.getBody())).doesNotContain("studio");

        // disattiva e riattiva un collega
        String base = "/api/organizzazione/" + studioA + "/persone/";
        assertThat(stato(ref, HttpMethod.POST, base + collega + "/disattiva", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("collega-" + sigla, HttpMethod.GET, "/api/me", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(stato(ref, HttpMethod.POST, base + collega + "/riattiva", null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("collega-" + sigla, HttpMethod.GET, "/api/me", null)).isEqualTo(HttpStatus.OK);

        // chi è anche in un'altra organizzazione si può solo togliere dalla sua
        assertThat(stato(ref, HttpMethod.POST, base + condiviso + "/disattiva", null)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato(ref, HttpMethod.DELETE, base + condiviso, null)).isEqualTo(HttpStatus.OK);
        Map<String, Object> dopoTolto = utenteAdmin("condiviso-" + sigla);
        assertThat(dopoTolto.get("enabled")).isEqualTo(true);
        assertThat((List<Object>) dopoTolto.get("organizzazioni")).extracting(o -> ((Number) o).longValue()).containsExactly(studioB);

        // tolto dall'unica organizzazione: anche disattivato
        assertThat(stato(ref, HttpMethod.DELETE, base + idNuova, null)).isEqualTo(HttpStatus.OK);
        assertThat(utenteAdmin(nuova).get("enabled")).isEqualTo(false);

        // non su sé stesso, non su un altro referente, non fuori dalla sua organizzazione (IDOR), non su un'altra
        assertThat(stato(ref, HttpMethod.POST, base + referente + "/disattiva", null)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato(ref, HttpMethod.POST, base + altroReferente + "/disattiva", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(ref, HttpMethod.POST, base + legaleB + "/disattiva", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato(ref, HttpMethod.POST, "/api/organizzazione/" + studioB + "/persone/" + legaleB + "/disattiva", null))
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(utenteAdmin("legale-b-" + sigla).get("enabled")).isEqualTo(true);
        // e resta fuori dall'amministrazione
        assertThat(stato(ref, HttpMethod.GET, "/api/admin/organizzazioni/" + studioA + "/persone", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato(ref, HttpMethod.PUT, "/api/admin/organizzazioni/" + studioA + "/referenti",
            Map.of("utenti", List.of(referente, collega)))).isEqualTo(HttpStatus.FORBIDDEN);

        // un collega qualunque non gestisce nulla
        assertThat(lista("collega-" + sigla, "/api/organizzazione")).isEmpty();
        assertThat(stato("collega-" + sigla, HttpMethod.POST, base + condiviso + "/disattiva", null)).isEqualTo(HttpStatus.FORBIDDEN);

        // tutto nel registro, con chi l'ha fatto
        assertThat(azioni(ref)).contains("UTENTE_ORGANIZZAZIONE_AGGIUNTO", "UTENTE_ORGANIZZAZIONE_DISATTIVATO",
            "UTENTE_ORGANIZZAZIONE_RIATTIVATO", "UTENTE_ORGANIZZAZIONE_TOLTO", "ACCESSO_NEGATO");
        assertThat(azioni("admin")).contains("REFERENTE_NOMINATO");
        assertThat(azioni(nuova)).contains("PASSWORD_CAMBIATA");

        // l'organizzazione disattivata ferma il referente
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/organizzazioni/" + studioA,
            Map.of("nome", "Studio A " + sigla, "attiva", false))).isEqualTo(HttpStatus.OK);
        assertThat(lista(ref, "/api/organizzazione")).isEmpty();
        assertThat(stato(ref, HttpMethod.POST, base + collega + "/disattiva", null)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nominaSoloEsterniDellOrganizzazioneEDecadeSeEsce() {
        Long studio = organizzazione("studio-n");
        Long altro = organizzazione("studio-m");
        Long esterno = utente("esterno", "ESTERNO", List.of(studio));
        Long fuori = utente("fuori", "ESTERNO", List.of(altro));
        Long interno = utente("interno", "INTERNO", List.of());
        String url = "/api/admin/organizzazioni/" + studio + "/referenti";

        assertThat(stato("admin", HttpMethod.PUT, url, Map.of("utenti", List.of(interno)))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.PUT, url, Map.of("utenti", List.of(fuori)))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("admin", HttpMethod.PUT, url, Map.of("utenti", List.of(esterno)))).isEqualTo(HttpStatus.OK);
        assertThat(referenti(studio)).containsExactly("esterno-" + sigla);

        // l'amministratore lo sposta in un'altra organizzazione: non è più referente della prima
        Map<String, Object> u = utenteAdmin("esterno-" + sigla);
        u.put("organizzazioni", List.of(altro));
        u.put("password", null);
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/users/" + esterno, u)).isEqualTo(HttpStatus.OK);
        assertThat(referenti(studio)).isEmpty();
        assertThat(lista("esterno-" + sigla, "/api/organizzazione")).isEmpty();

        // revoca registrata
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/organizzazioni/" + altro + "/referenti",
            Map.of("utenti", List.of(esterno)))).isEqualTo(HttpStatus.OK);
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/organizzazioni/" + altro + "/referenti",
            Map.of("utenti", List.of()))).isEqualTo(HttpStatus.OK);
        assertThat(azioni("admin")).contains("REFERENTE_REVOCATO");
    }

    @Test
    void limiteDiAggiunteAllOra() {
        Long studio = organizzazione("studio-l");
        Long referente = utente("capo", "ESTERNO", List.of(studio));
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/organizzazioni/" + studio + "/referenti",
            Map.of("utenti", List.of(referente)))).isEqualTo(HttpStatus.OK);
        String url = "/api/organizzazione/" + studio + "/persone";
        for (int i = 0; i < 3; i++)
            assertThat(stato("capo-" + sigla, HttpMethod.POST, url, persona("p" + i))).isEqualTo(HttpStatus.OK);
        assertThat(stato("capo-" + sigla, HttpMethod.POST, url, persona("p3"))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void datiDellaNuovaPersonaControllati() {
        Long studio = organizzazione("studio-d");
        Long referente = utente("resp", "ESTERNO", List.of(studio));
        assertThat(stato("admin", HttpMethod.PUT, "/api/admin/organizzazioni/" + studio + "/referenti",
            Map.of("utenti", List.of(referente)))).isEqualTo(HttpStatus.OK);
        String url = "/api/organizzazione/" + studio + "/persone";
        assertThat(stato("resp-" + sigla, HttpMethod.POST, url, Map.of("nome", "A", "cognome", "B", "email", "non-una-email")))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("resp-" + sigla, HttpMethod.POST, url, Map.of("nome", " ", "cognome", "B", "email", "a" + sigla + "@b.it")))
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------ supporto

    private Map<String, Object> persona(String nome) {
        return Map.of("nome", nome, "cognome", "Prova", "email", nome + "-" + sigla + "@esempio.it");
    }

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
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

    private HttpStatusCode cambio(TestRestTemplate chi, String attuale, String nuova) {
        return chi.exchange("/api/me/password", HttpMethod.POST,
            new HttpEntity<>(Map.of("attuale", attuale, "nuova", nuova), xhr()), String.class).getStatusCode();
    }

    private Long organizzazione(String codice) {
        ResponseEntity<Map<String, Object>> r = chiama("admin", HttpMethod.POST, "/api/admin/organizzazioni",
            Map.of("codice", codice + "-" + sigla, "nome", codice + " " + sigla, "attiva", true));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private Long utente(String nome, String tipo, List<Long> organizzazioni) {
        Map<String, Object> u = new HashMap<>();
        u.put("username", nome + "-" + sigla);
        u.put("password", "password");
        u.put("enabled", true);
        u.put("tipo", tipo);
        u.put("groups", List.of());
        u.put("ruoli", List.of());
        u.put("organizzazioni", organizzazioni);
        ResponseEntity<Map<String, Object>> r = chiama("admin", HttpMethod.POST, "/api/admin/users", u);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private Map<String, Object> utenteAdmin(String username) {
        return lista("admin", "/api/admin/users").stream().filter(u -> username.equals(u.get("username")))
            .findFirst().map(HashMap::new).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<String> referenti(Long organizzazione) {
        return lista("admin", "/api/admin/organizzazioni/" + organizzazione + "/persone").stream()
            .filter(p -> Boolean.TRUE.equals(p.get("referente"))).map(p -> (String) p.get("username")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> azioni(String utente) {
        Map<String, Object> r = chiama("admin", HttpMethod.GET, "/api/admin/registro?dimensione=1000", null).getBody();
        return ((List<Map<String, Object>>) r.get("righe")).stream().filter(x -> utente.equals(x.get("utente")))
            .map(x -> (String) x.get("azione")).toList();
    }
}
