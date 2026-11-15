import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import { cx } from '../../lib/format.js'

/** Copia negli appunti, con conferma visiva. */
export function Copia({ testo, etichetta = 'Copia', className, scuro = false }) {
  const [fatto, setFatto] = useState(false)
  const copia = async () => {
    try {
      await navigator.clipboard.writeText(testo)
    } catch {
      // senza permesso per gli appunti: selezione manuale
      const t = document.createElement('textarea')
      t.value = testo; document.body.appendChild(t); t.select(); document.execCommand('copy'); t.remove()
    }
    setFatto(true)
    setTimeout(() => setFatto(false), 1600)
  }
  return (
    <button type="button" onClick={copia} aria-label={etichetta}
            className={cx('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium transition-colors',
              scuro ? 'text-pino-200 hover:bg-white/10 hover:text-white' : 'text-grafite hover:bg-carta hover:text-pino-900', className)}>
      {fatto ? <Check className="size-3.5 text-verde-500" /> : <Copy className="size-3.5" />}
      {fatto ? 'Copiato' : etichetta}
    </button>
  )
}

/** Blocco di codice scuro con pulsante Copia. */
export function Codice({ codice, titolo, className, altezza = 'max-h-96' }) {
  return (
    <div className={cx('overflow-hidden rounded-campo bg-pino-950 ring-1 ring-pino-900', className)}>
      <div className="flex items-center justify-between gap-2 border-b border-white/10 px-3 py-1.5">
        <span className="text-xs font-medium uppercase tracking-wide text-pino-300">{titolo}</span>
        <Copia testo={codice} scuro />
      </div>
      <pre className={cx('overflow-auto px-4 py-3 font-mono text-[12.5px] leading-relaxed text-[#E6F1EE]', altezza)}><code>{codice}</code></pre>
    </div>
  )
}

/** Codice in più linguaggi, a schede. voci: { 'cURL': '...', 'JavaScript': '...' } */
export function CodiceASchede({ voci, className }) {
  const nomi = Object.keys(voci)
  const [scelta, setScelta] = useState(nomi[0])
  const attiva = voci[scelta] !== undefined ? scelta : nomi[0]
  return (
    <div className={cx('overflow-hidden rounded-campo bg-pino-950 ring-1 ring-pino-900', className)}>
      <div className="flex items-center justify-between gap-2 border-b border-white/10 px-2">
        <div role="tablist" className="flex min-w-0 overflow-x-auto">
          {nomi.map(n => (
            <button key={n} type="button" role="tab" aria-selected={attiva === n} onClick={() => setScelta(n)}
                    className={cx('-mb-px shrink-0 whitespace-nowrap border-b-2 px-3 py-2 text-xs font-medium transition-colors',
                      attiva === n ? 'border-zafferano-400 text-white' : 'border-transparent text-pino-300 hover:text-white')}>
              {n}
            </button>
          ))}
        </div>
        <Copia testo={voci[attiva]} scuro />
      </div>
      <pre className="max-h-96 overflow-auto px-4 py-3 font-mono text-[12.5px] leading-relaxed text-[#E6F1EE]"><code>{voci[attiva]}</code></pre>
    </div>
  )
}

const COLORI_METODO = {
  GET: 'bg-verde-50 text-verde-700 ring-verde-100',
  POST: 'bg-zafferano-50 text-zafferano-800 ring-zafferano-100',
  PUT: 'bg-pino-900/5 text-pino-800 ring-linea',
  DELETE: 'bg-mattone-50 text-mattone-700 ring-mattone-100'
}
export function Metodo({ metodo, className }) {
  return (
    <span className={cx('inline-flex w-12 shrink-0 justify-center rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold ring-1 ring-inset',
      COLORI_METODO[metodo] || COLORI_METODO.PUT, className)}>{metodo}</span>
  )
}

/** Caselle di scelta con descrizione. voci: [{ id, label, aiuto }] */
export function Caselle({ voci, valori, onChange, colonne = 'sm:grid-cols-2' }) {
  const alterna = (id) => onChange(valori.includes(id) ? valori.filter(v => v !== id) : [...valori, id])
  return (
    <div className={cx('grid gap-2', colonne)}>
      {voci.map(v => {
        const on = valori.includes(v.id)
        return (
          <label key={v.id} className={cx('flex cursor-pointer items-start gap-2.5 rounded-campo border px-3 py-2.5 transition-colors',
            on ? 'border-verde-500 bg-verde-50/60' : 'border-linea hover:border-pino-200')}>
            <input type="checkbox" className="mt-0.5 size-4 accent-verde-600" checked={on} onChange={() => alterna(v.id)} />
            <span className="min-w-0">
              <span className="block font-mono text-[13px] font-medium text-pino-900">{v.label}</span>
              {v.aiuto && <span className="block text-xs leading-snug text-grafite">{v.aiuto}</span>}
            </span>
          </label>
        )
      })}
    </div>
  )
}

/** "Tutti i processi" oppure solo alcuni. valori vuoto = tutti. */
export function SceltaProcessi({ processi, valori, onChange }) {
  const tutti = valori.length === 0
  return (
    <div className="space-y-2">
      <div className="inline-flex rounded-campo border border-linea bg-carta p-0.5 text-sm">
        <button type="button" onClick={() => onChange([])}
                className={cx('rounded-[7px] px-3 py-1.5 font-medium', tutti ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite')}>Tutti i processi</button>
        <button type="button" onClick={() => tutti && onChange(processi.slice(0, 1).map(p => p.chiave))} disabled={!processi.length}
                className={cx('rounded-[7px] px-3 py-1.5 font-medium disabled:opacity-50', !tutti ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite')}>Solo alcuni</button>
      </div>
      {!tutti && (
        <Caselle voci={processi.map(p => ({ id: p.chiave, label: p.chiave, aiuto: p.nome }))} valori={valori} onChange={onChange} />
      )}
      {!tutti && valori.some(v => !processi.find(p => p.chiave === v)) && (
        <p className="text-xs text-grafite">Anche processi non (ancora) pubblicati: {valori.filter(v => !processi.find(p => p.chiave === v)).join(', ')}</p>
      )}
    </div>
  )
}

/** Testo con un minimo di formattazione: paragrafi, **grassetto**, `codice`. */
export function Testo({ md, className }) {
  if (!md) return null
  const inline = (s, k) => s.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((p, i) =>
    p.startsWith('**') ? <strong key={`${k}-${i}`} className="font-semibold text-pino-900">{p.slice(2, -2)}</strong>
      : p.startsWith('`') ? <code key={`${k}-${i}`} className="rounded bg-carta px-1 py-0.5 font-mono text-[0.9em] text-pino-900 ring-1 ring-linea">{p.slice(1, -1)}</code>
      : p)
  return (
    <div className={cx('space-y-2 text-[15px] leading-relaxed text-grafite', className)}>
      {md.split(/\n\n+/).map((par, i) => <p key={i}>{inline(par, i)}</p>)}
    </div>
  )
}
