package com.eligesoft.orditogram.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aggiornamenti dello schema che "ddl-auto: update" non fa da solo.
 *
 * Hibernate crea un vincolo CHECK con i valori ammessi per le colonne enum (es. pratica_evento.tipo) e non lo
 * aggiorna quando si aggiungono valori: su un database creato da una versione precedente i nuovi tipi di evento
 * (SCADENZA, EMAIL, CHIAMATA_API, ERRORE) verrebbero rifiutati. Il vincolo si toglie: i valori li controlla l'applicazione.
 */
@Component
public class SchemaCompatibilita {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilita.class);

    private final JdbcTemplate jdbc;

    public SchemaCompatibilita(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationStartedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void aggiorna() {
        try {
            jdbc.execute("ALTER TABLE pratica_evento DROP CONSTRAINT IF EXISTS pratica_evento_tipo_check");
        } catch (Exception e) {
            log.warn("Vincolo pratica_evento_tipo_check non rimosso: {}", e.getMessage());
        }
    }
}
