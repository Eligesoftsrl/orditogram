package com.eligesoft.orditogram.anagrafica;

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
 * Anagrafiche configurabili con chiamate HTTP vere: definizione nello Studio, permessi per tipo, validazione dei
 * dati, campi chiave, ricerca, versioni e blocco ottimistico, cessazione, relazioni e campi riservati.
 * Utenti di prova: paolo è analista (definisce i tipi), mario è negli operatori, laura nei dirigenti, marco è esterno.
 * Ogni test usa codici suoi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnagraficheIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;
    private String societa;
    private String fondi;

    @BeforeEach
    void preparaTipi() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        societa = "societa-" + sigla;
        fondi = "fondi-" + sigla;
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of(
            "codice", societa, "nome", "Società " + sigla,
            "definizione", Map.of("fields", List.of(
                Map.of("name", "denominazione", "label", "Denominazione", "type", "text", "required", true, "titolo", true),
                Map.of("name", "partitaIva", "label", "Partita IVA", "type", "text", "chiave", true, "formato", "partitaIva"),
                Map.of("name", "iban", "label", "IBAN", "type", "text", "ruoliVisibili", List.of("analista")),
                Map.of("name", "note", "label", "Note", "type", "textarea")))))).isEqualTo(HttpStatus.CREATED);
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of(
            "codice", fondi, "nome", "Fondi " + sigla,
            "definizione", Map.of("fields", List.of(
                Map.of("name", "nome", "label", "Nome del fondo", "type", "text", "required", true, "titolo", true),
                Map.of("name", "codice", "label", "Codice", "type", "text", "chiave", true),
                Map.of("name", "tipologia", "label", "Tipologia", "type", "select", "options", List.of("Aperto", "Negoziale"), "cercabile", true),
                Map.of("name", "societa", "label", "Società istitutrice", "type", "anagrafica", "tipoAnagrafica", societa)))))).isEqualTo(HttpStatus.CREATED);
        // operatori modificano entrambe; dirigenti vedono solo le società; l'analista vede le società (e il loro IBAN)
        regole(societa, List.of(regola("MODIFICA", "GRUPPO", "operatori"), regola("VEDE", "GRUPPO", "dirigenti"),
            regola("VEDE", "RUOLO", "analista")));
        regole(fondi, List.of(regola("MODIFICA", "GRUPPO", "operatori")));
    }

    @Test
    void soloChiDefinisceLeAnagraficheEntraNelloStudio() {
        assertThat(stato("mario", HttpMethod.GET, "/api/admin/anagrafiche", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("mario", HttpMethod.PUT, "/api/admin/anagrafiche/" + societa + "/permessi",
            List.of(regola("MODIFICA", "GRUPPO", "dirigenti")))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/anagrafiche/" + societa, null)).isEqualTo(HttpStatus.OK);
        // la definizione è controllata: relazione verso un'anagrafica che non c'è, chiave su un campo non indicizzabile
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", "x-" + sigla, "nome", "X",
            "definizione", Map.of("fields", List.of(Map.of("name", "a", "type", "anagrafica", "tipoAnagrafica", "inesistente"))))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", "y-" + sigla, "nome", "Y",
            "definizione", Map.of("fields", List.of(Map.of("name", "a", "type", "textarea", "chiave", true))))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", "Maiuscolo", "nome", "Z",
            "definizione", Map.of("fields", List.of(Map.of("name", "a", "type", "text"))))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        // un ruolo che non esiste non si assegna
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + societa + "/permessi",
            List.of(regola("VEDE", "RUOLO", "inesistente")))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void senzaRegoleNessunoVedeLeSchedeTranneLAmministratore() {
        String vuota = "vuota-" + sigla;
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", vuota, "nome", "Vuota",
            "definizione", Map.of("fields", List.of(Map.of("name", "a", "type", "text")))))).isEqualTo(HttpStatus.CREATED);
        assertThat(stato("mario", HttpMethod.GET, "/api/anagrafiche/" + vuota, null)).isEqualTo(HttpStatus.NOT_FOUND);
        // chi definisce i tipi non vede per questo le schede
        assertThat(stato("paolo", HttpMethod.GET, "/api/anagrafiche/" + vuota + "/schede", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stato("admin", HttpMethod.GET, "/api/anagrafiche/" + vuota + "/schede", null)).isEqualTo(HttpStatus.OK);
        List<Object> tipiDiMario = chiamaLista("mario", "/api/anagrafiche");
        assertThat(tipiDiMario).extracting(t -> String.valueOf(((Map<?, ?>) t).get("codice"))).contains(societa, fondi).doesNotContain(vuota);
    }

    @Test
    void gliUtentiEsterniNonArrivanoAlleAnagrafiche() {
        assertThat(stato("marco", HttpMethod.GET, "/api/anagrafiche", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stato("marco", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede", null)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schedeConValidazioneChiaveRicercaEPermessi() {
        Map<String, Object> creata = crea("mario", societa, Map.of("denominazione", "Alfa Previdenza S.p.A.", "partitaIva", "01234567897"));
        Number id = (Number) creata.get("id");
        assertThat(creata.get("titolo")).isEqualTo("Alfa Previdenza S.p.A.");
        assertThat(creata.get("azioni")).asList().contains("MODIFICA");

        // obbligatorio mancante e formato sbagliato: errori per campo, tutti insieme
        ResponseEntity<Map<String, Object>> errata = chiama("mario", HttpMethod.POST, "/api/anagrafiche/" + societa + "/schede",
            Map.of("dati", Map.of("partitaIva", "123")));
        assertThat(errata.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campi(errata)).contains("denominazione", "partitaIva");

        // la chiave non si ripete (maiuscole e spazi non contano)
        ResponseEntity<Map<String, Object>> doppia = chiama("mario", HttpMethod.POST, "/api/anagrafiche/" + societa + "/schede",
            Map.of("dati", Map.of("denominazione", "Copia", "partitaIva", " 01234567897 ")));
        assertThat(doppia.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campi(doppia)).containsExactly("partitaIva");

        // i dirigenti vedono e cercano, ma non modificano
        assertThat(stato("laura", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + id, null)).isEqualTo(HttpStatus.OK);
        assertThat(stato("laura", HttpMethod.POST, "/api/anagrafiche/" + societa + "/schede",
            Map.of("dati", Map.of("denominazione", "Beta")))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(titoli("laura", societa, "testo=previdenza")).containsExactly("Alfa Previdenza S.p.A.");
        assertThat(titoli("laura", societa, "campo=partitaIva&valore=01234567897")).containsExactly("Alfa Previdenza S.p.A.");
        // i caratteri speciali della ricerca non diventano jolly
        assertThat(titoli("laura", societa, "testo=%25")).isEmpty();
        // chi non ha regole sul tipo non sa nemmeno che la scheda esiste
        assertThat(stato("giulia", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + id, null)).isEqualTo(HttpStatus.NOT_FOUND);
        // una scheda di un altro tipo non si legge passando per un tipo visibile
        Map<String, Object> fondo = crea("mario", fondi, Map.of("nome", "Fondo Uno", "codice", "F1"));
        assertThat(stato("laura", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + fondo.get("id"), null))
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void versioniBloccoOttimisticoECessazione() {
        Map<String, Object> s = crea("mario", societa, Map.of("denominazione", "Gamma S.r.l."));
        String url = "/api/anagrafiche/" + societa + "/schede/" + s.get("id");
        Object versione = s.get("versione");

        ResponseEntity<Map<String, Object>> modificata = chiama("mario", HttpMethod.PUT, url,
            Map.of("versione", versione, "dati", Map.of("denominazione", "Gamma S.r.l.", "note", "Sede a Roma")));
        assertThat(modificata.getStatusCode()).isEqualTo(HttpStatus.OK);
        // chi modifica partendo dalla versione vecchia riceve un conflitto
        assertThat(stato("anna", HttpMethod.PUT, url, Map.of("versione", versione, "dati", Map.of("denominazione", "Altro"))))
            .isEqualTo(HttpStatus.CONFLICT);
        Object nuova = modificata.getBody().get("versione");

        ResponseEntity<Map<String, Object>> cessata = chiama("mario", HttpMethod.POST, url + "/cessa",
            Map.of("versione", nuova, "nota", "Fusa in Delta"));
        assertThat(cessata.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cessata.getBody().get("stato")).isEqualTo("CESSATA");
        assertThat(titoli("mario", societa, "testo=gamma")).isEmpty();
        assertThat(titoli("mario", societa, "testo=gamma&stato=TUTTE")).containsExactly("Gamma S.r.l.");
        // una scheda cessata non si modifica
        assertThat(stato("mario", HttpMethod.PUT, url, Map.of("versione", cessata.getBody().get("versione"),
            "dati", Map.of("denominazione", "Gamma 2")))).isEqualTo(HttpStatus.CONFLICT);

        List<Object> versioni = chiamaLista("laura", url + "/versioni");
        assertThat(versioni).extracting(v -> String.valueOf(((Map<?, ?>) v).get("operazione"))).containsExactly("CESSATA", "MODIFICATA", "CREATA");
        assertThat(((Map<?, ?>) versioni.get(0)).get("nota")).isEqualTo("Fusa in Delta");
        assertThat(((Map<?, ?>) versioni.get(0)).get("utenteNome")).isEqualTo("Mario Rossi");
    }

    @Test
    void relazioniTraSchede() {
        Map<String, Object> soc = crea("mario", societa, Map.of("denominazione", "Istitutrice S.p.A."));
        Map<String, Object> fondo = crea("mario", fondi, Map.of("nome", "Fondo Pensione Due", "codice", "F2",
            "tipologia", "Aperto", "societa", soc.get("id").toString()));
        Map<?, ?> collegamento = (Map<?, ?>) ((Map<?, ?>) fondo.get("collegamenti")).get("societa");
        assertThat(collegamento.get("titolo")).isEqualTo("Istitutrice S.p.A.");
        assertThat(collegamento.get("visibile")).isEqualTo(true);

        // la società sa quali fondi la citano
        Map<String, Object> letta = chiama("mario", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + soc.get("id"), null).getBody();
        assertThat((List<?>) letta.get("citataDa")).extracting(c -> String.valueOf(((Map<?, ?>) c).get("titolo"))).containsExactly("Fondo Pensione Due");
        // laura non vede i fondi: la citazione per lei non c'è
        Map<String, Object> perLaura = chiama("laura", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + soc.get("id"), null).getBody();
        assertThat((List<?>) perLaura.get("citataDa")).isEmpty();

        // ricerca dei fondi di una società e per campo ricercabile
        assertThat(titoli("mario", fondi, "campo=societa&valore=" + soc.get("id"))).containsExactly("Fondo Pensione Due");
        assertThat(titoli("mario", fondi, "testo=aperto")).containsExactly("Fondo Pensione Due");

        // una relazione verso una scheda inesistente o di un altro tipo è rifiutata
        ResponseEntity<Map<String, Object>> errata = chiama("mario", HttpMethod.POST, "/api/anagrafiche/" + fondi + "/schede",
            Map.of("dati", Map.of("nome", "Fondo Tre", "societa", fondo.get("id").toString())));
        assertThat(errata.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campi(errata)).containsExactly("societa");

        // con schede salvate, il campo relazione non cambia anagrafica; il tipo collegato non si elimina
        assertThat(stato("paolo", HttpMethod.DELETE, "/api/admin/anagrafiche/" + societa, null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void campiRiservatiAdAlcuniRuoli() {
        // l'amministratore compila anche l'IBAN, riservato agli analisti
        Map<String, Object> s = crea("admin", societa, Map.of("denominazione", "Riservata S.p.A.", "iban", "IT60X0542811101000000123456"));
        String url = "/api/anagrafiche/" + societa + "/schede/" + s.get("id");

        Map<String, Object> perMario = chiama("mario", HttpMethod.GET, url, null).getBody();
        assertThat(((Map<?, ?>) perMario.get("dati")).containsKey("iban")).isFalse();
        Map<String, Object> perPaolo = chiama("paolo", HttpMethod.GET, url, null).getBody();
        assertThat(((Map<?, ?>) perPaolo.get("dati")).get("iban")).isEqualTo("IT60X0542811101000000123456");
        // il tipo che vede mario non mostra il campo
        Map<String, Object> tipo = chiama("mario", HttpMethod.GET, "/api/anagrafiche/" + societa, null).getBody();
        assertThat(tipo.toString()).doesNotContain("iban");

        // mario modifica senza perdere l'IBAN, ma non può scriverlo
        assertThat(stato("mario", HttpMethod.PUT, url, Map.of("versione", perMario.get("versione"),
            "dati", Map.of("denominazione", "Riservata S.p.A.", "iban", "IT00")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stato("mario", HttpMethod.PUT, url, Map.of("versione", perMario.get("versione"),
            "dati", Map.of("denominazione", "Riservata S.p.A.", "note", "aggiornata")))).isEqualTo(HttpStatus.OK);
        Map<String, Object> dopo = chiama("paolo", HttpMethod.GET, url, null).getBody();
        assertThat(((Map<?, ?>) dopo.get("dati")).get("iban")).isEqualTo("IT60X0542811101000000123456");
        // le versioni non mostrano a mario il campo riservato
        assertThat(chiamaLista("mario", url + "/versioni").toString()).doesNotContain("IT60X");
    }

    @Test
    void definizioneCheCambiaRicostruisceLIndice() {
        crea("mario", fondi, Map.of("nome", "Primo", "codice", "A1", "tipologia", "Negoziale"));
        crea("mario", fondi, Map.of("nome", "Secondo", "codice", "A2", "tipologia", "Negoziale"));
        Map<String, Object> tipo = chiama("paolo", HttpMethod.GET, "/api/admin/anagrafiche/" + fondi, null).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> campi = new ArrayList<>((List<Map<String, Object>>) ((Map<?, ?>) tipo.get("definizione")).get("fields"));
        // la tipologia come chiave: due schede hanno lo stesso valore, rifiutato
        List<Map<String, Object>> conChiave = campi.stream().map(c -> {
            Map<String, Object> copia = new LinkedHashMap<>(c);
            if ("tipologia".equals(c.get("name"))) copia.put("chiave", true);
            return copia;
        }).toList();
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + fondi,
            Map.of("definizione", Map.of("fields", conChiave)))).isEqualTo(HttpStatus.BAD_REQUEST);
        // il nome diventa ricercabile: la ricerca lo trova dopo la modifica, e la versione del tipo cresce
        List<Map<String, Object>> conNome = campi.stream().map(c -> {
            Map<String, Object> copia = new LinkedHashMap<>(c);
            if ("tipologia".equals(c.get("name"))) copia.remove("cercabile");
            return copia;
        }).toList();
        ResponseEntity<Map<String, Object>> r = chiama("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + fondi,
            Map.of("definizione", Map.of("fields", conNome)));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("versione")).isEqualTo(2);
        assertThat(titoli("mario", fondi, "testo=negoziale")).isEmpty();
        assertThat(titoli("mario", fondi, "campo=codice&valore=a2")).containsExactly("Secondo");
        // un tipo con schede non si elimina
        assertThat(stato("paolo", HttpMethod.DELETE, "/api/admin/anagrafiche/" + fondi, null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------ supporto

    private Map<String, Object> crea(String utente, String tipo, Map<String, Object> dati) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.POST, "/api/anagrafiche/" + tipo + "/schede", Map.of("dati", dati));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private List<String> titoli(String utente, String tipo, String filtri) {
        Map<String, Object> pagina = chiama(utente, HttpMethod.GET, "/api/anagrafiche/" + tipo + "/schede?" + filtri, null).getBody();
        assertThat(pagina).as(filtri).containsKey("schede");
        return ((List<?>) pagina.get("schede")).stream().map(s -> (String) ((Map<?, ?>) s).get("titolo")).toList();
    }

    private static List<String> campi(ResponseEntity<Map<String, Object>> r) {
        return ((List<?>) r.getBody().get("campi")).stream().map(c -> (String) ((Map<?, ?>) c).get("campo")).toList();
    }

    private void regole(String tipo, List<Map<String, String>> regole) {
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + tipo + "/permessi", regole)).isEqualTo(HttpStatus.OK);
    }

    private static Map<String, String> regola(String azione, String tipoSoggetto, String soggetto) {
        return Map.of("azione", azione, "tipoSoggetto", tipoSoggetto, "soggetto", soggetto);
    }

    private TestRestTemplate come(String utente) {
        return rest.withBasicAuth(utente, "admin".equals(utente) ? "admin" : "password");
    }

    private ResponseEntity<Map<String, Object>> chiama(String utente, HttpMethod metodo, String url, Object corpo) {
        return come(utente).exchange(url, metodo, new HttpEntity<>(corpo, xhr()), MAPPA);
    }

    private List<Object> chiamaLista(String utente, String url) {
        ResponseEntity<List<Object>> r = come(utente).exchange(url, HttpMethod.GET, new HttpEntity<>(xhr()),
            new ParameterizedTypeReference<>() {});
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
