# Report di collaudo: Bando ISI: domanda di finanziamento

Eseguito il 26/11/2026 alle 23:27 su http://localhost:8080.

**21 scenari su 21 superati.** Copertura del diagramma: 112 passi su 117.

| Scenario | Esito | Durata | Pratica |
|---|---|---|---|
| Asse 2 in posizione utile e ammessa (TC-01) | ✅ superato | 7.4 s | `61f9a725-e9f0-11f1-b34b-02fc00000001` |
| Sotto soglia: si torna al punteggio, poi indietro e bozza annullata (TC-02) | ✅ superato | 3.0 s | `6667d6ec-e9f0-11f1-b34b-02fc00000001` |
| Impresa senza requisiti: si resta sui dati dell'impresa | ✅ superato | 2.1 s | `68321007-e9f0-11f1-b34b-02fc00000001` |
| Errori riportati al passo giusto: progetto e dichiarazioni | ✅ superato | 3.6 s | `696fe66f-e9f0-11f1-b34b-02fc00000001` |
| Modifiche dal riepilogo, poi bozza annullata | ✅ superato | 6.1 s | `6b9bc7a2-e9f0-11f1-b34b-02fc00000001` |
| Seconda domanda della stessa impresa (TC-19) e chiusura della compilazione | ✅ superato | 11.5 s | `6f408c78-e9f0-11f1-b34b-02fc00000001` |
| Fuori dal periodo di compilazione | ✅ superato | 0.8 s | `7634f1f4-e9f0-11f1-b34b-02fc00000001` |
| Asse 1.2 subentrata, decaduta, revoca accolta, integrazione e preavviso (TC-05) | ✅ superato | 21.4 s | `7693179d-e9f0-11f1-b34b-02fc00000001` |
| Asse 4 fuori dai fondi e senza subentro (TC-13) | ✅ superato | 9.3 s | `835478d3-e9f0-11f1-b34b-02fc00000001` |
| NCD, tecnico non ammesso, revoca respinta | ✅ superato | 9.0 s | `88df89ca-e9f0-11f1-b34b-02fc00000001` |
| Istanza di revoca non presentata | ✅ superato | 29.2 s | `8e3ad889-e9f0-11f1-b34b-02fc00000001` |
| Codice non acquisito nei termini | ✅ superato | 20.7 s | `9fa6fd64-e9f0-11f1-b34b-02fc00000001` |
| Codice sbagliato, poi sportello chiuso (TC-21) | ✅ superato | 19.5 s | `abf8d47d-e9f0-11f1-b34b-02fc00000001` |
| Elenchi mai pubblicati | ✅ superato | 20.5 s | `b79abc59-e9f0-11f1-b34b-02fc00000001` |
| Respinta, con termini scaduti e avvisi dell'istruttoria | ✅ superato | 50.3 s | `c3d36591-e9f0-11f1-b34b-02fc00000001` |
| Rinuncia durante l'istruttoria | ✅ superato | 11.7 s | `e1d12954-e9f0-11f1-b34b-02fc00000001` |
| Rinuncia prima dello sportello | ✅ superato | 8.2 s | `e8cbf75c-e9f0-11f1-b34b-02fc00000001` |
| Rinuncia durante il caricamento dei documenti | ✅ superato | 10.8 s | `edb745db-e9f0-11f1-b34b-02fc00000001` |
| Anagrafica INAIL non disponibile | ✅ superato | 1.6 s | `f44c4da2-e9f0-11f1-b34b-02fc00000001` |
| Servizio di calcolo non disponibile | ✅ superato | 1.4 s | `f53044af-e9f0-11f1-b34b-02fc00000001` |
| Registrazione non disponibile | ✅ superato | 2.3 s | `f607df92-e9f0-11f1-b34b-02fc00000001` |

## Asse 2 in posizione utile e ammessa (TC-01)

Percorso principale: dati letti da INAIL, un passo per attività, calcolo (201 punti, 70.200 €), riepilogo, registrazione, codice, sportello, posizione utile, documenti e perizia, istruttoria.

