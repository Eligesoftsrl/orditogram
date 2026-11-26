# Servizio ISI

Soluzione del cliente (livello 2 della regola dei quattro livelli): il calcolo del Bando ISI e i registri del bando,
chiamati dai connettori `apiConnector` dei processi `isiDomanda` e `isiElenchi`. Non fa parte di Orditogram.
Java 21 e Spring Boot 3.5.16, come Orditogram; registri in PostgreSQL (H2 su file in sviluppo) con Flyway.

| File | Cosa contiene |
|---|---|
| `Calcolo.java` | motore di calcolo puro: punteggio, importi, assi ammessi, errori, documenti; ogni valore con il codice regola |
| `Registro.java` | registri: registrazioni con fotografia e impronta, codici, sportello, elenchi, istruttorie |
| `ServizioController.java` | API HTTP (vedi `../documentazione/06-integrazioni.md`) |
| `FiltroAccesso.java`, `GestoreErrori.java` | credenziale Bearer, errore simulato, limite del corpo, log; errori `{"errore": {codice, messaggio}}` |
| `ServiziFinti.java`, `AvvisoOrditogram.java` | anagrafica INAIL finta e controllo del tecnico; avviso degli esiti a Orditogram |
| `configurazione/2025.json` | tutte le tabelle del capitolo 9 della specifica, il calendario e gli stanziamenti |
| `dati-prova/` | imprese dell'anagrafica INAIL finta, calendario del collaudo |
| `src/test` | casi di prova del capitolo 11 (TC-01 … TC-24) e altri controlli: `CalcoloTest`, `RegistroTest`, `ServizioHttpTest` |

```bash
./mvnw -B verify                                  # 37 test, poi target/servizio-isi.jar
ISI_TOKEN=segreto java -jar target/servizio-isi.jar   # http://127.0.0.1:8095/salute
```

Installazione e configurazione: `../documentazione/03-manuale-di-esercizio.md`.
