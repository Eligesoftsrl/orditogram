# Orditogram

Piattaforma di processo: l'analista disegna il BPMN e configura i form, Orditogram lo fa funzionare.
Attività per gruppi, documenti con versioni e verifica delle firme, notifiche, storico completo.
Nessun codice da scrivere per un nuovo processo.

**Stack:** JDK 21, Spring Boot 3.5, Activiti 8.8, PostgreSQL, React 19 + Vite 8 + Tailwind 4.

## Avvio con Docker (il modo più semplice)

```
cp .env.example .env        # e cambia almeno le password
docker compose up -d
```

Orditogram risponde su http://localhost:8081 (utente `admin`, password `ADMIN_PASSWORD_INIZIALE` del file `.env`).
Per i dati di prova metti `UTENTI_DI_PROVA=true` e `SERVIZI_DI_PROVA=true`. Con `--profile keycloak` parte anche un
Keycloak di prova su http://localhost:8180.

In produzione imposta `ORDITOGRAM_INDIRIZZO` con il nome con cui gli utenti aprono Orditogram (es.
`orditogram.ente.it`): nginx passa sempre quel nome al backend, che lo usa per i link e per i redirect
dell'accesso con Keycloak o Entra ID, invece dell'intestazione `Host` mandata da chi chiama.

Tutte le impostazioni di `application.yml` si possono dare come variabili d'ambiente (es. `app.calendario.zona`
diventa `APP_CALENDARIO_ZONA`, `spring.mail.host` diventa `SPRING_MAIL_HOST`). Il backend espone
`/actuator/health/liveness` e `/actuator/health/readiness` per Docker e Kubernetes.

## Avvio

Requisiti: JDK 21, Maven 3.9+, Node 18+ (consigliato 22 o superiore), PostgreSQL.

```sql
-- una volta sola, come superutente
CREATE USER orditogram WITH PASSWORD 'orditogram';
CREATE DATABASE orditogram OWNER orditogram;
```

```bash
cd backend && mvn spring-boot:run          # porta 8080
cd frontend && npm install && npm run dev  # porta 5173
```

Apri http://localhost:5173. Le tabelle si creano da sole: quelle del motore (`ACT_*`) le gestisce Activiti, quelle
di Orditogram le migrazioni Flyway (vedi "Aggiornamenti del database"). Con `app.demo.utenti: true`
vengono creati gli utenti di prova (password `password`, tranne `admin` / `admin`):

| Utente | Nome | Gruppi |
|---|---|---|
| laura | Laura Verdi | dirigenti |
| paolo, chiara | Paolo Neri, Chiara Galli | analisti (paolo ha anche il ruolo Analista: entra nello Studio per processi, form e integrazioni) |
| giulia | Giulia Russo | revisori |
| mario, anna | Mario Rossi, Anna Bianchi | operatori (anna anche responsabili) |
| admin | Amministratore | amministratore |

## Test

```
cd backend && mvn verify      # unitari e integrazione: applicazione e motore veri, database H2 in memoria
cd frontend && npm test       # Vitest
```

Il test di integrazione esegue il processo "Accesso agli atti" da capo a fondo con utenti diversi: protocollo via API,
strada di errore, termine di 30 giorni, rinuncia via messaggio, ricerca, registro. Lo stesso gira a ogni modifica su
GitHub Actions (`.github/workflows/ci.yml`), che con un tag `v1.2.3` pubblica anche le immagini Docker.

### Analisi del codice

A ogni modifica il workflow *Analisi del codice* (`.github/workflows/analisi.yml`) passa il codice a strumenti
gratuiti che coprono OWASP Top 10 e CWE Top 25: **SonarQube Community** (avviato dentro la CI, con la copertura dei
test), **Semgrep**, **SpotBugs con FindSecBugs**, **OSV-Scanner** sulle librerie Java e npm (dalla distinta SBOM
CycloneDX) e **npm audit**. Ogni esecuzione mostra un riepilogo e conserva i rapporti tra gli artefatti, da allegare
alle gare. Le soglie bloccanti sono in `strumenti/analisi/riepilogo.py`; in locale:

```
cd backend && mvn -Panalisi verify   # copertura (target/site/jacoco), SpotBugs, SBOM (target/classes/META-INF/sbom)
```

Un falso positivo si segna con il motivo, così resta tracciato per le gare: `// NOSONAR: motivo` o `// nosemgrep`
nel codice, oppure una voce commentata in `strumenti/analisi/spotbugs-esclusioni.xml` o, per le librerie, in
`strumenti/analisi/osv-scanner.toml` (con una data entro cui ricontrollarla). Le soglie sono attive: un problema
sopra soglia ferma la CI. Le azioni di GitHub sono
fissate per impronta (SHA), con la versione nel commento.

**Protezione CSRF.** Il token CSRF di Spring è spento di proposito: l'API `/api/v1` usa solo la chiave
nell'intestazione, e nel resto dell'applicazione ogni richiesta che modifica dati e arriva da un browser (sessione
del provider, cookie, `Origin` o `Sec-Fetch-Site`) deve avere l'intestazione `X-Requested-With`, che un altro sito
non può aggiungere. Le integrazioni server-to-server con nome utente e password non ne hanno bisogno. I messaggi di
log stanno su una riga sola (gli a capo diventano spazi), così nessuno può inserire righe false nei log.

## Aggiornamenti del database

Le tabelle di Orditogram nascono e cambiano solo con le migrazioni in `backend/src/main/resources/db/migration`
(`V1__schema_iniziale.sql`, `V2__...`): all'avvio Flyway applica quelle mancanti e annota quelle fatte nella tabella
`flyway_schema_history`. Hibernate non modifica più lo schema (`ddl-auto: validate`): se un'entità non corrisponde
alle tabelle, Orditogram non parte e il log dice quale colonna manca, invece di scoprirlo con un errore in esercizio.

- Una modifica a un'entità va sempre con una nuova migrazione `V<n>__descrizione.sql`, scritta in SQL che funzioni
  sia su PostgreSQL sia su H2 (i test usano le stesse migrazioni). Una migrazione già rilasciata non si modifica.
- Un database creato da Orditogram 0.12 (o precedente, aggiornato prima alla 0.12) si aggiorna da solo: la prima
  migrazione trova le tabelle già presenti e non le tocca.

## Ruoli e permessi (Studio → Ruoli)

Chi può fare che cosa nello Studio e nell'amministrazione si decide con i **ruoli**, insiemi di permessi:

