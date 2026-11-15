package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Sezioni e campi condizionali dei form (fase 3, form avanzati). */
class FormCondizionaliTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static final String ESAME = """
        { "title": "Esame", "fields": [
          { "name": "oggetto", "label": "Oggetto", "type": "text", "readonly": true },
          { "type": "sezione", "label": "Esito", "help": "Decidi come prosegue la richiesta" },
          { "name": "esito", "label": "Esito", "type": "select", "required": true,
            "options": ["Accoglibile", "Serve integrazione", "Ci sono controinteressati"] },
          { "name": "noteIntegrazione", "label": "Cosa chiedere", "type": "textarea", "required": true,
            "visibileSe": { "campo": "esito", "valori": ["Serve integrazione"] } },
          { "name": "urgente", "label": "Urgente", "type": "checkbox",
            "visibileSe": { "campo": "esito", "valori": ["Serve integrazione"] } },
          { "name": "motivoUrgenza", "label": "Perché è urgente", "type": "text", "required": true,
            "visibileSe": { "campo": "urgente", "valori": ["true"] } },
          { "name": "emailControinteressati", "label": "Email dei controinteressati", "type": "text", "required": true,
            "visibileSe": { "campo": "esito", "valore": "Ci sono controinteressati" } }
        ] }""";

    private static JsonNode esame() throws Exception {
        return M.readTree(ESAME);
    }

    private static FormService service() {
        return new FormService(null, M);
    }

    @Test
    void leSezioniNonSonoCampi() throws Exception {
        List<String> nomi = ValoriForm.campi(esame()).stream().map(ValoriForm.Campo::nome).toList();
        assertEquals(List.of("esito", "noteIntegrazione", "urgente", "motivoUrgenza", "emailControinteressati"), nomi);
        ValoriForm.Campo note = ValoriForm.campi(esame()).get(1);
        assertEquals(new ValoriForm.Condizione("esito", List.of("Serve integrazione")), note.visibileSe());
    }

    @Test
    void campiNascostiAncheACatena() throws Exception {
        assertEquals(Set.of("noteIntegrazione", "urgente", "motivoUrgenza", "emailControinteressati"),
            ValoriForm.nascosti(esame(), Map.of()));
        assertEquals(Set.of("motivoUrgenza", "emailControinteressati"),
            ValoriForm.nascosti(esame(), Map.of("esito", "Serve integrazione")));
        assertEquals(Set.of("emailControinteressati"),
            ValoriForm.nascosti(esame(), Map.of("esito", "Serve integrazione", "urgente", true)));
        // urgente resta a true ma è nascosto: anche il motivo sparisce
        assertEquals(Set.of("noteIntegrazione", "urgente", "motivoUrgenza"),
            ValoriForm.nascosti(esame(), Map.of("esito", "Ci sono controinteressati", "urgente", true)));
    }

    @Test
    void obbligatoriSoloSeVisibili() throws Exception {
        assertEquals(List.of(), ValoriForm.obbligatoriMancanti(esame(), Map.of("esito", "Accoglibile")));
        assertEquals(List.of("Cosa chiedere"),
            ValoriForm.obbligatoriMancanti(esame(), Map.of("esito", "Serve integrazione")));
        assertEquals(List.of("Cosa chiedere", "Perché è urgente"),
            ValoriForm.obbligatoriMancanti(esame(), Map.of("esito", "Serve integrazione", "urgente", true)));
        assertEquals(List.of("Email dei controinteressati"),
            ValoriForm.obbligatoriMancanti(esame(), Map.of("esito", "Ci sono controinteressati")));
    }

    @Test
    void iValoriDeiCampiNascostiNonSiSalvano() throws Exception {
        Map<String, Object> body = Map.of("esito", "Accoglibile", "noteIntegrazione", "rimasta dal giro prima",
            "urgente", true, "motivoUrgenza", "x");
        assertEquals(Map.of("esito", "Accoglibile"), ValoriForm.ammessi(esame(), body));
    }

    @Test
    void condizioneSuUnCampoInSolaLetturaUsaIDatiDellaPratica() throws Exception {
        JsonNode form = M.readTree("""
            { "fields": [
              { "name": "tipoAccesso", "label": "Modalità", "type": "text", "readonly": true },
              { "name": "numeroCopie", "label": "Numero di copie", "type": "number", "required": true,
                "visibileSe": { "campo": "tipoAccesso", "valori": ["Estrazione di copia", "Visione ed estrazione di copia"] } }
            ] }""");
        Map<String, Object> pratica = Map.of("tipoAccesso", "Visione");
        assertEquals(List.of(), ValoriForm.obbligatoriMancanti(form, Map.of(), pratica));
        assertEquals(Map.of(), ValoriForm.ammessi(form, Map.of("numeroCopie", "3"), pratica));

        Map<String, Object> copia = Map.of("tipoAccesso", "Estrazione di copia");
        assertEquals(List.of("Numero di copie"), ValoriForm.obbligatoriMancanti(form, Map.of(), copia));
        assertEquals(Map.of("numeroCopie", "3"), ValoriForm.ammessi(form, Map.of("numeroCopie", "3"), copia));
    }

    @Test
    void convalidaDellApiRispettaLeCondizioni() throws Exception {
        ValoriForm.Esito ok = ValoriForm.convalida(esame(), Map.of("esito", "Accoglibile", "noteIntegrazione", "ignorata"));
        assertTrue(ok.valido(), () -> ok.errori().toString());
        assertEquals(Map.of("esito", "Accoglibile"), ok.valori());

        Map<String, Object> in = new HashMap<>(Map.of("esito", "Serve integrazione", "urgente", "true"));
        ValoriForm.Esito ko = ValoriForm.convalida(esame(), in);
        assertEquals(List.of("noteIntegrazione", "motivoUrgenza"), ko.errori().stream().map(ValoriForm.Errore::campo).toList());
    }

    @Test
    void validazioneDelFormNelloStudio() {
        assertDoesNotThrow(() -> service().validate("esame", ESAME));
        assertEquals("Nel form \"f\" c'è una sezione senza titolo", messaggio("""
            { "fields": [ { "type": "sezione" } ] }"""));
        assertEquals("Campo \"b\": la condizione dipende da \"c\", che non è nel form", messaggio("""
            { "fields": [ { "name": "a", "type": "text" },
                          { "name": "b", "type": "text", "visibileSe": { "campo": "c", "valori": ["x"] } } ] }"""));
        assertEquals("Campo \"a\": la condizione non può dipendere dal campo stesso", messaggio("""
            { "fields": [ { "name": "a", "type": "text", "visibileSe": { "campo": "a", "valori": ["x"] } } ] }"""));
        assertEquals("Campo \"b\": la condizione deve indicare almeno un valore", messaggio("""
            { "fields": [ { "name": "a", "type": "text" },
                          { "name": "b", "type": "text", "visibileSe": { "campo": "a", "valori": [] } } ] }"""));
    }

    private static String messaggio(String json) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service().validate("f", json));
        return e.getReason();
    }
}
