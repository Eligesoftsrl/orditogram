import { useEffect, useRef, useState } from 'react'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import { Maximize, Minus, Plus } from 'lucide-react'
import { camminoFinoA, conPassaggi, durataViaggio, flussiPercorsi, puntoLungo, tappeLungo } from '../lib/percorsoBpmn.js'
import { Errore } from './ui.jsx'
import { gl } from '../lib/glossario.js'

const fermo = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const SVG = 'http://www.w3.org/2000/svg'
const svg = (nome, attributi) => {
  const el = document.createElementNS(SVG, nome)
  Object.entries(attributi).forEach(([k, v]) => el.setAttribute(k, String(v)))
  el.setAttribute('aria-hidden', 'true')
  return el
}

/**
 * Diagramma BPMN in sola lettura (zoom con la rotella, trascinamento).
 * attivi: passi dove la pratica è ferma (alone blu che pulsa) · fatti: passi già percorsi (blu, si accendono in fila)
 * segnalino: ogni tanto un segnalino percorre le frecce già fatte fino al passo attuale.
 * viaggio: il resto del diagramma si spegne, il segnalino parte dall'inizio, lascia una scia blu e accende ogni passo
 *   che raggiunge, poi si ferma sul passo attuale; `giro` (un numero) lo fa ripartire, `onTappe` riceve le tappe
 *   ([{ id, nome }]) e `onTappa` l'indice di quella raggiunta.
 * comandi: pulsanti per ingrandire, rimpicciolire e adattare (utili col dito, dove non c'è la rotella).
 * Con "riduci il movimento" tutto è fermo: niente accensione in fila né segnalino che corre.
 */
