# Novità di Orditogram

Le versioni seguono il formato MAGGIORE.MINORE.CORREZIONE: cambia la MINORE per funzioni nuove compatibili,
la MAGGIORE quando un aggiornamento richiede interventi (indicati qui sotto).

## Non rilasciato

### Il percorso della pratica, sempre a un tocco
- In ogni pratica e attività il pulsante *Il percorso* (in basso a destra) apre il diagramma BPMN in un pannello da
  destra: il resto si spegne, un segnalino lascia una scia blu dall'inizio al passo attuale, accende i passi che
  raggiunge (anche dentro i sottoprocessi) e si ferma dove la pratica è adesso. Le tappe sono scritte anche in parole.
- Il diagramma della pratica riceve i passi fatti nell'ordine in cui sono iniziati.

### Grafica: intestazioni, benvenuto e tessere-filtro
- Ogni pagina di lavoro e dello Studio apre con un'intestazione in una tessera bianca e il percorso (casa › sezione ›
  pagina corrente in una capsula blu chiara), come le briciole di pane di Bootstrap Italia.
- Scrivania: il benvenuto è la tessera blu doppia, con saluto, i numeri "Tocca a te" e "Del tuo gruppo", il disegno
  dell'ordito e le prime attività (le tue o, se non ne hai, quelle del gruppo); accanto quattro tessere, con
  "Concluse negli ultimi 30 giorni". Le tessere hanno l'icona in un cerchio pieno e forme morbide nell'angolo.
- Pratiche: tre tessere-filtro con i conteggi (in corso, concluse, tutte) al posto delle schede, ricerca e tipologia a
  capsula, a che punto è in grassetto con l'iniziale di chi la segue.