| Permesso | Che cosa apre |
|---|---|
| Processi (`studio.processi`) | Studio → Processi: bozze, pacchetti, connettori, credenziali |
| Pubblicazione (`studio.pubblica`) | mettere in produzione un processo (senza, si preparano solo le bozze) |
| Form (`studio.form`) | Studio → Form |
| Integrazioni (`integrazioni.gestisci`) | Studio → Integrazioni: chiavi API e webhook |
| Utenti e ruoli (`utenti.gestisci`) | Studio → Utenti, Ruoli e Strutture |
| Organizzazioni (`organizzazioni.gestisci`) | Studio → Organizzazioni: anagrafica delle organizzazioni esterne, anche da CSV |
| Registro (`registro.leggi`) | Studio → Registro |
| Tutte le pratiche (`pratiche.vedi-tutte`) | vedere ogni pratica, anche senza parteciparvi (non lavorarla) |

Ruoli predefiniti: **Amministratore** (tutti i permessi, anche quelli futuri; non si modifica né si elimina),
**Analista** (processi, pubblicazione, form, integrazioni), **Gestore utenti**, **Auditor** (registro e tutte le pratiche). Se ne creano altri
con il pulsante *Nuovo ruolo*. Un ruolo si dà:
- a una persona, in Studio → Utenti;
- a un **gruppo**, nella pagina del ruolo: chi è nel gruppo lo riceve. Funziona anche con i gruppi del provider di
  accesso (Keycloak, Entra ID), così i ruoli si governano dall'IdP dell'ente.

Chi non ha ruoli lavora le attività dei suoi gruppi e vede le sue pratiche, come prima. Regole di sicurezza:
- i permessi si leggono dal database a ogni richiesta: un ruolo tolto vale subito, anche per chi è collegato;
- nessuno dà, toglie o modifica permessi che non ha (un gestore utenti non crea amministratori e non cambia la
  password di un amministratore), nemmeno passando dai gruppi;
- resta sempre almeno un amministratore attivo; nessuno disattiva o elimina sé stesso;
- le sezioni nuove di `/api/admin` sono aperte solo a chi ha tutti i permessi finché non ricevono il loro.

## Chi avvia, vede ed esporta un processo (Studio → Processi → Accesso)

Per ogni processo pubblicato, il pulsante **Accesso** decide, per gruppo o per ruolo:
- **chi può avviarlo**: se c'è almeno una regola, vale al posto di `candidateStarterGroups` del BPMN;
- **chi vede tutte le pratiche** (supervisori, responsabili, controllo), con un ambito: tutte, quelle della propria
  struttura, quelle della propria struttura e delle strutture sotto;
- **chi può esportarle**: se c'è almeno una regola, solo loro (e l'amministratore) esportano l'elenco delle pratiche
  di quel processo.

Chi lavora le attività resta deciso dai gruppi del BPMN; chi ha lavorato una pratica continua a vederla. Le regole
valgono subito per tutte le versioni del processo, vanno nel registro e viaggiano nel pacchetto (`permessi.json`):
all'importazione si applicano solo se il processo nell'ambiente di arrivo non ne ha, tralasciando i ruoli che lì non
esistono.

**Strutture** (Studio → Strutture): gli uffici dell'ente in un albero, ad esempio dipartimento → provveditorati →
istituti. Le persone si mettono nelle strutture dalla pagina Utenti; una pratica prende come struttura competente
quella di chi la avvia (se ne ha una sola). Esempio: il gruppo `dirigenti` vede le pratiche "della propria struttura
e di quelle sotto"; un dirigente del provveditorato vede quelle degli istituti della sua regione, uno del
dipartimento le vede tutte.

L'elenco delle pratiche e l'apertura di una pratica usano la stessa regola, calcolata nel database
(`storico/VisibilitaPratiche`).

## Utenti esterni, organizzazioni e parti

Oltre al personale, Orditogram ha gli **utenti esterni**: cittadini, clienti, professionisti, referenti di enti e
aziende. Si creano in Studio → Utenti scegliendo *Esterno* (oppure arrivano da Keycloak/Entra ID con il gruppo
indicato in `app.oidc.gruppo-esterni`). Un utente esterno:
- non ha mai ruoli né permessi dello Studio;
- è nel gruppo automatico `esterni` (per dire che può avviare un processo dal portale:
  `candidateStarterGroups="esterni"` o una regola *Chi può avviarlo*) e in `org:<id>` per ogni sua organizzazione;
- usa solo le API del portale (scrivania, sue attività, sue pratiche, documenti, notifiche): tutto il resto
  risponde 403, anche le API aggiunte in futuro (`accesso/AccessoEsterni`);
- vede solo le pratiche in cui è **parte** (lui o la sua organizzazione) o in cui ha un'attività da lavorare.

**Organizzazioni** (Studio → Organizzazioni): l'anagrafica delle organizzazioni esterne, con importazione da CSV
(`codice;nome;codiceFiscale`, un codice già presente aggiorna la riga). I loro utenti si aggiungono dalla pagina
Utenti, oppure li aggiunge il referente dell'organizzazione.

**Referente dell'organizzazione**: in Studio → Organizzazioni → *Persone e referenti* chi ha il permesso
organizzazioni sceglie uno o più referenti tra gli utenti esterni dell'organizzazione. Il referente resta un utente
esterno senza ruoli; dal portale, in *La mia organizzazione*:
- vede le persone dell'organizzazione, il loro stato e l'ultimo accesso;
- **aggiunge** un collega con nome, cognome ed email (che diventa il nome utente). Con il login locale riceve una
  password temporanea da comunicare, mostrata una volta sola: al primo accesso il collega deve sceglierne una sua.
  Con un provider OIDC il collega entra con il suo account e si collega da solo, se il provider dichiara verificata
  la sua email;
- **disattiva**, **riattiva** e **toglie** i colleghi. Chi è anche in un'altra organizzazione si può solo togliere;
  chi resta senza organizzazioni viene anche disattivato.

Il referente non può dare ruoli, nominare altri referenti, agire su sé stesso, sugli altri referenti, sugli utenti
interni o su persone di altre organizzazioni; un'organizzazione disattivata lo ferma. Ogni azione va nel registro
(`REFERENTE_NOMINATO`, `UTENTE_ORGANIZZAZIONE_AGGIUNTO`, `…_DISATTIVATO`, `…_RIATTIVATO`, `…_TOLTO`), i tentativi
rifiutati tra gli accessi negati. Limiti: `app.organizzazioni.max-utenti` (200) persone per organizzazione e
`app.organizzazioni.max-aggiunte-ora` (20) aggiunte all'ora per referente.

**Cambio password**: gli utenti locali la cambiano dall'icona a chiave in basso nel menù (almeno 12 caratteri, senza
il nome utente). Con una password temporanea si può solo cambiarla.

**Parti della pratica**: chi la pratica riguarda dall'esterno, ognuno con un ruolo.
- Chi avvia una pratica da utente esterno ne è il **richiedente**.
- Un campo **Organizzazione** di un form (`"type": "organizzazione"`) con *È la parte della pratica*
  (`"parte": "controparte"`) rende l'organizzazione scelta una parte. Il personale la cerca nell'anagrafica.
