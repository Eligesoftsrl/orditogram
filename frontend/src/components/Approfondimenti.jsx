import { useCallback, useEffect, useState } from 'react'
import { FileText, History, Info } from 'lucide-react'
import { api } from '../api.js'
import { cx } from '../lib/format.js'
import { gl } from '../lib/glossario.js'
import DocumentsPanel from './DocumentsPanel.jsx'
import Timeline from './Timeline.jsx'
import { Cassetto, Selettore } from './ui.jsx'

/**
 * Documenti e storico di una pratica: due capsule con il conteggio che aprono il pannello da destra, così il form
 * e i dati hanno tutta la larghezza. La capsula dei documenti non compare se non ce ne sono e non se ne possono
 * aggiungere; se l'attività li chiede (`richiesti`) e mancano, lo dice.
 */
export default function Approfondimenti({ pratica, suggerimentoDocumenti, richiesti, onDocumenti, className }) {
  const [aperto, setAperto] = useState(null)   // 'documenti' | 'storico' | null
  const [documenti, setDocumenti] = useState(null)   // { n, modificabile }

  const conta = useCallback(() => {
    api.documenti(pratica.id)
      .then(d => setDocumenti({ n: d.documenti.length, modificabile: d.modificabile }))
      .catch(() => setDocumenti(null))
  }, [pratica.id])
  useEffect(() => { conta() }, [conta])

  const eventi = pratica.eventi?.length || 0
  const conDocumenti = documenti && (documenti.n > 0 || documenti.modificabile)
  const mancano = richiesti && documenti && documenti.n === 0

  return (
    <>
      <div className={cx('flex flex-wrap items-center gap-2', className)}>
        {conDocumenti && (
          <Capsula icona={FileText} etichetta="Documenti" n={documenti.n} onClick={() => setAperto('documenti')}
                   nota={mancano ? 'da caricare' : documenti.n === 0 ? 'aggiungi' : null} attenzione={mancano} />
        )}
        {eventi > 0 && <Capsula icona={History} etichetta="Storico" n={eventi} onClick={() => setAperto('storico')} />}
      </div>

      <Cassetto aperto={!!aperto} onClose={() => setAperto(null)} larghezza="max-w-[560px]"
                icona={aperto === 'storico' ? History : FileText}
                titolo={aperto === 'storico' ? 'Storico' : 'Documenti'} sottotitolo={pratica.titolo}>
        {conDocumenti && (
          <Selettore className="mb-4" etichetta={`Cosa vedere ${gl('pratica', 'del')}`} attiva={aperto} onChange={setAperto}
                     voci={[{ id: 'documenti', label: 'Documenti', icona: FileText }, { id: 'storico', label: 'Storico', icona: History }]} />
        )}
        {aperto === 'documenti' && (
          <div className="space-y-3">
            {suggerimentoDocumenti && (
              <p className="flex items-start gap-2 rounded-campo bg-zafferano-50 px-3 py-2 text-sm text-zafferano-800">
                <Info className="mt-0.5 size-4 shrink-0" /> {suggerimentoDocumenti}
              </p>
            )}
            <DocumentsPanel pi={pratica.id} onChange={() => { conta(); onDocumenti?.() }} />
          </div>
        )}
        {aperto === 'storico' && <Timeline eventi={pratica.eventi} />}
      </Cassetto>
    </>
  )
}

/** Capsula che apre il pannello: icona, parola, conteggio e, se serve, una nota (es. "da caricare"). */
function Capsula({ icona: Icona, etichetta, n, nota, attenzione, onClick }) {
  return (
    <button type="button" onClick={onClick} aria-haspopup="dialog"
            className={cx('inline-flex h-10 items-center gap-2 rounded-full border bg-white pl-3 pr-2 text-[15px] font-semibold transition-[background-color,border-color,transform] duration-200 hover:-translate-y-0.5',
              attenzione ? 'border-zafferano-400 text-zafferano-800 hover:bg-zafferano-50' : 'border-linea text-pino-900 hover:border-verde-400 hover:bg-verde-50')}>
      <Icona className="size-[18px]" aria-hidden="true" />
      {etichetta}
      <span className={cx('numeri rounded-full px-2 py-0.5 text-[13px]', attenzione ? 'bg-zafferano-50' : 'bg-carta text-grafite')}>{n}</span>
      {nota && <span className="pr-1 text-[13px] font-semibold">{nota}</span>}
    </button>
  )
}
