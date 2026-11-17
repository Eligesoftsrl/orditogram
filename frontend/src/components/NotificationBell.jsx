import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, CheckCheck } from 'lucide-react'
import { api } from '../api.js'
import { cx, fa } from '../lib/format.js'

/** Campanella: notifiche dell'utente, aggiornate ogni 30 secondi. */
export default function NotificationBell({ suBlu = false }) {
  const [dati, setDati] = useState({ nonLette: 0, notifiche: [] })
  const [aperto, setAperto] = useState(false)
  const box = useRef(null)
  const navigate = useNavigate()

  const carica = useCallback(() => api.notifiche().then(setDati).catch(() => {}), [])
  useEffect(() => {
    carica()
    const t = setInterval(carica, 30_000)
    return () => clearInterval(t)
  }, [carica])

  useEffect(() => {
    if (!aperto) return
    const fuori = (e) => !box.current?.contains(e.target) && setAperto(false)
    const esc = (e) => e.key === 'Escape' && setAperto(false)
    document.addEventListener('mousedown', fuori)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', fuori); document.removeEventListener('keydown', esc) }
  }, [aperto])

  const apri = async (n) => {
    setAperto(false)
    if (!n.letta) await api.notificaLetta(n.id).catch(() => {})
    carica()
    if (n.processInstanceId) navigate(`/pratiche/${n.processInstanceId}`)
  }

  const tutteLette = async () => { await api.notificheLette().catch(() => {}); carica() }

  return (
    <div ref={box} className="relative">
      <button onClick={() => setAperto(!aperto)} aria-label={`Notifiche, ${dati.nonLette} da leggere`} aria-expanded={aperto}
              className={cx('relative inline-flex size-10 items-center justify-center rounded-full transition-colors', suBlu ? 'text-white hover:bg-white/15' : 'text-pino-800 hover:bg-pino-900/5')}>
        <Bell className="size-5" />
        {dati.nonLette > 0 && (
          <span className="numeri absolute right-1 top-1 inline-flex min-w-[18px] items-center justify-center rounded-full bg-zafferano-400 px-1 text-[11px] font-bold leading-[18px] text-pino-950">
            {dati.nonLette > 9 ? '9+' : dati.nonLette}
          </span>
        )}
      </button>

      {aperto && (
        <div className="absolute right-0 z-40 mt-2 w-[min(92vw,380px)] animate-entra overflow-hidden rounded-pannello border border-linea bg-white shadow-sollevato">
          <div className="flex items-center justify-between border-b border-linea px-4 py-3">
            <p className="text-lg font-semibold">Notifiche</p>
            {dati.nonLette > 0 && (
              <button onClick={tutteLette} className="inline-flex items-center gap-1 text-sm font-medium text-verde-700 hover:underline">
                <CheckCheck className="size-4" /> Segna tutte come lette
              </button>
            )}
          </div>
          {dati.notifiche.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-grafite">Nessuna notifica. Qui arriveranno gli aggiornamenti sulle tue pratiche.</p>
          ) : (
            <ul className="max-h-[60vh] divide-y divide-linea overflow-y-auto">
              {dati.notifiche.map(n => (
                <li key={n.id}>
                  <button onClick={() => apri(n)} className={cx('flex w-full gap-3 px-4 py-3 text-left hover:bg-carta', !n.letta && 'bg-zafferano-50/50')}>
                    <span className={cx('mt-1.5 size-2 shrink-0 rounded-full', n.letta ? 'bg-transparent' : 'bg-zafferano-400')} />
                    <span className="min-w-0">
                      <span className="block text-[15px] font-medium text-pino-900">{n.titolo}</span>
                      {n.messaggio && <span className="mt-0.5 line-clamp-2 block text-sm text-grafite">{n.messaggio}</span>}
                      <span className="mt-1 block text-xs text-nebbia">{fa(n.creataIl)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
