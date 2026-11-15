package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regole di validazione dei campi (fase 3, form avanzati). */
class RegoleCampoTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final LocalDate OGGI = LocalDate.of(2026, 11, 15);

    private static JsonNode campo(String json) throws Exception {
        return M.readTree(json);
    }

    private static String prova(String json, String tipo, Object valore) throws Exception {
        return RegoleCampo.controlla(campo(json), tipo, valore, OGGI);
    }

    @Test
    void numeriEDate() throws Exception {
        String importo = """
            { "min": 10, "max": "1000.5" }""";
        assertNull(prova(importo, "number", "10"));
        assertEquals("deve essere almeno 10", prova(importo, "number", "9.99"));
        assertEquals("deve essere al massimo 1000,5", prova(importo, "number", 2000));
        String scadenza = """
            { "min": "oggi", "max": "2026-12-31" }""";
        assertNull(prova(scadenza, "date", "2026-11-15"));
        assertEquals("non può essere prima del giorno di oggi", prova(scadenza, "date", "2026-11-14"));
        assertEquals("non può essere dopo il 31/12/2026", prova(scadenza, "date", "2027-01-01"));
    }

    @Test
    void lunghezzaDelTesto() throws Exception {
        String note = """
            { "minLunghezza": 3, "maxLunghezza": 5 }""";
        assertEquals("deve avere almeno 3 caratteri", prova(note, "textarea", "ab"));
        assertEquals("può avere al massimo 5 caratteri", prova(note, "text", "abcdef"));
        assertNull(prova(note, "text", "àèìòù"));
        assertNull(prova(note, "text", ""), "un campo vuoto lo controllano gli obbligatori");
    }

    @Test
    void formatiItaliani() throws Exception {
        assertNull(prova("{\"formato\":\"codiceFiscale\"}", "text", "RSSMRA80A01H501U"));
        assertNull(prova("{\"formato\":\"codiceFiscale\"}", "text", "rssmra80a01h501u"));
        assertEquals("deve essere un codice fiscale valido", prova("{\"formato\":\"codiceFiscale\"}", "text", "RSSMRA80A01H501X"));
        assertNull(prova("{\"formato\":\"codiceFiscale\"}", "text", "00743110157"), "codice fiscale di un ente");
        assertNull(prova("{\"formato\":\"partitaIva\"}", "text", "00743110157"));
        assertNotNull(prova("{\"formato\":\"partitaIva\"}", "text", "00743110158"));
        assertNull(prova("{\"formato\":\"iban\"}", "text", "IT60 X054 2811 1010 0000 0123 456"));
        assertEquals("deve essere un IBAN valido", prova("{\"formato\":\"iban\"}", "text", "IT60X0542811101000000123457"));
        assertNull(prova("{\"formato\":\"cap\"}", "text", "00184"));
        assertNotNull(prova("{\"formato\":\"cap\"}", "text", "1018"));
        assertNull(prova("{\"formato\":\"email\"}", "text", "mario.rossi@comune.roma.it"));
        assertEquals("deve essere un indirizzo email valido", prova("{\"formato\":\"email\"}", "text", "mario@"));
        assertNull(prova("{\"formato\":\"telefono\"}", "text", "+39 06 1234567"));
        assertNotNull(prova("{\"formato\":\"telefono\"}", "text", "chiamami"));
    }

    @Test
    void espressioneDellAnalista() throws Exception {
        String protocollo = """
            { "formato": "espressione", "espressione": "[0-9]{4}/[0-9]+", "messaggioFormato": "scrivi anno/numero, es. 2026/123" }""";
        assertNull(prova(protocollo, "text", "2026/123"));
        assertEquals("scrivi anno/numero, es. 2026/123", prova(protocollo, "text", "123"));
    }

    @Test
    void unEspressioneEsplosivaNonBloccaIlServer() throws Exception {
        String cattiva = """
            { "formato": "espressione", "espressione": "(a+)+$" }""";
        long inizio = System.nanoTime();
        assertEquals("non è nel formato richiesto", prova(cattiva, "text", "a".repeat(40) + "!"));
        assertTrue((System.nanoTime() - inizio) / 1_000_000 < 2000, "il controllo deve fermarsi in fretta");
    }

    @Test
    void ilFormRispettaLeRegoleOvunque() throws Exception {
        JsonNode form = M.readTree("""
            { "fields": [
              { "name": "importo", "label": "Importo", "type": "number", "min": 1 },
              { "name": "cf", "label": "Codice fiscale", "type": "text", "formato": "codiceFiscale" },
              { "name": "pec", "label": "PEC", "type": "text", "formato": "email",
                "visibileSe": { "campo": "importo", "valori": ["5"] } }
            ] }""");
        List<ValoriForm.Errore> e = ValoriForm.erroriRegole(form, Map.of("importo", "0", "cf", "XYZ", "pec", "no"), Map.of(), OGGI);
        assertEquals(List.of("importo", "cf"), e.stream().map(ValoriForm.Errore::campo).toList(), "pec è nascosto");
        assertEquals("Importo: deve essere almeno 1; Codice fiscale: deve essere un codice fiscale valido",
            ValoriForm.descriviErrori(form, e));

        ValoriForm.Esito api = ValoriForm.convalida(form, Map.of("importo", 5, "pec", "no"));
        assertEquals(List.of("pec"), api.errori().stream().map(ValoriForm.Errore::campo).toList());
        assertEquals(Map.of("min", "1"), ValoriForm.campi(form).get(0).regole());
    }

    @Test
    void loStudioRifiutaRegoleSbagliate() {
        FormService s = new FormService(null, M);
        assertEquals("Campo \"a\": il minimo è più grande del massimo", messaggio(s, """
            { "fields": [ { "name": "a", "type": "number", "min": 5, "max": 1 } ] }"""));
        assertEquals("Campo \"a\": formato \"codice\" non previsto", messaggio(s, """
            { "fields": [ { "name": "a", "type": "text", "formato": "codice" } ] }"""));
        assertTrue(messaggio(s, """
            { "fields": [ { "name": "a", "type": "text", "formato": "espressione", "espressione": "([a-z" } ] }""")
            .startsWith("Campo \"a\": l'espressione non è valida"));
        assertEquals("Campo \"a\": il formato si usa solo con i campi di testo", messaggio(s, """
            { "fields": [ { "name": "a", "type": "number", "formato": "email" } ] }"""));
        assertEquals("Campo \"a\": la data minima deve essere AAAA-MM-GG oppure \"oggi\"", messaggio(s, """
            { "fields": [ { "name": "a", "type": "date", "min": "ieri" } ] }"""));
    }

    private static String messaggio(FormService s, String json) {
        return assertThrows(ResponseStatusException.class, () -> s.validate("f", json)).getReason();
    }
}
