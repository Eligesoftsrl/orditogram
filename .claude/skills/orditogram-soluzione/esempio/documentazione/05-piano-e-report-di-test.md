# Piano e report di test: contributi per eventi culturali

## 1. Strategia

| Livello | Strumento | Cosa garantisce |
|---|---|---|
| Verifica statica | `verifica.py` (stesse regole della pubblicazione di Orditogram) | processo, form, anagrafica e permessi pubblicabili; condizioni coerenti con le scelte dei form |
| Collaudo automatico | `collaudo.py` sulle API di Orditogram | ogni strada del processo percorsa con utenti dei gruppi veri, controllo di attività, dati, storico e fine raggiunta |
| Collaudo manuale | [checklist dell'analista](../collaudo/checklist.md) | schermate, testi, usabilità |
| Accettazione | Ufficio Cultura, nell'ambiente del Comune | il processo fa quello che serve all'ufficio |

## 2. Ambiente

Orditogram 0.11 su PostgreSQL 16, utenti di prova creati dal collaudo (`collaudo-cultura`, `collaudo-dirigenti`,
`collaudo-ragioneria`), due associazioni di prova nell'albo, servizio di prova del protocollo. Le scadenze (60 e 15
giorni) e i solleciti sono stati fatti scattare subito anticipandone la data.

## 3. Scenari

| # | Scenario | Strada provata | Dati principali |
|---|---|---|---|
| 1 | Contributo concesso e liquidato (sotto i 5.000 euro) | protocollo automatico → verifica ammissibile → decisione senza parere → concessione → liquidazione | Concerto d'estate in piazza, 3000 € |
| 2 | Protocollo non disponibile: si protocolla a mano | errore del protocollo → Protocolla a mano → conferma → verifica | Mostra fotografica, 400 € (variante: protocollo in errore) |
| 3 | Sopra i 5.000 euro: parere contrario e contributo negato | verifica ammissibile → parere del dirigente → diniego | Rassegna teatrale d'autunno, 8000 € |
| 4 | Domanda non ammissibile | verifica non ammissibile → PEC con il motivo | Cena sociale, 500 € |
| 5 | Integrazioni ricevute: l'istruttoria riprende | servono integrazioni → PEC → Registra le integrazioni → di nuovo verifica → decisione | Laboratorio di teatro per ragazzi, 1200 € |
| 6 | Nessuna integrazione in 15 giorni: archiviata | servono integrazioni → 15 giorni senza risposta → archiviazione | Festa della musica, 1000 € |
| 7 | Sollecito all'istruttore dopo 10 giorni | 10 giorni senza verifica → notifica all'Ufficio Cultura, la verifica resta aperta | Concerto di Natale, 250 € |
| 8 | Termine di 60 giorni scaduto | 60 giorni senza decisione → notifica ai dirigenti → Termine decorso | Teatro in cortile, 600 € |
| 9 | Rinuncia dell'associazione durante l'istruttoria | Registra arrivo della rinuncia → archiviazione | Reading di poesia, 300 € |

## 4. Esito

Eseguito il 16/11/2026 alle 23:48 su http://localhost:8080.

**9 scenari su 9 superati.** Copertura del diagramma: 36 passi su 36.

| Scenario | Esito | Durata | Pratica |
|---|---|---|---|
| Contributo concesso e liquidato (sotto i 5.000 euro) | ✅ superato | 1.6 s | `146df967-e218-11f1-91ad-02fc00000001` |
| Protocollo non disponibile: si protocolla a mano | ✅ superato | 0.9 s | `1575f40b-e218-11f1-91ad-02fc00000001` |
| Sopra i 5.000 euro: parere contrario e contributo negato | ✅ superato | 1.3 s | `15e8b25a-e218-11f1-91ad-02fc00000001` |
| Domanda non ammissibile | ✅ superato | 0.7 s | `16b3f20e-e218-11f1-91ad-02fc00000001` |
| Integrazioni ricevute: l'istruttoria riprende | ✅ superato | 1.2 s | `171943cd-e218-11f1-91ad-02fc00000001` |
| Nessuna integrazione in 15 giorni: archiviata | ✅ superato | 9.6 s | `17cfe935-e218-11f1-91ad-02fc00000001` |
| Sollecito all'istruttore dopo 10 giorni | ✅ superato | 10.2 s | `1d8f0321-e218-11f1-91ad-02fc00000001` |
| Termine di 60 giorni scaduto | ✅ superato | 9.5 s | `23a71156-e218-11f1-91ad-02fc00000001` |
| Rinuncia dell'associazione durante l'istruttoria | ✅ superato | 0.6 s | `2952f139-e218-11f1-91ad-02fc00000001` |

### Contributo concesso e liquidato (sotto i 5.000 euro)

