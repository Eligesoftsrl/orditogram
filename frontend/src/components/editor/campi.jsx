import { useEffect, useRef, useState } from 'react'
import { Braces, Plus, X } from 'lucide-react'
import { cx } from '../../lib/format.js'

/** Campo di testo che salva quando esci dal campo o premi Invio (evita un "annulla" per ogni lettera). */
export function TestoDifferito({ value, onCommit, multiline, placeholder, className, id, mono, rows = 3 }) {
  const [v, setV] = useState(value ?? '')
  useEffect(() => { setV(value ?? '') }, [value])
  const commit = () => { if ((v ?? '') !== (value ?? '')) onCommit(v) }
  const props = {
    id, value: v, placeholder,
    onChange: e => setV(e.target.value),
    onBlur: commit,
    className: cx('campo', mono && 'font-mono text-sm', className)
  }
  return multiline
    ? <textarea rows={rows} {...props} />
    : <input {...props} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); commit(); e.target.blur() } }} />
}

export function Sezione({ titolo, aiuto, children }) {
  return (
    <section className="border-b border-linea px-5 py-4 last:border-0">
      {titolo && <h3 className="text-[15px] font-semibold text-pino-900">{titolo}</h3>}
      {aiuto && <p className="mt-0.5 text-[13px] text-grafite">{aiuto}</p>}
      <div className={cx(titolo && 'mt-3', 'space-y-3')}>{children}</div>
    </section>
  )
}

export function Etichetta({ htmlFor, children }) {
  return <label htmlFor={htmlFor} className="etichetta">{children}</label>
}

/**
 * Scelta di uno o più gruppi tra quelli esistenti (con il numero di utenti), o un gruppo nuovo.
 * valori: array di nomi
 */
export function SceltaGruppi({ valori, gruppi, onChange, vuoto = 'Nessun gruppo scelto' }) {
  const [nuovo, setNuovo] = useState('')
  const aggiungi = (g) => { const v = g.trim(); if (v && !valori.includes(v)) onChange([...valori, v]); setNuovo('') }
  const disponibili = gruppi.filter(g => !valori.includes(g.nome))
  const senzaUtenti = valori.filter(v => !gruppi.find(g => g.nome === v && g.utenti > 0))
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {valori.length === 0 && <span className="text-sm text-nebbia">{vuoto}</span>}
        {valori.map(g => (
          <span key={g} className="inline-flex items-center gap-1 rounded-full bg-verde-50 py-1 pl-3 pr-1.5 text-sm text-verde-700 ring-1 ring-verde-100">
            {g}
            <button type="button" onClick={() => onChange(valori.filter(x => x !== g))} className="rounded-full p-0.5 hover:bg-white" aria-label={`Togli ${g}`}>
              <X className="size-3.5" />
            </button>
          </span>
        ))}
      </div>
      {disponibili.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {disponibili.map(g => (
            <button key={g.nome} type="button" onClick={() => aggiungi(g.nome)}
                    className="rounded-full px-2.5 py-1 text-[13px] text-pino-800 ring-1 ring-linea hover:bg-carta">
              + {g.nome} <span className="text-nebbia">{g.utenti === 1 ? '1 utente' : `${g.utenti} utenti`}</span>
            </button>
          ))}
        </div>
      )}
      <div className="flex gap-2">
        <input className="campo h-9" value={nuovo} placeholder="Nuovo gruppo" onChange={e => setNuovo(e.target.value)}
               onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); aggiungi(nuovo) } }} />
        <button type="button" onClick={() => aggiungi(nuovo)} className="inline-flex h-9 items-center gap-1 rounded-campo border border-linea bg-white px-3 text-sm font-medium hover:bg-carta">
          <Plus className="size-4" /> Aggiungi
        </button>
      </div>
      {senzaUtenti.length > 0 && (
        <p className="text-[13px] text-zafferano-800">
          {senzaUtenti.join(', ')}: nessun utente in questo gruppo. Ricordati di assegnarlo in Studio, Utenti.
        </p>
      )}
    </div>
  )
}

/** Pulsante che inserisce ${dato} in un testo: i dati sono i campi dei form del processo. */
export function InserisciDato({ campi, onInserisci }) {
  const [aperto, setAperto] = useState(false)
  const box = useRef(null)
  useEffect(() => {
    if (!aperto) return
    const fuori = (e) => !box.current?.contains(e.target) && setAperto(false)
    document.addEventListener('mousedown', fuori)
    return () => document.removeEventListener('mousedown', fuori)
  }, [aperto])
  return (
    <div ref={box} className="relative inline-block">
      <button type="button" onClick={() => setAperto(!aperto)}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-verde-700 hover:underline">
        <Braces className="size-3.5" /> Inserisci un dato della pratica
      </button>
      {aperto && (
        <ul className="absolute left-0 z-30 mt-1 max-h-64 w-64 overflow-y-auto rounded-campo border border-linea bg-white py-1 shadow-sollevato">
          {campi.length === 0 && <li className="px-3 py-2 text-sm text-grafite">Nessun dato: collega prima dei form alle attività.</li>}
          {campi.map(c => (
            <li key={c.name}>
              <button type="button" onClick={() => { onInserisci('${' + c.name + '}'); setAperto(false) }}
                      className="flex w-full flex-col px-3 py-1.5 text-left hover:bg-carta">
                <span className="text-sm text-pino-900">{c.label}</span>
                <span className="font-mono text-xs text-grafite">{c.name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
