/**
 * Condizioni dei gateway: dalla scelta "campo / operatore / valore" all'espressione per Activiti e ritorno.
 * Le espressioni che non seguono questi schemi restano modificabili come testo ("avanzata").
 */

export const OPERATORI = {
  testo:   [['==', 'è uguale a'], ['!=', 'è diverso da'], ['pieno', 'è compilato'], ['vuoto', 'non è compilato']],
  numero:  [['==', 'è uguale a'], ['!=', 'è diverso da'], ['>', 'è maggiore di'], ['>=', 'è almeno'], ['<', 'è minore di'], ['<=', 'è al massimo']],
  scelta:  [['==', 'è'], ['!=', 'non è']],
  sino:    [['vero', 'è Sì'], ['falso', 'è No']]
}

export function famiglia(tipo) {
  if (tipo === 'number') return 'numero'
  if (tipo === 'select') return 'scelta'
  if (tipo === 'checkbox') return 'sino'
  return 'testo'
}

const escape = (s) => String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'")

/** { campo, operatore, valore, tipo } → "${...}" */
export function componi({ campo, operatore, valore, tipo }) {
  if (!campo || !operatore) return ''
  if (operatore === 'pieno') return `\${not empty ${campo}}`
  if (operatore === 'vuoto') return `\${empty ${campo}}`
  if (operatore === 'vero') return `\${${campo} == true}`
  if (operatore === 'falso') return `\${${campo} == false}`
  if (tipo === 'number' && valore !== '' && !Number.isNaN(Number(valore))) return `\${${campo} ${operatore} ${Number(valore)}}`
  return `\${${campo} ${operatore} '${escape(valore ?? '')}'}`
}

/** "${...}" → { campo, operatore, valore } oppure null se l'espressione è "avanzata" */
export function scomponi(expr) {
  if (!expr) return null
  const e = expr.trim()
  let m = /^\$\{\s*not\s+empty\s+([a-zA-Z_]\w*)\s*\}$/.exec(e)
  if (m) return { campo: m[1], operatore: 'pieno', valore: '' }
  m = /^\$\{\s*empty\s+([a-zA-Z_]\w*)\s*\}$/.exec(e)
  if (m) return { campo: m[1], operatore: 'vuoto', valore: '' }
  m = /^\$\{\s*([a-zA-Z_]\w*)\s*==\s*(true|false)\s*\}$/.exec(e)
  if (m) return { campo: m[1], operatore: m[2] === 'true' ? 'vero' : 'falso', valore: '' }
  m = /^\$\{\s*([a-zA-Z_]\w*)\s*(==|!=|>=|<=|>|<)\s*'((?:[^'\\]|\\.)*)'\s*\}$/.exec(e)
  if (m) return { campo: m[1], operatore: m[2], valore: m[3].replace(/\\(.)/g, '$1') }
  m = /^\$\{\s*([a-zA-Z_]\w*)\s*(==|!=|>=|<=|>|<)\s*(-?\d+(?:\.\d+)?)\s*\}$/.exec(e)
  if (m) return { campo: m[1], operatore: m[2], valore: m[3] }
  return null
}

/** Frase leggibile: "Esito della verifica è Accettata". */
export function descrivi(expr, campi) {
  const c = scomponi(expr)
  if (!c) return expr ? 'Condizione avanzata' : ''
  const campo = campi.find(x => x.name === c.campo)
  const etichetta = campo?.label || c.campo
  // la parola giusta per il tipo di campo ("è" per le scelte, "è uguale a" per i testi)
  const op = (OPERATORI[famiglia(campo?.type)] || []).find(([k]) => k === c.operatore)?.[1]
    || Object.values(OPERATORI).flat().find(([k]) => k === c.operatore)?.[1] || c.operatore
  return ['pieno', 'vuoto', 'vero', 'falso'].includes(c.operatore) ? `${etichetta} ${op}` : `${etichetta} ${op} ${c.valore}`
}