- Nel BPMN un'attività con `candidateGroups="parte:controparte"` va agli utenti di quella parte (o alla persona,
  per `parte:richiedente`). La pubblicazione controlla che la parte sia indicata da un form.

**Che cosa vede un utente esterno** di una pratica:
- i dati dei campi marcati *Visibile anche alle parti esterne* (`"visibileAlleParti": true`) e quelli dei form che ha
  compilato lui; mai le variabili tecniche;
- nello storico, le sue azioni e, di quelle dell'ufficio, solo avvio, attività completate e parti, a nome
  dell'"Ufficio" e senza valori;
- i documenti **visibili alle parti**: quelli caricati da un esterno, quelli dei form con *I documenti caricati qui li
  vedono anche le parti* (`"documentiVisibilita": "parti"`) e quelli che il personale rende visibili con
  *Mostra alle parti*; gli altri per lui non esistono;
- non vede il diagramma del processo, la struttura competente, le pratiche collegate, i gruppi e i nomi del personale.

**Campi riservati per ruolo**: tra il personale, un campo con *Solo chi ha questi ruoli* (`"ruoliVisibili": ["auditor"]`)
nella scheda della pratica e nello storico lo vede solo chi ha uno di quei ruoli (e l'amministratore); chi lavora
un'attività vede sempre i campi del suo form.

Esempio completo: `esempi/reclamo-controparte` (il cittadino `marco` presenta un reclamo, l'operatore `mario` sceglie
la controparte, `elena` dello Studio legale Bianchi risponde; `elena` è anche la referente dello studio). Con `app.demo.utenti` gli utenti di prova esterni
e l'organizzazione ci sono già.

## Anagrafiche configurabili (Studio → Anagrafiche)

Le anagrafiche sono gli archivi di dati stabili che le pratiche usano: fondi, società, fornitori, immobili, soggetti
vigilati. L'analista ne definisce i **tipi** e il personale autorizzato ne gestisce le **schede**; i form delle
pratiche le scelgono, anche quelli compilati dagli utenti esterni e dall'API pubblica, che legge e scrive le schede
(`/api/v1/anagrafiche`).

**Nell'interfaccia**: lo Studio → *Anagrafiche* elenca i tipi e li modifica con il costruttore dei form (in più le
spunte *Dà il nome alla scheda*, *Chiave* e *Si trova con la ricerca*, e il campo *Anagrafica* per i collegamenti);
*Chi può accedere* imposta le regole. La sezione *Anagrafiche* del menu compare a chi vede almeno un tipo: per ogni
tipo la ricerca, la nuova scheda, l'importazione da CSV e l'esportazione; per ogni scheda dati, versioni (con i
campi cambiati) e collegamenti (schede indicate, schede che la citano, pratiche che la usano).