- Movimento: le pagine entrano con una dissolvenza, tessere e riquadri arrivano a cascata (500 ms, 60 ms l'uno
  dall'altro), le voci dei menu scendono in fila, la capsula dei selettori scorre, nel disegno del benvenuto un
  segnalino giallo percorre il filo. Tutto fermo con "riduci il movimento".
- Grafici: *Grafico* e *Tabella* sono un selettore a capsula. A 390px i giorni dei termini non fanno più scorrere la pagina.

### Prima soluzione per un cliente: Bando ISI di INAIL
- `soluzioni/isi-domanda` e `soluzioni/isi-elenchi`, costruite con la skill `orditogram-soluzione` dalla specifica
  `Requisiti_ISI_Domanda_v1`: domanda di finanziamento (compilazione, registrazione, codice e sportello, documenti e
  perizia del tecnico, istruttoria della sede) ed elenchi cronologici. Il calcolo del bando è un servizio della
  soluzione (`servizio-isi/`, Java 21 e Spring Boot 3.5, 37 test JUnit), chiamato dai connettori API. Collaudo: 16 scenari su 16 e 4 su 4.
- Richieste di sviluppo per il nucleo emerse dal caso (N1-N15) in `soluzioni/isi-domanda/analisi.md`.
- Skill: nel collaudo `@dato:<nome>` riusa nei dati di un'attività un valore della pratica (es. il codice rilasciato);
  tre errori frequenti in più (riunione parallela, caselle booleane, date nei connettori);
  il codice consegnato al cliente è sempre Java e Spring Boot, mai Python o altri runtime.

## 0.13.0 (26 novembre 2026)

Verso un Orditogram per ogni settore, il nucleo non parla più solo la lingua della PA, e pronto per i primi
progetti reali: installazione sicura, form che cambiano senza toccare le pratiche in corso, registro di chi legge
cosa, errori con un codice per l'assistenza, test nel browser.

- **Glossario** (Studio → Glossario, solo amministratore): le parole con cui l'installazione chiama chi la usa
  (*ente*: azienda, studio, comune...), il personale visto da fuori (*Ufficio*: Assistenza, Segreteria...) e chi
  entra da fuori (*cittadino*: cliente, fornitore, socio...), con singolare, plurale e genere. L'interfaccia mette da
  sola gli articoli giusti ("dell'azienda", "della società", "dello studio") e mostra un'anteprima prima di salvare.
  Vale subito per tutti, anche nella pagina di accesso (`GET /api/info` restituisce `glossario`); gli utenti
  esterni vedono la parola scelta al posto di "Ufficio" nello storico e nei documenti. Predefiniti invariati
  (quelli della PA). Ogni modifica va nel registro (`GLOSSARIO_MODIFICATO`). API `GET`/`PUT /api/admin/glossario`,
  migrazione `V11`.
- **"Pratica" nel glossario**: un quarto termine sceglie come si chiama il caso che si lavora (pratica, richiesta,
  ticket, ordine...). Cambia il menu, il pulsante *Nuova pratica* ("Nuovo ticket"), la scrivania, l'elenco, la
  scheda, l'editor dei processi e la simulazione, le integrazioni, con articoli e aggettivi accordati al genere
  ("il ticket concluso", "la richiesta conclusa"). Anche i messaggi del server per le persone (non trovata, accesso
  negato, termine scaduto, titolo predefinito delle notifiche). Restano invariati i percorsi e i nomi tecnici
  dell'API (`/api/pratiche`, `pratica.avviata`, `pratiche:avvia`).
- **Moduli** (Studio → Moduli, solo amministratore): le funzioni legate all'Italia e alla PA diventano pacchetti
  che si spengono per le installazioni di altri settori. *Italia*: festività nazionali nel calendario lavorativo e
  formati `codiceFiscale`, `partitaIva`, `cap`, `telefono` nei form (spento, i form nuovi o modificati che li usano
  sono rifiutati con un messaggio chiaro e lo Studio non li propone più). *Firma digitale*: verifica delle firme
  sui documenti caricati. *PA*: servizi dimostrativi come il protocollo. Tutti accesi per default, quindi nulla
  cambia per chi aggiorna. Lo spegnimento si conferma e va nel registro (`MODULO_SPENTO`, `MODULO_ACCESO`).
  API `GET /api/admin/moduli`, `PUT /api/admin/moduli/{codice}`; `GET /api/info` restituisce `moduli`; migrazione `V12`.
- **Profilo di produzione** (`ORDITOGRAM_PROFILO=produzione` nel `.env`, o `SPRING_PROFILES_ACTIVE=produzione`):
  spegne utenti e servizi di prova, manda il cookie di sessione solo su HTTPS e impedisce l'avvio con impostazioni
  insicure, elencandole tutte: password del database o dell'amministratore predefinite o sotto i 12 caratteri,
  chiave di cifratura assente o sotto i 32, `admin` o utenti di prova ancora attivi con password `admin` o `password`.
  Nuova sezione "Installazione in produzione" nel README (HTTPS, backup pianificato, aggiornamenti, risorse). La porta
  di PostgreSQL in Docker Compose ora è aperta solo sul server stesso (`127.0.0.1`). **Aggiornamento**: il `.env`
  d'esempio attiva il profilo; chi lo copia per una demo svuota `ORDITOGRAM_PROFILO`.
- **Versioni dei form**: ogni salvataggio che cambia un form ne crea una versione; le pratiche nuove usano l'ultima,
  quelle già avviate restano su quella del loro avvio (attività, completamento, scheda, documenti, campi visibili).
  Un campo obbligatorio aggiunto non blocca più le attività in corso. Colonna *Versione* in Studio → Form, API
  `GET /api/admin/forms/{formKey}/versioni`, versione nel registro (`FORM_SALVATO`), migrazione `V13`. Al primo avvio
  dopo l'aggiornamento le pratiche in corso ricevono le versioni attuali dei loro form.
- Esempi nei campi dello Studio senza riferimenti alla PA ("Richiesta d'acquisto", "portale-clienti",
  `numeroRegistrazione`).
- **Registro delle letture, degli accessi e degli errori** (Studio → Registro, schede *Azioni*, *Letture*,
  *Errori*): chi apre pratiche, attività, schede di anagrafica e documenti, anche quando l'accesso è negato, dall'
  interfaccia e dall'API pubblica; gli accessi con utente e password (uno ogni 30 minuti per utente) e le uscite.
  Ogni errore imprevisto riceve un codice `E-…` da comunicare all'assistenza: la persona non vede più dettagli
  tecnici, che restano nel registro con la traccia, insieme ai job del motore falliti. Letture conservate 365
  giorni, errori 90 (`app.registro.*`). Le letture si registrano con l'annotazione `@RegistraLettura` (un aspetto
  Spring AOP). API `GET /api/admin/registro/letture` e `/errori`, migrazione `V14`.
