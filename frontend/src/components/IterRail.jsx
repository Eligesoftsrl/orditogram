import { Check } from 'lucide-react'
import { cx, quando } from '../lib/format.js'

/**
 * Il "binario" di una pratica: le fermate realmente percorse, compresi i ritorni.
 * percorso: [{ nome, stato: 'fatto' | 'corrente' | 'fine', chi, quando }]
 *
 * compatto: una riga sottile per le liste (mostra le ultime fermate)
 * esteso:   fermate con nome, chi e quando, per il dettaglio
 */
export default function IterRail({ percorso = [], compatto = false, className }) {
  if (!percorso.length) return null

  if (compatto) {
    const MAX = 6
    const nascoste = Math.max(0, percorso.length - MAX)
    const visibili = percorso.slice(-MAX)
    return (
      <div className={cx('flex items-center', className)} aria-label={`Percorso: ${percorso.map(p => p.nome).join(', ')}`}>
        {nascoste > 0 && <span className="mr-1.5 text-xs text-nebbia numeri">+{nascoste}</span>}
        {visibili.map((p, i) => (
          <div key={i} className="flex items-center" title={p.nome + (p.chi ? ` (${p.chi})` : '')}>
            {i > 0 && <span className={cx('h-0.5 w-4 sm:w-5', p.stato === 'corrente' ? 'bg-verde-100' : 'bg-verde-600')} />}
            <Punto stato={p.stato} piccolo />
          </div>
        ))}
      </div>
    )
  }

  return (
    <ol ref={el => { if (el) el.scrollLeft = el.scrollWidth }}
        className={cx('flex w-full items-start overflow-x-auto pb-1', className)}>
      {percorso.map((p, i) => (
        <li key={i} className="relative flex min-w-[112px] flex-1 flex-col items-start last:flex-none last:min-w-[140px]">
          <div className="flex w-full items-center">
            <Punto stato={p.stato} />
            {i < percorso.length - 1 && (
              <span className={cx('mx-1.5 h-0.5 flex-1 rounded-full',
                percorso[i + 1].stato === 'corrente' ? 'bg-[repeating-linear-gradient(90deg,var(--color-verde-400)_0_6px,transparent_6px_10px)]' : 'bg-verde-600')} />
            )}
          </div>
          <div className="mt-2 pr-3">
            <p className={cx('text-sm font-medium leading-snug', p.stato === 'corrente' ? 'text-verde-700' : 'text-pino-900')}>{p.nome}</p>
            {(p.chi || p.quando) && (
              <p className="mt-0.5 text-xs leading-snug text-grafite">
                {p.chi}{p.chi && p.quando ? ', ' : ''}{p.quando && quando(p.quando)}
              </p>
            )}
            {p.stato === 'corrente' && !p.chi && <p className="mt-0.5 text-xs text-grafite">in attesa di presa in carico</p>}
          </div>
        </li>
      ))}
    </ol>
  )
}

function Punto({ stato, piccolo }) {
  const dim = piccolo ? 'size-2.5' : 'size-6'
  if (stato === 'corrente')
    return <span className={cx(dim, 'shrink-0 rounded-full border-[3px] border-verde-600 bg-white animate-respiro')} />
  if (stato === 'fine')
    return (
      <span className={cx(dim, 'inline-flex shrink-0 items-center justify-center rounded-full bg-pino-900 text-white')}>
        {!piccolo && <Check className="size-3.5" strokeWidth={3} />}
      </span>
    )
  return (
    <span className={cx(dim, 'inline-flex shrink-0 items-center justify-center rounded-full bg-verde-600 text-white')}>
      {!piccolo && <Check className="size-3.5" strokeWidth={3} />}
    </span>
  )
}
