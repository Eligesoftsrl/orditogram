/** Regole comuni dei grafici della scrivania: altezze, etichette delle date, fasce di avanzamento. */

const mese = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'short' })
const settimanaGiorno = new Intl.DateTimeFormat('it-IT', { weekday: 'short', day: 'numeric' })

/** Altezza in pixel di una barra: proporzionale, con 3px minimi se il valore non è zero (resta visibile). */
export function altezzaBarra(valore, massimo, pixel) {
  if (!valore || !massimo) return '0px'
  return `${Math.max(3, Math.round((valore / massimo) * pixel))}px`
}

/** "3 nov" dalla data ISO di un lunedì. */
export function etichettaSettimana(iso) {
  return mese.format(new Date(iso + 'T12:00:00'))
}

/** "oggi", "domani", poi "gio 20". */
export function etichettaGiorno(iso, indice) {
  if (indice === 0) return 'oggi'
  if (indice === 1) return 'domani'
  return settimanaGiorno.format(new Date(iso + 'T12:00:00'))
}

/** Le quattro fasce di avanzamento nell'ordine, con l'etichetta da mostrare. */
export function fasceAvanzamento(a = {}) {
  return [
    { etichetta: '0–25%', n: a.da0a25 || 0 },
    { etichetta: '25–50%', n: a.da25a50 || 0 },
    { etichetta: '50–75%', n: a.da50a75 || 0 },
    { etichetta: '75–95%', n: a.da75a100 || 0 }
  ]
}
