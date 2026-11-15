import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { CircleCheck, CircleX, LoaderCircle, X } from 'lucide-react'
import { cx, iniziali } from '../lib/format.js'

// ================================================================= pulsanti

const varianti = {
  primario: 'bg-verde-600 text-white hover:bg-verde-700 active:bg-verde-700 shadow-[inset_0_-1px_0_rgb(0_0_0/0.15)]',
  secondario: 'bg-white text-pino-900 border border-linea hover:border-pino-200 hover:bg-carta',
  fantasma: 'text-pino-800 hover:bg-pino-900/5',
  pericolo: 'bg-white text-mattone-600 border border-mattone-100 hover:bg-mattone-50',
  scuro: 'bg-pino-900 text-white hover:bg-pino-800'
}
const misure = { sm: 'h-8 px-3 text-sm gap-1.5', md: 'h-10 px-4 text-[15px] gap-2', lg: 'h-12 px-5 text-base gap-2' }

export function Button({ variante = 'secondario', misura = 'md', icona: Icona, attesa, className, children, ...props }) {
  return (
    <button
      {...props}
      disabled={props.disabled || attesa}
      className={cx('inline-flex shrink-0 items-center justify-center rounded-campo font-medium transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-55', varianti[variante], misure[misura], className)}>
      {attesa ? <LoaderCircle className="size-4 animate-spin" /> : Icona && <Icona className="size-4" strokeWidth={2} />}
      {children}
    </button>
  )
}

// ================================================================= etichette di stato

const toni = {
  verde: 'bg-verde-50 text-verde-700 ring-verde-100',
  zafferano: 'bg-zafferano-50 text-zafferano-800 ring-zafferano-100',
  mattone: 'bg-mattone-50 text-mattone-700 ring-mattone-100',
  neutro: 'bg-carta text-grafite ring-linea',
  pino: 'bg-pino-900 text-white ring-pino-900'
}
export function Badge({ tono = 'neutro', icona: Icona, className, children }) {
  return (
    <span className={cx('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[13px] font-medium ring-1 ring-inset', toni[tono], className)}>
      {Icona && <Icona className="size-3.5" strokeWidth={2.2} />}
      {children}
    </span>
  )
}

export function Avatar({ nome, className }) {
  return (
    <span className={cx('inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-pino-700 text-[13px] font-semibold text-white', className)}
          aria-hidden="true">
      {iniziali(nome)}
    </span>
  )
}

// ================================================================= contenitori

export function Pannello({ titolo, azioni, className, corpoClassName, children }) {
  return (
    <section className={cx('rounded-pannello border border-linea bg-white shadow-pannello', className)}>
      {(titolo || azioni) && (
        <header className="flex items-center justify-between gap-3 border-b border-linea px-5 py-3.5">
          {typeof titolo === 'string' ? <h2 className="text-[17px] font-semibold">{titolo}</h2> : titolo}
          {azioni && <div className="flex items-center gap-2">{azioni}</div>}
        </header>
      )}
      <div className={cx('p-5', corpoClassName)}>{children}</div>
    </section>
  )
}

export function Vuoto({ icona: Icona, titolo, children, azione }) {
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center">
      {Icona && <span className="mb-3 inline-flex size-11 items-center justify-center rounded-full bg-carta text-pino-600"><Icona className="size-5" /></span>}
      <p className="font-medium text-pino-900">{titolo}</p>
      {children && <p className="mt-1 max-w-sm text-sm text-grafite">{children}</p>}
      {azione && <div className="mt-4">{azione}</div>}
    </div>
  )
}

export function Caricamento({ testo = 'Caricamento…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-grafite">
      <LoaderCircle className="size-5 animate-spin text-verde-600" /> {testo}
    </div>
  )
}

export function Errore({ children }) {
  if (!children) return null
  return (
    <div role="alert" className="flex items-start gap-2 rounded-campo border border-mattone-100 bg-mattone-50 px-3.5 py-2.5 text-sm text-mattone-700">
      <CircleX className="mt-0.5 size-4 shrink-0" /> <span className="whitespace-pre-line">{children}</span>
    </div>
  )
}

/** Schede (tab) semplici. voci: [{ id, label, conteggio }] */
export function Schede({ voci, attiva, onChange, className }) {
  return (
    <div role="tablist" className={cx('flex gap-1 border-b border-linea', className)}>
      {voci.map(v => (
        <button key={v.id} role="tab" aria-selected={attiva === v.id} onClick={() => onChange(v.id)}
          className={cx('-mb-px inline-flex items-center gap-2 border-b-2 px-3 py-2.5 text-[15px] font-medium transition-colors',
            attiva === v.id ? 'border-verde-600 text-pino-900' : 'border-transparent text-grafite hover:text-pino-900')}>
          {v.label}
          {v.conteggio > 0 && <span className="numeri rounded-full bg-carta px-1.5 text-xs text-grafite ring-1 ring-linea">{v.conteggio}</span>}
        </button>
      ))}
    </div>
  )
}

// ================================================================= finestra modale

export function Dialogo({ aperto, onClose, titolo, sottotitolo, larghezza = 'max-w-2xl', children }) {
  const ref = useRef(null)
  useEffect(() => {
    if (!aperto) return
    const onKey = (e) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    ref.current?.focus()
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = prev }
  }, [aperto, onClose])
  if (!aperto) return null
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-pino-950/45 p-4 backdrop-blur-[2px] sm:p-8"
         onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={titolo}
           className={cx('w-full animate-entra rounded-pannello bg-white shadow-sollevato outline-none', larghezza)}>
        <header className="flex items-start justify-between gap-4 border-b border-linea px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold">{titolo}</h2>
            {sottotitolo && <p className="mt-0.5 text-sm text-grafite">{sottotitolo}</p>}
          </div>
          <button onClick={onClose} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi">
            <X className="size-5" />
          </button>
        </header>
        <div className="px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

// ================================================================= avvisi (toast)

const ToastContext = createContext(null)

export function ToastProvider({ children }) {
  const [lista, setLista] = useState([])
  const togli = useCallback((id) => setLista(l => l.filter(t => t.id !== id)), [])
  const mostra = useCallback((testo, tipo = 'ok') => {
    const id = Math.random().toString(36).slice(2)
    setLista(l => [...l, { id, testo, tipo }])
    setTimeout(() => togli(id), tipo === 'errore' ? 7000 : 4000)
  }, [togli])
  const toast = useRef({ ok: (t) => mostra(t, 'ok'), errore: (t) => mostra(t, 'errore') })
  return (
    <ToastContext.Provider value={toast.current}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-4 z-[60] flex flex-col items-center gap-2 px-4">
        {lista.map(t => (
          <div key={t.id}
               className={cx('pointer-events-auto flex max-w-lg animate-entra items-start gap-2.5 rounded-pannello px-4 py-3 text-[15px] shadow-sollevato',
                 t.tipo === 'errore' ? 'bg-mattone-700 text-white' : 'bg-pino-900 text-white')}>
            {t.tipo === 'errore' ? <CircleX className="mt-0.5 size-4 shrink-0" /> : <CircleCheck className="mt-0.5 size-4 shrink-0 text-verde-400" />}
            <span>{t.testo}</span>
            <button onClick={() => togli(t.id)} className="ml-2 opacity-60 hover:opacity-100" aria-label="Chiudi avviso"><X className="size-4" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
export const useToast = () => useContext(ToastContext)
