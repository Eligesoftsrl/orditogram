/**
 * Timer del BPMN in linguaggio semplice.
 *   dopo    → timeDuration  P30D, PT4H, P2W, P1M
 *   data    → timeDate      ${campoData}
 *   ripeti  → timeCycle     R/P2D (senza limite) o R3/P2D (al massimo 3 volte)
 */

export const UNITA = [
  ['minuti', 'minuti', 'minuto'],
  ['ore', 'ore', 'ora'],
  ['giorni', 'giorni', 'giorno'],
  ['settimane', 'settimane', 'settimana'],
  ['mesi', 'mesi', 'mese']
]

const A_ISO = { minuti: ['PT', 'M'], ore: ['PT', 'H'], giorni: ['P', 'D'], settimane: ['P', 'W'], mesi: ['P', 'M'] }

export function durata(n, unita) {
  const [pre, suf] = A_ISO[unita] || A_ISO.giorni
  return `${pre}${Math.max(1, Number.parseInt(n, 10) || 1)}${suf}`
}

/** "P30D" → { n: 30, unita: 'giorni' }; null se non è una durata semplice. */
export function leggiDurata(s) {
  const m = /^P(?:(\d+)([DWMY])|T(\d+)([HMS]))$/.exec((s || '').trim())
  if (!m) return null
  if (m[1]) return { n: +m[1], unita: { D: 'giorni', W: 'settimane', M: 'mesi', Y: 'anni' }[m[2]] }
  if (m[4] === 'S') return null
  return { n: +m[3], unita: m[4] === 'H' ? 'ore' : 'minuti' }
}

/** Legge la definizione del timer (bpmn:TimerEventDefinition). */
export function scomponiTimer(def) {
  const calendario = def?.businessCalendarName || ''
  const t = scomponiBase(def)
  return t.modo === 'dopo' || t.modo === 'data' ? { ...t, calendario } : t
}

export const CALENDARI = {
  '': 'giorni di calendario',
  lavorativi: 'giorni lavorativi',
  proroga: 'di calendario, prorogato se cade in un giorno non lavorativo'
}

function scomponiBase(def) {
  const dur = def?.timeDuration?.body, data = def?.timeDate?.body, ciclo = def?.timeCycle?.body
  if (dur) {
    const d = leggiDurata(dur)
    return d && d.unita !== 'anni' ? { modo: 'dopo', ...d } : { modo: 'avanzato', tipo: 'timeDuration', testo: dur }
  }
  if (data) {
    const m = /^\$\{([a-zA-Z][\w-]*)}$/.exec(data.trim())
    return m ? { modo: 'data', campo: m[1] } : { modo: 'avanzato', tipo: 'timeDate', testo: data }
  }
  if (ciclo) {
    const m = /^R(\d*)\/(P\S+)$/.exec(ciclo.trim())
    const d = m && leggiDurata(m[2])
    return d && d.unita !== 'anni'
      ? { modo: 'ripeti', ...d, volte: m[1] ? +m[1] : null }
      : { modo: 'avanzato', tipo: 'timeCycle', testo: ciclo }
  }
  return { modo: 'vuoto' }
}

/** { tipo: 'timeDuration'|'timeDate'|'timeCycle', body } */
export function componiTimer(t) {
  const calendario = t.calendario || undefined
  if (t.modo === 'dopo') return { tipo: 'timeDuration', body: durata(t.n, t.unita), calendario: t.unita !== 'giorni' && calendario === 'lavorativi' ? 'proroga' : calendario }
  if (t.modo === 'data') return { tipo: 'timeDate', body: t.campo ? '${' + t.campo + '}' : '', calendario }
  if (t.modo === 'ripeti') return { tipo: 'timeCycle', body: `R${t.volte ? t.volte : ''}/${durata(t.n, t.unita)}` }
  return { tipo: t.tipo, body: t.testo }
}

export function quantita(n, unita) {
  const u = UNITA.find(x => x[0] === unita)
  return `${n} ${u ? (n === 1 ? u[2] : u[1]) : unita}`
}

/** Descrizione breve: "dopo 30 giorni", "ogni 5 giorni, al massimo 3 volte", "alla data in Scadenza". */
export function descriviTimer(def, campi = []) {
  const t = scomponiTimer(def)
  if (t.modo === 'dopo') {
    if (t.calendario === 'lavorativi' && t.unita === 'giorni') return `dopo ${t.n} ${t.n === 1 ? 'giorno lavorativo' : 'giorni lavorativi'}`
    return `dopo ${quantita(t.n, t.unita)}${t.calendario === 'proroga' || t.calendario === 'lavorativi' ? ' (se cade in un festivo, slitta al primo giorno lavorativo)' : ''}`
  }
  if (t.modo === 'ripeti') return `ogni ${t.n === 1 ? UNITA.find(x => x[0] === t.unita)?.[2] || t.unita : quantita(t.n, t.unita)}${t.volte ? `, al massimo ${t.volte} ${t.volte === 1 ? 'volta' : 'volte'}` : ''}`
  if (t.modo === 'data') return `alla data in "${campi.find(c => c.name === t.campo)?.label || t.campo}"${t.calendario ? ' (se festiva, il primo giorno lavorativo dopo)' : ''}`
  if (t.modo === 'avanzato') return t.testo
  return 'tempo da impostare'
}

/** Che tipo di evento di bordo è (per etichette e pannello). */
export function tipoBordo(bo) {
  const def = bo?.eventDefinitions?.[0]
  if (!def) return null
  if (def.$type === 'bpmn:ErrorEventDefinition') return 'errore'
  if (def.$type === 'bpmn:MessageEventDefinition') return 'messaggio'
  if (def.$type === 'bpmn:TimerEventDefinition') return bo.cancelActivity === false ? 'sollecito' : 'scadenza'
  return 'altro'
}
