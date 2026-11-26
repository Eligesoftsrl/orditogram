import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { CheckCircle2, RotateCcw, Route } from 'lucide-react'
import { api } from '../api.js'
import { useAuth } from '../lib/auth.jsx'
import BpmnViewer from './BpmnViewer.jsx'
import { Badge, Button, Caricamento, Cassetto, Errore } from './ui.jsx'
import { gl } from '../lib/glossario.js'

/**
 * Il percorso della pratica, sempre a portata di tocco: un pulsante fisso in basso a destra apre da destra il
 * diagramma BPMN grande. Tutto ciò che la pratica non ha toccato si spegne, un segnalino parte dall'inizio,
 * lascia una scia blu, accende ogni passo che raggiunge e si ferma dove la pratica è adesso.
 * Sotto, le tappe in parole (per chi non vede il disegno). Non per gli esterni (il processo è dell'ente).
 */
export default function PercorsoPratica({ pratica }) {
  const { me } = useAuth()
  const [aperto, setAperto] = useState(false)
  if (me?.esterno || !pratica) return null
  return (
    <>
      {/* nel body: una pagina che entra con un'animazione farebbe da riferimento al "fixed" */}
      {createPortal(<button type="button" onClick={() => setAperto(true)} aria-haspopup="dialog"
              aria-label={`Il percorso ${gl('pratica', 'del')}: dove è arrivata`}
              className="group fixed bottom-5 right-5 z-40 inline-flex h-14 animate-sale items-center gap-2.5 rounded-full bg-verde-600 pl-2 pr-2 text-white shadow-sollevato transition-[background-color,transform] duration-200 ease-[var(--ease-morbido)] hover:-translate-y-0.5 hover:bg-verde-700 sm:pr-5 print:hidden">
        <span className="relative inline-flex size-10 items-center justify-center rounded-full bg-white text-verde-700">
          <Route className="size-5" aria-hidden="true" />
          {pratica.stato !== 'conclusa' && <span className="absolute -right-0.5 -top-0.5 size-3 animate-respiro rounded-full border-2 border-white bg-focus" aria-hidden="true" />}
        </span>
        <span className="hidden font-semibold sm:inline">Il percorso</span>
      </button>, document.body)}
      <Cassetto aperto={aperto} onClose={() => setAperto(false)} icona={Route} larghezza="max-w-[min(1180px,100vw)]"
                titolo="Il percorso" sottotitolo={`${pratica.processo} · ${pratica.titolo}`}>
        <Viaggio pratica={pratica} />
      </Cassetto>
    </>
  )
}

function Viaggio({ pratica }) {
  const [d, setD] = useState(null)
  const [errore, setErrore] = useState('')
  const [tappe, setTappe] = useState([])
  const [raggiunta, setRaggiunta] = useState(-1)
  const [giro, setGiro] = useState(0)
  useEffect(() => { api.diagramma(pratica.id).then(setD).catch(e => setErrore(e.message)) }, [pratica.id])

  if (errore) return <Errore>{errore}</Errore>
  if (!d) return <Caricamento />

  const conclusa = pratica.stato === 'conclusa'
  const adesso = (pratica.stepCorrenti || []).map(s => s.nome).filter(Boolean)
  const nominate = tappe.map((t, i) => ({ ...t, i })).filter(t => t.nome)
  const ultima = nominate.filter(t => t.i <= raggiunta).at(-1)
  const finito = tappe.length > 0 && raggiunta >= tappe.length - 1
  const rivedi = () => { setRaggiunta(-1); setGiro(g => g + 1) }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="flex flex-wrap items-center gap-2 text-[15px] text-pino-900">
          {conclusa
            ? <Badge tono="verde" icona={CheckCircle2}>{gl('pratica', 'Nome')} {gl.agg('pratica', 'conclus')}</Badge>
            : <><span className="size-2.5 shrink-0 animate-respiro rounded-full bg-verde-600" aria-hidden="true" />
                <span className="font-semibold">Adesso:</span> {adesso.join(', ') || 'in lavorazione'}</>}
        </p>
        <Button misura="sm" variante="fantasma" icona={RotateCcw} onClick={rivedi}>Rivedi il percorso</Button>
      </div>

      <div className="relative overflow-hidden rounded-blocco border border-linea bg-white p-2">
        <BpmnViewer xml={d.xml} attivi={d.activeIds} fatti={d.completedIds} viaggio giro={giro} comandi
                    onTappe={setTappe} onTappa={setRaggiunta} altezza="min(max(300px, calc(100dvh - 330px)), calc(45vw + 160px))" />
        {ultima && (
          <p key={ultima.i} aria-hidden="true"
             className="pointer-events-none absolute left-4 top-4 max-w-[calc(100%-5rem)] animate-menu rounded-full bg-pino-900/90 px-4 py-2 text-sm text-white shadow-sollevato">
            <span className="text-white/75">{finito && !conclusa ? 'È qui' : `Passo ${nominate.indexOf(ultima) + 1} di ${nominate.length}`}</span>
            <span className="mx-2 text-white/50">·</span>
            <span className="font-semibold">{ultima.nome}</span>
          </p>
        )}
      </div>

      {nominate.length > 0 && (
        <section aria-label="Le tappe in ordine">
          <h3 className="mb-2 text-sm font-semibold text-grafite">Le tappe</h3>
          <ol className="flex flex-wrap items-center gap-2">
            {nominate.map((t, n) => {
              const passata = t.i <= raggiunta
              const qui = t.adesso
              return (
                <li key={t.id + n} className="flex items-center gap-2">
                  {n > 0 && <span className={`h-0.5 w-4 rounded-full transition-colors duration-300 ${passata ? 'bg-verde-600' : 'bg-linea'}`} aria-hidden="true" />}
                  <span className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-[13px] transition-colors duration-300 ${
                    qui && passata ? 'border-verde-600 bg-verde-600 font-semibold text-white'
                      : passata ? 'border-verde-100 bg-verde-50 text-verde-700' : 'border-linea bg-white text-grafite'}`}>
                    <span className={`inline-flex size-5 items-center justify-center rounded-full text-[11px] font-semibold ${qui && passata ? 'bg-white text-verde-700' : passata ? 'bg-verde-600 text-white' : 'bg-carta text-grafite'}`}>{n + 1}</span>
                    {t.nome}
                    {qui && <span className="sr-only"> (adesso)</span>}
                  </span>
                </li>
              )
            })}
          </ol>
        </section>
      )}
    </div>
  )
}
