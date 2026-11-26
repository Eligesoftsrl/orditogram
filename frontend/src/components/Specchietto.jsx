import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { cx, quando } from '../lib/format.js'
import { posizioneAttuale, tempiDelPercorso } from '../lib/percorso.js'
import { BadgeScadenza } from './Scadenza.jsx'
import { gl } from '../lib/glossario.js'

/**
 * Specchietto del flusso: dove si trova la pratica, a colpo d'occhio.
 * All'apertura un segnalino percorre i passi fatti fino a quello attuale, che pulsa; a destra "Adesso"
 * (chi ci lavora, da quanto, la scadenza) e quanto è durato ogni passo. Fermo con "riduci il movimento".
 */
export default function Specchietto({ p }) {
  const percorso = p.percorso || []
  const pos = posizioneAttuale(percorso)
  const n = percorso.length
  const quota = n > 1 ? pos / (n - 1) : 0
  // la corsa dura di più se i passi sono tanti, ma non oltre un secondo e mezzo
  const corsa = Math.min(1500, 250 + pos * 220)
  const [partito, setPartito] = useState(false)
  useEffect(() => { const t = setTimeout(() => setPartito(true), 150); return () => clearTimeout(t) }, [])

  const conclusa = p.stato === 'conclusa'
  const corrente = p.stepCorrenti?.[0]
  const tempi = tempiDelPercorso(percorso, Date.now(), corrente?.dal)
  const inCorso = tempi.find(t => t.inCorso)
  const x = (q) => `calc(64px + (100% - 128px) * ${q})`

  if (!n) return null
  return (
    <section className="rounded-pannello border border-linea bg-white p-5 shadow-pannello" aria-labelledby="specchietto-titolo">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="specchietto-titolo" className="text-lg font-semibold">Dove si trova {gl('pratica', 'il')}</h2>
        <p className="numeri text-sm text-grafite">{conclusa ? 'Percorso completo' : `Passo ${pos + 1} di ${n} finora`}</p>
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_280px] lg:items-center">
        <div className="overflow-x-auto pb-1" ref={el => { if (el) el.scrollLeft = el.scrollWidth }}>
          <ol className="relative h-[104px]" style={{ minWidth: `${Math.max(2, n) * 132}px` }}
              aria-label={`Percorso: ${percorso.map(f => f.nome).join(', ')}`}>
            <span aria-hidden="true" className="absolute top-[15px] h-1 rounded-full bg-linea" style={{ left: '64px', right: '64px' }} />
            <span aria-hidden="true" className="absolute top-[15px] h-1 rounded-full bg-verde-600 transition-[width] ease-[var(--ease-morbido)]"
                  style={{ left: '64px', width: partito ? `calc((100% - 128px) * ${quota})` : '0px', transitionDuration: `${corsa}ms` }} />
            {!conclusa && (
              <span aria-hidden="true" className="absolute top-[9px] z-10 size-4 -translate-x-1/2 rounded-full border-[3px] border-white bg-zafferano-400 shadow transition-[left] ease-[var(--ease-morbido)]"
                    style={{ left: partito ? x(quota) : x(0), transitionDuration: `${corsa}ms` }} />
            )}
            {percorso.map((f, i) => {
              const q = n > 1 ? i / (n - 1) : 0
              const acceso = partito && i <= pos
              const ritardo = pos ? Math.round(corsa * (i / pos)) : 0
              return (
                <li key={i} className="absolute top-0 flex w-[124px] -translate-x-1/2 flex-col items-center text-center" style={{ left: x(q) }}>
                  <span className={cx('relative z-[1] inline-flex size-[34px] -mt-0.5 items-center justify-center rounded-full border-[3px] transition-colors duration-300',
                      f.stato === 'corrente'
                        ? cx('bg-white', acceso ? 'animate-respiro border-verde-600' : 'border-linea')
                        : acceso ? (f.stato === 'fine' ? 'border-ok-600 bg-ok-600 text-white' : 'border-verde-600 bg-verde-600 text-white') : 'border-linea bg-white text-transparent')}
                        style={{ transitionDelay: `${ritardo}ms` }} aria-hidden="true">
                    {f.stato !== 'corrente' && <Check className="size-4" strokeWidth={3} />}
                  </span>
                  <span className={cx('mt-2 line-clamp-2 text-[13px] font-semibold leading-snug', f.stato === 'corrente' ? 'text-verde-700' : 'text-pino-900')}>{f.nome}</span>
                  <span className="mt-0.5 truncate text-xs text-grafite" style={{ maxWidth: '120px' }}>
                    {f.stato === 'corrente' ? (f.chi || 'da prendere in carico') : quando(f.quando)}
                  </span>
                </li>
              )
            })}
          </ol>
        </div>

        <div className="space-y-3 rounded-blocco bg-carta p-4" aria-live="polite">
          <p className="text-xs font-semibold uppercase tracking-[.06em] text-grafite">Adesso</p>
          <p className="text-lg font-semibold leading-tight text-pino-900">
            {conclusa ? `${gl('pratica', 'Nome')} ${gl.agg('pratica', 'conclus')}` : p.stepCorrenti?.length ? p.stepCorrenti.map(s => s.nome).join(', ') : 'In attesa'}
          </p>
          <dl className="grid grid-cols-[88px_minmax(0,1fr)] gap-x-2 gap-y-1.5 text-sm">
            {conclusa ? <><dt className="text-grafite">Il</dt><dd className="font-semibold">{quando(p.fine)}</dd></> : <>
              {inCorso?.durata && <><dt className="text-grafite">Da</dt><dd className="font-semibold">{inCorso.durata}</dd></>}
              {corrente && <><dt className="text-grafite">In carico a</dt><dd className="font-semibold">{corrente.assegnatario || (corrente.gruppi?.length ? `gruppo ${corrente.gruppi.join(', ')}` : '—')}</dd></>}
              {p.prossimaScadenza && <><dt className="text-grafite">Scadenza</dt><dd><BadgeScadenza s={p.prossimaScadenza} conNome={false} /></dd></>}
            </>}
          </dl>
          {tempi.filter(t => !t.inCorso && t.durata).length > 0 && (
            <div className="space-y-1 border-t border-linea pt-3">
              <p className="text-xs font-semibold uppercase tracking-[.06em] text-grafite">Tempi dei passi</p>
              <ul className="space-y-1 text-[13px]">
                {tempi.filter(t => t.durata).slice(-5).map((t, i) => (
                  <li key={i} className="flex justify-between gap-3">
                    <span className="truncate">{t.nome}</span>
                    <span className="numeri shrink-0 text-grafite">{t.inCorso ? 'in corso' : t.durata}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
