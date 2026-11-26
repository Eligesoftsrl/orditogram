import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Hand, Undo2 } from 'lucide-react'
import { api } from '../api.js'
import { prossimaAttivita } from '../lib/prosegui.js'
import IterRail from '../components/IterRail.jsx'
import DynamicForm from '../components/DynamicForm.jsx'
import { Badge, Button, Caricamento, Errore, Pannello, useToast } from '../components/ui.jsx'
import { BadgeScadenza } from '../components/Scadenza.jsx'
import Approfondimenti from '../components/Approfondimenti.jsx'
import ProcessoVivo from '../components/ProcessoVivo.jsx'

/** Lavorare un'attività: il form a tutta larghezza; documenti e storico si aprono nel pannello da destra. */
export default function TaskPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [task, setTask] = useState(null)
  const [pratica, setPratica] = useState(null)
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const carica = useCallback(async () => {
    try {
      const t = await api.task(id)
      setTask(t)
      setPratica(await api.pratica(t.processInstanceId))
    } catch (e) { setErrore(e.message) }
  }, [id])
  useEffect(() => { carica() }, [carica])

  const azione = async (fn, msg) => {
    setAttesa(true)
    try { await fn(); toast.ok(msg); await carica() } catch (e) { toast.errore(e.message) } finally { setAttesa(false) }
  }

  const completa = async (values) => {
    setAttesa(true)
    try {
      await api.complete(task.id, values)
      // il passo successivo della stessa pratica tocca ancora a me? si continua senza tornare alla scrivania
      const prossima = await prossimaAttivita(task.processInstanceId)
      if (prossima) {
        toast.ok(`"${task.name}" completata: ora "${prossima.name}"`)
        setTask(null); setPratica(null); setAttesa(false)
        navigate(`/attivita/${prossima.id}`)
      } else {
        toast.ok(`"${task.name}" completata`)
        navigate('/')
      }
    } catch (e) {
      toast.errore(e.message)
      setAttesa(false)
    }
  }

  if (errore) return <div className="space-y-4"><Indietro /><Errore>{errore}</Errore></div>
  if (!task || !pratica) return <Caricamento />

  const altrui = task.assignee && !task.mine
  const modoDocumenti = task.form?.documenti

  return (
    <div className="space-y-6">
      <Indietro />

      <header className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm text-grafite">
              <Link to={`/pratiche/${pratica.id}`} className="hover:text-pino-900 hover:underline">{pratica.processo}</Link>
            </p>
            <h1 className="mt-1 text-[30px] font-semibold leading-tight">{pratica.titolo}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <Badge tono="zafferano">{task.name}</Badge>
              {(task.scadenze || []).map((s, i) => <BadgeScadenza key={i} s={s} />)}
              {task.mine && <span className="text-sm text-grafite">in carico a te</span>}
              {altrui && <span className="text-sm text-grafite">in carico a {task.assigneeName}</span>}
              {!task.assignee && <span className="text-sm text-grafite">disponibile per il tuo gruppo: lavorala qui, confermando diventa tua</span>}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Approfondimenti pratica={pratica} richiesti={!!task.form?.documentiObbligatori && modoDocumenti === 'modifica'} onDocumenti={carica} />
            {!task.assignee && (
              <Button variante="fantasma" icona={Hand} attesa={attesa} title="Riservala a te: i colleghi la vedono in carico a te"
                      onClick={() => azione(() => api.claim(task.id), 'Attività riservata a te')}>
                Riservala a me
              </Button>
            )}
            {task.mine && <Button variante="fantasma" icona={Undo2} attesa={attesa} onClick={() => azione(() => api.release(task.id), 'Attività rilasciata al gruppo')}>Rilascia</Button>}
          </div>
        </div>
        <div className="rounded-pannello border border-linea bg-white px-5 pb-3 pt-4 shadow-pannello">
          <IterRail percorso={pratica.percorso} />
        </div>
      </header>

      <Pannello titolo={task.form?.title || task.name}>
        {altrui && <div className="mb-5"><Errore>Questa attività è in carico a {task.assigneeName}: puoi consultarla ma non completarla.</Errore></div>}
        {task.form
          ? <DynamicForm key={task.id} form={task.form} initialValues={task.variables} organizzazioni={task.organizzazioni || {}} disabled={altrui}
                         busy={attesa} onSubmit={completa} mostraTitolo={false} />
          : !altrui && (
            <div className="flex items-center justify-between gap-4">
              <p className="text-grafite">Questa attività non ha un form: conferma quando l'hai svolta.</p>
              <Button variante="primario" attesa={attesa} onClick={() => completa({})}>Completa</Button>
            </div>
          )}
      </Pannello>

      <ProcessoVivo id={pratica.id} altezza={300} />
    </div>
  )
}

function Indietro() {
  return (
    <Link to="/" className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
      <ArrowLeft className="size-4" /> Scrivania
    </Link>
  )
}
