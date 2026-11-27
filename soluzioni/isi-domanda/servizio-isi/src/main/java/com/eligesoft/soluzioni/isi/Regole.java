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
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

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
            return conCalendario(cartella, annualita, calendario == null ? null : JSON.readTree(Files.readString(calendario)));
        } catch (IOException e) {
            throw new UncheckedIOException("Calendario " + calendario + " non leggibile", e);
        }
    }

    /** Come sopra, con il calendario già letto (o costruito, per la demo). */
    public static Regole conCalendario(Path cartella, String annualita, JsonNode calendario) {
        try {
            JsonNode r = JSON.readTree(Files.readString(cartella.resolve(annualita + ".json")));
            if (calendario != null) {
                ((ObjectNode) r).set("calendario", calendario);
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

    /**
     * Calendario della demo: compilazione, codice e sportello aperti da ieri per 60 giorni, elenchi pubblicabili
     * subito. Così una dimostrazione funziona in qualsiasi giorno dell'anno, anche fuori dal periodo del bando.
     */
    public static ObjectNode calendarioDemo(Clock orologio) {
        OffsetDateTime ieri = OffsetDateTime.now(orologio).minusDays(1).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime fine = ieri.plusDays(61);
        ObjectNode c = JSON.createObjectNode();
        c.put("aperturaCompilazione", ieri.toString());
        c.put("chiusuraCompilazione", fine.toString());
        c.put("aperturaCodice", ieri.toString());
        c.put("chiusuraCodice", fine.toString());
        c.put("aperturaSportello", ieri.toString());
        c.put("chiusuraSportello", fine.toString());
        c.put("pubblicazioneElenchi", ieri.toString());
        c.put("giorniCaricamentoDocumenti", 30);
        return c;
    }

    /** Vero se {@code adesso} è nel periodo di compilazione. */
    public boolean compilazioneAperta(OffsetDateTime adesso) {
        return !adesso.isBefore(OffsetDateTime.parse(testo("calendario", "aperturaCompilazione")))
                && adesso.isBefore(OffsetDateTime.parse(testo("calendario", "chiusuraCompilazione")));
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
