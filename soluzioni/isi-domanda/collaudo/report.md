# Report di collaudo: Bando ISI: domanda di finanziamento

Eseguito il 26/11/2026 alle 16:25 su http://localhost:8080.

**16 scenari su 16 superati.** Copertura del diagramma: 92 passi su 97.

| Scenario | Esito | Durata | Pratica |
|---|---|---|---|
| Asse 2 in posizione utile e ammessa (TC-01) | ✅ superato | 12.8 s | `6ff8e176-e9b5-11f1-a473-02fc00000001` |
| Sotto soglia, poi bozza annullata (TC-02) | ✅ superato | 1.0 s | `77a2b42d-e9b5-11f1-a473-02fc00000001` |
| Seconda domanda della stessa impresa (TC-19) e chiusura della compilazione | ✅ superato | 7.2 s | `783270f5-e9b5-11f1-a473-02fc00000001` |
| Asse 1.2 subentrata, decaduta, revoca accolta, integrazione e preavviso (TC-05) | ✅ superato | 22.1 s | `7c80bc71-e9b5-11f1-a473-02fc00000001` |
| Asse 4 fuori dai fondi e senza subentro (TC-13) | ✅ superato | 9.0 s | `89a94471-e9b5-11f1-a473-02fc00000001` |
| NCD, tecnico non ammesso, revoca respinta | ✅ superato | 9.2 s | `8f08d958-e9b5-11f1-a473-02fc00000001` |
| Istanza di revoca non presentata | ✅ superato | 29.7 s | `947da331-e9b5-11f1-a473-02fc00000001` |
| Codice non acquisito nei termini | ✅ superato | 19.7 s | `a62ceb36-e9b5-11f1-a473-02fc00000001` |
| Codice sbagliato, poi sportello chiuso (TC-21) | ✅ superato | 19.9 s | `b1eae685-e9b5-11f1-a473-02fc00000001` |
| Elenchi mai pubblicati | ✅ superato | 20.2 s | `bdcd3279-e9b5-11f1-a473-02fc00000001` |
| Respinta, con termini scaduti e avvisi dell'istruttoria | ✅ superato | 50.7 s | `c9d49340-e9b5-11f1-a473-02fc00000001` |
| Rinuncia durante l'istruttoria | ✅ superato | 11.6 s | `e80bb5fc-e9b5-11f1-a473-02fc00000001` |
| Rinuncia prima dello sportello | ✅ superato | 8.4 s | `eefa4fa0-e9b5-11f1-a473-02fc00000001` |
| Rinuncia durante il caricamento dei documenti | ✅ superato | 10.7 s | `f40003ae-e9b5-11f1-a473-02fc00000001` |
| Anagrafica INAIL non disponibile | ✅ superato | 0.9 s | `fa6bb136-e9b5-11f1-a473-02fc00000001` |
| Servizio di calcolo non disponibile | ✅ superato | 1.1 s | `faee9d14-e9b5-11f1-a473-02fc00000001` |

## Asse 2 in posizione utile e ammessa (TC-01)

Percorso principale: dati letti da INAIL, calcolo (201 punti, 70.200 €), registrazione, codice, sportello, posizione utile, documenti e perizia del tecnico, istruttoria, ammissione.

1. ✅ marco avvia la pratica "Linea vita sul capannone"
2. ✅ controllo: attivita "Compila la domanda"; dati {"ragioneSociale": "Carpenterie Rossi S.r.l.", "avvisoAnagrafica": "Dati letti dall'anagrafica INAIL"}
3. ✅ marco completa "Compila la domanda"
4. ✅ controllo: attivita "Compila la domanda"; dati {"punteggio": "201", "contributoTotaleTesto": "70.200,00 €", "registrabile": "Sì"}
5. ✅ marco completa "Compila la domanda"
6. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
7. ✅ scatta subito il timer "attesaCodice"
8. ✅ marco completa "Acquisisci il codice identificativo"
9. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
10. ✅ marco completa "Invia il codice allo sportello"
11. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
12. ✅ arriva il messaggio "esitoElenco"
13. ✅ controllo: attivita ["Carica i documenti", "Incarica il tecnico"]
14. ✅ marco completa "Incarica il tecnico"
15. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
16. ✅ elena completa "Compila e firma la perizia"
17. ✅ marco completa "Carica i documenti"
18. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"scadenzaIstruttoria": "*"}
19. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
20. ✅ controllo: conclusa true; fine "fineAmmessa"; eventi ["EMAIL", "CHIAMATA_API", "PARTE", "MESSAGGIO"]

