# Collaudo: scenari, prova automatica, checklist per l'analista

## Dove si prova

Su un Orditogram di sviluppo o collaudo, mai in produzione (il collaudo crea utenti, schede e pratiche di prova e
anticipa i timer nel database). In locale:

```bash
# PostgreSQL con database e utente "orditogram" (vedi CLAUDE.md), poi:
cd backend && ./mvnw -q -DskipTests package && java -jar target/orditogram-*.jar &
# utenti demo attivi (app.demo.utenti), admin/admin, servizio di prova /api/demo/protocollo
```

## collaudo/scenari.json

```json
{
  "password": "password",
  "utenti": { "operatori": "mario", "responsabili": "anna", "avvio": "mario" },
  "schede": {
    "associazioni": [ { "denominazione": "Pro Loco Borgo", "codiceFiscale": "90012345678" } ]
  },
  "scenari": [
    {
      "nome": "Domanda accolta",
      "descrizione": "Percorso principale: domanda completa, ammessa e liquidata.",
      "passi": [
        { "azione": "avvia", "dati": { "associazione": "@scheda:associazioni:0", "importo": "3000" } },
        { "azione": "controlla", "attivita": "Verifica la domanda", "dati": { "numeroProtocollo": "*" } },
        { "azione": "completa", "attivita": "Verifica la domanda", "dati": { "esitoVerifica": "Ammissibile" } },
        { "azione": "messaggio", "nome": "rinunciaRichiedente", "dati": { "motivoRinuncia": "..." } },
        { "azione": "timer", "elemento": "termine60" }
      ],
      "atteso": { "conclusa": true, "fine": "fineAccolta", "eventi": ["EMAIL"] }
    }
  ]
}
```

- `utenti`: gruppo → username che lavora le attività di quel gruppo; `avvio` = chi avvia. I gruppi non elencati
  ricevono un utente creato apposta (`collaudo-<gruppo>`, con `password`). Con gli utenti demo di Orditogram:
  `mario` (operatori), `anna` (operatori, responsabili), `laura` (dirigenti), `paolo`/`chiara` (analisti),
  `giulia` (revisori), `marco` ed `elena` (esterni).
- `schede`: schede di esempio create prima degli scenari; nei dati `"@scheda:<tipo>:<indice>"` diventa l'id.
- Passi:
  - `avvia`: `utente` (facoltativo), `dati` del form di avvio (i numeri come testo, le date `AAAA-MM-GG`, le
    tabelle come liste di oggetti), `allegati` (file in `collaudo/`; se il form li vuole obbligatori e non ne indichi,
    ne allega uno di prova).
  - `completa`: `attivita` (nome o id), `dati`, `utente` (facoltativo: altrimenti quello del gruppo).
    Nei `dati`, `"@dato:<nome>"` diventa il valore attuale di quel dato della pratica: serve quando un servizio
    genera un valore che la persona poi riscrive (un codice, un numero di protocollo).
    Aspetta che l'attività compaia nella scrivania dell'utente: verifica anche che sia assegnata al gruppo giusto.
  - `messaggio`: `nome`, `dati`; lo registra l'utente indicato (di solito chi ha avviato) dalla scheda della pratica.
  - `timer`: `elemento` = id del timer nel BPMN; lo fa scattare subito (serve `--db`).
  - `controlla`: `attivita` (attività correnti, nome o lista), `conclusa` (true/false), `fine` (id della fine
    raggiunta), `passa` (id di passi percorsi), `dati` (valori attesi, `"*"` = compilato), `eventi` (tipi nello
    storico: `AVVIO`, `PRESA_IN_CARICO`, `COMPLETAMENTO`, `DOCUMENTO`, `NOTIFICA`, `SCADENZA`, `EMAIL`,
    `CHIAMATA_API`, `ERRORE`, `MESSAGGIO`, `PARTE`; i solleciti risultano come `SCADENZA`). `visibileA` / `nonVisibileA`: utenti (o
    `gruppo:<nome>`, l'utente di prova del gruppo) che devono o non devono poter aprire la pratica. Aspetta fino a 25 secondi che diventi vero.
- `atteso`: controllo finale dello scenario, stessa forma di `controlla`.
- `variante`: `{"testo nel BPMN": "testo nuovo"}`. Per quello scenario si pubblica una versione modificata del
  processo, poi si ripubblica quella vera. Serve per le strade di errore: `{"api/demo/protocollo\"":
  "api/demo/protocollo?simula=errore\""}` fa fallire il servizio di prova.
- In `controlla.dati` i valori sono quelli che mostra la scheda della pratica: per un campo anagrafica il nome della
  scheda, per una casella "Sì"/"No", per una tabella "N righe". I dati `<campo>_titolo`/`<campo>_dati` non si vedono
  lì (ma ci sono per BPMN, email e form).

## Quali scenari scrivere

Uno scenario per ogni strada che porta a una fine diversa, più uno per ogni scadenza, sollecito, messaggio e
strada di errore. Obiettivo: copertura completa del diagramma nel report (nessun passo "mai percorso"). Per la
strada di errore di un'API usa una `variante` che punta al servizio di prova con `?simula=errore`. I solleciti con ripetizione (`timeCycle`) si provano con `timer` come gli altri.

## Lanciare

```bash
python3 script/collaudo.py <cartella> --pubblica --db "postgresql://orditogram:orditogram@localhost/orditogram"
```
Scrive `collaudo/report.md` (esito, passi, copertura) e `collaudo/report.json`. Un fallimento dice cosa si
aspettava e com'era la pratica (attività correnti, passi fatti, dati, eventi): correggi il processo, il form o lo
scenario e rilancia finché è tutto verde. Non consegnare con scenari falliti.

## Checklist di collaudo per l'analista (`collaudo/checklist.md`)

Il collaudo automatico prova il motore e le regole; l'analista prova quello che vede una persona. Una riga per
ogni scenario e per ogni schermata, nella forma:

| # | Entra come | Fai | Devi vedere | ✓ |
|---|---|---|---|---|
| 1 | mario (operatori) | Nuova pratica → Contributi per eventi, compila con i dati della riga A, allega un PDF | La pratica in "Pratiche" con il numero di protocollo nei dati | ☐ |

Aggiungi in fondo: dati di prova da usare (tabella con i valori, già pronti da copiare), cosa non è stato provato in
automatico e perché, cosa serve dal cliente prima della produzione (credenziali, indirizzi email veri, gruppi del
provider di identità, calendario delle festività).