Strada principale: protocollo automatico, verifica ammissibile, decisione senza parere, liquidazione.

1. ✅ collaudo-cultura avvia la pratica "Concerto d'estate in piazza"
2. ✅ controllo: attivita "Verifica la domanda"; dati {"numeroProtocollo": "*", "associazione": "Banda musicale di Valfiorita"}; eventi ["CHIAMATA_API", "EMAIL"]
3. ✅ collaudo-cultura completa "Verifica la domanda"
4. ✅ controllo: attivita "Decidi il contributo"
5. ✅ collaudo-cultura completa "Decidi il contributo"
6. ✅ collaudo-ragioneria completa "Liquida il contributo"
7. ✅ controllo: conclusa true; fine "fineLiquidato"; passa ["emailConcesso"]; visibileA ["gruppo:ragioneria", "gruppo:dirigenti"]

### Protocollo non disponibile: si protocolla a mano

Strada di errore: si pubblica per lo scenario una variante con il servizio di prova che risponde con un errore.

1. ✅ collaudo-cultura avvia la pratica "Mostra fotografica"
2. ✅ controllo: attivita "Protocolla a mano"; eventi ["ERRORE"]
3. ✅ collaudo-cultura completa "Protocolla a mano"
4. ✅ controllo: attivita "Verifica la domanda"; passa ["conferma"]; dati {"numeroProtocollo": "2026/009999"}

### Sopra i 5.000 euro: parere contrario e contributo negato

1. ✅ collaudo-cultura avvia la pratica "Rassegna teatrale d'autunno"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: attivita "Dai il parere sul contributo"
4. ✅ collaudo-dirigenti completa "Dai il parere sul contributo"
5. ✅ collaudo-cultura completa "Decidi il contributo"
6. ✅ controllo: conclusa true; fine "fineNegato"; passa ["parere", "emailNegato"]; dati {"parere": "Contrario"}; nonVisibileA ["gruppo:ragioneria"]

### Domanda non ammissibile

1. ✅ collaudo-cultura avvia la pratica "Cena sociale"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: conclusa true; fine "fineNonAmmissibile"; passa ["emailNonAmmissibile"]

### Integrazioni ricevute: l'istruttoria riprende

1. ✅ collaudo-cultura avvia la pratica "Laboratorio di teatro per ragazzi"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: attivita "Registra le integrazioni"; passa ["emailIntegrazioni"]
4. ✅ collaudo-cultura completa "Registra le integrazioni"
5. ✅ controllo: attivita "Verifica la domanda"; dati {"noteIntegrazioni": "Bilancio firmato arrivato via PEC il 20/11."}
6. ✅ collaudo-cultura completa "Verifica la domanda"
7. ✅ controllo: attivita "Decidi il contributo"

### Nessuna integrazione in 15 giorni: archiviata

1. ✅ collaudo-cultura avvia la pratica "Festa della musica"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ scatta subito il timer "nessunaIntegrazione"
4. ✅ controllo: conclusa true; fine "fineArchiviata"

### Sollecito all'istruttore dopo 10 giorni

1. ✅ collaudo-cultura avvia la pratica "Concerto di Natale"
2. ✅ scatta subito il timer "sollecitoVerifica"
3. ✅ controllo: attivita "Verifica la domanda"; passa ["notificaSollecito"]; eventi ["NOTIFICA"]

### Termine di 60 giorni scaduto

1. ✅ collaudo-cultura avvia la pratica "Teatro in cortile"
2. ✅ scatta subito il timer "termine60"
3. ✅ controllo: conclusa true; fine "fineTermine"; passa ["avvisoTermine"]; eventi ["SCADENZA", "NOTIFICA"]

### Rinuncia dell'associazione durante l'istruttoria

1. ✅ collaudo-cultura avvia la pratica "Reading di poesia"
2. ✅ arriva il messaggio "rinunciaAssociazione"
3. ✅ controllo: conclusa true; fine "fineRinuncia"

### Copertura

Tutti i passi del diagramma sono stati percorsi almeno una volta.

## 5. Non provato in automatico

- Invio reale delle PEC: senza server di posta le email risultano "non inviate" (testo e destinatario sono nello storico).
- Protocollo informatico reale del Comune: provato con il servizio di prova, anche in errore.
- Calcolo dei termini con le festività del Comune: dipende da `app.calendario` dell'installazione.
- Schermate e testi: con la checklist manuale.

## 6. Criteri di accettazione

1. Tutti gli scenari superati nell'ambiente di collaudo del Comune (rilanciando `collaudo.py`).
2. Checklist manuale completata senza anomalie bloccanti.
3. Configurazioni di produzione fatte (manuale di esercizio, sezione 3) e PEC di prova ricevuta.