## Sotto soglia, poi bozza annullata (TC-02)

89 punti: REGISTRA non procede e la domanda torna in compilazione; l'impresa annulla la bozza.

1. ✅ marco avvia la pratica "Silenziatori per le presse"
2. ✅ controllo: dati {"avvisoAnagrafica": "Impresa non trovata nell'anagrafica INAIL: compila i dati a mano"}
3. ✅ marco completa "Compila la domanda"
4. ✅ controllo: attivita "Compila la domanda"; dati {"punteggio": "89", "registrabile": "No"}
5. ✅ marco completa "Compila la domanda"
6. ✅ controllo: conclusa true; fine "fineAnnullata"

## Seconda domanda della stessa impresa (TC-19) e chiusura della compilazione

L'impresa del primo scenario ha già registrato: la seconda bozza non si registra. Alla chiusura della compilazione la bozza resta non registrata.

1. ✅ marco avvia la pratica "Seconda ipotesi di progetto"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: attivita "Compila la domanda"; dati {"esitoRegistrazione": "GIA_REGISTRATA", "messaggioRegistrazione": "L'impresa ha già registrato una domanda per questo Avviso"}
4. ✅ scatta subito il timer "chiusuraCompilazioneTimer"
5. ✅ controllo: conclusa true; fine "fineNonRegistrata"

## Asse 1.2 subentrata, decaduta, revoca accolta, integrazione e preavviso (TC-05)

Fuori dai fondi, subentra con la redistribuzione; senza perizia (Asse 1.2); i documenti non arrivano in tempo, l'istanza di revoca è accolta; in istruttoria integrazione e preavviso, poi ammessa parzialmente.

1. ✅ marco avvia la pratica "SGSL certificato"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ controllo: attivita []; passa ["attesaRedistribuzione"]
11. ✅ arriva il messaggio "esitoElenco"
12. ✅ controllo: attivita "Carica i documenti"; passa ["serveperizia"]
13. ✅ scatta subito il timer "termineDocumentiTimer"
14. ✅ marco completa "Presenta l'istanza di revoca"
15. ✅ collaudo-sede-toscana completa "Valuta l'istanza di revoca"
16. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
17. ✅ marco completa "Rispondi alla richiesta di integrazione"
18. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"giorniRimanenti": "*"}
19. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
20. ✅ marco completa "Presenta le osservazioni"
21. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
22. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"punteggio": "*"}
23. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
24. ✅ controllo: conclusa true; fine "fineAmmessaParz"; passa ["comunicaDecadenza", "riprendiIstruttoria", "comunicaPreavviso"]

## Asse 4 fuori dai fondi e senza subentro (TC-13)

Più tipologie (vale la c, 80 punti); fuori dai fondi negli elenchi provvisori e anche nei definitivi.

1. ✅ marco avvia la pratica "Aspirazione delle polveri di legno"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ arriva il messaggio "esitoElenco"
11. ✅ controllo: conclusa true; fine "fineFuoriFondi"

## NCD, tecnico non ammesso, revoca respinta

Elenco NCD prima dello sportello; il primo tecnico indicato è il legale rappresentante e viene rifiutato; i documenti non arrivano e l'istanza di revoca è respinta.

1. ✅ marco avvia la pratica "Ancoraggi per il magazzino"
2. ✅ marco completa "Compila la domanda"
3. ✅ scatta subito il timer "attesaCodice"
4. ✅ arriva il messaggio "esitoElenco"
5. ✅ marco completa "Incarica il tecnico"
6. ✅ controllo: attivita ["Carica i documenti", "Incarica il tecnico"]; dati {"incaricoAmmesso": "No"}
7. ✅ scatta subito il timer "termineDocumentiTimer"
8. ✅ marco completa "Presenta l'istanza di revoca"
9. ✅ collaudo-sede-toscana completa "Valuta l'istanza di revoca"
10. ✅ controllo: conclusa true; fine "fineDecaduta"; eventi ["EMAIL"]

## Istanza di revoca non presentata

1. ✅ marco avvia la pratica "Linea vita bis"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ scatta subito il timer "termineDocumentiTimer"
11. ✅ scatta subito il timer "termineRevoca"
12. ✅ controllo: conclusa true; fine "fineDecaduta"

