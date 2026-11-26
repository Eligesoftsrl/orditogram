package com.eligesoft.soluzioni.isi;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Dati e strumenti comuni ai test. */
final class Prova {
    static final Regole REGOLE = Regole.carica(Path.of("configurazione"), "2025", null);

    private Prova() {}

    /** Impresa manifatturiera di base, iscritta nel 2010, rapporto INAIL, dichiarazioni spuntate. */
    static Map<String, Object> domanda(String... coppie) {
        Map<String, Object> d = new HashMap<>(Map.of("annualita", "2025", "ateco", "25.11", "tipoSoggetto", "Impresa",
                "rapportoAssicurativo", "INAIL", "dataIscrizioneCciaa", "2010-03-01", "dataAssociazionePortale", "2026-01-10",
                "regione", "Toscana", "ula", "8", "fatturato", "1500000", "dipendentiImpresa", "8"));
        d.put("dipendentiUnita", "8");
        d.put("partiSociali", "Nessuna");
        d.put("finanziamentiPrecedenti", "Nessuno");
        for (String k : new String[] {"dichR02", "dichR03", "dichR05", "dichR06", "dichR07", "dichR08", "dichR09", "dichR10",
                "dichR11", "dichR12", "dichR15", "dichR16", "dichR17", "dichR18", "nessunaSpesaNonAmmessa"}) {
            d.put(k, "true");
        }
        for (int i = 0; i < coppie.length; i += 2) d.put(coppie[i], coppie[i + 1]);
        return d;
    }

    /** Orologio che si può far avanzare o sostituire nei test. */
    static final class Orologio extends Clock {
        LongSupplier millis = () -> Instant.parse("2026-11-26T10:00:00Z").toEpochMilli();

        @Override public ZoneId getZone() { return ZoneId.of("Europe/Rome"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.getAsLong()); }
        @Override public long millis() { return millis.getAsLong(); }
    }

    static Impostazioni impostazioni(boolean calendarioLibero) {
        return new Impostazioni(null, "2025", "configurazione", "dati-prova", calendarioLibero, null, null, null);
    }

    /** Un registro su un database H2 in memoria nuovo, con lo schema delle migrazioni. */
    static Registro registro(Orologio orologio, boolean calendarioLibero) {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(ds).load().migrate();
        return new Registro(new JdbcTemplate(ds), REGOLE, orologio, impostazioni(calendarioLibero));
    }
}
