package com.eligesoft.orditogram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L'esempio della skill "orditogram-soluzione" (.claude/skills/orditogram-soluzione/esempio) deve restare
 * importabile e pubblicabile senza errori né avvisi. Se una modifica dei formati lo rompe, va aggiornata anche la
 * skill (riferimenti, verifica.py, esempio): è il segnale che la skill è rimasta indietro rispetto al codice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SkillEsempioIntegrationTest {

    private static final Path ESEMPIO = Path.of("..", ".claude", "skills", "orditogram-soluzione", "esempio");

    @Autowired TestRestTemplate rest;

    private TestRestTemplate admin() {
        return rest.withBasicAuth("admin", "admin");
    }

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    /** Lo stesso pacchetto che crea script/pacchetto.py. */
    private static byte[] pacchetto() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            aggiungi(zip, "processo.bpmn20.xml", ESEMPIO.resolve("processo.bpmn20.xml"));
            aggiungi(zip, "permessi.json", ESEMPIO.resolve("permessi.json"));
            for (String cartella : List.of("form", "anagrafiche"))
                try (Stream<Path> files = Files.list(ESEMPIO.resolve(cartella))) {
                    for (Path p : files.sorted().toList()) aggiungi(zip, cartella + "/" + p.getFileName(), p);
                }
            zip.putNextEntry(new ZipEntry("orditogram-pacchetto.json"));
            zip.write("{\"formato\": 1, \"chiave\": \"contributiEventi\"}".getBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void aggiungi(ZipOutputStream zip, String nome, Path p) throws Exception {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(Files.readAllBytes(p));
        zip.closeEntry();
    }

    private static ByteArrayResource file(String nome, byte[] contenuto) {
        return new ByteArrayResource(contenuto) {
            @Override public String getFilename() { return nome; }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void lEsempioDellaSkillSiImportaESiPubblicaSenzaAvvisi() throws Exception {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", file("contributiEventi.orditogram.zip", pacchetto()));
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> importato = admin().postForEntity("/api/admin/pacchetti?sovrascrivi=true", new HttpEntity<>(corpo, h), Map.class);
        assertEquals(HttpStatus.OK, importato.getStatusCode(), () -> "importazione: " + importato.getBody());
        assertEquals(7, ((Collection<?>) importato.getBody().get("formSalvati")).size());
        assertTrue(importato.getBody().get("anagrafiche").toString().contains("associazioni"), () -> "anagrafiche: " + importato.getBody());

        // i permessi delle anagrafiche non viaggiano nel pacchetto: li applica installa.py
        Map<String, List<Map<String, String>>> regole = new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(ESEMPIO.resolve("permessi-anagrafiche.json").toFile(), Map.class);
        for (var e : regole.entrySet()) {
            ResponseEntity<String> r = admin().exchange("/api/admin/anagrafiche/" + e.getKey() + "/permessi", HttpMethod.PUT,
                new HttpEntity<>(e.getValue(), xhr()), String.class);
            assertEquals(HttpStatus.OK, r.getStatusCode(), r::getBody);
        }

        // la stessa verifica di verifica.py: niente errori e niente avvisi
        String xml = Files.readString(ESEMPIO.resolve("processo.bpmn20.xml"));
        Map<String, Object> esito = admin().postForEntity("/api/admin/processes/valida",
            new HttpEntity<>(Map.of("xml", xml), xhr()), Map.class).getBody();
        assertEquals(List.of(), esito.get("errors"));
        assertEquals(List.of(), esito.get("warnings"));

        MultiValueMap<String, Object> bpmn = new LinkedMultiValueMap<>();
        bpmn.add("bpmn", file("processo.bpmn20.xml", xml.getBytes()));
        ResponseEntity<Map> pubblicato = admin().postForEntity("/api/admin/processes/deploy", new HttpEntity<>(bpmn, h), Map.class);
        assertEquals(HttpStatus.OK, pubblicato.getStatusCode(), () -> "pubblicazione: " + pubblicato.getBody());
        assertEquals("contributiEventi", pubblicato.getBody().get("key"));
    }
}