1. ✅ marco avvia la pratica "Linea vita sul capannone"
2. ✅ controllo: attivita "Controlla i dati dell'impresa"; dati {"ragioneSociale": "Carpenterie Rossi S.r.l.", "avvisoAnagrafica": "Dati letti dall'anagrafica INAIL"}
3. ✅ marco completa "Controlla i dati dell'impresa"
4. ✅ controllo: attivita "Scegli asse e progetto"; dati {"assiAmmessi": "*"}
5. ✅ marco completa "Scegli asse e progetto"
6. ✅ marco completa "Criteri di punteggio e piano economico"
7. ✅ marco completa "Firma le dichiarazioni"
8. ✅ controllo: attivita "Rivedi e registra la domanda"; dati {"punteggio": "201", "contributoTotaleTesto": "70.200,00 €", "registrabile": "Sì", "passoDaCorreggere": "nessuno"}
9. ✅ marco completa "Rivedi e registra la domanda"
10. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
11. ✅ scatta subito il timer "attesaCodice"
12. ✅ marco completa "Acquisisci il codice identificativo"
13. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
14. ✅ marco completa "Invia il codice allo sportello"
15. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
16. ✅ arriva il messaggio "esitoElenco"
17. ✅ controllo: attivita ["Carica i documenti", "Incarica il tecnico"]
18. ✅ marco completa "Incarica il tecnico"
19. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
20. ✅ elena completa "Compila e firma la perizia"
21. ✅ marco completa "Carica i documenti"
22. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"scadenzaIstruttoria": "*"}
23. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
24. ✅ controllo: conclusa true; fine "fineAmmessa"; eventi ["EMAIL", "CHIAMATA_API", "PARTE", "MESSAGGIO"]

## Sotto soglia: si torna al punteggio, poi indietro e bozza annullata (TC-02)

89 punti: il calcolo riporta l'impresa al passo del punteggio con il motivo; l'impresa torna all'asse e annulla la bozza.

1. ✅ marco avvia la pratica "Silenziatori per le presse"
2. ✅ controllo: attivita "Controlla i dati dell'impresa"; dati {"avvisoAnagrafica": "Impresa non trovata nell'anagrafica INAIL: compila i dati a mano"}
3. ✅ marco completa "Controlla i dati dell'impresa"
4. ✅ marco completa "Scegli asse e progetto"
5. ✅ marco completa "Criteri di punteggio e piano economico"
6. ✅ marco completa "Firma le dichiarazioni"
7. ✅ controllo: attivita "Criteri di punteggio e piano economico"; dati {"punteggio": "89", "registrabile": "No", "passoDaCorreggere": "punteggio", "erroriPunteggio": "*"}
8. ✅ marco completa "Criteri di punteggio e piano economico"
9. ✅ controllo: attivita "Scegli asse e progetto"
10. ✅ marco completa "Scegli asse e progetto"
11. ✅ marco completa "Criteri di punteggio e piano economico"
12. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["erroriGw", "passoPunteggioAnnullata"]

## Impresa senza requisiti: si resta sui dati dell'impresa

Meno di 1 ULA: dopo il primo calcolo la domanda torna ai dati dell'impresa con il motivo; corretti i dati si passa all'asse, si torna indietro e si annulla dal primo passo.

1. ✅ marco avvia la pratica "Prova con poche ULA"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ controllo: attivita "Controlla i dati dell'impresa"; dati {"passoDaCorreggere": "impresa", "erroriImpresa": "*"}
4. ✅ marco completa "Controlla i dati dell'impresa"
5. ✅ controllo: attivita "Scegli asse e progetto"
6. ✅ marco completa "Scegli asse e progetto"
7. ✅ controllo: attivita "Controlla i dati dell'impresa"
8. ✅ marco completa "Controlla i dati dell'impresa"
9. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["impresaGw", "passoProgettoGw", "passoImpresaAnnullata"]

## Errori riportati al passo giusto: progetto e dichiarazioni

Manca la tipologia e una dichiarazione: il calcolo riporta prima al progetto, poi alle dichiarazioni; dalle dichiarazioni si torna al punteggio e infine si annulla.

1. ✅ marco avvia la pratica "Domanda con errori"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ controllo: attivita "Scegli asse e progetto"; dati {"passoDaCorreggere": "progetto", "erroriProgetto": "*"}
7. ✅ marco completa "Scegli asse e progetto"
8. ✅ marco completa "Criteri di punteggio e piano economico"
9. ✅ marco completa "Firma le dichiarazioni"
10. ✅ controllo: attivita "Firma le dichiarazioni"; dati {"passoDaCorreggere": "dichiarazioni", "erroriDichiarazioni": "*"}
11. ✅ marco completa "Firma le dichiarazioni"
12. ✅ controllo: attivita "Criteri di punteggio e piano economico"
13. ✅ marco completa "Criteri di punteggio e piano economico"
14. ✅ marco completa "Firma le dichiarazioni"
15. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["erroriGw", "passoDichiarazioniAnnullata"]

## Modifiche dal riepilogo, poi bozza annullata

Dal riepilogo l'impresa torna all'asse, poi al punteggio e alle dichiarazioni, ogni volta ricalcolando; infine annulla dal riepilogo.

