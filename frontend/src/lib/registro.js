// Etichette del registro: azioni di amministrazione e di sicurezza, letture, origini degli errori.
import { gl } from './glossario.js'

/** Azioni di amministrazione e di sicurezza: [etichetta, tono del Badge]. */
export const AZIONI = {
  PROCESSO_PUBBLICATO: ['Processo pubblicato', 'verde'],
  FORM_SALVATO: ['Form salvato', 'neutro'],
  FORM_ELIMINATO: ['Form eliminato', 'mattone'],
  UTENTE_CREATO: ['Utente creato', 'verde'],
  UTENTE_MODIFICATO: ['Utente modificato', 'neutro'],
  UTENTE_ELIMINATO: ['Utente eliminato', 'mattone'],
  BOZZA_SCARTATA: ['Bozza scartata', 'neutro'],
  PACCHETTO_ESPORTATO: ['Pacchetto esportato', 'neutro'],
  PACCHETTO_IMPORTATO: ['Pacchetto importato', 'zafferano'],
  ACCESSO: ['Accesso', 'neutro'],
  ACCESSO_PROVIDER: ['Accesso con provider', 'neutro'],
  ACCESSO_NEGATO: ['Accesso negato', 'mattone'],
  USCITA: ['Uscita', 'neutro'],
  CHIAVE_API_CREATA: ['Chiave API creata', 'verde'],
  CHIAVE_API_MODIFICATA: ['Chiave API modificata', 'neutro'],
  CHIAVE_API_RUOTATA: ['Chiave API rinnovata', 'zafferano'],
  CHIAVE_API_REVOCATA: ['Chiave API revocata', 'mattone'],
  WEBHOOK_CREATO: ['Webhook creato', 'verde'],
  WEBHOOK_MODIFICATO: ['Webhook modificato', 'neutro'],
  WEBHOOK_ELIMINATO: ['Webhook eliminato', 'mattone'],
  WEBHOOK_SEGRETO_LETTO: ['Segreto webhook letto', 'zafferano'],
  WEBHOOK_SEGRETO_RIGENERATO: ['Segreto webhook rigenerato', 'zafferano']
}

/** Letture registrate con @RegistraLettura nel backend (getter: i testi seguono il glossario). */
export const LETTURE = {
  get PRATICA_APERTA () { return `${gl('pratica', 'Nome')} ${gl.agg('pratica', 'apert')}` },
  ATTIVITA_APERTA: 'Attività aperta',
  DOCUMENTO_SCARICATO: 'Documento scaricato',
  DOCUMENTO_ESTRATTO: 'Testo del documento letto',
  SCHEDA_APERTA: 'Scheda aperta'
}

export const ESITI = { CONSENTITA: ['Consentita', 'verde'], NEGATA: ['Negata', 'mattone'] }
export const ORIGINI = { RICHIESTA: 'Richiesta', MOTORE: 'Motore dei processi' }

export const etichettaAzione = (a) => AZIONI[a]?.[0] || a
export const etichettaLettura = (a) => LETTURE[a] || a

/** Filtri → parametri della richiesta, senza quelli vuoti. */
export const parametri = (filtri) =>
  new URLSearchParams(Object.entries(filtri).filter(([, v]) => v !== '' && v != null).map(([k, v]) => [k, String(v)])).toString()
