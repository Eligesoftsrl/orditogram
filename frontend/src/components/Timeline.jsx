import { Bell, CircleCheck, FileText, Hand, Inbox, Mail, Play, Timer, TriangleAlert, Undo2, UserPlus, Webhook } from 'lucide-react'
import { cx, quando } from '../lib/format.js'
import { Vuoto } from './ui.jsx'
import { gl } from '../lib/glossario.js'

const stile = {
  AVVIO:           { icona: Play,        colore: 'bg-pino-900 text-white',           get verbo() { return `ha avviato ${gl('pratica', 'il')}` } },
  PRESA_IN_CARICO: { icona: Hand,        colore: 'bg-zafferano-100 text-zafferano-800', verbo: 'ha preso in carico' },
  RILASCIO:        { icona: Undo2,       colore: 'bg-carta text-grafite',            verbo: 'ha rilasciato' },
  COMPLETAMENTO:   { icona: CircleCheck, colore: 'bg-verde-50 text-verde-700',       verbo: 'ha completato' },
  DOCUMENTO:       { icona: FileText,    colore: 'bg-pino-200/50 text-pino-800',     verbo: '' },
  NOTIFICA:        { icona: Bell,        colore: 'bg-zafferano-50 text-zafferano-800', verbo: 'notifica inviata' },
  // eventi automatici: li registra Orditogram, non una persona
  SCADENZA:        { icona: Timer,         colore: 'bg-zafferano-100 text-zafferano-800', prefisso: 'Scattato:' },
  EMAIL:           { icona: Mail,          colore: 'bg-pino-200/50 text-pino-800',       prefisso: 'Email:' },
  CHIAMATA_API:    { icona: Webhook,       colore: 'bg-verde-50 text-verde-700',         prefisso: 'Servizio esterno:' },
  ERRORE:          { icona: TriangleAlert, colore: 'bg-mattone-50 text-mattone-700',     prefisso: 'Non riuscita:' },
  MESSAGGIO:       { icona: Inbox,         colore: 'bg-verde-50 text-verde-700',         prefisso: '' },
  // parti della pratica (richiedente, controparte...): il titolo dice già cosa è successo
  PARTE:           { icona: UserPlus,      colore: 'bg-pino-200/50 text-pino-800',       verbo: '' }
}

/** Storico della pratica: chi ha fatto cosa, quando e con quali valori. */
export default function Timeline({ eventi = [] }) {
  if (!eventi.length) return <Vuoto titolo="Nessun evento registrato" />
  return (
    <ol className="relative">
      {eventi.map((e, i) => {
        const s = stile[e.tipo] || stile.DOCUMENTO
        const Icona = s.icona
        return (
          <li key={e.id} className="relative flex gap-3.5 pb-5 last:pb-0">
            {i < eventi.length - 1 && <span className="absolute left-[15px] top-8 bottom-0 w-px bg-linea" aria-hidden="true" />}
            <span className={cx('relative z-10 inline-flex size-8 shrink-0 items-center justify-center rounded-full', s.colore)}>
              <Icona className="size-4" />
            </span>
            <div className="min-w-0 flex-1 pt-1">
              <p className="text-[15px] leading-snug">
                {s.prefisso !== undefined
                  ? <><span className="text-grafite">{s.prefisso}</span> <span className="font-medium">{e.titolo}</span></>
                  : e.tipo === 'DOCUMENTO' || e.tipo === 'NOTIFICA'
                  ? <span className="font-medium">{e.titolo}</span>
                  : <><span className="font-medium">{e.utenteNome || e.utente}</span> {s.verbo}{e.tipo !== 'AVVIO' && <> <span className="font-medium">{e.titolo}</span></>}</>}
              </p>
              <p className="mt-0.5 text-xs text-grafite">
                {quando(e.quando)}{(e.tipo === 'DOCUMENTO' || s.prefisso !== undefined) && e.utenteNome ? `, ${e.utenteNome}` : ''}
              </p>
              {e.dettagli?.length > 0 && (
                <dl className="mt-2 grid grid-cols-[minmax(0,auto)_1fr] gap-x-3 gap-y-1 rounded-campo bg-carta px-3 py-2 text-sm">
                  {e.dettagli.map((d, j) => (
                    <div key={j} className="contents">
                      <dt className="text-grafite [overflow-wrap:anywhere]">{d.label}</dt>
                      <dd className="whitespace-pre-line break-words text-pino-900">{d.value}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
