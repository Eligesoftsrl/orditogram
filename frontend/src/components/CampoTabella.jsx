import { Plus, Trash2 } from 'lucide-react'
import { cx, giorno } from '../lib/format.js'
import { MAX_RIGHE } from '../lib/formRegole.js'
import { Button } from './ui.jsx'

/**
 * Tabella di righe da compilare: { type: "tabella", colonne: [...], minRighe, maxRighe }.
 * Il valore è un elenco di oggetti { colonna: valore }; le righe lasciate in bianco si tolgono all'invio.
 */
export const rigaVuota = (f) => Object.fromEntries((f.colonne || []).map(c => [c.name, c.type === 'checkbox' ? false : '']))

/** Righe iniziali: quelle già salvate, altrimenti tante righe vuote quante ne servono (almeno una). */
export function righeIniziali(f, valore) {
  if (Array.isArray(valore) && valore.length > 0) return valore.map(r => ({ ...rigaVuota(f), ...r }))
  const quante = Math.max(1, Math.min(Number(f.minRighe) || 1, 20))
  return Array.from({ length: quante }, () => rigaVuota(f))
}

export function CampoTabella({ f, value, onChange, disabled, errore }) {
  const id = `campo-${f.name}`
  const idErrore = `${id}-errore`
  const righe = Array.isArray(value) ? value : []
  const colonne = f.colonne || []
  const massimo = Math.min(Number(f.maxRighe) || MAX_RIGHE, MAX_RIGHE)
  const cambia = (i, nome, v) => onChange(righe.map((r, j) => j === i ? { ...r, [nome]: v } : r))
  const togli = (i) => onChange(righe.length > 1 ? righe.filter((_, j) => j !== i) : [rigaVuota(f)])
  if (errore) errore = errore[0].toUpperCase() + errore.slice(1)

  return (
    <fieldset className="sm:col-span-2" aria-describedby={errore ? idErrore : undefined}>
      <legend className="etichetta">{f.label || f.name}{f.required && <span className="text-mattone-600"> *</span>}</legend>
      {f.help && <p className="-mt-1 mb-2 text-[13px] text-grafite">{f.help}</p>}
      <div className={cx('overflow-x-auto rounded-campo border bg-white', errore ? 'border-mattone-600' : 'border-linea')}>
        <table className="w-full min-w-[480px] text-[15px]">
          <thead className="bg-carta text-left text-[13px] text-grafite">
            <tr>
              <th scope="col" className="w-10 px-2 py-2 font-medium">N.</th>
              {colonne.map(c => (
                <th key={c.name} scope="col" className="px-2 py-2 font-medium">
                  {c.label || c.name}{c.required && <span className="text-mattone-600"> *</span>}
                </th>
              ))}
              <th scope="col" className="w-10 px-2 py-2"><span className="sr-only">Azioni</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-linea">
            {righe.map((r, i) => (
              <tr key={i}>
                <td className="numeri px-2 py-1.5 text-[13px] text-grafite">{i + 1}</td>
                {colonne.map((c, k) => (
                  <td key={c.name} className="px-1.5 py-1.5">
                    <Cella c={c} value={r[c.name]} disabled={disabled} onChange={v => cambia(i, c.name, v)}
                           id={i === 0 && k === 0 ? id : undefined} etichetta={`${c.label || c.name}, riga ${i + 1}`} />
                  </td>
                ))}
                <td className="px-1 py-1.5 text-right">
                  {!disabled && (
                    <button type="button" onClick={() => togli(i)} aria-label={`Togli la riga ${i + 1}`}
                            className="rounded p-1.5 text-grafite hover:bg-carta hover:text-mattone-600">
                      <Trash2 className="size-4" />
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {errore && <p id={idErrore} role="alert" className="mt-1 text-[13px] font-medium text-mattone-600">{errore}</p>}
      {!disabled && (
        <div className="mt-2 flex items-center gap-3">
          <Button type="button" misura="sm" icona={Plus} disabled={righe.length >= massimo}
                  onClick={() => onChange([...righe, rigaVuota(f)])}>
            Aggiungi riga
          </Button>
          {f.maxRighe && <span className="text-[13px] text-grafite">al massimo {massimo} {massimo === 1 ? 'riga' : 'righe'}</span>}
        </div>
      )}
    </fieldset>
  )
}

function Cella({ c, value, onChange, disabled, id, etichetta }) {
  const comune = { id, disabled, 'aria-label': etichetta }
  const oggi = () => new Date().toLocaleDateString('sv-SE')
  if (c.type === 'checkbox') {
    return <input type="checkbox" {...comune} checked={value === true} onChange={e => onChange(e.target.checked)} className="ml-2 size-4 accent-verde-600" />
  }
  if (c.type === 'select') {
    return (
      <select {...comune} className="campo h-9 min-w-28 py-1" value={value ?? ''} onChange={e => onChange(e.target.value)}>
        <option value="">Scegli…</option>
        {(c.options || []).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    )
  }
  const limiti = c.type === 'number' || c.type === 'date'
    ? { min: c.min === 'oggi' ? oggi() : c.min, max: c.max === 'oggi' ? oggi() : c.max }
    : { maxLength: c.maxLunghezza || undefined }
  return (
    <input {...comune} className="campo h-9 py-1" value={value ?? ''} onChange={e => onChange(e.target.value)}
           type={c.type === 'number' ? 'number' : c.type === 'date' ? 'date' : 'text'} step={c.type === 'number' ? 'any' : undefined}
           placeholder={c.placeholder} {...limiti} />
  )
}

/** Valore di una cella da leggere. */
export function testoCella(c, v) {
  if (v === undefined || v === null || v === '') return ''
  if ((c.type || 'text') === 'checkbox') return v === true ? 'Sì' : 'No'
  if (c.type === 'date') return giorno(v)
  if (c.type === 'number') return String(v).replace('.', ',')
  return String(v)
}

/** Tabella in sola lettura: riepilogo di un'attività e scheda della pratica. */
export function TabellaLettura({ colonne = [], righe = [] }) {
  if (!Array.isArray(righe) || righe.length === 0) return <span className="text-grafite">Nessuna riga</span>
  return (
    <div className="mt-1 overflow-x-auto rounded-campo border border-linea bg-white">
      <table className="w-full text-[14px]">
        <thead className="bg-carta text-left text-[13px] text-grafite">
          <tr>
            <th scope="col" className="w-10 px-2.5 py-1.5 font-medium">N.</th>
            {colonne.map(c => <th key={c.name} scope="col" className={cx('px-2.5 py-1.5 font-medium', c.type === 'number' && 'text-right')}>{c.label || c.name}</th>)}
          </tr>
        </thead>
        <tbody className="divide-y divide-linea">
          {righe.map((r, i) => (
            <tr key={i}>
              <td className="numeri px-2.5 py-1.5 text-grafite">{i + 1}</td>
              {colonne.map(c => (
                <td key={c.name} className={cx('px-2.5 py-1.5 text-pino-900', c.type === 'number' && 'numeri text-right')}>
                  {testoCella(c, r?.[c.name])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