1. ✅ marco avvia la pratica "Domanda da rivedere"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: attivita "Scegli asse e progetto"
8. ✅ marco completa "Scegli asse e progetto"
9. ✅ marco completa "Criteri di punteggio e piano economico"
10. ✅ marco completa "Firma le dichiarazioni"
11. ✅ controllo: attivita "Rivedi e registra la domanda"; dati {"contributoTotaleTesto": "*"}
12. ✅ marco completa "Rivedi e registra la domanda"
13. ✅ marco completa "Criteri di punteggio e piano economico"
14. ✅ marco completa "Firma le dichiarazioni"
15. ✅ marco completa "Rivedi e registra la domanda"
16. ✅ marco completa "Firma le dichiarazioni"
17. ✅ marco completa "Rivedi e registra la domanda"
18. ✅ controllo: attivita "Controlla i dati dell'impresa"
19. ✅ marco completa "Controlla i dati dell'impresa"
20. ✅ marco completa "Scegli asse e progetto"
21. ✅ marco completa "Criteri di punteggio e piano economico"
22. ✅ marco completa "Firma le dichiarazioni"
23. ✅ marco completa "Rivedi e registra la domanda"
24. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["qualePassoGw", "riepilogoAnnullata"]

## Seconda domanda della stessa impresa (TC-19) e chiusura della compilazione

L'impresa del primo scenario ha già registrato: la seconda bozza non si registra e torna al riepilogo con il motivo. Alla chiusura della compilazione l'impresa è avvisata.

1. ✅ marco avvia la pratica "Seconda ipotesi di progetto"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: attivita "Rivedi e registra la domanda"; dati {"esitoRegistrazione": "GIA_REGISTRATA", "messaggioRegistrazione": "L'impresa ha già registrato una domanda per questo Avviso"}
8. ✅ scatta subito il timer "chiusuraCompilazioneTimer"
9. ✅ controllo: conclusa true; fine "fineNonRegistrata"; passa ["registrata", "avvisaChiusura"]; eventi ["NOTIFICA"]

## Fuori dal periodo di compilazione

Il calendario dice che la compilazione è chiusa: l'impresa riceve una notifica con le date e la pratica finisce con il motivo, senza attività da fare.

1. ✅ marco avvia la pratica "Domanda fuori periodo"
2. ✅ controllo: conclusa true; fine "fineFuoriPeriodo"; passa ["avvisaFuoriPeriodo"]; eventi ["NOTIFICA"]

## Asse 1.2 subentrata, decaduta, revoca accolta, integrazione e preavviso (TC-05)

Fuori dai fondi, subentra con la redistribuzione; senza perizia (Asse 1.2); i documenti non arrivano in tempo, l'istanza di revoca è accolta; in istruttoria integrazione e preavviso, poi ammessa parzialmente.

1. ✅ marco avvia la pratica "SGSL certificato"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ controllo: attivita []; passa ["attesaRedistribuzione"]
15. ✅ arriva il messaggio "esitoElenco"
16. ✅ controllo: attivita "Carica i documenti"; passa ["serveperizia"]
17. ✅ scatta subito il timer "termineDocumentiTimer"
18. ✅ marco completa "Presenta l'istanza di revoca"
19. ✅ collaudo-sede-toscana completa "Valuta l'istanza di revoca"
20. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
21. ✅ marco completa "Rispondi alla richiesta di integrazione"
22. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"giorniRimanenti": "*"}
23. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
24. ✅ marco completa "Presenta le osservazioni"
25. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
26. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"punteggio": "*"}
27. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
28. ✅ controllo: conclusa true; fine "fineAmmessaParz"; passa ["comunicaDecadenza", "riprendiIstruttoria", "comunicaPreavviso"]

## Asse 4 fuori dai fondi e senza subentro (TC-13)

Più tipologie (vale la c, 80 punti); fuori dai fondi negli elenchi provvisori e anche nei definitivi.

1. ✅ marco avvia la pratica "Aspirazione delle polveri di legno"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ arriva il messaggio "esitoElenco"
15. ✅ controllo: conclusa true; fine "fineFuoriFondi"

## NCD, tecnico non ammesso, revoca respinta

Elenco NCD prima dello sportello; il primo tecnico indicato è il legale rappresentante e viene rifiutato; i documenti non arrivano e l'istanza di revoca è respinta.

1. ✅ marco avvia la pratica "Ancoraggi per il magazzino"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ scatta subito il timer "attesaCodice"
8. ✅ arriva il messaggio "esitoElenco"
9. ✅ marco completa "Incarica il tecnico"
10. ✅ controllo: attivita ["Carica i documenti", "Incarica il tecnico"]; dati {"incaricoAmmesso": "No"}
11. ✅ scatta subito il timer "termineDocumentiTimer"
12. ✅ marco completa "Presenta l'istanza di revoca"
13. ✅ collaudo-sede-toscana completa "Valuta l'istanza di revoca"
14. ✅ controllo: conclusa true; fine "fineDecaduta"; eventi ["EMAIL"]

