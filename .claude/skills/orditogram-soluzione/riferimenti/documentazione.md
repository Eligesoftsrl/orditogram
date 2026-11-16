# Documentazione per il cliente

Obiettivo: con questi documenti il cliente usa, amministra e fa evolvere l'applicazione senza chiamarci.
Si scrivono DOPO il collaudo verde, partendo dai fatti: `documentazione/scheda-tecnica.md` (generata da
`script/documenta.py`), `collaudo/report.md`, l'analisi delle specifiche. Mai inventare funzioni, schermate o
configurazioni: se un fatto non è negli artefatti o nel README di Orditogram, non va scritto (o va chiesto).

Tutti in `documentazione/` della soluzione, in Markdown (la sorgente resta versionabile). Se il cliente li vuole in
Word o PDF, convertili alla fine (skill dei documenti, o `pandoc`), senza cambiare il contenuto.

| File | Per chi | Contenuto |
|---|---|---|
| `README.md` | tutti | indice, versione della soluzione e di Orditogram, data, a chi rivolgersi |
| `01-documento-funzionale.md` | committente, dirigenti | cosa fa l'applicazione e perché |
| `02-manuale-utente.md` | operatori, uno per gruppo | come si lavora, attività per attività |
| `03-manuale-di-esercizio.md` | amministratori di sistema e applicativi | installazione, configurazione, gestione, problemi |
| `04-scheda-tecnica.md` | analisti, chi farà evolvere | generata: passi, form, regole, integrazioni |
| `05-piano-e-report-di-test.md` | committente, collaudatori | cosa è stato provato, come, con quale esito |
| `06-integrazioni.md` | sviluppatori dei sistemi collegati | solo se ci sono API, messaggi o webhook |
| `diagramma.png` | tutti | `node script/anteprima.mjs processo.bpmn20.xml documentazione/diagramma.png` |

Stile: italiano semplice, frasi brevi, la seconda persona per i manuali ("Apri la scrivania"), i nomi esatti delle
attività, dei campi e dei pulsanti come compaiono nell'applicazione, in grassetto. Niente gergo tecnico nei documenti
01 e 02 (non "userTask", "gateway", "variabile": "attività", "decisione", "dato").

## 01 Documento funzionale

1. **Scopo**: il procedimento o servizio, la norma o la regola aziendale di riferimento, il risultato per l'utente.
2. **Chi è coinvolto**: tabella attore → gruppo in Orditogram → cosa fa (compresi i sistemi esterni e gli esterni).
3. **Il flusso in breve**: il diagramma e una descrizione numerata, un paragrafo per fase, con le strade alternative.
4. **Regole**: termini e come si contano (calendario, proroga), decisioni e criteri, cosa succede alla scadenza,
   solleciti, integrazioni, rinunce.
5. **Dati raccolti**: per form, i dati principali e perché servono (base per il registro dei trattamenti GDPR:
   segnala i dati personali e quelli particolari).
