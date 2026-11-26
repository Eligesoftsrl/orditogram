const dataOra = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
const dataOraAnno = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
const soloData = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', year: 'numeric' })
const relativo = new Intl.RelativeTimeFormat('it-IT', { numeric: 'auto' })

/** Una data leggibile o null: un valore che non è una data non deve rompere la pagina. */
function leggi(d) {
  const date = d instanceof Date ? d : new Date(d)
  return Number.isNaN(date.getTime()) ? null : date
}

export function quando(d) {
  if (!d) return ''
  const date = leggi(d)
  if (!date) return String(d)
  return (date.getFullYear() === new Date().getFullYear() ? dataOra : dataOraAnno).format(date)
}

export function giorno(d) {
  if (!d) return ''
  // le date dei form arrivano come "2026-10-03": niente fuso orario
  const date = typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d) ? new Date(d + 'T12:00:00') : leggi(d)
  // un valore che non è una data (es. scritto da un sistema esterno) si mostra com'è, senza rompere la pagina
  return date ? soloData.format(date) : String(d)
}

/** "3 ore fa", "ieri", "tra 2 giorni" */
export function fa(d) {
  if (!d) return ''
  if (!leggi(d)) return String(d)
  const sec = (new Date(d).getTime() - Date.now()) / 1000
  const abs = Math.abs(sec)
  if (abs < 60) return 'adesso'
  if (abs < 3600) return relativo.format(Math.round(sec / 60), 'minute')
  if (abs < 86400) return relativo.format(Math.round(sec / 3600), 'hour')
  if (abs < 86400 * 30) return relativo.format(Math.round(sec / 86400), 'day')
  return quando(d)
}

export function dimensione(byte) {
  if (byte == null) return ''
  if (byte < 1024) return `${byte} B`
  if (byte < 1024 * 1024) return `${(byte / 1024).toFixed(0)} KB`
  return `${(byte / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}

export function iniziali(nome = '') {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('') || '?'
}

export function saluto() {
  const h = new Date().getHours()
  if (h < 13) return 'Buongiorno'
  if (h < 18) return 'Buon pomeriggio'
  return 'Buonasera'
}

export const cx = (...c) => c.filter(Boolean).join(' ')

/** Data e ora complete per le esportazioni: "04/10/2026 09:30". */
export function dataOraCompleta(d) {
  if (!d) return ''
  if (!leggi(d)) return String(d)
  return new Intl.DateTimeFormat('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(d)).replace(',', '')
}
