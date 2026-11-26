# Bando ISI: domanda di finanziamento. Documento funzionale

## 1. Scopo

L'applicazione permette alle imprese di compilare, registrare e presentare la domanda di finanziamento del Bando ISI
di INAIL (incentivi per progetti di salute e sicurezza sul lavoro, annualità 2025), e alle sedi INAIL di svolgere
l'istruttoria. Riferimenti: Avviso pubblico ISI 2025, Allegato criteri (Delibera CdA INAIL n. 187 del 19/11/2025),
Allegati tecnici 1.1–5. Risultato per l'impresa: una domanda con punteggio e contributo calcolati, registrata una sola
volta, inviata allo sportello informatico e, se rientra nei fondi, istruita fino al provvedimento.

## 2. Chi è coinvolto

| Chi | In Orditogram | Cosa fa |
|---|---|---|
| Legale rappresentante dell'impresa | utente del portale (esterno) | compila e registra la domanda, acquisisce e invia il codice, carica i documenti, incarica il tecnico, risponde alle richieste |
| Tecnico asseveratore | utente del portale, membro dello studio professionale | compila e firma la perizia della domanda per cui è incaricato |
| Operatore della sede INAIL | gruppo della sede della Regione (es. `sede-toscana`) | svolge l'istruttoria, valuta le istanze di revoca |
| Supporto ISI | gruppo `supporto-isi` | collega lo studio del tecnico alla domanda |
| Direzione centrale (amministratore del bando) | gruppo `direzione-isi` | vede ed esporta tutte le domande, calcola e pubblica gli elenchi (processo *Bando ISI: elenchi cronologici*) |
| Servizio ISI | sistema collegato | calcola punteggio e importi, tiene il registro delle domande registrate, dei codici, dello sportello e degli elenchi |

## 3. Il flusso in breve

![Diagramma](diagramma.png)

1. **Nuova domanda.** L'impresa indica codice fiscale e titolo del progetto. I dati dell'impresa arrivano
   dall'anagrafica INAIL; se il servizio non risponde si compilano a mano.
2. **Compilazione.** Un modulo unico con gli otto passi della specifica. Con **Calcola e salva** i dati si conservano
   (anche incompleti) e la domanda mostra punteggio, contributo, errori bloccanti, assi ammessi, il calcolo voce per
   voce e i documenti da preparare. Più bozze per impresa sono possibili.
3. **Registrazione.** Con **Registra la domanda** e la conferma esplicita, se non ci sono errori bloccanti. Il
   servizio ISI ricalcola tutto, rifiuta la registrazione se l'impresa ne ha già una per l'annualità, salva una
   fotografia immutabile con la versione delle regole e l'ora al millisecondo. L'impresa riceve la ricevuta via PEC.
   Alla chiusura della compilazione le bozze non registrate si chiudono.
4. **Codice identificativo.** Nel periodo previsto l'impresa acquisisce un codice di 12 caratteri senza caratteri
   ambigui.
5. **Sportello informatico.** Nell'ora di apertura l'impresa scrive il codice e lo invia: conta l'ora di arrivo sul
   server. Un codice sbagliato o già usato è rifiutato senza dire perché.
6. **Elenchi.** La direzione pubblica gli elenchi (NCD, provvisori, definitivi); ogni domanda riceve il suo esito.
   Fuori dai fondi si attende la redistribuzione: se subentra prosegue, altrimenti si chiude.
7. **Documenti e perizia.** In parallelo l'impresa carica i documenti e (tranne l'Asse 1.2) incarica il tecnico, che
   compila e firma la perizia. Se i documenti non arrivano entro il termine la domanda decade: l'impresa ha 5 giorni
   per un'istanza di revoca, che la sede accoglie o respinge.
