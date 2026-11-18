# Interfaccia di Orditogram: come descriverla e mostrarla

Orditogram usa il linguaggio di Designers Italia (AgID). Nei manuali e nelle schermate per il cliente usa questi
nomi e queste regole, così il documento corrisponde a quello che l'utente vede. Le regole per chi sviluppa sono in
`CLAUDE.md`, sezione *Interfaccia*.

## Nomi delle parti

| Parte | Come chiamarla nel manuale |
|---|---|
| Barra in alto con la campanella (blu sul telefono) | intestazione |
| Menu scuro a sinistra (Scrivania, Pratiche, Anagrafiche, Studio), che si stringe a sole icone | menu laterale (*Stringi il menu* / *Allarga il menu*) |
| Riquadri colorati con un numero in cima alla scrivania | tessere della scrivania (Tocca a te, Del tuo gruppo, In scadenza, Scadute, Pratiche in corso) |
| Riquadri con i grafici sotto le tessere | *Come vanno le pratiche* (ogni grafico ha *Tabella* con gli stessi valori) |
| Diagramma del processo nella pratica, con il passo attuale che pulsa e il segnalino giallo | il processo |
| Pulsante con i tre puntini in cima alla pratica | menu *Altre azioni* |
| Pannello che scorre da destra cliccando una pratica | pannello laterale (*Apri la pratica* porta alla pagina completa) |
| Finestra al centro che chiede conferma | finestra di conferma |
| Etichette colorate a capsula | stati (verde = concluso o valido, blu = in corso, arancione = in scadenza, rosso = scaduto o errore) |
| Binario con i passi della pratica | percorso (il passo attuale è il cerchio blu che pulsa) |
| Riquadro in cima alla pratica con percorso, *Adesso* e tempi | specchietto del flusso |
| Tessere grandi per scegliere l'esito di un'attività | scelta dell'esito |
| Pannello laterale aperto da *Vedi* su un documento | anteprima del documento |

## Regole che valgono per ogni soluzione

Lo stile di Orditogram vale per tutto ciò che si consegna (scelta di Filippo, novembre 2026): tessere di tinte
diverse, pannelli che si aprono da destra, movimento fluido, il flusso BPMN sempre visibile con il passo attuale
che pulsa, grafici degli indicatori con la loro tabella. Quando la soluzione ha numeri importanti per il cliente
(richieste, scadenze, avanzamento), proponili come indicatori nella documentazione funzionale: la scrivania li mostra
già per processo e per passo, quindi nomina bene i passi del BPMN, perché diventano le etichette dei grafici.

## Scelta dell'esito: come scrivere le opzioni

Un campo `select` con al massimo 4 opzioni, di cui almeno due riconosciute come esiti, si mostra a tessere
con icona e colore (regole in `frontend/src/lib/esiti.js`). Scrivi opzioni brevi che inizino dalla parola chiave:
*Accolta*, *Accolta in parte*, *Serve integrazione*, *Respinta*. Parole riconosciute: accolta/accoglibile,
approvata, concessa, sì (verde); integrazione, sospesa, chiarimenti, in parte (arancione); respinta, rigetto,
diniego, inammissibile, no (rosso). Le altre restano blu.

## Schermate per la documentazione

- Larghezza 1360px per il computer, 400px per il telefono; tema chiaro.
- Mostra dati di prova realistici (quelli del collaudo), mai dati veri di persone.
- Aspetta circa due secondi dopo l'apertura della pagina: tessere, numeri, barre dei grafici e passi del diagramma
  entrano in sequenza.
- Per spiegare una pratica, mostra il pannello laterale aperto dall'elenco *Pratiche*: riassume stato, chi ci
  lavora e percorso in una sola immagine.

## Accessibilità da dichiarare

L'interfaccia segue WCAG 2.1 AA: contrasti verificati, uso completo da tastiera con focus giallo ben visibile,
link "Vai al contenuto", animazioni ferme quando il sistema chiede di ridurre il movimento. Nei documenti per
gare o dichiarazioni di accessibilità riporta solo questo; le verifiche con lettore di schermo vanno fatte e
documentate a parte.
