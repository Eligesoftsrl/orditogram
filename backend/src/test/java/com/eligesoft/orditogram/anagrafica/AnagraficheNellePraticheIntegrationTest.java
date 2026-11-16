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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schede di anagrafica nelle pratiche: campo "anagrafica" nei form, controllo della scheda per chi compila,
 * fotografia nelle variabili (usata da un gateway), collegamento visibile dalla pratica e dalla scheda,
 * campi riservati fuori dalla fotografia, pacchetti con le anagrafiche usate dai form.
 * Utenti di prova: mario negli operatori (vede le società), laura nei dirigenti (non le vede), paolo analista.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnagraficheNellePraticheIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;

    private String sigla;
    private String societa;
    private String processo;
    private Number alfa;

    @BeforeEach
    void prepara() {
        sigla = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        societa = "societa-" + sigla;
        processo = "contributo" + sigla;
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of(
            "codice", societa, "nome", "Società " + sigla,
            "definizione", Map.of("fields", List.of(
                Map.of("name", "denominazione", "label", "Denominazione", "type", "text", "required", true, "titolo", true),
                Map.of("name", "categoria", "label", "Categoria", "type", "select", "options", List.of("A", "B")),
                Map.of("name", "iban", "label", "IBAN", "type", "text", "ruoliVisibili", List.of("analista"))))))).isEqualTo(HttpStatus.CREATED);
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/anagrafiche/" + societa + "/permessi",
            List.of(Map.of("azione", "MODIFICA", "tipoSoggetto", "GRUPPO", "soggetto", "operatori")))).isEqualTo(HttpStatus.OK);
        alfa = (Number) crea("admin", Map.of("denominazione", "Alfa S.p.A.", "categoria", "A", "iban", "IT60X0542811101000000123456")).get("id");

        // il gateway legge la fotografia: categoria A → "Esame categoria A"
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn" targetNamespace="http://orditogram.it/test">
              <process id="%1$s" name="Contributo %2$s" isExecutable="true">
                <startEvent id="inizio" activiti:formKey="avvio-%2$s"/>
                <sequenceFlow id="f1" sourceRef="inizio" targetRef="scelta"/>
                <exclusiveGateway id="scelta"/>
                <sequenceFlow id="fa" sourceRef="scelta" targetRef="esameA">
                  <conditionExpression>${beneficiario_dati.categoria == 'A'}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="fb" sourceRef="scelta" targetRef="esameAltro">
                  <conditionExpression>${beneficiario_dati.categoria != 'A'}</conditionExpression>
                </sequenceFlow>
                <userTask id="esameA" name="Esame categoria A" activiti:candidateGroups="operatori" activiti:formKey="esame-%2$s"/>
                <userTask id="esameAltro" name="Esame altre categorie" activiti:candidateGroups="operatori" activiti:formKey="esame-%2$s"/>
                <sequenceFlow id="f2" sourceRef="esameA" targetRef="fine"/>
                <sequenceFlow id="f3" sourceRef="esameAltro" targetRef="fine"/>
                <endEvent id="fine"/>
              </process>
            </definitions>
            """.formatted(processo, sigla);
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("bpmn", file(processo + ".bpmn20.xml", xml));
        corpo.add("forms", file("avvio-" + sigla + ".json", """
            { "title": "Domanda", "fields": [
              { "name": "beneficiario", "label": "Beneficiario", "type": "anagrafica", "tipoAnagrafica": "%s",
                "required": true, "titolo": true },
              { "name": "importo", "label": "Importo", "type": "number" } ] }""".formatted(societa)));
        corpo.add("forms", file("esame-" + sigla + ".json", """
            { "title": "Esame", "fields": [
              { "name": "beneficiario", "label": "Beneficiario", "type": "anagrafica", "tipoAnagrafica": "%s" } ] }""".formatted(societa)));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = come("admin").exchange("/api/admin/processes/deploy", HttpMethod.POST, new HttpEntity<>(corpo, h), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void laPraticaFotografaLaSchedaELaUsaNelProcesso() {
        String pi = avvia("mario", Map.of("beneficiario", alfa.toString(), "importo", "1000"));

        Map<String, Object> pratica = chiama("mario", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody();
        // il titolo della pratica è quello della scheda, non il suo id
        assertThat(pratica.toString()).contains("Alfa S.p.A.");
        List<?> collegate = (List<?>) pratica.get("anagrafiche");
        assertThat(collegate).hasSize(1);
        Map<?, ?> c = (Map<?, ?>) collegate.get(0);
        assertThat(c.get("titolo")).isEqualTo("Alfa S.p.A.");
        assertThat(((Map<?, ?>) c.get("fotografia")).get("categoria")).isEqualTo("A");
        // i campi riservati non entrano nella fotografia
        assertThat(((Map<?, ?>) c.get("fotografia")).containsKey("iban")).isFalse();
        assertThat(pratica.toString()).doesNotContain("IT60X");
        // nei dati il campo mostra il titolo; titolo e fotografia non compaiono come dati a parte
        List<?> dati = (List<?>) pratica.get("dati");
        assertThat(dati).extracting(d -> String.valueOf(((Map<?, ?>) d).get("nome"))).contains("beneficiario")
            .doesNotContain("beneficiario_dati", "beneficiario_titolo");
        // il gateway ha letto la fotografia
        assertThat(attivita("mario", pi).get("name")).isEqualTo("Esame categoria A");

        // la scheda cambia, la fotografia della pratica no
        Map<String, Object> scheda = chiama("mario", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + alfa, null).getBody();
        assertThat(stato("mario", HttpMethod.PUT, "/api/anagrafiche/" + societa + "/schede/" + alfa,
            Map.of("versione", scheda.get("versione"), "dati", Map.of("denominazione", "Alfa Holding S.p.A.", "categoria", "B"))))
            .isEqualTo(HttpStatus.OK);
        Map<?, ?> dopo = (Map<?, ?>) ((List<?>) chiama("mario", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody().get("anagrafiche")).get(0);
        assertThat(dopo.get("titolo")).isEqualTo("Alfa S.p.A.");
        assertThat(dopo.get("titoloAttuale")).isEqualTo("Alfa Holding S.p.A.");
        assertThat(((Map<?, ?>) dopo.get("fotografia")).get("categoria")).isEqualTo("A");

        // dalla scheda: le pratiche collegate, solo per chi le vede
        List<Map<String, Object>> pratiche = lista("mario", "/api/anagrafiche/" + societa + "/schede/" + alfa + "/pratiche");
        assertThat(pratiche).extracting(p -> String.valueOf(p.get("id"))).containsExactly(pi);
        assertThat(lista("admin", "/api/anagrafiche/" + societa + "/schede/" + alfa + "/pratiche")).hasSize(1);
    }

    @Test
    void laSchedaDeveEssereVisibileEAttivaPerChiCompila() {
        // laura non vede le società: per lei la scheda non esiste
        ResponseEntity<String> r = come("laura").exchange("/api/processes/" + processo + "/start", HttpMethod.POST,
            new HttpEntity<>(Map.of("beneficiario", alfa.toString()), xhr()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("scheda non trovata");
        // un id che non esiste
        assertThat(come("mario").exchange("/api/processes/" + processo + "/start", HttpMethod.POST,
            new HttpEntity<>(Map.of("beneficiario", "999999999"), xhr()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // una scheda cessata non si sceglie, ma una pratica che la cita già può proseguire
        String pi = avvia("mario", Map.of("beneficiario", alfa.toString()));
        Map<String, Object> s = chiama("mario", HttpMethod.GET, "/api/anagrafiche/" + societa + "/schede/" + alfa, null).getBody();
        assertThat(stato("mario", HttpMethod.POST, "/api/anagrafiche/" + societa + "/schede/" + alfa + "/cessa",
            Map.of("versione", s.get("versione")))).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> cessata = come("mario").exchange("/api/processes/" + processo + "/start", HttpMethod.POST,
            new HttpEntity<>(Map.of("beneficiario", alfa.toString()), xhr()), String.class);
        assertThat(cessata.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cessata.getBody()).contains("cessata");
        String task = String.valueOf(attivita("mario", pi).get("id"));
        assertThat(stato("mario", HttpMethod.POST, "/api/tasks/" + task + "/complete", Map.of("beneficiario", alfa.toString())))
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void unAttivitaPuoCambiareLaScheda() {
        Number beta = (Number) crea("mario", Map.of("denominazione", "Beta S.r.l.", "categoria", "B")).get("id");
        String pi = avvia("mario", Map.of("beneficiario", alfa.toString()));
        String task = String.valueOf(attivita("mario", pi).get("id"));
        assertThat(stato("mario", HttpMethod.POST, "/api/tasks/" + task + "/complete", Map.of("beneficiario", beta.toString())))
            .isEqualTo(HttpStatus.OK);
        Map<?, ?> c = (Map<?, ?>) ((List<?>) chiama("mario", HttpMethod.GET, "/api/pratiche/" + pi, null).getBody().get("anagrafiche")).get(0);
        assertThat(c.get("titolo")).isEqualTo("Beta S.r.l.");
        assertThat(lista("mario", "/api/anagrafiche/" + societa + "/schede/" + alfa + "/pratiche")).isEmpty();
        assertThat(lista("mario", "/api/anagrafiche/" + societa + "/schede/" + beta + "/pratiche")).hasSize(1);
    }

    @Test
    void iFormControllanoLAnagraficaELaProteggono() {
        String form = "{ \"fields\": [ { \"name\": \"x\", \"type\": \"anagrafica\", \"tipoAnagrafica\": \"inesistente-" + sigla + "\" } ] }";
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/forms/prova-" + sigla, form))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        // un'anagrafica usata da un form non si elimina
        String usata = "usata-" + sigla;
        assertThat(stato("paolo", HttpMethod.POST, "/api/admin/anagrafiche", Map.of("codice", usata, "nome", "Usata",
            "definizione", Map.of("fields", List.of(Map.of("name", "a", "type", "text")))))).isEqualTo(HttpStatus.CREATED);
        String conCampo = "{ \"fields\": [ { \"name\": \"x\", \"type\": \"anagrafica\", \"tipoAnagrafica\": \"" + usata + "\" } ] }";
        assertThat(stato("paolo", HttpMethod.PUT, "/api/admin/forms/usa-" + sigla, conCampo)).isEqualTo(HttpStatus.OK);
        assertThat(stato("paolo", HttpMethod.DELETE, "/api/admin/anagrafiche/" + usata, null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ilPacchettoPortaConSeLeAnagraficheDeiForm() throws Exception {
        byte[] zip = come("admin").exchange("/api/admin/pacchetti/" + processo + "?fonte=produzione", HttpMethod.GET,
            new HttpEntity<>(xhr()), byte[].class).getBody();
        Map<String, String> voci = leggiZip(zip);
        assertThat(voci).containsKey("anagrafiche/" + societa + ".json");
        assertThat(voci.get("anagrafiche/" + societa + ".json")).contains("denominazione").doesNotContain("Alfa S.p.A.");

        // in un altro ambiente l'anagrafica manca: la crea l'importazione (qui con un codice nuovo)
        String nuova = "nuova-" + sigla;
        Map<String, String> altro = new LinkedHashMap<>();
        altro.put("processo.bpmn20.xml", voci.get("processo.bpmn20.xml"));
        altro.put("form/avvio-" + sigla + ".json", voci.get("form/avvio-" + sigla + ".json").replace(societa, nuova));
        altro.put("form/esame-" + sigla + ".json", voci.get("form/esame-" + sigla + ".json").replace(societa, nuova));
        altro.put("anagrafiche/" + nuova + ".json", voci.get("anagrafiche/" + societa + ".json").replace(societa, nuova));
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", new ByteArrayResource(scriviZip(altro)) { @Override public String getFilename() { return "p.orditogram.zip"; } });
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> r = come("paolo").exchange("/api/admin/pacchetti?sovrascrivi=true", HttpMethod.POST,
            new HttpEntity<>(corpo, h), MAPPA);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(r.getBody().get("anagrafiche"))).contains("create " + nuova);
        assertThat(stato("paolo", HttpMethod.GET, "/api/admin/anagrafiche/" + nuova, null)).isEqualTo(HttpStatus.OK);
        // le schede non viaggiano con il pacchetto
        assertThat(chiama("admin", HttpMethod.GET, "/api/anagrafiche/" + nuova + "/schede", null).getBody().get("totale")).isEqualTo(0);
    }

    // ------------------------------------------------------------ supporto

    private Map<String, Object> crea(String utente, Map<String, Object> dati) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.POST, "/api/anagrafiche/" + societa + "/schede", Map.of("dati", dati));
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private String avvia(String utente, Map<String, Object> dati) {
        ResponseEntity<Map<String, Object>> r = chiama(utente, HttpMethod.POST, "/api/processes/" + processo + "/start", dati);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.OK);
        return String.valueOf(r.getBody().get("id"));
    }

    private Map<String, Object> attivita(String utente, String pi) {
        return come(utente).exchange("/api/tasks", HttpMethod.GET, new HttpEntity<>(xhr()), LISTA).getBody().stream()
            .filter(t -> pi.equals(t.get("processInstanceId"))).findFirst()
            .orElseThrow(() -> new AssertionError(utente + " non ha attività della pratica " + pi));
    }

    private static Map<String, String> leggiZip(byte[] zip) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) out.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static byte[] scriviZip(Map<String, String> voci) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            for (var v : voci.entrySet()) {
                out.putNextEntry(new ZipEntry(v.getKey()));
                out.write(v.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bos.toByteArray();
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
