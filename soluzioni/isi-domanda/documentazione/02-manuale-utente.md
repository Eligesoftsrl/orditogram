# Bando ISI: manuale utente

Un capitolo per chi usa l'applicazione. I nomi in **grassetto** sono quelli che vedi sullo schermo.

## 1. Imprese (portale)

### Accesso
Entri dal portale con le credenziali ricevute (in produzione con SPID, CIE o CNS tramite il provider di identità).
Al primo accesso con una password temporanea ti viene chiesto di cambiarla.

### Nuova domanda
**Nuova pratica → Bando ISI: domanda di finanziamento.** Scrivi il **Codice fiscale dell'impresa** (11 cifre o 16
caratteri) e il **Titolo del progetto** (massimo 200 caratteri), poi **Crea la bozza**. I dati dell'impresa arrivano
dall'anagrafica INAIL: se non arrivano, il campo **Dati dell'impresa** te lo dice e li scrivi tu. Puoi creare più
bozze per confrontare ipotesi diverse; ne potrai registrare una sola.

### Compila la domanda
La trovi nella scrivania. In alto, **Situazione della domanda**: **Punteggio e contributo**, **Errori che bloccano
la registrazione**, **Avvisi del calcolo** (per esempio un importo ridotto al massimale), **Esito dell'ultima
registrazione**. Poi i passi, che puoi compilare in qualsiasi ordine:

1. **Dati dell'impresa**: controlla i dati precompilati; la **PEC** riceve tutte le comunicazioni.
2. **Unità produttiva e dimensione**: la **Regione** decide lo stanziamento; le **ULA** si arrotondano a un decimale.
3. **Asse e tipologia**: vedi **Assi ammessi per l'impresa** e, per gli altri, il motivo. I campi cambiano con
   l'asse: tipologie, intervento aggiuntivo, beni dell'Asse 5 (fino a due), requisiti del giovane agricoltore.
4. **Dati del progetto**: descrizione, lavorazione e rischio; macchine (fino a due) per gli Assi 1.1, 2 e 4;
   superfici e spese per l'Asse 3; requisiti per l'Asse 1.2.
5. **Criteri di punteggio**: tasso medio di tariffa (vuoto = non definito), parti sociali, RLS, certificazioni.
6. **Piano economico**: spese, anticipo e la conferma che il piano non contiene spese non ammesse. Sotto trovi
   **Calcolo degli importi voce per voce**.
7. **Dichiarazioni**: una casella per requisito; compaiono solo quelle del tuo asse.
8. **Riepilogo**: **Punteggio per criterio** e **Documenti da preparare**.

In fondo, **Azione**:
- **Calcola e salva**: conserva i dati, anche incompleti o con errori, e aggiorna punteggio, importi ed errori. La
  domanda torna subito nella tua scrivania.
- **Registra la domanda**: spunta **Ho capito: dopo la registrazione la domanda non sarà più modificabile**. Se ci
  sono errori bloccanti la domanda torna da te con gli errori; se l'impresa ha già registrato un'altra domanda leggi
  «L'impresa ha già registrato una domanda per questo Avviso». Registrata, ricevi la ricevuta via PEC.
- **Annulla la bozza**: con la conferma, la bozza si chiude e non si recupera.

Dopo la chiusura della compilazione le bozze non registrate si chiudono da sole.

### Acquisisci il codice identificativo
Arriva quando si apre il periodo del codice. Spunta **Acquisisci ora il codice identificativo**: il codice compare
nell'attività successiva. Se non lo acquisisci entro la chiusura del periodo, la domanda non partecipa allo sportello.

### Invia il codice allo sportello
Nel giorno e nell'ora dello sportello scrivi il **Codice identificativo** e premi **Invia allo sportello**. Se il
codice non è accettato leggi il motivo in **Esito dell'ultimo tentativo** e riprova. Accettato, l'ora di arrivo
registrata è in **Esito dell'ultimo tentativo** e la domanda attende gli elenchi.