export default function BpmnViewer({ xml, attivi = [], fatti = [], altezza = 340, segnalino = false, viaggio = false, giro = 0, onTappe, onTappa, comandi = false }) {
  const container = useRef(null)
  const viewer = useRef(null)
  const [errore, setErrore] = useState('')
  const avvisi = useRef({ onTappe, onTappa })
  avvisi.current = { onTappe, onTappa }

  useEffect(() => {
    viewer.current = new NavigatedViewer({ container: container.current })
    return () => viewer.current?.destroy()
  }, [])

  useEffect(() => {
    if (!xml || !viewer.current) return
    let annullato = false
    const timer = []
    let giroAnimazione = null
    setErrore('')
    viewer.current.importXML(xml)
      .then(() => {
        if (annullato) return
        const canvas = viewer.current.get('canvas')
        const registro = viewer.current.get('elementRegistry')
        canvas.zoom('fit-viewport', 'auto')
        // un sottoprocesso aperto contiene i passi: non si colora tutto, si colorano i passi dentro
        const contenitore = (id) => (registro.get(id)?.children || []).length > 0
        const segna = (id, cls) => {
          if (contenitore(id)) return
          try { canvas.removeMarker(id, 'bpmn-spento'); canvas.addMarker(id, cls) } catch { /* non nel diagramma */ }
          // l'etichetta esterna (eventi, gateway, frecce) si accende con il suo elemento
          const etichetta = registro.get(id)?.label
          if (etichetta) { try { canvas.removeMarker(etichetta.id, 'bpmn-spento') } catch { /* nessuna */ } }
        }
        const flussi = registro.filter(e => e.type === 'bpmn:SequenceFlow' && e.businessObject?.sourceRef && e.businessObject?.targetRef)
          .map(e => ({ id: e.id, da: e.businessObject.sourceRef.id, a: e.businessObject.targetRef.id, punti: e.waypoints }))
        const percorsi = flussiPercorsi(flussi, fatti, attivi)
        const strada = camminoFinoA(flussi, fatti, attivi)
        const punti = strada.map(id => flussi.find(f => f.id === id).punti).flat()

        if (viaggio) return avvia({ canvas, registro, flussi, percorsi, segna })

        attivi.forEach(id => segna(id, 'bpmn-corrente'))
        // i passi fatti si accendono uno dopo l'altro, nell'ordine in cui sono stati percorsi
        const daAccendere = [...fatti.filter(id => !attivi.includes(id)), ...percorsi]
        if (fermo()) daAccendere.forEach(id => segna(id, 'bpmn-fatto'))
        else daAccendere.forEach((id, i) => timer.push(setTimeout(() => !annullato && segna(id, 'bpmn-fatto'), 120 + Math.min(i, 24) * 70)))

        if (!segnalino || fermo() || punti.length < 2) return
        const strato = canvas.getLayer('orditogram-segnalino', 1000)
        const punto = svg('circle', { r: 7, fill: '#FFB000', stroke: '#fff', 'stroke-width': 2.5 })
        punto.style.opacity = '0'
        strato.appendChild(punto)
        const durata = Math.min(2600, 500 + punti.length * 120)
        const corri = () => {
          const inizio = performance.now()
          const passo = (ora) => {
            if (annullato) return
            const q = Math.min(1, (ora - inizio) / durata)
            const e = q < .5 ? 2 * q * q : 1 - Math.pow(-2 * q + 2, 2) / 2   // morbido all'inizio e alla fine
            const p = puntoLungo(punti, e)
            punto.setAttribute('transform', `translate(${p.x} ${p.y})`)
            punto.style.opacity = q < .08 ? String(q / .08) : q > .9 ? String((1 - q) / .1) : '1'
            if (q < 1) giroAnimazione = requestAnimationFrame(passo)
          }
          giroAnimazione = requestAnimationFrame(passo)
        }
        // la prima corsa dopo l'accensione dei passi, poi ogni 8 secondi
        timer.push(setTimeout(corri, 400 + Math.min(daAccendere.length, 24) * 70))
        timer.push(setInterval(corri, 8000))
      })
      .catch(e => !annullato && setErrore('Diagramma non visualizzabile: ' + e.message))

    /** Il viaggio: tutto spento, poi il segnalino corre dall'inizio al passo attuale accendendo la strada. */
    function avvia({ canvas, registro, flussi: frecce, percorsi, segna }) {
      // il segnalino entra nei sottoprocessi e si ferma su un passo vero (non sul riquadro né su un evento di bordo)
      const sottoprocessi = registro.filter(e => e.type === 'bpmn:SubProcess' && (e.children || []).length)
        .map(e => ({ id: e.id, inizi: e.children.filter(c => c.type === 'bpmn:StartEvent').map(c => ({ id: c.id, x: c.x + c.width / 2, y: c.y + c.height / 2 })) }))
      const flussi = conPassaggi(frecce, sottoprocessi)
      const ferma = (id) => { const e = registro.get(id); return e && !(e.children || []).length && e.type !== 'bpmn:BoundaryEvent' }
      const mete = attivi.filter(ferma)
      const strada = camminoFinoA(flussi, [...fatti, ...attivi.filter(id => !mete.includes(id))], mete)
      const punti = strada.map(id => flussi.find(f => f.id === id).punti).flat()
      const accesi = new Set([...fatti, ...attivi, ...percorsi])
      const radice = canvas.getRootElement()
      registro.forEach(e => {
        if (e === radice || e.type === 'bpmn:Participant' || e.type === 'bpmn:Lane' || contenitoreDi(e)) return
        const chi = e.labelTarget ? e.labelTarget.id : e.id
        if (!accesi.has(chi)) canvas.addMarker(e.id, 'bpmn-spento')
      })
      const nomeDi = (id) => registro.get(id)?.businessObject?.name?.trim() || ''
      const tappe = tappeLungo(flussi, strada)
      const elenco = (tappe.length ? tappe.map(t => t.id) : [...fatti, ...attivi])
        .map(id => ({ id, nome: nomeDi(id), tipo: registro.get(id)?.type || '', adesso: mete.includes(id) }))
      avvisi.current.onTappe?.(elenco)
      const arriva = (id, i) => {
        segna(id, attivi.includes(id) ? 'bpmn-corrente' : 'bpmn-fatto')
        if (i != null) avvisi.current.onTappa?.(i)
      }
      const fine = () => {
        // i rami fuori dalla strada più breve (cicli, rami paralleli) si accendono insieme all'arrivo
        ;[...fatti.filter(id => !attivi.includes(id)), ...percorsi].forEach(id => segna(id, 'bpmn-fatto'))
        attivi.forEach(id => segna(id, 'bpmn-corrente'))
        avvisi.current.onTappa?.(elenco.length - 1)
      }
      if (punti.length < 2) return fine()

      // sopra il disegno (dentro i sottoprocessi il fondo bianco la coprirebbe), sotto il segnalino
      const sotto = canvas.getLayer('orditogram-scia', 1)
      const sopra = canvas.getLayer('orditogram-segnalino', 1000)
      const d = 'M' + punti.map(p => `${p.x} ${p.y}`).join(' L')
      const scia = svg('path', { d, fill: 'none', class: 'viaggio-scia', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })
      const filo = svg('path', { d, fill: 'none', class: 'viaggio-filo', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })
      sotto.append(scia, filo)
      const lunghezza = filo.getTotalLength ? filo.getTotalLength() : 0
      const alone = svg('circle', { r: 15, class: 'viaggio-alone' })
      const punto = svg('circle', { r: 8, class: 'viaggio-segnalino' })
      const segnalino = svg('g', {})
      segnalino.append(alone, punto)
      sopra.appendChild(segnalino)
      const metti = (p) => p && segnalino.setAttribute('transform', `translate(${p.x} ${p.y})`)

      if (fermo() || !lunghezza) {
        tappe.forEach(t => { if (t.flusso && !t.flusso.startsWith('~')) segna(t.flusso, 'bpmn-fatto') })
        metti(punti[punti.length - 1])
        segnalino.classList.add('fermo')
        return fine()
      }
      ;[scia, filo].forEach(p => { p.style.strokeDasharray = `${lunghezza}`; p.style.strokeDashoffset = `${lunghezza}` })
      metti(punti[0])
      arriva(tappe[0].id, 0)
      const durata = durataViaggio(lunghezza)
      let prossima = 1
      const parti = () => {
        const inizio = performance.now()
        const passo = (ora) => {
          if (annullato) return
          const q = Math.min(1, (ora - inizio) / durata)
          const e = q < .5 ? 4 * q * q * q : 1 - Math.pow(-2 * q + 2, 3) / 2   // parte piano, corre, si posa piano
          const p = filo.getPointAtLength(e * lunghezza)
          metti(p)
          const resto = `${lunghezza * (1 - e)}`
          scia.style.strokeDashoffset = resto
          filo.style.strokeDashoffset = resto
          while (prossima < tappe.length && e >= tappe[prossima].a - 1e-6) {
            if (!tappe[prossima].flusso.startsWith('~')) segna(tappe[prossima].flusso, 'bpmn-fatto')
            arriva(tappe[prossima].id, prossima)
            prossima++
          }
          if (q < 1) giroAnimazione = requestAnimationFrame(passo)
          else { segnalino.classList.add('fermo'); fine() }
        }
        giroAnimazione = requestAnimationFrame(passo)
      }
      // si parte quando il cassetto ha finito di scorrere
      timer.push(setTimeout(parti, 450))
    }
    function contenitoreDi(e) { return (e.children || []).length > 0 && e.type !== 'label' }

    return () => { annullato = true; timer.forEach(t => { clearTimeout(t); clearInterval(t) }); if (giroAnimazione) cancelAnimationFrame(giroAnimazione) }
  }, [xml, attivi.join(','), fatti.join(','), segnalino, viaggio, giro])

  const zoom = (fattore) => {
    const canvas = viewer.current?.get('canvas')
    if (!canvas) return
    if (!fattore) canvas.zoom('fit-viewport', 'auto')
    else canvas.zoom(Math.min(4, Math.max(.2, canvas.zoom() * fattore)), 'auto')
  }

  return (
    <div>
      <div className="relative">
        <div ref={container} className={`bpmn-host isolate w-full rounded-campo bg-[radial-gradient(circle,var(--color-linea)_1px,transparent_1px)] bg-[size:18px_18px]${viaggio ? ' bpmn-viaggio' : ''}`}
             style={{ height: altezza }} />
        {comandi && (
          <div className="absolute right-3 top-3 flex flex-col overflow-hidden rounded-campo border border-linea bg-white shadow-pannello">
            <button type="button" onClick={() => zoom(1.25)} className="p-2 text-pino-800 hover:bg-carta" aria-label="Ingrandisci"><Plus className="size-4" /></button>
            <button type="button" onClick={() => zoom(.8)} className="border-t border-linea p-2 text-pino-800 hover:bg-carta" aria-label="Rimpicciolisci"><Minus className="size-4" /></button>
            <button type="button" onClick={() => zoom(0)} className="border-t border-linea p-2 text-pino-800 hover:bg-carta" aria-label="Adatta allo spazio"><Maximize className="size-4" /></button>
          </div>
        )}
      </div>
      {(attivi.length > 0 || fatti.length > 0) && (
        <div className="mt-3 flex flex-wrap gap-4 text-[13px] text-grafite">
          <span className="inline-flex items-center gap-1.5"><span className="size-3 animate-respiro rounded-sm border-2 border-verde-700 bg-verde-100" /> {gl('pratica', 'il')} è qui</span>
          <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm border border-verde-600 bg-verde-50" /> già percorso</span>
          {viaggio && <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm border border-pino-300 bg-white opacity-60" /> non percorso</span>}
          {(segnalino || viaggio) && <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-full border-2 border-white bg-focus ring-1 ring-pino-700" /> il cammino fatto</span>}
        </div>
      )}
      {errore && <div className="mt-3"><Errore>{errore}</Errore></div>}
    </div>
  )
}
