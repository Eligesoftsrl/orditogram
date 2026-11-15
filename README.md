# Orditogram

Piattaforma di processo: l'analista disegna il BPMN e configura i form, Orditogram lo fa funzionare.
Attività per gruppi, documenti con versioni e verifica delle firme, notifiche, storico completo.
Nessun codice da scrivere per un nuovo processo.

**Stack:** JDK 21, Spring Boot 3.5, Activiti 8.8, PostgreSQL, React 18 + Tailwind 4.

## Avvio con Docker (il modo più semplice)

```
cp .env.example .env        # e cambia almeno le password
docker compose up -d
```

Orditogram risponde su http://localhost:8081 (utente `admin`, password `ADMIN_PASSWORD_INIZIALE` del file `.env`).
Per i dati di prova metti `UTENTI_DI_PROVA=true` e `SERVIZI_DI_PROVA=true`. Con `--profile keycloak` parte anche un
Keycloak di prova su http://localhost:8180.

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

Apri http://localhost:5173. Le tabelle si creano da sole. Con `app.demo.utenti: true`
vengono creati gli utenti di prova (password `password`, tranne `admin` / `admin`):

| Utente | Nome | Gruppi |
|---|---|---|
| laura | Laura Verdi | dirigenti |
| paolo, chiara | Paolo Neri, Chiara Galli | analisti |
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
cd backend && mvn -Panalisi verify   # copertura (target/site/jacoco), SpotBugs, SBOM (target/bom.json)
```

## Backup e ripristino

`strumenti/backup.sh` salva database e documenti in una cartella datata (con Docker Compose o, con `MODO=locale`,
su un'installazione tradizionale); `strumenti/ripristino.sh <cartella>` li rimette al loro posto dopo una conferma.
Conserva i backup fuori dal server.

## Registro delle azioni

Studio, Registro: pubblicazioni di processi, form salvati ed eliminati, utenti creati, modificati ed eliminati,
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

| Proprietà di un campo | Effetto |
|---|---|
| `type` | text, textarea, number, date, select, checkbox |
| `required` / `readonly` | obbligatorio / mostrato nel riepilogo in sola lettura |
| `precompila: false` | non ripropone il valore precedente (utile nei cicli verifica ↔ revisione) |
| `titolo: true` | il valore diventa il titolo della pratica |
| stesso `name` in più form | è lo stesso dato della pratica |
| `visibileSe: { campo, valori }` | il campo compare solo se l'altro campo ha uno dei valori indicati |
| `min`, `max` | numero: valori ammessi; data: `AAAA-MM-GG` oppure `oggi` |
| `minLunghezza`, `maxLunghezza` | testo: numero di caratteri |
| `formato` | testo: `email`, `codiceFiscale`, `partitaIva`, `iban`, `cap`, `telefono`, `espressione` (con `espressione` e `messaggioFormato`) |

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
mai a un amministratore locale) e a ogni accesso nome, email e gruppi vengono aggiornati dal token (`app.oidc`):
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
