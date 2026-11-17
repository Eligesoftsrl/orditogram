import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { CircleCheck, CircleX, LoaderCircle, X } from 'lucide-react'
import { cx, iniziali } from '../lib/format.js'

// ================================================================= pulsanti

const varianti = {
  primario: 'bg-verde-600 text-white hover:bg-verde-700 active:bg-verde-700',
  secondario: 'bg-white text-verde-700 border-2 border-verde-600 hover:bg-verde-50',
  fantasma: 'text-pino-800 hover:bg-pino-900/5',
  pericolo: 'bg-white text-mattone-600 border border-mattone-100 hover:bg-mattone-50',
  scuro: 'bg-pino-900 text-white hover:bg-pino-800',
  chiaro: 'bg-white text-verde-700 hover:bg-verde-50'
}
const misure = { sm: 'h-8 px-3 text-sm gap-1.5', md: 'h-10 px-4 text-[15px] gap-2', lg: 'h-12 px-5 text-base gap-2' }

export function Button({ variante = 'secondario', misura = 'md', icona: Icona, attesa, className, children, ...props }) {
  return (
    <button
      {...props}
      disabled={props.disabled || attesa}
      className={cx('inline-flex shrink-0 items-center justify-center rounded-campo font-semibold transition-[background-color,color,border-color,transform] duration-150 active:scale-[.97]',
        'disabled:cursor-not-allowed disabled:opacity-55', varianti[variante], misure[misura], className)}>
      {attesa ? <LoaderCircle className="size-4 animate-spin" /> : Icona && <Icona className="size-4" strokeWidth={2} />}
      {children}
    </button>
  )
}

// ================================================================= etichette di stato

const toni = {
  verde: 'bg-ok-50 text-ok-700 ring-ok-100',
  blu: 'bg-verde-50 text-verde-700 ring-verde-100',
  zafferano: 'bg-zafferano-50 text-zafferano-800 ring-zafferano-100',
  mattone: 'bg-mattone-50 text-mattone-700 ring-mattone-100',
  neutro: 'bg-carta text-grafite ring-linea',
  pino: 'bg-pino-900 text-white ring-pino-900'
}
export function Badge({ tono = 'neutro', icona: Icona, className, children }) {
  return (
    <span className={cx('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[13px] font-semibold ring-1 ring-inset', toni[tono], className)}>
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
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-linea px-5 py-4">
          {typeof titolo === 'string' ? <h2 className="text-lg font-semibold">{titolo}</h2> : titolo}
          {azioni && <div className="flex flex-wrap items-center gap-2">{azioni}</div>}
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
          className={cx('-mb-px inline-flex items-center gap-2 border-b-[3px] px-3 py-2.5 text-[15px] font-semibold transition-colors',
            attiva === v.id ? 'border-verde-600 text-verde-700' : 'border-transparent text-grafite hover:text-pino-900')}>
          {v.label}
          {v.conteggio > 0 && <span className={cx('numeri rounded-full px-1.5 text-xs', attiva === v.id ? 'bg-verde-600 text-white' : 'bg-carta text-grafite ring-1 ring-linea')}>{v.conteggio}</span>}
        </button>
      ))}
    </div>
  )
}

// ================================================================= finestra modale

/** Le finestre aperte, dalla prima all'ultima: Esc chiude solo quella in primo piano. */
const finestreAperte = []

