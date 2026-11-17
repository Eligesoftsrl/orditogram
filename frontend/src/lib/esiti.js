/**
 * Tono di un'opzione di scelta, dal testo: serve a dare icona e colore di stato alle scelte d'esito
 * ("Accolta", "Respinta", "Serve un'integrazione"). Il colore accompagna sempre la parola, mai da solo.
 */
const NEGATIVO = /(^|\s)(no|non)(?=$|[\s.,!])|\b(respin[tg]|rifiut|rigett|negat|annull|archiviat|chius[ao] senza|inammissibil|irricevibil|diniego|revoc)/i
const ATTESA = /\b(integra|sospe|chiarim|rinvi|parzial|in parte|in attesa|da rivedere|richiedi|richiesta di)/i
const POSITIVO = /(^|\s)(s[iì]|ok)(?=$|[\s.,!])|\b(accolt|accogl|accett|approv|conferm|ammess|ammissibil|favorevol|positiv|concess|rilasci)/i

export function tonoEsito(testo = '') {
  const t = String(testo).trim()
  if (!t) return 'neutro'
  if (NEGATIVO.test(t)) return 'negativo'
  if (ATTESA.test(t)) return 'attesa'
  if (POSITIVO.test(t)) return 'positivo'
  return 'neutro'
}

/** Ha senso mostrare le opzioni come esiti colorati solo se almeno due hanno un tono riconosciuto. */
export function sonoEsiti(opzioni = []) {
  return opzioni.filter(o => tonoEsito(o) !== 'neutro').length >= 2
}
