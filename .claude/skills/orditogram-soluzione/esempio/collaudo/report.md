# Report di collaudo: Contributi per eventi culturali

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

## Contributo concesso e liquidato (sotto i 5.000 euro)

Strada principale: protocollo automatico, verifica ammissibile, decisione senza parere, liquidazione.

1. ✅ collaudo-cultura avvia la pratica "Concerto d'estate in piazza"
2. ✅ controllo: attivita "Verifica la domanda"; dati {"numeroProtocollo": "*", "associazione": "Banda musicale di Valfiorita"}; eventi ["CHIAMATA_API", "EMAIL"]
3. ✅ collaudo-cultura completa "Verifica la domanda"
4. ✅ controllo: attivita "Decidi il contributo"
5. ✅ collaudo-cultura completa "Decidi il contributo"
6. ✅ collaudo-ragioneria completa "Liquida il contributo"
7. ✅ controllo: conclusa true; fine "fineLiquidato"; passa ["emailConcesso"]; visibileA ["gruppo:ragioneria", "gruppo:dirigenti"]

## Protocollo non disponibile: si protocolla a mano

Strada di errore: si pubblica per lo scenario una variante con il servizio di prova che risponde con un errore.

1. ✅ collaudo-cultura avvia la pratica "Mostra fotografica"
2. ✅ controllo: attivita "Protocolla a mano"; eventi ["ERRORE"]
3. ✅ collaudo-cultura completa "Protocolla a mano"
4. ✅ controllo: attivita "Verifica la domanda"; passa ["conferma"]; dati {"numeroProtocollo": "2026/009999"}

## Sopra i 5.000 euro: parere contrario e contributo negato

1. ✅ collaudo-cultura avvia la pratica "Rassegna teatrale d'autunno"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: attivita "Dai il parere sul contributo"
4. ✅ collaudo-dirigenti completa "Dai il parere sul contributo"
5. ✅ collaudo-cultura completa "Decidi il contributo"
6. ✅ controllo: conclusa true; fine "fineNegato"; passa ["parere", "emailNegato"]; dati {"parere": "Contrario"}; nonVisibileA ["gruppo:ragioneria"]

## Domanda non ammissibile

1. ✅ collaudo-cultura avvia la pratica "Cena sociale"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: conclusa true; fine "fineNonAmmissibile"; passa ["emailNonAmmissibile"]

## Integrazioni ricevute: l'istruttoria riprende

1. ✅ collaudo-cultura avvia la pratica "Laboratorio di teatro per ragazzi"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ controllo: attivita "Registra le integrazioni"; passa ["emailIntegrazioni"]
4. ✅ collaudo-cultura completa "Registra le integrazioni"
5. ✅ controllo: attivita "Verifica la domanda"; dati {"noteIntegrazioni": "Bilancio firmato arrivato via PEC il 20/11."}
6. ✅ collaudo-cultura completa "Verifica la domanda"
7. ✅ controllo: attivita "Decidi il contributo"

## Nessuna integrazione in 15 giorni: archiviata

1. ✅ collaudo-cultura avvia la pratica "Festa della musica"
2. ✅ collaudo-cultura completa "Verifica la domanda"
3. ✅ scatta subito il timer "nessunaIntegrazione"
4. ✅ controllo: conclusa true; fine "fineArchiviata"

## Sollecito all'istruttore dopo 10 giorni

1. ✅ collaudo-cultura avvia la pratica "Concerto di Natale"
2. ✅ scatta subito il timer "sollecitoVerifica"
3. ✅ controllo: attivita "Verifica la domanda"; passa ["notificaSollecito"]; eventi ["NOTIFICA"]

## Termine di 60 giorni scaduto

1. ✅ collaudo-cultura avvia la pratica "Teatro in cortile"
2. ✅ scatta subito il timer "termine60"
3. ✅ controllo: conclusa true; fine "fineTermine"; passa ["avvisoTermine"]; eventi ["SCADENZA", "NOTIFICA"]

## Rinuncia dell'associazione durante l'istruttoria

1. ✅ collaudo-cultura avvia la pratica "Reading di poesia"
2. ✅ arriva il messaggio "rinunciaAssociazione"
3. ✅ controllo: conclusa true; fine "fineRinuncia"

## Copertura

Tutti i passi del diagramma sono stati percorsi almeno una volta.
