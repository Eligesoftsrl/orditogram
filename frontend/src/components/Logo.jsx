import { cx } from '../lib/format.js'
import { useInfo } from '../lib/useGlossario.jsx'

/**
 * Marchio dell'applicazione: il logo e il nome scelti dal cliente (Studio → Glossario e logo), altrimenti
 * quelli di Orditogram (tre fermate su un binario, l'ultima è quella corrente).
 */
export default function Logo({ chiaro = true, soloSimbolo = false, className }) {
  const { info } = useInfo()
  const nome = info?.nome || 'Orditogram'
  return (
    <span className={cx('inline-flex items-center gap-2.5', className)}>
      {info?.logo ? (
        // su fondo scuro o blu il logo del cliente sta su una piastrina bianca, così resta leggibile
        <span className={cx('inline-flex h-9 items-center', chiaro && 'rounded-blocco bg-white px-1.5')}>
          <img src={info.logo} alt={soloSimbolo ? nome : ''} className="h-7 w-auto max-w-[140px] object-contain" />
        </span>
      ) : (
        <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
          <rect width="32" height="32" rx="9" fill={chiaro ? 'rgb(255 255 255 / .16)' : '#0066CC'} />
          <path d="M7 19h18" stroke="#fff" strokeOpacity=".7" strokeWidth="2.5" strokeLinecap="round" />
          <circle cx="9" cy="19" r="3" fill="#fff" />
          <circle cx="16" cy="19" r="3" fill="#fff" />
          <circle cx="23" cy="19" r="3.6" fill="#FFB000" />
        </svg>
      )}
      {!soloSimbolo && <span className={cx('font-display text-[22px] font-bold leading-tight tracking-tight', chiaro ? 'text-white' : 'text-pino-900')}>{nome}</span>}
    </span>
  )
}
