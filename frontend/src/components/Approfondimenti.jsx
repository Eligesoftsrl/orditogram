import { useState } from 'react'
import { Info } from 'lucide-react'
import DocumentsPanel from './DocumentsPanel.jsx'
import Timeline from './Timeline.jsx'
import { Schede } from './ui.jsx'

/** Colonna laterale di una pratica: documenti e storico (il diagramma del processo è in cima alla pagina). */
export default function Approfondimenti({ pratica, suggerimentoDocumenti, onDocumenti }) {
  const [scheda, setScheda] = useState('documenti')

  const voci = [
    { id: 'documenti', label: 'Documenti' },
    { id: 'storico', label: 'Storico', conteggio: pratica.eventi?.length }
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
      </div>
    </section>
  )
}
