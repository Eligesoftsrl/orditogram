---
name: orditogram-soluzione
description: Costruisce una soluzione Orditogram completa dalle specifiche di un cliente (documenti, BPMN degli analisti, note) - verifica le specifiche, genera BPMN, form, anagrafiche, permessi e pacchetto .orditogram.zip, la collauda su un Orditogram avviato e scrive la documentazione per il cliente. Usala per creare, verificare o correggere processi, form, anagrafiche o pacchetti Orditogram.
---

# Costruire una soluzione Orditogram

Orditogram trasforma un processo in software: BPMN eseguito da Activiti, form JSON, anagrafiche, permessi, pacchetto
importabile dallo Studio. Questa skill porta dalle specifiche del cliente a una soluzione **pronta da collaudare**:
all'analista resta solo da verificare che funzioni, con la checklist che gli prepari.

Lingua: italiano, in tutto (nomi, etichette, testi, documenti). Testi per persone non tecniche.

## Prima di iniziare

- Lavora nel repository di Orditogram (`Eligesoftsrl/orditogram`): gli script stanno in
  `.claude/skills/orditogram-soluzione/script/`, i riferimenti in `riferimenti/`. Se non ce l'hai, clonalo: senza
  il codice non puoi verificare i formati né collaudare. Leggi `CLAUDE.md` del repo.
- **Il codice vince**. I riferimenti descrivono il formato alla data in cui sono stati scritti. Se uno script o un
  riferimento contraddice il backend (`process/ProcessValidator`, `form/FormService`, `anagrafica/DefinizioneAnagrafica`,
  `bozza/BozzaController`, i connettori), segui il codice e segnala che la skill va aggiornata.
- Cartella della soluzione: dove la chiede l'utente; altrimenti `soluzioni/<chiave>/` fuori dal codice del prodotto.

```
<soluzione>/
  specifiche/                 materiale ricevuto, intatto
  analisi.md                  fase 1: mappa, lacune, scelte, domande
  processo.bpmn20.xml
  form/<formKey>.json
  anagrafiche/<codice>.json   se servono
  permessi.json               chi avvia, vede, esporta (viaggia nel pacchetto)
  permessi-anagrafiche.json   chi vede, modifica, sceglie le schede (si applica con installa.py)
  servizio-<nome>/            solo se serve codice del cliente: Spring Boot (riferimenti/servizio-soluzione.md)
  collaudo/scenari.json  collaudo/checklist.md  collaudo/report.md
  documentazione/             fase 7
  <chiave>.orditogram.zip     il pacchetto
```