## Istanza di revoca non presentata

1. ✅ marco avvia la pratica "Linea vita bis"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ scatta subito il timer "termineDocumentiTimer"
15. ✅ scatta subito il timer "termineRevoca"
16. ✅ controllo: conclusa true; fine "fineDecaduta"

## Codice non acquisito nei termini

1. ✅ marco avvia la pratica "Ancoraggi tetto"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ scatta subito il timer "chiusuraCodiceTimer"
10. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineSenzaCodice"]

## Codice sbagliato, poi sportello chiuso (TC-21)

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"esitoInvio": "RIFIUTATO", "messaggioInvio": "Codice non valido o già utilizzato"}
13. ✅ scatta subito il timer "chiusuraSportelloTimer"
14. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineNonInviata"]

## Elenchi mai pubblicati

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ scatta subito il timer "attesaElenchi"
14. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineSenzaElenchi"]

## Respinta, con termini scaduti e avvisi dell'istruttoria

Integrazione senza risposta in 30 giorni, osservazioni non presentate in 10 giorni, avviso a 100 giorni e termine di 120 giorni segnalati, poi rigetto.

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ marco completa "Incarica il tecnico"
15. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
16. ✅ elena completa "Compila e firma la perizia"
17. ✅ marco completa "Carica i documenti"
18. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
19. ✅ scatta subito il timer "termineIntegrazione"
20. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
21. ✅ scatta subito il timer "termineOsservazioni"
22. ✅ scatta subito il timer "avviso100"
23. ✅ scatta subito il timer "termine120"
24. ✅ controllo: attivita "Svolgi l'istruttoria"; eventi ["NOTIFICA", "SCADENZA"]
25. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
26. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"registrabile": "No"}
27. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
28. ✅ controllo: conclusa true; fine "fineRespinta"

## Rinuncia durante l'istruttoria

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ marco completa "Incarica il tecnico"
15. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
16. ✅ elena completa "Compila e firma la perizia"
17. ✅ marco completa "Carica i documenti"
18. ✅ arriva il messaggio "rinunciaImpresa"
19. ✅ controllo: conclusa true; fine "fineRinuncia"

## Rinuncia prima dello sportello

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ arriva il messaggio "rinunciaImpresa"
12. ✅ controllo: conclusa true; fine "fineRinuncia"

## Rinuncia durante il caricamento dei documenti

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
8. ✅ scatta subito il timer "attesaCodice"
9. ✅ marco completa "Acquisisci il codice identificativo"
10. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
11. ✅ marco completa "Invia il codice allo sportello"
12. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
13. ✅ arriva il messaggio "esitoElenco"
14. ✅ arriva il messaggio "rinunciaImpresa"
15. ✅ controllo: conclusa true; fine "fineRinuncia"

## Anagrafica INAIL non disponibile

1. ✅ marco avvia la pratica "Prova con INAIL spento"
2. ✅ controllo: attivita "Controlla i dati dell'impresa"; passa ["leggiAnagraficaErrore"]; eventi ["ERRORE"]
3. ✅ marco completa "Controlla i dati dell'impresa"
4. ✅ marco completa "Scegli asse e progetto"
5. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["passoProgettoAnnullata"]

## Servizio di calcolo non disponibile

1. ✅ marco avvia la pratica "Prova con il calcolo spento"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ controllo: attivita "Controlla i dati dell'impresa"; passa ["calcolaAssiErrore"]
4. ✅ marco completa "Controlla i dati dell'impresa"
5. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["passoImpresaAnnullata"]

## Registrazione non disponibile

Il servizio non risponde alla registrazione: la domanda torna al riepilogo, nulla è perso.

1. ✅ marco avvia la pratica "Prova con la registrazione spenta"
2. ✅ marco completa "Controlla i dati dell'impresa"
3. ✅ marco completa "Scegli asse e progetto"
4. ✅ marco completa "Criteri di punteggio e piano economico"
5. ✅ marco completa "Firma le dichiarazioni"
6. ✅ marco completa "Rivedi e registra la domanda"
7. ✅ controllo: attivita "Rivedi e registra la domanda"
8. ✅ marco completa "Rivedi e registra la domanda"
9. ✅ controllo: conclusa true; fine "fineAnnullata"; passa ["registraErrore"]

## Copertura

Passi che nessuno scenario ha percorso (da provare a mano o con uno scenario in più):
- Servizio ISI non disponibile (`calcolaErrore`)
- Servizio ISI non disponibile (`generaCodiceErrore`)
- Servizio ISI non disponibile (`registraInvioErrore`)
- Servizio ISI non disponibile (`verificaIncaricoErrore`)
- Servizio ISI non disponibile (`ricalcolaErrore`)
