# ISI Domanda – Specifica dei requisiti per generazione del software

Versione 1.0 del 26/11/2026 · Documento di test, autosufficiente

> **Scopo del documento.** Questo testo descrive un'applicazione web, chiamata qui **ISI Domanda**, che permette alle imprese di compilare, registrare e presentare la domanda di finanziamento del **Bando ISI 2025 di INAIL** (incentivi alle imprese per progetti di salute e sicurezza sul lavoro). È scritto per essere dato in input a uno strumento di generazione del codice: ogni requisito ha un codice, una descrizione e criteri di accettazione verificabili. Non è la specifica del sistema ufficiale gestito da IBM per INAIL: è una ricostruzione funzionale basata sul testo pubblico dell'Avviso ISI 2025, sull'Allegato criteri (Delibera CdA INAIL n. 187 del 19/11/2025) e sugli Allegati tecnici 1.1–5.
>
> **Istruzioni per lo strumento di generazione.**
> 1. Implementa tutti i requisiti con priorità **M** (Must). Quelli **S** (Should) se possibile, quelli **C** (Could) solo se richiesto.
> 2. Tutte le soglie, percentuali, punteggi e tabelle del capitolo 9 vanno caricate come **dati di configurazione** (tabelle del database o file di seed), mai scritte nel codice: cambiano a ogni annualità del bando.
> 3. I servizi esterni (autenticazione SPID/CIE/CNS, anagrafica INAIL, registro aiuti, PEC, firma digitale) vanno realizzati come **interfacce con un'implementazione finta (mock)** attivabile in configurazione.
> 4. Interfaccia utente in **italiano**. Date a video in formato gg/mm/aaaa, importi in euro con 2 decimali e separatore delle migliaia.
> 5. Produci anche i test automatici per i casi di prova del capitolo 11.

---

## Indice

1. Contesto e glossario
2. Attori e ruoli
3. Perimetro
4. Ciclo di vita della domanda (stati)
5. Requisiti funzionali – Area impresa
6. Requisiti funzionali – Area tecnico (perizia)
7. Requisiti funzionali – Back-office INAIL
8. Requisiti funzionali – Interfacce esterne
9. Regole di business e tabelle di configurazione
10. Modello dati
11. Casi di prova
12. Requisiti non funzionali
13. Punti aperti e ipotesi adottate

---

## 1. Contesto e glossario

INAIL pubblica ogni anno un Avviso pubblico (Bando ISI) che finanzia a fondo perduto progetti delle imprese per ridurre i rischi per la salute e la sicurezza dei lavoratori. Le risorse sono divise per **Regione** e per **Asse di finanziamento**. L'impresa compila online la domanda, il sistema calcola un **punteggio**: solo le domande che raggiungono la **soglia di 130 punti** possono essere registrate. Le domande registrate partecipano a uno **sportello informatico** (il cosiddetto *click day*) in cui l'ordine di arrivo decide chi rientra nei fondi. Chi rientra carica poi i documenti, e INAIL svolge l'istruttoria.

