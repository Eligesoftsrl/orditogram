-- Prima di Flyway, Hibernate (ddl-auto) creava su pratica_evento.tipo un vincolo CHECK con i tipi di evento di allora
-- e non lo aggiornava: i tipi aggiunti dopo (SCADENZA, EMAIL, CHIAMATA_API, ERRORE...) venivano rifiutati.
-- Il vincolo si toglie, i valori li controlla l'applicazione. Sostituisce la vecchia classe SchemaCompatibilita.
ALTER TABLE pratica_evento DROP CONSTRAINT IF EXISTS pratica_evento_tipo_check;
