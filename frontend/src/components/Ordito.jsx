/**
 * Il disegno del benvenuto: i fili dell'ordito e, intrecciato, il filo di una pratica che passa
 * dall'inizio, per due attività e una scelta, fino al passo corrente (giallo). Solo decorazione.
 */
export default function Ordito({ className }) {
  return (
    <svg viewBox="0 0 240 160" className={className} aria-hidden="true" focusable="false">
      <g stroke="#fff" strokeOpacity=".16" strokeWidth="1.5">
        {[20, 44, 68, 92, 116, 140, 164, 188, 212].map(x => <line key={x} x1={x} y1="6" x2={x} y2="154" />)}
      </g>
      <g stroke="#fff" strokeOpacity=".1" strokeWidth="1.5">
        {[30, 58, 86, 114, 142].map(y => <line key={y} x1="8" y1={y} x2="232" y2={y} />)}
      </g>
      <path d="M22 112 C52 112 52 60 82 60 L106 60 C130 60 130 104 154 104 C178 104 178 50 206 50"
            fill="none" stroke="#fff" strokeOpacity=".85" strokeWidth="3" strokeLinecap="round" strokeDasharray="1 7" />
      <circle cx="22" cy="112" r="8" fill="none" stroke="#fff" strokeWidth="3" />
      <rect x="70" y="47" width="40" height="26" rx="8" fill="#fff" fillOpacity=".95" />
      <path d="M154 90 l14 14 -14 14 -14 -14 z" fill="#fff" fillOpacity=".85" />
      <rect x="186" y="37" width="40" height="26" rx="8" fill="#FFB000" />
      <circle cx="206" cy="50" r="18" fill="none" stroke="#FFB000" strokeOpacity=".45" strokeWidth="2" />
      <g fill="#0066CC" fillOpacity=".55">
        <rect x="77" y="56" width="20" height="3" rx="1.5" />
        <rect x="77" y="62" width="13" height="3" rx="1.5" />
      </g>
    </svg>
  )
}
