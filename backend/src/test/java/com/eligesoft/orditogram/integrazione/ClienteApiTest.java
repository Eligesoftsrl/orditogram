package com.eligesoft.orditogram.integrazione;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Il connettore API contro un piccolo server HTTP locale. */
class ClienteApiTest {

    static HttpServer server;
    static String base;
    static final String[] ultima = new String[3];   // metodo, corpo, intestazioni

    @BeforeAll
    static void avvia() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ultima[0] = ex.getRequestMethod();
            ultima[1] = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ultima[2] = ex.getRequestHeaders().getFirst("X-API-Key") + "|" + ex.getRequestHeaders().getFirst("Authorization");
            String path = ex.getRequestURI().getPath();
            int code = path.startsWith("/errore") ? 503 : 200;
            if (path.startsWith("/lento")) try { Thread.sleep(2500); } catch (InterruptedException ignored) { }
            byte[] b = (code == 200
                ? "{\"numero\":\"2026/000101\",\"esito\":{\"codice\":7,\"ok\":true},\"elenco\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"vuoto\":null}"
                : "{\"errore\":\"giu\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void ferma() {
        server.stop(0);
    }

    private static final Map<String, Object> DATI = Map.of("oggetto", "Accesso \"atti\"\nriga 2", "codice", "A B/è&x");

    @Test
    void preparaRichiesta() {
        Map<String, String> p = new HashMap<>(Map.of("metodo", "post", "url", base + "/p/${codice}",
            "corpo", "{\"oggetto\": \"${oggetto}\", \"assente\": \"${nonEsiste}\"}"));
        ClienteApi.Richiesta r = ClienteApi.prepara(p, DATI);
        assertEquals("POST", r.metodo());
        assertEquals(base + "/p/A%20B%2F%C3%A8%26x", r.url());
        assertEquals("{\"oggetto\": \"Accesso \\\"atti\\\"\\nriga 2\", \"assente\": \"\"}", r.corpo());
        assertTrue(r.intestazioni().get("Content-Type").contains("json"));
    }

    @Test
    void chiamataELetturaDellaRisposta() {
        Map<String, String> p = Map.of("metodo", "POST", "url", base + "/x", "corpo", "{\"a\": \"${oggetto}\"}");
        Credenziali.Credenziale c = new Credenziali.Credenziale();
        c.setTipo("header"); c.setIntestazione("X-API-Key"); c.setValore("segreto");
        ClienteApi.Risposta r = ClienteApi.esegui(ClienteApi.prepara(p, DATI), c);
        assertTrue(r.ok());
        assertEquals("POST", ultima[0]);
        assertEquals("segreto|null", ultima[2]);

        Map<String, String> v = ClienteApi.estrai("n = numero\ncod = esito.codice\nok = esito.ok\nsecondo = elenco[1].id\nprimo = elenco.0.id\nnullo = vuoto\nstato = @stato", r, new ObjectMapper());
        assertEquals("2026/000101", v.get("n"));
        assertEquals("7", v.get("cod"));
        assertEquals("true", v.get("ok"));
        assertEquals("b", v.get("secondo"));
        assertEquals("a", v.get("primo"));
        assertEquals("", v.get("nullo"));
        assertEquals("200", v.get("stato"));
    }

    @Test
    void credenzialeBasic() {
        Credenziali.Credenziale b = new Credenziali.Credenziale();
        b.setTipo("basic"); b.setUtente("iter"); b.setPassword("pw");
        ClienteApi.esegui(ClienteApi.prepara(Map.of("url", base + "/x"), DATI), b);
        assertEquals("null|Basic aXRlcjpwdw==", ultima[2]);
    }

    @Test
    void errori() {
        assertFalse(ClienteApi.esegui(ClienteApi.prepara(Map.of("url", base + "/errore"), DATI), null).ok());
        ClienteApi.ErroreApi t = assertThrows(ClienteApi.ErroreApi.class,
            () -> ClienteApi.esegui(ClienteApi.prepara(Map.of("url", base + "/lento", "timeout", "1"), DATI), null));
        assertTrue(t.getMessage().contains("1 secondi"));
        assertThrows(ClienteApi.ErroreApi.class, () -> ClienteApi.prepara(Map.of("url", "${host}/x"), DATI));
        assertThrows(ClienteApi.ErroreApi.class, () -> ClienteApi.prepara(Map.of("url", base, "metodo", "TRACE"), DATI));
        ClienteApi.ErroreApi h = assertThrows(ClienteApi.ErroreApi.class,
            () -> ClienteApi.esegui(ClienteApi.prepara(Map.of("url", base, "intestazioni", "Host: x"), DATI), null));
        assertTrue(h.getMessage().startsWith("Intestazione non valida"));
    }

    @Test
    void datiDallaMappatura() {
        assertEquals("[numeroProtocollo, dataProtocollo]",
            ClienteApi.datiDaMappatura("numeroProtocollo = numero\nriga sbagliata\ndataProtocollo = data").toString());
    }

    @Test
    void righeDellaMappatura() {
        var r = ClienteApi.righe("  esito =  stato.codice  \nnote= testo con = dentro\n1nome = x\nvuoto =   \n= solo valore\nsenza uguale");
        assertEquals(2, r.size());
        assertArrayEquals(new String[]{"esito", "stato.codice"}, r.get(0));
        assertArrayEquals(new String[]{"note", "testo con = dentro"}, r.get(1));
        // tempo lineare anche su una riga lunghissima (prima: espressione con backtracking)
        assertTrue(ClienteApi.righe("a =" + " ".repeat(100_000) + "x" + " ".repeat(100_000) + "\u0000").size() <= 1);
    }
}
