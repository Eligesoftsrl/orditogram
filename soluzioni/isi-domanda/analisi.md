# Analisi: Bando ISI, domanda di finanziamento

Fonte: `specifiche/Requisiti_ISI_Domanda_v1.md` (versione 1.0 del 26/11/2026). Chiave del processo: `isiDomanda`,
prefisso dei form `isi-`. Primo caso reale di Orditogram: la specifica è scritta per un generatore di codice e
descrive un'applicazione su misura, quindi qui conta soprattutto **cosa entra nella configurazione e cosa no**.

## Regola dei quattro livelli

Ogni requisito è stato messo al livello più basso che lo realizza davvero:

1. **Configurazione** (Studio): BPMN, form, permessi, termini, email. Nessun codice.
2. **Soluzione del cliente**: codice che vale solo per ISI, consegnato con la soluzione e chiamato dai connettori
   (`servizio-isi/`, il *servizio ISI*: Java 21 e Spring Boot 3.5 come Orditogram). Non entra in Orditogram.
3. **Modulo**: funzione di settore accendibile, riusabile da altri clienti (oggi `italia`, `firma-digitale`, `pa`).
4. **Nucleo**: Orditogram stesso. Qui **non è stato toccato niente**: le richieste sono elencate sotto.

| Area della specifica | Livello | Come |
|---|---|---|
| Ciclo di vita (cap. 4), passi della domanda, codice, sportello, elenchi, documenti, perizia, decadenza, revoca, istruttoria, integrazione, preavviso, rinuncia | 1 | `processo.bpmn20.xml`, 13 form, `permessi.json` |
| Termini di 5, 10, 30 giorni (proroga se festivo), avviso a 100 giorni, termine di 120 | 1 + 2 | timer BPMN; i 120 giorni con sospensione li calcola il servizio ISI (`/istruttoria/*`) |
| Comunicazioni PEC (ricevuta, decadenza, integrazione, preavviso, provvedimento) | 1 | `emailConnector` verso `campo:pec`; senza server di posta restano "non inviate" nello storico (il mock di RF-73) |
| Firma digitale dei documenti caricati | 1 + 3 | verifica delle firme di Orditogram (modulo `firma-digitale`) |
| Registro di audit (RF-59) | 1 | registro di Orditogram: letture, azioni, accessi, storico della pratica |
| Punteggio, importi, assi ammessi, errori bloccanti, documenti richiesti (cap. 9) | 2 | `servizio-isi/…/Calcolo.java`, classe pura con il dettaglio e il codice regola di ogni valore (RNF-ARC-02) |
| Tabelle del capitolo 9 per annualità (soglie, punteggi, ATECO, stanziamenti, calendario) | 2 | `servizio-isi/configurazione/2025.json`: dati, mai codice; la versione delle regole finisce nella fotografia |
| Una sola domanda per impresa e annualità, garantita dal database (RF-05) | 2 | `UNIQUE (impresa, annualita)` nel registro del servizio ISI; provato anche con 8 registrazioni in contemporanea |
| Fotografia immutabile alla registrazione con hash e versione delle regole (RF-20 CA-04) | 2 | tabella `domanda` del servizio ISI |
| Codice identificativo, ora di arrivo dal server al millisecondo, codice usato una volta (RF-21, RF-25) | 2 | `/codice`, `/sportello` |
| Elenchi NCD, provvisori con l'ipotesi H3, redistribuzione e definitivi, criteri di precedenza 9.10 (RF-53…55) | 2 | `/elenchi/anteprima`, `/elenchi/pubblica`: l'esito arriva alla pratica con il messaggio `esitoElenco` |
| Anagrafica INAIL finta (RF-71), controllo del tecnico (RF-40) | 2 | `/impresa`, `/incarico` |
| Autenticazione SPID/CIE/CNS (RF-70) | 3 | OIDC di Orditogram con Keycloak come broker (roadmap 5, SAML): per le prove bastano gli utenti esterni |
| Bandi a sportello e graduatorie in genere | 3 (futuro) | se un secondo cliente li chiede, sportello ed elenchi diventano un modulo "bandi" |
| Salvataggio in bozza, calcolo in tempo reale, documenti generati, ecc. | 4 | **richieste di sviluppo**, sotto |

