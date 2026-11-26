import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ChevronRight, Workflow } from 'lucide-react'
import { api } from '../api.js'
import { prossimaAttivita } from '../lib/prosegui.js'
import DynamicForm from './DynamicForm.jsx'
import { Button, Caricamento, Dialogo, Errore, Vuoto, useToast } from './ui.jsx'
import { gl, nuovaPratica } from '../lib/glossario.js'

/** "Nuova pratica": scelta del processo, poi il suo form di avvio (con eventuali allegati). */
export default function StartDialog({ aperto, onClose, chiaveIniziale }) {
  const [processi, setProcessi] = useState(null)
  const [scelto, setScelto] = useState(null)
  const [attesa, setAttesa] = useState(false)
  const [errore, setErrore] = useState('')
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    if (!aperto) return
    setScelto(null); setErrore('')
    api.processes().then(p => {
      setProcessi(p)
      const iniziale = p.find(x => x.key === chiaveIniziale) || (p.length === 1 ? p[0] : null)
      if (iniziale) setScelto(iniziale)
    }).catch(e => setErrore(e.message))
  }, [aperto, chiaveIniziale])

  const avvia = async (values, files) => {
    setAttesa(true); setErrore('')
    try {
      const r = await api.start(scelto.key, values, files)
      // se il primo passo tocca a chi ha avviato, si prosegue subito lì
      const prossima = await prossimaAttivita(r.id)
      onClose()
      if (prossima) {
        toast.ok(`${gl('pratica', 'Nome')} "${r.name}" ${gl.agg('pratica', 'avviat')}: prosegui con "${prossima.name}"`)
        navigate(`/attivita/${prossima.id}`)
      } else {
        toast.ok(`${gl('pratica', 'Nome')} "${r.name}" ${gl.agg('pratica', 'avviat')}`)
        navigate(`/pratiche/${r.id}`)
      }
    } catch (e) {
      setErrore(e.message)
    } finally {
      setAttesa(false)
    }
  }

  return (
    <Dialogo aperto={aperto} onClose={onClose}
             titolo={scelto ? (scelto.startForm?.title || scelto.name) : nuovaPratica()}
             sottotitolo={scelto ? scelto.name : `Scegli il tipo di ${gl('pratica', 'nome')} da avviare`}>
      {!processi && !errore && <Caricamento />}
      {errore && !scelto && <Errore>{errore}</Errore>}

      {processi && !scelto && (
        processi.length === 0
          ? <Vuoto icona={Workflow} titolo="Nessun processo da avviare">Non ci sono processi che il tuo profilo può avviare. Chiedi all'amministratore di abilitarti.</Vuoto>
          : (
            <ul className="divide-y divide-linea overflow-hidden rounded-pannello border border-linea">
              {processi.map(p => (
                <li key={p.key}>
                  <button onClick={() => setScelto(p)} className="flex w-full items-center gap-4 px-4 py-3.5 text-left hover:bg-carta">
                    <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-campo bg-verde-50 text-verde-700">
                      <Workflow className="size-5" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-medium text-pino-900">{p.name}</span>
                      {(p.description || p.startForm?.description) && (
                        <span className="mt-0.5 line-clamp-2 block text-sm text-grafite">{p.description || p.startForm.description}</span>
                      )}
                    </span>
                    <ChevronRight className="size-5 text-nebbia" />
                  </button>
                </li>
              ))}
            </ul>
          )
      )}

      {scelto && (
        <div className="space-y-4">
          {processi?.length > 1 && (
            <button onClick={() => setScelto(null)} className="inline-flex items-center gap-1 text-sm font-medium text-grafite hover:text-pino-900">
              <ArrowLeft className="size-4" /> Altri processi
            </button>
          )}
          {errore && <Errore>{errore}</Errore>}
          {scelto.startForm
            ? <DynamicForm key={scelto.key} form={{ ...scelto.startForm, title: null }} avvio busy={attesa} onSubmit={avvia} />
            : (
              <div className="flex items-center justify-between gap-4 rounded-pannello bg-carta px-4 py-4">
                <p className="text-[15px] text-pino-900">Questo processo non richiede dati iniziali.</p>
                <Button variante="primario" attesa={attesa} onClick={() => avvia({}, [])}>Avvia</Button>
              </div>
            )}
        </div>
      )}
    </Dialogo>
  )
}
