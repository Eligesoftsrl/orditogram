# Accesso agli atti (legge 241/1990)

Processo dimostrativo di un procedimento amministrativo: la richiesta di accesso ai documenti
(art. 22 e seguenti della legge 241/1990, DPR 184/2006). Serve a mostrare in un caso reale tutto
quello che l'analista può disegnare in Orditogram.

## Il flusso

1. **Istanza ricevuta**: l'operatore registra la richiesta del cittadino (form `aa-istanza`) e allega l'istanza firmata.
2. **Protocolla l'istanza**: chiamata API al protocollo (servizio di prova `/api/demo/protocollo`).
   Il numero e la data restituiti diventano i dati `numeroProtocollo` e `dataProtocollo`.
   *Se non riesce*: l'attività "Protocolla a mano" (operatori) chiede di inserirli a mano.
3. **Conferma ricezione al cittadino**: email all'indirizzo scritto nel form, con il numero di protocollo.
4. **Istruttoria (30 giorni)**, sottoprocesso con un termine sul bordo:
   - **Esamina la richiesta** (responsabili), con un **sollecito settimanale** (al massimo 3) al gruppo;
   - *Ci sono controinteressati*: email ai controinteressati, poi **attesa di 10 giorni** per le opposizioni;
   - **Decidi sulla richiesta** (responsabili): Accolta, Accolta in parte, Respinta, con il provvedimento allegato.
   - **Termine di 30 giorni** scaduto: l'istruttoria si chiude, il responsabile viene avvisato, il cittadino informato
     del silenzio-rigetto (art. 25, comma 4) e dei rimedi.
5. *Serve integrazione*: sottoprocesso **Richiesta di integrazioni**: email al cittadino con le note dell'istruttoria,
   l'operatore registra quanto ricevuto e la richiesta torna in esame (il termine di 30 giorni riparte).
   Senza risposta in **10 giorni** la pratica viene archiviata.
6. **Rinuncia del richiedente**: messaggio in arrivo sul bordo dell'istruttoria. Lo invia il portale
   (`POST /api/messaggi/rinunciaRichiedente`) o lo registra un operatore dalla scheda: la pratica si archivia.
7. **Comunica l'esito al cittadino**: email con la decisione, la motivazione e i documenti della pratica in allegato.

## Cosa mostra

| Elemento | Dove |
|---|---|
| Chiamata a un servizio esterno con credenziale e dati dalla risposta | Protocolla l'istanza |
| Strada di errore | Protocollo non disponibile → Protocolla a mano |
| Email a un indirizzo preso dal form, con allegati | Conferma ricezione, Comunica l'esito |
| Sollecito ripetuto, l'attività resta aperta | Sollecito settimanale su "Esamina la richiesta" |
| Scadenza su un sottoprocesso | Termine di 30 giorni |
| Attesa nel flusso | 10 giorni per le opposizioni |
| Sottoprocesso con il proprio termine | Richiesta di integrazioni |
| Ciclo che torna indietro | Riesame dopo le integrazioni |
| Termini prorogati se cadono in un festivo | 30 giorni dell'istruttoria, 10 per opposizioni e integrazioni |
| Messaggio in arrivo da un sistema esterno | Rinuncia del richiedente |

## Come provarlo

1. Entra come **admin** → Studio → Processi → **Importa pacchetto**: `accesso-atti.orditogram.zip` (BPMN e cinque form).
   Si apre come bozza nell'editor: il controllo in basso deve dire "Pronto da pubblicare". Premi **Pubblica**.
   In alternativa: **Carica BPMN** con `accesso-atti.bpmn20.xml` e i cinque `aa-*.json`.
2. Entra come **mario** (operatori) → Nuova pratica → Accesso agli atti. Scrivi un'email vera o finta
   nel campo del richiedente e allega un file.
3. Nella scheda della pratica, Storico: trovi la chiamata al protocollo con il numero ricevuto e l'email al cittadino
   (senza server email configurato risulta "non inviata", la pratica prosegue comunque).
4. Entra come **anna** (responsabili): in scrivania "Esamina la richiesta" con la scadenza "scade tra 30 giorni".
   - scegli *Serve integrazione* e scrivi cosa manca → mario trova "Registra le integrazioni" → la richiesta torna ad anna;
   - scegli *Ci sono controinteressati* e indica un'email → la pratica resta in attesa 10 giorni (visibile in Pratiche);
   - scegli *Accoglibile* → "Decidi sulla richiesta" → l'esito parte per email con gli allegati.

**Provare la strada di errore**: nell'editor, attività "Protocolla l'istanza", aggiungi `?simula=errore` all'indirizzo
e pubblica: la pratica va a "Protocolla a mano" e lo storico riporta il motivo.

**Provare la rinuncia dal "portale"**: imposta la variabile d'ambiente `PORTALE_API_KEY` (es. `prova123`), riavvia e:

```
curl -X POST http://localhost:8080/api/messaggi/rinunciaRichiedente \
  -H "X-API-Key: prova123" -H "Content-Type: application/json" \
  -d '{"cerca": {"numeroProtocollo": "2026/000101"}, "dati": {"motivoRinuncia": "Documenti già ottenuti"}}'
```

**Provare il percorso senza avviare pratiche vere**: nell'editor premi **Simula**.

**Provare i tempi senza aspettare giorni**: nell'editor imposta il termine a "dopo 2 minuti" e il sollecito a
"ogni 1 minuto", pubblica e avvia una nuova pratica. Le pratiche già avviate mantengono la versione con cui sono partite.

## Note

- I termini sono in giorni di calendario, prorogati al primo giorno lavorativo se cadono in un festivo. Le sospensioni previste dalla norma (es. durante l'integrazione) sono rese
  facendo ripartire il termine al riesame.
- L'indirizzo del servizio di protocollo è `http://localhost:8080/...`: in un'installazione reale va sostituito con
  quello del protocollo informatico dell'ente e la credenziale `protocollo-demo` con quella vera (vedi il README principale).
