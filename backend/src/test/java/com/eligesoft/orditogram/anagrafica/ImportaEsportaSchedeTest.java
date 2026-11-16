package com.eligesoft.orditogram.anagrafica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Lettura dei file CSV e conversione delle celle, senza database. */
class ImportaEsportaSchedeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode campo(String tipo) {
        return JSON.createObjectNode().put("name", "x").put("type", tipo);
    }

    @Test
    void csvConVirgoletteSeparatoriEAcapo() {
        List<List<String>> righe = ImportaEsportaSchede.leggiCsv("a;b;c\n\"uno; due\";\"dice \"\"sì\"\"\";\"riga\nseconda\"\r\n");
        assertThat(righe).containsExactly(List.of("a", "b", "c"), List.of("uno; due", "dice \"sì\"", "riga\nseconda"));
        // separatore virgola, scelto dall'intestazione
        assertThat(ImportaEsportaSchede.leggiCsv("a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void valoriConvertitiSecondoIlCampo() {
        assertThat(ImportaEsportaSchede.valore(campo("checkbox"), "Sì")).isEqualTo(true);
        assertThat(ImportaEsportaSchede.valore(campo("checkbox"), "no")).isEqualTo(false);
        assertThat(ImportaEsportaSchede.valore(campo("date"), "5/3/2026")).isEqualTo("2026-03-05");
        assertThat(ImportaEsportaSchede.valore(campo("date"), "2026-03-05")).isEqualTo("2026-03-05");
        assertThat(ImportaEsportaSchede.valore(campo("text"), "")).isEqualTo("");
        // l'apostrofo che l'esportazione mette davanti alle formule si toglie; gli altri restano
        assertThat(ImportaEsportaSchede.valore(campo("text"), "'=SOMMA(A1)")).isEqualTo("=SOMMA(A1)");
        assertThat(ImportaEsportaSchede.valore(campo("text"), "'tra apici'")).isEqualTo("'tra apici'");
    }
}