8. **Istruttoria.** La sede verifica requisiti, documenti e perizia, può correggere i criteri di punteggio e
   ricalcolare, chiedere integrazioni (30 giorni, il termine dell'istruttoria è sospeso), inviare il preavviso di
   rigetto (10 giorni per le osservazioni), ammettere, ammettere parzialmente o respingere. L'esito arriva via PEC.
9. **Rinuncia.** In ogni fase dopo la registrazione la rinuncia chiude la domanda e libera le risorse.

## 4. Regole

- **Punteggio, importi e ammissibilità**: tutte le regole del capitolo 9 della specifica, con le tabelle
  dell'annualità come dati (vedi manuale di esercizio). Soglia 130 punti (Asse 5: per bene); contributo massimo
  130.000 €, minimo 5.000 € (non per l'Asse 1.2 sotto i 50 dipendenti); anticipo e fideiussione.
- **Termini**: chiusura della compilazione, periodo del codice, sportello: dal calendario dell'annualità. Termine dei
  documenti: dalla pubblicazione dell'elenco. Istanza di revoca 5 giorni, integrazione 30, osservazioni 10: se
  scadono di festivo slittano al primo giorno lavorativo. Istruttoria: 120 giorni, sospesi durante le integrazioni,
  avviso alla sede a 100 giorni e segnalazione alla direzione a 120.
- **Ammettere sotto soglia**: se dopo il ricalcolo la domanda non raggiunge più la soglia, "Ammetti" non procede.

## 5. Dati raccolti

| Modulo | Dati principali | Dati personali |
|---|---|---|
| Nuova domanda ISI | codice fiscale dell'impresa, titolo del progetto | codice fiscale (ditte individuali) |
| Compila la domanda | dati dell'impresa e dell'unità produttiva, dimensione, asse e tipologia, progetto, macchine o beni, criteri di punteggio, piano economico, dichiarazioni | PEC, codici fiscali delle ditte individuali; dichiarazioni su condanne (R-REQ-11): dato giudiziario |
| Incarica il tecnico | dati del tecnico, codici fiscali di legale rappresentante e soci | sì |
| Carica i documenti | scadenza del documento di identità, allegati | documento di identità |
| Perizia | situazione ante e post, data certa del DVR, allegati | no |
| Istruttoria, integrazione, osservazioni, revoca | verifiche, esito, motivazioni, risposte | no |

## 6. Comunicazioni

Tutte via email alla PEC indicata nella domanda: ricevuta di registrazione, ricevuta del caricamento dei documenti,
comunicazione di decadenza, rigetto dell'istanza di revoca, richiesta di integrazione, preavviso di rigetto,
provvedimento finale (con i documenti della pratica). Notifiche in Orditogram: alla sede a 100 giorni
dall'avvio dell'istruttoria, alla direzione a 120. I testi sono nella scheda tecnica.

## 7. Integrazioni

Il **servizio ISI** (consegnato con la soluzione) calcola e tiene i registri del bando; l'anagrafica INAIL, la
verifica delle deleghe, la PEC e la firma sono finte (mock) come chiede la specifica. Se il servizio non risponde:
la compilazione e l'istruttoria tornano al modulo, il controllo del tecnico torna all'incarico, gli altri passi si
fermano e si riprovano. Dettagli in `06-integrazioni.md`.

## 8. Chi vede cosa

Le imprese vedono solo le proprie domande e i dati destinati a loro; il tecnico vede la domanda per cui è abilitato;
la sede vede le domande su cui lavora; la direzione e il supporto vedono tutte le domande, la direzione le esporta.

## 9. Fuori ambito e limiti

Fuori ambito per la specifica: rendicontazione, pagamenti, protocollo, integrazioni reali. Limiti di questa prima
versione (dipendono da sviluppi di Orditogram, elencati nell'analisi come N1…N15): le attività dell'impresa le lavora
chi ha avviato la domanda (non ancora i delegati); il tecnico va collegato dal supporto; non c'è salvataggio
automatico né calcolo mentre si scrive; al massimo due macchine e due beni; niente PDF generati (Modulo A,
ricevute, riepilogo); niente pagina pubblica degli elenchi; il carico dello sportello non è stato misurato.

## 10. Glossario

Vedi il capitolo 1 della specifica: Asse, Tipologia, Intervento aggiuntivo, ULA, Tasso medio di tariffa, Soglia,
Registrazione, Codice identificativo, Sportello informatico, Elenchi NCD, Modulo A, Perizia asseverata.
