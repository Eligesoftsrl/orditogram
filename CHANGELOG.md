# Novità di Orditogram

Le versioni seguono il formato MAGGIORE.MINORE.CORREZIONE: cambia la MINORE per funzioni nuove compatibili,
la MAGGIORE quando un aggiornamento richiede interventi (indicati qui sotto).

## 0.12.0 (in preparazione)

**Aggiornamento**: chi era amministratore riceve il ruolo Amministratore (migrazione `V3`); gli altri utenti non
hanno ruoli e lavorano come prima le loro attività e pratiche. Il campo `admin` di `/api/admin/users` diventa
`ruoli` (codici); `/api/me` aggiunge `ruoli` e `permessi`.

Form avanzati, prima parte.

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
