# Bando ISI: integrazioni

## Servizio ISI (chiamato da Orditogram)

HTTP con JSON, `Authorization: Bearer <ISI_TOKEN>`. Le risposte sono oggetti piatti con valori di testo; gli errori
`{"errore": {"codice", "messaggio"}}` con 400, 401, 404, 409, 413, 503 (con `?simula=errore`, per il collaudo).
Gli errori imprevisti rispondono 500 con il solo codice `E-…`, il dettaglio è nel log.

| Richiesta | Chi la usa | Cosa fa |
|---|---|---|
| `GET /calendario` | avvio della domanda | date delle fasi |
| `GET /impresa?codiceFiscale=` | avvio della domanda | anagrafica INAIL (finta, `dati-prova/imprese.json`) |
| `POST /calcola` | compilazione, istruttoria | punteggio, importi, assi, errori, dettaglio, documenti, gruppo della sede, modello di perizia |
| `POST /registra` | REGISTRA | ricalcolo, unicità impresa + annualità, fotografia con hash, numero e ora al millisecondo |
| `POST /codice` | codice identificativo | 12 caratteri senza 0, O, 1, I; si rivede finché non è usato |
| `POST /sportello` | invio allo sportello | ora dal server al millisecondo presa per prima; codice usato una volta; risposta uguale per codice inesistente, di altri o usato |
| `POST /stato` | decadenza, documenti inviati, rinuncia, esito | cambio di stato con lo storico delle transizioni |
| `POST /incarico` | incarico del tecnico | il tecnico non può essere legale rappresentante o socio |
| `POST /istruttoria/avvia`, `/sospendi`, `/riprendi` | istruttoria | 120 giorni con la sospensione durante le integrazioni |
| `POST /elenchi/anteprima`, `/elenchi/pubblica` | processo degli elenchi | NCD, provvisori (ipotesi H3), definitivi con redistribuzione; criteri di precedenza 9.10 |
| `GET /elenchi`, `GET /salute` | pubblico | elenchi pubblicati (importi troncati all'euro), stato del servizio |

## Messaggi verso Orditogram (dal servizio ISI)

`POST /api/messaggi/esitoElenco` con la chiave API (`messaggi:invia`):

```json
{ "cerca": { "numeroDomanda": "ISI2025-01234567" },
  "dati": { "esitoElenco": "IN_POSIZIONE_UTILE", "posizioneElenco": "12", "termineDocumenti": "2027-06-30T18:00:00+02:00" } }
```

`esitoElenco`: `NCD`, `IN_POSIZIONE_UTILE`, `NON_AMMISSIBILE_FONDI`, `SUBENTRATA`.

`POST /api/messaggi/rinunciaImpresa` con `{"cerca": {"numeroDomanda": …}, "dati": {"motivoRinuncia": "…"}}`: rinuncia
da un sistema esterno (o dalla scheda della pratica).

## Bozze dal simulatore (RF-75)

Non realizzato: l'API pubblica `/api/v1` di Orditogram avvia pratiche con una chiave API, ma senza idempotenza e
senza l'impresa come richiedente. Richiesta di sviluppo N12 nell'analisi.
