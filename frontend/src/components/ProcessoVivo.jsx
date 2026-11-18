import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../lib/auth.jsx'
import BpmnViewer from './BpmnViewer.jsx'
import { Caricamento, Errore } from './ui.jsx'

/**
 * Il diagramma BPMN vero, sempre in vista: il passo dove la pratica è ferma pulsa, i passi fatti si accendono
 * in fila e ogni tanto un segnalino percorre il cammino fatto. Non per gli esterni (il processo è dell'ente).
 */
export default function ProcessoVivo({ id, altezza = 360 }) {
  const { me } = useAuth()
  const [d, setD] = useState(null)
  const [errore, setErrore] = useState('')
  useEffect(() => { if (!me?.esterno) api.diagramma(id).then(setD).catch(e => setErrore(e.message)) }, [id, me?.esterno])
  if (me?.esterno) return null
  return (
    <section className="rounded-pannello border border-linea bg-white p-5 shadow-pannello" aria-labelledby="processo-titolo">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="processo-titolo" className="text-lg font-semibold">Il processo</h2>
        <p className="text-sm text-grafite">Trascina per spostarti, rotella per ingrandire</p>
      </div>
      {errore ? <Errore>{errore}</Errore>
        : !d ? <Caricamento />
        : <BpmnViewer xml={d.xml} attivi={d.activeIds} fatti={d.completedIds} altezza={altezza} segnalino />}
    </section>
  )
}