**Definizione** (`/api/admin/anagrafiche`, permesso `anagrafiche.definisci`, dato anche all'Analista): codice
stabile (`fondi`), nome e campi nello **stesso formato dei form** (sezioni, tabelle, campi condizionali, regole e
formati), con in più:
- `"chiave": true`: il valore identifica la scheda (es. partita IVA); due schede dello stesso tipo non possono
  averlo uguale, senza contare maiuscole e spazi;
- `"cercabile": true`: il valore si trova con la ricerca, oltre al titolo;
- `"type": "anagrafica", "tipoAnagrafica": "societa"`: relazione con una scheda di un'altra anagrafica (o della stessa);
- `"titolo": true` dà il nome alla scheda; `"ruoliVisibili"` riserva un campo a chi ha certi ruoli.

Quando cambiano i campi chiave o ricercabili, l'indice si ricostruisce subito; se un nuovo campo chiave ha valori
ripetuti, la modifica è rifiutata. Ogni modifica della definizione aumenta la versione del tipo e va nel registro.
Un tipo si elimina solo se non ha schede e nessun altro lo collega: altrimenti si disattiva.

**Chi vede e modifica le schede** (`/api/admin/anagrafiche/{codice}/permessi`): regole *sceglie*, *vede*, *modifica*
(comprende vede) ed *esporta*, per gruppi (anche del provider di accesso) o ruoli. Senza regole solo l'amministratore
vede le schede, e il permesso di definire i tipi non dà accesso ai dati. Un tipo o una scheda che l'utente non può
vedere risponde "non trovata", come se non esistesse.

*Sceglie* serve a chi compila un form ma non deve consultare l'archivio: trova le schede attive per nome
(`GET /api/anagrafiche/{codice}/scelta?testo=`, al massimo 30, solo numero e titolo) e le sceglie nei form; non apre
le schede, non le vede nell'elenco e il tipo non compare nel menu. Chi vede le schede le sceglie anche. Per gli
**utenti esterni** conta solo *sceglie*, data al gruppo `esterni` (tutti) o `org:<id>` (gli utenti di
un'organizzazione); le altre regole e quelle per ruolo per loro non valgono, e del resto delle anagrafiche non
raggiungono nulla.

**Schede** (`/api/anagrafiche`):
```
GET  /api/anagrafiche                                  tipi che posso usare, con le mie azioni
GET  /api/anagrafiche/fondi/schede?testo=&stato=&pagina=&dimensione=   ricerca nel titolo e nei campi ricercabili
GET  /api/anagrafiche/fondi/schede?campo=codice&valore=F1              valore esatto di un campo chiave o relazione
POST /api/anagrafiche/fondi/schede            { "dati": { ... } }
GET  /api/anagrafiche/fondi/schede/{id}       dati, schede collegate e schede che la citano
PUT  /api/anagrafiche/fondi/schede/{id}       { "versione": 3, "dati": { ... }, "nota": "..." }
POST /api/anagrafiche/fondi/schede/{id}/cessa      { "versione": 4, "nota": "motivo" }   (e /riattiva)
GET  /api/anagrafiche/fondi/schede/{id}/versioni
```
I dati passano dalle stesse regole dei form; gli errori arrivano tutti insieme, campo per campo
(`{ "message", "campi": [ { "campo", "messaggio" } ] }`). La `versione` è quella letta: se nel frattempo qualcuno ha
modificato la scheda, la risposta è 409 e si ricarica. Una relazione punta solo a schede attive di un tipo che chi
scrive può vedere. I campi riservati non si vedono (neanche nelle versioni) e non si cambiano: chi non li vede
modifica il resto e i loro valori restano. Le schede non si cancellano: si **cessano** (con una nota) e si possono
riattivare.

**Nelle pratiche**: un campo di form `{ "name": "fondo", "type": "anagrafica", "tipoAnagrafica": "fondi" }` fa
scegliere una scheda (il valore è il suo id). Quando il form si invia:
- la scheda deve esistere, essere del tipo indicato, attiva e sceglibile da chi compila (regola *sceglie* o *vede*;
  per l'API pubblica, un'anagrafica della chiave con il permesso `anagrafiche:leggi`); altrimenti "scheda non trovata";
- la pratica si **collega** alla scheda con una **fotografia** dei dati di quel momento, senza i campi riservati: se
  la scheda cambia dopo, la pratica resta legata ai dati su cui si è deciso;
- nelle variabili del processo vanno anche `fondo_titolo` e `fondo_dati`, così BPMN, gateway ed email li usano:
  `${fondo_dati.tipologia == 'Negoziale'}`;
- con `"titolo": true` il campo dà alla pratica il titolo della scheda.

La scheda della pratica (`GET /api/pratiche/{id}`, voce `anagrafiche`) mostra il titolo e i dati fotografati e, a chi
può vedere la scheda, anche titolo e stato attuali. Dalla scheda, `GET /api/anagrafiche/fondi/schede/{id}/pratiche`
elenca le pratiche collegate che l'utente vede. Un'attività successiva può cambiare la scheda scelta: il collegamento
si aggiorna e lo storico conserva il passaggio. Lo Studio rifiuta un form che sceglie da un'anagrafica che non esiste,
e un'anagrafica usata da un form non si elimina.

**Nuova scheda dal form**: con *Si può creare una scheda nuova da qui* (`"creaScheda": true` sul campo) chi ha la
regola *modifica* vede il pulsante *Nuova scheda*: si apre il form dell'anagrafica sopra quello della pratica, la scheda
si crea con le solite regole e resta scelta nel campo. Accanto alla scheda scelta, *Apri la scheda* (per chi la vede)
la apre in un'altra scheda del browser, dove si aggiorna.

**Importazione da CSV** (`POST /api/anagrafiche/fondi/importa`, regola *modifica*, al massimo 5.000 righe e 10 MB):
prima riga con le etichette o i nomi dei campi, separatore punto e virgola o virgola; date anche GG/MM/AAAA, caselle
sì/no, numeri all'italiana. Se c'è una colonna chiave, la riga con una chiave già presente aggiorna quella scheda
(una cella vuota svuota il campo), altrimenti crea una scheda nuova. Ogni riga si salva da sola: le righe sbagliate
sono scartate con il motivo e le altre restano. Le colonne delle tabelle, dei campi in sola lettura e quelle col nome
della scheda collegata (`Fondo (nome)`) si ignorano, così il file esportato si reimporta così com'è.

**Esportazione** (`GET /api/anagrafiche/fondi/esporta`, regola *esporta*, al massimo 20.000 schede): tutte le schede
con i dati che l'utente vede; l'interfaccia ne fa Excel, CSV o PDF. Nei CSV un testo che inizia con `=`, `+`, `-` o
`@` ha un apostrofo davanti, così Excel non lo esegue come formula. Importazioni ed esportazioni vanno nel registro.

**API pubblica** (`/api/v1/anagrafiche`, chiave API): la chiave elenca le anagrafiche a cui accede (nessuna per
default) e ha il permesso `anagrafiche:leggi` o `anagrafiche:scrivi` (che comprende la lettura). Chi crea la chiave
deve vedere quelle anagrafiche (e modificarle, se la chiave scrive). Per il sistema un tipo non elencato non esiste.
```
GET  /api/v1/anagrafiche                                   tipi della chiave, con i campi
GET  /api/v1/anagrafiche/fornitori/schede?testo=&campo=&valore=&stato=&pagina=&dimensione=
GET  /api/v1/anagrafiche/fornitori/schede/{id}
POST /api/v1/anagrafiche/fornitori/schede        { "dati": { ... } }                          → 201 e Location
PUT  /api/v1/anagrafiche/fornitori/schede/{id}   { "versione": 2, "dati": { ... }, "nota": "..." }
```
Stesse regole dell'interfaccia: dati controllati (422 `dati_non_validi` con i campi), chiavi uniche, versione letta
(409 `conflitto`), niente campi riservati ai ruoli né esportazione. Avviando una pratica dall'API, un campo anagrafica
accetta l'id di una scheda attiva di un'anagrafica della chiave. L'OpenAPI generata descrive tutto.

**Pacchetti**: l'esportazione di un processo include le definizioni delle anagrafiche usate dai suoi form (e di quelle
che collegano), in `anagrafiche/<codice>.json`, senza schede né permessi. All'importazione si creano quelle che mancano
(serve anche il permesso `anagrafiche.definisci`); quelle già presenti restano come sono.

## Backup e ripristino

`strumenti/backup.sh` salva database e documenti in una cartella datata (con Docker Compose o, con `MODO=locale`,
su un'installazione tradizionale); `strumenti/ripristino.sh <cartella>` li rimette al loro posto dopo una conferma.
Conserva i backup fuori dal server.

## Registro delle azioni

Studio, Registro: pubblicazioni di processi, form salvati ed eliminati, utenti e ruoli creati, modificati ed eliminati,
pacchetti importati ed esportati, accessi negati e accessi con il provider, con utente, ora e indirizzo.
Le stesse righe vanno anche nel log dell'applicazione (`orditogram.registro`) per chi usa un SIEM.

## Ripartire da zero con le pratiche

`strumenti/svuota-pratiche.sql` elimina tutte le pratiche (in corso e concluse) con storico, attività, timer,
notifiche e documenti; lascia processi pubblicati, bozze, form e utenti. Ferma il backend, esegui lo script sul
database `orditogram` (in DBeaver: Esegui script) e svuota la cartella dei documenti (`backend/data/documenti`).
È in una transazione: o va tutto a buon fine o non cambia nulla.

## Provare l'esempio "Verifica pratica"

1. Entra come **admin** → Studio → **Processi** → Carica BPMN.
   BPMN: `esempi/verifica-pratica/verifica-pratica.bpmn20.xml`, form: i tre `vp-*.json`.
   Poi aprilo con **Modifica** per vederlo nell'editor.
2. Entra come **laura** → Nuova pratica → Verifica pratica. Allega qualche file da
   `esempi/verifica-pratica/file-di-prova/` (firme valide, alterate, scadute, buste annidate).
3. Entra come **paolo**: la trovi in "Disponibili per il tuo gruppo". Guarda gli esiti delle firme,
   scegli "Da revisionare" e scrivi i rilievi.
4. Entra come **giulia**: prendi in carico, carica una nuova versione del documento con firma non valida, rimanda.
5. Di nuovo **paolo**: "Accettata". **laura** riceve la notifica (campanella).

I file di prova sono firmati con un certificato di test generato apposta, non con un certificato qualificato.

## Provare l'esempio "Accesso agli atti"

Un procedimento amministrativo completo (legge 241/1990): protocollo via API, email al cittadino, sollecito settimanale,
termine di 30 giorni con silenzio-rigetto, controinteressati con 10 giorni per opporsi, sottoprocesso di integrazioni.
Istruzioni passo passo in [`esempi/accesso-atti/README.md`](esempi/accesso-atti/README.md).

## Editor dei processi (Studio, Processi)

Il modo normale di lavorare: **Nuovo processo** (basta il nome) oppure **Modifica** su un processo esistente.

- **Tela**: palette in italiano con i soli elementi che Orditogram esegue (inizio, fine, attività di una persona,
  attività automatica, decisione, attività in parallelo, sottoprocesso, attesa, note). Accanto a ogni elemento le icone per aggiungere il passo successivo.
- **Pannello a destra**, per l'elemento selezionato:
  processo (nome, descrizione, chi può avviarlo), inizio (form di avvio), attività (gruppi e form),
  attività automatica (connettore dal catalogo e i suoi parametri, con inserimento dei dati della pratica),
  decisione (le strade possibili), freccia (condizione guidata *campo, operatore, valore* oppure "altrimenti"),
  sottoprocesso, attesa, scadenza, sollecito, strada di errore.
- **Scadenze, solleciti, errori**: dal pannello di un'attività o di un sottoprocesso, *+ Scadenza*, *+ Sollecito*, *+ Se non riesce*
  aggiungono l'evento sul bordo già collegato a un passo successivo (il sollecito arriva con la sua notifica). Il tempo si imposta
  in parole: "dopo 30 giorni", "alla data del campo Scadenza", "ogni 7 giorni, al massimo 3 volte".
- **"Nuovo form" / "Modifica il form"** aprono il costruttore senza lasciare l'editor; al salvataggio il form è già collegato.
- **Controllo continuo**: in basso errori e avvisi, cliccabili; gli elementi con problemi sono evidenziati nel diagramma.
- **Bozza automatica**: ogni modifica viene salvata come bozza; la versione in produzione non cambia finché non premi **Pubblica**.
  "Scarta bozza" torna alla versione in produzione.
- **Pubblica**: controllo sul server e messa in produzione come nuova versione, senza riavvii.

### Simulazione

**Simula** (barra in alto) fa scorrere dei gettoni sul diagramma, senza salvare nulla: si avviano pratiche di prova,
si sceglie la strada alle decisioni, si fanno scattare scadenze, solleciti, errori, attese e messaggi cliccando ▶
sull'evento. Il pannello a destra racconta in italiano cosa succede a ogni pratica di prova. Per default il gettone si
ferma sulle attività delle persone, così si possono provare le scadenze. Basata su bpmn-js-token-simulation (licenza MIT).

### Pacchetti

**Esporta pacchetto** produce un file `.orditogram.zip` con il BPMN e tutti i form usati.
**Importa pacchetto** lo carica come bozza (se un form esiste già con contenuto diverso viene chiesto se sovrascriverlo):
lo controlli nell'editor e lo pubblichi. È il modo per preparare i processi sulla tua installazione e portarli dal cliente,
o per passare da test a produzione.

### Connettori pronti

| Connettore | Cosa fa | Parametri principali |
|---|---|---|
| **Invia notifica** (`notificaConnector`) | campanella di Orditogram (ed email agli utenti, se configurata) | a chi, titolo, messaggio |
| **Invia email** (`emailConnector`) | email anche a esterni, con i documenti allegati | a chi: `iniziatore`, `gruppo:x`, `utente:x`, `campo:emailRichiedente`, `email:ufficio@ente.it`; oggetto; testo; allegati |
| **Chiama un servizio** (`apiConnector`) | richiesta REST a un sistema esterno, salva i valori della risposta | metodo, indirizzo, credenziale, intestazioni, corpo, *dati da salvare* (`numeroProtocollo = numero`), attesa massima, cosa fare se non riesce |

In indirizzo, intestazioni, corpo e testi si inseriscono i dati della pratica con `${nomeDato}`: nell'indirizzo vengono
codificati, nel corpo resi sicuri per il JSON. I valori letti dalla risposta diventano dati della pratica, utilizzabili
nei form, nei testi e nelle condizioni. Percorsi nella risposta: `numero`, `esito.codice`, `elenco[0].id`, `@stato` (codice HTTP), `@corpo`.

**Se la chiamata non riesce** (servizio irraggiungibile, timeout, codice diverso da 2xx):
- *Segui la strada di errore*: la pratica prosegue sulla freccia dell'evento "Se non riesce" sul bordo (dell'attività o del
  sottoprocesso che la contiene); il motivo è nello storico. Se l'evento manca, Orditogram si comporta come "Blocca".
- *Blocca e riprova più tardi*: l'operazione viene annullata (l'utente vede il messaggio e può riprovare); se la chiamata
  partiva da un timer, il motore la ritenta.