### Carica i documenti
Arriva quando la domanda è negli elenchi NCD, in posizione utile o subentrata. Trovi **Documenti richiesti** e il
**Termine per il caricamento**. Allega i file, scrivi la **Scadenza del documento di identità** (deve essere futura),
spunta **Ho caricato tutti i documenti dell'elenco** e premi **Conferma l'invio**. Insieme, per tutti gli assi tranne
l'1.2, trovi **Incarica il tecnico**: scrivi i dati del tecnico e i codici fiscali di legale rappresentante e soci;
se il tecnico è uno di loro, l'incarico torna da te con il motivo.

Se il termine passa senza conferma, la domanda decade e ricevi la PEC. Hai 5 giorni per **Presenta l'istanza di
revoca**, con i motivi e gli allegati.

### Richieste della sede
**Rispondi alla richiesta di integrazione** (30 giorni) e **Presenta le osservazioni** (10 giorni, preavviso di
rigetto) arrivano con la PEC: leggi la richiesta, scrivi la risposta, allega i documenti.

## 2. Tecnici asseveratori (portale)

Dopo l'incarico, il supporto collega il tuo studio alla domanda e trovi **Compila e firma la perizia**. Vedi in sola
lettura i dati principali della domanda e il **Modello di perizia** (B1.1, B2, B3, B4 o B5). Compila situazione ante
e post, data certa del DVR, conformità dei preventivi, confronto tra bene nuovo e sostituito; allega la perizia
firmata digitalmente e premi **Firma e invia la perizia**. Dopo l'invio non si modifica più.

## 3. Supporto ISI (gruppo `supporto-isi`)

**Abilita il tecnico sulla domanda**: trovi i dati del tecnico indicato dall'impresa. Scegli lo **Studio del tecnico**
tra le organizzazioni; se non c'è, crealo in **Amministrazione → Organizzazioni** con l'utente del tecnico, poi torna
all'attività.

## 4. Operatori delle sedi INAIL (gruppi `sede-<regione>`)

### Svolgi l'istruttoria
Arriva quando l'impresa ha completato i documenti. Trovi numero, impresa, asse, **Termine dell'istruttoria**,
**Giorni rimanenti**, punteggio e importi voce per voce, i documenti. Spunta le **Verifiche**; correggi i **Criteri
di punteggio** che i documenti non confermano. In **Cosa fai**:

- **Ricalcola**: aggiorna punteggio e contributo e ti ripropone l'attività.
- **Ammetti**: chiude con l'ammissione. Se il punteggio ricalcolato è sotto 130 l'attività torna da te: usa il
  preavviso di rigetto.
- **Ammetti parzialmente**: con **Contributo ammesso (€)** e **Motivazione**.
- **Richiedi integrazione**: scrivi **Cosa chiedi all'impresa**; il termine dell'istruttoria si sospende fino alla
  risposta (o ai 30 giorni).
- **Preavviso di rigetto**: scrivi i motivi; l'impresa ha 10 giorni per le osservazioni, poi l'attività torna da te.
- **Respingi**: con la **Motivazione**.

A 100 giorni ricevi una notifica; a 120 la direzione è avvisata. L'esito parte via PEC con i documenti.

### Valuta l'istanza di revoca
Leggi i motivi e gli allegati, scegli **Accolta** (la domanda passa all'istruttoria) o **Respinta**, con la motivazione.

### Rinuncia
Se l'impresa comunica la rinuncia via PEC, apri la domanda e registra l'arrivo del messaggio **rinunciaImpresa**
dalla scheda della pratica, allegando la PEC.

## 5. Direzione centrale (gruppo `direzione-isi`)

Vedi ed esporti tutte le domande da **Pratiche**. Gli elenchi: **Nuova pratica → Bando ISI: elenchi cronologici**,
scegli **NCD**, **PROVVISORIO** o **DEFINITIVO**, controlla l'anteprima e scegli **Pubblica**: ogni domanda riceve il
suo esito. La pubblicazione non si annulla.

## 6. Domande frequenti

- *Non vedo l'attività.* Le attività dell'impresa le vede chi ha creato la bozza; quelle della sede il gruppo della
  Regione dell'unità produttiva.
- *Ho scritto un dato sbagliato dopo la registrazione.* Non si modifica: la sede può correggere i criteri in istruttoria.
- *La PEC risulta "non inviata".* Il server di posta non è configurato (ambiente di prova): vedi il manuale di esercizio.
