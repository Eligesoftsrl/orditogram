package com.eligesoft.orditogram.personalizzazione;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Nome e logo del cliente: li cambia l'amministratore, li vedono tutti (anche senza accesso), mai un SVG. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PersonalizzazioneIntegrationTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };

    @Autowired TestRestTemplate rest;

    private static HttpHeaders xhr() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    private TestRestTemplate admin() { return rest.withBasicAuth("admin", "admin"); }

    private ResponseEntity<Map> carica(TestRestTemplate chi, byte[] contenuto, String nomeFile) {
        LinkedMultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("file", new ByteArrayResource(contenuto) { @Override public String getFilename() { return nomeFile; } });
        HttpHeaders h = xhr();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return chi.postForEntity("/api/admin/personalizzazione/logo", new HttpEntity<>(corpo, h), Map.class);
    }

    @AfterEach
    void predefinito() {
        admin().exchange("/api/admin/personalizzazione/logo", HttpMethod.DELETE, new HttpEntity<>(xhr()), Map.class);
        admin().exchange("/api/admin/personalizzazione/nome", HttpMethod.PUT, new HttpEntity<>(Map.of("nome", ""), xhr()), Map.class);
    }

    @Test
    void senzaPersonalizzazioneSiChiamaOrditogram() {
        Map<?, ?> info = rest.getForObject("/api/info", Map.class);
        assertEquals("Orditogram", info.get("nome"));
        assertNull(info.get("logo"));
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/api/info/logo", byte[].class).getStatusCode());
    }

    @Test
    void nomeELogoDelClienteValgonoPerTutti() {
        ResponseEntity<Map> nome = admin().exchange("/api/admin/personalizzazione/nome", HttpMethod.PUT,
            new HttpEntity<>(Map.of("nome", "Portale incentivi INAIL"), xhr()), Map.class);
        assertEquals(HttpStatus.OK, nome.getStatusCode(), () -> String.valueOf(nome.getBody()));
        assertEquals(HttpStatus.OK, carica(admin(), PNG, "logo.png").getStatusCode());

        Map<?, ?> info = rest.getForObject("/api/info", Map.class);
        assertEquals("Portale incentivi INAIL", info.get("nome"));
        String indirizzo = (String) info.get("logo");
        assertTrue(indirizzo.startsWith("/api/info/logo?v="), indirizzo);

        ResponseEntity<byte[]> logo = rest.getForEntity(indirizzo, byte[].class);
        assertEquals(HttpStatus.OK, logo.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, logo.getHeaders().getContentType());
        assertArrayEquals(PNG, logo.getBody());
    }

    @Test
    void soloImmaginiVereESoloLAmministratore() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        ResponseEntity<Map> rifiutato = carica(admin(), svg, "logo.png");
        assertEquals(HttpStatus.BAD_REQUEST, rifiutato.getStatusCode());

        ResponseEntity<Map> nomeConMarkup = admin().exchange("/api/admin/personalizzazione/nome", HttpMethod.PUT,
            new HttpEntity<>(Map.of("nome", "<b>Portale</b>"), xhr()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, nomeConMarkup.getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN, carica(rest.withBasicAuth("mario", "password"), PNG, "logo.png").getStatusCode());
        assertNull(rest.getForObject("/api/info", Map.class).get("logo"));
    }
}
