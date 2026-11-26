# Orditogram: contesto per chi lavora sul progetto

Questo file si legge all'inizio di ogni sessione di lavoro, da persone e assistenti.
Tienilo aggiornato quando cambiano architettura, convenzioni o roadmap.

## Cos'è

Orditogram è una piattaforma che trasforma i processi in software. Gli analisti disegnano il BPMN, configurano
form, gruppi, scadenze e integrazioni nello **Studio**, lo validano e lo pubblicano. Da quel momento è
un'applicazione funzionante: scrivania delle attività, pratiche, documenti, storico, notifiche, API.

Si rivolge ad **aziende, pubbliche amministrazioni e software house**. Il modello: i nostri analisti studiano i
processi del cliente, li modellano e consegnano in poco tempo un software pronto; il cliente può poi
estendere form e processi da solo con lo Studio. Il nome viene da *ordito* (la trama su cui si tesse) + *diagramma*.

Proprietario: Eligesoft. Lingua del prodotto, del codice e delle risposte all'utente: **italiano**.

## Architettura

- **backend/**: Java 21, Spring Boot 3.5.16, **Activiti 8.8.1** (non Flowable: tieni allineate le versioni di
  Spring Boot e Activiti), Spring Security 6.5, JPA/Hibernate 6, PostgreSQL (H2 in memoria nei test).
  Pacchetto radice `com.eligesoft.orditogram`.
- **frontend/**: React 19, Vite 8, Tailwind 4, bpmn-js 18 (+ token simulation 1.x), lucide-react, Vitest 5, Node 24.
  Grafica nel linguaggio di Designers Italia (AgID): vedi la sezione *Interfaccia*.
  Librerie sempre all'ultima versione: l'unico vincolo è Activiti nel backend (vedi sotto).
- **esempi/accesso-atti/**: processo dimostrativo completo (legge 241/1990) con form e pacchetto `.orditogram.zip`.
- **docker-compose.yml**: postgres + backend + frontend (nginx), Keycloak di prova con `--profile keycloak`.
- **strumenti/**: backup e ripristino, script SQL per svuotare le pratiche.

### Pacchetti del backend
| Pacchetto | Cosa fa |
|---|---|
| `process` | validazione dei BPMN (`ProcessValidator`), pubblicazione, Studio processi |
| `bozza` | bozze dei processi, pacchetti `.orditogram.zip` (esporta/importa) |
| `form` | form JSON dello Studio, `ValoriForm` (regole comuni su valori, obbligatori, titolo) |
| `storico` | pratiche (`PraticaController`, ricerca paginata), storico eventi, scadenze, permessi |
| `documento` | documenti con versioni e verifica delle firme |
| `connettore`, `notifica`, `integrazione` | connettori dei service task: notifiche, email, chiamate API, credenziali |
| `messaggio` | messaggi in arrivo da sistemi esterni verso le pratiche |
| `calendario` | giorni lavorativi e festività (calendari Activiti "lavorativi" e "proroga") |
| `auth`, `user` | utenti locali, OIDC (Keycloak, Entra ID), utente corrente |
| `accesso` | ruoli e permessi applicativi (`Permesso`, `Ruolo`, `PermessiService`), regole anti-escalation, permessi per processo (`PermessoProcesso`: AVVIA, VEDE con ambito, ESPORTA), strutture (`Organizzazione` in albero), organizzazioni esterne, utenti esterni (`AccessoEsterni`: solo API del portale), parti della pratica (`PartiService`, `AscoltatoreParti`), referenti delle organizzazioni (`ReferentiService`, portale `/api/organizzazione`) |
| `anagrafica` | anagrafiche configurabili: tipi definiti dall'analista (`DefinizioneAnagrafica`, formato dei form + chiave, cercabile, relazioni), schede con indice, versioni e cessazione, permessi per tipo (`AccessoAnagrafiche`, unico punto di controllo) |
| `modulo` | moduli di settore accendibili (`Modulo`: italia, firma-digitale, pa; `ModuliService`, tabella `modulo_spento`) |
| `glossario` | parole dell'installazione per ente, ufficio, utenti esterni e pratica (`Termine`, `GlossarioService`); predefiniti della PA |
| `registro` | registro delle azioni di amministrazione e accessi negati |
| `api` | **API pubblica `/api/v1`** con chiavi, OpenAPI generata dai processi, Studio → Integrazioni |
| `webhook` | avvisi firmati verso sistemi esterni: coda nel DB, invio con ripetizioni |
| `config` | cifratura dei segreti, pianificazione |

### Concetti chiave
- **Pratica** = istanza di processo Activiti. Titolo da un campo del form marcato `titolo`.
- **Form**: JSON `{fields:[{name,label,type,required,readonly,options,titolo,visibileSe}], documenti, documentiObbligatori}`.
  Tipi: text, textarea, number, date, select, checkbox, tabella. L'interfaccia invia i numeri come testo.
  `{type:"tabella",colonne:[campi senza textarea],minRighe,maxRighe}`: valore = lista di oggetti, righe vuote tolte;
  regole in `ValoriForm.righe/erroriTabella` e `formRegole.js righePiene/erroriTabella`, allineate.
  `{type:"sezione",label,help}` apre una sezione (non è un dato). `visibileSe:{campo,valori}` nasconde il campo:
  regole uguali in `ValoriForm.nascosti` (backend) e `lib/formCondizioni.js` (frontend), tienile allineate.
  Regole dei campi (`min`, `max`, `minLunghezza`, `maxLunghezza`, `formato`): `form/RegoleCampo.java` e
  `lib/formRegole.js`, anche queste allineate; le espressioni dell'analista girano con un limite di passi (ReDoS).
- Estensioni BPMN proprie: namespace `http://orditogram.it/bpmn`, prefisso `orditogram:`
  (es. `<orditogram:datiMessaggio>motivoRinuncia</orditogram:datiMessaggio>`); estensioni Activiti per il resto.
- **Sicurezza**: due catene. `/api/v1/**` (ordine 1): solo chiavi API, stateless. Tutto il resto (ordine 2):
  utenti locali con Basic (solo se `app.login.locale`) o sessione OIDC. `/api/admin/**`: ogni sezione con il suo
  permesso (`accesso/Permesso`, letto dal DB a ogni richiesta, `AutorizzazioniAdmin`); ruoli assegnati a utenti o
  gruppi; l'amministratore ha sempre tutti i permessi. Un permesso nuovo: enum `Permesso` + regola in `SecurityConfig`
  + `lib/permessi.js`. Mai dare/togliere permessi che chi agisce non ha (`ControlliAccesso`).
- **Chi vede una pratica**: una sola regola SQL in `storico/VisibilitaPratiche` (elenco e dettaglio), più la pratica
  madre in `PraticaAccessService` (non per gli esterni). **Quali dati**: `storico/CampiVisibili` (esterni: campi
  `visibileAlleParti` e form compilati da loro; personale: `ruoliVisibili`); storico e documenti ridotti per gli esterni.
- **Utenti esterni** (`AppUser.tipo` ESTERNO): niente ruoli né permessi, gruppi automatici `esterni` e `org:<id>`
  (`gruppiEffettivi()`), solo le richieste elencate in `accesso/AccessoEsterni` (una API nuova del portale va
  aggiunta lì). Parti: `pratica_parte` + variabile `parte_<ruolo>` = `org:<id>`/`utente:<username>`, risolta da
  `AscoltatoreParti` sulle attività `candidateGroups="parte:<ruolo>"`. L'avvio passa da `RuntimeService` dopo `canStart` (regole AVVIA o BPMN).
  Referente (`organizzazione_referente`): utente esterno che gestisce le persone della sua organizzazione; ogni regola
  in `ReferentiService`. `AppUser.passwordTemporanea`: finché è vera, `AccessoEsterni` lascia solo `/api/me` e il cambio password.
- **Anagrafiche**: `anagrafica_tipo` (definizione JSON), `anagrafica_scheda` (dati JSON, stato ATTIVA/CESSATA,
  `@Version`), `anagrafica_indice` (valori normalizzati di chiavi, ricercabili e relazioni; UNIQUE su chiave),
  `anagrafica_versione`, `permesso_anagrafica`. Ogni accesso alle schede passa da `SchedeService` →
  `AccessoAnagrafiche`; tipo o scheda non visibile = 404. Validazione con `ValoriForm.convalida`, tipo campo `anagrafica`.
  Nei form delle pratiche: `AnagraficheNellePratiche` (controllo per chi compila, `pratica_anagrafica` con fotografia,
  variabili `<campo>_dati`/`<campo>_titolo`, che `CampiVisibili` lega alla visibilità del campo; mai `_dati` agli esterni).
  CSV: `ImportaEsportaSchede` (una transazione per riga, colonne `<etichetta> (nome)` ignorate per il giro
  esporta → importa). Interfaccia: `FormEditor` con `anagrafica={...}` per i campi dei tipi, `CampoAnagrafica` in
  `DynamicForm`, pagine `studio/Anagrafiche.jsx`, `Anagrafiche.jsx`, `SchedeAnagrafica.jsx`, `SchedaAnagrafica.jsx`,
  regole comuni in `lib/anagrafiche.js`. Il CSV esportato (`Esporta.jsx`) protegge le formule con un apostrofo.
  Azione SCEGLIE (`/scelta`, solo id e titolo; VEDE la comprende): l'unica che vale per gli esterni (gruppi `esterni`,
  `org:<id>`). API pubblica `ApiV1AnagraficheController`: la chiave elenca i tipi (`chiave_api.anagrafiche`, vuoto =
  nessuno), `AccessoAnagrafiche.sistema(...)` dà al sistema VEDE/SCEGLIE (+MODIFICA con `anagrafiche:scrivi`) solo su
  quelli, mai ESPORTA né campi riservati; `SchedeService.controllaConcedibili` impedisce di dare più di quanto si vede.
  Campo con `creaScheda: true`: *Nuova scheda* in `CampoAnagrafica` (form annidato in un portale, `prefissoId`).
- **Glossario**: mai scrivere "ente", "Ufficio", "cittadino", "pratica" nei testi dell'interfaccia; usa
  `gl('pratica', 'del')` → "della pratica" / "del ticket" (o `g` da `useGlossario()`; forme in `lib/glossario.js`:
  nome, nomi, il, i, un, nessun, del, dei, al, ai, dal, dai, nel, nei, sul, sui; con la maiuscola, es. `'Nome'`),
  aggettivi con `gl.agg('pratica', 'conclus')` → "conclusa"/"concluso", e `nuovaPratica()`, `senzaTitolo()`.
  `App` si iscrive al glossario, così i testi con `gl` si ridisegnano. Costanti con testi: getter o funzioni.
  Nel backend `GlossarioService.singolare/conArticolo/accorda(Termine...)` per i messaggi alle persone (non per l'API pubblica). Un termine nuovo: enum `Termine` + `PREDEFINITO` in `lib/glossario.js`.
- **Moduli**: ciò che vale solo per l'Italia o la PA va dietro un modulo (`modulo/Modulo`), controllato con
  `ModuliService.acceso(...)` nel backend e `moduloAcceso(info, codice)` (`lib/moduli.js`) nel frontend. Il nucleo
  resta generico; un modulo nuovo: voce in `Modulo` con descrizione e `porta`, effetti nei servizi, riga nel README.
- **Storico → eventi**: `StoricoService.registra` pubblica `EventoStorico`; i webhook lo ascoltano nella stessa transazione.

## Interfaccia

Regole per ogni schermata nuova o cambiata (proposta approvata da Filippo, novembre 2026: "UX/UI 2026, accessibile,
in linea AgID"; direzione "bento a colori" scelta il 18 novembre 2026). **Valgono per tutto ciò che costruiamo**:
Orditogram e ogni soluzione consegnata ai clienti. Obiettivo dichiarato: un effetto "wow" senza uscire da AgID.

- **Ogni schermata ha**: tessere di misure e tinte diverse (mai tutte uguali), un pannello o menu che si apre da
  destra, movimento fluido, il flusso BPMN visibile con il passo dove la pratica è ferma che pulsa, grafici degli
  indicatori quando ci sono numeri da leggere, ciascuno con la sua tabella a un clic.

- **Linguaggio**: Designers Italia (AgID, Bootstrap Italia 2) con superfici morbide. Blu Italia `#0066CC` per
  azioni, link, selezione, "adesso" e la tessera principale; verde, arancione e rosso per gli stati, sempre con icona
  e parola (mai il solo colore). Le tinte delle tessere (`acqua`, `lilla`, `ambra`, `rosa`: fondi chiari con testo
  `pino`) distinguono gli argomenti, non gli stati: `rosa` e `ambra` solo dove il numero è davvero un problema o
  un'attenzione. Testo `#17324D`, secondario `#5D7083`, fondo `#F2F5F8`. Ogni coppia testo/sfondo almeno 4,5:1.
- **Gettoni**: in `frontend/src/index.css` (`@theme`). I nomi storici restano (`pino` = blu notte del testo,
  `verde` = Blu Italia delle azioni, `ok` = successo, `zafferano` = attenzione, `mattone` = errore; `acqua`, `lilla`
  = tinte delle tessere): mai colori scritti a mano nei componenti. Caratteri Titillium Web e Roboto Mono, serviti
  dall'applicazione (`@fontsource`).
- **Guscio**: menu laterale scuro (`pino-950`) con la voce attiva piena di blu; si stringe a sole icone e si
  riallarga (*Stringi il menu*, scelta ricordata nel browser). Sul telefono intestazione blu e menu che scorre.
- **Forme**: pannelli e tessere `rounded-pannello` (18px), blocchi interni `rounded-blocco` (12px) su fondo `carta`,
  pulsanti e campi `rounded-campo` (10px), stati e filtri a capsula. Niente righe colorate sopra le tessere.
- **Componenti** (`components/ui.jsx`): `Button` (primario, secondario, fantasma, pericolo, chiaro), `Badge`
  (verde = successo, blu = in corso, zafferano, mattone, neutro, pino), `Tessera` (numero che sale contando, icona,
  pastiglia; `tono` blu piena per la cosa principale, `grande` per la tessera doppia del bento, tinte `acqua`,
  `lilla`, `ambra`, `rosa`; `children` sotto il numero), `Contatore`, `Menu` (azioni a comparsa, frecce ed Esc),
  `Pannello`, `Schede`, `Dialogo` (al centro, per confermare), `Cassetto` (pannello laterale da destra: vedere o
  fare qualcosa senza cambiare pagina). Altri: `BpmnViewer` (con `segnalino`), `ProcessoVivo` (il diagramma della
  pratica, non per gli esterni), `GraficiScrivania` (riquadri con *Tabella*), `AnteprimaPratica`. Prima di crearne uno nuovo,
  usa o estendi questi.
- **Aperture laterali**: un elemento di un elenco si apre nel `Cassetto` con il riassunto e l'azione principale;
  Ctrl/Cmd + clic apre la pagina; i documenti si vedono nel cassetto con `AnteprimaDocumento`. Le decisioni importanti si confermano in un `Dialogo` che dice cosa succede e
  che resta nello storico.
- **Flusso e grafici**: il diagramma BPMN vero è sempre in vista nella pratica e nell'attività; il passo dove è ferma
  pulsa (`.bpmn-corrente`), i passi fatti si accendono in fila, ogni 8 secondi un segnalino giallo percorre il
  cammino fatto (`lib/percorsoBpmn.js`). Grafici: un solo tono di blu per le grandezze, due serie al massimo con
  legenda, barre che crescono dalla base (`.cresce-y`, `.cresce-x`), valori scritti, `aria-label` con i numeri e
  pulsante *Tabella* con gli stessi valori. Indicatori dal server solo sulle pratiche visibili (`IndicatoriPratiche`).
- **Movimento**: solo opacità e spostamento (le barre crescono in scala), 160–320 ms, `--ease-morbido`; numeri
  che contano fino al valore (700 ms), menu che scendono (`animate-menu`). Elenchi e tessere entrano in sequenza con
  `.in-sequenza` (massimo 8 passi da 50 ms), il cassetto scorre da destra, le finestre si aprono con `animate-entra`,
  "adesso" pulsa con `animate-respiro`. Con "riduci il movimento" tutto è fermo (regola globale in `index.css`).
- **Accessibilità** (WCAG 2.1 AA): focus giallo `#FFB000` spesso 3px, link "Vai al contenuto", finestre con Esc,
  focus dentro all'apertura e di ritorno alla chiusura, `aria-label` sui pulsanti con sola icona, nessuno
  scorrimento orizzontale a 400px.

## Come si lavora

```bash
# database locale (una volta)
psql postgres -c "CREATE USER orditogram WITH PASSWORD 'orditogram';"
psql postgres -c "CREATE DATABASE orditogram OWNER orditogram;"

cd backend && ./mvnw verify            # compila + test unitari e di integrazione (H2)
cd backend && ./mvnw spring-boot:run   # http://localhost:8080
cd frontend && npm install && npm test && npm run dev   # http://localhost:5173
docker compose up -d                   # tutto insieme, http://localhost:8081
```

Utenti di prova (`app.demo.utenti: true`): admin/admin, mario e anna (operatori, anna anche responsabili),
laura, paolo, chiara, giulia: password `password`.

## Convenzioni
- Nomi di classi, metodi, variabili, messaggi e commenti **in italiano**; termini tecnici standard restano in inglese.
- Testi dell'interfaccia brevi e concreti, rivolti a persone non tecniche (operatori, analisti).
- Ogni nuova funzione: test (JUnit 5 nel backend, Vitest nel frontend), voce nel `CHANGELOG.md`, sezione nel `README.md`.
- Le azioni di amministrazione vanno nel registro (`RegistroService.registra`).
- Errori dell'API pubblica sempre `{"errore": {"codice", "messaggio", "campi"}}`, codici stabili.
- Gli stati salvati nel DB come testo, non enum Hibernate (i vincoli CHECK non si aggiornano: vedi la migrazione V2).
- Schema del DB solo con migrazioni Flyway (`backend/src/main/resources/db/migration`, `V<n>__descrizione.sql`), SQL
  valido su PostgreSQL e H2; `ddl-auto: validate`. Mai modificare una migrazione già rilasciata.
- Versioni MAGGIORE.MINORE.CORREZIONE in `backend/pom.xml` e `frontend/package.json`, allineate.

## Skill `orditogram-soluzione`
`.claude/skills/orditogram-soluzione/` insegna a Claude a costruire soluzioni dalle specifiche dei clienti
(BPMN, form, anagrafiche, permessi, pacchetto, collaudo, documentazione). **Regola**: ogni PR che aggiunge o cambia
qualcosa che l'analista configura (elementi BPMN ammessi, estensioni `orditogram:`, connettori e loro parametri,
proprietà dei form, anagrafiche, permessi, struttura del pacchetto, API usate dal collaudo) aggiorna nella stessa PR:
il riferimento in `riferimenti/`, la regola in `script/verifica.py` (stesso messaggio del backend), il catalogo
`CONNETTORI` di `verifica.py`, `script/documenta.py` se va documentato, l'esempio in `esempio/` se cambia il formato.
`SkillEsempioIntegrationTest` pubblica l'esempio a ogni build e pretende zero errori e zero avvisi.

## Insidie già incontrate
- **H2 nei test**: niente `MODE=PostgreSQL`, lo script di Activiti per H2 usa il tipo `IDENTITY`.
- **Pagina `/error`**: le risposte d'errore passano dal dispatch ERROR; è `permitAll`, altrimenti chi non ha
  utente (sistemi esterni) riceve 401 invece del vero errore.
- **Dipendenze di test** vanno in `<dependencies>`, non in `<dependencyManagement>`.
- Activiti: i dati dei messaggi vanno impostati con `setVariables` prima di `messageEventReceived`;
  le espressioni `${...}` negli output dei mapping sono rifiutate; il clock del motore si legge pigramente.
- Le email partono dopo il commit (`TransactionSynchronization`); senza `spring.mail.host` restano "non inviate".
- bpmn-js: estensioni moddle `activiti` e `orditogram`; la simulazione usa `bpmn-js-token-simulation` 1.x.

## Stato e roadmap
- **0.9** fondamenta: ricerca paginata, registro, Docker, test, CI, backup.
- **0.10** integrazioni: API pubblica `/api/v1`, chiavi, OpenAPI generata, webhook firmati.
- **0.11** nuovo nome Orditogram, Maven Wrapper, questo file.
- Codice su GitHub (`Eligesoftsrl/orditogram`), CI verde: 76 test del backend, compreso `ApiPubblicaIntegrationTest`;
  analisi del codice (SonarQube, Semgrep, SpotBugs, OSV, npm audit) con soglie bloccanti, falsi positivi motivati
  in `strumenti/analisi/`.
- **Prossime fasi**: 3) form avanzati (fatto nella 0.12);
  4) documenti da modello (lettere, provvedimenti); 5) tabelle decisionali DMN; 6) multi-ente / white label;
  7) versioni dei form e ruolo "analista del cliente"; 8) cruscotti; 9) libreria di modelli di processo;
  10) documentazione automatica del processo; 11) assistente IA per analisti.
- **0.12** (novembre 2026): form avanzati (sezioni, campi condizionali, regole, tabelle di righe), processi richiamati,
  ruoli e permessi granulari, utenti esterni e referenti, anagrafiche configurabili, grafica "bento a colori".

## Obiettivo: piattaforma enterprise

Clienti tipo: grandi gruppi, pubbliche amministrazioni, system integrator (es. Engineering, TIM). Il percorso che
deve restare breve e semplice: l'analista disegna o importa i BPMN, crea i form nello Studio, pubblica, e il cliente
ha un software pronto, configurabile da un'area di amministrazione senza sviluppo. Ogni scelta va giudicata su
tre assi: **flessibilità** (BPMN anche molto complessi), **semplicità** (pochi passi per l'analista e per chi
installa), **sicurezza e affidabilità** (requisiti da gara pubblica e da grande azienda).

### Già presente
OIDC (Keycloak, Entra ID), gruppi dal token, registro delle azioni di amministrazione e degli accessi negati,
segreti cifrati, API con chiavi (solo impronta salvata, limiti, scadenza, revoca), webhook firmati con ripetizioni,
backup e ripristino, Docker, CI.

### Roadmap sicurezza e affidabilità (da rivedere con Filippo, non ancora iniziata)
In ordine di priorità proposto:
1. **Ruoli e permessi granulari** (fatto nella 0.12: ruoli applicativi, permessi per processo, strutture, utenti esterni, organizzazioni e parti, campi per ruolo; referente dell'organizzazione che gestisce i suoi utenti): oltre ad "amministratore", ruoli come analista, gestore utenti, auditor,
   operatore; permessi per processo (chi avvia, chi vede, chi esporta), assegnabili da gruppi IdP.
2. **Audit log completo e non modificabile**: ogni lettura e modifica di pratiche e documenti, con catena di hash
   o firma periodica, esportabile; ricerca per utente, pratica, periodo. Base per GDPR e controlli.
3. **Versionamento e migrazione dei processi**: le pratiche in corso restano sulla loro versione; strumento guidato
   per migrarle alla nuova (mappatura delle attività), con anteprima e registro. Versioni dei form legate alla
   versione del processo.
4. **Pacchetto applicazione**: esportare e importare in un colpo processi, form, gruppi, connettori, calendari,
   modelli di documento, tra ambienti (sviluppo → collaudo → produzione) con confronto delle differenze.
5. **SSO SAML 2.0** (SPID/CIE per i portali dei cittadini, ADFS delle aziende), direttamente o tramite Keycloak
   come broker; MFA delegata all'IdP; logout centralizzato.
6. **Multi-tenant**: più enti o società sulla stessa installazione con dati separati (schema o colonna tenant),
   personalizzazione grafica per ente, amministratori per ente.
7. **Alta disponibilità e scalabilità**: più istanze del backend dietro bilanciatore (sessioni nel DB, executor
   asincrono di Activiti in cluster, lock dei job), PostgreSQL in replica, controlli di salute, prove di carico
   con numeri dichiarati (pratiche al giorno, utenti contemporanei).
8. **Osservabilità**: metriche (Micrometer/Prometheus), tracce, log strutturati JSON con id della richiesta,
   cruscotto di salute dei processi (pratiche bloccate, errori dei connettori, job falliti) con riprova.
9. **Conformità AgID e GDPR**: conservazione e cancellazione dei dati per processo, registro dei trattamenti,
   esportazione dei dati di una persona, minimizzazione nei log; accessibilità WCAG 2.1 AA e dichiarazione di
   accessibilità; linee guida AgID su sicurezza e interoperabilità (ModI per le API), predisposizione per
   protocollo informatico e conservazione a norma.
10. **Sicurezza del software**: OWASP ASVS livello 2 come riferimento, analisi statica e delle dipendenze in CI (SonarQube, Semgrep, SpotBugs, Dependency-Check; Kiuwan prima delle gare),
    SBOM per ogni versione, immagini firmate, test di penetrazione prima delle gare, guida all'installazione sicura.

### Fase qualità e sicurezza (richiesta di Filippo, dopo la prima versione completa)
Obiettivo: "a prova di bomba" prima di proporlo ai clienti.
- **Test estesi**: copertura misurata (JaCoCo, Vitest coverage) con soglie in CI; test end-to-end dell'interfaccia
  con Playwright sui percorsi principali (Studio → pubblicazione → scrivania → pratica); test di carico.
- **Security by design**: threat modeling per ogni funzione nuova (STRIDE sui flussi di dati: Studio, motore,
  API, connettori, documenti), requisiti di sicurezza scritti prima dello sviluppo, revisione sicura del codice
  su ogni PR, impostazioni sicure per default, registro delle decisioni di sicurezza.
- **Analisi del codice gratuita, obbligatoria in CI** (copre le regole che Kiuwan applica nelle gare PA:
  OWASP Top 10, CWE Top 25), con soglie bloccanti:
  SonarQube Community (in Docker, quality gate), Semgrep (regole OWASP e Java/React), SpotBugs con FindSecBugs
  (backend), OWASP Dependency-Check o equivalente (librerie con vulnerabilità note), `npm audit` (frontend).
  Report conservati per ogni versione, da allegare alle gare.
- **Kiuwan** come verifica finale prima delle gare, quando ci sarà la licenza (è a pagamento).
- **Attacchi simulati**: OWASP ZAP automatico in CI contro l'applicazione avviata (frontend, `/api`, `/api/v1`);
  verifica di autenticazione, autorizzazione tra utenti e gruppi, IDOR sulle pratiche, upload di documenti,
  injection nelle espressioni dei BPMN e nei form, CSRF, intestazioni di sicurezza; poi penetration test manuale.
- **Frontend conforme AgID**: linee guida di design per i servizi digitali della PA (Designers Italia),
  componenti Bootstrap Italia o equivalenti coerenti con l'UI kit, accessibilità WCAG 2.1 AA verificata
  (axe in CI e prova con lettore di schermo), dichiarazione di accessibilità.