- **Test nel browser** (Playwright, `npm run e2e`): il percorso principale Studio → pubblicazione → scrivania →
  pratica conclusa, più accesso negato allo Studio e password sbagliata, su backend con H2 in memoria. Gira in CI
  nel job `e2e`, che ora serve anche per costruire le immagini Docker.

## 0.12.0 (novembre 2026)

**Aggiornamento**: chi era amministratore riceve il ruolo Amministratore (migrazione `V3`); gli altri utenti non
hanno ruoli e lavorano come prima le loro attività e pratiche. Il campo `admin` di `/api/admin/users` diventa
`ruoli` (codici); `/api/me` aggiunge `ruoli` e `permessi`.

Form avanzati (sezioni, campi condizionali, regole, tabelle di righe), processi richiamati, ruoli e permessi
granulari, utenti esterni e organizzazioni, anagrafiche configurabili, nuova grafica AgID "bento a colori".

- **Nuova grafica nel linguaggio AgID** (Designers Italia, Bootstrap Italia 2): Blu Italia per le azioni,
  Titillium Web e Roboto Mono, intestazione blu come nei siti della PA, menu laterale chiaro, superfici arrotondate,
  stati con icona e parola, focus giallo, link "Vai al contenuto". Contrasti verificati (WCAG 2.1 AA).
- **Scrivania come cruscotto**: tessere con attività tue, del gruppo, scadute e in scadenza.
- **Pannello laterale** nell'elenco delle pratiche: riassunto, chi ci lavora e percorso senza cambiare pagina.
- **Specchietto del flusso** nella pagina della pratica: il percorso si riempie fino al passo attuale, che pulsa;
  accanto chi ha in carico la pratica, la scadenza e quanto è durato ogni passo.
- **Conferma in finestra AgID** per *Registra arrivo* dei messaggi, al posto della finestra del browser.
- **Scelta dell'esito a tessere**: un campo a scelta con al massimo 4 opzioni che sono esiti (accolta, respinta,
  serve integrazione...) si mostra con tessere grandi, icona e colore di stato accanto alla parola.
- **Anteprima dei documenti** nel pannello laterale: PDF, immagini e testo si leggono senza lasciare la pagina;
  per le buste firmate `.p7m` si vede il contenuto estratto.
- **Movimento**: entrate in sequenza, pannelli che scorrono, passo attuale che pulsa; tutto fermo con
  "riduci il movimento". Nuovi componenti `Cassetto` e `Tessera`; le finestre riportano il focus dove era.
- **Grafica "bento a colori"**: scrivania a tessere di misure e tinte diverse (blu, acqua, lilla, ambra, rosa) con i
  numeri che salgono contando; menu laterale scuro che si stringe a sole icone; clic su attività e pratiche della
  scrivania che apre il riassunto da destra; nella pratica tessere del termine, delle parti e della durata e il menu
  *Altre azioni*.
- **Il processo dal vivo**: il diagramma BPMN è sempre in vista nella pratica e nell'attività, con il passo attuale
  che pulsa, i passi fatti che si accendono in fila e un segnalino che percorre il cammino fatto.
- **Grafici della scrivania** con tabella dei valori a un clic: dove si trovano le pratiche, avanzamento stimato,
  aperte e chiuse per settimana, termini in arrivo. Nuova API `GET /api/pratiche/indicatori` (solo pratiche visibili,
  non per gli utenti esterni).

