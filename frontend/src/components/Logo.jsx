import { cx } from '../lib/format.js'

/** Marchio: tre fermate su un binario, l'ultima è quella corrente. */
export default function Logo({ chiaro = true, className }) {
  return (
    <span className={cx('inline-flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill={chiaro ? '#163F3C' : '#0F2E2C'} />
        <path d="M7 19h18" stroke="#5FB8A3" strokeWidth="2.5" strokeLinecap="round" />
        <circle cx="9" cy="19" r="3" fill="#5FB8A3" />
        <circle cx="16" cy="19" r="3" fill="#5FB8A3" />
        <circle cx="23" cy="19" r="3.6" fill="#F2A93B" />
      </svg>
      <span className={cx('font-display text-[22px] font-semibold tracking-tight', chiaro ? 'text-white' : 'text-pino-900')}>Orditogram</span>
    </span>
  )
}
