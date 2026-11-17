# BPMN per Orditogram

Fonte di verità: `backend/.../process/ProcessValidator.java`, i connettori (`integrazione/ApiConnector`,
`notifica/EmailConnector`, `notifica/NotificaConnector`) e l'esempio `esempi/accesso-atti/accesso-atti.bpmn20.xml`.
Se il codice e questo file non coincidono, vale il codice: aggiorna questo file.

## Intestazione

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:activiti="http://activiti.org/bpmn"
             xmlns:orditogram="http://orditogram.it/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             id="Definitions_<chiave>" targetNamespace="http://orditogram.it/processi">
  <message id="nomeMessaggio" name="nomeMessaggio"/>      <!-- uno per ogni messaggio atteso -->
  <process id="<chiave>" name="<Nome leggibile>" isExecutable="true" activiti:candidateStarterGroups="operatori">
    <documentation>Una o due frasi su cosa fa il processo: finiscono nella scheda tecnica.</documentation>
    ...
  </process>
  <!-- BPMNDiagram: lo genera script/disposizione.py -->
</definitions>
```

- Un solo `<process>` eseguibile. `id` = chiave del processo: lettere, numeri, `.`, `-`, `_`, inizia con una lettera.
  Usa camelCase descrittivo (`contributiEventi`). Non cambiarla dopo il primo rilascio: è l'identità del processo.
- `name` è quello che vedono gli utenti ("Nuova pratica → …").
- `activiti:candidateStarterGroups` = chi può avviare (più gruppi separati da virgola). Se mancano e non ci sono
  regole AVVIA in `permessi.json`, avviano tutti. Le regole AVVIA, se ci sono, vincono sul BPMN.

## Elementi ammessi (tutto il resto è un errore)

| Elemento | Uso | Note |
|---|---|---|
| `startEvent` | uno solo nel processo, uno in ogni sottoprocesso | niente timer/messaggi sull'inizio; `activiti:formKey` = form di avvio |
| `endEvent` | almeno una, anche più fini con nomi diversi | niente fini di errore/messaggio; il nome dice com'è finita ("Accolta", "Archiviata") |
| `userTask` | lavoro di una persona | `activiti:candidateGroups` obbligatorio (o candidateUsers/assignee), `activiti:formKey` |
| `serviceTask` | lavoro automatico | `implementation="apiConnector|emailConnector|notificaConnector"`, parametri con `activiti:field` |
| `exclusiveGateway` | decisione | ogni uscita ha una condizione, tranne la `default` ("altrimenti", sempre consigliata) |
| `parallelGateway` | strade in parallelo e loro riunione | apri e chiudi sempre con un parallelo |
| `subProcess` | fase con un termine o una gestione comune | dentro: un inizio, almeno una fine; le frecce non attraversano il bordo |
| `boundaryEvent` | sul bordo di attività o sottoprocesso | timer (scadenza/sollecito), errore, messaggio |
| `intermediateCatchEvent` | attesa nel flusso | timer o messaggio |
| `callActivity` | richiama un altro processo già pubblicato | `calledElement` = chiave, mai un'espressione; non ricorsivo |
| `sequenceFlow` | freccia | `conditionExpression xsi:type="tFormalExpression"` sulle uscite delle decisioni |

**Non supportati** (da segnalare nell'analisi e sostituire): task generico, scriptTask, businessRuleTask,
sendTask/receiveTask, manualTask, gateway inclusivo ed event-based, eventi di lancio (throw), segnali, compensazione,
escalation, inizi con timer o messaggio, multi-istanza, pool/lane multipli (le lane si possono tenere, sono ignorate),
`activiti:class`, `delegateExpression`, `expression` sui service task.
Sostituzioni tipiche: gateway inclusivo → paralleli + decisioni dentro i rami; event-based (aspetto A o B) →
attività con un messaggio e un timer sul bordo; avvio a orario → processo avviato da un sistema esterno con l'API.

## Attività di persone

```xml
<userTask id="esame" name="Esamina la domanda" activiti:candidateGroups="istruttori" activiti:formKey="ce-esame"/>
```
- Gruppi: nomi semplici in minuscolo (`istruttori`, `commissione`). Sono i gruppi degli utenti (locali o dall'IdP).
- `candidateGroups="parte:richiedente"`: l'attività la lavora chi ha avviato la pratica (anche un utente esterno).
  `parte:<ruolo>`: l'organizzazione indicata da un campo `organizzazione` con `"parte": "<ruolo>"` in un form.
- Nomi delle attività: verbo all'imperativo + oggetto ("Valuta la domanda"), sono quelli della scrivania.

## Attività automatiche (connettori)

Nei testi `${nomeDato}` inserisce un dato della pratica. Gli a-capo nei valori si scrivono `&#10;`.

