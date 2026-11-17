# Checklist di collaudo: contributi per eventi culturali

Il collaudo automatico è verde (9 scenari su 9, tutto il diagramma percorso: vedi `report.md`). Questa checklist
serve a controllare quello che vede una persona. Prima di iniziare: pacchetto importato e pubblicato, permessi
dell'albo applicati, utenti nei gruppi `cultura` (es. **mario**), `dirigenti` (es. **laura**), `ragioneria`.

## Dati di prova

| Associazione (albo) | Codice fiscale | N. iscrizione | Settore | Legale rappresentante |
|---|---|---|---|---|
| Banda musicale di Valfiorita | 90011122232 | A-014 | Musica | Giorgio Valli |
| Compagnia teatrale Il Sipario | 90044455567 | A-027 | Teatro | Marta Sala |

| Domanda | Evento | Data | Contributo | Spese |
|---|---|---|---|---|
| A | Concerto d'estate in piazza, Piazza Roma | 10/07/2027 | 3000 | Service audio 1800, Rimborsi ospiti 2200 |
| B | Rassegna teatrale d'autunno, Teatro comunale | 01/10/2027 | 8000 | Cachet compagnie 9000, Allestimento 3000 |

## Controlli

| # | Entra come | Fai | Devi vedere | ✓ |
|---|---|---|---|---|
| 1 | mario | Anagrafiche → Albo delle associazioni → nuova scheda con la Banda | La scheda salvata; con codice fiscale 12345678901 un errore sul codice fiscale | ☐ |
| 2 | mario | Nuova pratica → Contributi per eventi culturali | Il form in tre sezioni: Associazione, Evento, Spese e contributo | ☐ |
| 3 | mario | Spunta **Chiede anche il patrocinio** e poi toglila | **Userà il logo del Comune** compare e scompare | ☐ |
| 4 | mario | Prova data dell'evento passata, contributo 50, descrizione di 10 caratteri | Un messaggio chiaro su ciascun campo, la domanda non parte | ☐ |
| 5 | mario | Compila la domanda A, cerca "Banda" nell'associazione, allega un PDF, **Registra la domanda** | La pratica "Concerto d'estate in piazza" con il numero di protocollo; nello storico la chiamata al protocollo e la PEC | ☐ |
| 6 | mario | Scrivania → **Verifica la domanda** | I dati della domanda in sola lettura, l'allegato, la scadenza dell'istruttoria | ☐ |
| 7 | mario | Esito **Serve integrazione** | Compare il campo per scrivere cosa manca, obbligatorio | ☐ |
| 8 | mario | Esito **Ammissibile**, conferma | La pratica passa a **Decidi il contributo** (sotto i 5.000 euro, niente parere) | ☐ |
| 9 | mario | **Concesso**, 2500, motivazione | **Contributo concesso** compare solo con Concesso; nello storico la PEC con gli allegati | ☐ |
| 10 | utente ragioneria | Scrivania → **Liquida il contributo**, mandato e data | La pratica conclusa come "Contributo liquidato" | ☐ |
| 11 | mario | Domanda B, verifica **Ammissibile** | L'attività va ai dirigenti | ☐ |
| 12 | laura | **Dai il parere sul contributo**: Contrario con note | La tabella delle spese in sola lettura; le note obbligatorie con Contrario | ☐ |
| 13 | mario | **Decidi il contributo** della domanda B | Parere e note del dirigente visibili | ☐ |
| 14 | mario | Nuova domanda, verifica **Serve integrazione**, poi **Registra le integrazioni** con un allegato | La domanda torna in verifica con "Integrazioni ricevute" compilato | ☐ |
| 15 | mario | Su una pratica in istruttoria: **Può arrivare: Rinuncia dell'associazione** → **Registra arrivo** | La pratica si chiude come "Archiviata per rinuncia" | ☐ |
| 16 | laura | Pratiche → filtri → **Esporta** | L'elenco in Excel con le pratiche di prova | ☐ |
| 17 | utente ragioneria | Pratiche | Solo le pratiche che ha liquidato | ☐ |

## Non provato in automatico

- PEC reali (server di posta da configurare), protocollo reale del Comune, festività del Comune nei termini.

## Da chiedere al Comune prima della produzione

- Indirizzo e autenticazione delle API del protocollo; casella PEC per l'invio; festività locali.
- Utenti dei tre gruppi (o nomi dei gruppi nel loro sistema di accesso).
- Risposte alle domande aperte dell'analisi (termine dopo le integrazioni, valore del parere contrario).
