# Reclamo con controparte

Processo dimostrativo per gli **utenti esterni** e le **parti della pratica**: un cittadino presenta un reclamo dal
portale, l'ufficio lo istruisce e indica l'organizzazione interessata, gli utenti di quell'organizzazione rispondono,
l'ufficio chiude.

## Il flusso

1. **Reclamo presentato** (form `rc-reclamo`): lo avvia un utente esterno (gruppo automatico `esterni`) o un operatore.
   Chi lo avvia da esterno diventa il *richiedente*. Gli allegati sono visibili alle parti.
2. **Istruttoria** (operatori, form `rc-istruttoria`): il campo Organizzazione *Organizzazione interessata* è la
   parte `controparte`. La *Valutazione interna* è riservata al ruolo auditor e non la vede nessuna parte.
3. **Risposta della controparte** (`candidateGroups="parte:controparte"`): l'attività va agli utenti
   dell'organizzazione scelta, che vedono oggetto, descrizione e richiesta dell'ufficio.
4. **Chiusura del reclamo** (operatori): esito e comunicazione, visibili alle parti.

## Provarlo

Con gli utenti di prova (`app.demo.utenti: true`, password `password`): pubblica il pacchetto
`reclamo-controparte.orditogram.zip` (Studio → Processi → *Importa pacchetto*, poi *Metti in produzione*), quindi
- `marco` (cittadino, utente esterno) presenta il reclamo da *Nuova pratica*;
- `mario` (operatori) lavora l'istruttoria e sceglie *Studio legale Bianchi*;
- `elena` (Studio legale Bianchi, utente esterno) trova l'attività nella sua scrivania e risponde;
- `mario` chiude; `marco` ed `elena` vedono la pratica con i soli dati per le parti.
