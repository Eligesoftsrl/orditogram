import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronRight, Hand, Inbox, Plus, Route } from 'lucide-react'
import { api } from '../api.js'
import { useAuth } from '../lib/auth.jsx'
import { fa, giorno, saluto } from '../lib/format.js'
import IterRail from '../components/IterRail.jsx'
import { useAvvio } from '../components/Shell.jsx'
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast } from '../components/ui.jsx'
import { BadgeScadenza, principale } from '../components/Scadenza.jsx'

export default function Scrivania() {
  const { me } = useAuth()
  const apriAvvio = useAvvio()
  const navigate = useNavigate()
  const toast = useToast()
  const [tasks, setTasks] = useState(null)
  const [pratiche, setPratiche] = useState(null)
  const [errore, setErrore] = useState('')
  const [presa, setPresa] = useState(null)
  const [totalePratiche, setTotalePratiche] = useState(0)

  const carica = useCallback(async () => {
    try {
      const [t, p] = await Promise.all([api.tasks(), api.pratiche('in-corso', '', { dimensione: 8 })])
      setTasks(t); setPratiche(p.righe); setTotalePratiche(p.totale)
    } catch (e) { setErrore(e.message) }
  }, [])
  useEffect(() => { carica() }, [carica])

  const prendi = async (t) => {
    setPresa(t.id)
    try {
      await api.claim(t.id)
      toast.ok(`"${t.name}" è in carico a te`)
      navigate(`/attivita/${t.id}`)
    } catch (e) { toast.errore(e.message); carica() } finally { setPresa(null) }
  }

  if (errore) return <Errore>{errore}</Errore>
  if (!tasks || !pratiche) return <Caricamento />

  const mie = tasks.filter(t => t.mine)
  const libere = tasks.filter(t => !t.assignee)
  const nome = me.fullName.split(' ')[0]

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-grafite">{giorno(new Date())}</p>
          <h1 className="mt-1 text-[34px] font-semibold leading-tight">{saluto()}, {nome}</h1>
          <p className="mt-2 max-w-2xl text-[17px] text-pino-800">{riassunto(mie.length, libere.length)}</p>
        </div>
      </header>

      <div className="grid gap-6 xl:grid-cols-[1.35fr_1fr]">
        <div className="space-y-6">
          <Pannello titolo={<Titolo testo="Tocca a te" n={mie.length} accento />} corpoClassName="p-0">
            {mie.length === 0
              ? <Vuoto icona={Inbox} titolo="Niente in carico a te">Prendi in carico un'attività del tuo gruppo per iniziare.</Vuoto>
              : <ul className="divide-y divide-linea">{mie.map(t => <RigaAttivita key={t.id} t={t} />)}</ul>}
          </Pannello>

          <Pannello titolo={<Titolo testo="Disponibili per il tuo gruppo" n={libere.length} />} corpoClassName="p-0">
            {libere.length === 0
              ? <Vuoto titolo="Nessuna attività in attesa">Quando arriva una nuova attività per i tuoi gruppi la trovi qui.</Vuoto>
              : (
                <ul className="divide-y divide-linea">
                  {libere.map(t => (
                    <RigaAttivita key={t.id} t={t} azione={
                      <Button misura="sm" icona={Hand} attesa={presa === t.id} onClick={(e) => { e.preventDefault(); prendi(t) }}>
                        Lavora
                      </Button>
                    } />
                  ))}
                </ul>
              )}
          </Pannello>
        </div>

        <Pannello titolo={<Titolo testo="Pratiche in corso" n={totalePratiche} />}
                  azioni={<Link to="/pratiche" className="text-sm font-medium text-verde-700 hover:underline">Tutte le pratiche</Link>}
                  corpoClassName="p-0">
          {pratiche.length === 0
            ? <Vuoto icona={Route} titolo="Nessuna pratica in corso" azione={<Button misura="sm" icona={Plus} onClick={() => apriAvvio()}>Avvia una pratica</Button>}>
                Le pratiche che avvii o su cui lavori compaiono qui, con il loro percorso.
              </Vuoto>
            : (
              <ul className="divide-y divide-linea">
                {pratiche.slice(0, 8).map(p => (
                  <li key={p.id}>
                    <Link to={`/pratiche/${p.id}`} className="block px-5 py-4 hover:bg-carta/70">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate font-medium text-pino-900">{p.titolo}</p>
                          <p className="mt-0.5 text-[13px] text-grafite">{p.processo}</p>
                        </div>
                        <IterRail percorso={p.percorso} compatto className="mt-1.5 shrink-0" />
                      </div>
                      <p className="mt-2 text-[13px] text-pino-800">
                        {p.stepCorrenti.map(s => s.nome).join(', ') || 'In avanzamento'}
                        <span className="text-grafite">
                          {p.stepCorrenti[0]?.assegnatario ? `, ${p.stepCorrenti[0].assegnatario}` : p.stepCorrenti[0] ? ', disponibile per il gruppo' : ''}
                        </span>
                      </p>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
        </Pannello>
      </div>
    </div>
  )
}

function riassunto(mie, libere) {
  if (mie === 0 && libere === 0) return 'Non hai attività in sospeso. Puoi avviare una nuova pratica o consultare quelle in corso.'
  const a = mie === 1 ? 'un\'attività in carico' : `${mie} attività in carico`
  const b = libere === 1 ? 'una disponibile per il tuo gruppo' : `${libere} disponibili per il tuo gruppo`
  if (mie === 0) return `Hai ${b.replace('una disponibile', 'un\'attività disponibile')}.`
  if (libere === 0) return `Hai ${a}.`
  return `Hai ${a} e ${b}.`
}

function Titolo({ testo, n, accento }) {
  return (
    <h2 className="flex items-center gap-2 text-[17px] font-semibold">
      {accento && <span className="size-2 rounded-full bg-zafferano-400" />}
      {testo}
      <span className="numeri text-[15px] font-normal text-grafite">{n}</span>
    </h2>
  )
}

function RigaAttivita({ t, azione }) {
  return (
    <li>
      <Link to={`/attivita/${t.id}`} className="flex items-center gap-4 px-5 py-3.5 hover:bg-carta/70">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-pino-900">{t.pratica || 'Pratica senza titolo'}</p>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <Badge tono={t.mine ? 'zafferano' : 'neutro'}>{t.name}</Badge>
            <BadgeScadenza s={principale(t.scadenze)} />
            {t.processo && t.processo !== t.name && <span className="truncate text-[13px] text-grafite">{t.processo}</span>}
          </div>
        </div>
        <span className="hidden text-[13px] text-grafite sm:block">{fa(t.createdDate)}</span>
        {azione || <ChevronRight className="size-5 text-nebbia" />}
      </Link>
    </li>
  )
}
