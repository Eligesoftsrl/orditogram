# Codice della soluzione del cliente (livello 2)

Si scrive codice solo quando configurazione (livello 1) e moduli (livello 3) non bastano: calcoli del dominio,
registri con vincoli propri, regole che cambiano per annualità. Quello che servirebbe a più clienti è una richiesta
di sviluppo per Orditogram, non codice della soluzione. Esempio completo: `soluzioni/isi-domanda/servizio-isi/`.

## Tecnologia (vincolante)

- **Java 21, Spring Boot alla stessa versione del `backend/pom.xml` di Orditogram** (oggi 3.5.16), con le stesse
  correzioni di sicurezza nelle `properties` (Tomcat, PostgreSQL, Jackson). Maven Wrapper (copia `.mvn/` e `mvnw`
  dal backend), jar eseguibile con `finalName` uguale alla cartella.
- Dipendenze: `spring-boot-starter-web`, `spring-boot-starter-jdbc` (`JdbcTemplate`), Flyway con
  `flyway-database-postgresql`, driver PostgreSQL e H2 (H2 su file solo per sviluppo e collaudo), test con
  `spring-boot-starter-test`. Niente librerie in più senza un motivo scritto nell'analisi.
- **Mai Python, Node, Go o altri runtime** nel codice consegnato al cliente.
- Pacchetto `com.eligesoft.soluzioni.<nome>`; nomi, messaggi e commenti in italiano come in Orditogram.

## Forma

| Parte | Regola |
|---|---|
| Calcolo | classe pura (`Calcolo.java`): riceve i dati come `Map<String, ?>` di testi (come li manda Orditogram), restituisce un risultato con il dettaglio e il codice della regola di ogni valore; `BigDecimal` per gli importi |
| Regole | `configurazione/<anno>.json` fuori dal jar, lette all'avvio (`USE_BIG_DECIMAL_FOR_FLOATS`); versione delle regole nelle fotografie |
| Registri | `JdbcTemplate`, schema solo con migrazioni Flyway `V<n>__descrizione.sql` valide su PostgreSQL e H2 (`VARCHAR`, `BIGINT`, `TEXT` solo per colonne senza indici); vincoli di unicità nel database, non solo nel codice |
| API | `@RestController`; risposte **oggetti JSON piatti con valori di testo** (i connettori li mappano con `dato = campo`); numeri con il punto per le condizioni del BPMN e la versione con la virgola per le persone |
| Date in ingresso | accetta `AAAA-MM-GG` e il formato di Java che Orditogram mette nei testi dei connettori (`Mon Mar 01 00:00:00 UTC 2010`) |
| Errori | `{"errore": {"codice", "messaggio"}}` con 400, 401, 404, 409, 413; imprevisti 500 con il solo codice `E-…` e il dettaglio nel log |
| Sicurezza | `Authorization: Bearer` confrontato a tempo costante (`MessageDigest.isEqual`), token da variabile d'ambiente, ascolto su `127.0.0.1`, corpo massimo 1 MB; nel BPMN solo il nome della credenziale (`app.credenziali.<nome>`) |
| Collaudo | `?simula=errore` risponde 503 (per le strade di errore degli scenari); servizi esterni finti in `dati-prova/`; impostazioni "solo collaudo" che scrivono un avviso all'avvio |
| Log | una riga JSON per richiesta |
| Avvisi verso Orditogram | `java.net.http.HttpClient` su `POST /api/messaggi/<nome>` con `X-API-Key` (ambito `messaggi:invia`) |

## Test

JUnit 5 e AssertJ: i casi di prova della specifica uno per uno (nomi `tc01…`), il calcolo senza Spring, i registri su
un H2 in memoria nuovo per test con le migrazioni Flyway e un `Clock` sostituibile, un test `@SpringBootTest` con
porta casuale per credenziale, errori e formato delle risposte. `./mvnw -B verify` verde prima del collaudo.

## Documentazione

Il manuale di esercizio dice come costruire (`./mvnw verify`), creare il database, avviare il jar e quali variabili
servono; `06-integrazioni.md` elenca ogni richiesta. Il servizio ha un `README.md` con la tabella dei file.
