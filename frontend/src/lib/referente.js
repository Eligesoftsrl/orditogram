/**
 * Regole del portale "La mia organizzazione", allineate con il backend (accesso/ReferentiService e
 * user/PasswordController): qui servono solo a mostrare i pulsanti giusti e a dare l'errore prima dell'invio;
 * il server controlla comunque tutto.
 */

/** Lunghezza minima della password, come PasswordController.MINIMO. */
export const MINIMO_PASSWORD = 12

/** Cosa può fare il referente su una persona: nulla su sé stesso e sugli altri referenti. */
export function azioniPersona(p) {
  if (!p || p.io || p.referente) return { attiva: false, togli: false }
  // chi è anche in un'altra organizzazione si può solo togliere da questa
  return { attiva: !p.altreOrganizzazioni, togli: true }
}

/** Etichetta dello stato di una persona. */
export function statoPersona(p) {
  if (!p.attivo) return { testo: 'Disattivata', tono: 'mattone' }
  if (p.inAttesa) return { testo: 'Non ancora entrata', tono: 'zafferano' }
  return { testo: 'Attiva', tono: 'verde' }
}

/** Primo errore della nuova password, o stringa vuota. */
export function erroreNuovaPassword(nuova, conferma, username, attuale) {
  if (!nuova || nuova.length < MINIMO_PASSWORD) return `Almeno ${MINIMO_PASSWORD} caratteri`
  if (nuova.length > 128) return 'Al massimo 128 caratteri'
  if (attuale && nuova === attuale) return 'Deve essere diversa da quella attuale'
  if (username && nuova.toLowerCase().includes(username.toLowerCase())) return 'Non può contenere il nome utente'
  if (nuova !== conferma) return 'Le due password non coincidono'
  return ''
}
