package com.eligesoft.orditogram.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le migrazioni Flyway su un database vuoto (nuova installazione) e su uno creato da Orditogram 0.12 con ddl-auto,
 * dove le tabelle esistono già e contengono dati.
 */
class MigrazioniDatabaseTest {

    private static JdbcDataSource nuovoDatabase() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migrazioni-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }

    private static Flyway flyway(JdbcDataSource ds) {
        // stessa configurazione di application.yml
        return Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("0").load();
    }

    @Test
    void databaseVuotoRiceveTutteLeMigrazioni() {
        JdbcDataSource ds = nuovoDatabase();
        MigrateResult esito = flyway(ds).migrate();

        assertThat(esito.success).isTrue();
        assertThat(esito.migrationsExecuted).isGreaterThanOrEqualTo(2);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        // ripetere l'avvio non fa nulla
        assertThat(flyway(ds).migrate().migrationsExecuted).isZero();
    }

    @Test
    void databaseDellaVersionePrecedenteConservaIDati() throws Exception {
        JdbcDataSource ds = nuovoDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // database di Orditogram 0.12: tabelle create da Hibernate, tabelle di Activiti, dati, nessuna storia di Flyway
        String schema;
        try (var in = getClass().getResourceAsStream("/db/migration/V1__schema_iniziale.sql")) {
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String istruzione : schema.replaceAll("(?m)^--.*$", "").split(";")) {
            if (!istruzione.isBlank()) jdbc.execute(istruzione);
        }
        jdbc.execute("CREATE TABLE act_ge_property (name_ VARCHAR(64) PRIMARY KEY, value_ VARCHAR(300))");
        jdbc.execute("ALTER TABLE pratica_evento ADD CONSTRAINT pratica_evento_tipo_check CHECK (tipo IN ('AVVIO'))");
        jdbc.update("INSERT INTO app_user (admin, enabled, password, username) VALUES (true, true, 'x', 'admin')");

        MigrateResult esito = flyway(ds).migrate();

        assertThat(esito.success).isTrue();
        assertThat(jdbc.queryForObject("SELECT username FROM app_user", String.class)).isEqualTo("admin");
        // V2 ha tolto il vincolo che rifiutava i tipi di evento nuovi
        jdbc.update("INSERT INTO pratica_evento (process_instance_id, quando, tipo) VALUES ('1', CURRENT_TIMESTAMP, 'EMAIL')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pratica_evento", Integer.class)).isEqualTo(1);
    }
}