**Credenziali**: token e password non stanno mai nel BPMN né nei pacchetti. Si configurano sul server e l'analista sceglie solo il nome:

```yaml
app:
  credenziali:
    protocollo:
      tipo: header              # bearer | basic | header
      intestazione: X-API-Key
      valore: ${PROTOCOLLO_API_KEY}
      descrizione: Protocollo informatico
```

Per provare senza un sistema reale c'è un servizio finto: `POST /api/demo/protocollo` (credenziale `protocollo-demo`,
`?simula=errore` per ottenere un 503). Si disattiva con `app.demo.servizi: false`.

### Scrivere un nuovo connettore

Un connettore è un bean Spring che implementa `Connector` (Activiti) e `ConnettoreOrditogram` (catalogo):
compare da solo nel pannello delle attività automatiche, con il modulo dei suoi parametri.

```java
@Component("protocolloConnector")
public class ProtocolloConnector implements Connector, ConnettoreOrditogram {
    public String id() { return "protocolloConnector"; }
    public String nome() { return "Protocolla la pratica"; }
    public String descrizione() { return "Registra la pratica nel sistema di protocollo e salva il numero."; }
    public List<Parametro> parametri() {
        return List.of(Parametro.testo("registro", "Registro", true, "Es. GENERALE"));
    }
    public Set<String> datiProdotti(Map<String, String> valori) { return Set.of("numeroProtocollo"); }
    public IntegrationContext apply(IntegrationContext ctx) {
        Map<String, String> p = supporto.parametri(ctx);           // SupportoConnettori: le activiti:field del BPMN
        Map<String, Object> dati = SupportoConnettori.dati(ctx);    // dati della pratica
        ctx.addOutBoundVariable("numeroProtocollo", "2026/000123");
        return ctx;
    }
}
```

Tipi di parametro disponibili per il modulo dell'editor: `testo`, `testoLungo`, `codice`, `scelta` (con opzioni),
`destinatario`, `destinatariEmail`, `mappatura`, `credenziale`. Per seguire la strada di errore il connettore lancia
`new BpmnError("CODICE", "messaggio")`.

## I mattoncini che configura l'analista

Tutto quello che segue si imposta dall'editor e dal costruttore dei form; le tabelle mostrano cosa finisce nel BPMN e nel JSON.

### Nel BPMN

