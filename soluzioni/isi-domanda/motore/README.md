# Servizio ISI

Soluzione del cliente (livello 2 della regola dei quattro livelli): il calcolo del Bando ISI e i registri del bando,
chiamati dai connettori `apiConnector` dei processi `isiDomanda` e `isiElenchi`. Non fa parte di Orditogram.

| File | Cosa contiene |
|---|---|
| `calcolo.py` | motore di calcolo puro: punteggio, importi, assi ammessi, errori, documenti; ogni valore con il codice regola |
| `servizio.py` | API HTTP e registri in SQLite (registrazioni, codici, sportello, elenchi, istruttorie), servizi finti |
| `configurazione/2025.json` | tutte le tabelle del capitolo 9 della specifica, il calendario e gli stanziamenti |
| `dati-prova/` | imprese dell'anagrafica INAIL finta, calendario del collaudo |
| `test_calcolo.py`, `test_servizio.py` | casi di prova del capitolo 11 (TC-01 … TC-24) e altri controlli |

```bash
python3 -m unittest -v test_calcolo test_servizio     # 37 test
ISI_TOKEN=segreto python3 servizio.py --porta 8095 --db isi.sqlite
```

Installazione e configurazione: `../documentazione/03-manuale-di-esercizio.md`. API: `../documentazione/06-integrazioni.md`.