- **Skill per costruire soluzioni** (`.claude/skills/orditogram-soluzione`): dalle specifiche di un cliente Claude
  scrive l'analisi (lacune, scelte, domande), genera BPMN, form, anagrafiche, permessi e pacchetto `.orditogram.zip`,
  li verifica con le stesse regole della pubblicazione (`verifica.py`), li disegna, li collauda su un Orditogram
  avviato percorrendo ogni strada del processo (`collaudo.py`, con report e copertura del diagramma) e scrive la
  documentazione per il cliente (funzionale, manuale utente, manuale di esercizio, scheda tecnica generata, piano e
  report di test) e la checklist di collaudo per l'analista. Esempio completo: contributi per eventi culturali.
  Il test `SkillEsempioIntegrationTest` pubblica l'esempio a ogni build: se i formati cambiano, la CI lo segnala.

- **Sezioni**: i form si dividono in parti con titolo e istruzioni (`{ "type": "sezione", "label" }`).
- **Campi condizionali**: *Mostra solo se* fa comparire un campo solo quando un campo a scelta o sì/no ha certi
  valori (`visibileSe`). I campi nascosti non sono obbligatori e i loro valori non si salvano, sia
  dall'interfaccia sia dall'API pubblica; l'OpenAPI generata lo documenta.
- Lo Studio controlla le condizioni al salvataggio del form (campo esistente, almeno un valore).
- **Regole di validazione dei campi**: minimo e massimo per numeri e date (anche "oggi"), lunghezza dei testi,
  formati (email, codice fiscale, partita IVA, IBAN con carattere di controllo, CAP, telefono, espressione
  personalizzata). Messaggi accanto al campo; stessi controlli sul server e nell'API pubblica, documentati
  nell'OpenAPI. I numeri con decimali ora si inseriscono senza blocchi del browser.
- **Tabelle di righe** (`"type": "tabella"`): più righe con le stesse colonne (testo, numero, data, scelta,
  sì/no), ciascuna con obbligo e regole, più il numero minimo e massimo di righe. Chi compila aggiunge e toglie
  righe; le righe vuote non si salvano. Le attività successive e la scheda della pratica le mostrano come
  tabella; l'API pubblica le accetta come array di oggetti, documentato nell'OpenAPI, con errori che indicano
  riga e colonna. Lo Studio controlla colonne, tipi e limiti al salvataggio.
- **Processi richiamati** (call activity): un processo apre una pratica di un altro processo pubblicato, le passa i
  suoi dati e riprende quando finisce, con i dati raccolti. Nell'editor: *Processo richiamato*. La scheda della
  pratica mostra le pratiche collegate. Controlli alla pubblicazione: processo richiamato pubblicato, niente
  richiami di sé stesso o a vicenda.
- **Analisi del codice in CI**: SonarQube Community, Semgrep, SpotBugs con FindSecBugs, OSV-Scanner e npm audit a
  ogni modifica, con riepilogo e rapporti conservati; distinta delle librerie (SBOM) e copertura dei test con
  `mvn -Panalisi verify`.
- **Librerie aggiornate** dopo la prima analisi: Spring Boot 3.5.16, con Spring, Jackson e Micrometer
  riportati alle sue versioni anche dove Activiti le fissava, Tomcat 10.1.62, driver PostgreSQL 42.7.14,
  Bouncy Castle 1.87, React Router 7, DOMPurify corretto; Tailwind tra gli strumenti di sviluppo.

- **Correzioni dalla prima analisi del codice**: espressioni regolari riscritte senza rischio di blocco (email,
  mappature delle chiamate API); controllo `X-Requested-With` contro il CSRF esteso a ogni accesso dal browser,
  non solo a Keycloak ed Entra ID; log su una riga sola contro le righe false; transazione dei webhook coerente;
  rollback esplicito su pubblicazione, importazione dei pacchetti e avvio con allegati; eccezioni non più ignorate
  in silenzio; azioni di GitHub fissate per impronta. Interfaccia: etichette collegate ai campi nello Studio
  (Form, Utenti), area degli allegati usabile da tastiera, errori di caricamento sempre gestiti.

- **Librerie alle versioni più nuove**: React 19, Vite 8, Vitest 5, bpmn-js 18 con la simulazione 1.1, Node 24,
  nginx 1.30; nel backend Jackson 2.21.8, Commons Lang 3.22, Log4j 2.26 e la posta di Spring Boot al posto di
  quella vecchia portata da Activiti. Activiti resta alla 8.8.1, l'ultima stabile per Java 21 e Spring Boot 3.5.
