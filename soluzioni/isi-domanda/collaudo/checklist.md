# Checklist di collaudo per l'analista: Bando ISI

Il collaudo automatico (`report.md`) ha provato il motore e le regole passando dalle API. Qui si prova quello che
vede una persona. Preparazione: Orditogram avviato con gli utenti di prova, servizio ISI avviato
(`java -jar target/servizio-isi.jar` in `servizio-isi/`) con `ISI_CALENDARIO_LIBERO=1 ISI_CALENDARIO_FILE=dati-prova/calendario-collaudo.json`
e un database nuovo, pacchetti
importati, credenziale `servizio-isi` configurata, utenti dei gruppi `sede-toscana`, `supporto-isi`, `direzione-isi`.

| # | Entra come | Fai | Devi vedere | ✓ |
|---|---|---|---|---|
| 1 | marco (impresa) | Nuova pratica → Bando ISI: domanda di finanziamento, codice fiscale `01234560785`, titolo a piacere | "Compila la domanda" nella scrivania, ragione sociale "Carpenterie Rossi S.r.l." già scritta | ☐ |
| 2 | marco | Apri "Compila la domanda": scorri i passi, cambia l'**Asse** tra 2, 3, 4 e 5.1 | compaiono solo i campi dell'asse scelto; l'Asse 5 mostra Bene 1 e la casella per il Bene 2 | ☐ |
| 3 | marco | Compila con i dati A, **Calcola e salva** | la domanda torna nella scrivania con "Punteggio 201 su soglia 130 · contributo 70.200,00 € · registrabile", dettaglio del punteggio e degli importi, elenco dei documenti | ☐ |
| 4 | marco | Togli una dichiarazione, **Calcola e salva** | in **Errori che bloccano la registrazione** "Manca la dichiarazione R-REQ-…" | ☐ |
| 5 | marco | Rimettila, **Registra la domanda** senza la casella di conferma | il form chiede la conferma | ☐ |
| 6 | marco | Registra con la conferma | la domanda sparisce dalla scrivania; nello storico la ricevuta PEC ("non inviata" senza posta) | ☐ |
| 7 | marco | Seconda bozza con lo stesso codice fiscale, Registra | torna con «L'impresa ha già registrato una domanda per questo Avviso» | ☐ |
| 8 | admin | Fai scattare il timer "Apertura del periodo del codice" (o aspetta la data) | marco ha "Acquisisci il codice identificativo"; dopo, "Invia il codice allo sportello" mostra il codice | ☐ |
| 9 | marco | Invia un codice sbagliato, poi quello giusto | "Codice non valido o già utilizzato", poi la presa in carico con l'ora al millisecondo | ☐ |
| 10 | direzione | Nuova pratica → Bando ISI: elenchi cronologici, PROVVISORIO, guarda l'anteprima, Pubblica | anteprima con Regione, Asse, posizione, importo troncato all'euro ed esito; la domanda di marco passa a "Carica i documenti" (serve `ORDITOGRAM_URL` e la chiave sul servizio) | ☐ |
| 11 | marco | "Incarica il tecnico" con il codice fiscale del legale rappresentante | torna con il motivo; poi con un altro tecnico va avanti | ☐ |
| 12 | supporto | "Abilita il tecnico sulla domanda": scegli lo studio | elena (studio) vede "Compila e firma la perizia" e solo i dati della domanda destinati alle parti | ☐ |
| 13 | elena (tecnico) | Compila la perizia, allega un P7M | la firma è verificata nel pannello documenti | ☐ |
| 14 | marco | "Carica i documenti": allega, scadenza del documento passata | il form rifiuta la data; con una data futura conferma | ☐ |
| 15 | sede-toscana | "Svolgi l'istruttoria": correggi i criteri, **Ricalcola** | torna con punteggio e importi aggiornati, giorni rimanenti | ☐ |
| 16 | sede-toscana | **Richiedi integrazione**, poi marco risponde | i giorni rimanenti dell'istruttoria tengono conto della sospensione | ☐ |
| 17 | sede-toscana | **Ammetti** con criteri sotto soglia | l'attività torna con il punteggio sotto 130 | ☐ |
| 18 | sede-toscana | **Respingi** con la motivazione | PEC con l'esito; pratica conclusa "Respinta" | ☐ |
| 19 | marco | Apri le tue pratiche | vedi solo le tue; non vedi i dati riservati della sede | ☐ |
| 20 | telefono | Ripeti il passo 3 da un telefono | il form è leggibile, nessuno scorrimento orizzontale | ☐ |

## Dati di prova

| Riga | Valori |
|---|---|
| A (TC-01) | ATECO 25.11, Impresa, INAIL, iscrizione CCIAA 01/03/2010, associazione al portale 10/01/2026, Toscana, ULA 8, fatturato 1.500.000, dipendenti 8 e 8, Asse 2 tipologia a, tasso 60, Ente bilaterale, RLS, ISO 45001, spese di progetto 100.000, spese tecniche 8.000, anticipo, tutte le dichiarazioni |
| B (TC-02) | come A ma ULA 60, fatturato 40.000.000, tasso 10, iscrizione 2005, Asse 1.1 tipologia b, parti sociali Nessuna, senza RLS e certificazioni → 89 punti |
| Imprese INAIL finte | `01234560785` Carpenterie Rossi, `02345670893` Azienda agricola Verdi (Asse 3 e 5), `03456780901` Falegnameria Bianchi (Asse 4) |
| Codici fiscali | legale rappresentante `RSSMRA70A01D612W`, tecnico `VRDLGU75B02H501X` |

## Non provato in automatico

- **Schermate**: il collaudo usa le API; vanno viste le 13 schermate (righe 2-20), in particolare il form lungo della
  domanda (155 campi) e le condizioni per asse.
- **Documenti obbligatori nelle attività** ("Carica i documenti", "Compila e firma la perizia"): il collaudo
  completa le attività senza allegati; controlla che l'interfaccia li chieda.
- **Avviso degli elenchi dal servizio ISI a Orditogram**: negli scenari il messaggio `esitoElenco` lo manda il
  collaudo; la consegna dal servizio (`ORDITOGRAM_URL`, chiave API) va provata alla riga 10.
- **Carico dello sportello** (5.000 invii al minuto): non misurato.
- **Disegno**: il diagramma è largo e generato in automatico; conviene ritoccarlo nello Studio.

## Prima della produzione serve dal cliente

- Indirizzo e certificato del servizio ISI, segreto `ISI_TOKEN`, chiave API per i messaggi.
- Casella PEC per l'invio (`spring.mail.*`).
- Provider di identità con SPID, CIE, CNS (Keycloak come broker) e come si riconoscono legali rappresentanti e delegati.
- Sedi INAIL: gruppi per Regione e persone; studi tecnici come organizzazioni (o il self-service della richiesta N2).
- Calendario, stanziamenti e bonus ATECO regionali veri del bando.
- Risposte alle domande aperte di `analisi.md`.
