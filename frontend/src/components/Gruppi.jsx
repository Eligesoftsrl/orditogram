import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import { Button } from './ui.jsx'

/** Elenco di gruppi modificabile: chip con la x, campo per aggiungere, suggerimenti tra i gruppi già noti. */
export default function Gruppi({ valori, noti, onChange, titolo = 'Gruppi', vuoto = 'Nessun gruppo' }) {
  const [nuovo, setNuovo] = useState('')
  const aggiungi = (g) => { const v = g.trim(); if (v && !valori.includes(v)) onChange([...valori, v]); setNuovo('') }
  const suggeriti = noti.filter(g => !valori.includes(g))
  return (
    <div>
      <p className="etichetta">{titolo}</p>
      <div className="flex flex-wrap gap-2">
        {valori.length === 0 && <span className="text-sm text-grafite">{vuoto}</span>}
        {valori.map(g => (
          <span key={g} className="inline-flex items-center gap-1 rounded-full bg-verde-50 py-1 pl-3 pr-1.5 text-sm text-verde-700 ring-1 ring-verde-100">
            {g}
            <button type="button" onClick={() => onChange(valori.filter(x => x !== g))} className="rounded-full p-0.5 hover:bg-white" aria-label={`Togli ${g}`}><X className="size-3.5" /></button>
          </span>
        ))}
      </div>
      <div className="mt-2 flex gap-2">
        <input className="campo h-9" value={nuovo} placeholder="Aggiungi un gruppo" onChange={e => setNuovo(e.target.value)}
               onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); aggiungi(nuovo) } }} />
        <Button type="button" misura="sm" icona={Plus} onClick={() => aggiungi(nuovo)}>Aggiungi</Button>
      </div>
      {suggeriti.length > 0 && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <span className="text-[13px] text-grafite">Esistenti:</span>
          {suggeriti.map(g => (
            <button key={g} type="button" onClick={() => aggiungi(g)} className="rounded-full px-2.5 py-0.5 text-[13px] text-pino-800 ring-1 ring-linea hover:bg-carta">+ {g}</button>
          ))}
        </div>
      )}
    </div>
  )
}
