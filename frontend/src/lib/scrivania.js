/** Regole della scrivania a tessere: quali attività mettere in primo piano e come contare in modo fluido. */

const GIORNO = 86400e3

/** Il momento della scadenza vera di un'attività (non solleciti né attese), o Infinity se non ne ha. */
export function scadenzaDi(t) {
  const s = (t.scadenze || []).find(x => x.tipo === 'scadenza')
  return s ? new Date(s.quando).getTime() : Infinity
}

/**
 * Le attività da mettere in primo piano: prima quelle con la scadenza più vicina (le scadute in testa),
 * poi le più vecchie. Non cambia la lista ricevuta.
 */
export function inPrimoPiano(tasks, quante = 3) {
  return [...tasks]
    .sort((a, b) => scadenzaDi(a) - scadenzaDi(b) || new Date(a.createdDate) - new Date(b.createdDate))
    .slice(0, quante)
}

/** Il tono della pastiglia di un'attività: 'mattone' scaduta, 'zafferano' entro due giorni, altrimenti 'neutro'. */
export function tonoScadenza(t, ora = Date.now()) {
  const q = scadenzaDi(t)
  if (q === Infinity) return 'neutro'
  if (q - ora <= 0) return 'mattone'
  return q - ora < 2 * GIORNO ? 'zafferano' : 'neutro'
}

/**
 * Valore di un contatore che sale da 0 a `fine` in `durata` ms, rallentando alla fine (easeOutCubic).
 * Restituisce sempre un intero; a tempo scaduto restituisce esattamente `fine`.
 */
export function valoreContatore(fine, trascorso, durata = 700) {
  if (!Number.isFinite(fine)) return fine
  if (durata <= 0 || trascorso >= durata) return fine
  const t = Math.max(0, trascorso) / durata
  return Math.round(fine * (1 - Math.pow(1 - t, 3)))
}