## Mappa

| Attore | In Orditogram | Cosa fa |
|---|---|---|
| ATT-01 Legale rappresentante | utente esterno (portale), parte `richiedente` | avvia la domanda, compila, registra, codice, sportello, documenti, incarico al tecnico, revoca, integrazioni, osservazioni |
| ATT-02 Delegato | utente esterno | **vedi N1**: oggi le attività del richiedente le lavora solo chi ha avviato |
| ATT-03 Tecnico | utente esterno di un'organizzazione (studio), parte `tecnico` | compila e firma la perizia |
| ATT-04 Operatore di sede | gruppi `sede-<regione>` (es. `sede-toscana`), scelti dal servizio ISI in base alla Regione | istruttoria, valutazione dell'istanza di revoca |
| ATT-05 Amministratore del bando | gruppo `direzione-isi` (vede ed esporta tutte le domande) + configurazione del servizio ISI | calendario, tabelle, stanziamenti, elenchi |
| ATT-06 Sistema | timer del BPMN e servizio ISI | chiusure di fase, decadenze, avvisi |
| Supporto | gruppo `supporto-isi` | abilita lo studio del tecnico sulla domanda (passo a mano, vedi N2) |

| Passo | Chi | Realizzazione |
|---|---|---|
| Nuova domanda | impresa | form `isi-avvio` (codice fiscale, titolo); poi calendario e dati INAIL dal servizio ISI. INAIL non risponde → si compila a mano con l'avviso |
| Compilazione (passi 1-8) | impresa | attività "Compila la domanda" (`isi-domanda`, 155 campi in sezioni, campi per asse con `visibileSe`). L'azione "Calcola e salva" chiama `/calcola` e torna al form con punteggio, errori, dettaglio e documenti |
| Registrazione | impresa | azione "Registra la domanda" + conferma esplicita; solo se `registrabile == 'Sì'`; `/registra` ricalcola, applica l'unicità e salva la fotografia; ricevuta via PEC |
| Chiusura della compilazione | sistema | timer `${chiusuraCompilazione}` sulla fase → "Non registrata entro il termine" |
| Codice | impresa | attesa dell'apertura, "Acquisisci il codice" (entro `${chiusuraCodice}`), `/codice` |
| Sportello | impresa | "Invia il codice allo sportello" (entro `${chiusuraSportello}`), `/sportello`; codice rifiutato → si riprova |
| Esito degli elenchi | servizio ISI | messaggio `esitoElenco` (NCD, IN_POSIZIONE_UTILE, NON_AMMISSIBILE_FONDI, SUBENTRATA); fuori dai fondi → attesa della redistribuzione |
| Documenti | impresa + tecnico | in parallelo "Carica i documenti" e, tranne l'Asse 1.2, incarico del tecnico (controllo `/incarico`), abilitazione dello studio, perizia firmata |
| Decadenza e revoca | sistema, impresa, sede | timer `${termineDocumenti}` → PEC di decadenza, istanza entro 5 giorni, valutazione della sede |
| Istruttoria | sede | 120 giorni con sospensione durante le integrazioni; "Ricalcola" con i criteri corretti; ammettere sotto soglia riporta all'istruttoria; integrazione (30 giorni), preavviso (10 giorni), esito con PEC |
| Rinuncia | sede o sistema esterno | messaggio `rinunciaImpresa` nelle fasi sportello, documenti, istruttoria |

## Verifica rispetto a Orditogram

Tutto il processo usa elementi supportati. Due accorgimenti:

