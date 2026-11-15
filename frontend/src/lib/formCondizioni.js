/**
 * Sezioni e campi condizionali dei form, con le stesse regole del backend (form/ValoriForm.java).
 *
 *  { "type": "sezione", "label": "Dati del richiedente", "help": "…" }        apre una sezione, non è un dato
 *  "visibileSe": { "campo": "esito", "valori": ["Serve integrazione"] }      il campo si mostra solo se
 *                                                                             "esito" ha uno di quei valori
 * Per un sì/no i valori sono "true" o "false". Un campo nascosto non è obbligatorio e non si invia.
 */

export const SEZIONE = 'sezione'

export const eSezione = (f) => f?.type === SEZIONE

/** La condizione del campo normalizzata ({ campo, valori }) oppure null. */
export function condizione(f) {
  const c = f?.visibileSe
  if (!c || typeof c !== 'object' || !String(c.campo || '').trim()) return null
  const valori = Array.isArray(c.valori) ? c.valori : c.valore !== undefined ? [c.valore] : []
  return { campo: String(c.campo).trim(), valori: valori.map(v => String(v).trim()) }
}

export function soddisfatta(c, valore) {
  if (valore === undefined || valore === null) return false
  const s = String(valore).trim()
  return s !== '' && c.valori.includes(s)
}

/** Nomi dei campi nascosti dalle condizioni, dati i valori; chi dipende da un campo nascosto è nascosto. */
export function campiNascosti(fields = [], valori = {}) {
  const condizioni = fields.map(f => [f.name, condizione(f)]).filter(([n, c]) => n && c)
  const out = new Set()
  let cambiato = true
  while (cambiato) {
    cambiato = false
    for (const [nome, c] of condizioni) {
      if (out.has(nome)) continue
      const v = out.has(c.campo) ? undefined : valori[c.campo]
      if (!soddisfatta(c, v)) { out.add(nome); cambiato = true }
    }
  }
  return out
}

/** I valori da inviare: senza quelli dei campi nascosti. */
export function senzaNascosti(fields, valori, contesto = {}) {
  const nascosti = campiNascosti(fields, { ...contesto, ...valori })
  return Object.fromEntries(Object.entries(valori).filter(([k]) => !nascosti.has(k)))
}

/**
 * I campi raggruppati per sezione: [{ sezione: {label, help} | null, campi: [...] }].
 * I campi prima della prima sezione stanno in un gruppo senza titolo; i gruppi vuoti si tolgono.
 */
export function raggruppa(fields = []) {
  const gruppi = [{ sezione: null, campi: [] }]
  for (const f of fields) {
    if (eSezione(f)) gruppi.push({ sezione: f, campi: [] })
    else gruppi[gruppi.length - 1].campi.push(f)
  }
  return gruppi.filter(g => g.campi.length > 0)
}

/** Valori proponibili per una condizione sul campo indicato (scelte di un select, sì/no di un checkbox). */
export function valoriPossibili(f) {
  if (!f) return []
  if (f.type === 'select') return (f.options || []).map(o => ({ valore: String(o), etichetta: String(o) }))
  if (f.type === 'checkbox') return [{ valore: 'true', etichetta: 'Sì' }, { valore: 'false', etichetta: 'No' }]
  return []
}

/** "Visibile se Esito è Serve integrazione o Da respingere" */
export function descriviCondizione(c, fields = []) {
  if (!c) return ''
  const sorgente = fields.find(f => f.name === c.campo)
  const etichette = c.valori.map(v => valoriPossibili(sorgente).find(p => p.valore === v)?.etichetta ?? v)
  return `visibile se ${sorgente?.label || c.campo} è ${etichette.join(' o ')}`
}
