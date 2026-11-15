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

- **backend/**: Java 21, Spring Boot 3.5.7, **Activiti 8.8.1** (non Flowable: tieni allineate le versioni di
  Spring Boot e Activiti), Spring Security 6.5, JPA/Hibernate 6, PostgreSQL (H2 in memoria nei test).
  Pacchetto radice `com.eligesoft.orditogram`.
- **frontend/**: React 18, Vite, Tailwind 4, bpmn-js 17 (+ token simulation), lucide-react, Vitest.
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
| `registro` | registro delle azioni di amministrazione e accessi negati |
| `api` | **API pubblica `/api/v1`** con chiavi, OpenAPI generata dai processi, Studio → Integrazioni |
| `webhook` | avvisi firmati verso sistemi esterni: coda nel DB, invio con ripetizioni |
| `config` | cifratura dei segreti, pianificazione, compatibilità dello schema |

### Concetti chiave
- **Pratica** = istanza di processo Activiti. Titolo da un campo del form marcato `titolo`.
- **Form**: JSON `{fields:[{name,label,type,required,readonly,options,titolo}], documenti, documentiObbligatori}`.
  Tipi: text, textarea, number, date, select, checkbox. L'interfaccia invia i numeri come testo.
- Estensioni BPMN proprie: namespace `http://orditogram.it/bpmn`, prefisso `orditogram:`
  (es. `<orditogram:datiMessaggio>motivoRinuncia</orditogram:datiMessaggio>`); estensioni Activiti per il resto.
- **Sicurezza**: due catene. `/api/v1/**` (ordine 1): solo chiavi API, stateless. Tutto il resto (ordine 2):
  utenti locali con Basic (solo se `app.login.locale`) o sessione OIDC, `/api/admin/**` solo amministratori.
- **Storico → eventi**: `StoricoService.registra` pubblica `EventoStorico`; i webhook lo ascoltano nella stessa transazione.

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
- Gli stati salvati nel DB come testo, non enum Hibernate (i vincoli CHECK non si aggiornano: vedi `SchemaCompatibilita`).
- Versioni MAGGIORE.MINORE.CORREZIONE in `backend/pom.xml` e `frontend/package.json`, allineate.

## Insidie già incontrate
- **H2 nei test**: niente `MODE=PostgreSQL`, lo script di Activiti per H2 usa il tipo `IDENTITY`.
- **Pagina `/error`**: le risposte d'errore passano dal dispatch ERROR; è `permitAll`, altrimenti chi non ha
  utente (sistemi esterni) riceve 401 invece del vero errore.
- **Dipendenze di test** vanno in `<dependencies>`, non in `<dependencyManagement>`.
- Activiti: i dati dei messaggi vanno impostati con `setVariables` prima di `messageEventReceived`;
  le espressioni `${...}` negli output dei mapping sono rifiutate; il clock del motore si legge pigramente.
- Le email partono dopo il commit (`TransactionSynchronization`); senza `spring.mail.host` restano "non inviate".
- bpmn-js: estensioni moddle `activiti` e `orditogram`; la simulazione usa `bpmn-js-token-simulation` 0.35.

## Stato e roadmap
- **0.9** fondamenta: ricerca paginata, registro, Docker, test, CI, backup.
- **0.10** integrazioni: API pubblica `/api/v1`, chiavi, OpenAPI generata, webhook firmati.
- **0.11** nuovo nome Orditogram, Maven Wrapper, questo file.
- Da verificare alla prima build sul Mac: `ApiPubblicaIntegrationTest` (scritto senza poterlo eseguire).
- **Prossime fasi**: 3) form avanzati (sezioni, campi condizionali, tabelle di righe, validazioni);
  4) documenti da modello (lettere, provvedimenti); 5) tabelle decisionali DMN; 6) multi-ente / white label;
  7) versioni dei form e ruolo "analista del cliente"; 8) cruscotti; 9) libreria di modelli di processo;
  10) documentazione automatica del processo; 11) assistente IA per analisti.
