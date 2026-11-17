# Interfaccia di Orditogram: come descriverla e mostrarla

Orditogram usa il linguaggio di Designers Italia (AgID). Nei manuali e nelle schermate per il cliente usa questi
nomi e queste regole, così il documento corrisponde a quello che l'utente vede. Le regole per chi sviluppa sono in
`CLAUDE.md`, sezione *Interfaccia*.

## Nomi delle parti

| Parte | Come chiamarla nel manuale |
|---|---|
| Fascia blu in alto con il marchio e la campanella | intestazione |
| Menu chiaro a sinistra (Scrivania, Pratiche, Anagrafiche, Studio) | menu laterale |
| Riquadri con un numero in cima alla scrivania | tessere della scrivania (Tocca a te, Del tuo gruppo, Scadute, In scadenza) |
| Pannello che scorre da destra cliccando una pratica | pannello laterale (*Apri la pratica* porta alla pagina completa) |
| Finestra al centro che chiede conferma | finestra di conferma |
| Etichette colorate a capsula | stati (verde = concluso o valido, blu = in corso, arancione = in scadenza, rosso = scaduto o errore) |
| Binario con i passi della pratica | percorso (il passo attuale è il cerchio blu che pulsa) |

## Schermate per la documentazione

- Larghezza 1360px per il computer, 400px per il telefono; tema chiaro.
- Mostra dati di prova realistici (quelli del collaudo), mai dati veri di persone.
- Aspetta circa un secondo dopo l'apertura della pagina: tessere ed elenchi entrano in sequenza.
- Per spiegare una pratica, mostra il pannello laterale aperto dall'elenco *Pratiche*: riassume stato, chi ci
  lavora e percorso in una sola immagine.

## Accessibilità da dichiarare

L'interfaccia segue WCAG 2.1 AA: contrasti verificati, uso completo da tastiera con focus giallo ben visibile,
link "Vai al contenuto", animazioni ferme quando il sistema chiede di ridurre il movimento. Nei documenti per
gare o dichiarazioni di accessibilità riporta solo questo; le verifiche con lettore di schermo vanno fatte e
documentate a parte.
