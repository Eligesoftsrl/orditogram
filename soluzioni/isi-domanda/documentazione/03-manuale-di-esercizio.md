# Bando ISI: manuale di esercizio

## 1. Cosa si installa

1. **Orditogram 0.13** (vedi il README di Orditogram: Docker o Java 21 + PostgreSQL).
2. **Servizio ISI** (`servizio-isi/`): un'applicazione Spring Boot 3.5 su Java 21, come Orditogram. Si costruisce
   con `./mvnw verify` (compila e fa girare i 37 test) e si avvia come un jar. I registri del bando stanno in
   PostgreSQL (lo stesso server di Orditogram, con un database suo); lo schema lo crea Flyway al primo avvio.
3. I pacchetti `isiDomanda.orditogram.zip` e `isiElenchi.orditogram.zip`.

## 2. Servizio ISI

```bash
cd servizio-isi
./mvnw -B verify                       # target/servizio-isi.jar
psql postgres -c "CREATE USER isi WITH PASSWORD '<password>';"
psql postgres -c "CREATE DATABASE isi OWNER isi;"

ISI_TOKEN=<segreto lungo> \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost/isi SPRING_DATASOURCE_USERNAME=isi SPRING_DATASOURCE_PASSWORD=<password> \
ORDITOGRAM_URL=https://orditogram.ente.it ORDITOGRAM_CHIAVE=<chiave API con messaggi:invia> \
java -jar target/servizio-isi.jar
```

Va avviato dalla cartella `servizio-isi/` (legge `configurazione/` e `dati-prova/` da lì) oppure con
`ISI_CONFIGURAZIONE` e `ISI_DATI_PROVA` che puntano alle cartelle. Come servizio di sistema si usa un'unità
systemd con le stesse variabili.

| Variabile | A cosa serve |
|---|---|
| `ISI_TOKEN` | segreto che Orditogram manda come `Authorization: Bearer`; senza, il servizio risponde a chiunque (solo sviluppo) |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | database dei registri; senza, un file H2 in `dati/` (solo sviluppo e collaudo) |
| `ORDITOGRAM_URL`, `ORDITOGRAM_CHIAVE` | per avvisare le pratiche dell'esito degli elenchi (messaggio `esitoElenco`); la chiave API va creata in Studio → Integrazioni con l'ambito `messaggi:invia` |
| `ISI_ANNUALITA` | annualità delle regole (`configurazione/<anno>.json`), predefinita 2025 |
| `ISI_PORTA`, `ISI_INDIRIZZO` | porta (8095) e indirizzo di ascolto (127.0.0.1) |
| `ISI_CALENDARIO_LIBERO=1` | **solo collaudo**: lo sportello accetta invii a qualsiasi ora |
| `ISI_CALENDARIO_FILE` | **solo collaudo**: un calendario con date diverse da quelle dell'annualità |

Il servizio ascolta solo su `127.0.0.1`: va sulla stessa macchina di Orditogram o dietro un proxy HTTPS. Il
database dei registri va nel backup insieme a quello di Orditogram. I log hanno una riga JSON per richiesta
(logger `richieste`); gli errori imprevisti rispondono con il solo codice `E-…`, il dettaglio è nel log.

### Regole dell'annualità
Tutte le tabelle del capitolo 9 sono in `servizio-isi/configurazione/2025.json`: soglie e parametri, assi, fasce di
dimensione e tasso, tipologie, misure e fattori dell'Asse 5, ATECO dell'Asse 4, bonus regionali, macrosettori e
valore parametrico dell'Asse 1.2, dichiarazioni, documenti, stanziamenti per Regione e Asse, sedi per Regione,
calendario, festività. Per una nuova annualità copia il file (`2026.json`), cambia `annualita` e `versioneRegole`, e
avvia il servizio con `ISI_ANNUALITA=2026`. Dopo l'apertura della compilazione una correzione va fatta cambiando
`versioneRegole`: le domande registrate tengono la versione della loro fotografia.

## 3. Orditogram

- **Credenziale** del servizio: `app.credenziali.servizio-isi.tipo=bearer`, `app.credenziali.servizio-isi.token=<ISI_TOKEN>`.
- **Indirizzo** del servizio nei BPMN: `http://localhost:8095`; se è diverso, cambialo nello Studio (attività
  automatiche) prima di pubblicare.
- **Gruppi**: `sede-<regione>` per ogni Regione della configurazione (`sedi`), `sede-nazionale`, `supporto-isi`,
  `direzione-isi`. Le imprese e i tecnici sono utenti esterni; ogni studio tecnico è un'organizzazione esterna.
- **Posta**: `spring.mail.*` con la casella PEC dell'ente; senza, le comunicazioni restano "non inviate" nello storico.
- **Calendario**: festività nazionali di `app.calendario` (i termini di 5, 10 e 30 giorni slittano se scadono di festivo).
- **Produzione**: `app.demo.utenti: false`, `app.demo.servizi: false`, profilo `produzione`.

## 4. Installare la soluzione

Studio → Processi → **Importa pacchetto**: prima `isiDomanda.orditogram.zip`, poi `isiElenchi.orditogram.zip`;
controlla "Pronto da pubblicare" e **Pubblica**. In alternativa
`python3 .claude/skills/orditogram-soluzione/script/installa.py soluzioni/isi-domanda --url … --admin …`.

## 5. Problemi frequenti

| Sintomo | Causa | Cosa fare |
|---|---|---|
| Nuova domanda: "Leggi il calendario del bando non è riuscita" | servizio ISI spento o credenziale sbagliata | avvia il servizio, controlla `ISI_TOKEN` e la credenziale |
| Nello storico "Servizio ISI non disponibile" | il servizio non ha risposto | la domanda torna all'attività precedente: riprovare quando il servizio è attivo |
| Una pratica resta ferma dopo la pubblicazione di un elenco | il messaggio non è arrivato | controlla `ORDITOGRAM_URL` e la chiave; si può reinviare con `POST /api/messaggi/esitoElenco` |
| Le PEC risultano "non inviate" | posta non configurata | `spring.mail.*` |