- **nginx con indirizzo fisso**: `ORDITOGRAM_INDIRIZZO` (nel file `.env`) è il nome con cui gli utenti aprono
  Orditogram; il backend riceve sempre quello, mai l'intestazione `Host` scelta da chi chiama.
- **Analisi del codice bloccante**: le soglie fermano la CI. Le due vulnerabilità di Spring MVC ancora aperte
  (XsltView, Server Sent Events) non toccano Orditogram: sono motivate in `strumenti/analisi/osv-scanner.toml`.

- **Migrazioni del database con Flyway**: le tabelle di Orditogram si creano e si aggiornano con script versionati
  (`db/migration`), gli stessi in produzione e nei test; Hibernate controlla soltanto che lo schema corrisponda
  (`ddl-auto: validate`). Il vincolo sui tipi di evento dello storico si toglie con una migrazione, al posto della
  classe `SchemaCompatibilita`.
- **Ruoli e permessi**: al posto della sola casella "amministratore", ruoli fatti di permessi (processi, pubblicazione,
  form, integrazioni, utenti e ruoli, registro, tutte le pratiche): chi ha i processi senza la pubblicazione
  prepara le bozze e un altro le mette in produzione. Predefiniti: Amministratore, Analista, Gestore utenti,
  Auditor; altri si creano in Studio → **Ruoli**. Un ruolo si assegna alle persone o a un gruppo, anche del provider
  di accesso (Keycloak, Entra ID). Lo Studio mostra solo le sezioni permesse e il server controlla ogni sezione a
  ogni richiesta, quindi un ruolo tolto vale subito. Nessuno dà, toglie o modifica permessi che non ha, e resta
  sempre almeno un amministratore attivo. Creazioni, modifiche ed eliminazioni dei ruoli vanno nel registro.
- **Permessi per processo** (Studio → Processi → *Accesso*): per ogni processo, chi può avviarlo (sostituisce i gruppi
  di avvio del BPMN), chi vede tutte le sue pratiche e chi può esportarle, per gruppo o per ruolo. Valgono subito,
  senza ripubblicare, e viaggiano nel pacchetto del processo (si applicano dove il processo non ha ancora regole).
- **Strutture** (Studio → Strutture): gli uffici dell'ente in un albero; le persone si mettono nelle strutture dalla
  pagina Utenti e una pratica prende la struttura di chi la avvia. "Vede tutte le pratiche" si dà con un ambito:
  tutte, quelle della propria struttura o anche delle strutture sotto.
- **Elenco delle pratiche nel database**: la visibilità si calcola con una sola query (storico, attività, regole del
  processo e strutture), la stessa usata per aprire una pratica; finito il limite delle 500 attività lette in memoria.
  Le pagine a video sono al massimo di 100 righe; le esportazioni seguono le regole del processo e vanno nel registro.
- Chi gestisce gli utenti senza essere amministratore non cambia i propri gruppi, ruoli e strutture.
- **Utenti esterni e parti della pratica**: cittadini, clienti e referenti di organizzazioni entrano come utenti
  esterni (Studio → Utenti, o il gruppo `app.oidc.gruppo-esterni` del provider): niente ruoli, solo le API del
  portale (negazione per default per tutto il resto), solo le pratiche in cui sono parte o hanno un'attività.
  Chi avvia da esterno è il *richiedente*; un campo **Organizzazione** dei form può rendere un'organizzazione esterna
  una parte (es. la controparte) e le attività `parte:controparte` del BPMN vanno ai suoi utenti.
- **Organizzazioni** (Studio → Organizzazioni, permesso `organizzazioni.gestisci`, dato anche al Gestore utenti):
  anagrafica delle organizzazioni esterne con importazione da CSV; tutto nel registro.