Leggi il riferimento giusto prima di scrivere ogni artefatto: `riferimenti/bpmn.md`, `riferimenti/form.md`,
`riferimenti/anagrafiche-e-accessi.md`, `riferimenti/collaudo.md`, `riferimenti/documentazione.md`,
`riferimenti/interfaccia.md` (nomi delle parti dell'interfaccia e regole per le schermate dei manuali),
`riferimenti/servizio-soluzione.md` (codice del cliente, solo Java e Spring Boot).
Un esempio completo e collaudato è in `esempio/` (specifica di partenza compresa): imitane la forma.

## Fase 1. Capire e verificare le specifiche

Leggi tutto il materiale. Scrivi `analisi.md` con:

1. **Mappa**: attori → gruppi (nomi in minuscolo, es. `istruttori`); passi in ordine con chi li fa; decisioni con i
   criteri; dati per passo (→ form); termini con il calendario (`proroga` per i termini di legge, `lavorativi` se
   la specifica parla di giorni lavorativi); comunicazioni (email, notifiche: a chi, quando, testo); integrazioni
   (servizi chiamati, messaggi in arrivo); soggetti ricorrenti (→ anagrafiche); esterni e parti; chi vede cosa.
2. **Verifica rispetto a Orditogram**: per ogni elemento, come si realizza. Elementi BPMN non supportati, connettori
   che non esistono, regole che il motore non sa applicare: proponi la sostituzione (vedi `riferimenti/bpmn.md`) o,
   se non c'è, segnalala come **richiesta di sviluppo** per Orditogram. Non aggirare i limiti con trucchi.
3. **Lacune e contraddizioni** della specifica: passi senza responsabile, decisioni senza criterio, termini senza
   conseguenza ("entro 30 giorni" e poi?), dati usati ma mai raccolti, strade che non finiscono, email senza
   destinatario.
4. **Scelte fatte**: per ogni lacuna non bloccante, la scelta più ragionevole per quel dominio (es. termini di un
   procedimento amministrativo prorogati se festivi; motivazione obbligatoria se l'esito è negativo) e perché.
5. **Domande**: solo quelle che cambierebbero il processo e che nessuna scelta ragionevole copre. Se ci sono, falle
   all'utente tutte insieme, poi prosegui con le scelte provvisorie dichiarate.

Se arriva un BPMN dell'analista: tienine id, nomi e disegno; correggi solo ciò che serve e annota ogni modifica.

## Fase 2. Generare gli artefatti

- **BPMN** (`riferimenti/bpmn.md`): chiave del processo stabile e leggibile, ogni elemento con un nome italiano,
  `documentation` sul processo, decisioni con la freccia "altrimenti", ogni API con la strada di errore,
  scadenze sulle fasi (sottoprocessi), messaggi dichiarati. Niente segreti nel BPMN.
- **Form** (`riferimenti/form.md`): uno per l'avvio e uno per ogni attività; prefisso comune dei formKey
  (`ce-domanda`, `ce-verifica`); stesso `name` per lo stesso dato in tutti i form; dati di riepilogo in `readonly`;
  ogni opzione dei `select` scritta esattamente come nelle condizioni.
- **Anagrafiche e permessi** (`riferimenti/anagrafiche-e-accessi.md`): solo se la specifica ha soggetti ricorrenti
  o regole di visibilità. Ricordati SCEGLIE per chi compila i form con campi anagrafica.
- **Codice del cliente** (`riferimenti/servizio-soluzione.md`): solo quando la configurazione non basta (calcoli,
  registri, regole del dominio), come servizio chiamato dai connettori `apiConnector`. **Sempre Java 21 e Spring
  Boot alla stessa versione di Orditogram, Maven Wrapper, JUnit 5, Flyway**: è quello che il cliente sa installare
  e mantenere. Mai Python, Node, Go o altri runtime nel codice consegnato, nemmeno per un prototipo (gli script
  della skill sono strumenti dell'analista e non si consegnano). Le regole che cambiano ogni anno stanno in file
  JSON fuori dal codice.

## Fase 3. Verifica statica (ripeti finché non ci sono errori)

```bash
S=.claude/skills/orditogram-soluzione/script
python3 $S/verifica.py <soluzione>            # stesse regole e messaggi della pubblicazione
python3 $S/disposizione.py <soluzione>/processo.bpmn20.xml        # disegno, se manca
node $S/anteprima.mjs <soluzione>/processo.bpmn20.xml <soluzione>/documentazione/diagramma.png
```
Zero errori. Ogni avviso o si corregge o si motiva in `analisi.md`. Guarda l'immagine del diagramma: frecce che
si incrociano male o etichette sovrapposte si sistemano (o si dice all'analista di ritoccare nello Studio).

## Fase 4. Collaudo automatico

Avvia un Orditogram di prova (vedi `riferimenti/collaudo.md`), scrivi `collaudo/scenari.json` con uno scenario per
ogni strada (ogni fine diversa, ogni scadenza, sollecito, messaggio, errore) e dati di prova realistici, poi:

```bash
python3 $S/collaudo.py <soluzione> --pubblica --db "postgresql://orditogram:orditogram@localhost/orditogram"
```
Itera su processo, form o scenari finché tutti gli scenari passano e il report non elenca passi mai percorsi
(se uno resta scoperto, spiega perché e mettilo nella checklist manuale). Non dichiarare collaudato ciò che non
hai visto passare.

Il collaudo passa dalle API, che a volte permettono più dell'interfaccia: per ogni passo chiediti se una persona lo
può fare davvero dalle schermate (es. **Registra arrivo** non chiede dati né allegati: le integrazioni ricevute via
PEC vanno in un'attività con un form). Nel dubbio leggi il componente in `frontend/src` o avvia il frontend
(`cd frontend && npm ci && npm run dev`) e guarda le schermate con Playwright.

## Fase 5. Documentazione per il cliente

Segui `riferimenti/documentazione.md`: `documenta.py` genera la scheda tecnica dagli artefatti; scrivi tu
documento funzionale, manuale utente (per gruppo), manuale di esercizio, piano e report di test, integrazioni (se ci
sono), sempre partendo da scheda tecnica, report e analisi. Mai descrivere funzioni o configurazioni che non ci sono.
Per nominare le parti dello schermo e preparare le schermate segui `riferimenti/interfaccia.md`.

## Fase 6. Consegna

```bash
python3 $S/pacchetto.py <soluzione>           # crea <chiave>.orditogram.zip solo se la verifica è pulita
python3 $S/installa.py <soluzione> --url <orditogram del cliente> --admin <utente:password>   # se richiesto
```
Scrivi `collaudo/checklist.md` per l'analista (formato in `riferimenti/collaudo.md`): una riga per scenario e per
schermata, dati di prova pronti, cosa non è stato provato in automatico, cosa serve dal cliente prima della
produzione. Poi riassumi all'utente: cosa hai costruito, esito del collaudo (scenari, copertura), scelte fatte e
domande aperte, richieste di sviluppo per Orditogram, dove sono pacchetto, checklist e documentazione.

## Errori frequenti

- Opzione di un `select` scritta diversamente nella condizione (`'Accolta'` vs `'Accolto'`): la strada non si prende mai.
- Numeri confrontati come testo: usa `${importo > 5000.0}`.
- Sollecito ripetuto senza `cancelActivity="false"`, o con un calendario (non ammesso).
- `apiConnector` senza errore sul bordo: la pratica si ferma al primo disservizio.
- Attività senza `candidateGroups`, o gruppo scritto diversamente da quello degli utenti.
- Campo `anagrafica` senza permesso SCEGLIE per chi compila: il campo resta vuoto per l'operatore.
- Frecce che attraversano il bordo di un sottoprocesso.
- Gateway parallelo di riunione con più frecce in ingresso dei rami aperti: aspetta un arrivo per **ogni** freccia.
  Se un ramo ha due strade (es. "serve la perizia?" sì/no), riuniscile prima con una decisione di raccordo.
- Caselle nei dati degli scenari scritte `"true"`: il backend vuole `true` (booleano).
- Date dei form nei testi dei connettori (`${data}`): escono nel formato di Java (`Mon Mar 01 00:00:00 UTC 2010`),
  non `AAAA-MM-GG`. Un servizio che le riceve deve saperle leggere (richiesta di sviluppo aperta).
- BPMN senza disegno: lo Studio mostra una tela vuota.

## Tenere aggiornata questa skill

Ogni funzione nuova di Orditogram che tocca BPMN, form, anagrafiche, permessi, pacchetti, connettori o API va
riportata qui nella stessa PR: riferimento, regola in `verifica.py` (stesso messaggio del backend), catalogo dei
connettori, `documenta.py` se va documentata, `esempio/` se cambia il formato. Lo prevede `CLAUDE.md`.