- **Parallelo**: la riunione dei rami documenti e perizia passa prima da una decisione di raccordo ("Perizia pronta o
  non richiesta"): un gateway parallelo aspetta un arrivo per ogni freccia in ingresso, e con tre frecce la pratica
  restava ferma (scoperto nel collaudo, aggiunto agli errori frequenti della skill).
- **Gruppo della sede da un dato**: `candidateGroups="${gruppoSede}"`. Funziona (provato), la verifica lo elenca
  come gruppo.

L'avviso di `verifica.py` su `direzione-isi` (riceve una notifica ma non lavora attività) è voluto: la direzione vede
ed esporta tutte le domande e riceve solo la segnalazione dei 120 giorni superati.

## Richieste di sviluppo per il nucleo (non realizzate)

In ordine di impatto su questo cliente. Nessuna è stata aggirata con trucchi; dove c'è una soluzione provvisoria è
scritta.

| # | Cosa manca | Requisiti | Oggi |
|---|---|---|---|
| N1 | **Parte richiedente = l'impresa**: tutte le persone dell'organizzazione lavorano le attività del richiedente, e alcune azioni (REGISTRA, sportello) riservate al legale rappresentante o al delegato con il potere (`delega.puo_registrare`) | RF-02, RF-05, RF-20, ATT-02 | lavora solo chi ha avviato la domanda; un delegato non può completare |
| N2 | **L'esterno indica un'altra parte** (il tecnico, per codice fiscale o scegliendo lo studio) | RF-40, RF-41 | l'impresa scrive i dati, il gruppo `supporto-isi` collega lo studio a mano |
| N3 | **Salvare un'attività senza completarla** (bozza, salvataggio automatico ogni 60 s, controllo di concorrenza) | RF-08 | "Calcola e salva" completa l'attività e la ripropone: i dati restano, ma ogni salvataggio è un passo nello storico |
| N4 | **Valori calcolati nel form mentre si scrive** (pannello laterale con punteggio e contributo) | §5.3, RF-11 CA-02, RNF-PER-01 | il calcolo si vede dopo "Calcola e salva" |
| N5 | **Tabelle in JSON nel corpo dei connettori** (oggi escono nel formato interno di Java) | RF-13 (macchine), RF-12 CA-03 | due macchine e due beni come campi separati: per più macchine serve N5 |
| N6 | **Date in formato AAAA-MM-GG nei testi dei connettori** (oggi `Mon Mar 01 00:00:00 UTC 2010`) | tutte le regole sulle date | il servizio ISI legge anche quel formato (`calcolo.data`) |
| N7 | **Documenti tipizzati e obbligatori per condizione** (elenco di 9.9 con un posto per documento, solo PDF/P7M, 10 MB, tipo reale, antivirus) e controllo di `documentiObbligatori` anche nelle attività | RF-31, RNF-SEC-06 | l'elenco si legge, l'impresa conferma con una casella; il backend controlla gli obbligatori solo all'avvio (da verificare) |
| N8 | **Documenti generati da modello** (Modulo A, ricevute, riepilogo PDF "non valido ai fini…", provvedimento) | RF-17 CA-03, RF-20 CA-06, RF-21 CA-03, RF-31 CA-03, RF-57 CA-05 | ricevute ed esiti come testo della PEC; è la fase 4 della roadmap |
| N9 | **Struttura della pratica da un dato** (Regione → sede) per "vede le domande della propria Regione" | RF-56 | la sede vede le domande su cui lavora; la direzione vede tutto |
| N10 | **Azioni dell'esterno sulla pratica** fuori dalle attività (rinuncia, annullamento di una bozza in qualsiasi momento) | RF-35, RF-07 | rinuncia registrata dalla sede o da un sistema esterno |
| N11 | **Duplica pratica** | RF-06 | non c'è |
| N12 | **Avvio idempotente dall'API pubblica** (409 sulla coppia codice simulazione + versione) e OAuth2 client credentials | RF-75 | l'API `/api/v1` avvia pratiche con chiave API, senza idempotenza; la bozza importata non avrebbe l'impresa come richiedente (N1) |
| N13 | **Colonne personalizzate nell'elenco delle pratiche** (asse, punteggio, contributo) e **pagine pubbliche** (elenchi, calendario) | RF-04, RF-30, RF-50 CA-03 | elenchi e calendario in JSON dal servizio ISI (`/elenchi`, `/calendario`) |
| N14 | **Sportello ad alto carico**: 5.000 invii al minuto con l'ora presa prima di tutto | RNF-PER-02 | l'ora la prende il servizio ISI per prima, ma la richiesta passa dal motore di processo: **non misurato**, serve una prova di carico (o uno sportello dedicato nella soluzione del cliente) |
| N15 | Calendario diverso per Regione o Asse, sessione a 30 minuti, mock SPID con 5 utenti | RF-50, RF-01 | un calendario solo; sessione e utenti di prova si configurano in Keycloak |

## Lacune e contraddizioni della specifica, scelte fatte

1. **TC-15 è incoerente con 9.4**: il bene 2 (misura 2f 60 + fattore 3a 60) vale 120 solo se le parti sociali
   (18) contano per il bene 1 e non per il 2. Il punteggio delle parti sociali è dell'impresa, non del bene: il motore
   lo somma a ogni bene (138). Il test verifica la regola com'è scritta e copre l'esclusione del bene sotto soglia con
   un caso coerente (140 e 120, senza criteri comuni). **Da confermare.**
2. *"Fatturato o totale di bilancio superano il limite"* (R-ALL-02): letto come nella definizione UE delle PMI,
   la riduzione si applica se **tutti** i valori indicati superano il limite (con il solo fatturato, conta quello).
3. *Tasso sotto 3,5 ‰*: non è in tabella; si danno 4 punti come per "non definito".
4. *Spese di progetto e macchine*: il campo "Spese di progetto" esclude le macchine del passo 4, che entrano con la
   regola dell'80% del listino. Progetto "solo macchine" = macchine presenti e nessun'altra spesa.
5. *Cambio di asse* (RF-12 CA-06): Orditogram non svuota i campi; i dati degli altri assi restano nascosti e il
   calcolo li ignora.
6. *120 giorni dell'istruttoria*: la specifica non dice cosa succede dopo. Scelta: avviso alla sede a 100 giorni,
   segnalazione alla direzione a 120, la pratica resta aperta.
7. *Preavviso di rigetto*: non sospende i 120 giorni (la specifica parla di sospensione solo per le integrazioni).
8. *Integrazione o osservazioni non arrivate*: alla scadenza l'istruttoria riprende con quello che c'è.
9. *Revoca accolta*: la domanda va a `DOCUMENTI_INVIATI` come scritto, anche se i documenti mancano: li verifica
   l'istruttoria (e può chiederli con un'integrazione).
10. *Ammettere sotto soglia* (RF-57 CA-02): se dopo il ricalcolo la domanda non è più registrabile, "Ammetti"
    riporta all'istruttoria e il form lo dice: la sede sceglie il preavviso di rigetto.
11. *Elenco NCD*: arriva alla pratica in qualsiasi momento tra la registrazione e l'invio allo sportello.
12. *Gruppo della sede*: dalla Regione dell'unità produttiva (`sedi` nella configurazione); Regione senza sede →
    `sede-nazionale`.

## Domande aperte per il cliente

- TC-15: le parti sociali (e RLS, RLAQ) si contano per ogni bene dell'Asse 5? (scelta 1)
- R-ALL-02: la riduzione per fatturato scatta se supera il fatturato **o** il bilancio, o solo se li superano entrambi? (scelta 2)
- Cosa succede alla domanda quando l'istruttoria supera i 120 giorni? (scelta 6)
- Il preavviso di rigetto sospende il termine dell'istruttoria? (scelta 7)
- Chi registra la rinuncia: l'impresa dal portale (serve N10) o la sede su PEC dell'impresa?
- Sedi INAIL: un gruppo per Regione (`sede-toscana`…) va bene, o servono sedi provinciali?
