// Moduli di paese e di settore (backend modulo/Modulo.java, Studio → Moduli). Di base sono tutti accesi: finché
// /api/info non risponde, o se un server più vecchio non manda l'elenco, si considerano accesi.

/** Formati dei campi che richiedono il modulo Italia (uguale a RegoleCampo.FORMATI_ITALIA del backend). */
export const FORMATI_ITALIA = ['codiceFiscale', 'partitaIva', 'cap', 'telefono']

/** Il modulo è acceso in questa installazione. */
export function moduloAcceso(info, codice) {
  const accesi = info?.moduli
  return !Array.isArray(accesi) || accesi.includes(codice)
}

/** I formati da offrire nel costruttore dei form: quelli italiani solo con il modulo Italia (o se il campo lo usa già). */
export function formatiDisponibili(formati, info, attuale) {
  if (moduloAcceso(info, 'italia')) return formati
  return formati.filter(f => !FORMATI_ITALIA.includes(f.id) || f.id === attuale)
}
