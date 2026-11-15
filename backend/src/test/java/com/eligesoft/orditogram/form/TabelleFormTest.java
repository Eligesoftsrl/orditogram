package com.eligesoft.orditogram.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tabelle di righe nei form (fase 3, form avanzati). */
public class TabelleFormTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final LocalDate OGGI = LocalDate.of(2026, 11, 15);

    public static final String SPESE = """
        { "title": "Rimborso spese", "fields": [
          { "name": "oggetto", "label": "Oggetto", "type": "text", "required": true },
          { "name": "voci", "label": "Voci di spesa", "type": "tabella", "required": true, "maxRighe": 3,
            "colonne": [
              { "name": "descrizione", "label": "Descrizione", "type": "text", "required": true, "maxLunghezza": 20 },
              { "name": "importo", "label": "Importo", "type": "number", "required": true, "min": 0.01 },
              { "name": "data", "label": "Data", "type": "date", "max": "oggi" },
              { "name": "tipo", "label": "Tipo", "type": "select", "options": ["Viaggio", "Pasto"] },
              { "name": "fattura", "label": "Con fattura", "type": "checkbox" } ] }
        ] }""";

    static JsonNode spese() throws Exception {
        return M.readTree(SPESE);
    }

    @Test
    void leColonneDiventanoCampi() throws Exception {
        ValoriForm.Campo voci = ValoriForm.campi(spese()).get(1);
        assertEquals("tabella", voci.tipo());
        assertEquals(List.of("descrizione", "importo", "data", "tipo", "fattura"),
            voci.colonne().stream().map(ValoriForm.Campo::nome).toList());
        assertEquals(List.of("Viaggio", "Pasto"), voci.colonne().get(3).opzioni());
        assertEquals(Map.of("maxRighe", "3"), voci.regole());
    }

    @Test
    void dallInterfacciaSiTolgonoRigheVuoteEColonneSconosciute() throws Exception {
        Map<String, Object> body = Map.of("oggetto", "Trasferta", "voci", List.of(
            Map.of("descrizione", "Treno", "importo", "45.50", "nascosta", "x"),
            Map.of("descrizione", "", "importo", "", "fattura", false)));
        Map<String, Object> ammessi = ValoriForm.ammessi(spese(), body);
        assertEquals(List.of(Map.of("descrizione", "Treno", "importo", "45.50", "fattura", false)), ammessi.get("voci"));
    }

    @Test
    void unaTabellaObbligatoriaVuolAlmenoUnaRiga() throws Exception {
        assertEquals(List.of("Voci di spesa"), ValoriForm.obbligatoriMancanti(spese(), Map.of("oggetto", "x", "voci", List.of())));
        assertEquals(List.of(), ValoriForm.obbligatoriMancanti(spese(),
            Map.of("oggetto", "x", "voci", List.of(Map.of("descrizione", "Treno")))));
    }

    @Test
    void regoleDelleColonneRigaPerRiga() throws Exception {
        Map<String, Object> vars = Map.of("voci", List.of(
            Map.of("descrizione", "Treno", "importo", "45"),
            Map.of("descrizione", "Cena con il cliente di Milano", "importo", "0"),
            Map.of("importo", "12", "data", "2026-11-16", "tipo", "Albergo")));
        List<ValoriForm.Errore> e = ValoriForm.erroriRegole(spese(), vars, Map.of(), OGGI);
        assertEquals(1, e.size());
        assertEquals("voci", e.get(0).campo());
        assertEquals("riga 2, Descrizione: può avere al massimo 20 caratteri; riga 2, Importo: deve essere almeno 0,01; "
            + "riga 3, Descrizione: è obbligatorio", e.get(0).messaggio());
    }

    @Test
    void troppeRighe() throws Exception {
        Map<String, Object> riga = Map.of("descrizione", "Treno", "importo", "1");
        List<ValoriForm.Errore> e = ValoriForm.erroriRegole(spese(), Map.of("voci", List.of(riga, riga, riga, riga)), Map.of(), OGGI);
        assertEquals("al massimo 3 righe", e.get(0).messaggio());
    }

    @Test
    void lApiControllaTipiEColonne() throws Exception {
        ValoriForm.Esito ok = ValoriForm.convalida(spese(), Map.of("oggetto", "Trasferta", "voci", List.of(
            Map.of("descrizione", " Treno ", "importo", 45.5, "tipo", "Viaggio", "fattura", true))));
        assertTrue(ok.valido(), () -> ok.errori().toString());
        assertEquals(List.of(Map.of("descrizione", "Treno", "importo", 45.5, "tipo", "Viaggio", "fattura", true)), ok.valori().get("voci"));

        ValoriForm.Esito colonna = ValoriForm.convalida(spese(), Map.of("oggetto", "x", "voci", List.of(Map.of("importi", 3))));
        assertEquals("riga 1: colonna \"importi\" non prevista (forse intendevi importo?)", colonna.errori().get(0).messaggio());

        ValoriForm.Esito scelta = ValoriForm.convalida(spese(), Map.of("oggetto", "x", "voci", List.of(
            Map.of("descrizione", "Hotel", "importo", 80, "tipo", "Albergo"))));
        assertEquals("riga 1, Tipo: valore non ammesso; scegli tra: Viaggio, Pasto", scelta.errori().get(0).messaggio());

        ValoriForm.Esito nonLista = ValoriForm.convalida(spese(), Map.of("oggetto", "x", "voci", "Treno 45"));
        assertEquals("deve essere un elenco di righe (array di oggetti)", nonLista.errori().get(0).messaggio());

        ValoriForm.Esito vuota = ValoriForm.convalida(spese(), Map.of("oggetto", "x", "voci", List.of()));
        assertEquals(List.of(new ValoriForm.Errore("voci", "obbligatorio")), vuota.errori());
    }

    @Test
    void loStudioControllaLeTabelle() {
        FormService s = new FormService(null, M);
        assertDoesNotThrow(() -> s.validate("spese", SPESE));
        assertEquals("Campo \"t\": una tabella deve avere almeno una colonna", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "colonne": [] } ] }"""));
        assertEquals("Campo \"t\": colonna \"a\": tipo \"textarea\" non ammesso in una tabella", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "colonne": [ { "name": "a", "type": "textarea" } ] } ] }"""));
        assertEquals("Campo \"t\": la colonna \"a\" è duplicata", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "colonne": [ { "name": "a" }, { "name": "a" } ] } ] }"""));
        assertEquals("Campo \"t\": colonna \"n\": il minimo è più grande del massimo", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "colonne": [ { "name": "n", "type": "number", "min": 5, "max": 1 } ] } ] }"""));
        assertEquals("Campo \"t\": le righe minime sono più delle massime", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "minRighe": 4, "maxRighe": 2, "colonne": [ { "name": "a" } ] } ] }"""));
        assertEquals("Campo \"b\": la condizione non può dipendere da una tabella", messaggio(s, """
            { "fields": [ { "name": "t", "type": "tabella", "colonne": [ { "name": "a" } ] },
                          { "name": "b", "type": "text", "visibileSe": { "campo": "t", "valori": ["x"] } } ] }"""));
        assertEquals(Map.of("oggetto", "string", "voci", "json"), assertDoesNotThrow(() -> s.fieldTypes(M.readTree(SPESE))));
    }

    private static String messaggio(FormService s, String json) {
        return assertThrows(ResponseStatusException.class, () -> s.validate("f", json)).getReason();
    }
}
