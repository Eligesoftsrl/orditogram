import { useEffect, useState } from 'react'
import { Info } from 'lucide-react'
import { api } from '../api.js'
import BpmnViewer from './BpmnViewer.jsx'
import DocumentsPanel from './DocumentsPanel.jsx'
import Timeline from './Timeline.jsx'
import { Caricamento, Errore, Schede } from './ui.jsx'

/** Colonna laterale di una pratica: documenti, storico, diagramma del processo. */
export default function Approfondimenti({ pratica, suggerimentoDocumenti, onDocumenti }) {
  const [scheda, setScheda] = useState('documenti')
  const [diagramma, setDiagramma] = useState(null)
  const [errore, setErrore] = useState('')

  useEffect(() => {
    if (scheda !== 'diagramma' || diagramma) return
    api.diagramma(pratica.id).then(setDiagramma).catch(e => setErrore(e.message))
  }, [scheda, diagramma, pratica.id])

  const voci = [
    { id: 'documenti', label: 'Documenti' },
    { id: 'storico', label: 'Storico', conteggio: pratica.eventi?.length },
    { id: 'diagramma', label: 'Processo' }
  ]

  return (
    <section className="rounded-pannello border border-linea bg-white shadow-pannello lg:sticky lg:top-20">
      <Schede voci={voci} attiva={scheda} onChange={setScheda} className="px-3" />
      <div className="max-h-none p-5 lg:max-h-[calc(100vh-11rem)] lg:overflow-y-auto">
        {scheda === 'documenti' && (
          <div className="space-y-3">
            {suggerimentoDocumenti && (
              <p className="flex items-start gap-2 rounded-campo bg-zafferano-50 px-3 py-2 text-sm text-zafferano-800">
                <Info className="mt-0.5 size-4 shrink-0" /> {suggerimentoDocumenti}
              </p>
            )}
            <DocumentsPanel pi={pratica.id} onChange={onDocumenti} />
          </div>
        )}
        {scheda === 'storico' && <Timeline eventi={pratica.eventi} />}
        {scheda === 'diagramma' && (
          errore ? <Errore>{errore}</Errore>
            : !diagramma ? <Caricamento />
            : <BpmnViewer xml={diagramma.xml} attivi={diagramma.activeIds} fatti={diagramma.completedIds} altezza={380} />
        )}
      </div>
    </section>
  )
}
