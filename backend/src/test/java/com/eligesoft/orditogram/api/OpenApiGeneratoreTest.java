package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.webhook.TipiEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class OpenApiGeneratoreTest {

    static ProcessoPubblicato accessoAtti() {
        return new ProcessoPubblicato("accessoAtti", "Accesso agli atti", "Legge 241/1990", 3, "accessoAtti:3:1",
            "Nuova richiesta", List.of(
                new ValoriForm.Campo("oggetto", "Oggetto", "text", true, List.of(), null),
                new ValoriForm.Campo("emailRichiedente", "Email", "text", true, List.of(), "Email o PEC"),
                new ValoriForm.Campo("tipoAccesso", "Modalità", "select", true, List.of("Visione", "Copia"), null),
                new ValoriForm.Campo("importo", "Importo", "number", false, List.of(), null),
                new ValoriForm.Campo("privacy", "Privacy", "checkbox", true, List.of(), null)),
            "obbligatori", List.of(new ProcessoPubblicato.Messaggio("rinunciaRichiedente", List.of("motivoRinuncia"))));
    }

    @Test
    void percorsoDiAvvioConLoSchemaDelForm() {
        Map<String, Object> doc = OpenApiGeneratore.genera(List.of(accessoAtti()), "https://servizi.ente.it", "0.10.0", null);
        assertEquals("3.1.0", doc.get("openapi"));
        assertEquals("https://servizi.ente.it/api/v1", ((List<Map<String, Object>>) doc.get("servers")).get(0).get("url"));

        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        Map<String, Object> avvio = (Map<String, Object>) ((Map<String, Object>) paths.get("/processi/accessoAtti/pratiche")).get("post");
        Map<String, Object> contenuto = (Map<String, Object>) ((Map<String, Object>) avvio.get("requestBody")).get("content");
        assertTrue(contenuto.containsKey("application/json"));
        assertTrue(contenuto.containsKey("multipart/form-data"), "allegati obbligatori: si può inviare multipart");

        Map<String, Object> schemi = (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");
        Map<String, Object> dati = (Map<String, Object>) schemi.get("Dati_accessoAtti");
        assertEquals(List.of("oggetto", "emailRichiedente", "tipoAccesso", "privacy"), dati.get("required"));
        assertEquals(false, dati.get("additionalProperties"));
        Map<String, Object> props = (Map<String, Object>) dati.get("properties");
        assertEquals(List.of("Visione", "Copia"), ((Map<String, Object>) props.get("tipoAccesso")).get("enum"));
        assertEquals("number", ((Map<String, Object>) props.get("importo")).get("type"));
        assertEquals(true, ((Map<String, Object>) props.get("privacy")).get("const"));
        assertEquals("Email o PEC", ((Map<String, Object>) props.get("emailRichiedente")).get("description"));
    }

    @Test
    void campoCondizionaleNonObbligatorioNelloSchema() {
        ValoriForm.Campo copie = new ValoriForm.Campo("numeroCopie", "Numero di copie", "number", true, List.of(), null,
            new ValoriForm.Condizione("tipoAccesso", List.of("Copia")));
        Map<String, Object> s = OpenApiGeneratore.schemaCampo(copie);
        assertEquals("Si usa solo se tipoAccesso vale Copia, e in quel caso è obbligatorio; altrimenti il valore viene ignorato.",
            s.get("description"));
        ProcessoPubblicato p = accessoAtti();
        List<ValoriForm.Campo> campi = new java.util.ArrayList<>(p.campi());
        campi.add(copie);
        ProcessoPubblicato conCopie = new ProcessoPubblicato(p.chiave(), p.nome(), p.descrizione(), p.versione(), p.definizioneId(),
            p.titoloForm(), campi, p.allegati(), p.messaggi());
        assertEquals(List.of("oggetto", "emailRichiedente", "tipoAccesso", "privacy"), OpenApiGeneratore.schemaDati(conCopie).get("required"));
    }

    @Test
    void regoleDeiCampiNelloSchema() {
        Map<String, Object> importo = OpenApiGeneratore.schemaCampo(new ValoriForm.Campo("importo", "Importo", "number", true,
            List.of(), null, null, Map.of("min", "10", "max", "1000")));
        assertEquals(new java.math.BigDecimal("10"), importo.get("minimum"));
        assertEquals(new java.math.BigDecimal("1000"), importo.get("maximum"));
        Map<String, Object> pec = OpenApiGeneratore.schemaCampo(new ValoriForm.Campo("pec", "PEC", "text", true,
            List.of(), null, null, Map.of("formato", "email", "maxLunghezza", "200")));
        assertEquals("email", pec.get("format"));
        assertEquals(200, pec.get("maxLength"));
        Map<String, Object> cf = OpenApiGeneratore.schemaCampo(new ValoriForm.Campo("cf", "CF", "text", true,
            List.of(), null, null, Map.of("formato", "codiceFiscale")));
        assertTrue(cf.get("description").toString().startsWith("Codice fiscale italiano"));
    }

    @Test
    void messaggiEWebhookDocumentati() {
        Map<String, Object> doc = OpenApiGeneratore.genera(List.of(accessoAtti()), "http://localhost:8080", "sviluppo", "Comune di Prova");
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        assertTrue(paths.containsKey("/messaggi/rinunciaRichiedente"));
        Map<String, Object> schemi = (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");
        assertTrue(((Map<String, Object>) ((Map<String, Object>) schemi.get("DatiMessaggio_rinunciaRichiedente")).get("properties"))
            .containsKey("motivoRinuncia"));
        Map<String, Object> webhooks = (Map<String, Object>) doc.get("webhooks");
        assertEquals(TipiEvento.TUTTI.keySet(), webhooks.keySet());
        assertTrue(((Map<String, Object>) doc.get("info")).get("title").toString().startsWith("Comune di Prova"));
    }

    @Test
    void tuttiIRiferimentiPortanoAUnoSchemaEsistente() {
        Map<String, Object> doc = OpenApiGeneratore.genera(List.of(accessoAtti()), "http://x", "1", null);
        Map<String, Object> schemi = (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");
        controllaRiferimenti(doc, schemi);
    }

    @Test
    void senzaProcessiIlDocumentoEComunqueValido() {
        Map<String, Object> doc = OpenApiGeneratore.genera(List.of(), "http://x", "1", null);
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        assertTrue(paths.containsKey("/pratiche"));
        controllaRiferimenti(doc, (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas"));
    }

    private static void controllaRiferimenti(Object nodo, Map<String, Object> schemi) {
        if (nodo instanceof Map<?, ?> m) {
            Object ref = m.get("$ref");
            if (ref != null) {
                String nome = ref.toString().replace("#/components/schemas/", "");
                assertTrue(schemi.containsKey(nome), "schema mancante: " + nome);
            }
            m.values().forEach(v -> controllaRiferimenti(v, schemi));
        } else if (nodo instanceof List<?> l) {
            l.forEach(v -> controllaRiferimenti(v, schemi));
        }
    }

    @Test
    void schemaOpenApiDellaTabella() throws Exception {
        Map<String, Object> s = OpenApiGeneratore.schemaCampo(ValoriForm.campi(new ObjectMapper().readTree(com.eligesoft.orditogram.form.TabelleFormTest.SPESE)).get(1));
        assertEquals("array", s.get("type"));
        assertEquals(3, s.get("maxItems"));
        assertEquals(1, s.get("minItems"));
        @SuppressWarnings("unchecked") Map<String, Object> riga = (Map<String, Object>) s.get("items");
        assertEquals(List.of("descrizione", "importo"), riga.get("required"));
        @SuppressWarnings("unchecked") Map<String, Object> props = (Map<String, Object>) riga.get("properties");
        assertEquals(Map.of("type", "number", "title", "Importo", "minimum", new java.math.BigDecimal("0.01")), props.get("importo"));
    }
}