- **Chi vede quali dati**: gli esterni vedono i campi *visibili alle parti* e quelli che hanno compilato, uno storico
  ridotto a nome dell'"Ufficio" e i documenti visibili alle parti (il personale sceglie quali con *Mostra alle parti*);
  tra il personale, un campo può essere riservato a certi ruoli (`ruoliVisibili`). Processo d'esempio
  `esempi/reclamo-controparte` e utenti di prova esterni (`marco`, `elena`).
- **Anagrafiche configurabili**, prima parte (backend): l'analista definisce tipi di anagrafica (fondi, società,
  fornitori, immobili...) con lo stesso formato dei form, più campi **chiave** (valore unico, es. partita IVA),
  **ricercabili** e **relazioni** tra schede (`"type": "anagrafica"`). Le schede si cercano (testo o valore esatto,
  a pagine), si modificano con il controllo delle modifiche contemporanee, si cessano e riattivano invece di
  cancellarle, e ogni modifica resta nelle **versioni** (chi, quando, dati, nota). Permessi per tipo (vede,
  modifica, esporta) a gruppi o ruoli; senza regole solo l'amministratore vede le schede; i campi `ruoliVisibili`
  restano nascosti anche nelle versioni; niente accesso per gli utenti esterni. Nuovo permesso
  `anagrafiche.definisci` (dato anche all'Analista); API `/api/admin/anagrafiche` e `/api/anagrafiche`;
  migrazione `V7`. Interfaccia e API pubblica arrivano nelle prossime parti.
- **Anagrafiche nelle pratiche**: campo di form `"type": "anagrafica"` per scegliere una scheda, controllata per chi
  compila (esistente, del tipo giusto, attiva, visibile). La pratica si collega alla scheda con la **fotografia** dei
  dati di quel momento (senza campi riservati), disponibile nel processo come `<campo>_dati` e `<campo>_titolo`
  (es. `${fondo_dati.tipologia}` in un gateway); il campo può dare il titolo alla pratica. La pratica mostra le
  schede collegate (dati fotografati e stato attuale), la scheda le pratiche collegate visibili all'utente.
  I pacchetti portano con sé le definizioni delle anagrafiche usate dai form. Migrazione `V9`.
- **Anagrafiche nell'interfaccia**: Studio → *Anagrafiche* per definire i tipi con lo stesso costruttore dei form
  (campi chiave, ricercabili, titolo della scheda, collegamenti ad altre anagrafiche) e decidere chi consulta,
  modifica ed esporta. Nuova sezione *Anagrafiche* per il personale che vede almeno un tipo: ricerca, nuova scheda,
  modifica con nota, cessazione e riattivazione, versioni con i campi cambiati, schede collegate e che la citano,
  pratiche che la usano. Nei form il campo *Anagrafica* cerca e sceglie una scheda attiva; la scheda della pratica
  mostra le anagrafiche scelte. Errori dei campi accanto al campo, etichette e messaggi accessibili.
- **Importazione ed esportazione delle schede**: CSV (punto e virgola o virgola, intestazioni con etichette o nomi
  dei dati, date GG/MM/AAAA, sì/no) che aggiorna le schede con la stessa chiave; ogni riga si salva da sola e le
  righe scartate arrivano con il motivo. Esportazione in Excel, CSV e PDF per chi ha la regola *esporta*; il CSV
  esportato si reimporta così com'è. Importazioni ed esportazioni vanno nel registro.
- **Anagrafiche dall'API pubblica**: `/api/v1/anagrafiche` per cercare, leggere, creare e modificare le schede, con i
  permessi `anagrafiche:leggi` e `anagrafiche:scrivi`. Ogni chiave elenca le anagrafiche a cui accede (nessuna per
  default); chi la crea deve vederle (e modificarle, se la chiave scrive). Errori `dati_non_validi` (422) e
  `conflitto` (409); OpenAPI aggiornata. L'avvio di una pratica dall'API accetta i campi anagrafica con le schede
  delle anagrafiche della chiave. Migrazione `V10`.
- **Regola *sceglie***: trova le schede per nome e le sceglie nei form senza consultare l'archivio
  (`/api/anagrafiche/{tipo}/scelta`, solo numero e titolo). È l'unica regola che vale per gli utenti esterni
  (gruppi `esterni` e `org:<id>`), che ora possono compilare i campi anagrafica.
- **Nuova scheda dal form**: un campo anagrafica con *Si può creare una scheda nuova da qui* fa creare la scheda senza
  lasciare la pratica, a chi ha la regola *modifica*; *Apri la scheda* la apre in un'altra scheda del browser.
  Con più finestre aperte, Esc chiude solo quella in primo piano.
- **Esportazioni protette dalle formule**: nei CSV esportati un testo che inizia con `=`, `+`, `-` o `@` ha un
  apostrofo davanti, così Excel non lo esegue come formula (CSV injection).
- **Referente dell'organizzazione**: chi ha il permesso organizzazioni sceglie uno o più referenti tra gli utenti
  esterni di un'organizzazione (Studio → Organizzazioni → *Persone e referenti*). Il referente, dal portale
  (*La mia organizzazione*), aggiunge colleghi, li disattiva, li riattiva e li toglie; non dà ruoli, non tocca altri
  referenti, utenti interni o persone di altre organizzazioni, e chi è anche in un'altra organizzazione si può solo
  togliere dalla sua. Limiti `app.organizzazioni.max-utenti` e `max-aggiunte-ora`; tutto nel registro, con i
  tentativi rifiutati tra gli accessi negati. Con OIDC la persona aggiunta si collega al primo accesso se il
  provider dichiara verificata la sua email. Nella demo `elena` è referente dello Studio legale Bianchi.
- **Cambio password** per gli utenti locali (icona a chiave in basso nel menù). Chi riceve una password temporanea
  dal referente deve cambiarla al primo accesso e fino ad allora non fa altro; almeno 12 caratteri, senza il nome
  utente. `/api/me` aggiunge `passwordDaCambiare` e `referenteDi`; si registra anche l'ultimo accesso.

**Aggiornamento**: impostare `ORDITOGRAM_INDIRIZZO` nel file `.env` (es. `orditogram.ente.it`). Chi chiama l'API interna (`/api`, non `/api/v1`) da un browser o da uno script che manda
cookie o `Origin` deve aggiungere l'intestazione `X-Requested-With: XMLHttpRequest`, come già fa l'interfaccia.

Database: chi arriva da una versione precedente alla 0.12 avvii una volta la 0.12 prima di aggiornare, così le
tabelle sono complete; da qui in poi gli aggiornamenti dello schema li applica Flyway da solo all'avvio. Prima di
aggiornare conviene comunque un backup (`strumenti/backup.sh`).


## 0.11.0 (novembre 2026)

Il prodotto si chiama **Orditogram**.

- Nuovo nome in tutta l'applicazione, nei PDF esportati, nella documentazione API e nei webhook.
- Pacchetto Java `com.eligesoft.orditogram`, artefatto Maven `com.eligesoft:orditogram`.
- Database predefinito `orditogram` (utente `orditogram`), configurabile con `DB_URL`, `DB_UTENTE`, `DB_PASSWORD`.
- Maven Wrapper: `./mvnw` scarica Maven la prima volta, non serve installarlo.
- `CLAUDE.md`: contesto del progetto per chi ci lavora (persone e assistenti).

**Aggiornamento da 0.10**
- Variabili d'ambiente: `ITER_*` diventano `ORDITOGRAM_*` (`ORDITOGRAM_CHIAVE_CIFRATURA`, `ORDITOGRAM_PORTA`,
  `ORDITOGRAM_VERSIONE`); `ITER_CHIAVE_CIFRATURA` è ancora letta se la nuova non c'è.
