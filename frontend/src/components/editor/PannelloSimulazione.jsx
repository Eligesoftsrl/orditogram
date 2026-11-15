import { useEffect, useRef, useState } from 'react'
import {
  Bell, Bot, Mail, CircleCheck, CircleDot, CirclePlay, CircleStop, Flag, GitBranch, Hourglass, Pause, Play, RotateCcw,
  Timer, TriangleAlert, UserRound
} from 'lucide-react'
import { cx } from '../../lib/format.js'
import { tipoBordo } from './timer.js'
import { avviaPraticaDiProva, EVENTI } from '../../lib/bpmn/simulazione.js'

const VELOCITA = [['Lenta', 0.5], ['Normale', 1], ['Veloce', 2]]

/**
 * Pannello della simulazione: comandi, istruzioni e il registro in italiano di quello che succede
 * alle pratiche di prova. Nessun dato viene salvato: è solo una prova sul diagramma.
 */
export default function PannelloSimulazione({ modeler, onEsci }) {
  const [registro, setRegistro] = useState([])
  const [inPausa, setInPausa] = useState(false)
  const [velocita, setVelocita] = useState(1)
  const [fermati, setFermati] = useState(true)
  const [conteggi, setConteggi] = useState({ avviate: 0, concluse: 0, interrotte: 0 })
  const progressivo = useRef(0)
  const pratiche = useRef(new Map())   // scope id -> numero della pratica di prova

  useEffect(() => {
    if (!modeler) return
    const eb = modeler.get('eventBus')
    const nomeDi = (el) => el?.businessObject?.name || ''
    const numero = (scope) => {
      let s = scope
      while (s && s.parent) s = s.parent
      return s ? pratiche.current.get(s.id) : null
    }
    const aggiungi = (voce) => setRegistro(r => [{ id: ++progressivo.current, ...voce }, ...r].slice(0, 200))

    const traccia = (e) => {
      const { action, element, scope } = e
      if (!element || !scope) return
      const t = element.type
      if (action === 'createScope') return
      if (action === 'destroyScope') {
        if (t === 'bpmn:Process') {
          const n = pratiche.current.get(scope.id)
          const ok = scope.completed
          setConteggi(c => ok ? { ...c, concluse: c.concluse + 1 } : { ...c, interrotte: c.interrotte + 1 })
          aggiungi({ tipo: ok ? 'fine' : 'interrotta', testo: `Pratica di prova ${n} ${ok ? 'conclusa' : 'interrotta'}`, n })
        } else if (t === 'bpmn:SubProcess') {
          aggiungi({ tipo: scope.completed ? 'ok' : 'interrotta', n: numero(scope),
            testo: `Sottoprocesso "${nomeDi(element) || 'senza nome'}" ${scope.completed ? 'concluso' : 'interrotto'}` })
        }
        return
      }
      const n = numero(scope)
      if (action === 'exit') {
        if (t === 'bpmn:UserTask') aggiungi({ tipo: 'persona', n, testo: `Completata: ${nomeDi(element) || 'attività di una persona'}` })
        else if (t === 'bpmn:ServiceTask') aggiungi({ tipo: 'automatica', n, testo: `Eseguita: ${nomeDi(element) || 'attività automatica'}` })
        else if (t === 'bpmn:CallActivity') aggiungi({ tipo: 'automatica', n, testo: `Conclusa la pratica richiamata: ${nomeDi(element) || element.businessObject.calledElement || 'processo richiamato'}` })
        else if (t === 'bpmn:IntermediateCatchEvent' && tipoBordo(element.businessObject) === 'messaggio') aggiungi({ tipo: 'messaggio', n, testo: `Messaggio arrivato: ${nomeDi(element) || element.businessObject.eventDefinitions?.[0]?.messageRef?.name || 'messaggio'}` })
        else if (t === 'bpmn:IntermediateCatchEvent') aggiungi({ tipo: 'attesa', n, testo: `Attesa finita: ${nomeDi(element) || 'attesa'}` })
      }
      if ((action === 'signal' || action === 'enter') && t === 'bpmn:BoundaryEvent') {
        const tb = tipoBordo(element.businessObject)
        const cosa = { scadenza: 'Scaduto il termine', sollecito: 'Inviato il sollecito', errore: 'Non riuscita', messaggio: 'Messaggio arrivato' }[tb] || 'Scattato'
        aggiungi({ tipo: tb || 'evento', n, testo: `${cosa}: ${nomeDi(element) || nomeDi(element.host) || ''}` })
        return
      }
      if (action === 'enter') {
        if (t === 'bpmn:SequenceFlow' && element.source?.type === 'bpmn:ExclusiveGateway') {
          aggiungi({ tipo: 'decisione', n, testo: `${nomeDi(element.source) || 'Decisione'} → ${nomeDi(element) || 'verso ' + (nomeDi(element.target) || 'il passo successivo')}` })
        } else if (t === 'bpmn:EndEvent' && element.parent?.type !== 'bpmn:SubProcess') {
          aggiungi({ tipo: 'esito', n, testo: `Esito: ${nomeDi(element) || 'fine'}` })
        }
      }
    }
    const nuovaScope = ({ scope }) => {
      if (scope?.element?.type !== 'bpmn:Process') return
      const n = pratiche.current.size + 1
      pratiche.current.set(scope.id, n)
      setConteggi(c => ({ ...c, avviate: c.avviate + 1 }))
      aggiungi({ tipo: 'avvio', testo: `Pratica di prova ${n} avviata`, n })
    }
    const play = () => setInPausa(false)
    const pausa = () => setInPausa(true)
    const reset = () => {
      pratiche.current = new Map()
      setRegistro([]); setConteggi({ avviate: 0, concluse: 0, interrotte: 0 }); setInPausa(false)
    }
    const vel = (e) => e?.speed && setVelocita(e.speed)

    eb.on(EVENTI.traccia, traccia)
    eb.on(EVENTI.nuovaScope, nuovaScope)
    eb.on(EVENTI.play, play)
    eb.on(EVENTI.pausa, pausa)
    eb.on(EVENTI.reset, reset)
    eb.on(EVENTI.velocita, vel)
    return () => {
      eb.off(EVENTI.traccia, traccia); eb.off(EVENTI.nuovaScope, nuovaScope); eb.off(EVENTI.play, play); eb.off(EVENTI.pausa, pausa)
      eb.off(EVENTI.reset, reset); eb.off(EVENTI.velocita, vel)
    }
  }, [modeler])

  // fermarsi sulle attività delle persone: così si possono far scattare scadenze e solleciti
  useEffect(() => {
    if (!modeler) return
    const simulator = modeler.get('simulator')
    modeler.get('elementRegistry').filter(e => e.type === 'bpmn:UserTask').forEach(e => simulator.waitAtElement(e, fermati))
  }, [modeler, fermati])

  const avvia = () => {
    if (inPausa) modeler.get('pauseSimulation').unpause()
    if (!avviaPraticaDiProva(modeler)) alert('Il processo non ha un inizio da cui partire.')
  }
  const alternaPausa = () => modeler.get('pauseSimulation').toggle()
  const ricomincia = () => modeler.get('resetSimulation').resetSimulation()
  const impostaVelocita = (v) => { modeler.get('animation').setAnimationSpeed(v); setVelocita(v) }

  return (
    <div>
      <header className="flex items-center gap-3 border-b border-linea px-5 py-4">
        <span className="inline-flex size-9 items-center justify-center rounded-campo bg-zafferano-400 text-pino-900"><CirclePlay className="size-[18px]" /></span>
        <div className="min-w-0">
          <p className="text-[13px] text-grafite">Prova del processo</p>
          <p className="font-display text-lg font-semibold leading-tight">Simulazione</p>
        </div>
        <button onClick={onEsci} className="ml-auto inline-flex items-center gap-1.5 text-[13px] font-medium text-verde-700 hover:underline">
          <CircleStop className="size-4" /> Esci
        </button>
      </header>

      <section className="space-y-3 border-b border-linea px-5 py-4">
        <button type="button" onClick={avvia}
                className="flex w-full items-center justify-center gap-2 rounded-campo bg-verde-600 px-4 py-2.5 font-medium text-white hover:bg-verde-700">
          <Play className="size-4" /> Avvia una pratica di prova
        </button>
        <div className="flex gap-2">
          <button type="button" onClick={alternaPausa} disabled={conteggi.avviate === 0}
                  className="inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-campo border border-linea bg-white text-sm font-medium hover:bg-carta disabled:opacity-40">
            {inPausa ? <><Play className="size-4" /> Riprendi</> : <><Pause className="size-4" /> Pausa</>}
          </button>
          <button type="button" onClick={ricomincia}
                  className="inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-campo border border-linea bg-white text-sm font-medium hover:bg-carta">
            <RotateCcw className="size-4" /> Ricomincia
          </button>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[13px] text-grafite">Velocità</span>
          <div className="inline-flex rounded-campo bg-carta p-1" role="radiogroup" aria-label="Velocità">
            {VELOCITA.map(([l, v]) => (
              <button key={v} type="button" role="radio" aria-checked={velocita === v} onClick={() => impostaVelocita(v)}
                      className={cx('rounded-md px-3 py-1 text-[13px] font-medium', velocita === v ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite hover:text-pino-900')}>
                {l}
              </button>
            ))}
          </div>
        </div>
        <label className="flex items-start gap-2 text-[13px] text-pino-900">
          <input type="checkbox" className="mt-0.5 size-4 accent-verde-600" checked={fermati} onChange={e => setFermati(e.target.checked)} />
          <span>Fermati sulle attività delle persone <span className="block text-grafite">Il gettone aspetta che tu clicchi ▶ per completarle: intanto puoi far scattare scadenze e solleciti.</span></span>
        </label>
        <dl className="grid grid-cols-3 gap-2 text-center">
          {[['Avviate', conteggi.avviate], ['Concluse', conteggi.concluse], ['Interrotte', conteggi.interrotte]].map(([l, v]) => (
            <div key={l} className="rounded-campo bg-carta py-2">
              <dd className="numeri text-xl font-semibold text-pino-900">{v}</dd>
              <dt className="text-xs text-grafite">{l}</dt>
            </div>
          ))}
        </dl>
      </section>

      <section className="border-b border-linea px-5 py-4">
        <h3 className="text-[15px] font-semibold text-pino-900">Come si usa</h3>
        <ul className="mt-2 space-y-1.5 text-[13px] text-pino-800">
          <li className="flex gap-2"><Play className="mt-0.5 size-3.5 shrink-0 text-verde-700" /> Il gettone scorre da solo; sulle attività delle persone si ferma (se lo lasci scegliere sopra): clicca ▶ per completarle.</li>
          <li className="flex gap-2"><GitBranch className="mt-0.5 size-3.5 shrink-0 text-verde-700" /> Alle decisioni clicca l'icona accanto per scegliere la strada: le condizioni sui dati qui non vengono calcolate.</li>
          <li className="flex gap-2"><Timer className="mt-0.5 size-3.5 shrink-0 text-verde-700" /> Scadenze, solleciti, errori, attese e messaggi aspettano te: clicca ▶ sull'evento per farlo scattare.</li>
          <li className="flex gap-2"><CircleDot className="mt-0.5 size-3.5 shrink-0 text-verde-700" /> Puoi avviare più pratiche di prova: ognuna ha il suo colore.</li>
        </ul>
      </section>

      <section className="px-5 py-4">
        <h3 className="text-[15px] font-semibold text-pino-900">Cosa è successo</h3>
        {registro.length === 0
          ? <p className="mt-2 text-[13px] text-grafite">Avvia una pratica di prova: qui vedrai ogni passo.</p>
          : (
            <ol className="mt-2 space-y-1.5" aria-live="polite">
              {registro.map(v => <Voce key={v.id} v={v} />)}
            </ol>
          )}
      </section>
    </div>
  )
}

const ICONE = {
  avvio: [Play, 'text-verde-700'], fine: [Flag, 'text-verde-700'], esito: [Flag, 'text-pino-800'],
  interrotta: [CircleStop, 'text-mattone-600'], ok: [CircleCheck, 'text-verde-700'],
  persona: [UserRound, 'text-pino-800'], automatica: [Bot, 'text-pino-800'], attesa: [Hourglass, 'text-zafferano-800'],
  decisione: [GitBranch, 'text-pino-800'], scadenza: [Timer, 'text-zafferano-800'], sollecito: [Bell, 'text-zafferano-800'],
  errore: [TriangleAlert, 'text-mattone-600'], evento: [CircleDot, 'text-grafite'], messaggio: [Mail, 'text-verde-700']
}

function Voce({ v }) {
  const [Icona, colore] = ICONE[v.tipo] || ICONE.evento
  return (
    <li className="flex items-start gap-2 text-[13px]">
      <Icona className={cx('mt-0.5 size-3.5 shrink-0', colore)} />
      <span className="min-w-0 flex-1 text-pino-900">{v.testo}</span>
      {v.n && <span className="shrink-0 rounded bg-carta px-1.5 font-mono text-[11px] text-grafite">#{v.n}</span>}
    </li>
  )
}
