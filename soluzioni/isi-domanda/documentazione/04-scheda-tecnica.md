# Scheda tecnica: Bando ISI: domanda di finanziamento

Generata dagli artefatti della soluzione con `documenta.py`: non modificarla a mano, rigenerala.

Domanda di finanziamento del Bando ISI di INAIL: compilazione guidata con punteggio e importi calcolati dal servizio ISI, registrazione (una per impresa), codice identificativo, sportello informatico, esito degli elenchi, caricamento dei documenti con la perizia del tecnico, decadenza e istanza di revoca, istruttoria della sede INAIL entro 120 giorni con integrazioni e preavviso di rigetto.

| | |
|---|---|
| Identificativo del processo | `isiDomanda` |
| Chi può avviare (BPMN) | tutti gli utenti |
| Gruppi che lavorano le attività | ${gruppoSede}, supporto-isi |
| Form | `isi-avvio`, `isi-domanda`, `isi-codice`, `isi-sportello`, `isi-documenti`, `isi-incarico`, `isi-abilita-tecnico`, `isi-perizia`, `isi-revoca`, `isi-valuta-revoca`, `isi-istruttoria`, `isi-integrazione`, `isi-osservazioni` |
| Anagrafiche | nessuna |

![Diagramma del processo](diagramma.png)

## Passi del processo