- Webhook: le intestazioni sono ora `X-Orditogram-Firma`, `X-Orditogram-Evento`, `X-Orditogram-Consegna`.
- BPMN: le estensioni usano lo spazio dei nomi `http://orditogram.it/bpmn` (prefisso `orditogram:`).
  I processi salvati con il prefisso `iter:` vanno riaperti e ripubblicati.
- Pacchetti: estensione `.orditogram.zip`; l'importazione accetta anche i vecchi `.iter.zip`.

## 0.10.0 (ottobre 2026)

Integrazioni: gli altri sistemi parlano con Orditogram, Orditogram avvisa gli altri sistemi.

**API pubblica `/api/v1`** (stabile, per portali, gestionali, app)
- Avvio di pratiche con i dati del form di avvio, controllati in modo rigoroso: tutti gli errori in una risposta,
  con suggerimenti sui nomi sbagliati. Documenti all'avvio (multipart). Riferimento esterno: la stessa richiesta
  ripetuta non crea doppioni.
- Ricerca (processo, stato, riferimento, date), stato completo della pratica (attività in corso, scadenze,
  messaggi attesi, dati), storico, documenti (elenco, scaricamento, caricamento), messaggi.
- Chiavi API create nello Studio: permessi, processi consentiti, scadenza, sospensione, rotazione, revoca,
  ultimo uso. Della chiave si conserva solo l'impronta. Limite di richieste al minuto, CORS configurabile.
