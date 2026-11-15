import { BellRing, Hourglass, Timer } from 'lucide-react'
import { fa, quando } from '../lib/format.js'
import { Badge } from './ui.jsx'

const GIORNO = 86400e3

/**
 * Scadenza, sollecito o attesa di una pratica, con il tempo che manca.
 * s: { nome, quando, tipo: 'scadenza' | 'sollecito' | 'attesa' }
 */
export function BadgeScadenza({ s, conNome = true }) {
  if (!s) return null
  const manca = new Date(s.quando).getTime() - Date.now()
  const scaduta = manca <= 0
  const Icona = s.tipo === 'sollecito' ? BellRing : s.tipo === 'attesa' ? Hourglass : Timer
  let tono = 'neutro', testo
  if (s.tipo === 'attesa') {
    testo = scaduta ? 'riprende a breve' : `riprende ${fa(s.quando)}`
  } else if (s.tipo === 'sollecito') {
    testo = scaduta ? 'sollecito in invio' : `sollecito ${fa(s.quando)}`
  } else {
    tono = scaduta ? 'mattone' : manca < 2 * GIORNO ? 'zafferano' : 'verde'
    testo = scaduta ? 'scaduta' : `scade ${fa(s.quando)}`
  }
  const etichetta = conNome && s.nome && !['Scadenza', 'Sollecito', 'Attesa'].includes(s.nome) ? `${s.nome}: ${testo}` : testo
  return (
    <span title={`${s.nome || ''} ${quando(s.quando)}`.trim()}>
      <Badge tono={tono} icona={Icona}>{etichetta}</Badge>
    </span>
  )
}

/** La scadenza da mostrare per prima: la più vicina tra le scadenze vere, altrimenti il primo sollecito. */
export function principale(scadenze = []) {
  return scadenze.find(s => s.tipo === 'scadenza') || scadenze.find(s => s.tipo !== 'attesa') || null
}
