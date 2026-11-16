package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValoriFormTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode form() throws Exception {
        return M.readTree("""
            { "title": "Richiesta", "documenti": "carica", "documentiObbligatori": true, "fields": [
              { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true, "titolo": true },
              { "name": "emailRichiedente", "label": "Email", "type": "text", "required": true },
              { "name": "importo", "label": "Importo", "type": "number" },
              { "name": "dataNascita", "label": "Data di nascita", "type": "date" },
              { "name": "tipoAccesso", "label": "Modalità", "type": "select", "options": ["Visione", "Copia"], "required": true },
              { "name": "privacy", "label": "Accetto", "type": "checkbox", "required": true },
              { "name": "numeroProtocollo", "label": "Protocollo", "type": "text", "readonly": true }
            ] }""");
    }

    @Test
    void campiEsclusiQuelliInSolaLettura() throws Exception {
        List<ValoriForm.Campo> c = ValoriForm.campi(form());
        assertEquals(List.of("oggetto", "emailRichiedente", "importo", "dataNascita", "tipoAccesso", "privacy"),
            c.stream().map(ValoriForm.Campo::nome).toList());
        assertEquals(List.of("Visione", "Copia"), c.get(4).opzioni());
        assertEquals("obbligatori", ValoriForm.allegatiAvvio(form()));
    }

    @Test
    void convalidaCorrettaNormalizzaIValori() throws Exception {
        Map<String, Object> in = new HashMap<>();
        in.put("oggetto", "  Accesso ai verbali  ");
        in.put("emailRichiedente", "mario@example.com");
        in.put("importo", "1250.50");
        in.put("dataNascita", "1980-01-31");
        in.put("tipoAccesso", "Copia");
        in.put("privacy", "true");
        ValoriForm.Esito e = ValoriForm.convalida(form(), in);
        assertTrue(e.valido(), () -> e.errori().toString());
        assertEquals("Accesso ai verbali", e.valori().get("oggetto"));
        assertEquals(Boolean.TRUE, e.valori().get("privacy"));
        assertEquals("1250.50", e.valori().get("importo"));
    }

    @Test
    void convalidaRiportaTuttiGliErroriInsieme() throws Exception {
        Map<String, Object> in = new HashMap<>();
        in.put("oggeto", "refuso");             // nome sbagliato: suggerisce quello giusto
        in.put("importo", "mille");
        in.put("dataNascita", "31/01/1980");
        in.put("tipoAccesso", "Fotocopia");
        in.put("privacy", false);
        in.put("numeroProtocollo", "2026/1");   // in sola lettura
        ValoriForm.Esito e = ValoriForm.convalida(form(), in);
        Map<String, String> errori = new HashMap<>();
        e.errori().forEach(x -> errori.put(x.campo(), x.messaggio()));
        assertTrue(errori.get("oggeto").contains("forse intendevi oggetto"), errori.get("oggeto"));
        assertTrue(errori.get("importo").contains("numero"));
        assertTrue(errori.get("dataNascita").contains("AAAA-MM-GG"));
        assertTrue(errori.get("tipoAccesso").contains("Visione, Copia"));
        assertEquals("deve essere true", errori.get("privacy"));
        assertEquals("il campo è in sola lettura", errori.get("numeroProtocollo"));
        assertEquals("obbligatorio", errori.get("oggetto"));
        assertEquals("obbligatorio", errori.get("emailRichiedente"));
    }

    @Test
    void obbligatoriETitoloComeNellInterfaccia() throws Exception {
        Map<String, Object> vars = new HashMap<>(Map.of("oggetto", "Verbali 2025", "privacy", true));
        assertEquals(List.of("Email", "Modalità"), ValoriForm.obbligatoriMancanti(form(), vars));
        assertEquals("Verbali 2025", ValoriForm.titolo(form(), vars, "Accesso agli atti", LocalDate.of(2026, 10, 4)));
        assertEquals("Accesso agli atti del 04/10/2026", ValoriForm.titolo(null, Map.of(), "Accesso agli atti", LocalDate.of(2026, 10, 4)));
        assertEquals(Map.of("oggetto", "x"), ValoriForm.ammessi(form(), Map.of("oggetto", "x", "numeroProtocollo", "y", "altro", 1)));
    }

    @Test
    void senzaFormSoloValoriSemplici() {
        ValoriForm.Esito e = ValoriForm.convalida(null, Map.of("a", "x", "b", 2, "c", List.of(1)));
        assertEquals(Map.of("a", "x", "b", 2), e.valori());
        assertEquals("c", e.errori().get(0).campo());
    }

    @Test
    void campoOrganizzazioneAccettaSoloUnId() throws Exception {
        JsonNode f = M.readTree("""
            { "fields": [ { "name": "controparte", "label": "Controparte", "type": "organizzazione", "parte": "controparte" } ] }""");
        ValoriForm.Esito ok = ValoriForm.convalida(f, Map.of("controparte", 12));
        assertTrue(ok.valido());
        assertEquals("12", ok.valori().get("controparte"));
        ValoriForm.Esito ko = ValoriForm.convalida(f, Map.of("controparte", "Studio Rossi"));
        assertFalse(ko.valido());
        assertEquals("controparte", ko.errori().get(0).campo());
    }
}