| Passo | Tipo | Chi | Form | Note |
|---|---|---|---|---|
| Nuova domanda | Avvio | tutti | `isi-avvio` |  |
| Leggi il calendario del bando | Automatica | Orditogram |  | chiama un servizio esterno |
| Leggi i dati dell'impresa da INAIL | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Compilazione della domanda |
| Compilazione della domanda | Sottoprocesso |  |  | scadenza "Chiusura della compilazione": dalla data o durata nel dato chiusuraCompilazione → Non registrata entro il termine |
| Compila la domanda (nel sottoprocesso) | Attività | la parte richiedente | `isi-domanda` |  |
| Calcola punteggio e importi (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Compila la domanda |
| Registra la domanda (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Compila la domanda |
| Domanda registrata (nel sottoprocesso) | Fine |  |  |  |
| Bozza annullata (nel sottoprocesso) | Fine |  |  |  |
| Non registrata entro il termine | Fine |  |  |  |
| Bozza annullata | Fine |  |  |  |
| Invia la ricevuta di registrazione | Automatica | Orditogram |  | invia un'email |
| Codice e sportello informatico | Sottoprocesso |  |  | se arriva il messaggio `esitoElenco` → Esito negli elenchi?; se arriva il messaggio `rinunciaImpresa` → Registra la rinuncia |
| Apertura del periodo del codice (nel sottoprocesso) | Attesa |  |  | aspetta dalla data o durata nel dato aperturaCodice |
| Acquisisci il codice identificativo (nel sottoprocesso) | Attività | la parte richiedente | `isi-codice` | scadenza "Chiusura del periodo del codice": dalla data o durata nel dato chiusuraCodice → Codice non acquisito |
| Rilascia il codice identificativo (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Acquisisci il codice identificativo |
| Invia il codice allo sportello (nel sottoprocesso) | Attività | la parte richiedente | `isi-sportello` | scadenza "Chiusura dello sportello": dalla data o durata nel dato chiusuraSportello → Codice non inviato |
| Registra l'ora di arrivo (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Invia il codice allo sportello |
| Attesa degli elenchi (90 giorni) (nel sottoprocesso) | Attesa |  |  | aspetta 90 giorni |
| Codice non acquisito (nel sottoprocesso) | Fine |  |  |  |
| Codice non inviato (nel sottoprocesso) | Fine |  |  |  |
| Elenchi non pubblicati (nel sottoprocesso) | Fine |  |  |  |
| Non in elenco (codice non inviato) | Fine |  |  |  |
| Attesa della redistribuzione | Messaggio in arrivo |  |  | aspetta il messaggio `esitoElenco` |
| Non ammissibile per carenza di fondi | Fine |  |  |  |
| Caricamento dei documenti | Sottoprocesso |  |  | scadenza "Termine per i documenti": dalla data o durata nel dato termineDocumenti → Comunica la decadenza; se arriva il messaggio `rinunciaImpresa` → Registra la rinuncia |
| Carica i documenti (nel sottoprocesso) | Attività | la parte richiedente | `isi-documenti` |  |
| Incarica il tecnico (nel sottoprocesso) | Attività | la parte richiedente | `isi-incarico` |  |
| Controlla il tecnico incaricato (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Incarica il tecnico |
| Abilita il tecnico sulla domanda (nel sottoprocesso) | Attività | gruppo supporto-isi | `isi-abilita-tecnico` |  |
| Compila e firma la perizia (nel sottoprocesso) | Attività | la parte tecnico | `isi-perizia` |  |
| Documenti completi (nel sottoprocesso) | Fine |  |  |  |
| Segna i documenti come inviati | Automatica | Orditogram |  | chiama un servizio esterno |
| Invia la ricevuta del caricamento | Automatica | Orditogram |  | invia un'email |
| Comunica la decadenza | Automatica | Orditogram |  | invia un'email |
| Segna la domanda come decaduta | Automatica | Orditogram |  | chiama un servizio esterno |
| Presenta l'istanza di revoca | Attività | la parte richiedente | `isi-revoca` | scadenza "5 giorni per l'istanza": 5 giorni (se scade in un giorno festivo, slitta al primo lavorativo) → Decaduta |
| Valuta l'istanza di revoca | Attività | gruppo ${gruppoSede} | `isi-valuta-revoca` |  |
| Comunica il rigetto dell'istanza | Automatica | Orditogram |  | invia un'email |
| Decaduta | Fine |  |  |  |
| Avvia il conteggio dei 120 giorni | Automatica | Orditogram |  | chiama un servizio esterno |
| Istruttoria della sede INAIL | Sottoprocesso |  |  | sollecito "Avviso a 100 giorni": dalla data o durata nel dato avvisoIstruttoria → Segnala l'istruttoria a 100 giorni; sollecito "Termine di 120 giorni": dalla data o durata nel dato scadenzaIstruttoria → Segnala il termine superato; se arriva il messaggio `rinunciaImpresa` → Registra la rinuncia |
| Svolgi l'istruttoria (nel sottoprocesso) | Attività | gruppo ${gruppoSede} | `isi-istruttoria` |  |
| Ricalcola punteggio e contributo (nel sottoprocesso) | Automatica | Orditogram |  | chiama un servizio esterno; se non riesce ("Servizio ISI non disponibile") → Svolgi l'istruttoria |
| Esito deciso (nel sottoprocesso) | Fine |  |  |  |
| Segnala l'istruttoria a 100 giorni | Automatica | Orditogram |  | invia una notifica |
| Avviso inviato | Fine |  |  |  |
| Segnala il termine superato | Automatica | Orditogram |  | invia una notifica |
| Termine segnalato | Fine |  |  |  |
| Sospendi il conteggio dei 120 giorni | Automatica | Orditogram |  | chiama un servizio esterno |
| Chiedi l'integrazione | Automatica | Orditogram |  | invia un'email |
| Rispondi alla richiesta di integrazione | Attività | la parte richiedente | `isi-integrazione` | scadenza "30 giorni per l'integrazione": 30 giorni (se scade in un giorno festivo, slitta al primo lavorativo) → Riprendi il conteggio dei 120 giorni |
| Riprendi il conteggio dei 120 giorni | Automatica | Orditogram |  | chiama un servizio esterno |
| Comunica il preavviso di rigetto | Automatica | Orditogram |  | invia un'email |
| Presenta le osservazioni | Attività | la parte richiedente | `isi-osservazioni` | scadenza "10 giorni per le osservazioni": 10 giorni (se scade in un giorno festivo, slitta al primo lavorativo) → Istruttoria della sede INAIL |
| Registra l'esito dell'istruttoria | Automatica | Orditogram |  | chiama un servizio esterno |
| Invia il provvedimento | Automatica | Orditogram |  | invia un'email |
| Ammessa | Fine |  |  |  |
| Ammessa parzialmente | Fine |  |  |  |
| Respinta | Fine |  |  |  |
| Registra la rinuncia | Automatica | Orditogram |  | chiama un servizio esterno |
| Rinunciata | Fine |  |  |  |

## Decisioni

- **Cosa ha scelto l'impresa?**
  - altrimenti → Compila la domanda ("Calcola e salva")
  - `${azione == 'Registra la domanda' && registrabile == 'Sì'}` → Registra la domanda ("Registra, senza errori")
- **Registrata?**
  - `${esitoRegistrazione == 'REGISTRATA'}` → Domanda registrata ("Sì")
  - altrimenti → Compila la domanda ("No")
- **Annulla la bozza?**
  - `${azione == 'Annulla la bozza'}` → Bozza annullata ("Sì")
  - altrimenti → Calcola punteggio e importi ("No")
- **Domanda registrata?**
  - altrimenti → Bozza annullata ("No")
  - `${azione == 'Registra la domanda'}` → Invia la ricevuta di registrazione ("Sì")
- **Codice preso in carico?**
  - `${esitoInvio == 'INVIATA'}` → Attesa degli elenchi (90 giorni) ("Sì")
  - altrimenti → Invia il codice allo sportello ("Rifiutato")
- **Esito negli elenchi?**
  - altrimenti → Non in elenco (codice non inviato) ("Altrimenti")
  - `${esitoElenco == 'NCD' || esitoElenco == 'IN_POSIZIONE_UTILE' || esitoElenco == 'SUBENTRATA'}` → Caricamento dei documenti ("NCD o in posizione utile")
  - `${esitoElenco == 'NON_AMMISSIBILE_FONDI'}` → Attesa della redistribuzione ("Fuori dai fondi")
- **Subentrata?**
  - `${esitoElenco == 'SUBENTRATA'}` → Caricamento dei documenti ("Sì")
  - altrimenti → Non ammissibile per carenza di fondi ("No")
- **Documenti e perizia** (in parallelo): Carica i documenti, Serve la perizia?
- **Serve la perizia?**
  - `${asse == '1.2 Modelli organizzativi'}` → Perizia pronta o non richiesta ("Asse 1.2: no")
  - altrimenti → Incarica il tecnico ("Sì")
- **Tecnico ammesso?**
  - `${incaricoAmmesso == 'Sì'}` → Abilita il tecnico sulla domanda ("Sì")
  - altrimenti → Incarica il tecnico ("No")
- **Perizia pronta o non richiesta**
  - altrimenti → Tutto pronto
- **Tutto pronto** (in parallelo): riunisce le strade parallele
- **Istanza accolta?**
  - `${esitoRevoca == 'Accolta'}` → Segna i documenti come inviati ("Accolta")
  - altrimenti → Comunica il rigetto dell'istanza ("Respinta")
- **Esito pronto?**
  - `${esitoIstruttoria == 'Ricalcola' || (esitoIstruttoria == 'Ammetti' && registrabile != 'Sì')}` → Svolgi l'istruttoria ("Ricalcola o sotto soglia")
  - altrimenti → Esito deciso ("Altrimenti")
- **Cosa ha deciso la sede?**
  - `${esitoIstruttoria == 'Richiedi integrazione'}` → Sospendi il conteggio dei 120 giorni ("Richiedi integrazione")
  - `${esitoIstruttoria == 'Preavviso di rigetto'}` → Comunica il preavviso di rigetto ("Preavviso di rigetto")
  - altrimenti → Registra l'esito dell'istruttoria ("Ammetti, ammetti parzialmente o respingi")
- **Esito?**
  - `${esitoIstruttoria == 'Ammetti'}` → Ammessa ("Ammessa")
  - `${esitoIstruttoria == 'Ammetti parzialmente'}` → Ammessa parzialmente ("Parziale")
  - altrimenti → Respinta ("Respinta")

## Attività automatiche

### Leggi il calendario del bando

- Chiamata **GET** a `http://localhost:8095/calendario`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `chiusuraCompilazione = chiusuraCompilazione`, `aperturaCodice = aperturaCodice`, `chiusuraCodice = chiusuraCodice`, `aperturaSportello = aperturaSportello`, `chiusuraSportello = chiusuraSportello`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi

### Leggi i dati dell'impresa da INAIL

- Chiamata **GET** a `http://localhost:8095/impresa?codiceFiscale=${codiceFiscaleImpresa}`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `ragioneSociale = ragioneSociale`, `partitaIva = partitaIva`, `formaGiuridica = formaGiuridica`, `ateco = ateco`, `sedeLegale = sedeLegale`, `codiceDitta = codiceDitta`, `tassoMedio = tassoMedio`, `rapportoAssicurativo = rapportoAssicurativo`, `dataAssociazionePortale = dataAssociazionePortale`, `dataIscrizioneCciaa = dataIscrizioneCciaa`, `dataCostituzione = dataCostituzione`, `pec = pec`, `avvisoAnagrafica = avvisoAnagrafica`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore

### Calcola punteggio e importi

- Chiamata **POST** a `http://localhost:8095/calcola`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `riepilogo = riepilogo`, `punteggio = punteggio`, `registrabile = registrabile`, `erroriBloccanti = erroriBloccanti`, `avvisiCalcolo = avvisiCalcolo`, `dettaglioPunteggio = dettaglioPunteggio`, `dettaglioImporti = dettaglioImporti`, `assiAmmessi = assiAmmessi`, `assiNonAmmessi = assiNonAmmessi`, `documentiRichiesti = documentiRichiesti`, `contributoTotale = contributoTotale`, `contributoTotaleTesto = contributoTotaleTesto`, `importoAnticipo = anticipo`, `fideiussione = fideiussione`, `ulaCalcolate = ula`, `versioneRegole = versioneRegole`, `modelloPerizia = modelloPerizia`, `gruppoSede = gruppoSede`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{
  "ragioneSociale": "${ragioneSociale}",
  "partitaIva": "${partitaIva}",
  "formaGiuridica": "${formaGiuridica}",
  "ateco": "${ateco}",
  "dataCostituzione": "${dataCostituzione}",
  "dataIscrizioneCciaa": "${dataIscrizioneCciaa}",
  "sedeLegale": "${sedeLegale}",
  "codiceDitta": "${codiceDitta}",
  "pec": "${pec}",
  "tipoSoggetto": "${tipoSoggetto}",
  "agricolaPrimaria": "${agricolaPrimaria}",
  "iap": "${iap}",
  "associataCollegata": "${associataCollegata}",
  "rapportoAssicurativo": "${rapportoAssicurativo}",
  "dataAssociazionePortale": "${dataAssociazionePortale}",
  "indirizzoUnita": "${indirizzoUnita}",
  "regione": "${regione}",
  "ula": "${ula}",
  "fatturato": "${fatturato}",
  "totaleBilancio": "${totaleBilancio}",
  "dipendentiImpresa": "${dipendentiImpresa}",
  "dipendentiUnita": "${dipendentiUnita}",
  "attivitaStagionale": "${attivitaStagionale}",
  "dataInizioAttivita": "${dataInizioAttivita}",
  "dittaSenzaDipendenti": "${dittaSenzaDipendenti}",
  "asse": "${asse}",
  "tipologia11": "${tipologia11}",
  "tipologia12": "${tipologia12}",
  "tipologia2": "${tipologia2}",
  "tipologia3": "${tipologia3}",
  "tipologia4a": "${tipologia4a}",
  "tipologia4b": "${tipologia4b}",
  "tipologia4c": "${tipologia4c}",
  "aggiuntivo11": "${aggiuntivo11}",
  "aggiuntivo2": "${aggiuntivo2}",
  "aggiuntivo3": "${aggiuntivo3}",
  "aggiuntivo4": "${aggiuntivo4}",
  "bene1Tipo": "${bene1Tipo}",
  "bene1Misura": "${bene1Misura}",
  "bene1Fattore": "${bene1Fattore}",
  "bene1Preventivo": "${bene1Preventivo}",
  "bene1Listino": "${bene1Listino}",
  "bene1PotenzaKw": "${bene1PotenzaKw}",
  "bene1AnnoSostituito": "${bene1AnnoSostituito}",
  "bene1Modalita": "${bene1Modalita}",
  "bene1RicavoPermuta": "${bene1RicavoPermuta}",
  "secondoBene": "${secondoBene}",
  "bene2Tipo": "${bene2Tipo}",
  "bene2Misura": "${bene2Misura}",
  "bene2Fattore": "${bene2Fattore}",
  "bene2Preventivo": "${bene2Preventivo}",
  "bene2Listino": "${bene2Listino}",
  "bene2PotenzaKw": "${bene2PotenzaKw}",
  "bene2AnnoSostituito": "${bene2AnnoSostituito}",
  "bene2Modalita": "${bene2Modalita}",
  "bene2RicavoPermuta": "${bene2RicavoPermuta}",
  "noleggioPattoAcquisto": "${noleggioPattoAcquisto}",
  "giovaneEta": "${giovaneEta}",
  "giovaneFormazione": "${giovaneFormazione}",
  "giovaneCapoAzienda": "${giovaneCapoAzienda}",
  "titoloProgetto": "${titoloProgetto}",
  "descrizioneProgetto": "${descrizioneProgetto}",
  "lavorazioneRischio": "${lavorazioneRischio}",
  "macchina1Descrizione": "${macchina1Descrizione}",
  "macchina1MarcaModello": "${macchina1MarcaModello}",
  "macchina1Preventivo": "${macchina1Preventivo}",
  "macchina1Listino": "${macchina1Listino}",
  "macchina1Trattore": "${macchina1Trattore}",
  "macchina1ListinoAccessori": "${macchina1ListinoAccessori}",
  "macchina1PotenzaKw": "${macchina1PotenzaKw}",
  "macchina1AnnoSostituito": "${macchina1AnnoSostituito}",
  "macchina1Modalita": "${macchina1Modalita}",
  "secondaMacchina": "${secondaMacchina}",
  "macchina2Descrizione": "${macchina2Descrizione}",
  "macchina2MarcaModello": "${macchina2MarcaModello}",
  "macchina2Preventivo": "${macchina2Preventivo}",
  "macchina2Listino": "${macchina2Listino}",
  "macchina2Trattore": "${macchina2Trattore}",
  "macchina2ListinoAccessori": "${macchina2ListinoAccessori}",
  "macchina2PotenzaKw": "${macchina2PotenzaKw}",
  "macchina2AnnoSostituito": "${macchina2AnnoSostituito}",
  "macchina2Modalita": "${macchina2Modalita}",
  "mqCopertura": "${mqCopertura}",
  "speseCopertura": "${speseCopertura}",
  "mqControsoffitto": "${mqControsoffitto}",
  "speseControsoffitto": "${speseControsoffitto}",
  "speseAccessorie": "${speseAccessorie}",
  "immobileLocato": "${immobileLocato}",
  "quotaImmobile": "${quotaImmobile}",
  "sgslUltimi3Anni": "${sgslUltimi3Anni}",
  "concessione12Precedenti": "${concessione12Precedenti}",
  "tassoMedio": "${tassoMedio}",
  "partiSociali": "${partiSociali}",
  "rls": "${rls}",
  "certIso45001": "${certIso45001}",
  "certIso14001": "${certIso14001}",
  "certEmas": "${certEmas}",
  "certIso39001": "${certIso39001}",
  "certMog": "${certMog}",
  "certRlaq": "${certRlaq}",
  "ratingLegalita": "${ratingLegalita}",
  "speseProgetto": "${speseProgetto}",
  "speseTecniche": "${speseTecniche}",
  "costoPerizia": "${costoPerizia}",
  "speseAggiuntivo": "${speseAggiuntivo}",
  "ivaNonRecuperabile": "${ivaNonRecuperabile}",
  "motivazioneIva": "${motivazioneIva}",
  "anticipo": "${anticipo}",
  "nessunaSpesaNonAmmessa": "${nessunaSpesaNonAmmessa}",
  "dichR02": "${dichR02}",
  "dichR03": "${dichR03}",
  "dichR05": "${dichR05}",
  "dichR06": "${dichR06}",
  "dichR07": "${dichR07}",
  "dichR08": "${dichR08}",
  "dichR09": "${dichR09}",
  "dichR10": "${dichR10}",
  "dichR11": "${dichR11}",
  "dichR12": "${dichR12}",
  "dichR15": "${dichR15}",
  "dichR16": "${dichR16}",
  "dichR17": "${dichR17}",
  "dichR18": "${dichR18}",
  "finanziamentiPrecedenti": "${finanziamentiPrecedenti}",
  "assePrecedente": "${assePrecedente}",
  "rinunciaPec": "${rinunciaPec}",
  "azione": "${azione}",
  "confermaRegistrazione": "${confermaRegistrazione}",
  "confermaAnnullamento": "${confermaAnnullamento}",
  "codiceFiscaleImpresa": "${codiceFiscaleImpresa}",
  "annualita": "2025"
}
```

### Registra la domanda

- Chiamata **POST** a `http://localhost:8095/registra`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `esitoRegistrazione = esitoRegistrazione`, `messaggioRegistrazione = messaggioRegistrazione`, `numeroDomanda = numeroDomanda`, `dataOraRegistrazione = dataOraRegistrazione`, `gruppoSede = gruppoSede`, `esitoElenco = esitoElenco`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{
  "ragioneSociale": "${ragioneSociale}",
  "partitaIva": "${partitaIva}",
  "formaGiuridica": "${formaGiuridica}",
  "ateco": "${ateco}",
  "dataCostituzione": "${dataCostituzione}",
  "dataIscrizioneCciaa": "${dataIscrizioneCciaa}",
  "sedeLegale": "${sedeLegale}",
  "codiceDitta": "${codiceDitta}",
  "pec": "${pec}",
  "tipoSoggetto": "${tipoSoggetto}",
  "agricolaPrimaria": "${agricolaPrimaria}",
  "iap": "${iap}",
  "associataCollegata": "${associataCollegata}",
  "rapportoAssicurativo": "${rapportoAssicurativo}",
  "dataAssociazionePortale": "${dataAssociazionePortale}",
  "indirizzoUnita": "${indirizzoUnita}",
  "regione": "${regione}",
  "ula": "${ula}",
  "fatturato": "${fatturato}",
  "totaleBilancio": "${totaleBilancio}",
  "dipendentiImpresa": "${dipendentiImpresa}",
  "dipendentiUnita": "${dipendentiUnita}",
  "attivitaStagionale": "${attivitaStagionale}",
  "dataInizioAttivita": "${dataInizioAttivita}",
  "dittaSenzaDipendenti": "${dittaSenzaDipendenti}",
  "asse": "${asse}",
  "tipologia11": "${tipologia11}",
  "tipologia12": "${tipologia12}",
  "tipologia2": "${tipologia2}",
  "tipologia3": "${tipologia3}",
  "tipologia4a": "${tipologia4a}",
  "tipologia4b": "${tipologia4b}",
  "tipologia4c": "${tipologia4c}",
  "aggiuntivo11": "${aggiuntivo11}",
  "aggiuntivo2": "${aggiuntivo2}",
  "aggiuntivo3": "${aggiuntivo3}",
  "aggiuntivo4": "${aggiuntivo4}",
  "bene1Tipo": "${bene1Tipo}",
  "bene1Misura": "${bene1Misura}",
  "bene1Fattore": "${bene1Fattore}",
  "bene1Preventivo": "${bene1Preventivo}",
  "bene1Listino": "${bene1Listino}",
  "bene1PotenzaKw": "${bene1PotenzaKw}",
  "bene1AnnoSostituito": "${bene1AnnoSostituito}",
  "bene1Modalita": "${bene1Modalita}",
  "bene1RicavoPermuta": "${bene1RicavoPermuta}",
  "secondoBene": "${secondoBene}",
  "bene2Tipo": "${bene2Tipo}",
  "bene2Misura": "${bene2Misura}",
  "bene2Fattore": "${bene2Fattore}",
  "bene2Preventivo": "${bene2Preventivo}",
  "bene2Listino": "${bene2Listino}",
  "bene2PotenzaKw": "${bene2PotenzaKw}",
  "bene2AnnoSostituito": "${bene2AnnoSostituito}",
  "bene2Modalita": "${bene2Modalita}",
  "bene2RicavoPermuta": "${bene2RicavoPermuta}",
  "noleggioPattoAcquisto": "${noleggioPattoAcquisto}",
  "giovaneEta": "${giovaneEta}",
  "giovaneFormazione": "${giovaneFormazione}",
  "giovaneCapoAzienda": "${giovaneCapoAzienda}",
  "titoloProgetto": "${titoloProgetto}",
  "descrizioneProgetto": "${descrizioneProgetto}",
  "lavorazioneRischio": "${lavorazioneRischio}",
  "macchina1Descrizione": "${macchina1Descrizione}",
  "macchina1MarcaModello": "${macchina1MarcaModello}",
  "macchina1Preventivo": "${macchina1Preventivo}",
  "macchina1Listino": "${macchina1Listino}",
  "macchina1Trattore": "${macchina1Trattore}",
  "macchina1ListinoAccessori": "${macchina1ListinoAccessori}",
  "macchina1PotenzaKw": "${macchina1PotenzaKw}",
  "macchina1AnnoSostituito": "${macchina1AnnoSostituito}",
  "macchina1Modalita": "${macchina1Modalita}",
  "secondaMacchina": "${secondaMacchina}",
  "macchina2Descrizione": "${macchina2Descrizione}",
  "macchina2MarcaModello": "${macchina2MarcaModello}",
  "macchina2Preventivo": "${macchina2Preventivo}",
  "macchina2Listino": "${macchina2Listino}",
  "macchina2Trattore": "${macchina2Trattore}",
  "macchina2ListinoAccessori": "${macchina2ListinoAccessori}",
  "macchina2PotenzaKw": "${macchina2PotenzaKw}",
  "macchina2AnnoSostituito": "${macchina2AnnoSostituito}",
  "macchina2Modalita": "${macchina2Modalita}",
  "mqCopertura": "${mqCopertura}",
  "speseCopertura": "${speseCopertura}",
  "mqControsoffitto": "${mqControsoffitto}",
  "speseControsoffitto": "${speseControsoffitto}",
  "speseAccessorie": "${speseAccessorie}",
  "immobileLocato": "${immobileLocato}",
  "quotaImmobile": "${quotaImmobile}",
  "sgslUltimi3Anni": "${sgslUltimi3Anni}",
  "concessione12Precedenti": "${concessione12Precedenti}",
  "tassoMedio": "${tassoMedio}",
  "partiSociali": "${partiSociali}",
  "rls": "${rls}",
  "certIso45001": "${certIso45001}",
  "certIso14001": "${certIso14001}",
  "certEmas": "${certEmas}",
  "certIso39001": "${certIso39001}",
  "certMog": "${certMog}",
  "certRlaq": "${certRlaq}",
  "ratingLegalita": "${ratingLegalita}",
  "speseProgetto": "${speseProgetto}",
  "speseTecniche": "${speseTecniche}",
  "costoPerizia": "${costoPerizia}",
  "speseAggiuntivo": "${speseAggiuntivo}",
  "ivaNonRecuperabile": "${ivaNonRecuperabile}",
  "motivazioneIva": "${motivazioneIva}",
  "anticipo": "${anticipo}",
  "nessunaSpesaNonAmmessa": "${nessunaSpesaNonAmmessa}",
  "dichR02": "${dichR02}",
  "dichR03": "${dichR03}",
  "dichR05": "${dichR05}",
  "dichR06": "${dichR06}",
  "dichR07": "${dichR07}",
  "dichR08": "${dichR08}",
  "dichR09": "${dichR09}",
  "dichR10": "${dichR10}",
  "dichR11": "${dichR11}",
  "dichR12": "${dichR12}",
  "dichR15": "${dichR15}",
  "dichR16": "${dichR16}",
  "dichR17": "${dichR17}",
  "dichR18": "${dichR18}",
  "finanziamentiPrecedenti": "${finanziamentiPrecedenti}",
  "assePrecedente": "${assePrecedente}",
  "rinunciaPec": "${rinunciaPec}",
  "azione": "${azione}",
  "confermaRegistrazione": "${confermaRegistrazione}",
  "confermaAnnullamento": "${confermaAnnullamento}",
  "codiceFiscaleImpresa": "${codiceFiscaleImpresa}",
  "annualita": "2025"
}
```

### Invia la ricevuta di registrazione

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda} registrata
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

la domanda n. ${numeroDomanda} per il progetto "${titoloProgetto}" è stata registrata il ${dataOraRegistrazione}.
${riepilogo}

Dal ${aperturaCodice} potrà acquisire il codice identificativo per lo sportello informatico.

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Rilascia il codice identificativo

- Chiamata **POST** a `http://localhost:8095/codice`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `codiceIdentificativo = codiceIdentificativo`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}"}
```

### Registra l'ora di arrivo

- Chiamata **POST** a `http://localhost:8095/sportello`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `esitoInvio = esitoInvio`, `messaggioInvio = messaggioInvio`, `dataOraInvio = dataOraInvio`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}", "codice": "${codiceInviato}"}
```

### Controlla il tecnico incaricato

- Chiamata **POST** a `http://localhost:8095/incarico`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `incaricoAmmesso = incaricoAmmesso`, `motivoIncarico = motivoIncarico`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{"cfTecnico": "${cfTecnico}", "cfLegaleRappresentante": "${cfLegaleRappresentante}", "cfSoci": "${cfSoci}"}
```

### Segna i documenti come inviati

- Chiamata **POST** a `http://localhost:8095/stato`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `statoDomanda = statoDomanda`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}", "stato": "DOCUMENTI_INVIATI", "chi": "impresa"}
```

### Invia la ricevuta del caricamento

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda}, documenti ricevuti
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

il caricamento dei documenti della domanda n. ${numeroDomanda} è completo. La sede INAIL della Regione ${regione} avvia l'istruttoria, che si conclude entro 120 giorni.

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Comunica la decadenza

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda} decaduta
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

la domanda n. ${numeroDomanda} è decaduta perché i documenti non sono stati inviati entro il ${termineDocumenti}.
Entro 5 giorni può presentare un'istanza motivata di revoca della decadenza dalla sua area riservata.

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Segna la domanda come decaduta

- Chiamata **POST** a `http://localhost:8095/stato`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `statoDomanda = statoDomanda`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}", "stato": "DECADUTA", "motivo": "Documenti non inviati nei termini"}
```

### Comunica il rigetto dell'istanza

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda}, istanza di revoca respinta
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

l'istanza di revoca della decadenza della domanda n. ${numeroDomanda} è stata respinta:
${noteRevoca}

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Avvia il conteggio dei 120 giorni

- Chiamata **POST** a `http://localhost:8095/istruttoria/avvia`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `scadenzaIstruttoria = scadenzaIstruttoria`, `avvisoIstruttoria = avvisoIstruttoria`, `giorniRimanenti = giorniRimanenti`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}"}
```

### Ricalcola punteggio e contributo

- Chiamata **POST** a `http://localhost:8095/calcola`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `riepilogo = riepilogo`, `punteggio = punteggio`, `registrabile = registrabile`, `erroriBloccanti = erroriBloccanti`, `avvisiCalcolo = avvisiCalcolo`, `dettaglioPunteggio = dettaglioPunteggio`, `dettaglioImporti = dettaglioImporti`, `assiAmmessi = assiAmmessi`, `assiNonAmmessi = assiNonAmmessi`, `documentiRichiesti = documentiRichiesti`, `contributoTotale = contributoTotale`, `contributoTotaleTesto = contributoTotaleTesto`, `importoAnticipo = anticipo`, `fideiussione = fideiussione`, `ulaCalcolate = ula`, `versioneRegole = versioneRegole`, `modelloPerizia = modelloPerizia`, `gruppoSede = gruppoSede`
- Attesa massima: 20 secondi
- Se non riesce: Segui la strada di errore
- Corpo della richiesta:

```json
{
  "ragioneSociale": "${ragioneSociale}",
  "partitaIva": "${partitaIva}",
  "formaGiuridica": "${formaGiuridica}",
  "ateco": "${ateco}",
  "dataCostituzione": "${dataCostituzione}",
  "dataIscrizioneCciaa": "${dataIscrizioneCciaa}",
  "sedeLegale": "${sedeLegale}",
  "codiceDitta": "${codiceDitta}",
  "pec": "${pec}",
  "tipoSoggetto": "${tipoSoggetto}",
  "agricolaPrimaria": "${agricolaPrimaria}",
  "iap": "${iap}",
  "associataCollegata": "${associataCollegata}",
  "rapportoAssicurativo": "${rapportoAssicurativo}",
  "dataAssociazionePortale": "${dataAssociazionePortale}",
  "indirizzoUnita": "${indirizzoUnita}",
  "regione": "${regione}",
  "ula": "${ula}",
  "fatturato": "${fatturato}",
  "totaleBilancio": "${totaleBilancio}",
  "dipendentiImpresa": "${dipendentiImpresa}",
  "dipendentiUnita": "${dipendentiUnita}",
  "attivitaStagionale": "${attivitaStagionale}",
  "dataInizioAttivita": "${dataInizioAttivita}",
  "dittaSenzaDipendenti": "${dittaSenzaDipendenti}",
  "asse": "${asse}",
  "tipologia11": "${tipologia11}",
  "tipologia12": "${tipologia12}",
  "tipologia2": "${tipologia2}",
  "tipologia3": "${tipologia3}",
  "tipologia4a": "${tipologia4a}",
  "tipologia4b": "${tipologia4b}",
  "tipologia4c": "${tipologia4c}",
  "aggiuntivo11": "${aggiuntivo11}",
  "aggiuntivo2": "${aggiuntivo2}",
  "aggiuntivo3": "${aggiuntivo3}",
  "aggiuntivo4": "${aggiuntivo4}",
  "bene1Tipo": "${bene1Tipo}",
  "bene1Misura": "${bene1Misura}",
  "bene1Fattore": "${bene1Fattore}",
  "bene1Preventivo": "${bene1Preventivo}",
  "bene1Listino": "${bene1Listino}",
  "bene1PotenzaKw": "${bene1PotenzaKw}",
  "bene1AnnoSostituito": "${bene1AnnoSostituito}",
  "bene1Modalita": "${bene1Modalita}",
  "bene1RicavoPermuta": "${bene1RicavoPermuta}",
  "secondoBene": "${secondoBene}",
  "bene2Tipo": "${bene2Tipo}",
  "bene2Misura": "${bene2Misura}",
  "bene2Fattore": "${bene2Fattore}",
  "bene2Preventivo": "${bene2Preventivo}",
  "bene2Listino": "${bene2Listino}",
  "bene2PotenzaKw": "${bene2PotenzaKw}",
  "bene2AnnoSostituito": "${bene2AnnoSostituito}",
  "bene2Modalita": "${bene2Modalita}",
  "bene2RicavoPermuta": "${bene2RicavoPermuta}",
  "noleggioPattoAcquisto": "${noleggioPattoAcquisto}",
  "giovaneEta": "${giovaneEta}",
  "giovaneFormazione": "${giovaneFormazione}",
  "giovaneCapoAzienda": "${giovaneCapoAzienda}",
  "titoloProgetto": "${titoloProgetto}",
  "descrizioneProgetto": "${descrizioneProgetto}",
  "lavorazioneRischio": "${lavorazioneRischio}",
  "macchina1Descrizione": "${macchina1Descrizione}",
  "macchina1MarcaModello": "${macchina1MarcaModello}",
  "macchina1Preventivo": "${macchina1Preventivo}",
  "macchina1Listino": "${macchina1Listino}",
  "macchina1Trattore": "${macchina1Trattore}",
  "macchina1ListinoAccessori": "${macchina1ListinoAccessori}",
  "macchina1PotenzaKw": "${macchina1PotenzaKw}",
  "macchina1AnnoSostituito": "${macchina1AnnoSostituito}",
  "macchina1Modalita": "${macchina1Modalita}",
  "secondaMacchina": "${secondaMacchina}",
  "macchina2Descrizione": "${macchina2Descrizione}",
  "macchina2MarcaModello": "${macchina2MarcaModello}",
  "macchina2Preventivo": "${macchina2Preventivo}",
  "macchina2Listino": "${macchina2Listino}",
  "macchina2Trattore": "${macchina2Trattore}",
  "macchina2ListinoAccessori": "${macchina2ListinoAccessori}",
  "macchina2PotenzaKw": "${macchina2PotenzaKw}",
  "macchina2AnnoSostituito": "${macchina2AnnoSostituito}",
  "macchina2Modalita": "${macchina2Modalita}",
  "mqCopertura": "${mqCopertura}",
  "speseCopertura": "${speseCopertura}",
  "mqControsoffitto": "${mqControsoffitto}",
  "speseControsoffitto": "${speseControsoffitto}",
  "speseAccessorie": "${speseAccessorie}",
  "immobileLocato": "${immobileLocato}",
  "quotaImmobile": "${quotaImmobile}",
  "sgslUltimi3Anni": "${sgslUltimi3Anni}",
  "concessione12Precedenti": "${concessione12Precedenti}",
  "tassoMedio": "${tassoMedio}",
  "partiSociali": "${partiSociali}",
  "rls": "${rls}",
  "certIso45001": "${certIso45001}",
  "certIso14001": "${certIso14001}",
  "certEmas": "${certEmas}",
  "certIso39001": "${certIso39001}",
  "certMog": "${certMog}",
  "certRlaq": "${certRlaq}",
  "ratingLegalita": "${ratingLegalita}",
  "speseProgetto": "${speseProgetto}",
  "speseTecniche": "${speseTecniche}",
  "costoPerizia": "${costoPerizia}",
  "speseAggiuntivo": "${speseAggiuntivo}",
  "ivaNonRecuperabile": "${ivaNonRecuperabile}",
  "motivazioneIva": "${motivazioneIva}",
  "anticipo": "${anticipo}",
  "nessunaSpesaNonAmmessa": "${nessunaSpesaNonAmmessa}",
  "dichR02": "${dichR02}",
  "dichR03": "${dichR03}",
  "dichR05": "${dichR05}",
  "dichR06": "${dichR06}",
  "dichR07": "${dichR07}",
  "dichR08": "${dichR08}",
  "dichR09": "${dichR09}",
  "dichR10": "${dichR10}",
  "dichR11": "${dichR11}",
  "dichR12": "${dichR12}",
  "dichR15": "${dichR15}",
  "dichR16": "${dichR16}",
  "dichR17": "${dichR17}",
  "dichR18": "${dichR18}",
  "finanziamentiPrecedenti": "${finanziamentiPrecedenti}",
  "assePrecedente": "${assePrecedente}",
  "rinunciaPec": "${rinunciaPec}",
  "azione": "${azione}",
  "confermaRegistrazione": "${confermaRegistrazione}",
  "confermaAnnullamento": "${confermaAnnullamento}",
  "codiceFiscaleImpresa": "${codiceFiscaleImpresa}",
  "annualita": "2025"
}
```

### Segnala l'istruttoria a 100 giorni

- A: il gruppo ${gruppoSede}
- Titolo: Istruttoria oltre 100 giorni: domanda ${numeroDomanda}
- Messaggio: La domanda n. ${numeroDomanda} di ${ragioneSociale} è in istruttoria da oltre 100 giorni: il termine è il ${scadenzaIstruttoria}.

### Segnala il termine superato

- A: il gruppo direzione-isi
- Titolo: Termine di 120 giorni superato: domanda ${numeroDomanda}
- Messaggio: L'istruttoria della domanda n. ${numeroDomanda} di ${ragioneSociale} (sede ${gruppoSede}) ha superato i 120 giorni.

### Sospendi il conteggio dei 120 giorni

- Chiamata **POST** a `http://localhost:8095/istruttoria/sospendi`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `giorniRimanenti = giorniRimanenti`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}"}
```

### Chiedi l'integrazione

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda}, richiesta di integrazione
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

per l'istruttoria della domanda n. ${numeroDomanda} la sede INAIL chiede:
${richiestaIstruttoria}

Ha 30 giorni per rispondere dalla sua area riservata. Il termine dell'istruttoria resta sospeso fino alla risposta.

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Riprendi il conteggio dei 120 giorni

- Chiamata **POST** a `http://localhost:8095/istruttoria/riprendi`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `scadenzaIstruttoria = scadenzaIstruttoria`, `avvisoIstruttoria = avvisoIstruttoria`, `giorniRimanenti = giorniRimanenti`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}"}
```

### Comunica il preavviso di rigetto

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda}, preavviso di rigetto
- Allegati: Nessuno
- Testo:

```
Gentile ${ragioneSociale},

la sede INAIL intende respingere la domanda n. ${numeroDomanda} per questi motivi:
${richiestaIstruttoria}

Ha 10 giorni per presentare osservazioni e documenti dalla sua area riservata (art. 10-bis L. 241/1990).

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Registra l'esito dell'istruttoria

- Chiamata **POST** a `http://localhost:8095/stato`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `statoDomanda = statoDomanda`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}", "esitoIstruttoria": "${esitoIstruttoria}", "chi": "sede"}
```

### Invia il provvedimento

- A: l'indirizzo nel campo `pec`
- Oggetto: Bando ISI 2025: domanda ${numeroDomanda}, esito dell'istruttoria
- Allegati: Tutti i documenti della pratica
- Testo:

```
Gentile ${ragioneSociale},

l'istruttoria della domanda n. ${numeroDomanda} si è conclusa con questo esito: ${esitoIstruttoria}.
Contributo richiesto: ${contributoTotaleTesto}. Contributo ammesso (se parziale): ${importoAmmesso} €.
${motivazioneIstruttoria}

Il provvedimento vale per l'intervento principale e per quello aggiuntivo.

INAIL - Bando ISI 2025
Questa comunicazione è inviata alla PEC indicata nella domanda.
```

### Registra la rinuncia

- Chiamata **POST** a `http://localhost:8095/stato`
- Credenziale: `servizio-isi` (configurata sul server)
- Dati salvati dalla risposta: `statoDomanda = statoDomanda`
- Attesa massima: 20 secondi
- Se non riesce: Blocca e riprova più tardi
- Corpo della richiesta:

```json
{"numeroDomanda": "${numeroDomanda}", "stato": "RINUNCIATA", "motivo": "${motivoRinuncia}", "chi": "impresa"}
```

## Messaggi in arrivo da altri sistemi

| Messaggio | Dove | Dati portati | Indirizzo |
|---|---|---|---|
| `esitoElenco` | sul bordo di "Codice e sportello informatico" | esitoElenco, posizioneElenco, termineDocumenti | `POST /api/messaggi/esitoElenco` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/esitoElenco` (operatore) |
| `rinunciaImpresa` | sul bordo di "Codice e sportello informatico" | motivoRinuncia | `POST /api/messaggi/rinunciaImpresa` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/rinunciaImpresa` (operatore) |
| `esitoElenco` | nel flusso | esitoElenco, posizioneElenco, termineDocumenti | `POST /api/messaggi/esitoElenco` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/esitoElenco` (operatore) |
| `rinunciaImpresa` | sul bordo di "Caricamento dei documenti" | motivoRinuncia | `POST /api/messaggi/rinunciaImpresa` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/rinunciaImpresa` (operatore) |
| `rinunciaImpresa` | sul bordo di "Istruttoria della sede INAIL" | motivoRinuncia | `POST /api/messaggi/rinunciaImpresa` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/rinunciaImpresa` (operatore) |

## Form

### Nuova domanda ISI (`isi-avvio`)

Indica l'impresa e il titolo del progetto: i dati dell'impresa arrivano dall'anagrafica INAIL e la domanda si compila nel passo successivo. Puoi avere più bozze, ma registrarne una sola.

_usato in: Nuova domanda_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Codice fiscale dell'impresa | `codiceFiscaleImpresa` | Testo | sì | formato formato personalizzato; visibile alle parti esterne |
| Titolo del progetto | `titoloProgetto` | Testo | sì | titolo della pratica/scheda; al massimo 200 caratteri; visibile alle parti esterne; Al massimo 200 caratteri. Si può cambiare durante la compilazione. |

### Compila la domanda ISI (`isi-domanda`)

Compila i passi in qualsiasi ordine. «Calcola e salva» conserva i dati e aggiorna punteggio, contributo ed errori; quando non ci sono errori scegli «Registra la domanda».

_usato in: Compila la domanda_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| **Situazione della domanda** | | sezione | | Si aggiorna ogni volta che confermi con «Calcola e salva». |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Errori che bloccano la registrazione | `erroriBloccanti` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Avvisi del calcolo | `avvisiCalcolo` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Esito dell'ultima registrazione | `messaggioRegistrazione` | Testo |  | sola lettura; visibile alle parti esterne |
| Dati dell'impresa | `avvisoAnagrafica` | Testo |  | sola lettura; visibile alle parti esterne |
| **Passo 1. Dati dell'impresa** | | sezione | | Precompilati dall'anagrafica INAIL: controllali e correggili se serve. |
| Ragione sociale | `ragioneSociale` | Testo |  | al massimo 200 caratteri; visibile alle parti esterne |
| Codice fiscale | `codiceFiscaleImpresa` | Testo |  | sola lettura; visibile alle parti esterne |
| Partita IVA | `partitaIva` | Testo |  | formato partita IVA; Facoltativa per gli Enti del Terzo settore. |
| Forma giuridica | `formaGiuridica` | Scelta |  | scelte: Impresa individuale, Società di persone, Società di capitali, Società cooperativa, Società agricola, Ente del Terzo settore, Altro |
| Codice ATECO 2025 | `ateco` | Testo |  | formato formato personalizzato; visibile alle parti esterne |
| Data di costituzione | `dataCostituzione` | Data |  | massimo oggi |
| Data di iscrizione alla CCIAA (o al RUNTS o all'Albo) | `dataIscrizioneCciaa` | Data |  | massimo oggi |
| Sede legale | `sedeLegale` | Testo |  | visibile alle parti esterne |
| Codice ditta INAIL | `codiceDitta` | Testo |  |  |
| PEC dell'impresa | `pec` | Testo |  | formato email; Tutte le comunicazioni del bando arrivano qui. |
| Tipo di soggetto | `tipoSoggetto` | Scelta |  | scelte: Impresa, ETS iscritto al RUNTS, ETS iscritto al Registro imprese, ONLUS |
| Impresa agricola primaria | `agricolaPrimaria` | Sì/No |  |  |
| Imprenditore agricolo professionale (IAP) | `iap` | Sì/No |  |  |
| Impresa associata o collegata | `associataCollegata` | Sì/No |  | In questo caso ULA e fatturato del passo 2 vanno scritti già sommati. |
| Rapporto assicurativo | `rapportoAssicurativo` | Scelta |  | scelte: INAIL, INPS gestione agricola, Nessuno |
| Data di associazione al portale INAIL | `dataAssociazionePortale` | Data |  | Almeno 5 giorni lavorativi prima della chiusura della compilazione (art. 12). |
| **Passo 2. Unità produttiva e dimensione** | | sezione | |  |
| Indirizzo dell'unità produttiva | `indirizzoUnita` | Testo |  |  |
| Regione dell'unità produttiva | `regione` | Scelta |  | scelte: Abruzzo, Basilicata, Calabria, Campania, Emilia-Romagna, Friuli-Venezia Giulia, Lazio, Liguria, Lombardia, Marche, Molise, Piemonte, Puglia, Sardegna, Sicilia, Toscana, Trentino-Alto Adige, Umbria, Valle d'Aosta, Veneto; visibile alle parti esterne; Decide lo stanziamento a cui concorre la domanda. |
| ULA dell'anno di riferimento | `ula` | Numero |  | minimo 0; Con un decimale: 10,44 diventa 10,4. |
| Fatturato annuo (€) | `fatturato` | Numero |  | minimo 0 |
| Totale di bilancio (€) | `totaleBilancio` | Numero |  | minimo 0 |
| Dipendenti dell'impresa | `dipendentiImpresa` | Numero |  | minimo 0 |
| Dipendenti dell'unità produttiva | `dipendentiUnita` | Numero |  | minimo 0 |
| Attività stagionale | `attivitaStagionale` | Sì/No |  |  |
| Data di inizio dell'attività | `dataInizioAttivita` | Data |  | solo se `attivitaStagionale` è "true" |
| Ditta individuale senza dipendenti con titolare autonomo | `dittaSenzaDipendenti` | Sì/No |  |  |
| ULA considerate | `ulaCalcolate` | Testo |  | sola lettura |
| **Passo 3. Asse e tipologia** | | sezione | | Si vedono gli assi ammessi per l'impresa e il motivo di quelli esclusi. |
| Assi ammessi per l'impresa | `assiAmmessi` | Testo |  | sola lettura; visibile alle parti esterne |
| Assi non ammessi e perché | `assiNonAmmessi` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Asse di finanziamento | `asse` | Scelta |  | scelte: 1.1 Rischi tecnopatici, 1.2 Modelli organizzativi, 2 Rischi infortunistici, 3 Bonifica amianto, 4 Settori specifici (micro e piccole), 5.1 Agricoltura, 5.2 Giovani agricoltori; visibile alle parti esterne; Se cambi asse, i dati del progetto che non valgono per il nuovo asse non contano più nel calcolo. |
| Tipologia di intervento (Asse 1.1) | `tipologia11` | Scelta |  | scelte: a) Riduzione del rischio chimico, b) Riduzione del rischio rumore (sostituzione di macchine), c) Riduzione del rischio da vibrazioni meccaniche, d) Movimentazione manuale di persone, e) Movimentazione manuale di carichi (automazione), f) Rischio emergenza nella pesca, g) Movimentazione dei carichi nella pesca; solo se `asse` è "1.1 Rischi tecnopatici" |
| Tipologia di intervento (Asse 1.2) | `tipologia12` | Scelta |  | scelte: a) SGSL certificato ISO 45001, b) SGSL di settore (accordi INAIL-Parti sociali), c) MOG art. 30 asseverato, d) MOG semplificato per MPMI; solo se `asse` è "1.2 Modelli organizzativi" |
| Tipologia di intervento (Asse 2) | `tipologia2` | Scelta |  | scelte: a) Caduta dall'alto: ancoraggi permanenti, b) Sostituzione di trattori e macchine obsoleti, c) Spazi confinati o sospetti di inquinamento; solo se `asse` è "2 Rischi infortunistici" |
| Tipologia di intervento (Asse 3) | `tipologia3` | Scelta |  | scelte: a) Rimozione di coperture con amianto, b) Rimozione di coperture e controsoffitti con amianto; solo se `asse` è "3 Bonifica amianto" |
| a) Sostituzione di macchine | `tipologia4a` | Sì/No |  | solo se `asse` è "4 Settori specifici (micro e piccole)" |
| b) Sostituzione di apparecchi elettrici | `tipologia4b` | Sì/No |  | solo se `asse` è "4 Settori specifici (micro e piccole)" |
| c) Riduzione del rischio da polveri di legno | `tipologia4c` | Sì/No |  | solo se `asse` è "4 Settori specifici (micro e piccole)"; Puoi scegliere più tipologie: conta quella con il punteggio più alto. |
| Intervento aggiuntivo (Asse 1.1) | `aggiuntivo11` | Scelta |  | scelte: Nessuno, SGSL ISO 45001; solo se `asse` è "1.1 Rischi tecnopatici" |
| Intervento aggiuntivo (Asse 2) | `aggiuntivo2` | Scelta |  | scelte: Nessuno, DPI intelligenti, Moduli prefabbricati; solo se `asse` è "2 Rischi infortunistici" |
| Intervento aggiuntivo (Asse 3) | `aggiuntivo3` | Scelta |  | scelte: Nessuno, Coperture a verde, Fotovoltaico; solo se `asse` è "3 Bonifica amianto" |
| Intervento aggiuntivo (Asse 4) | `aggiuntivo4` | Scelta |  | scelte: Nessuno, SGSL ISO 45001; solo se `asse` è "4 Settori specifici (micro e piccole)"; Con l'aggiuntivo «SGSL ISO 45001» la certificazione ISO 45001 non dà punti (R-ALL-06). |
| **Bene 1** | | sezione | | Una misura e un fattore di rischio per ogni bene (Allegato tecnico 5). |
| Bene 1: tipo | `bene1Tipo` | Scelta |  | scelte: Trattore, Macchina con motore, Macchina senza motore, Mietitrebbiatrice; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: misura | `bene1Misura` | Scelta |  | scelte: 1a) Macchina priva di motore, 2a) Gasolio, emissioni oltre il 50% sotto i limiti, 2b) Gasolio, emissioni dal 30 al 50% sotto i limiti, 2c) Benzina, emissioni oltre il 35% sotto i limiti, 2d) Benzina, emissioni dal 20 al 35% sotto i limiti, 2e) Motore elettrico o a metano, 2f) Attrezzatura intercambiabile con consumo ridotto del 30%; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: fattore di rischio | `bene1Fattore` | Scelta |  | scelte: 1a) Trattore con rottamazione, 1b) Macchina con rottamazione, 2a) Trattore −3 dB(A), con permuta, 2b) Trattore −2 dB(A), con permuta, 2c) Macchina −3 dB(A), con permuta (non mietitrebbiatrici), 2d) Macchina −2 dB(A), con permuta (non mietitrebbiatrici), 3a) Meccanizzazione di un'operazione manuale; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: prezzo di preventivo (€) | `bene1Preventivo` | Numero |  | minimo 0; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: prezzo di listino del fabbricante (€) | `bene1Listino` | Numero |  | minimo 0; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: potenza (kW) | `bene1PotenzaKw` | Numero |  | minimo 0; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: anno di immissione sul mercato del bene sostituito | `bene1AnnoSostituito` | Numero |  | minimo 1950; massimo 2026; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: sostituzione | `bene1Modalita` | Scelta |  | scelte: Rottamazione, Permuta, Nessuna sostituzione; solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Bene 1: ricavo della permuta (€) | `bene1RicavoPermuta` | Numero |  | minimo 0; solo se `bene1Modalita` è "Permuta" |
| Aggiungi un secondo bene | `secondoBene` | Sì/No |  | solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| **Bene 2** | | sezione | | Una misura e un fattore di rischio per ogni bene (Allegato tecnico 5). |
| Bene 2: tipo | `bene2Tipo` | Scelta |  | scelte: Trattore, Macchina con motore, Macchina senza motore, Mietitrebbiatrice; solo se `secondoBene` è "true" |
| Bene 2: misura | `bene2Misura` | Scelta |  | scelte: 1a) Macchina priva di motore, 2a) Gasolio, emissioni oltre il 50% sotto i limiti, 2b) Gasolio, emissioni dal 30 al 50% sotto i limiti, 2c) Benzina, emissioni oltre il 35% sotto i limiti, 2d) Benzina, emissioni dal 20 al 35% sotto i limiti, 2e) Motore elettrico o a metano, 2f) Attrezzatura intercambiabile con consumo ridotto del 30%; solo se `secondoBene` è "true" |
| Bene 2: fattore di rischio | `bene2Fattore` | Scelta |  | scelte: 1a) Trattore con rottamazione, 1b) Macchina con rottamazione, 2a) Trattore −3 dB(A), con permuta, 2b) Trattore −2 dB(A), con permuta, 2c) Macchina −3 dB(A), con permuta (non mietitrebbiatrici), 2d) Macchina −2 dB(A), con permuta (non mietitrebbiatrici), 3a) Meccanizzazione di un'operazione manuale; solo se `secondoBene` è "true" |
| Bene 2: prezzo di preventivo (€) | `bene2Preventivo` | Numero |  | minimo 0; solo se `secondoBene` è "true" |
| Bene 2: prezzo di listino del fabbricante (€) | `bene2Listino` | Numero |  | minimo 0; solo se `secondoBene` è "true" |
| Bene 2: potenza (kW) | `bene2PotenzaKw` | Numero |  | minimo 0; solo se `secondoBene` è "true" |
| Bene 2: anno di immissione sul mercato del bene sostituito | `bene2AnnoSostituito` | Numero |  | minimo 1950; massimo 2026; solo se `secondoBene` è "true" |
| Bene 2: sostituzione | `bene2Modalita` | Scelta |  | scelte: Rottamazione, Permuta, Nessuna sostituzione; solo se `secondoBene` è "true" |
| Bene 2: ricavo della permuta (€) | `bene2RicavoPermuta` | Numero |  | minimo 0; solo se `bene2Modalita` è "Permuta" |
| Noleggio con patto d'acquisto | `noleggioPattoAcquisto` | Sì/No |  | solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Età sotto i 41 anni alla chiusura della compilazione (R-DEST-08) | `giovaneEta` | Sì/No |  | solo se `asse` è "5.2 Giovani agricoltori" |
| Formazione o competenze del giovane agricoltore | `giovaneFormazione` | Sì/No |  | solo se `asse` è "5.2 Giovani agricoltori" |
| Ruolo di capo azienda | `giovaneCapoAzienda` | Sì/No |  | solo se `asse` è "5.2 Giovani agricoltori" |
| **Passo 4. Dati del progetto** | | sezione | |  |
| Titolo del progetto | `titoloProgetto` | Testo |  | al massimo 200 caratteri; visibile alle parti esterne |
| Descrizione del progetto | `descrizioneProgetto` | Testo lungo |  | al massimo 4000 caratteri; visibile alle parti esterne |
| Lavorazione e rischio interessati | `lavorazioneRischio` | Testo lungo |  | visibile alle parti esterne |
| **Macchina 1** | | sezione | |  |
| Macchina 1: descrizione | `macchina1Descrizione` | Testo |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: marca e modello | `macchina1MarcaModello` | Testo |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: prezzo di preventivo (€) | `macchina1Preventivo` | Numero |  | minimo 0; solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: prezzo di listino del fabbricante (€) | `macchina1Listino` | Numero |  | minimo 0; solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)"; Si ammette al massimo l'80% del listino (R-CALC-02). |
| Macchina 1: è un trattore | `macchina1Trattore` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: listino degli accessori (€) | `macchina1ListinoAccessori` | Numero |  | minimo 0; solo se `macchina1Trattore` è "true"; Al massimo il 30% del listino del trattore base (R-CALC-03). |
| Macchina 1: potenza (kW) | `macchina1PotenzaKw` | Numero |  | minimo 0; solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: anno del bene sostituito | `macchina1AnnoSostituito` | Numero |  | minimo 1950; massimo 2026; solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Macchina 1: sostituzione | `macchina1Modalita` | Scelta |  | scelte: Rottamazione, Permuta, Nessuna sostituzione; solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| Aggiungi una seconda macchina | `secondaMacchina` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" |
| **Macchina 2** | | sezione | |  |
| Macchina 2: descrizione | `macchina2Descrizione` | Testo |  | solo se `secondaMacchina` è "true" |
| Macchina 2: marca e modello | `macchina2MarcaModello` | Testo |  | solo se `secondaMacchina` è "true" |
| Macchina 2: prezzo di preventivo (€) | `macchina2Preventivo` | Numero |  | minimo 0; solo se `secondaMacchina` è "true" |
| Macchina 2: prezzo di listino del fabbricante (€) | `macchina2Listino` | Numero |  | minimo 0; solo se `secondaMacchina` è "true"; Si ammette al massimo l'80% del listino (R-CALC-02). |
| Macchina 2: è un trattore | `macchina2Trattore` | Sì/No |  | solo se `secondaMacchina` è "true" |
| Macchina 2: listino degli accessori (€) | `macchina2ListinoAccessori` | Numero |  | minimo 0; solo se `macchina2Trattore` è "true"; Al massimo il 30% del listino del trattore base (R-CALC-03). |
| Macchina 2: potenza (kW) | `macchina2PotenzaKw` | Numero |  | minimo 0; solo se `secondaMacchina` è "true" |
| Macchina 2: anno del bene sostituito | `macchina2AnnoSostituito` | Numero |  | minimo 1950; massimo 2026; solo se `secondaMacchina` è "true" |
| Macchina 2: sostituzione | `macchina2Modalita` | Scelta |  | scelte: Rottamazione, Permuta, Nessuna sostituzione; solo se `secondaMacchina` è "true" |
| Superficie della copertura (mq) | `mqCopertura` | Numero |  | minimo 0; solo se `asse` è "3 Bonifica amianto" |
| Spese per la copertura (€) | `speseCopertura` | Numero |  | minimo 0; solo se `asse` è "3 Bonifica amianto"; Al massimo 60 €/mq (R-ALL-09). |
| Superficie di controsoffitto o sottocopertura (mq) | `mqControsoffitto` | Numero |  | minimo 0; solo se `asse` è "3 Bonifica amianto" |
| Spese per il controsoffitto (€) | `speseControsoffitto` | Numero |  | minimo 0; solo se `asse` è "3 Bonifica amianto"; Al massimo 20 €/mq (R-ALL-09). |
| Spese per parapetti, ancoraggi e scale (€) | `speseAccessorie` | Numero |  | minimo 0; solo se `asse` è "3 Bonifica amianto"; Al massimo il 10% delle spese della copertura (R-ALL-09). |
| Immobile in locazione | `immobileLocato` | Sì/No |  | solo se `asse` è "3 Bonifica amianto" |
| Quota dell'immobile usata dall'impresa (%) | `quotaImmobile` | Numero |  | minimo 0; massimo 100; solo se `immobileLocato` è "true" |
| L'impresa ha adottato un SGSL o MOG negli ultimi 3 anni | `sgslUltimi3Anni` | Sì/No |  | solo se `asse` è "1.2 Modelli organizzativi" |
| L'impresa ha avuto una concessione Asse 1.2 nelle 3 edizioni precedenti | `concessione12Precedenti` | Sì/No |  | solo se `asse` è "1.2 Modelli organizzativi" |
| **Passo 5. Criteri di punteggio** | | sezione | |  |
| Tasso medio di tariffa INAIL (‰) | `tassoMedio` | Numero |  | minimo 0; massimo 1000; solo se `asse` è "1.1 Rischi tecnopatici" o "1.2 Modelli organizzativi" o "2 Rischi infortunistici" o "3 Bonifica amianto"; Lascia vuoto se non è definito (4 punti). Per l'Asse 4 la rischiosità dipende dall'ATECO. |
| Condivisione con le parti sociali | `partiSociali` | Scelta |  | scelte: Nessuna, Ente bilaterale o Organismo paritetico, Due o più parti sociali; Serve il Modulo E1. |
| Informativa o condivisione con RLS/RLST | `rls` | Sì/No |  | Serve il Modulo E2. |
| Certificazione SGSL UNI EN ISO 45001:2023 | `certIso45001` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "4 Settori specifici (micro e piccole)" |
| Certificazione UNI EN ISO 14001:2015 | `certIso14001` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "4 Settori specifici (micro e piccole)" |
| Registrazione EMAS | `certEmas` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "4 Settori specifici (micro e piccole)" |
| Certificazione UNI ISO 39001:2016 | `certIso39001` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "4 Settori specifici (micro e piccole)" |
| MOG art. 30 d.lgs. 81/08 asseverato | `certMog` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "4 Settori specifici (micro e piccole)" |
| Iscrizione alla Rete del lavoro agricolo di qualità (RLAQ) | `certRlaq` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "1.2 Modelli organizzativi" o "2 Rischi infortunistici" o "3 Bonifica amianto" o "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Rating di legalità | `ratingLegalita` | Sì/No |  | Conta solo a parità di ora di invio, per fatturati oltre 2 milioni. |
| **Passo 6. Piano economico** | | sezione | | Importi al netto dell'IVA; se l'IVA non è recuperabile scrivili con l'IVA. |
| Spese di progetto, escluse le macchine del passo 4 (€) | `speseProgetto` | Numero |  | minimo 0 |
| Spese tecniche, esclusa la perizia (€) | `speseTecniche` | Numero |  | minimo 0; Fino al 10% delle spese di progetto, massimo 10.000 € (5% e 5.000 € se il progetto è solo acquisto di macchine). |
| Costo della perizia (€) | `costoPerizia` | Numero |  | minimo 0; Massimo 1.850 €. |
| Spese dell'intervento aggiuntivo (€) | `speseAggiuntivo` | Numero |  | minimo 0 |
| IVA non recuperabile | `ivaNonRecuperabile` | Sì/No |  |  |
| Perché l'IVA non è recuperabile | `motivazioneIva` | Testo lungo |  | solo se `ivaNonRecuperabile` è "true" |
| Chiedo l'anticipo | `anticipo` | Sì/No |  |  |
| Confermo che il piano non contiene spese non ammesse | `nessunaSpesaNonAmmessa` | Sì/No |  | Non ammessi: DPI (salvo Asse 2 c e DPI intelligenti), veicoli, ponteggi fissi, trasporto, consulenza per la domanda, formazione, valutazione dei rischi, manutenzione ordinaria, leasing, personale interno, costi autofatturati o fatturati da soci e imprese collegate. |
| Calcolo degli importi voce per voce | `dettaglioImporti` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| **Passo 7. Dichiarazioni** | | sezione | | Una casella per ogni requisito dell'Avviso: senza tutte non si registra. |
| Il progetto riguarda lavorazioni già attive alla data di pubblicazione, con rischio documentato nel DVR (art. 5) | `dichR02` | Sì/No |  |  |
| Il progetto non amplia la sede produttiva e non comprende beni usati (art. 5) | `dichR03` | Sì/No |  |  |
| Le macchine o i trattori da sostituire sono di piena proprietà da almeno 3 anni al 31/12/2025 (art. 5) | `dichR05` | Sì/No |  | solo se `asse` è "1.1 Rischi tecnopatici" o "2 Rischi infortunistici" o "4 Settori specifici (micro e piccole)" o "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| Gli immobili sono nella disponibilità dell'impresa da almeno 3 anni al 31/12/2025 (art. 5) | `dichR06` | Sì/No |  | solo se `asse` è "3 Bonifica amianto" |
| L'unità produttiva è attiva nella Regione dell'Avviso (art. 2) | `dichR07` | Sì/No |  |  |
| L'impresa è iscritta a registri o albi entro il 18/12/2025 (art. 2) | `dichR08` | Sì/No |  |  |
| L'impresa è iscritta alla gestione assicurativa e previdenziale ed è in regola con il DURC (art. 2) | `dichR09` | Sì/No |  |  |
| Nessuna procedura concorsuale o liquidazione in corso (art. 2) | `dichR10` | Sì/No |  |  |
| Nessuna condanna definitiva per omicidio o lesioni colpose legate alla sicurezza sul lavoro (art. 2) | `dichR11` | Sì/No |  |  |
| Nessun provvedimento interdittivo art. 9 c. 2 lett. d) d.lgs. 231/2001 (art. 2) | `dichR12` | Sì/No |  |  |
| Sono rispettate le regole sul cumulo di aiuti pubblici sugli stessi costi (art. 9) | `dichR15` | Sì/No |  |  |
| Nessun ordine di recupero pendente e impresa non in difficoltà (Reg. UE 2022/2472) | `dichR16` | Sì/No |  | solo se `asse` è "5.1 Agricoltura" o "5.2 Giovani agricoltori" |
| L'impresa ha la polizza contro calamità naturali ed eventi catastrofali, dove obbligatoria (non richiesta alle imprese agricole art. 2135 c.c.) | `dichR17` | Sì/No |  |  |
| L'impresa sottoscrive il Patto di integrità (Modulo G) | `dichR18` | Sì/No |  |  |
| Finanziamenti ISI ottenuti nelle edizioni precedenti | `finanziamentiPrecedenti` | Scelta |  | scelte: Nessuno, ISI 2022, ISI 2023, ISI 2024 |
| Asse del finanziamento precedente | `assePrecedente` | Scelta |  | scelte: 1.1 Rischi tecnopatici, 1.2 Modelli organizzativi, 2 Rischi infortunistici, 3 Bonifica amianto, 4 Settori specifici (micro e piccole), 5.1 Agricoltura, 5.2 Giovani agricoltori; solo se `finanziamentiPrecedenti` è "ISI 2022" o "ISI 2023" o "ISI 2024" |
| Ho rinunciato via PEC al finanziamento precedente entro il giorno prima dell'apertura (R-REQ-13) | `rinunciaPec` | Sì/No |  | solo se `finanziamentiPrecedenti` è "ISI 2022" o "ISI 2023" o "ISI 2024" |
| **Passo 8. Riepilogo** | | sezione | |  |
| Punteggio per criterio | `dettaglioPunteggio` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Documenti da preparare | `documentiRichiesti` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| **Cosa vuoi fare** | | sezione | |  |
| Azione | `azione` | Scelta | sì | scelte: Calcola e salva, Registra la domanda, Annulla la bozza; «Calcola e salva» conserva i dati anche incompleti e aggiorna punteggio ed errori. |
| Ho capito: dopo la registrazione la domanda non sarà più modificabile | `confermaRegistrazione` | Sì/No | sì | solo se `azione` è "Registra la domanda" |
| Ho capito: la bozza annullata non si recupera | `confermaAnnullamento` | Sì/No | sì | solo se `azione` è "Annulla la bozza" |

### Acquisisci il codice identificativo (`isi-codice`)

Il codice serve il giorno dello sportello informatico. Conservalo: si potrà rivedere finché non lo usi.

_usato in: Acquisisci il codice identificativo_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Registrata il | `dataOraRegistrazione` | Testo |  | sola lettura; visibile alle parti esterne |
| Acquisisci ora il codice identificativo | `confermaCodice` | Sì/No | sì |  |

### Invia il codice allo sportello (`isi-sportello`)

Nel giorno e nell'ora dello sportello scrivi il codice e invialo: conta l'ora di arrivo sul server.

_usato in: Invia il codice allo sportello_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Il tuo codice identificativo | `codiceIdentificativo` | Testo |  | sola lettura; visibile alle parti esterne |
| Esito dell'ultimo tentativo | `messaggioInvio` | Testo |  | sola lettura; visibile alle parti esterne |
| Codice identificativo | `codiceInviato` | Testo | sì | almeno 12 caratteri; al massimo 14 caratteri; visibile alle parti esterne |

### Carica i documenti della domanda (`isi-documenti`)

Allega tutti i documenti dell'elenco in PDF o P7M (massimo 10 MB ciascuno). Il Modulo A va firmato digitalmente dal legale rappresentante. La perizia non si carica qui: la firma il tecnico.

_usato in: Carica i documenti; si caricano documenti (almeno uno obbligatorio); i documenti li vedono anche le parti esterne_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Esito negli elenchi | `esitoElenco` | Testo |  | sola lettura; visibile alle parti esterne |
| Termine per il caricamento | `termineDocumenti` | Testo |  | sola lettura; visibile alle parti esterne |
| Documenti richiesti | `documentiRichiesti` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| **Conferma** | | sezione | |  |
| Scadenza del documento di identità del legale rappresentante | `scadenzaDocumentoIdentita` | Data | sì | minimo oggi; visibile alle parti esterne |
| Ho caricato tutti i documenti dell'elenco | `documentiCompleti` | Sì/No | sì | visibile alle parti esterne |

### Incarica il tecnico per la perizia (`isi-incarico`)

Il tecnico compila e firma la perizia asseverata online. Non può essere titolare, legale rappresentante, amministratore o socio dell'impresa.

_usato in: Incarica il tecnico_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Esito del controllo | `motivoIncarico` | Testo |  | sola lettura; visibile alle parti esterne |
| **Tecnico** | | sezione | |  |
| Codice fiscale del tecnico | `cfTecnico` | Testo | sì | formato codice fiscale; visibile alle parti esterne |
| Nome e cognome del tecnico | `nomeTecnico` | Testo | sì | visibile alle parti esterne |
| Ordine o collegio | `ordineTecnico` | Testo | sì | visibile alle parti esterne |
| Numero di iscrizione | `numeroIscrizioneTecnico` | Testo | sì | visibile alle parti esterne |
| Email o PEC del tecnico | `emailTecnico` | Testo | sì | formato email; visibile alle parti esterne |
| **Persone dell'impresa** | | sezione | | Servono per il controllo sul tecnico. |
| Codice fiscale del legale rappresentante | `cfLegaleRappresentante` | Testo | sì | formato codice fiscale; visibile alle parti esterne |
| Codici fiscali di titolari, amministratori e soci (separati da virgola) | `cfSoci` | Testo lungo |  |  |

### Abilita il tecnico sulla domanda (`isi-abilita-tecnico`)

Collega lo studio del tecnico indicato dall'impresa: da quel momento il tecnico vede questa domanda e compila la perizia. Se lo studio non c'è, crealo in Amministrazione → Organizzazioni.

_usato in: Abilita il tecnico sulla domanda_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Tecnico | `nomeTecnico` | Testo |  | sola lettura; visibile alle parti esterne |
| Codice fiscale | `cfTecnico` | Testo |  | sola lettura; visibile alle parti esterne |
| Ordine o collegio | `ordineTecnico` | Testo |  | sola lettura; visibile alle parti esterne |
| Numero di iscrizione | `numeroIscrizioneTecnico` | Testo |  | sola lettura; visibile alle parti esterne |
| Email o PEC | `emailTecnico` | Testo |  | sola lettura; visibile alle parti esterne |
| Studio del tecnico | `studioTecnico` | Organizzazione | sì | diventa la parte `tecnico` della pratica |

### Compila e firma la perizia asseverata (`isi-perizia`)

Riprende in sola lettura i dati della domanda registrata. Allega la perizia firmata digitalmente (PDF o P7M): dopo la conferma non si modifica più.

_usato in: Compila e firma la perizia; si caricano documenti (almeno uno obbligatorio); i documenti li vedono anche le parti esterne_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Descrizione del progetto | `descrizioneProgetto` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Lavorazione e rischio | `lavorazioneRischio` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Modello di perizia | `modelloPerizia` | Testo |  | sola lettura; visibile alle parti esterne |
| **Perizia** | | sezione | |  |
| Situazione prima dell'intervento | `situazioneAnte` | Testo lungo | sì | visibile alle parti esterne |
| Situazione dopo l'intervento | `situazionePost` | Testo lungo | sì | visibile alle parti esterne |
| Data certa del DVR che attesta il rischio | `dataCertaDvr` | Data | sì | massimo oggi; visibile alle parti esterne |
| Attesto la conformità dei preventivi e dei listini | `conformitaPreventivi` | Sì/No | sì | visibile alle parti esterne |
| Confronto tra bene nuovo e bene sostituito (R-ALL-13) | `confrontoBeni` | Testo lungo |  | visibile alle parti esterne; Per ogni macchina: tipo, prestazioni, scostamento e motivazione se oltre i limiti. |
| La perizia allegata è firmata digitalmente da me | `periziaFirmata` | Sì/No | sì | visibile alle parti esterne |

### Presenta l'istanza di revoca della decadenza (`isi-revoca`)

Hai 5 giorni dalla comunicazione di decadenza. Spiega perché e allega i documenti.

_usato in: Presenta l'istanza di revoca; si caricano documenti; i documenti li vedono anche le parti esterne_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Motivi dell'istanza | `motivazioneRevoca` | Testo lungo | sì | visibile alle parti esterne |

### Valuta l'istanza di revoca della decadenza (`isi-valuta-revoca`)

_usato in: Valuta l'istanza di revoca; documenti in sola lettura_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Motivi dell'impresa | `motivazioneRevoca` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Esito | `esitoRevoca` | Scelta | sì | scelte: Accolta, Respinta; visibile alle parti esterne |
| Motivazione | `noteRevoca` | Testo lungo | sì | visibile alle parti esterne |

### Svolgi l'istruttoria (`isi-istruttoria`)

Verifica requisiti, documenti e perizia. Puoi correggere i criteri di punteggio: con «Ricalcola» il sistema aggiorna punteggio e contributo con le regole dell'annualità.

_usato in: Svolgi l'istruttoria; documenti in sola lettura_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Regione | `regione` | Testo |  | sola lettura |
| Termine dell'istruttoria | `scadenzaIstruttoria` | Testo |  | sola lettura |
| Giorni rimanenti | `giorniRimanenti` | Testo |  | sola lettura |
| Punteggio per criterio | `dettaglioPunteggio` | Testo lungo |  | sola lettura |
| Importi | `dettaglioImporti` | Testo lungo |  | sola lettura |
| Controlli del calcolo | `erroriBloccanti` | Testo lungo |  | sola lettura |
| **Verifiche** | | sezione | |  |
| Requisiti dichiarati verificati | `verificaRequisiti` | Sì/No |  |  |
| Documenti verificati | `verificaDocumenti` | Sì/No |  |  |
| Perizia o dichiarazione B1.2 verificata | `verificaPerizia` | Sì/No |  |  |
| **Criteri di punteggio** | | sezione | | Correggi solo i valori non confermati dai documenti. |
| Tasso medio di tariffa INAIL (‰) | `tassoMedio` | Numero |  | minimo 0; massimo 1000 |
| Condivisione con le parti sociali | `partiSociali` | Scelta |  | scelte: Nessuna, Ente bilaterale o Organismo paritetico, Due o più parti sociali |
| Informativa o condivisione con RLS/RLST | `rls` | Sì/No |  |  |
| Certificazione ISO 45001 | `certIso45001` | Sì/No |  |  |
| Certificazione ISO 14001 | `certIso14001` | Sì/No |  |  |
| Registrazione EMAS | `certEmas` | Sì/No |  |  |
| Certificazione ISO 39001 | `certIso39001` | Sì/No |  |  |
| MOG asseverato | `certMog` | Sì/No |  |  |
| Iscrizione RLAQ | `certRlaq` | Sì/No |  |  |
| **Esito** | | sezione | |  |
| Cosa fai | `esitoIstruttoria` | Scelta | sì | scelte: Ricalcola, Ammetti, Ammetti parzialmente, Richiedi integrazione, Preavviso di rigetto, Respingi; visibile alle parti esterne; Se il punteggio ricalcolato è sotto 130 non si può ammettere: usa il preavviso di rigetto. |
| Contributo ammesso (€) | `importoAmmesso` | Numero | sì | minimo 0; solo se `esitoIstruttoria` è "Ammetti parzialmente"; visibile alle parti esterne |
| Cosa chiedi all'impresa | `richiestaIstruttoria` | Testo lungo | sì | solo se `esitoIstruttoria` è "Richiedi integrazione" o "Preavviso di rigetto"; visibile alle parti esterne |
| Motivazione | `motivazioneIstruttoria` | Testo lungo | sì | solo se `esitoIstruttoria` è "Ammetti parzialmente" o "Respingi"; visibile alle parti esterne |

### Rispondi alla richiesta di integrazione (`isi-integrazione`)

Hai 30 giorni: scaduto il termine il caricamento si chiude e l'istruttoria riprende.

_usato in: Rispondi alla richiesta di integrazione; si caricano documenti; i documenti li vedono anche le parti esterne_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Cosa chiede la sede INAIL | `richiestaIstruttoria` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| La tua risposta | `rispostaIntegrazione` | Testo lungo | sì | visibile alle parti esterne |

### Presenta le osservazioni al preavviso di rigetto (`isi-osservazioni`)

Hai 10 giorni per presentare osservazioni e documenti (art. 10-bis L. 241/1990).

_usato in: Presenta le osservazioni; si caricano documenti; i documenti li vedono anche le parti esterne_

| Campo | Nome | Tipo | Obbl. | Regole e note |
|---|---|---|---|---|
| Numero della domanda | `numeroDomanda` | Testo |  | sola lettura; visibile alle parti esterne |
| Impresa | `ragioneSociale` | Testo |  | sola lettura; visibile alle parti esterne |
| Asse | `asse` | Testo |  | sola lettura; visibile alle parti esterne |
| Progetto | `titoloProgetto` | Testo |  | sola lettura; visibile alle parti esterne |
| Punteggio e contributo | `riepilogo` | Testo |  | sola lettura; visibile alle parti esterne |
| Motivi del preavviso | `richiestaIstruttoria` | Testo lungo |  | sola lettura; visibile alle parti esterne |
| Le tue osservazioni | `osservazioni` | Testo lungo | sì | visibile alle parti esterne |

## Chi avvia, vede ed esporta le pratiche

| Azione | Chi | Ambito |
|---|---|---|
| AVVIA | gruppo esterni | tutte le pratiche |
| VEDE | gruppo direzione-isi | tutte le pratiche |
| VEDE | gruppo supporto-isi | tutte le pratiche |
| ESPORTA | gruppo direzione-isi | tutte le pratiche |

## Da predisporre nell'installazione del cliente

- Gruppi (locali o dal provider di identità): `${gruppoSede}`, `supporto-isi`
- Credenziali dei servizi esterni (`app.credenziali` o variabili d'ambiente): `servizio-isi`
- Server email (`spring.mail.host`): senza, le email restano "non inviate" e la pratica prosegue
- Chiave API con permesso `messaggi:invia` per ogni sistema che invia messaggi
- I timer che leggono una data dai dati della pratica richiedono che il dato sia sempre compilato
- Calendario delle festività aggiornato (Amministrazione → Calendario) se il processo usa giorni lavorativi o proroghe