**Chiamata a un servizio** (`apiConnector`):
```xml
<serviceTask id="protocolla" name="Protocolla la domanda" implementation="apiConnector">
  <extensionElements>
    <activiti:field name="metodo" stringValue="POST"/>                       <!-- GET POST PUT PATCH DELETE -->
    <activiti:field name="url" stringValue="https://protocollo.ente.it/api/registra"/>
    <activiti:field name="credenziale" stringValue="protocollo"/>            <!-- nome configurato sul server -->
    <activiti:field name="intestazioni" stringValue="X-Ente: comune"/>       <!-- una per riga -->
    <activiti:field name="corpo" stringValue='{"oggetto": "${oggetto}"}'/>
    <activiti:field name="risposta" stringValue="numeroProtocollo = numero&#10;dataProtocollo = data"/>
    <activiti:field name="timeout" stringValue="20"/>
    <activiti:field name="inErrore" stringValue="Segui la strada di errore"/> <!-- o "Blocca e riprova più tardi" -->
  </extensionElements>
</serviceTask>
<boundaryEvent id="erroreProtocollo" name="Protocollo non disponibile" attachedToRef="protocolla">
  <errorEventDefinition/>
</boundaryEvent>
```
- `risposta`: `dato = percorso` (es. `esito.codice`, `elenco[0].id`, `@stato` = codice HTTP). I dati diventano
  disponibili per i passi successivi e per le condizioni.
- Con "Segui la strada di errore" metti SEMPRE un `errorEventDefinition` sul bordo (o sul sottoprocesso che la contiene)
  con una strada alternativa, di solito un'attività "… a mano" che chiede gli stessi dati.
- Credenziali, token e password non vanno MAI nel BPMN: solo il nome della credenziale.
- Sviluppo/collaudo: `http://localhost:8080/api/demo/protocollo` con credenziale `protocollo-demo` risponde
  `{numero, data}`; `?simula=errore` fa fallire la chiamata.

**Email** (`emailConnector`): `destinatari` (elenco con virgole di `iniziatore`, `gruppo:x`, `utente:x`, `campo:x`
= indirizzo scritto in un campo, `email:indirizzo`), `oggetto`, `testo`, `allegati` (`Nessuno` |
`Tutti i documenti della pratica`). Le email partono dopo il salvataggio; senza server email risultano "non inviate"
e la pratica prosegue.

**Notifica** (`notificaConnector`): `destinatario` (`iniziatore`, `gruppo:x`, username, separati da virgole),
`titolo`, `messaggio`. Compare nella campanella (e per email se configurata).

## Decisioni e condizioni

```xml
<exclusiveGateway id="esitoEsame" name="Esito dell'esame?" default="fEsameAltrimenti"/>
<sequenceFlow id="fIntegra" name="Serve integrazione" sourceRef="esitoEsame" targetRef="...">
  <conditionExpression xsi:type="tFormalExpression">${esito == 'Serve integrazione'}</conditionExpression>
</sequenceFlow>
<sequenceFlow id="fEsameAltrimenti" name="Altrimenti" sourceRef="esitoEsame" targetRef="..."/>
```
- La condizione usa i `name` dei campi dei form, i dati della risposta di un'API, i dati dei messaggi o
  `avviataDa`, `avviataDaUtente`. Un dato che nessuno produce è un avviso: quasi sempre un errore di battitura.
- Scelte (`select`): confronta con il testo ESATTO di un'opzione (verifica.py lo controlla).
- Caselle (`checkbox`): `${urgente}` oppure `${!urgente}`.
- Numeri: l'interfaccia li invia come testo. Confronta con un numero decimale: `${importo > 5000.0}`
  (EL converte entrambi in numero). Mai `importo > '5000'` (confronto tra testi).
- Date: testo `AAAA-MM-GG`, si confrontano come testo: `${dataEvento < '2027-01-01'}`.
- Testo vuoto: `${empty noteIstruttoria}`.

## Tempo: scadenze, solleciti, attese

