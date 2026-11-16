# Analisi: contributi alle associazioni per eventi culturali

Fonte: `specifiche/richiesta-del-cliente.md`. Chiave del processo: `contributiEventi`, prefisso dei form `ce-`.

## Mappa

| Attore | Gruppo | Cosa fa |
|---|---|---|
| Operatore dell'Ufficio Cultura | `cultura` | inserisce la domanda, protocolla a mano, registra le integrazioni, tiene l'albo |
| Istruttore (Ufficio Cultura) | `cultura` | verifica la domanda, decide il contributo |
| Dirigente | `dirigenti` | parere sopra i 5.000 euro, vede tutte le pratiche |
| Ragioneria | `ragioneria` | liquida i contributi concessi |
| Protocollo informatico | sistema (API) | protocolla la domanda e restituisce numero e data |
| Associazione | nessun accesso (riceve email) | presenta la domanda, integra, può rinunciare |

| Passo | Chi | Realizzazione |
|---|---|---|
| Domanda | `cultura` | form di avvio `ce-domanda`, campo anagrafica `associazione` (albo), tabella `spese`, allegati obbligatori |
| Protocollo | sistema | `apiConnector` POST, dati `numeroProtocollo`, `dataProtocollo`; errore → "Protocolla a mano" (`ce-protocollo`) |
| Conferma | sistema | `emailConnector` a `campo:pec` |
| Istruttoria entro 60 giorni | | sottoprocesso con scadenza `P60D` calendario `proroga` |
| Verifica | `cultura` | `ce-verifica`: `esitoVerifica` = Ammissibile / Serve integrazione / Non ammissibile |
| Sollecito | sistema | timer `R2/P10D` non interrompente → notifica a `gruppo:cultura` |
| Parere sopra 5.000 € | `dirigenti` | decisione `${importoRichiesto > 5000.0}` → `ce-parere` |
| Decisione | `cultura` | `ce-decisione`: `esito` = Concesso / Negato, `importoConcesso`, `motivazioneDecisione` |
| Integrazioni | `cultura` | sottoprocesso: email con `noteVerifica`, attività "Registra le integrazioni" (`ce-integrazioni`, allegati), scadenza `P15D` → archiviazione; poi ritorno all'istruttoria |
| Esito | sistema | email di concessione, diniego o non ammissibilità |
| Liquidazione | `ragioneria` | `ce-liquidazione`: `numeroMandato`, `dataPagamento` |
| Termine decorso | sistema | notifica a `gruppo:dirigenti`, fine "Termine decorso" |
| Rinuncia | operatore | messaggio `rinunciaAssociazione` sul bordo dell'istruttoria (Registra arrivo dalla scheda) |

Anagrafica `associazioni` (albo): denominazione (titolo, ricercabile), codice fiscale (chiave), sede, PEC, settore,
legale rappresentante, numero di iscrizione (ricercabile). Permessi: `cultura` modifica, `dirigenti` vedono.

Permessi del processo: avvia `cultura`; vedono tutte le pratiche `cultura` e `dirigenti`. La Ragioneria vede le
pratiche di cui lavora la liquidazione (regola standard di Orditogram), come chiede la specifica.

## Verifica rispetto a Orditogram

Tutto realizzabile con gli elementi supportati. Nessuna richiesta di sviluppo.

## Lacune e scelte fatte

1. *"60 giorni dal protocollo"*: il termine parte quando inizia l'istruttoria, cioè subito dopo il protocollo e la
   conferma (pochi secondi dopo). Calendario `proroga`: se scade di festivo slitta al primo giorno lavorativo.
2. *Dopo le integrazioni "l'istruttoria riparte"*: il termine di 60 giorni riparte da capo (come nel procedimento
   amministrativo, art. 2 c. 7 L. 241/1990). Da confermare con l'Ufficio Cultura.
3. *Parere del dirigente*: la specifica non dice cosa succede con un parere contrario. Scelta: il parere è
   obbligatorio ma non vincolante, l'istruttore decide comunque e il parere è visibile nella decisione.
4. *Soglia 5.000 €*: sull'importo richiesto ("sopra" = strettamente maggiore).
5. *Termine decorso*: la specifica dice che la domanda si decide fuori dal sistema; la pratica si chiude con la fine
   "Termine decorso" e la notifica ai dirigenti. Nessuna email all'associazione (non prevista).
6. *Rinuncia*: la registra l'operatore dalla scheda della pratica (Registra arrivo). Il pulsante non chiede dati,
   quindi il motivo si annota allegando la lettera di rinuncia. Nessuna email.
7. *Non ammissibile*: motivazione obbligatoria, comunicata all'associazione via email.
8. *PEC*: chiesta nel form della domanda (serve un indirizzo certo per le comunicazioni di questa pratica), anche se
   l'albo ha la PEC dell'associazione.
9. Protocollo: in collaudo il servizio di prova di Orditogram (`/api/demo/protocollo`, credenziale `protocollo-demo`).
   In produzione servono l'indirizzo vero e la credenziale `protocollo-comune`.
10. *Integrazioni*: prima versione con un messaggio in arrivo; cambiata in un'attività con form perché "Registra
   arrivo" non permette all'operatore di scrivere cosa è arrivato né di allegare (scoperto controllando l'interfaccia).

## Domande aperte per il cliente

- Confermate che dopo le integrazioni i 60 giorni ripartono da capo (scelta 2)?
- Il parere contrario del dirigente deve bloccare la concessione (scelta 3)?
- Indirizzo e modalità di autenticazione delle API del protocollo informatico.