- Documentazione OpenAPI 3.1 **generata dai processi pubblicati**: ogni processo ha il suo percorso di avvio con
  lo schema esatto dei suoi campi. Pagina "Documentazione API" con esempi (cURL, JavaScript, Python) e prova dal vivo.

**Webhook**
- Eventi: pratica avviata/conclusa/annullata, attività creata/assegnata/completata, messaggio ricevuto,
  documento aggiunto, scadenza scattata, errore. Filtri per evento e processo.
- Avvisi firmati (HMAC-SHA256, `X-Orditogram-Firma`), coda nel database nella stessa transazione della pratica,
  nuovi tentativi fino a 24 ore, elenco delle consegne con corpo e risposta, ripetizione a mano, invio di prova,
  ricevitore di prova integrato. Segreti cifrati con `ORDITOGRAM_CHIAVE_CIFRATURA`.

**Correzioni**
- Le risposte di errore (400, 404, 409) arrivano con il codice giusto anche ai sistemi senza utente
  (prima diventavano 401).
- Test: dipendenze di test nel posto giusto del `pom.xml`, database H2 dei test in modalità normale.

**Aggiornamento**
- Nuova variabile consigliata in produzione: `ORDITOGRAM_CHIAVE_CIFRATURA` (vedi README, "Webhook").
- Le chiavi in `app.messaggi.chiavi` continuano a funzionare per i messaggi; conviene sostituirle con chiavi dello Studio.

## 0.9.0 (ottobre 2026)

Prima versione con le fondamenta da prodotto.

**Piattaforma**
- Installazione con Docker: `docker compose up -d` avvia database, backend e frontend; impostazioni in `.env`.
- Stato di salute e versione: `/actuator/health` (anche `liveness` e `readiness` per Kubernetes), `/api/info`;
  la versione è visibile nell'applicazione.
- Password iniziale dell'amministratore configurabile (`APP_ADMIN_PASSWORD_INIZIALE`).
- Test automatici: unitari e di integrazione (applicazione e motore veri su database in memoria) per il backend,
  Vitest per il frontend; integrazione continua con GitHub Actions e immagini Docker sui tag di versione.
- Script di backup e ripristino (`strumenti/backup.sh`, `strumenti/ripristino.sh`).
- Registro delle azioni di amministrazione e degli accessi negati (Studio, Registro), esportabile.

**Pratiche**
- Ricerca e pagine sul database: nessun limite di righe; filtro per tipologia; esportazioni complete.

**Modellazione** (sviluppate prima di questa versione)
- Editor BPMN guidato, form visuali, controllo continuo, bozze, pacchetti, simulazione con i gettoni.
- Scadenze, solleciti, attese, sottoprocessi, strade di errore; termini in giorni lavorativi o prorogati se festivi.
- Connettori: notifiche, email, API REST; messaggi in arrivo da sistemi esterni.
- Accesso con utenti locali o con Keycloak / Microsoft Entra ID.

**Aggiornamento da versioni precedenti**
- La risposta di `GET /api/pratiche` è ora una pagina: `{ righe, totale, pagina, dimensione }`.
- Nuove tabelle e colonne create in automatico all'avvio (registro delle azioni, collegamento degli utenti OIDC).
