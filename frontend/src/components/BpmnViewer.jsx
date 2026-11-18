import { useEffect, useRef, useState } from 'react'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import { camminoFinoA, flussiPercorsi, puntoLungo } from '../lib/percorsoBpmn.js'
import { Errore } from './ui.jsx'

const fermo = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const SVG = 'http://www.w3.org/2000/svg'

/**
 * Diagramma BPMN in sola lettura (zoom con la rotella, trascinamento).
 * attivi: passi dove la pratica è ferma (alone blu che pulsa) · fatti: passi già percorsi (blu, si accendono in fila)
 * segnalino: ogni tanto un segnalino percorre le frecce già fatte fino al passo attuale.
 * Con "riduci il movimento" tutto è fermo: niente accensione in fila né segnalino.
 */
export default function BpmnViewer({ xml, attivi = [], fatti = [], altezza = 340, segnalino = false }) {
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
    const timer = []
    let giro = null
    setErrore('')
    viewer.current.importXML(xml)
      .then(() => {
        if (annullato) return
        const canvas = viewer.current.get('canvas')
        const registro = viewer.current.get('elementRegistry')
        canvas.zoom('fit-viewport', 'auto')
        // un sottoprocesso aperto contiene i passi: non si colora tutto, si colorano i passi dentro
        const contenitore = (id) => (registro.get(id)?.children || []).length > 0
        const segna = (id, cls) => { if (contenitore(id)) return; try { canvas.addMarker(id, cls) } catch { /* non nel diagramma */ } }
        const flussi = registro.filter(e => e.type === 'bpmn:SequenceFlow' && e.businessObject?.sourceRef && e.businessObject?.targetRef)
          .map(e => ({ id: e.id, da: e.businessObject.sourceRef.id, a: e.businessObject.targetRef.id, punti: e.waypoints }))
        const percorsi = flussiPercorsi(flussi, fatti, attivi)
        attivi.forEach(id => segna(id, 'bpmn-corrente'))
        // i passi fatti si accendono uno dopo l'altro, nell'ordine in cui sono stati percorsi
        const daAccendere = [...fatti.filter(id => !attivi.includes(id)), ...percorsi]
        if (fermo()) daAccendere.forEach(id => segna(id, 'bpmn-fatto'))
        else daAccendere.forEach((id, i) => timer.push(setTimeout(() => !annullato && segna(id, 'bpmn-fatto'), 120 + Math.min(i, 24) * 70)))

        if (!segnalino || fermo()) return
        const strada = camminoFinoA(flussi, fatti, attivi).map(id => flussi.find(f => f.id === id).punti).flat()
        if (strada.length < 2) return
        const strato = canvas.getLayer('orditogram-segnalino', 1000)
        const punto = document.createElementNS(SVG, 'circle')
        punto.setAttribute('r', '7')
        punto.setAttribute('fill', '#FFB000')
        punto.setAttribute('stroke', '#fff')
        punto.setAttribute('stroke-width', '2.5')
        punto.setAttribute('aria-hidden', 'true')
        punto.style.opacity = '0'
        strato.appendChild(punto)
        const durata = Math.min(2600, 500 + strada.length * 120)
        const corri = () => {
          const inizio = performance.now()
          const passo = (ora) => {
            if (annullato) return
            const q = Math.min(1, (ora - inizio) / durata)
            const e = q < .5 ? 2 * q * q : 1 - Math.pow(-2 * q + 2, 2) / 2   // morbido all'inizio e alla fine
            const p = puntoLungo(strada, e)
            punto.setAttribute('transform', `translate(${p.x} ${p.y})`)
            punto.style.opacity = q < .08 ? String(q / .08) : q > .9 ? String((1 - q) / .1) : '1'
            if (q < 1) giro = requestAnimationFrame(passo)
          }
          giro = requestAnimationFrame(passo)
        }
        // la prima corsa dopo l'accensione dei passi, poi ogni 8 secondi
        timer.push(setTimeout(corri, 400 + Math.min(daAccendere.length, 24) * 70))
        timer.push(setInterval(corri, 8000))
      })
      .catch(e => !annullato && setErrore('Diagramma non visualizzabile: ' + e.message))
    return () => { annullato = true; timer.forEach(t => { clearTimeout(t); clearInterval(t) }); if (giro) cancelAnimationFrame(giro) }
  }, [xml, attivi.join(','), fatti.join(','), segnalino])

  return (
    <div>
      <div ref={container} className="bpmn-host isolate w-full rounded-campo bg-[radial-gradient(circle,var(--color-linea)_1px,transparent_1px)] bg-[size:18px_18px]"
           style={{ height: altezza }} />
      {(attivi.length > 0 || fatti.length > 0) && (
        <div className="mt-3 flex flex-wrap gap-4 text-[13px] text-grafite">
          <span className="inline-flex items-center gap-1.5"><span className="size-3 animate-respiro rounded-sm border-2 border-verde-700 bg-verde-100" /> la pratica è qui</span>
          <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm border border-verde-600 bg-verde-50" /> già percorso</span>
          {segnalino && <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-full border-2 border-white bg-focus ring-1 ring-linea" /> il cammino fatto</span>}
        </div>
      )}
      {errore && <div className="mt-3"><Errore>{errore}</Errore></div>}
    </div>
  )
}
