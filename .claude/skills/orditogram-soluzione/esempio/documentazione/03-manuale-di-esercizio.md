# Manuale di esercizio: contributi per eventi culturali

Per gli amministratori di Orditogram del Comune. L'installazione e la configurazione generale di Orditogram sono
nel README di Orditogram; qui ci sono le scelte e i passi propri di questa soluzione.

## 1. Requisiti

Orditogram 0.11 o successivo (Docker con `docker compose up -d`, oppure Java 21 e PostgreSQL 16). In produzione:
`ORDITOGRAM_CHIAVE_CIFRATURA` impostata, `app.demo.utenti: false`, `app.demo.servizi: false`, password
dell'utente `admin` cambiata, HTTPS davanti all'applicazione (`server.forward-headers-strategy: framework` se c'è
un reverse proxy).

## 2. Installare la soluzione

1. **Studio → Processi → Importa pacchetto** → `contributiEventi.orditogram.zip`. Il pacchetto crea l'anagrafica
   **Albo delle associazioni** (se manca), i sei form `ce-*`, la bozza del processo e le regole di accesso.
2. Nell'editor, il controllo in basso deve dire **Pronto da pubblicare**. Cambia l'attività **Protocolla la
   domanda** (vedi 3) e premi **Pubblica**.
3. **Studio → Anagrafiche → Albo delle associazioni → Chi può accedere**: `cultura` modifica ed esporta, `dirigenti` vedono.

In alternativa, da un computer con il repository di Orditogram:
`python3 .claude/skills/orditogram-soluzione/script/installa.py <cartella> --url https://… --admin utente:password`
(fa i passi 1-3 e pubblica).

## 3. Configurazione richiesta

| Cosa | Dove | Valore |
|---|---|---|
| Gruppi `cultura`, `dirigenti`, `ragioneria` | Amministrazione → Utenti, o gruppi del provider di identità (`app.oidc`) | gli utenti dei tre uffici |
| Protocollo informatico | attività **Protocolla la domanda** nell'editor | indirizzo delle API del protocollo al posto di `http://localhost:8080/api/demo/protocollo`; nella **Risposta** i nomi dei campi numero e data restituiti |
| Credenziale del protocollo | `app.credenziali` (o variabili d'ambiente) | una credenziale (es. `protocollo-comune`) da indicare nell'attività al posto di `protocollo-demo` |
| Server email/PEC | `spring.mail` | casella PEC del Comune: senza, le comunicazioni risultano "non inviate" |
| Festività del Comune | `app.calendario` | patrono e chiusure: contano per i termini di 60 e 15 giorni |
| Albo iniziale | Anagrafiche → Albo delle associazioni → Importa | CSV con le associazioni già iscritte |

## 4. Gestione ordinaria

- **Utenti**: un nuovo dipendente dell'Ufficio Cultura va nel gruppo `cultura`, ecc. Chi esce si disattiva.
- **Albo**: lo tiene l'Ufficio Cultura; le associazioni si cessano, non si cancellano.
- **Registro delle azioni** (Amministrazione → Registro): installazioni, pubblicazioni, cambi di permessi.
- **Esportazioni**: elenco pratiche in Excel/CSV/PDF dal menu Pratiche (dirigenti).

## 5. Cambiare il processo

Modifica nello Studio (o importa un nuovo pacchetto) e pubblica: le pratiche già avviate restano sulla versione
con cui sono partite, le nuove usano quella nuova. Prima di pubblicare in produzione prova la modifica in un
ambiente di collaudo (vedi il piano di test).

## 6. Backup e ripristino

`strumenti/backup.sh` salva database e documenti; `strumenti/ripristino.sh` li ripristina. Consigliato: backup
giornaliero, conservazione 30 giorni, una prova di ripristino ogni sei mesi in un ambiente separato.

## 7. Monitoraggio

- Salute dell'applicazione: `/actuator/health` (anche `liveness` e `readiness`).
- Pratica ferma: la scheda della pratica mostra il passo corrente e lo storico (errori delle chiamate al protocollo,
  email non inviate, scadenze scattate).
- Le scadenze e i solleciti li esegue il motore in automatico (`spring.activiti.async-executor-activate: true`).

## 8. Problemi noti

| Sintomo | Causa probabile | Cosa fare |
|---|---|---|
| Tutte le domande vanno in "Protocolla a mano" | API del protocollo non raggiungibile o credenziale sbagliata | storico della pratica (messaggio di errore), verifica indirizzo e credenziale |
| PEC "non inviate" | `spring.mail` assente o server non raggiungibile | configura la posta; le comunicazioni già perse vanno inviate a mano |
| Solleciti o termini che non scattano | executor dei timer spento | `spring.activiti.async-executor-activate: true`, riavvio |
| Un operatore non vede le attività | non è nel gruppo, o il gruppo dell'IdP ha un altro nome | Utenti → gruppi, o mappatura in `app.oidc` |
| Nel form l'associazione non si trova | non è nell'albo, è cessata, o l'utente non ha accesso all'albo | iscrivila/riattivala; controlla l'accesso dell'anagrafica |
| Il termine scade di un giorno diverso dal previsto | festività del Comune non configurate | `app.calendario` |

## 9. Sicurezza

Solo gli amministratori e gli analisti hanno i permessi dello Studio in produzione. Le credenziali del protocollo
stanno solo nella configurazione del server, mai nel diagramma. Le azioni di amministrazione finiscono nel registro.
