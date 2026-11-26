# Report di collaudo: Bando ISI: elenchi cronologici

Eseguito il 26/11/2026 alle 17:11 su http://localhost:8080.

**4 scenari su 4 superati.** Copertura del diagramma: 10 passi su 10.

| Scenario | Esito | Durata | Pratica |
|---|---|---|---|
| Elenco provvisorio pubblicato | ✅ superato | 0.6 s | `619c699f-e9bc-11f1-9114-02fc00000001` |
| Elenco NCD non pubblicato | ✅ superato | 0.5 s | `61f9a4f1-e9bc-11f1-9114-02fc00000001` |
| Servizio ISI non disponibile | ✅ superato | 0.5 s | `62595142-e9bc-11f1-9114-02fc00000001` |
| Pubblicazione non riuscita | ✅ superato | 0.8 s | `62a65fe8-e9bc-11f1-9114-02fc00000001` |

## Elenco provvisorio pubblicato

Anteprima calcolata sulle domande inviate allo sportello nel collaudo di isiDomanda, poi pubblicata.

1. ✅ collaudo-direzione-isi avvia la pratica "PROVVISORIO"
2. ✅ controllo: attivita "Controlla l'anteprima e pubblica"; dati {"righeElenco": "*", "riepilogoElenco": "*"}
3. ✅ collaudo-direzione-isi completa "Controlla l'anteprima e pubblica"
4. ✅ controllo: conclusa true; fine "finePubblicato"; dati {"termineDocumenti": "*"}

## Elenco NCD non pubblicato

1. ✅ collaudo-direzione-isi avvia la pratica "NCD"
2. ✅ collaudo-direzione-isi completa "Controlla l'anteprima e pubblica"
3. ✅ controllo: conclusa true; fine "fineNonPubblicato"

## Servizio ISI non disponibile

1. ✅ collaudo-direzione-isi avvia la pratica "DEFINITIVO"
2. ✅ controllo: conclusa true; fine "fineErrore"; eventi ["ERRORE", "NOTIFICA"]

## Pubblicazione non riuscita

1. ✅ collaudo-direzione-isi avvia la pratica "DEFINITIVO"
2. ✅ collaudo-direzione-isi completa "Controlla l'anteprima e pubblica"
3. ✅ controllo: conclusa true; fine "fineErrore"; passa ["pubblicaErrore"]

## Copertura

Tutti i passi del diagramma sono stati percorsi almeno una volta.