export function Dialogo({ aperto, onClose, titolo, sottotitolo, larghezza = 'max-w-2xl', children }) {
  const ref = useRef(null)
  useFinestra(aperto, onClose, ref)
  if (!aperto) return null
  return (
    // lo sfondo chiude con il mouse; da tastiera si chiude con Esc (sopra)
    <div role="presentation" className="fixed inset-0 z-50 flex animate-velo items-start justify-center overflow-y-auto bg-pino-950/40 p-4 backdrop-blur-[2px] sm:p-8"
         onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={titolo}
           className={cx('w-full animate-entra rounded-pannello bg-white shadow-sollevato outline-none', larghezza)}>
        <header className="flex items-start justify-between gap-4 border-b border-linea px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold">{titolo}</h2>
            {sottotitolo && <p className="mt-0.5 text-sm text-grafite">{sottotitolo}</p>}
          </div>
          <button onClick={onClose} className="rounded-full p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi">
            <X className="size-5" />
          </button>
        </header>
        <div className="px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

/** Gestisce Esc, il blocco dello scorrimento e il focus di una finestra aperta (modale o laterale). */
function useFinestra(aperto, onClose, ref) {
  const chiudi = useRef(onClose)
  useEffect(() => { chiudi.current = onClose }, [onClose])
  useEffect(() => {
    if (!aperto) return
    const io = {}
    finestreAperte.push(io)
    const prima = document.activeElement
    const onKey = (e) => e.key === 'Escape' && finestreAperte[finestreAperte.length - 1] === io && chiudi.current()
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    ref.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
      finestreAperte.splice(finestreAperte.indexOf(io), 1)
      // il focus torna dove era prima dell'apertura (WCAG 2.4.3)
      if (prima instanceof HTMLElement) prima.focus()
    }
  }, [aperto, ref])
}

/**
 * Pannello laterale: si apre da destra sopra la pagina, per vedere o fare qualcosa senza cambiare pagina.
 * piede: i pulsanti in fondo (azione principale a destra).
 */
export function Cassetto({ aperto, onClose, titolo, sottotitolo, icona: Icona, piede, larghezza = 'max-w-[440px]', children }) {
  const ref = useRef(null)
  useFinestra(aperto, onClose, ref)
  if (!aperto) return null
  return (
    <div role="presentation" className="fixed inset-0 z-50 animate-velo bg-pino-950/35"
         onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={titolo}
             className={cx('absolute inset-y-0 right-0 flex w-full animate-cassetto flex-col bg-white shadow-sollevato outline-none sm:inset-y-3 sm:right-3 sm:rounded-pannello', larghezza)}>
        <header className="flex items-start gap-3 px-6 pb-4 pt-5">
          {Icona && <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-blocco bg-verde-50 text-verde-700"><Icona className="size-5" /></span>}
          <div className="min-w-0 flex-1">
            <h2 className="text-xl font-semibold leading-tight">{titolo}</h2>
            {sottotitolo && <p className="mt-0.5 text-sm text-grafite">{sottotitolo}</p>}
          </div>
          <button onClick={onClose} className="rounded-full p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi">
            <X className="size-5" />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">{children}</div>
        {piede && <footer className="flex flex-wrap justify-end gap-2 border-t border-linea px-6 py-4">{piede}</footer>}
      </aside>
    </div>
  )
}

// ================================================================= tessere numeriche

const toniTessera = {
  blu: 'bg-verde-600 border-verde-600 text-white',
  neutro: 'bg-white border-linea text-pino-900',
  mattone: 'bg-white border-linea text-pino-900',
  zafferano: 'bg-white border-linea text-pino-900',
  verde: 'bg-white border-linea text-pino-900'
}
const iconeTessera = {
  blu: 'bg-white/20 text-white', neutro: 'bg-carta text-grafite', mattone: 'bg-mattone-50 text-mattone-700',
  zafferano: 'bg-zafferano-50 text-zafferano-800', verde: 'bg-ok-50 text-ok-700'
}

/** Tessera con un numero: icona, etichetta, valore grande, pastiglia e nota. Con `to` è un collegamento. */
export function Tessera({ tono = 'neutro', icona: Icona, etichetta, valore, pastiglia, nota, as: Comp = 'div', className, ...props }) {
  const piena = tono === 'blu'
  return (
    <Comp {...props}
      className={cx('group flex flex-col gap-2.5 rounded-pannello border p-4 shadow-pannello transition-[transform,box-shadow] duration-150',
        props.to || props.href || props.onClick ? 'hover:-translate-y-0.5 hover:shadow-[0_8px_20px_-12px_rgb(0_40_80/0.35)]' : '', toniTessera[tono], className)}>
      <span className="flex items-center gap-2.5">
        {Icona && <span className={cx('inline-flex size-9 items-center justify-center rounded-[10px]', iconeTessera[tono])}><Icona className="size-[18px]" /></span>}
        <span className={cx('text-[15px] font-semibold', piena ? 'text-white/90' : 'text-grafite')}>{etichetta}</span>
      </span>
      <span className="flex flex-wrap items-baseline gap-2.5">
        <span className="numeri text-4xl font-semibold leading-none tracking-tight">{valore}</span>
        {pastiglia && <span className={cx('rounded-full px-2 py-0.5 text-xs font-semibold', iconeTessera[tono])}>{pastiglia}</span>}
      </span>
      {nota && <span className={cx('text-[13px]', piena ? 'text-white/85' : 'text-grafite')}>{nota}</span>}
    </Comp>
  )
}

// ================================================================= avvisi (toast)

const ToastContext = createContext(null)

let ultimoAvviso = 0
export function ToastProvider({ children }) {
  const [lista, setLista] = useState([])
  const togli = useCallback((id) => setLista(l => l.filter(t => t.id !== id)), [])
  const mostra = useCallback((testo, tipo = 'ok') => {
    const id = ++ultimoAvviso
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
            {t.tipo === 'errore' ? <CircleX className="mt-0.5 size-4 shrink-0" /> : <CircleCheck className="mt-0.5 size-4 shrink-0 text-ok-100" />}
            <span>{t.testo}</span>
            <button onClick={() => togli(t.id)} className="ml-2 opacity-60 hover:opacity-100" aria-label="Chiudi avviso"><X className="size-4" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
export const useToast = () => useContext(ToastContext)
