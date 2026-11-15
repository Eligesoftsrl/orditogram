import { useEffect, useRef, useState } from 'react'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import { Errore } from './ui.jsx'

/**
 * Diagramma BPMN in sola lettura (zoom con la rotella, trascinamento).
 * attivi: step correnti (zafferano) · fatti: step già percorsi (verde)
 */
export default function BpmnViewer({ xml, attivi = [], fatti = [], altezza = 340 }) {
  const container = useRef(null)
  const viewer = useRef(null)
  const [errore, setErrore] = useState('')

  useEffect(() => {
    viewer.current = new NavigatedViewer({ container: container.current })
    return () => viewer.current?.destroy()
  }, [])

  useEffect(() => {
    if (!xml || !viewer.current) return
    let annullato = false
    setErrore('')
    viewer.current.importXML(xml)
      .then(() => {
        if (annullato) return
        const canvas = viewer.current.get('canvas')
        canvas.zoom('fit-viewport', 'auto')
        const segna = (id, cls) => { try { canvas.addMarker(id, cls) } catch { /* non nel diagramma */ } }
        fatti.filter(id => !attivi.includes(id)).forEach(id => segna(id, 'bpmn-fatto'))
        attivi.forEach(id => segna(id, 'bpmn-corrente'))
      })
      .catch(e => !annullato && setErrore('Diagramma non visualizzabile: ' + e.message))
    return () => { annullato = true }
  }, [xml, attivi.join(','), fatti.join(',')])

  return (
    <div>
      <div ref={container} className="bpmn-host isolate w-full rounded-campo bg-[radial-gradient(circle,var(--color-linea)_1px,transparent_1px)] bg-[size:18px_18px]"
           style={{ height: altezza }} />
      {(attivi.length > 0 || fatti.length > 0) && (
        <div className="mt-3 flex flex-wrap gap-4 text-xs text-grafite">
          <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm border-2 border-zafferano-500 bg-zafferano-50" /> in corso</span>
          <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm border border-verde-600 bg-verde-50" /> già percorso</span>
        </div>
      )}
      {errore && <div className="mt-3"><Errore>{errore}</Errore></div>}
    </div>
  )
}
