# Installazione del cliente: parole, nome, logo, attori della demo

Una soluzione si consegna finita: il cliente la installa sui suoi server o la mostra ai suoi utenti. Quello che
vedono deve parlare la loro lingua e non mostrare niente di Orditogram che non serve.

## Parole (`glossario.json`, viaggia nel pacchetto)

Le parole dell'interfaccia vengono dal glossario dell'installazione. La soluzione porta le sue:

```json
{
  "pratica":  { "singolare": "domanda", "plurale": "domande", "genere": "F" },
  "processo": { "singolare": "bando",   "plurale": "bandi",   "genere": "M" },
  "esterno":  { "singolare": "impresa", "plurale": "imprese", "genere": "F" }
}
```

| Termine | Che cos'è | Predefinito | Esempi |
|---|---|---|---|
| `pratica` | il caso che si lavora | pratica | domanda, richiesta, ticket, istanza |
| `processo` | il modello da cui nasce (nelle schermate di chi lo usa) | processo | bando, servizio, procedimento |
| `esterno` | chi entra da fuori | cittadino | impresa, cliente, fornitore |
| `ente` | chi usa l'applicazione | ente | azienda, istituto, comune |
| `ufficio` | il personale visto da fuori | Ufficio | Sede, Assistenza, Segreteria |

- Singolare e plurale da 1 a 60 caratteri, solo lettere, spazi, apostrofi e trattini; genere `M` o `F` (serve agli
  articoli: "la domanda", "il bando", "l'impresa"). `verifica.py` usa le regole e i messaggi del backend.
- All'importazione le parole valgono **solo per i termini ancora predefiniti**: un pacchetto non cambia le scelte
  già fatte dall'installazione. Le applica solo l'amministratore (Studio → Glossario e logo); se importa un
  analista, la risposta lo dice.
- Le parole valgono per tutta l'installazione: un'installazione, una soluzione del cliente.
- Nei form, nei nomi delle attività e nei testi delle email scrivi già le parole del cliente: il glossario cambia le
  parti fisse dell'interfaccia, non i testi che scrivi tu.

## Nome e logo

Nome dell'applicazione e logo si impostano da **Studio → Glossario e logo** (solo l'amministratore): il nome va nel
menu, nella pagina di accesso e nel titolo della finestra; il logo è un'immagine PNG, JPEG o WebP fino a 512 KB
(niente SVG: può contenere codice). Senza, restano Orditogram e il suo marchio. Non viaggiano nel pacchetto: sono
dell'installazione. Nella checklist di consegna scrivi "Chiedere al cliente nome e logo".

## Niente esempi dal cliente

I processi di esempio di Orditogram ("Hello World BPMN", "Pratica") esistono solo con `app.demo.processi: true`
(spento di base e vietato dal profilo `produzione`). Un'installazione che li aveva li toglie all'avvio: si
cancellano se non hanno pratiche, altrimenti non si avviano più. Un cliente vede solo le soluzioni importate.
Gli utenti esterni vedono solo i processi aperti a loro (gruppo `esterni` o regola AVVIA): un processo senza
indicazioni lo avvia solo il personale.

## Attori della demo (`utenti-prova.json`, viaggia nel pacchetto)

Per le demo e il collaudo a mano, un utente per ogni ruolo della soluzione:

```json
[
  { "username": "marco", "nome": "Marco", "cognome": "Ferri", "ruolo": "Impresa richiedente", "esterno": true },
  { "username": "istruttore.toscana", "nome": "Sara", "cognome": "Neri", "ruolo": "Istruttore, sede Toscana",
    "gruppi": ["sede-toscana"] }
]
```

- `username` in minuscolo (lettere, cifre, `.`, `_`, `-`), mai `admin`; `ruolo` come lo chiama il cliente (al
  massimo 80 caratteri); `gruppi` quelli delle attività; `esterno: true` per i portali (i gruppi `esterni` e
  `org:<id>` sono automatici e non si scrivono).
- L'importazione dell'amministratore crea gli utenti mancanti con la password `password`, **solo se gli utenti di
  prova sono accesi** (`app.demo.utenti`, mai in produzione). Un utente che esiste già resta com'è: si aggiunge solo
  il suo ruolo agli accessi rapidi.
- La pagina di accesso mostra gli **accessi rapidi** (un clic, si entra con quel ruolo) solo se valgono insieme:
  utenti di prova accesi, accesso con password, profilo non di produzione, indirizzo `localhost`.
- `verifica.py` avvisa se un gruppo delle attività non ha nessun attore: chi prova non vedrebbe quel passo.
