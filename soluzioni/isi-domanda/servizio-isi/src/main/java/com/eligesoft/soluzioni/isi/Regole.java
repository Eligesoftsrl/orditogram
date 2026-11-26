package com.eligesoft.soluzioni.isi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le regole di un'annualità ({@code configurazione/<anno>.json}): tutte le tabelle del capitolo 9 della specifica,
 * il calendario e gli stanziamenti. Si cambiano nel file, mai nel codice.
 */
public final class Regole {
    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private final JsonNode radice;

    private Regole(JsonNode radice) {
        this.radice = radice;
    }

    /** Legge {@code <cartella>/<annualita>.json}; con {@code calendario} sostituisce il calendario (solo collaudo). */
    public static Regole carica(Path cartella, String annualita, Path calendario) {
        try {
            JsonNode r = JSON.readTree(Files.readString(cartella.resolve(annualita + ".json")));
            if (calendario != null) {
                ((ObjectNode) r).set("calendario", JSON.readTree(Files.readString(calendario)));
            }
            return new Regole(r);
        } catch (IOException e) {
            throw new UncheckedIOException("Regole dell'annualità " + annualita + " non leggibili", e);
        }
    }

    public JsonNode nodo(String... percorso) {
        JsonNode n = radice;
        for (String p : percorso) n = n.path(p);
        return n;
    }

    /** Numero della configurazione (zero se manca). */
    public BigDecimal num(String... percorso) {
        return numero(nodo(percorso));
    }

    public String testo(String... percorso) {
        return nodo(percorso).asText();
    }

    public static BigDecimal numero(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO;
        return n.isNumber() ? n.decimalValue() : new BigDecimal(n.asText());
    }

    public static BigDecimal numeroONull(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : numero(n);
    }

    public BigDecimal parametro(String nome) {
        return num("parametri", nome);
    }

    public String annualita() { return testo("annualita"); }
    public String versione() { return testo("versioneRegole"); }
    public BigDecimal soglia() { return parametro("soglia"); }

    /** Gruppo della sede INAIL della Regione (o la sede nazionale). */
    public String gruppoSede(String regione) {
        JsonNode s = nodo("sedi", regione);
        return s.isTextual() ? s.asText() : testo("sedePredefinita");
    }
}