| Termine | Significato |
|---|---|
| Avviso / Bando | Il bando annuale ISI (qui: ISI 2025). Ogni dato di configurazione è legato a un'annualità. |
| Asse | Linea di finanziamento: 1.1, 1.2, 2, 3, 4, 5.1, 5.2 (vedi 9.1). |
| Tipologia di intervento | Tipo di progetto all'interno dell'asse (es. Asse 2, tipologia a: ancoraggi contro le cadute dall'alto). |
| Intervento aggiuntivo | Secondo intervento facoltativo, finanziato all'80%, ammesso solo per alcuni assi. |
| Bene (Asse 5) | Macchina o trattore agricolo acquistato; una domanda Asse 5 ha 1 o 2 beni. |
| ULA | Unità lavorative annue (numero di dipendenti a tempo pieno equivalenti), con un decimale. |
| Tasso medio di tariffa | Indicatore di rischio INAIL dell'impresa, in per mille (‰). |
| Soglia di ammissibilità | 130 punti. Sotto soglia la domanda non può essere registrata. |
| Registrazione | Salvataggio definitivo della domanda con il tasto «REGISTRA». Dopo la registrazione la domanda non si modifica più. |
| Codice identificativo | Codice univoco della domanda registrata, da usare il giorno dello sportello informatico. |
| Sportello informatico | Finestra temporale in cui le imprese inviano il codice identificativo; conta l'ordine di arrivo. |
| Elenchi NCD | Elenchi delle domande ammesse direttamente al caricamento documenti, senza sportello, quando i fondi di Regione+Asse bastano per tutti. |
| Modulo A | Domanda in PDF generata dal sistema, da firmare dal legale rappresentante. |
| Perizia asseverata (Modulo B) | Relazione tecnica compilata online da un professionista abilitato (non per Asse 1.2, che usa una dichiarazione B1.2). |
| ATECO | Classificazione delle attività economiche (versione 2025). |
| PEC | Posta elettronica certificata, canale ufficiale delle comunicazioni. |

---

## 2. Attori e ruoli

| Codice | Attore | Descrizione | Cosa può fare |
|---|---|---|---|
| ATT-01 | Legale rappresentante | Titolare o legale rappresentante dell'impresa, autenticato. | Tutto sulle domande della propria impresa, inclusa registrazione, acquisizione codice, invio allo sportello, firma del Modulo A. |
| ATT-02 | Delegato / consulente | Persona autenticata con delega dell'impresa. | Compila e salva la domanda. **Non** può registrarla né inviarla allo sportello se la delega non lo prevede (campo `delega.puo_registrare`). |
| ATT-03 | Tecnico asseveratore | Professionista iscritto a un ordine, autenticato, incaricato dall'impresa. | Compila e firma la perizia asseverata della domanda per cui è incaricato. Non vede le altre domande. |
| ATT-04 | Operatore sede INAIL | Funzionario della sede territoriale. | Consulta le domande della propria regione, svolge l'istruttoria, chiede integrazioni, registra gli esiti. |
| ATT-05 | Amministratore bando | Funzionario della Direzione centrale. | Configura annualità, calendario, stanziamenti, tabelle di punteggio; avvia le elaborazioni degli elenchi. |
| ATT-06 | Sistema | Processi automatici pianificati. | Chiusure di fase, calcolo elenchi, decadenze, notifiche. |

Regola generale (RNF-SEC-03): ogni utente vede solo i dati per cui ha titolo; ogni azione viene registrata nel registro di audit.

---

## 3. Perimetro

**Nel perimetro**
- Accesso autenticato e scelta dell'impresa per cui operare.
- Compilazione guidata della domanda a passi, con calcolo in tempo reale di punteggio e importi.
- Salvataggio in bozza, più bozze per impresa, una sola domanda registrabile.
- Verifica della soglia e dei vincoli, registrazione, rilascio del codice identificativo.
- Sportello informatico con ordine cronologico.
- Elenchi cronologici (NCD, provvisori, definitivi) e redistribuzione delle risorse.
- Caricamento dei documenti, Modulo A, perizia online, ricevuta, decadenza e istanza di revoca.
- Back-office INAIL: configurazione del bando e istruttoria di base.
- Interfaccia (API) per ricevere una bozza precompilata da un sistema esterno (simulatore).

**Fuori perimetro**
- Rendicontazione delle spese ed erogazione dei pagamenti (fase successiva alla concessione).
- Gestione contabile, protocollo informatico reale, integrazione reale con SPID/PEC/firma (solo mock).

---

## 4. Ciclo di vita della domanda (stati)

| Stato | Significato | Transizioni consentite |
|---|---|---|
| `BOZZA` | In compilazione, modificabile. | → `REGISTRATA` (azione REGISTRA, se valida) · → `ANNULLATA` (cancellazione da parte dell'impresa) |
| `REGISTRATA` | Salvata definitivamente, non modificabile. | → `CODICE_RILASCIATO` |
| `CODICE_RILASCIATO` | L'impresa ha acquisito il codice identificativo. | → `INVIATA_SPORTELLO` · → `NCD` (se l'Asse/Regione non richiede sportello) |
| `INVIATA_SPORTELLO` | Codice inviato allo sportello, con data e ora al millisecondo. | → `IN_POSIZIONE_UTILE` · → `NON_AMMISSIBILE_FONDI` |
| `NCD` | Ammessa direttamente al caricamento documenti. | → `DOCUMENTI_INVIATI` · → `DECADUTA` |
| `IN_POSIZIONE_UTILE` | Rientra negli elenchi provvisori entro i fondi. | → `DOCUMENTI_INVIATI` · → `DECADUTA` |
| `NON_AMMISSIBILE_FONDI` | Fuori dai fondi. Può subentrare dopo la redistribuzione. | → `SUBENTRATA` · resta definitiva negli elenchi definitivi |
| `SUBENTRATA` | Entrata in posizione utile dopo la redistribuzione. | → `DOCUMENTI_INVIATI` · → `DECADUTA` |
| `DOCUMENTI_INVIATI` | Caricamento completato, ricevuta rilasciata. | → `IN_ISTRUTTORIA` |
| `DECADUTA` | Documenti non inviati nei termini. | → `DOCUMENTI_INVIATI` solo se l'istanza di revoca è accolta |
| `IN_ISTRUTTORIA` | Verifica tecnico-amministrativa della sede INAIL. | → `INTEGRAZIONE_RICHIESTA` · → `PREAVVISO_RIGETTO` · → `AMMESSA` · → `AMMESSA_PARZIALMENTE` |
| `INTEGRAZIONE_RICHIESTA` | La sede ha chiesto documenti; 30 giorni per rispondere. | → `IN_ISTRUTTORIA` |
| `PREAVVISO_RIGETTO` | 10 giorni per presentare osservazioni. | → `IN_ISTRUTTORIA` · → `RESPINTA` |
| `AMMESSA` / `AMMESSA_PARZIALMENTE` / `RESPINTA` | Esiti finali dell'istruttoria. | Nessuna (stati finali in questo perimetro) |
| `ANNULLATA` | Bozza cancellata o domanda annullata per violazione delle regole tecniche. | Nessuna |

Ogni transizione registra: stato precedente, stato nuovo, data e ora, utente o processo, motivo.

---

## 5. Requisiti funzionali – Area impresa

Formato: **RF-nn · Titolo** · Priorità (M/S/C) · Attori. Segue la descrizione e i criteri di accettazione (CA).

### 5.1 Accesso

**RF-01 · Accesso autenticato** · M · ATT-01, ATT-02, ATT-03
L'utente accede con identità digitale (SPID, CIE o CNS) tramite un servizio di autenticazione esterno (mock in sviluppo, che restituisce codice fiscale, nome, cognome).
- CA-01: senza autenticazione nessuna pagina dell'area è raggiungibile; l'utente viene reindirizzato alla pagina di accesso.
- CA-02: la sessione scade dopo 30 minuti di inattività (parametro configurabile); i dati inseriti fino all'ultimo salvataggio automatico restano.
- CA-03: il mock permette di scegliere fra almeno 5 utenti di prova con ruoli diversi.

**RF-02 · Scelta dell'impresa** · M · ATT-01, ATT-02
Dopo l'accesso l'utente vede le imprese per cui può operare (come legale rappresentante o delegato) e ne sceglie una.
- CA-01: l'elenco mostra ragione sociale, codice fiscale, partita IVA e ruolo dell'utente.
- CA-02: se l'utente ha una sola impresa, viene selezionata automaticamente.
- CA-03: un'impresa non associata all'utente non è mai selezionabile, nemmeno modificando l'URL (risposta 403).

**RF-03 · Verifica della registrazione al portale** · M · ATT-06
L'impresa deve essere registrata al portale e associata a una posizione assicurativa almeno 5 giorni lavorativi prima della chiusura della compilazione.
- CA-01: se la data di associazione è successiva al limite, la registrazione della domanda (RF-20) è bloccata con il messaggio «Registrazione al portale avvenuta oltre il termine previsto dall'art. 12 dell'Avviso».
- CA-02: il calcolo dei giorni lavorativi esclude sabati, domeniche e festività nazionali configurate.

### 5.2 Gestione delle domande

**RF-04 · Cruscotto dell'impresa** · M · ATT-01, ATT-02
Pagina iniziale con le domande dell'impresa per l'annualità corrente e le precedenti.
- CA-01: per ogni domanda mostra numero, asse, titolo del progetto, stato, punteggio, contributo richiesto, data ultima modifica.
- CA-02: mostra il calendario del bando (RF-50) con la fase in corso evidenziata.
- CA-03: il pulsante «Nuova domanda» è disponibile solo durante la fase di compilazione.

**RF-05 · Più bozze, una sola domanda** · M · ATT-01, ATT-02
L'impresa può avere più bozze contemporanee (per confrontare ipotesi), ma può registrare una sola domanda per annualità: una sola Regione, un solo Asse, una sola unità produttiva.
- CA-01: alla registrazione di una domanda, il sistema impedisce la registrazione di qualsiasi altra bozza della stessa impresa e annualità con il messaggio «L'impresa ha già registrato una domanda per questo Avviso».
- CA-02: il vincolo vale per codice fiscale dell'impresa e viene garantito anche a livello di database (vincolo di unicità su impresa + annualità per gli stati diversi da `BOZZA` e `ANNULLATA`).
- CA-03: le altre bozze restano consultabili in sola lettura.

**RF-06 · Duplica bozza** · S · ATT-01, ATT-02
- CA-01: «Duplica» crea una nuova bozza con tutti i dati copiati e un nuovo numero.

**RF-07 · Cancella bozza** · M · ATT-01, ATT-02
- CA-01: solo le domande in `BOZZA` si possono cancellare; viene chiesta conferma; lo stato diventa `ANNULLATA` (cancellazione logica).

**RF-08 · Salvataggio** · M · ATT-01, ATT-02
- CA-01: pulsante «Salva» su ogni passo; salvataggio automatico ogni 60 secondi se ci sono modifiche.
- CA-02: il salvataggio è consentito anche con dati incompleti o con errori; gli errori bloccano solo la registrazione.
- CA-03: due utenti sulla stessa bozza: il secondo salvataggio su una versione superata viene rifiutato con un messaggio che invita a ricaricare (controllo di concorrenza ottimistica).

### 5.3 Compilazione guidata

La compilazione è divisa in passi. In ogni passo un pannello laterale mostra in tempo reale **punteggio parziale, punteggio totale, soglia (130), contributo calcolato** e l'elenco degli errori bloccanti. L'utente può muoversi liberamente tra i passi.

**RF-10 · Passo 1 – Dati dell'impresa** · M
Campi: ragione sociale, codice fiscale, partita IVA (facoltativa per gli Enti del Terzo settore), forma giuridica, codice ATECO 2025, data di costituzione, data di iscrizione alla CCIAA (o al RUNTS / Albo), sede legale, codice ditta INAIL, PEC, tipo di soggetto (impresa / ETS iscritto al RUNTS / ETS iscritto al Registro imprese / ONLUS), impresa agricola primaria (sì/no), IAP (sì/no), impresa associata o collegata (sì/no), rapporto assicurativo (INAIL / INPS gestione agricola / nessuno).
- CA-01: i dati anagrafici sono precompilati dal servizio anagrafica INAIL (RF-71) e restano modificabili, tranne codice fiscale.
- CA-02: codice fiscale validato (16 caratteri persona fisica o 11 cifre); partita IVA di 11 cifre con controllo del carattere di controllo.
- CA-03: PEC obbligatoria e in formato e-mail valido.
- CA-04: ATECO scelto da un elenco con ricerca per codice e descrizione.

**RF-11 · Passo 2 – Unità produttiva e dimensione** · M
Campi: indirizzo e Regione dell'unità produttiva oggetto dell'intervento; ULA dell'anno di riferimento (1 decimale); fatturato annuo e totale di bilancio; numero di dipendenti dell'impresa e dell'unità produttiva; attività stagionale (sì/no) e data di inizio attività; ditta individuale senza dipendenti con titolare autonomo (sì/no); per le imprese associate o collegate, ULA e fatturato già sommati.
- CA-01: le ULA sono arrotondate matematicamente al primo decimale (R-ALL-01).
- CA-02: la classe dimensionale e il punteggio dimensionale (9.4) sono calcolati e mostrati subito.
- CA-03: il sistema calcola se l'impresa è **micro o piccola** (meno di 50 ULA e fatturato o totale di bilancio ≤ 10 milioni €) (R-DEST-06).
- CA-04: forza lavoro minima di 1 ULA (R-REQ-01), salvo ditta individuale senza dipendenti con titolare autonomo non IAP, o attività stagionale con ULA < 1 attiva da più di un anno alla data di pubblicazione (in quel caso le ULA valgono 1).
- CA-05: la Regione dell'unità produttiva determina lo stanziamento a cui concorre la domanda.

**RF-12 · Passo 3 – Scelta dell'asse e della tipologia** · M
L'utente sceglie l'asse; il sistema mostra **solo gli assi ammessi** per l'impresa secondo le regole 9.2, e per ogni asse non ammesso il motivo.
- CA-01: Assi 1.1, 1.2, 2, 3: una sola tipologia selezionabile (R-ALL-04).
- CA-02: Asse 4: più tipologie selezionabili; conta la tipologia con punteggio maggiore (R-ALL-05).
- CA-03: Asse 5: da 1 a 2 beni; per ogni bene una misura e un fattore di rischio (R-ALL-07) e le combinazioni ammesse: trattore + macchina, macchina con motore + macchina senza motore, 2 macchine senza motore.
- CA-04: ETS iscritto al RUNTS: solo Asse 1.1 tipologia d (R-DEST-02).
- CA-05: intervento aggiuntivo selezionabile solo per gli assi che lo prevedono (9.5); per gli Assi 1.1 e 4 l'aggiuntivo «Adozione SGSL ISO 45001» rende non selezionabile il punteggio della certificazione ISO 45001 (R-ALL-06).
- CA-06: cambiare asse dopo aver inserito i dati del progetto chiede conferma e svuota i dati non compatibili con il nuovo asse.

**RF-13 · Passo 4 – Dati del progetto** · M
Campi comuni: titolo (max 200 caratteri), descrizione (max 4.000), lavorazione e rischio interessati.
Campi per asse:
- Assi 1.1, 2, 4 con macchine: per ogni macchina descrizione, marca e modello, prezzo di preventivo, prezzo di listino del fabbricante (o URL), trattore (sì/no), listino degli accessori, potenza in kW, bene sostituito (anno, proprietà da almeno 3 anni al 31/12/2025, rottamazione o permuta).
- Asse 3: superficie in mq della copertura, superficie in mq di controsoffitto/sottocopertura, spese per parapetti, ancoraggi e scale, immobile in disponibilità da almeno 3 anni, immobile locato (sì/no) e quota usata dall'impresa.
- Asse 1.2: complessità del macrosettore (ricavata dall'ATECO, 9.8), numero di dipendenti di tutta l'impresa, adozione di SGSL/MOG negli ultimi 3 anni (sì/no), concessioni Asse 1.2 nelle 3 edizioni precedenti (sì/no).
- Asse 5: per ogni bene misura, fattore di rischio, tipo (trattore, macchina con motore, macchina senza motore, mietitrebbiatrice), anno di immissione sul mercato del bene sostituito, rottamazione o permuta, ricavo della permuta, noleggio con patto d'acquisto (sì/no); per l'Asse 5.2 i requisiti del giovane agricoltore (età sotto 41 anni alla chiusura della compilazione, formazione o competenze, ruolo di capo azienda) secondo la forma giuridica (R-DEST-08).
- CA-01: i controlli di 9.6 e 9.7 sono applicati a ogni modifica e mostrati vicino al campo.
- CA-02: per l'Asse 5 il punteggio e l'esito sono calcolati **per bene**; è ammesso un solo bene se l'altro non raggiunge la soglia.
- CA-03: mietitrebbiatrice: fattori di rischio 2c e 2d non selezionabili (R-ALL-08).

**RF-14 · Passo 5 – Criteri di punteggio** · M
Campi: tasso medio di tariffa INAIL (‰) o «non definito»; condivisione con le parti sociali (nessuna / Ente bilaterale o Organismo paritetico / due o più parti sociali); informativa o condivisione con RLS/RLST (sì/no); certificazioni possedute (9.4.5); bonus ATECO regionale (calcolato, non modificabile).
- CA-01: per l'Asse 4 il punteggio di rischiosità deriva dall'ATECO (tabella 9.4.7) e il campo del tasso è nascosto.
- CA-02: per l'Asse 5 i criteri dimensione, rischiosità, certificazioni (tranne RLAQ) e bonus non sono mostrati.
- CA-03: imprese iscritte alla CCIAA dal 31/12/2023 in poi, Assi 1.1, 1.2, 2, 3: rischiosità fissa 4 punti, qualunque sia il tasso.
- CA-04: per ogni criterio selezionato il sistema mostra il documento che andrà caricato (es. Modulo E1, E2, certificato).

**RF-15 · Passo 6 – Piano economico** · M
Campi: spese di progetto (al netto IVA), spese tecniche, costo della perizia, spese dell'intervento aggiuntivo, IVA non recuperabile (sì/no con motivazione), richiesta di anticipo (sì/no).
- CA-01: il sistema calcola spesa ammissibile, contributo principale, contributo aggiuntivo, contributo totale, anticipo e fideiussione secondo le regole 9.5 e mostra il dettaglio del calcolo voce per voce.
- CA-02: ogni importo che supera un massimale viene ridotto al massimale e il sistema mostra un avviso con il motivo (es. «Spese tecniche ridotte a 10.000 €: massimo previsto dall'Allegato tecnico»).
- CA-03: con noleggio con patto d'acquisto l'anticipo non è richiedibile.

**RF-16 · Passo 7 – Dichiarazioni** · M
Una dichiarazione distinta (casella da spuntare) per ogni requisito della tabella 9.3 applicabile all'asse scelto, con il testo del requisito e l'articolo dell'Avviso.
- CA-01: compaiono solo le dichiarazioni applicabili (es. polizza catastrofale non richiesta alle imprese agricole art. 2135 c.c.; dichiarazioni Asse 5 solo per l'Asse 5).
- CA-02: finanziamenti ISI 2022, 2023, 2024 ottenuti: l'utente indica edizione e asse; se diverso dall'Asse 1.2 deve dichiarare di aver rinunciato via PEC (R-REQ-13), altrimenti la registrazione è bloccata.
- CA-03: senza tutte le dichiarazioni obbligatorie la registrazione è bloccata.

**RF-17 · Passo 8 – Riepilogo** · M
- CA-01: mostra tutti i dati inseriti, il dettaglio del punteggio per criterio (e per bene per l'Asse 5), gli importi e l'elenco dei documenti da preparare.
- CA-02: l'elenco degli errori bloccanti ha un link al campo da correggere.
- CA-03: pulsante «Scarica riepilogo PDF» con la dicitura «Documento non valido ai fini della presentazione».

### 5.4 Registrazione e codice identificativo

**RF-20 · Registrazione della domanda (REGISTRA)** · M · ATT-01 (e ATT-02 se abilitato)
- CA-01: il pulsante «REGISTRA» è attivo solo durante la fase di compilazione del calendario.
- CA-02: la registrazione è consentita solo se: punteggio ≥ 130 (per l'Asse 5 almeno un bene ≥ 130); contributo nei limiti (9.5); nessun errore bloccante; rapporto assicurativo coerente con l'asse (R-DEST-10, R-DEST-11, R-DEST-12); tutte le dichiarazioni spuntate; nessun'altra domanda registrata per l'impresa (RF-05); termine di RF-03 rispettato.
- CA-03: prima di registrare il sistema chiede conferma esplicita con il testo «Dopo la registrazione la domanda non sarà più modificabile».
- CA-04: alla registrazione viene salvata la data e ora al millisecondo (usata come criterio di precedenza, 9.10) e una fotografia immutabile di tutti i dati, del punteggio e degli importi, con la versione delle regole di calcolo usate.
- CA-05: lo stato passa a `REGISTRATA`; nessun campo è più modificabile da nessun ruolo dell'impresa.
- CA-06: viene generata la ricevuta di registrazione in PDF con numero domanda, data e ora, punteggio, contributo richiesto.

**RF-21 · Acquisizione del codice identificativo** · M · ATT-01
- CA-01: disponibile solo per domande `REGISTRATA` e solo nel periodo del calendario dedicato.
- CA-02: il codice è univoco, non prevedibile (almeno 12 caratteri alfanumerici, senza caratteri ambigui come 0/O, 1/I) e associato a una sola domanda.
- CA-03: il codice si mostra a video e si scarica in PDF; può essere visualizzato di nuovo finché non viene usato.
- CA-04: lo stato passa a `CODICE_RILASCIATO`.

### 5.5 Sportello informatico

**RF-25 · Invio allo sportello** · M · ATT-01
Nel giorno e nella fascia oraria pubblicati, l'impresa inserisce il codice identificativo e lo invia.
- CA-01: fuori dalla finestra di apertura (per Regione e Asse) l'invio è rifiutato con data e ora della prossima apertura.
- CA-02: l'ora di arrivo viene presa dall'orologio del server, al millisecondo, al momento della ricezione; non dal client.
- CA-03: al termine viene mostrato un messaggio che attesta la presa in carico, con data e ora registrate.
- CA-04: dopo l'invio il codice è annullato e non più utilizzabile; un secondo invio con lo stesso codice è rifiutato.
- CA-05: un codice inesistente o di un'altra impresa è rifiutato senza rivelare se esiste.
- CA-06: lo sportello deve reggere il carico della sezione 12 (RNF-PER-02); le richieste in coda conservano l'ora di arrivo effettiva.

**RF-26 · Regole tecniche dello sportello** · S · ATT-05
- CA-01: l'amministratore pubblica le regole tecniche (testo e PDF) almeno 7 giorni prima dell'apertura; il sistema impedisce di fissare un'apertura a meno di 7 giorni dalla pubblicazione.
- CA-02: limite configurabile di tentativi di invio per utente al minuto; oltre il limite le richieste sono respinte (protezione da automatismi).

### 5.6 Esiti, documenti e perfezionamento

**RF-30 · Consultazione degli elenchi** · M · tutti
- CA-01: pagina pubblica con gli elenchi pubblicati per Regione e Asse: posizione, numero domanda, ragione sociale, importo richiesto **troncato all'euro** (R-CALC-10), esito.
- CA-02: l'impresa vede nel cruscotto la propria posizione ed esito.

**RF-31 · Caricamento della documentazione** · M · ATT-01, ATT-02
Per le domande in `NCD`, `IN_POSIZIONE_UTILE` o `SUBENTRATA`, entro il termine comunicato (almeno 30 giorni dalla pubblicazione degli elenchi).
- CA-01: il sistema mostra la checklist dei documenti richiesti calcolata dai dati della domanda (asse, tipologia, criteri di punteggio scelti, macchine) secondo la tabella 9.9.
- CA-02: formati ammessi PDF e P7M; dimensione massima per file 10 MB (configurabile).
- CA-03: il Modulo A è generato dal sistema con i dati registrati e va ricaricato firmato digitalmente; il sistema verifica la presenza di una firma digitale valida (mock) e che il firmatario coincida con il legale rappresentante.
- CA-04: va caricata la copia di un documento di identità valido del legale rappresentante; la data di scadenza inserita deve essere successiva alla data di caricamento.
- CA-05: la perizia (Assi 1.1, 2, 3, 4, 5) non si carica come file: deve risultare compilata e firmata dal tecnico nella sua area (RF-40).
- CA-06: «Conferma invio» è attivo solo con tutti i documenti obbligatori presenti; dopo la conferma lo stato diventa `DOCUMENTI_INVIATI` e viene rilasciata la ricevuta di completamento del caricamento in PDF.

**RF-32 · Decadenza** · M · ATT-06
- CA-01: alla scadenza del termine, le domande in posizione utile senza conferma dell'invio passano a `DECADUTA`.
- CA-02: entro 10 giorni dalla scadenza viene inviata la comunicazione di decadenza alla PEC della domanda (mock).

**RF-33 · Istanza di revoca della decadenza** · S · ATT-01
- CA-01: entro 5 giorni dalla comunicazione l'impresa può presentare un'istanza motivata con allegati.
- CA-02: l'operatore INAIL accoglie o respinge; se accolta la domanda torna a `DOCUMENTI_INVIATI`.

**RF-34 · Risposta alle richieste di integrazione e al preavviso di rigetto** · M · ATT-01
- CA-01: l'impresa vede la richiesta, il termine (30 giorni per le integrazioni, 10 per le osservazioni) e carica i documenti o il testo di risposta.
- CA-02: scaduto il termine il caricamento è chiuso.

**RF-35 · Rinuncia** · S · ATT-01
- CA-01: l'impresa può rinunciare alla domanda in qualsiasi stato non finale; la rinuncia è irreversibile e libera le risorse per la redistribuzione.

---

## 6. Requisiti funzionali – Area tecnico (perizia)

**RF-40 · Incarico al tecnico** · M · ATT-01
- CA-01: l'impresa indica codice fiscale, nome, ordine o collegio e numero di iscrizione del tecnico.
- CA-02: il tecnico non può essere il titolare, il legale rappresentante, un amministratore o un socio dell'impresa (per le società di persone); il sistema blocca l'incarico se il codice fiscale coincide con una di queste persone.

**RF-41 · Compilazione della perizia online** · M · ATT-03
- CA-01: il tecnico vede solo le domande per cui ha un incarico attivo.
- CA-02: il modulo di perizia (B1.1, B2, B3, B4 o B5 secondo l'asse) riprende in sola lettura i dati della domanda registrata e chiede: descrizione della situazione ante e post intervento, attestazione del rischio nel DVR con data certa, conformità dei preventivi e dei listini, per ogni macchina il confronto tra bene nuovo e bene sostituito (R-ALL-13), allegati tecnici.
- CA-03: il tecnico firma la perizia digitalmente (mock); dopo la firma non è più modificabile e risulta completata nella checklist dell'impresa.
- CA-04: per l'Asse 1.2 la perizia non è richiesta; al suo posto c'è la dichiarazione sostitutiva B1.2 firmata dal legale rappresentante.

---

## 7. Requisiti funzionali – Back-office INAIL

**RF-50 · Configurazione dell'annualità e del calendario** · M · ATT-05
- CA-01: l'amministratore crea un'annualità (es. 2025) e ne configura le date di ogni fase: apertura e chiusura compilazione, periodo di acquisizione del codice, apertura e chiusura dello sportello (anche diverse per Regione o Asse), pubblicazione degli elenchi, caricamento documenti.
- CA-02: una data già passata non può essere modificata; una data futura si modifica solo con almeno 7 giorni di preavviso.
- CA-03: ogni modifica al calendario è storicizzata e visibile pubblicamente con la data di aggiornamento.

**RF-51 · Configurazione di regole e tabelle** · M · ATT-05
- CA-01: tutte le tabelle del capitolo 9 sono modificabili da interfaccia o importabili da file CSV, per annualità.
- CA-02: dopo l'apertura della compilazione le tabelle sono bloccate; una correzione crea una nuova versione delle regole e le bozze vengono ricalcolate, le domande registrate no.
- CA-03: funzione «Copia annualità» che duplica tutte le tabelle in una nuova annualità.

**RF-52 · Stanziamenti** · M · ATT-05
- CA-01: per ogni Regione e Asse si inserisce lo stanziamento in euro.
- CA-02: il sistema mostra per ogni Regione e Asse lo stanziamento, il totale richiesto dalle domande registrate e il residuo.

**RF-53 · Calcolo degli elenchi NCD** · M · ATT-05, ATT-06
Alla chiusura della registrazione, per ogni Regione e Asse:
- CA-01: se la somma dei contributi richiesti delle domande registrate è ≤ stanziamento, tutte le domande di quella combinazione passano a `NCD`, ordinate per ora di registrazione (l'ordine non dà priorità); quella combinazione non partecipa allo sportello.
- CA-02: altrimenti le domande partecipano allo sportello.

**RF-54 · Calcolo degli elenchi provvisori dello sportello** · M · ATT-05, ATT-06
- CA-01: entro 14 giorni dalla chiusura dello sportello, le domande inviate sono ordinate per ora di arrivo e, a parità, con i criteri di precedenza 9.10.
- CA-02: si scorre l'elenco sommando i contributi richiesti: finché la somma non supera lo stanziamento la domanda è `IN_POSIZIONE_UTILE`, le successive `NON_AMMISSIBILE_FONDI`. Una domanda che da sola supererebbe il residuo è esclusa e si prosegue con la successiva (ipotesi adottata, vedi 13).
- CA-03: le domande `REGISTRATA` o `CODICE_RILASCIATO` mai inviate allo sportello non entrano negli elenchi.
- CA-04: l'amministratore vede un'anteprima, poi pubblica; la pubblicazione rende visibili gli elenchi (RF-30) e avvia il termine di caricamento documenti.

**RF-55 · Redistribuzione e elenchi definitivi** · M · ATT-05
- CA-01: dopo la fase documenti, i contributi delle domande decadute o rinunciate tornano disponibili per la stessa Regione e Asse; le domande `NON_AMMISSIBILE_FONDI` successive in ordine diventano `SUBENTRATA` fino a esaurimento del residuo.
- CA-02: l'elenco definitivo riporta: ammissibili definitivamente, decadute, subentrate, non ammissibili per carenza di fondi.

**RF-56 · Scrivania dell'operatore di sede** · M · ATT-04
- CA-01: elenco delle domande della propria Regione con filtri per stato, asse, data, punteggio.
- CA-02: dettaglio della domanda con fotografia registrata, documenti, perizia, storico degli stati.

**RF-57 · Istruttoria tecnico-amministrativa** · M · ATT-04
- CA-01: l'operatore spunta la verifica di ogni requisito e documento; per ogni criterio di punteggio può confermare o correggere il valore; il sistema ricalcola punteggio e contributo con le regole dell'annualità.
- CA-02: se il punteggio ricalcolato scende sotto 130 il sistema propone il preavviso di rigetto.
- CA-03: azioni: «Richiedi integrazione» (termine 30 giorni), «Preavviso di rigetto» (termine 10 giorni), «Ammetti», «Ammetti parzialmente» (con importo ridotto e motivazione), «Respingi» (con motivazione).
- CA-04: l'istruttoria deve chiudersi entro 120 giorni dall'avvio; il sistema mostra i giorni rimanenti e segnala in rosso le pratiche oltre 100 giorni. Il tempo è sospeso durante l'attesa delle integrazioni.
- CA-05: il provvedimento finale è unico per intervento principale e aggiuntivo e genera un PDF inviato via PEC (mock).

**RF-58 · Reportistica** · S · ATT-04, ATT-05
- CA-01: conteggi e importi per Regione, Asse e stato, esportabili in CSV e Excel.

**RF-59 · Registro di audit** · M · ATT-05
- CA-01: consultazione di tutte le azioni con filtri per utente, domanda, tipo azione e periodo.

---

## 8. Requisiti funzionali – Interfacce esterne

Tutte le interfacce sono definite come servizi interni con un'implementazione finta (mock) e un'implementazione reale da completare.

**RF-70 · Autenticazione (SPID/CIE/CNS)** · M — protocollo OpenID Connect o SAML; il mock restituisce utenti di prova.

**RF-71 · Anagrafica INAIL** · M — dato il codice fiscale dell'impresa restituisce ragione sociale, partita IVA, ATECO, sede, codice ditta, tasso medio di tariffa, rapporto assicurativo, data di associazione al portale. Se il servizio non risponde l'utente compila a mano e un avviso lo segnala.

**RF-72 · Abilitazioni e deleghe** · M — dato il codice fiscale della persona restituisce le imprese e il ruolo (legale rappresentante / delegato con o senza potere di registrare).

**RF-73 · Notifiche PEC** · M — invio di comunicazioni con oggetto, testo, allegati e destinatario; il mock salva il messaggio in una tabella consultabile da una pagina di sviluppo.

**RF-74 · Verifica firma digitale** · M — dato un file PDF o P7M restituisce validità, codice fiscale del firmatario, data della firma.

**RF-75 · API di ricezione bozza da simulatore** · S
Servizio REST autenticato (client credentials OAuth2) che crea una domanda in `BOZZA` per un'impresa a partire da un pacchetto di dati inviato da un sistema esterno (simulatore).
- CA-01: `POST /api/v1/bozze` con JSON contenente codice della simulazione, versione del pacchetto, annualità, versione delle regole, dati dell'impresa, dimensione, progetto, criteri, importi calcolati (solo come valori di controllo), codice fiscale di chi ha autorizzato l'invio.
- CA-02: risposta `201` con identificativo della bozza; `409` se la coppia codice simulazione + versione è già stata ricevuta (idempotenza: stesso identificativo della prima volta); `422` con l'elenco dei campi non validi o con codici sconosciuti.
- CA-03: punteggio e importi sono ricalcolati da ISI Domanda; se differiscono dai valori ricevuti, la bozza viene creata comunque e la differenza è registrata e mostrata all'utente.
- CA-04: se l'impresa ha già una domanda registrata, la risposta è `409` con codice `DOMANDA_GIA_REGISTRATA`.
- CA-05: `GET /api/v1/bozze/{id}/stato` restituisce lo stato corrente della domanda.
- CA-06: la bozza ricevuta compare nel cruscotto dell'impresa con l'etichetta «Importata dal simulatore» e va comunque verificata e registrata dall'utente.
- CA-07: specifica OpenAPI 3 generata e pubblicata.

---

## 9. Regole di business e tabelle di configurazione

I codici tra parentesi (R-…) servono a tracciare la regola nei test. Tutti i valori sono per l'annualità 2025.

### 9.1 Assi di finanziamento

| Asse | Nome | % contributo | Destinatari | Aggiuntivo | Anticipo 70% |
|---|---|---|---|---|---|
| 1.1 | Riduzione dei rischi tecnopatici | 65% | Imprese iscritte a Registro imprese o Albo artigiani; ETS RUNTS solo tipologia d | Sì (ISO 45001) | No |
| 1.2 | Modelli organizzativi e responsabilità sociale | 80% | Imprese con dipendenti | No | No |
| 2 | Riduzione dei rischi infortunistici | 65% | Imprese iscritte | Sì (DPI intelligenti; moduli prefabbricati) | No |
| 3 | Bonifica da materiali contenenti amianto | 65% | Imprese iscritte, anche agricole primarie e settori Asse 4 | Sì (coperture a verde; fotovoltaico) | No |
| 4 | Micro e piccole imprese in settori specifici | 65% | Solo micro e piccole con ATECO in tabella 9.4.7 | Sì (ISO 45001) | Sì |
| 5.1 | Agricoltura – generalità delle imprese | fino al 65% | Micro e piccole agricole primarie | No | Sì |
| 5.2 | Agricoltura – giovani agricoltori | fino all'80% | Come 5.1, con giovani agricoltori under 41 | No | Sì |

### 9.2 Regole di accesso agli assi

- **R-DEST-02** ETS iscritti al RUNTS alla data di pubblicazione: solo Asse 1.1 tipologia d. ETS iscritti al Registro imprese: nessun limite.
- **R-DEST-03** Micro e piccole imprese agricole primarie: solo Assi 3 e 5.
- **R-DEST-04** Micro e piccole imprese con ATECO dei settori Asse 4: escluse dagli Assi 2 e 5.
- **R-DEST-05** Asse 4: solo micro e piccole con ATECO in tabella; i codici valgono come prefisso (es. «16» comprende tutti i 16.xx).
- **R-DEST-06** Micro e piccola impresa: meno di 50 ULA e fatturato o totale di bilancio ≤ 10.000.000 €.
- **R-DEST-07** Asse 5: impresa individuale, società agricola o cooperativa, con qualifica di imprenditore agricolo e partita IVA agricola; IAP e società agricole solo con dipendenti o soci lavoratori assicurati.
- **R-DEST-08** Asse 5.2: età sotto i 41 anni alla data di chiusura della compilazione di: titolare (impresa individuale); almeno metà dei soci (società semplici, SNC, cooperative); socio accomandatario o metà degli accomandatari (SAS); metà del capitale e metà degli amministratori (società di capitali).
- **R-DEST-10** Rapporto assicurativo INAIL: ammesso per gli Assi 1.1, 1.2, 2, 3, 4; escluso per l'Asse 5.
- **R-DEST-11** Rapporto INPS gestione agricola: micro e piccole agricole primarie (ATECO A01) → solo Assi 3 e 5; agricole medie e grandi, silvicoltura (A02) o altri settori → Assi 1.1, 1.2, 2, 3. Eccezione: ATECO 01.61 con rapporto INAIL.
- **R-DEST-12** Senza rapporto assicurativo (né INAIL né INPS agricolo): nessun asse ammesso.
- **R-REQ-04 / R-ALL-11** Asse 1.2: escluse le imprese senza dipendenti, con soli datore di lavoro e soci, o senza dipendenti nell'unità produttiva; escluse le imprese che hanno adottato o mantenuto un SGSL/MOG nei 3 anni precedenti la chiusura della compilazione.
- **R-REQ-14** Asse 1.2: escluse le imprese con concessione Asse 1.2 nelle 3 edizioni precedenti.

### 9.3 Requisiti da dichiarare (passo 7)

| Codice | Requisito | Assi |
|---|---|---|
| R-REQ-02 | Progetto riferito a lavorazioni già attive alla data di pubblicazione, con rischio documentato (DVR). | Tutti |
| R-REQ-03 | Il progetto non amplia la sede produttiva e non comprende beni usati. | Tutti |
| R-REQ-05 | Macchine o trattori da sostituire di piena proprietà da almeno 3 anni al 31/12/2025. | 1.1, 2, 4, 5 (se c'è sostituzione) |
| R-REQ-06 | Immobili nella disponibilità dell'impresa da almeno 3 anni al 31/12/2025. | 3 |
| R-REQ-07 | Unità produttiva attiva nella Regione dell'Avviso. | Tutti |
| R-REQ-08 | Iscrizione a registri o albi entro la pubblicazione in G.U. (18/12/2025). | Tutti |
| R-REQ-09 | Iscrizione alla gestione assicurativa e previdenziale e regolarità contributiva (DURC). | Tutti |
| R-REQ-10 | Nessuna procedura concorsuale o liquidazione (ammesso il concordato in continuità omologato). | Tutti |
| R-REQ-11 | Nessuna condanna definitiva di titolare, legale rappresentante o soci amministratori per omicidio o lesioni colpose legate alla sicurezza sul lavoro. | Tutti |
| R-REQ-12 | Nessun provvedimento interdittivo art. 9 c. 2 lett. d) d.lgs. 231/2001. | Tutti |
| R-REQ-13 | Nessuna concessione ISI 2022, 2023, 2024 (salvo Asse 1.2), oppure rinuncia via PEC entro il giorno prima dell'apertura. | 1.1, 2, 3, 4, 5 |
| R-REQ-15 | Rispetto delle regole sul cumulo di aiuti pubblici sugli stessi costi. | Tutti |
| R-REQ-16 | Nessun ordine di recupero pendente e impresa non in difficoltà (Reg. UE 2022/2472). | 5 |
| R-REQ-17 | Polizza contro calamità naturali ed eventi catastrofali, dove obbligatoria. | Tutti tranne imprese agricole art. 2135 c.c. |
| R-REQ-18 | Sottoscrizione del Patto di integrità (Modulo G). | Tutti |

### 9.4 Punteggio

Punteggio totale = somma dei criteri applicabili all'asse. Soglia di ammissibilità: **130 punti** (Asse 5: per ciascun bene).

**9.4.1 Punteggio massimo per asse**

| Criterio | 1.1 | 1.2 | 2 | 3 | 4 | 5.1 | 5.2 |
|---|---|---|---|---|---|---|---|
| Dimensioni aziendali | 50 | 50 | 50 | 50 | 50 | — | — |
| Rischiosità (tasso INAIL; Asse 4: ATECO) | 45 | 45 | 45 | 45 | 40 | — | — |
| Tipologia di intervento / Misura | 90 | 90 | 90 | 85 | 80 | 70 | 70 |
| Fattore di rischio | — | — | — | — | — | 70 | 70 |
| Parti sociali | 18 | 18 | 18 | 18 | 18 | 18 | 18 |
| RLS/RLST | 19 | 19 | 18 | 18 | 18 | 18 | 18 |
| Certificazioni e RLAQ | 22 | 5 | 22 | 22 | 17 | 5 | 5 |
| Bonus ATECO regionale | 10 | 10 | 10 | 10 | — | — | — |
| **Totale massimo** | **254** | **237** | **253** | **248** | **223** | **181 per bene** | **181 per bene** |

**9.4.2 Dimensioni aziendali** (Assi 1.1, 1.2, 2, 3; Asse 4 solo fino a 50 ULA). Fasce contigue: limite inferiore escluso, superiore incluso (10,4 ULA → fascia «oltre 10 fino a 15»). Se fatturato o totale di bilancio superano il limite della fascia, il punteggio è moltiplicato per 0,6 (R-ALL-02), conservando un decimale.

| ULA | Limite fatturato o bilancio | Punti | Punti oltre il limite |
|---|---|---|---|
| oltre 0 fino a 10 | ≤ 2 mln € | 50 | 30 |
| oltre 10 fino a 15 | ≤ 10 mln € | 45 | 27 |
| oltre 15 fino a 20 | ≤ 10 mln € | 40 | 24 |
| oltre 20 fino a 30 | ≤ 10 mln € | 35 | 21 |
| oltre 30 fino a 50 | ≤ 10 mln € | 30 | 18 |
| oltre 50 fino a 100 | ≤ 50 mln € | 20 | 12 |
| oltre 100 fino a 150 | ≤ 50 mln € | 17 | 10,2 |
| oltre 150 fino a 200 | ≤ 50 mln € | 14 | 8,4 |
| oltre 200 fino a 250 | ≤ 50 mln € | 12 | 7,2 |
| oltre 250 fino a 500 | nessun limite | 9 | — |
| oltre 500 | nessun limite | 7 | — |

**9.4.3 Rischiosità – tasso medio di tariffa INAIL** (Assi 1.1, 1.2, 2, 3). Imprese iscritte alla CCIAA dal 31/12/2023 in poi: 4 punti fissi.

| Tasso medio (‰) | Punti |
|---|---|
| 96,5 – 110 | 45 |
| 84,5 – 96,49 | 41 |
| 72,5 – 84,49 | 38 |
| 60,5 – 72,49 | 35 |
| 48,5 – 60,49 | 25 |
| 36,5 – 48,49 | 20 |
| 24,5 – 36,49 | 15 |
| 12,5 – 24,49 | 10 |
| 3,5 – 12,49 | 4 |
| Non definito | 4 |

**9.4.4 Parti sociali e RLS** — Parti sociali: nessuna 0; Ente bilaterale o Organismo paritetico 18; due o più parti sociali (almeno una datoriale e una dei lavoratori) 18. RLS/RLST: 19 punti Assi 1.1 e 1.2; 18 punti per gli altri assi.

**9.4.5 Certificazioni e RLAQ** (cumulabili)

| Certificazione | Punti | Assi |
|---|---|---|
| SGSL UNI EN ISO 45001:2023 | 5 | 1.1, 2, 3, 4 (non con aggiuntivo ISO 45001 negli Assi 1.1 e 4) |
| UNI EN ISO 14001:2015 | 3 | 1.1, 2, 3, 4 |
| Registrazione EMAS | 3 | 1.1, 2, 3, 4 |
| UNI ISO 39001:2016 | 3 | 1.1, 2, 3, 4 |
| MOG art. 30 d.lgs. 81/08 asseverato | 3 | 1.1, 2, 3, 4 |
| Iscrizione alla Rete del lavoro agricolo di qualità (RLAQ) | 5 | 1.1, 1.2, 2, 3, 5.1, 5.2 |

**9.4.6 Tipologie di intervento**

| Asse | Codice | Descrizione | Punti |
|---|---|---|---|
| 1.1 | a | Riduzione del rischio chimico | 80 |
| 1.1 | b | Riduzione del rischio rumore mediante sostituzione di macchine | 65 |
| 1.1 | c | Riduzione del rischio da vibrazioni meccaniche | 65 |
| 1.1 | d | Riduzione del rischio da movimentazione manuale di persone | 90 |
| 1.1 | e | Movimentazione manuale di carichi mediante automazione | 70 |
| 1.1 | f | Rischio emergenza nel settore della pesca | 80 |
| 1.1 | g | Movimentazione manuale dei carichi nel settore della pesca | 80 |
| 1.2 | a | Adozione di SGSL certificato UNI EN ISO 45001:2023 | 90 |
| 1.2 | b | Adozione di SGSL di settore previsto da accordi INAIL-Parti sociali | 80 |
| 1.2 | c | Adozione di MOG art. 30 d.lgs. 81/2008 asseverato | 80 |
| 1.2 | d | Adozione da parte di MPMI di MOG con procedure semplificate (d.m. 13/2/2014) | 80 |
| 2 | a | Caduta dall'alto mediante ancoraggi fissati permanentemente | 85 |
| 2 | b | Sostituzione di trattori agricoli o forestali e macchine obsoleti | 70 |
| 2 | c | Lavorazioni in spazi confinati o sospetti di inquinamento | 90 |
| 3 | a | Rimozione di coperture in materiali contenenti amianto e rifacimento | 80 |
| 3 | b | Rimozione di coperture e controsoffitti con amianto e rifacimento delle coperture | 85 |
| 4 | a | Sostituzione di macchine | 70 |
| 4 | b | Sostituzione di apparecchi elettrici | 65 |
| 4 | c | Riduzione del rischio da polveri di legno | 80 |

**Misure Asse 5 (una per bene)**: 1a macchina priva di motore 70; 2a gasolio, emissioni oltre il 50% sotto i limiti 70; 2b gasolio, 30–50% sotto i limiti 65; 2c benzina, oltre il 35% sotto i limiti 70; 2d benzina, 20–35% sotto i limiti 65; 2e motore elettrico o a metano 70; 2f attrezzatura intercambiabile con consumo ridotto del 30% 60.

**Fattori di rischio Asse 5 (uno per bene)**: 1a trattore con rottamazione 70; 1b macchina con rottamazione 70; 2a trattore con rumorosità −3 dB(A), con permuta 65; 2b trattore −2 dB(A), con permuta 60; 2c macchina −3 dB(A), con permuta 65 (non mietitrebbiatrici); 2d macchina −2 dB(A), con permuta 60 (non mietitrebbiatrici); 3a meccanizzazione di un'operazione prima manuale 60.

**Interventi aggiuntivi**: Asse 1.1 a) SGSL ISO 45001; Asse 2 a) DPI intelligenti (Modulo H), b) moduli prefabbricati per rischi meteoclimatici; Asse 3 a) coperture a verde, b) fotovoltaico per autoproduzione; Asse 4 a) SGSL ISO 45001.

**9.4.7 Rischiosità Asse 4 per ATECO** (i codici valgono come prefisso; conta il prefisso più lungo)

03 → 35 · 10.41.10 → 38 · 10.51.20 → 34 · 10.61.11 → 38 · 10.61.19 → 40 · 13 → 32 · 14 → 30 · 15 → 30 · 16 → 40 · 23.15.10 → 36 · 23.41.00 → 32 · 23.70.10 → 38 · 31 → 36 · 32.12 → 30 · 32.13 → 30 · 32.2 → 30 · 32.3 → 32 · 32.4 → 30 · 32.99.1 → 30 · 56.11.1 → 28 · 56.11.2 → 22 · 56.11.91 → 28 · 56.11.92 → 28 · 56.11.93 → 26 · 56.12 → 26 · 56.2 → 30 · 56.3 → 24 · 47.11.02 → 20 · 47.27.90 → 20.

**9.4.8 Bonus ATECO regionale** — +10 punti (Assi 1.1, 1.2, 2, 3) se l'ATECO dell'impresa rientra nei settori indicati dalla Regione dell'unità produttiva. Tabella `bonus_ateco_regionale` (Regione, prefisso ATECO), da caricare con dati di prova.

### 9.5 Calcolo degli importi

- **R-CALC-01** Spesa ammissibile = spese di progetto + spese tecniche ammesse, al netto IVA (IVA inclusa solo se non recuperabile).
- **R-CALC-02** Ogni macchina o trattore: spesa ammessa = min(preventivo; 80% del listino).
- **R-CALC-03** Trattori: listino degli accessori ≤ 30% del listino del trattore base; la parte eccedente non è ammessa.
- **R-ALL-03** Spese tecniche: fino al 10% delle spese di progetto, massimo 10.000 € (Assi 1.1, 2, 3, 4); se il progetto è solo acquisto di macchine, fino al 5%, massimo 5.000 € (Assi 1.1, 2, 4). La perizia è compresa nelle spese tecniche, massimo 1.850 €. Asse 5: unica spesa tecnica ammessa la perizia. Asse 1.2: nessuna spesa tecnica.
- **R-ALL-09** Asse 3: copertura max 60 €/mq; controsoffitto o sottocopertura max 20 €/mq; parapetti, ancoraggi e scale max 10% delle spese della copertura.
- **R-ALL-10** Asse 1.2: spesa massima = valore parametrico (9.8) × 4.000 € per la consulenza + valore parametrico × 2.000 € per certificazione o asseverazione.
- **R-CALC-04** Contributo principale = spesa ammissibile × percentuale dell'asse, limitato a **130.000 €**. Se è inferiore a **5.000 €** la registrazione è bloccata, tranne per l'Asse 1.2 con meno di 50 dipendenti (nessun minimo).
- **R-CALC-05** Contributo aggiuntivo = spesa aggiuntiva × 80%, limitato a min(20.000 €; 130.000 € − contributo principale) e comunque non superiore al contributo principale.
- **R-CALC-06** Asse 5.2: 80% solo con tutti e tre i requisiti (età, formazione o competenze, capo azienda); altrimenti 65%. Asse 5.1: 65%.
- **R-CALC-07** Asse 5 con permuta: se il ricavo della permuta supera la quota a carico dell'impresa (35% della spesa; 20% per i giovani agricoltori), il contributo è ridotto della differenza.
- **R-CALC-08** Anticipo: fino al 50% se il contributo totale è ≥ 30.000 €; fino al 70% senza soglia per micro e piccole imprese degli Assi 4 e 5. L'anticipo si applica anche all'aggiuntivo con la stessa percentuale. Mai con noleggio con patto d'acquisto.
- **R-CALC-09** Fideiussione = anticipo × 1,10.
- **R-CALC-10** Negli elenchi l'importo richiesto è troncato all'euro.
- Arrotondamenti: tutti gli importi intermedi e finali al centesimo, arrotondamento commerciale (metà per eccesso); calcolo con tipo decimale, mai in virgola mobile.

### 9.6 Controlli sui beni sostituiti (R-ALL-08, R-ALL-13)

- Asse 5, trattori: immessi sul mercato prima del 2005 solo rottamazione; dal 2005 al 2012 rottamazione o permuta; dal 2013 non sostituibili. Potenza massima 130 kW.
- Asse 2, trattori: stessa categoria, potenza non superiore del 50% e comunque non oltre 130 kW, con cabina ROPS.
- Asse 1.1 tipologie b e c: bene nuovo di analogo tipo, prestazioni non superiori del 30%.
- Asse 4: analogo tipo, solo rottamazione.
- Lo scostamento oltre i limiti richiede una motivazione obbligatoria (campo di testo) e viene segnalato al tecnico e all'istruttoria.

### 9.7 Spese non ammesse (testo di aiuto e controlli)

DPI (salvo Asse 2 tipologia c e aggiuntivo DPI intelligenti); veicoli, aeromobili e imbarcazioni; ponteggi fissi; trasporto del bene; consulenza per la compilazione della domanda; formazione dei lavoratori; valutazione dei rischi; manutenzione ordinaria; leasing (ammesso solo il noleggio con patto d'acquisto per l'Asse 5); costi del personale interno; costi autofatturati; spese fatturate dai soci o da imprese collegate. Nel passo 6 una casella obbligatoria conferma che il piano non le contiene.

### 9.8 Asse 1.2 – valore parametrico

Complessità del macrosettore ATECO: **ALTA** macrosettori 1 (manifattura: C13, C14, C16, C24–C33, T95.3), 4 (costruzioni: F), 7 (estrattive: B, C23), 8 (chimica, carta, gomma, rifiuti: C15, C17, C18.2, C19–C22, E37–E39, T96), 12 (C24.46); **MEDIA** macrosettori 2 (H, O79, K61), 3 (D, E36), 6 (C10–C12, I), 9 (A01, A02), 10 (A03), 11 (R, N75); **BASSA** macrosettore 5 (servizi, commercio, PA e altro: G, J58–J60, L, M68, P, Q, N69–N74, V, S, O77, O78, O80–O82, T94, T95.1, T95.2, T95.4).

| Dipendenti | ALTA | MEDIA | BASSA |
|---|---|---|---|
| 1–10 | 3,5 | 3 | 3 |
| 11–15 | 4,5 | 3,5 | 3 |
| 16–20 | 5 | 4 | 3 |
| 21–30 | 6 | 5 | 3 |
| 31–50 | 7 | 6 | 3,5 |
| 51–100 | 10 | 7,5 | 4 |
| 101–150 | 11,5 | 8,5 | 4,5 |
| 151–200 | 12,5 | 9,5 | 5 |
| 201–250 | 13,5 | 10 | 5 |
| 251–500 | 16 | 12 | 6 |
| 501–1000 | 20 | 16 | 12 |
| 1001–2000 | 23 | 18 | 13 |
| 2001–4000 | 27 | 20 | 15 |
| oltre 4000 | 36 | 29 | 21 |

### 9.9 Documenti richiesti (checklist di RF-31)

| Documento | Quando è richiesto |
|---|---|
| Modulo A (domanda generata, firmata digitalmente) | Sempre |
| Documento di identità del legale rappresentante | Sempre |
| Perizia asseverata online (B1.1, B2, B3, B4, B5) | Assi 1.1, 2, 3, 4, 5 |
| Dichiarazione sostitutiva B1.2 | Asse 1.2 |
| Modulo C1 (dati e dimensione impresa) / C2 (ETS) | Sempre (C2 per ETS) |
| Modulo D-cumulo | Sempre |
| Modulo D-aiuti | Asse 5 |
| Modulo E1 (condivisione con parti sociali) | Se scelto il punteggio parti sociali |
| Modulo E2 (informativa RLS/RLST) | Se scelto il punteggio RLS |
| Modulo G (Patto di integrità) | Sempre |
| Modulo H (DPI intelligenti) | Asse 2 con aggiuntivo DPI intelligenti |
| DVR con data certa | Sempre |
| Preventivo e listino per ogni macchina | Se ci sono macchine o trattori |
| Certificati (ISO 45001, 14001, 39001, EMAS, MOG, RLAQ) | Per ogni certificazione dichiarata |
| Contratto di assicurazione catastrofale | Se obbligatoria (R-REQ-17) |

### 9.10 Criteri di precedenza a parità di ora di invio allo sportello

Nell'ordine: 1) ora di registrazione della domanda più vecchia; 2) contributo richiesto minore; 3) importo del progetto maggiore; 4) data di iscrizione alla CCIAA più vecchia; 5) possesso del rating di legalità (solo imprese con fatturato oltre 2 milioni €).

---

## 10. Modello dati

Entità principali (nomi indicativi). Tutte hanno `id`, `creato_il`, `creato_da`, `modificato_il`, `modificato_da`, `versione` (per la concorrenza ottimistica).

| Entità | Campi principali | Relazioni |
|---|---|---|
| `annualita` | anno, data_pubblicazione_gu, stato (configurazione / aperta / chiusa) | 1–N con calendario, tabelle di regole, domande |
| `fase_calendario` | annualita_id, tipo_fase, regione_id (facoltativa), asse_id (facoltativo), data_ora_inizio, data_ora_fine, data_aggiornamento | |
| `regione` | codice ISTAT, nome, pec_sede_inail | |
| `stanziamento` | annualita_id, regione_id, asse_id, importo | |
| `asse` | annualita_id, codice, nome, percentuale, ammette_aggiuntivo, ammette_anticipo_70, punteggio_massimo | 1–N tipologie, aggiuntivi |
| `tipologia_intervento` | asse_id, codice, descrizione, punteggio | |
| `intervento_aggiuntivo_tipo` | asse_id, codice, descrizione, incompatibile_iso45001 | |
| `misura_asse5`, `fattore_rischio_asse5` | codice, descrizione, punteggio, condizione | |
| `classe_dimensionale` | annualita_id, ula_da, ula_a, limite_fatturato, punti, punti_ridotti | |
| `fascia_tasso` | annualita_id, tasso_da, tasso_a, punti | |
| `ateco` | codice, descrizione, macrosettore, versione | |
| `asse4_ateco` | annualita_id, prefisso_ateco, rischiosita | |
| `bonus_ateco_regionale` | annualita_id, regione_id, prefisso_ateco | |
| `certificazione_tipo` | codice, descrizione, punti, assi_ammessi | |
| `valore_parametrico_12` | annualita_id, dipendenti_da, dipendenti_a, complessita, valore | |
| `requisito_tipo` | codice, testo, articolo, assi_ammessi, condizione | |
| `documento_tipo` | codice, descrizione, condizione | |
| `persona` | codice_fiscale, nome, cognome | |
| `impresa` | codice_fiscale (unico), partita_iva, ragione_sociale, forma_giuridica, ateco, data_costituzione, data_iscrizione_cciaa, sede_legale, codice_ditta_inail, tipo_soggetto, agricola_primaria, iap, rapporto_assicurativo, data_associazione_portale | |
| `abilitazione` | persona_id, impresa_id, ruolo, puo_registrare, valida_da, valida_a | |
| `domanda` | numero, annualita_id, impresa_id, stato, regione_id, asse_id, titolo, descrizione, dati dimensione, criteri di punteggio, importi calcolati, punteggio_totale, origine (manuale / simulatore), codice_simulazione, data_ora_registrazione, data_ora_invio_sportello, posizione_elenco | 1–N interventi, beni, dichiarazioni, documenti, transizioni |
| `domanda_tipologia` | domanda_id, tipologia_id | |
| `intervento` | domanda_id, principale/aggiuntivo, tipo, spese_progetto, spese_tecniche, perizia, importo_ammissibile, contributo | |
| `macchina` | intervento_id, descrizione, marca_modello, preventivo, listino, trattore, listino_accessori, potenza_kw, sostituito_anno, modalita (rottamazione / permuta), ricavo_permuta | |
| `bene_asse5` | domanda_id, numero (1–2), tipo, misura_id, fattore_id, punteggio, ammesso | |
| `superficie_asse3` | intervento_id, mq_copertura, mq_controsoffitto, spese_accessorie | |
| `dichiarazione` | domanda_id, requisito_tipo_id, valore, dettaglio | |
| `fotografia_registrazione` | domanda_id, json_dati, versione_regole, hash | Immutabile |
| `codice_identificativo` | domanda_id, codice (unico), rilasciato_il, usato_il | |
| `invio_sportello` | codice_id, data_ora_ricezione (ms), ip, esito | |
| `elenco` | annualita_id, regione_id, asse_id, tipo (NCD / provvisorio / definitivo), pubblicato_il | 1–N righe |
| `riga_elenco` | elenco_id, domanda_id, posizione, importo_troncato, esito | |
| `documento` | domanda_id, documento_tipo_id, nome_file, hash_sha256, dimensione, firmato, firmatario_cf, caricato_il | |
| `perizia` | domanda_id, tecnico_id, modello, dati_json, firmata_il | |
| `incarico_tecnico` | domanda_id, persona_id, ordine, numero_iscrizione, attivo | |
| `istruttoria` | domanda_id, operatore_id, avviata_il, giorni_sospesi, esito, importo_ammesso, motivazione | 1–N richieste |
| `richiesta_istruttoria` | istruttoria_id, tipo (integrazione / preavviso rigetto), inviata_il, scadenza, risposta_il | |
| `transizione_stato` | domanda_id, da, a, data_ora, utente_o_processo, motivo | |
| `notifica_pec` | destinatario, oggetto, testo, allegati, inviata_il, esito | |
| `bozza_importata` | codice_simulazione, versione_pacchetto, domanda_id, json_ricevuto, differenze_calcolo | Unico su codice + versione |
| `audit` | data_ora, utente, azione, entita, entita_id, prima, dopo, ip | |

---

## 11. Casi di prova

I test automatici devono verificare almeno questi casi. Valori attesi calcolati con le regole del capitolo 9.

**TC-01 · Domanda Asse 2 sopra soglia con anticipo**
Impresa: 8 ULA, fatturato 1.500.000 €, tasso 60 ‰, iscritta CCIAA nel 2010, rapporto INAIL. Asse 2 tipologia a (85). Parti sociali: Ente bilaterale. RLS: sì. Certificazione ISO 45001. Spese progetto 100.000 €, spese tecniche 8.000 €, anticipo richiesto.
Atteso: dimensione 50, rischiosità 25, tipologia 85, parti sociali 18, RLS 18, certificazioni 5 → **201 punti**; spesa ammissibile 108.000 €; contributo 70.200 €; anticipo 50% = 35.100 €; fideiussione 38.610 €; registrazione consentita.

**TC-02 · Sotto soglia**
Impresa: 60 ULA, fatturato 40.000.000 €, tasso 10 ‰, iscritta nel 2005. Asse 1.1 tipologia b (65), nessun altro criterio.
Atteso: 20 + 4 + 65 = **89 punti**; pulsante REGISTRA disabilitato; messaggio «Punteggio 89 inferiore alla soglia di 130».

**TC-03 · Riduzione per fatturato oltre limite**
8 ULA, fatturato 3.000.000 € → punteggio dimensionale **30** (50 × 0,6).

**TC-04 · Contributo sotto il minimo**
Asse 3, spesa ammissibile 6.000 € → contributo 3.900 € < 5.000 € → registrazione bloccata.

**TC-05 · Asse 1.2 senza minimo**
Asse 1.2, 20 dipendenti, spesa 5.000 € → contributo 4.000 € → registrazione consentita (nessun minimo sotto 50 dipendenti).

**TC-06 · Massimale e aggiuntivo**
Asse 1.1, spesa ammissibile principale 190.000 € → 123.500 €; aggiuntivo spesa 15.000 € → 80% = 12.000 €, limite min(20.000; 130.000 − 123.500 = 6.500) → **6.500 €**; totale 130.000 €.

**TC-07 · Contributo oltre il massimo**
Asse 2, spesa ammissibile 250.000 € → 162.500 € → limitato a **130.000 €** con avviso.

**TC-08 · Macchina oltre l'80% del listino**
Preventivo 50.000 €, listino 55.000 € → spesa ammessa min(50.000; 44.000) = **44.000 €**.

**TC-09 · Spese tecniche oltre il 10%**
Spese progetto 60.000 €, spese tecniche 9.000 € → ammesse **6.000 €**.

**TC-10 · Fascia ULA al confine**
ULA inserite 10,44 → arrotondate 10,4 → fascia «oltre 10 fino a 15» → 45 punti. ULA 10,0 → fascia «oltre 0 fino a 10» → 50 punti.

**TC-11 · Accesso agli assi per agricola primaria**
Micro impresa agricola primaria, ATECO 01.11, rapporto INPS agricolo → assi selezionabili solo **3, 5.1, 5.2**; gli altri sono mostrati come non ammessi con motivo R-DEST-03.

**TC-12 · Asse 4 con ATECO fuori tabella**
Piccola impresa ATECO 25.11 → Asse 4 non selezionabile (R-DEST-05). ATECO 16.23 → selezionabile, rischiosità 40 (prefisso 16).

**TC-13 · Asse 4 più tipologie**
Tipologie a (70) e c (80) selezionate → punteggio tipologia **80**.

**TC-14 · Incompatibilità ISO 45001**
Asse 1.1 con aggiuntivo «SGSL ISO 45001» e certificazione ISO 45001 dichiarata → la certificazione vale 0 punti e viene mostrato il motivo.

**TC-15 · Asse 5 con due beni, uno sotto soglia**
Bene 1: misura 2a (70) + fattore 1a (70) + parti sociali 18 → 158 ammesso. Bene 2: misura 2f (60) + fattore 3a (60) → 120, non ammesso. Atteso: domanda registrabile, contributo calcolato solo sul bene 1.

**TC-16 · Asse 5.2 con requisito mancante**
Giovane agricoltore senza requisito di formazione → percentuale **65%**.

**TC-17 · Permuta Asse 5**
Spesa bene 40.000 €, quota a carico 35% = 14.000 €, ricavo permuta 18.000 € → riduzione 4.000 €; contributo 26.000 − 4.000 = **22.000 €**.

**TC-18 · Anticipo al confine**
Contributo esattamente 30.000 € → anticipo 50% spettante (15.000 €). Contributo 29.999 € (Asse 2) → anticipo non spettante.

**TC-19 · Una sola domanda per impresa**
Due bozze della stessa impresa, entrambe sopra soglia: dopo la registrazione della prima, la seconda non è registrabile. Due registrazioni concorrenti della stessa impresa: solo una riesce.

**TC-20 · Sportello**
Tre domande Regione X Asse 2 con contributi 60.000, 50.000, 40.000 €, stanziamento 100.000 €, inviate alle 10:00:00.120, 10:00:00.050, 10:00:00.300. Atteso: ordine 50.000 (pos. 1), 60.000 (pos. 2), 40.000 (pos. 3); posizione utile per la prima (50.000); la seconda supera il residuo (50.000) ed è non ammissibile per fondi; la terza (40.000) rientra nel residuo ed è in posizione utile (ipotesi 13-H3).

**TC-21 · Codice già usato**
Secondo invio con lo stesso codice → rifiutato.

**TC-22 · Elenco NCD**
Regione Y Asse 3: domande registrate per 80.000 € totali, stanziamento 200.000 € → tutte `NCD`, nessuno sportello.

**TC-23 · Decadenza**
Domanda in posizione utile senza documenti alla scadenza → `DECADUTA` e PEC registrata nel mock.

**TC-24 · Tecnico non ammesso**
Incarico di perizia al legale rappresentante → bloccato.

**TC-25 · API bozza idempotente**
Stesso pacchetto inviato due volte → stessa bozza, nessun duplicato; punteggio inviato 150 contro calcolato 148 → bozza creata con differenza registrata.

---

## 12. Requisiti non funzionali

**Architettura e tecnologia**
- RNF-ARC-01: applicazione web a tre livelli: frontend single page (responsive), backend con API REST documentate OpenAPI 3, database relazionale (PostgreSQL consigliato).
- RNF-ARC-02: motore di calcolo del punteggio e degli importi in un modulo separato, senza dipendenze dall'interfaccia, riusabile e testabile da solo; ogni risultato restituisce anche il dettaglio («perché questo valore») con i codici regola.
- RNF-ARC-03: configurazione per ambiente (sviluppo, test, produzione) tramite variabili d'ambiente; avvio locale con un solo comando (es. Docker Compose) con dati di prova caricati.
- RNF-ARC-04: migrazioni del database versionate.

**Sicurezza e privacy**
- RNF-SEC-01: tutte le comunicazioni in HTTPS; dati personali cifrati a riposo.
- RNF-SEC-02: protezione dalle vulnerabilità OWASP Top 10 (iniezioni, XSS, CSRF, controllo accessi).
- RNF-SEC-03: autorizzazione verificata lato server a ogni richiesta, per ruolo e per impresa.
- RNF-SEC-04: registro di audit non modificabile, conservato 10 anni.
- RNF-SEC-05: conformità al GDPR: informativa, minimizzazione dei dati, tempi di conservazione configurabili.
- RNF-SEC-06: file caricati controllati per tipo reale (non solo estensione) e con antivirus (interfaccia mock).

**Prestazioni e disponibilità**
- RNF-PER-01: ricalcolo del punteggio in meno di 300 ms per il 95% delle richieste.
- RNF-PER-02: sportello informatico: almeno 5.000 invii al minuto con ora di arrivo esatta al millisecondo e nessuna perdita; registrazione dell'ora prima di qualsiasi altra elaborazione.
- RNF-PER-03: disponibilità del 99,5% durante le fasi aperte del calendario; manutenzioni solo fuori dalle finestre.
- RNF-PER-04: sincronizzazione dell'orologio dei server con fonte NTP; l'ora ufficiale è quella del server.

**Usabilità e accessibilità**
- RNF-USA-01: conformità WCAG 2.1 livello AA (obbligo per la PA, Legge 4/2004).
- RNF-USA-02: funzionante sulle ultime due versioni di Chrome, Edge, Firefox, Safari e su smartphone.
- RNF-USA-03: messaggi di errore in italiano semplice, vicino al campo, con l'indicazione di come correggere.
- RNF-USA-04: guida contestuale (icona «?») su ogni campo, con il riferimento all'articolo dell'Avviso.

**Qualità**
- RNF-QUA-01: copertura dei test automatici del motore di calcolo ≥ 90%; tutti i casi del capitolo 11 automatizzati.
- RNF-QUA-02: log applicativi strutturati (JSON) con identificativo di correlazione per richiesta.
- RNF-QUA-03: documentazione README con avvio, architettura e utenti di prova.

---

## 13. Punti aperti e ipotesi adottate

| # | Punto | Ipotesi adottata per questo test |
|---|---|---|
| H1 | Minimo Asse 1.2: l'Avviso dice «meno di 50 dipendenti», l'Allegato tecnico 1.2 «fino a 50». | Soglia parametrica, valore predefinito: meno di 50. |
| H2 | Arrotondamento dei punteggi dimensionali ridotti non interi (10,2; 8,4; 7,2). | Si conserva un decimale. |
| H3 | Scorrimento degli elenchi quando una domanda supera da sola il residuo. | Si esclude e si prosegue con le successive. |
| H4 | Bonus ATECO regionale: settori per Regione non ancora noti. | Tabella vuota con dati di prova. |
| H5 | Data di chiusura della compilazione. | Data configurabile nel calendario. |
| H6 | Integrazione reale con SPID, anagrafica INAIL, PEC, firma digitale, protocollo. | Solo interfacce mock. |
| H7 | Formato del codice identificativo. | 12 caratteri alfanumerici casuali, senza caratteri ambigui. |

---

*Fonti: Avviso pubblico ISI 2025 (testo completo, versione regionale); Allegato criteri, Delibera CdA INAIL n. 187 del 19/11/2025; Allegati tecnici 1.1, 1.2, 2, 3, 4, 5. Sintesi tratte da «Avviso ISI 2025 – Sunto per sviluppatori v2», «Bando ISI 2025 – Parametri e punteggi per asse» e «Simulatore ISI – Interoperabilità con ISI Domanda v1».*