| Cosa | Come |
|---|---|
| Chi vede un'attività | `activiti:candidateGroups="analisti"` sullo userTask |
| Quale form mostra | `activiti:formKey="vp-verifica"` sullo userTask o sullo startEvent |
| Chi può avviare il processo | `activiti:candidateStarterGroups="dirigenti"` sul process (se manca: tutti) |
| Decisioni | gateway esclusivo con condizione sui campi del form, es. `${esito == 'Accettata'}` |
| Notifica | serviceTask `implementation="notificaConnector"` con i parametri sotto |
| Scadenza | boundaryEvent con `timerEventDefinition` (`timeDuration` P30D, `timeDate` `${campo}`) sul bordo: chiude l'attività e segue la sua freccia |
| Sollecito | come la scadenza ma `cancelActivity="false"`, anche ripetuto (`timeCycle` R3/P7D): l'attività resta aperta |
| Attesa | intermediateCatchEvent con `timerEventDefinition` nel flusso |
| Sottoprocesso | subProcess con un inizio e almeno una fine; scadenze ed errori sul suo bordo valgono per tutti i passi interni |
| Strada di errore | boundaryEvent con `errorEventDefinition` su un'attività automatica, un sottoprocesso o un processo richiamato |
| Processo richiamato | callActivity con `calledElement="chiaveDelProcesso"`: apre una pratica di un altro processo pubblicato e aspetta che finisca |

**Processi richiamati.** Un processo può richiamarne un altro già pubblicato (nell'editor: *Processo richiamato*,
poi la scelta del processo). Quando la pratica arriva lì si apre una pratica figlia del processo scelto, con tutti i
dati della madre e lo stesso titolo; la madre aspetta. Quando la figlia finisce, i dati raccolti dalla figlia (es.
un esito) tornano alla madre e si usano nelle decisioni e nei form successivi. Si usa sempre l'ultima versione
pubblicata del processo richiamato. Nella scheda della pratica il riquadro *Pratiche collegate* porta dall'una
all'altra; chi può vedere la madre vede anche le figlie. La pubblicazione è rifiutata se il processo richiamato non
è pubblicato, se un processo richiama sé stesso o se due processi si richiamano a vicenda.

```xml
<serviceTask id="notifica" name="Notifica al dirigente" implementation="notificaConnector">
  <extensionElements>
    <activiti:field name="destinatario" stringValue="iniziatore"/>  <!-- iniziatore | username | gruppo:nome, anche più valori separati da virgola -->
    <activiti:field name="titolo" stringValue="Pratica accettata: ${oggetto}"/>
    <activiti:field name="messaggio" stringValue="Accettata da ${chiusaDa}."/>
  </extensionElements>
</serviceTask>
```

### Nei form (Studio → Form, costruttore visuale)

| Proprietà del form | Effetto |
|---|---|
| `documenti: "carica"` | nel form di avvio: area per allegare i documenti |
| `documenti: "lettura"` | l'attività mostra i documenti della pratica |
| `documenti: "modifica"` | chi ha in carico l'attività può aggiungere documenti e nuove versioni |
| `documentiObbligatori: true` | almeno un allegato all'avvio |
| `documentiVisibilita: "parti"` | i documenti caricati con questo form li vedono anche le parti esterne |

| Proprietà di un campo | Effetto |
|---|---|
| `type` | text, textarea, number, date, select, checkbox, tabella, organizzazione (un'organizzazione esterna dall'anagrafica) |
| `parte: "controparte"` | campo Organizzazione: l'organizzazione scelta diventa quella parte della pratica |
| `visibileAlleParti: true` | il dato lo vedono anche gli utenti esterni che vedono la pratica |
| `ruoliVisibili: ["auditor"]` | tra il personale, nella scheda della pratica lo vede solo chi ha uno di quei ruoli |
| `required` / `readonly` | obbligatorio / mostrato nel riepilogo in sola lettura |
| `precompila: false` | non ripropone il valore precedente (utile nei cicli verifica ↔ revisione) |
| `titolo: true` | il valore diventa il titolo della pratica |
| stesso `name` in più form | è lo stesso dato della pratica |
| `visibileSe: { campo, valori }` | il campo compare solo se l'altro campo ha uno dei valori indicati |
| `min`, `max` | numero: valori ammessi; data: `AAAA-MM-GG` oppure `oggi` |
| `minLunghezza`, `maxLunghezza` | testo: numero di caratteri |
| `formato` | testo: `email`, `codiceFiscale`, `partitaIva`, `iban`, `cap`, `telefono`, `espressione` (con `espressione` e `messaggioFormato`) |
| `colonne`, `minRighe`, `maxRighe` | tabella: le colonne (campi senza testo lungo) e quante righe servono |

Il backend accetta solo i campi modificabili dichiarati nel form e controlla gli obbligatori:
un utente non può impostare dati che il form non prevede.

**Regole di validazione.** Nel costruttore, sotto ogni campo: valore minimo e massimo (numeri e date, anche
"oggi"), caratteri minimi e massimi, formato. Codice fiscale (anche di un ente), partita IVA e IBAN si controllano
con il carattere di controllo. Chi compila vede il messaggio accanto al campo; il server ripete gli stessi controlli
all'avvio, al completamento delle attività e nell'API pubblica, e la documentazione OpenAPI li riporta
(`minimum`, `maxLength`, `format`, `pattern`). Un'espressione personalizzata si prova con un limite di passi:
una scritta male non può bloccare il server. Lo Studio rifiuta regole incoerenti (minimo oltre il massimo,
espressione non valida, formato su un campo che non è di testo).

**Sezioni.** Un elemento `{ "type": "sezione", "label": "Dati del richiedente", "help": "…" }` apre una sezione:
i campi che seguono, fino alla sezione successiva, compaiono sotto quel titolo. Non è un dato della pratica.
Nel costruttore si aggiunge con il pulsante *Sezione*; una sezione senza campi visibili non si mostra.

**Campi condizionali.** Nel costruttore, *Mostra solo se* lega un campo a un campo a scelta o sì/no:

```json
{ "name": "noteIntegrazione", "label": "Cosa chiedere", "type": "textarea", "required": true,
  "visibileSe": { "campo": "esito", "valori": ["Serve integrazione"] } }
```

Per un sì/no i valori sono `"true"` o `"false"`. La condizione può guardare anche un campo in sola lettura
(vale il dato già presente nella pratica). Un campo nascosto non è obbligatorio e il suo valore non viene
salvato, né dall'interfaccia né dall'API pubblica; chi dipende da un campo nascosto è nascosto anche lui.
Nella documentazione OpenAPI un campo condizionale non è mai tra gli obbligatori e la sua descrizione
spiega quando si usa. Lo Studio rifiuta condizioni su campi che non esistono nel form.