6. **Comunicazioni**: ogni email e notifica, a chi, quando, con il testo.
7. **Integrazioni**: sistemi chiamati o che chiamano, cosa si scambiano, cosa succede se non rispondono.
8. **Chi vede cosa**: chi avvia, chi vede le pratiche, campi riservati, cosa vedono gli esterni.
9. **Fuori ambito e limiti**: cosa la soluzione non fa (dall'analisi delle specifiche), scelte fatte dove la
   specifica non era chiara.
10. **Glossario**.

## 02 Manuale utente

Un capitolo per ogni gruppo (o profilo) che usa l'applicazione:
1. **Accesso**: indirizzo, credenziali o "Accedi con …" (provider dell'ente), cambio password.
2. **La scrivania**: le attività del gruppo, le scadenze ("scade domani" in giallo, "scaduta" in rosso),
   "Riservala a me", come si trova una pratica (Pratiche, ricerca, filtri, esportazione).
3. **Avviare una pratica** (se il gruppo avvia): Nuova pratica → nome del processo, i campi uno per uno
   (cosa scrivere, quali sono obbligatori, regole come formato del codice fiscale), gli allegati.
4. **Ogni attività del gruppo**, nella stessa forma: *quando arriva* (dopo quale passo), *cosa trovi* (dati in
   sola lettura, documenti), *cosa decidi* (ogni scelta e cosa provoca, con la motivazione), *entro quando*
   (scadenze e solleciti), *cosa succede dopo*.
5. **Documenti**: caricare, versioni, verifica delle firme.
6. **Casi particolari**: rinuncia registrata a mano ("Registra arrivo"), integrazioni, pratiche ferme.
7. **Domande frequenti**: almeno "non vedo l'attività" (gruppo sbagliato o già presa da un collega), "ho sbagliato
   una scelta", "l'email al cittadino risulta non inviata".

Per gli utenti esterni (portale), un capitolo a parte: registrazione o invito, cosa vedono, cosa possono fare.

## 03 Manuale di esercizio (amministrazione)

1. **Requisiti e installazione**: Docker (`docker compose up -d`) o Java 21 + PostgreSQL; indirizzi; variabili
   d'ambiente (`DB_URL`, `DB_UTENTE`, `DB_PASSWORD`, `ORDITOGRAM_CHIAVE_CIFRATURA`, `APP_ADMIN_PASSWORD_INIZIALE`).
   Rimanda al README di Orditogram per i dettagli: non copiarlo, elenca le scelte fatte per questo cliente.
2. **Installare la soluzione**: Studio → Processi → Importa pacchetto (`<chiave>.orditogram.zip`), controllo
   "Pronto da pubblicare", Pubblica; oppure `script/installa.py`. Permessi delle anagrafiche.
3. **Configurazione richiesta** (dalla sezione "Da predisporre" della scheda tecnica): gruppi e utenti o
   mappatura dei gruppi del provider di identità (`app.oidc`), ruoli, strutture, organizzazioni esterne,
   credenziali dei servizi (`app.credenziali`), server email (`spring.mail`), calendario e festività dell'ente
   (`app.calendario`), chiavi API e webhook, `app.demo.utenti: false` e `app.demo.servizi: false` in produzione.
4. **Gestione ordinaria**: utenti e gruppi, anagrafiche (import/export CSV), registro delle azioni, esportazioni.
5. **Nuove versioni del processo**: si modifica nello Studio o si importa un nuovo pacchetto, si pubblica; le
   pratiche in corso restano sulla versione con cui sono partite.
6. **Backup e ripristino**: `strumenti/backup.sh` e `strumenti/ripristino.sh` (database e documenti), frequenza
   consigliata, prova di ripristino.
7. **Monitoraggio**: `/actuator/health` (liveness/readiness), log, storico della pratica per capire dove si è
   fermata; pratiche ferme su un servizio esterno (strada di errore o "Blocca e riprova più tardi").
8. **Problemi noti e soluzioni**: tabella sintomo → causa → cosa fare (email non inviate, timer che non scattano
   con l'executor spento, utente che non vede un'attività, chiamata API in errore, credenziale scaduta).
9. **Sicurezza**: password iniziale dell'admin, HTTPS dietro reverse proxy (`server.forward-headers-strategy`),
   rotazione delle chiavi API, chi ha i permessi dello Studio in produzione.

## 04 Scheda tecnica

`python3 script/documenta.py <cartella> -o <cartella>/documentazione/04-scheda-tecnica.md`. Non va ritoccata a mano:
se è sbagliata, è sbagliato un artefatto. Mettila accanto a `diagramma.png`.

## 05 Piano e report di test

1. **Strategia**: verifica statica (verifica.py, stesse regole della pubblicazione), collaudo automatico sulle
   API (collaudo.py), collaudo manuale dell'analista (checklist), test di accettazione del cliente.
2. **Ambiente**: versione di Orditogram, database, data, utenti di prova.
3. **Scenari**: tabella scenario → strada del processo → dati usati → esito atteso (da `collaudo/scenari.json`).
4. **Esito**: incolla `collaudo/report.md` (esiti, passi, copertura del diagramma) e lo stato della checklist.
5. **Non provato in automatico**: con il motivo e come provarlo (email reali, servizi reali, firma digitale).
6. **Criteri di accettazione** per il cliente: tutti gli scenari superati nel suo ambiente, checklist completa,
   configurazioni di produzione fatte.

## 06 Integrazioni (se servono)

Per ogni sistema: cosa chiama o cosa riceve, indirizzo, autenticazione (chiave `X-API-Key` e permessi della chiave),
esempi `curl` completi con i dati veri del processo (campi del form di avvio per `POST /api/v1/processi/<chiave>/pratiche`,
`POST /api/messaggi/<nome>` con `pratica` o `cerca` e i dati del messaggio), risposte ed errori
(`{"errore": {"codice", "messaggio", "campi"}}`), webhook e verifica della firma, `GET /api/v1/openapi.json`.
