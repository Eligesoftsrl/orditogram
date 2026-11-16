# Anagrafiche, permessi, ruoli, utenti esterni

Fonte di verità: `anagrafica/DefinizioneAnagrafica`, `anagrafica/TipiAnagraficaService`, `anagrafica/AccessoAnagrafiche`,
`accesso/RegolePermessiProcesso`, `accesso/PartiService`, `accesso/AccessoEsterni`, migrazioni `V3`-`V9`.

## Anagrafiche (`anagrafiche/<codice>.json`)

Archivi di soggetti o oggetti riusati da più pratiche: associazioni, fornitori, immobili, veicoli, dipendenti.
Una pratica che nomina sempre gli stessi soggetti → anagrafica + campo `anagrafica` nel form, non testo libero.

```json
{
  "codice": "associazioni",
  "nome": "Associazioni",
  "descrizione": "Associazioni iscritte all'albo comunale.",
  "attivo": true,
  "definizione": { "fields": [
    { "type": "sezione", "label": "Dati principali" },
    { "name": "denominazione", "label": "Denominazione", "type": "text", "required": true, "titolo": true, "cercabile": true },
    { "name": "codiceFiscale", "label": "Codice fiscale", "type": "text", "required": true, "chiave": true, "formato": "codiceFiscale" },
    { "name": "pec", "label": "PEC", "type": "text", "formato": "email" },
    { "name": "presidente", "label": "Presidente", "type": "anagrafica", "tipoAnagrafica": "persone" }
  ] }
}
```
- `codice`: minuscole, numeri e trattini (2-60), uguale al nome del file. Non si cambia dopo il rilascio.
- Stesso formato dei form (sezioni, condizioni, tabelle, regole) più: `chiave: true` (valore unico tra le schede,
  maiuscole e spazi non contano: codice fiscale, partita IVA, matricola), `cercabile: true` (si trova con la ricerca),
  `type: "anagrafica"` + `tipoAnagrafica` (relazione con un'altra anagrafica, anche la stessa).
- Un solo campo `titolo` (testo, numero, data o scelta): il nome della scheda negli elenchi. Chiave, ricercabili e
  titolo non possono essere condizionali (`visibileSe` sulla chiave) né riservati (`ruoliVisibili`).
- Niente campi `organizzazione`, niente `parte`. Al massimo 150 campi.
- Le anagrafiche collegate (relazioni) vanno nel pacchetto anche loro, o devono già esistere.
- Il pacchetto crea solo i tipi che mancano: un tipo già installato non viene modificato (si cambia dallo Studio).

## Permessi sulle anagrafiche (`permessi-anagrafiche.json`, non viaggia nel pacchetto)

```json
{ "associazioni": [
    { "azione": "MODIFICA", "tipoSoggetto": "GRUPPO", "soggetto": "ufficio-cultura" },
    { "azione": "SCEGLIE",  "tipoSoggetto": "GRUPPO", "soggetto": "esterni" }
] }
```
`VEDE` (schede e dati), `MODIFICA` (crea, modifica, cessa; comprende VEDE), `ESPORTA` (CSV), `SCEGLIE` (solo id e
nome, per scegliere nei form: è l'unica che vale per gli utenti esterni, gruppi `esterni` o `org:<id>`).
Senza regole le schede le vede solo l'amministratore: chi compila un form con un campo anagrafica deve avere almeno
SCEGLIE. `script/installa.py` le applica con `PUT /api/admin/anagrafiche/{codice}/permessi`.

## Permessi sul processo (`permessi.json`, viaggia nel pacchetto)

```json
[
  { "azione": "AVVIA",   "tipoSoggetto": "GRUPPO", "soggetto": "ufficio-cultura", "ambito": "TUTTE" },
  { "azione": "VEDE",    "tipoSoggetto": "GRUPPO", "soggetto": "dirigenti",        "ambito": "TUTTE" },
  { "azione": "VEDE",    "tipoSoggetto": "GRUPPO", "soggetto": "ufficio-cultura",  "ambito": "STRUTTURA" },
  { "azione": "ESPORTA", "tipoSoggetto": "RUOLO",  "soggetto": "auditor",          "ambito": "TUTTE" }
]
```
- `AVVIA`: se ce n'è almeno una, vale al posto di `candidateStarterGroups` del BPMN.
- `VEDE` con `ambito`: `TUTTE`, `STRUTTURA` (pratiche della struttura dell'utente), `STRUTTURA_E_SOTTO`.
  Senza regole VEDE la pratica la vedono chi l'ha avviata, chi ne lavora un'attività, l'amministratore e i ruoli
  con `pratiche.vedi-tutte` (auditor).
- `ESPORTA`: esportazione CSV dell'elenco pratiche.
- All'importazione le regole si applicano solo se il processo non ne ha (non si tolgono accessi in uso) e quelle su
  ruoli inesistenti si tralasciano.

## Ruoli applicativi

Predefiniti: `amministratore`, `analista`, `gestore-utenti`, `auditor`. Un ruolo nuovo (es. `dirigente` per
`ruoliVisibili`) si crea in Amministrazione → Ruoli o con `POST /api/admin/ruoli`: va nella checklist di
installazione. Gruppi ≠ ruoli: i gruppi decidono chi lavora le attività, i ruoli cosa si può fare nell'applicazione
e quali campi riservati si vedono.

## Utenti esterni, organizzazioni e parti

- Utente esterno (`tipo: ESTERNO`): cittadino, professionista, impresa. Nessun ruolo, gruppi automatici `esterni`
  e `org:<id>`; vede solo le pratiche di cui è parte e i campi con `visibileAlleParti: true`.
- Se un esterno avvia la pratica, è la parte `richiedente`: le attività `candidateGroups="parte:richiedente"` le
  lavora lui (es. "Integra la domanda" dal portale).
- Organizzazione esterna come parte: campo `{"type": "organizzazione", "parte": "controinteressato"}` nel form →
  attività `candidateGroups="parte:controinteressato"` lavorate dagli utenti di quell'organizzazione. Il referente
  dell'organizzazione gestisce le persone della sua organizzazione.
- Per un processo con il portale degli esterni: i campi che devono vedere hanno `visibileAlleParti`, i documenti per
  loro stanno in un form con `documentiVisibilita: "parti"`.

## Strutture

`Organizzazione` interna in albero (uffici, settori). La pratica prende la struttura di chi la avvia, se ne ha una
sola. Serve alle regole `VEDE` con ambito `STRUTTURA`. Si configurano in Amministrazione → Strutture.
