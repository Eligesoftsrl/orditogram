# Bando ISI: piano e report di test

## 1. Strategia

| Livello | Strumento | Cosa prova |
|---|---|---|
| Regole del bando | `servizio-isi/src/test` (JUnit 5, 37 test: `CalcoloTest`, `RegistroTest`, `ServizioHttpTest`) | casi del capitolo 11 della specifica (TC-01 … TC-24): punteggio, importi, massimali, assi ammessi, errori bloccanti, unicità, codice, sportello, elenchi, termini dell'istruttoria |
| Verifica statica | `verifica.py` della skill (stesse regole della pubblicazione) | BPMN, form, permessi di `isiDomanda` e `isiElenchi` |
| Collaudo automatico | `collaudo.py` della skill, sulle API di un Orditogram avviato | 16 scenari della domanda e 4 degli elenchi, con il servizio ISI vero |
| Collaudo manuale | [`../collaudo/checklist.md`](../collaudo/checklist.md) | le schermate, gli allegati, la consegna degli esiti dal servizio |
| Accettazione | il cliente, nel suo ambiente | vedi il punto 6 |

## 2. Ambiente

Orditogram 0.13.0 (jar) su PostgreSQL 16, 26 novembre 2026. Servizio ISI (jar Spring Boot) sulla stessa macchina (porta 8095) con
`ISI_CALENDARIO_LIBERO=1`, `ISI_CALENDARIO_FILE=dati-prova/calendario-collaudo.json` (fasi nel 2027, così i timer
non scattano da soli) e un database nuovo a ogni collaudo. Credenziale `servizio-isi` di tipo bearer. Utenti di prova
di Orditogram e utenti creati dal collaudo: marco (impresa), elena (tecnico, studio come organizzazione esterna),
operatori di `sede-toscana`, `sede-campania`, `sede-lombardia`, `supporto-isi`, `direzione-isi`.

## 3. Scenari

### Domanda (`isiDomanda`)

| Scenario | Strada del processo |
|---|---|
| Asse 2 in posizione utile e ammessa (TC-01) | percorso principale: anagrafica INAIL, 201 punti e 70.200 €, registrazione, codice, sportello, posizione utile, documenti, perizia, istruttoria, ammessa |
| Sotto soglia, poi bozza annullata (TC-02) | 89 punti, la registrazione non procede, bozza annullata |
| Seconda domanda della stessa impresa (TC-19) | «L'impresa ha già registrato una domanda per questo Avviso», poi chiusura della compilazione |
| Asse 1.2 subentrata, decaduta, revoca accolta (TC-05) | fuori dai fondi, subentro, niente perizia, decadenza, revoca accolta, integrazione, preavviso, ammessa parzialmente |
| Asse 4 fuori dai fondi e senza subentro (TC-13) | più tipologie, fuori dai fondi nei provvisori e nei definitivi |
| NCD, tecnico non ammesso, revoca respinta | elenco NCD, tecnico uguale al legale rappresentante rifiutato, revoca respinta |
| Istanza di revoca non presentata | decadenza senza istanza nei 5 giorni |
| Codice non acquisito nei termini | chiusura del periodo del codice |
| Codice sbagliato, poi sportello chiuso (TC-21) | codice rifiutato, chiusura dello sportello |
| Elenchi mai pubblicati | attesa di 90 giorni scaduta |
| Respinta, con termini scaduti e avvisi | integrazione e osservazioni senza risposta, avvisi a 100 e 120 giorni, respinta |
| Rinuncia prima dello sportello, durante i documenti, durante l'istruttoria | messaggio `rinunciaImpresa` nelle tre fasi |
| Anagrafica INAIL non disponibile | il servizio non risponde all'avvio: dati a mano |
| Servizio di calcolo non disponibile | errore del calcolo, la domanda torna in compilazione |

### Elenchi (`isiElenchi`)

| Scenario | Strada |
|---|---|
| Elenco provvisorio pubblicato | anteprima sulle domande inviate allo sportello, pubblicazione, esiti alle pratiche |
| Elenco NCD non pubblicato | la direzione decide di non pubblicare |
| Servizio ISI non disponibile | errore dell'anteprima, notifica alla direzione |
| Pubblicazione non riuscita | errore della pubblicazione, notifica alla direzione |

## 4. Esito

- **Test delle regole**: 37 su 37 superati (`./mvnw verify` in `servizio-isi/`).
- **Verifica statica**: 0 errori per entrambi i processi. Avvisi: la notifica a un gruppo (`direzione-isi`), voluta;
  sul server di verifica la credenziale `servizio-isi` non era configurata, avviso che sparisce quando la si configura.
- **Collaudo della domanda**: **16 scenari su 16 superati**, copertura 92 passi su 97. Dettaglio passo per passo in
  [`../collaudo/report.md`](../collaudo/report.md).
- **Collaudo degli elenchi**: **4 scenari su 4 superati**, copertura 10 passi su 10
  ([`../../isi-elenchi/collaudo/report.md`](../../isi-elenchi/collaudo/report.md)).
- **Checklist manuale**: da fare.

Passi mai percorsi in automatico: le cinque strade "Servizio ISI non disponibile" dopo **Registra**, **Rilascia il
codice identificativo**, **Registra l'ora di arrivo**, **Controlla il tecnico incaricato** e **Ricalcola**. Sono uguali
a quella provata nello scenario "Servizio di calcolo non disponibile" (errore sul bordo, la pratica torna all'attività
precedente); per provarle serve spegnere il servizio a metà pratica.

## 5. Non provato in automatico

- Le schermate (il collaudo usa le API): righe 2-20 della checklist.
- Gli allegati obbligatori in **Carica i documenti** e **Compila e firma la perizia** e la verifica della firma P7M.
- La consegna degli esiti dal servizio ISI a Orditogram (`ORDITOGRAM_URL`): nel collaudo il messaggio `esitoElenco` lo
  manda lo script.
- Le PEC vere (senza `spring.mail.*` restano "non inviate").
- Il carico dello sportello (5.000 invii al minuto).

## 6. Criteri di accettazione

Tutti gli scenari superati nell'ambiente del cliente con il calendario e gli stanziamenti veri, checklist completa,
configurazioni di produzione fatte (manuale di esercizio, punto 3) e risposte alle domande aperte di `analisi.md`.
