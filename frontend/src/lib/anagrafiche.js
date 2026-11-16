// Anagrafiche configurabili: regole comuni alle pagine delle schede e allo Studio.
import { eSezione } from './formCondizioni.js'

export const AZIONI_ANAGRAFICA = [
  { codice: 'VEDE', titolo: 'Chi consulta le schede', aiuto: 'Cerca e legge le schede, e le sceglie nei form delle pratiche.' },
  { codice: 'MODIFICA', titolo: 'Chi crea e modifica le schede', aiuto: 'Crea, modifica, cessa e importa da CSV; consulta anche.' },
  { codice: 'ESPORTA', titolo: 'Chi esporta le schede', aiuto: 'Scarica tutte le schede in Excel, CSV o PDF; consulta anche.' }
]

export const STATI_SCHEDA = [
  { codice: 'ATTIVA', nome: 'Attive' },
  { codice: 'CESSATA', nome: 'Cessate' },
  { codice: 'TUTTE', nome: 'Tutte' }
]

/** Il suffisso della colonna col nome della scheda collegata (come ImportaEsportaSchede.SUFFISSO_NOME). */
export const SUFFISSO_NOME = ' (nome)'

/** I campi con un dato (senza le sezioni) di una definizione. */
export const campiDati = (definizione) => (definizione?.fields || []).filter(f => !eSezione(f) && f.name)

/**
 * I valori iniziali di una scheda per DynamicForm: i dati più "<campo>_titolo" delle schede collegate,
 * così i campi anagrafica mostrano il nome e non solo il numero.
 */
export function valoriScheda(scheda) {
  const v = { ...(scheda?.dati || {}) }
  Object.entries(scheda?.collegamenti || {}).forEach(([campo, c]) => {
    v[`${campo}_titolo`] = c.visibile && c.titolo ? c.titolo : `Scheda n. ${c.id}`
  })
  return v
}

/** Una tabella di righe come testo leggibile: "Importo: 10, Descrizione: carta | Importo: 5 ...". */
function tabellaInTesto(f, righe) {
  if (!Array.isArray(righe)) return ''
  return righe.map(r => (f.colonne || []).filter(c => r[c.name] != null && r[c.name] !== '')
    .map(c => `${c.label || c.name}: ${c.type === 'checkbox' ? (r[c.name] === true ? 'sì' : 'no') : r[c.name]}`).join(', ')).join(' | ')
}

/**
 * Le colonne dell'esportazione (componente Esporta) di un tipo: una per campo, con l'etichetta come intestazione,
 * così il CSV si reimporta così com'è. Un campo anagrafica dà due colonne: il numero della scheda (che si importa)
 * e il suo nome (che l'importazione ignora). Le tabelle diventano testo.
 */
export function colonneEsportazione(definizione) {
  const out = []
  campiDati(definizione).forEach(f => {
    const titolo = f.label || f.name
    if (f.type === 'anagrafica') {
      out.push({ titolo, valore: r => r.dati?.[f.name] ?? '' })
      out.push({ titolo: titolo + SUFFISSO_NOME, valore: r => r.dati?.[`${f.name}_titolo`] ?? '' })
    } else if (f.type === 'tabella') {
      out.push({ titolo, valore: r => tabellaInTesto(f, r.dati?.[f.name]), larghezza: 60 })
    } else if (f.type === 'checkbox') {
      out.push({ titolo, valore: r => r.dati?.[f.name] === true ? 'sì' : r.dati?.[f.name] === false ? 'no' : '' })
    } else {
      out.push({ titolo, valore: r => r.dati?.[f.name] ?? '' })
    }
  })
  out.push({ titolo: 'Stato', valore: r => r.stato === 'CESSATA' ? 'cessata' : 'attiva' })
  return out
}

/** I campi cambiati tra due versioni di una scheda, con le etichette: per lo storico delle versioni. */
export function campiCambiati(definizione, prima, dopo) {
  const a = prima || {}, b = dopo || {}
  const nomi = new Set([...Object.keys(a), ...Object.keys(b)])
  const etichette = Object.fromEntries(campiDati(definizione).map(f => [f.name, f.label || f.name]))
  return [...nomi].filter(n => JSON.stringify(a[n] ?? null) !== JSON.stringify(b[n] ?? null))
    .map(n => etichette[n] || n)
}

/** "3 nuove, 1 aggiornata, 2 righe scartate". */
export function descriviImportazione(e) {
  const parti = [`${e.create} ${e.create === 1 ? 'nuova' : 'nuove'}`, `${e.aggiornate} ${e.aggiornate === 1 ? 'aggiornata' : 'aggiornate'}`]
  if (e.invariate) parti.push(`${e.invariate} senza cambiamenti`)
  if (e.scartate?.length) parti.push(`${e.scartate.length} ${e.scartate.length === 1 ? 'riga scartata' : 'righe scartate'}`)
  return parti.join(', ')
}
