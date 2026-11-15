# Novità di Orditogram

Le versioni seguono il formato MAGGIORE.MINORE.CORREZIONE: cambia la MINORE per funzioni nuove compatibili,
la MAGGIORE quando un aggiornamento richiede interventi (indicati qui sotto).

## 0.12.0 (in preparazione)

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
- **Processi richiamati** (call activity): un processo apre una pratica di un altro processo pubblicato, le passa i
  suoi dati e riprende quando finisce, con i dati raccolti. Nell'editor: *Processo richiamato*. La scheda della
  pratica mostra le pratiche collegate. Controlli alla pubblicazione: processo richiamato pubblicato, niente
  richiami di sé stesso o a vicenda.

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