## Codice non acquisito nei termini

1. ✅ marco avvia la pratica "Ancoraggi tetto"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ scatta subito il timer "chiusuraCodiceTimer"
6. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineSenzaCodice"]

## Codice sbagliato, poi sportello chiuso (TC-21)

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"esitoInvio": "RIFIUTATO", "messaggioInvio": "Codice non valido o già utilizzato"}
9. ✅ scatta subito il timer "chiusuraSportelloTimer"
10. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineNonInviata"]

## Elenchi mai pubblicati

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ scatta subito il timer "attesaElenchi"
10. ✅ controllo: conclusa true; fine "fineSenzaEsito"; passa ["sFineSenzaElenchi"]

## Respinta, con termini scaduti e avvisi dell'istruttoria

Integrazione senza risposta in 30 giorni, osservazioni non presentate in 10 giorni, avviso a 100 giorni e termine di 120 giorni segnalati, poi rigetto.

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ marco completa "Incarica il tecnico"
11. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
12. ✅ elena completa "Compila e firma la perizia"
13. ✅ marco completa "Carica i documenti"
14. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
15. ✅ scatta subito il timer "termineIntegrazione"
16. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
17. ✅ scatta subito il timer "termineOsservazioni"
18. ✅ scatta subito il timer "avviso100"
19. ✅ scatta subito il timer "termine120"
20. ✅ controllo: attivita "Svolgi l'istruttoria"; eventi ["NOTIFICA", "SCADENZA"]
21. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
22. ✅ controllo: attivita "Svolgi l'istruttoria"; dati {"registrabile": "No"}
23. ✅ collaudo-sede-toscana completa "Svolgi l'istruttoria"
24. ✅ controllo: conclusa true; fine "fineRespinta"

## Rinuncia durante l'istruttoria

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ marco completa "Incarica il tecnico"
11. ✅ collaudo-supporto-isi completa "Abilita il tecnico sulla domanda"
12. ✅ elena completa "Compila e firma la perizia"
13. ✅ marco completa "Carica i documenti"
14. ✅ arriva il messaggio "rinunciaImpresa"
15. ✅ controllo: conclusa true; fine "fineRinuncia"

## Rinuncia prima dello sportello

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ arriva il messaggio "rinunciaImpresa"
8. ✅ controllo: conclusa true; fine "fineRinuncia"

## Rinuncia durante il caricamento dei documenti

1. ✅ marco avvia la pratica "Ancoraggi"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: dati {"esitoRegistrazione": "REGISTRATA", "numeroDomanda": "*"}
4. ✅ scatta subito il timer "attesaCodice"
5. ✅ marco completa "Acquisisci il codice identificativo"
6. ✅ controllo: attivita "Invia il codice allo sportello"; dati {"codiceIdentificativo": "*"}
7. ✅ marco completa "Invia il codice allo sportello"
8. ✅ controllo: attivita []; dati {"esitoInvio": "INVIATA"}
9. ✅ arriva il messaggio "esitoElenco"
10. ✅ arriva il messaggio "rinunciaImpresa"
11. ✅ controllo: conclusa true; fine "fineRinuncia"

## Anagrafica INAIL non disponibile

1. ✅ marco avvia la pratica "Prova con INAIL spento"
2. ✅ controllo: attivita "Compila la domanda"; passa ["leggiAnagraficaErrore"]; eventi ["ERRORE"]
3. ✅ marco completa "Compila la domanda"
4. ✅ controllo: conclusa true; fine "fineAnnullata"

## Servizio di calcolo non disponibile

1. ✅ marco avvia la pratica "Prova con il calcolo spento"
2. ✅ marco completa "Compila la domanda"
3. ✅ controllo: attivita "Compila la domanda"; passa ["calcolaErrore"]
4. ✅ marco completa "Compila la domanda"
5. ✅ controllo: conclusa true; fine "fineAnnullata"

## Copertura

Passi che nessuno scenario ha percorso (da provare a mano o con uno scenario in più):
- Servizio ISI non disponibile (`registraErrore`)
- Servizio ISI non disponibile (`generaCodiceErrore`)
- Servizio ISI non disponibile (`registraInvioErrore`)
- Servizio ISI non disponibile (`verificaIncaricoErrore`)
- Servizio ISI non disponibile (`ricalcolaErrore`)
