# Form

Fonte di verità: `form/FormService.validate`, `form/ValoriForm`, `form/RegoleCampo` (backend) e
`components/DynamicForm.jsx`, `lib/formRegole.js`, `lib/formCondizioni.js` (frontend). Un file per form:
`form/<formKey>.json`, dove `formKey` (lettere, numeri, `-`, `_`) è quello del BPMN.

## Struttura

```json
{
  "title": "Domanda di contributo",
  "description": "Una frase che dice a chi compila cosa serve e cosa allegare.",
  "fields": [ ... ],
  "documenti": "carica",
  "documentiObbligatori": true,
  "documentiVisibilita": "interna",
  "submitLabel": "Invia la domanda"
}
```
- `documenti`: `carica` (si allegano file), `lettura` (si vedono i documenti della pratica), `modifica`
  (si possono aggiungere versioni). Omesso = nessun pannello documenti.
- `documentiObbligatori: true` con `carica`: almeno un file.
- `documentiVisibilita`: `interna` (predefinito) o `parti` (i documenti caricati qui li vedono le parti esterne).

## Campi

```json
{ "name": "importo", "label": "Importo richiesto (€)", "type": "number", "required": true,
  "min": 100, "max": 20000, "help": "Al massimo l'80% delle spese ammissibili." }
```
| Proprietà | Significato |
|---|---|
| `name` | nome del dato nella pratica (camelCase, inizia con una lettera, unico nel form). Lo stesso nome in più form = lo stesso dato |
| `label` | etichetta per chi compila |
| `type` | `text`, `textarea`, `number`, `date`, `select`, `checkbox`, `tabella`, `organizzazione`, `anagrafica` (omesso = `text`) |
| `required` | obbligatorio (se il campo è nascosto da una condizione non lo è) |
| `readonly` | mostra il dato già nella pratica senza farlo modificare (riepilogo nelle attività successive) |
| `options` | per `select`: elenco di testi; le condizioni del BPMN li confrontano alla lettera |
| `titolo: true` | il valore diventa il titolo della pratica (uno per form, nel form di avvio) |
| `help` | testo d'aiuto sotto il campo |
| `placeholder`, `default` | esempio nel campo vuoto, valore iniziale |
| `precompila: false` | non riproporre il valore precedente (utile quando un'attività si ripete in un ciclo) |
| `min`, `max` | numeri; per le date `AAAA-MM-GG` oppure `"oggi"` |
| `minLunghezza`, `maxLunghezza` | testi |
| `formato` | testi: `email`, `codiceFiscale`, `partitaIva`, `cap`, `telefono`, `iban`, `espressione` (+ `espressione`, `messaggioFormato`) |
| `visibileSe` | `{"campo": "tipoRichiedente", "valori": ["Associazione"]}`: il campo si vede solo per quei valori; per una casella `["true"]` |
| `visibileAlleParti: true` | il dato lo vedono anche gli utenti esterni parti della pratica |
| `ruoliVisibili` | `["dirigente"]`: dato riservato al personale con uno di questi ruoli |

I formati `codiceFiscale`, `partitaIva`, `cap` e `telefono` richiedono il modulo *Italia* (acceso per default): se
l'installazione lo ha spento, il backend rifiuta il form con "il formato \"X\" richiede il modulo Italia". Per
clienti fuori dall'Italia usa `espressione`.

**Sezioni** (non sono dati): `{ "type": "sezione", "label": "Dati dell'evento", "help": "..." }`. Niente condizioni.

**Tabelle di righe**:
```json
{ "name": "spese", "label": "Piano delle spese", "type": "tabella", "required": true, "minRighe": 1, "maxRighe": 30,
  "colonne": [
    { "name": "voce", "label": "Voce", "type": "text", "required": true },
    { "name": "importo", "label": "Importo (€)", "type": "number", "required": true, "min": 0 }
  ] }
```
Colonne: `text`, `number`, `date`, `select`, `checkbox` (niente `textarea`), al massimo 20, regole come i campi.
Il valore è una lista di oggetti; le righe vuote si tolgono. Una tabella non fa da titolo e non comanda condizioni.

**Organizzazione** (`type: "organizzazione"`): sceglie un'organizzazione esterna (ente, studio, impresa) dall'elenco
di Amministrazione → Organizzazioni. Con `"parte": "controparte"` l'organizzazione diventa parte della pratica: i suoi
utenti esterni la vedono e lavorano le attività `candidateGroups="parte:controparte"`. Una parte per campo.

**Anagrafica** (`type: "anagrafica"`, `"tipoAnagrafica": "associazioni"`): sceglie una scheda di quel tipo, con la
ricerca. Il tipo deve esistere (nel pacchetto o già installato). Nella pratica finiscono `<campo>` (id della
scheda), `<campo>_titolo` e `<campo>_dati` (fotografia dei dati della scheda). `"creaScheda": true` permette di
creare la scheda dal form se non c'è.

## Regole di buona fattura

- Form di avvio: i dati che servono per tutto il processo, il campo `titolo`, sezioni se più di 6-7 campi.
- Form delle attività: in alto, in `readonly`, i dati che servono per decidere (oggetto, richiedente, importo,
  esito dei passi precedenti); sotto, solo quello che l'attività decide. Un campo `select` per ogni decisione che il
  BPMN legge, con opzioni brevi e definitive ("Ammissibile", "Non ammissibile", "Serve integrazione").
- Una motivazione `textarea` obbligatoria accanto a ogni esito negativo (con `visibileSe` sull'esito).
- Lo stesso dato ha lo stesso `name` in tutti i form (così il valore passa e si ritrova nello storico).
- Etichette con l'unità di misura ("Importo (€)"), testi rivolti a persone non tecniche.
- Email e PEC del richiedente con `formato: "email"` se il processo gli scrive (`campo:<nome>` nell'emailConnector).
- Niente campi che nessuno usa: ogni dato raccolto va in una condizione, un'email, un documento o una decisione.