```xml
<!-- scadenza: interrompe il sottoprocesso/attività e prende un'altra strada -->
<boundaryEvent id="termine60" name="Termine di 60 giorni" attachedToRef="istruttoria">
  <timerEventDefinition activiti:businessCalendarName="proroga"><timeDuration>P60D</timeDuration></timerEventDefinition>
</boundaryEvent>
<!-- sollecito: l'attività resta aperta -->
<boundaryEvent id="sollecito" name="Sollecito settimanale" attachedToRef="esame" cancelActivity="false">
  <timerEventDefinition><timeCycle>R3/P7D</timeCycle></timerEventDefinition>
</boundaryEvent>
<!-- attesa nel flusso -->
<intermediateCatchEvent id="attesa" name="10 giorni per le opposizioni">
  <timerEventDefinition activiti:businessCalendarName="lavorativi"><timeDuration>P10D</timeDuration></timerEventDefinition>
</intermediateCatchEvent>
```
- Durate ISO 8601: `P30D`, `PT4H`, `P1M`. Ripetizioni: `R3/P7D` (non con un calendario). Data: `timeDate` con
  `${dataScadenza}` letta da un campo data.
- Calendari: `lavorativi` (conta solo i giorni lavorativi), `proroga` (se scade in un festivo slitta al primo
  lavorativo, art. 2963 c.c.: è quello giusto per i termini dei procedimenti amministrativi). Nessun altro nome.
- Il sollecito con ripetizione deve avere `cancelActivity="false"`. Dopo il sollecito: notifica e una fine nel
  sottoprocesso (o una fine dedicata), non un ritorno all'attività.

## Messaggi da sistemi esterni o registrati dall'operatore

```xml
<boundaryEvent id="rinuncia" name="Rinuncia del richiedente" attachedToRef="istruttoria">
  <extensionElements><orditogram:datiMessaggio>motivoRinuncia</orditogram:datiMessaggio></extensionElements>
  <messageEventDefinition messageRef="rinunciaRichiedente"/>
</boundaryEvent>
```
- Dichiara `<message id="X" name="X"/>` sotto `<definitions>`: nome senza spazi, inizia con una lettera. È anche
  l'indirizzo `POST /api/messaggi/X` (sistemi con chiave API `messaggi:invia`) e
  `POST /api/pratiche/{id}/messaggi/X` (operatore dalla scheda della pratica).
- `orditogram:datiMessaggio`: i dati che il messaggio porta (separati da virgola), usabili nelle condizioni.
- Un messaggio sul bordo di un sottoprocesso vale per tutta la fase (rinuncia, ritiro, annullamento in autotutela).
- **Registra arrivo** (operatore, dalla scheda della pratica) non chiede dati né allegati: i `datiMessaggio`
  arrivano solo dai sistemi esterni. Se è una persona a registrare qualcosa che va scritto o allegato (integrazioni
  ricevute via PEC), usa un'attività con un form, non un messaggio.

## Sottoprocessi e processi richiamati

- Sottoprocesso = fase con un termine comune (istruttoria entro 30 giorni) o una gestione comune (rinuncia in
  qualsiasi momento). Un'uscita, poi una decisione fuori se servono strade diverse (l'esito resta nei dati).
- Il ritorno "riesame dopo le integrazioni" si fa con una freccia dal sottoprocesso integrazioni all'istruttoria:
  il termine riparte da capo.
- `callActivity calledElement="altroProcesso"`: il processo richiamato deve essere già pubblicato; riceve e
  restituisce tutti i dati. Metti un errore sul bordo se il figlio può fallire.

## Nomi e identificativi

- Ogni `id` unico in tutto il file, leggibile (`esame`, `fEsameIntegra`), niente id generati tipo `Activity_0x9f`
  quando scrivi da zero (se il BPMN arriva dall'analista, tienili).
- Ogni elemento con un `name` in italiano: lo vedono utenti, storico e documentazione.
- Frecce in uscita dalle decisioni con un `name` breve ("Sì", "Respinta", "Altrimenti").

## Disegno

Lo Studio mostra solo ciò che ha una forma nel `BPMNDiagram`. Per un BPMN scritto da te: `script/disposizione.py
processo.bpmn20.xml` (aggiunge il disegno se manca; `--rifai` lo ricrea). Se il BPMN arriva dall'analista con il suo
disegno, non rifarlo: aggiungi solo le forme che mancano (l'avviso di verifica.py le elenca) o chiedi.
Controlla il risultato con `node script/anteprima.mjs processo.bpmn20.xml diagramma.png` e guardalo.