**Tabelle di righe.** Un campo `tabella` raccoglie più righe con le stesse colonne, come le voci di una spesa o i
componenti di una famiglia. Nel costruttore: tipo *Tabella*, poi le colonne (testo, numero, data, scelta, sì/no)
con obbligo e regole come i campi normali, e se serve il numero minimo e massimo di righe.

```json
{ "name": "voci", "label": "Voci di spesa", "type": "tabella", "required": true, "maxRighe": 20,
  "colonne": [
    { "name": "descrizione", "label": "Descrizione", "type": "text", "required": true },
    { "name": "importo", "label": "Importo", "type": "number", "required": true, "min": 0.01 } ] }
```

Chi compila aggiunge e toglie righe; le righe lasciate in bianco non si salvano. Il dato della pratica è un
elenco di oggetti (`[{ "descrizione": "Treno", "importo": "45.50" }]`), che le attività successive ricevono e
possono mostrare in sola lettura; la scheda della pratica lo mostra come tabella. Gli errori indicano riga e
colonna ("riga 2, Importo: deve essere almeno 0,01"). Nell'API pubblica una tabella è un array di oggetti, con le
colonne, gli obbligatori e i limiti di righe nell'OpenAPI; una colonna non prevista è un errore. Al massimo 20
colonne e 500 righe. Una tabella non può fare da titolo della pratica né da condizione di un altro campo.

## Documenti e firme

- Ogni caricamento crea una versione; le precedenti restano consultabili e scaricabili.
- I file sono salvati in `app.documenti.dir` (default `./data/documenti`), una sola copia per contenuto (SHA-256).
- Verifica automatica: **PAdES** (anche più firme e marche temporali), **CAdES** `.p7m`
  (anche annidate e in Base64, con estrazione del documento originale).
  Per ogni firma: firmatario, codice fiscale, emittente, data, integrità, validità del certificato,
  modifiche al PDF dopo l'ultima firma.
- **Limite**: è una verifica tecnica. Non controlla la catena di fiducia verso gli elenchi AgID/UE
  né la revoca dei certificati; per la validazione legale serve un servizio qualificato (es. DSS).
  Nemmeno la conservazione a norma è coperta: richiede un conservatore accreditato.
- L'archivio è dietro l'interfaccia `ArchivioDocumenti`: per usare Alfresco o uno storage S3
  basta una nuova implementazione.

## Lavorare senza passaggi inutili

- Un'attività libera del proprio gruppo si lavora subito: si compila e si conferma, e diventa propria in quel momento.
  "Riservala a me" serve solo per segnalarla ai colleghi prima di iniziare. Anche caricare un documento
  la prende in carico.
- Dopo l'avvio di una pratica, o dopo aver completato un'attività, se il passo successivo della stessa pratica tocca
  ancora a te Orditogram ci porta direttamente lì, senza ripassare dalla scrivania.

## Esportazioni

Le tabelle (Pratiche, Processi, Form, Utenti) hanno il pulsante **Esporta**: Excel (.xlsx), CSV (separatore `;`,
si apre direttamente in Excel in italiano) e PDF con intestazione, filtri applicati e numeri di pagina.
Si esportano tutte le righe filtrate dalla ricerca, non solo quelle visibili.

## Termini in giorni lavorativi

Nel pannello di una scadenza o di un'attesa, "Come si contano":
- **giorni di calendario**;
- **giorni lavorativi**: salta sabati, domeniche, festività nazionali (Pasqua e Lunedì dell'Angelo compresi) e quelle dell'ente;
- **di calendario, prorogato se festivo**: come i termini di legge, se l'ultimo giorno è festivo si va al primo giorno lavorativo.

Per le scadenze "alla data di un campo" c'è la stessa proroga. Il pannello mostra quando scadrebbe una pratica che arrivasse oggi.
Festività dell'ente (patrono, chiusure) e fuso orario in `app.calendario` (`application.yml`).
Nel BPMN: `<timerEventDefinition activiti:businessCalendarName="lavorativi">`. Le ripetizioni (solleciti "ogni N giorni")
restano in giorni di calendario.

## Messaggi in arrivo

L'evento **Messaggio in arrivo** (palette, o "+ Se arriva un messaggio" sul bordo di un'attività) mette la pratica in attesa
di un segnale esterno: il pagamento confermato, la PEC del cittadino, la rinuncia dal portale. Il messaggio arriva:
- da un **sistema esterno**: `POST /api/messaggi/{nome}` con l'intestazione `X-API-Key` (chiavi in `app.messaggi.chiavi`),
  indicando la pratica con `"pratica": "<id>"` oppure con i suoi dati, es. `"cerca": {"numeroProtocollo": "2026/000123"}`;
- da un **operatore**: nella scheda della pratica, "Registra arrivo" (amministratore, chi l'ha avviata o chi ne lavora un'attività).

I dati che il messaggio può portare si dichiarano nel pannello ("Dati che porta") e diventano dati della pratica;
gli altri vengono rifiutati, così un sistema esterno non può modificare il resto della pratica.

## API pubblica e webhook (Studio → Integrazioni)

Per i sistemi che lavorano con Orditogram: portali per cittadini o clienti, gestionali, CRM, app.

**Chiavi.** Ogni sistema ha la sua chiave, creata nella scheda *Chiavi API* con i permessi necessari
(`pratiche:avvia`, `pratiche:leggi`, `documenti:leggi`, `documenti:scrivi`, `messaggi:invia`) e, se serve,
solo per alcuni processi. Il valore (`iter_…`) si vede una volta sola; si può sospendere, rinnovare e revocare.
Si invia nell'intestazione `X-API-Key` (o `Authorization: Bearer`).

**Documentazione.** Si genera dai processi pubblicati: scheda *Documentazione API* nello Studio, oppure
`GET /api/v1/openapi.json` con una chiave (da importare in Postman, Swagger o in un generatore di client).

```
# chi sono
curl http://localhost:8080/api/v1/io -H "X-API-Key: iter_..."

# avvia una pratica (i dati sono i campi del form di avvio)
curl -X POST http://localhost:8080/api/v1/processi/accessoAtti/pratiche \
  -H "X-API-Key: iter_..." -H "Content-Type: application/json" \
  -d '{"riferimento": "DOM-2026-0042", "dati": {"oggetto": "Verbali del consiglio", ...}}'

# stato della pratica
curl http://localhost:8080/api/v1/pratiche/<id> -H "X-API-Key: iter_..."
```

Il `riferimento` è l'identificativo del sistema chiamante: se la stessa richiesta arriva due volte
(es. dopo un timeout) Orditogram restituisce la pratica già creata. Gli errori hanno sempre la forma
`{"errore": {"codice", "messaggio", "campi"}}`.

