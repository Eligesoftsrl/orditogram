import { useEffect, useId, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '../api.js'
import { AMBITI, AZIONI_PROCESSO } from '../lib/permessi.js'
import { Button, Caricamento, Dialogo, Errore, useToast } from './ui.jsx'
import { gl } from '../lib/glossario.js'

/**
 * Studio → Processi → Accesso: chi avvia il processo, chi vede tutte le sue pratiche (con l'ambito) e chi le esporta.
 * Le regole valgono subito, senza ripubblicare, e viaggiano nel pacchetto del processo.
 */
export default function AccessoProcesso({ aperto, onClose, processo, onSalvato }) {
  const uid = useId()
  const toast = useToast()
  const [regole, setRegole] = useState(null)
  const [gruppi, setGruppi] = useState([])
  const [ruoli, setRuoli] = useState([])
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  useEffect(() => {
    if (!aperto || !processo) return
    setRegole(null); setErrore('')
    api.permessiProcesso(processo.key).then(setRegole).catch(e => setErrore(e.message))
    api.gruppi().then(g => setGruppi((g || []).map(x => x.nome))).catch(() => {})
    // l'elenco dei ruoli lo vede chi gestisce gli utenti; agli altri resta il campo libero
    api.ruoli().then(setRuoli).catch(() => setRuoli([]))
  }, [aperto, processo])

  const aggiungi = (azione) => setRegole(r => [...r, { azione, tipoSoggetto: 'GRUPPO', soggetto: '', ambito: 'TUTTE' }])
  const cambia = (i, k, v) => setRegole(r => r.map((x, j) => j === i ? { ...x, [k]: v } : x))
  const togli = (i) => setRegole(r => r.filter((_, j) => j !== i))

  const salva = async () => {
    setAttesa(true); setErrore('')
    try {
      const pulite = regole.filter(r => r.soggetto.trim())
      await api.salvaPermessiProcesso(processo.key, pulite)
      toast.ok('Accesso al processo salvato')
      onSalvato?.(pulite)
      onClose()
    } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }

  return (
    <Dialogo aperto={aperto} onClose={onClose} titolo={`Accesso: ${processo?.name || ''}`}
             sottotitolo="Vale subito per tutte le versioni, senza ripubblicare. Chi lavora le attività resta deciso dai gruppi del BPMN.">
      {!regole ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : (
        <div className="space-y-6">
          <datalist id={`${uid}-gruppi`}>{gruppi.map(g => <option key={g} value={g} />)}</datalist>
          <datalist id={`${uid}-ruoli`}>{ruoli.map(r => <option key={r.codice} value={r.codice}>{r.nome}</option>)}</datalist>
          {AZIONI_PROCESSO.map(a => {
            const righe = regole.map((r, i) => ({ r, i })).filter(x => x.r.azione === a.codice)
            return (
              <fieldset key={a.codice} className="space-y-2">
                <legend className="text-[15px] font-semibold text-pino-900">{a.titolo}</legend>
                <p className="text-[13px] text-grafite">{a.aiuto}</p>
                {righe.length === 0 && (
                  <p className="rounded-campo bg-carta px-3 py-2 text-sm text-grafite">
                    {a.codice === 'AVVIA' ? 'Come nel BPMN' : a.codice === 'VEDE' ? `Nessuno oltre a chi lavora ${gl('pratica', 'il')}` : `Chiunque veda ${gl('pratica', 'i')}`}
                  </p>
                )}
                {righe.map(({ r, i }) => (
                  <div key={i} className="flex flex-wrap items-center gap-2 rounded-campo bg-carta px-3 py-2">
                    <select className="campo h-9 w-auto py-0" aria-label="Gruppo o ruolo" value={r.tipoSoggetto}
                            onChange={e => cambia(i, 'tipoSoggetto', e.target.value)}>
                      <option value="GRUPPO">Gruppo</option>
                      <option value="RUOLO">Ruolo</option>
                    </select>
                    <input className="campo h-9 min-w-0 flex-1" aria-label={r.tipoSoggetto === 'RUOLO' ? 'Codice del ruolo' : 'Nome del gruppo'}
                           list={r.tipoSoggetto === 'RUOLO' ? `${uid}-ruoli` : `${uid}-gruppi`}
                           placeholder={r.tipoSoggetto === 'RUOLO' ? 'es. auditor' : 'es. supervisori'}
                           value={r.soggetto} onChange={e => cambia(i, 'soggetto', e.target.value)} />
                    {a.codice === 'VEDE' && (
                      <select className="campo h-9 w-auto py-0" aria-label={`Quali ${gl('pratica', 'nomi')}`} value={r.ambito}
                              onChange={e => cambia(i, 'ambito', e.target.value)}>
                        {AMBITI.map(x => <option key={x.codice} value={x.codice}>{x.nome}</option>)}
                      </select>
                    )}
                    <button type="button" onClick={() => togli(i)} className="rounded-md p-1.5 text-grafite hover:bg-white hover:text-mattone-600"
                            aria-label="Togli la regola"><Trash2 className="size-4" /></button>
                  </div>
                ))}
                <Button type="button" misura="sm" variante="fantasma" icona={Plus} onClick={() => aggiungi(a.codice)}>Aggiungi</Button>
              </fieldset>
            )
          })}
          <Errore>{errore}</Errore>
          <div className="flex justify-end gap-2 border-t border-linea pt-4">
            <Button variante="fantasma" onClick={onClose}>Annulla</Button>
            <Button variante="primario" attesa={attesa} onClick={salva}>Salva</Button>
          </div>
        </div>
      )}
    </Dialogo>
  )
}