**Webhook.** Nella scheda *Webhook* si indica l'indirizzo da avvisare e gli eventi che interessano.
Ogni avviso è una POST firmata (`X-Orditogram-Firma: t=…,v1=…`, HMAC-SHA256 di `t.corpo` con il segreto del webhook):
la pagina mostra il codice per verificarla in Node.js, Python, Java e PHP. Gli avvisi vengono scritti nella stessa
transazione della pratica e consegnati da una coda: se il destinatario non risponde Orditogram riprova
(1, 5, 30 minuti, 2, 6, 12, 24 ore). Nella scheda si vedono le consegne, con corpo e risposta, e si possono ripetere.
Per provare senza un sistema esterno c'è il ricevitore di prova (`/api/demo/webhook`, con i servizi di prova attivi).

In produzione imposta `ORDITOGRAM_CHIAVE_CIFRATURA` (una frase lunga e casuale, es. `openssl rand -base64 32`):
cifra i segreti dei webhook nel database. Non cambiarla dopo averla impostata.

Impostazioni (`app.api`, `app.webhook` in `application.yml`): richieste al minuto per sistema, siti autorizzati
a chiamare l'API dal browser (CORS), intervallo e timeout degli invii, giorni di conservazione delle consegne.

## Accesso con Keycloak o Microsoft Entra ID

Basta configurare il provider in `application.yml` (esempi commentati in `spring.security.oauth2.client`): nella pagina
di accesso compare "Accedi con …". Al primo accesso l'utente viene creato in Orditogram (o collegato a quello con lo stesso nome,
mai a un utente locale con ruoli) e a ogni accesso nome, email e gruppi vengono aggiornati dal token (`app.oidc`).
Il gruppo indicato in `gruppo-admin` dà o toglie il ruolo Amministratore; quello in `gruppo-esterni` rende l'utente
esterno (senza ruoli, solo il portale); gli altri ruoli si legano ai gruppi in Studio → Ruoli:
- Keycloak: aggiungi al client il mapper "Group Membership" (claim `groups`), oppure usa `claim-gruppi: realm_access.roles`;
- Entra ID: i gruppi arrivano come ID: traducili con `mappa-gruppi`; con molti gruppi conviene usare gli "app roles"
  (`claim-gruppi: roles`).

Indirizzo di ritorno da registrare sul provider: `<indirizzo di Orditogram>/login/oauth2/code/<nome>`
(in sviluppo `http://localhost:5173/login/oauth2/code/keycloak`). `app.login.locale: false` toglie l'accesso con password.
Dietro un reverse proxy imposta `server.forward-headers-strategy: framework`.

## Scadenze nell'applicazione

- **Scrivania, attività e pratiche** mostrano le scadenze: "scade domani" (in giallo sotto i 2 giorni), "scaduta" in rosso,
  i solleciti programmati e, per le pratiche in attesa, quando riprenderanno.
- **Storico**: oltre alle azioni delle persone registra gli eventi automatici: scadenze e solleciti scattati, email
  (inviate o non inviate, con il motivo), chiamate ai servizi esterni con i valori ricevuti, errori.
- I timer sono eseguiti dal motore (`spring.activiti.async-executor-activate: true`, già attivo).
- I termini possono essere in giorni di calendario, lavorativi o prorogati se festivi (vedi "Termini in giorni lavorativi").

## Notifiche via email

Togli il commento a `spring.mail` in `application.yml` e indica l'email degli utenti nella pagina Utenti.
L'email parte solo dopo che l'attività è stata salvata davvero. Senza server email il connettore "Invia email"
non blocca la pratica: annota nello storico che l'email non è stata inviata.

## Costruire una soluzione con Claude (skill `orditogram-soluzione`)

Nel repository c'è una skill di Claude (`.claude/skills/orditogram-soluzione/`) che porta dalle specifiche di un
cliente a una soluzione pronta da collaudare. Basta chiedere a Claude, nel repository, per esempio: *"Costruisci la
soluzione Orditogram per queste specifiche"* allegando documenti o BPMN degli analisti. Claude:

1. scrive `analisi.md`: attori e gruppi, passi, dati, termini, comunicazioni, integrazioni, cosa Orditogram non sa
   fare (richieste di sviluppo), lacune della specifica con le scelte fatte e le domande per il cliente;
2. genera `processo.bpmn20.xml`, `form/*.json`, `anagrafiche/*.json`, `permessi.json`, `permessi-anagrafiche.json`;
3. li controlla con `script/verifica.py` (stesse regole e messaggi della pubblicazione) e li disegna
   (`script/disposizione.py`, `script/anteprima.mjs` per l'immagine);
4. li collauda su un Orditogram avviato con `script/collaudo.py`: un utente per gruppo, ogni strada del processo,
   scadenze fatte scattare subito, strade di errore con varianti, controlli su attività, dati, storico e visibilità;
   il report dice anche quali passi del diagramma nessuno scenario ha percorso;
5. scrive la documentazione per il cliente (`script/documenta.py` genera la scheda tecnica dagli artefatti) e la
   checklist per l'analista, che deve solo verificare;
6. crea il pacchetto `.orditogram.zip` (`script/pacchetto.py`) e, se richiesto, lo installa (`script/installa.py`).

Un esempio completo, dalle specifiche alla documentazione, è in `.claude/skills/orditogram-soluzione/esempio/`.
La skill va aggiornata insieme a ogni funzione che cambia i formati (vedi `CLAUDE.md`).

## Struttura

```
backend/src/main/java/com/eligesoft/orditogram/
  BpmController            processi e attività (avvio con allegati, completamento con controlli)
  storico/                 registro eventi, pratiche (elenco, dettaglio, percorso), scadenze, permessi di accesso
  documento/               documenti, versioni, archivio su disco, verifica firme
  notifica/                notifiche in app, connettori notificaConnector ed emailConnector
  integrazione/            connettore API REST, credenziali, servizio di prova /api/demo
  messaggio/               messaggi in arrivo da sistemi esterni o registrati a mano
  calendario/              giorni lavorativi, festività, calendari del motore
  auth/                    accesso OIDC (Keycloak, Entra ID), sincronizzazione di utenti e gruppi
  form/                    form su Postgres (validazione)
  process/                 pubblicazione a caldo, validazione (ProcessValidator)
  bozza/                   bozze dei processi, esporta/importa pacchetti
  connettore/              catalogo dei connettori (ConnettoreOrditogram), supporto comune, sostituzione dei ${dati}
  user/                    utenti, gruppi, login
frontend/src/
  pages/                   Scrivania, Pratiche, scheda pratica, attività, Studio (processi, editor, form, utenti)
  components/editor/       pannello guidato dell'editor, condizioni, timer, scadenze ed errori sul bordo
  lib/bpmn/                estensioni activiti e orditogram per bpmn-js, palette, traduzioni, simulazione
  components/              binario dell'iter, form dinamico, documenti, storico, diagramma, notifiche
esempi/                    processi di esempio pronti da caricare, file firmati di prova
```

I font sono inclusi nell'applicazione: il frontend non contatta server esterni.
