import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Building2, CalendarDays, Hourglass, Inbox, UserRound, Workflow } from 'lucide-react'
import { api } from '../api.js'
import { giorno, quando } from '../lib/format.js'
import IterRail from '../components/IterRail.jsx'
import Approfondimenti from '../components/Approfondimenti.jsx'
import { Badge, Button, Caricamento, Errore, Pannello, useToast } from '../components/ui.jsx'
import { BadgeScadenza } from '../components/Scadenza.jsx'
import { TabellaLettura } from '../components/CampoTabella.jsx'

/** Scheda di una pratica: dove si trova, i suoi dati, documenti e storico. */
export default function PraticaPage() {
  const { id } = useParams()
  const [p, setP] = useState(null)
  const [errore, setErrore] = useState('')
  const [invio, setInvio] = useState(null)
  const toast = useToast()

  const carica = useCallback(() => api.pratica(id).then(setP).catch(e => setErrore(e.message)), [id])
  useEffect(() => { carica() }, [carica])

  if (errore) return <div className="space-y-4"><Indietro /><Errore>{errore}</Errore></div>
  if (!p) return <Caricamento />

  const conclusa = p.stato === 'conclusa'
  const scadenze = p.scadenze || []
  const attese = scadenze.filter(x => x.tipo === 'attesa')
  const messaggi = p.messaggiAttesi || []
  const registra = async (m) => {
    if (!confirm(`Registrare l'arrivo di "${m.elemento}"? La pratica prosegue subito.`)) return
    setInvio(m.nome)
    try { await api.registraMessaggio(p.id, m.nome); toast.ok(`Arrivo di "${m.elemento}" registrato`); await carica() }
    catch (e) { toast.errore(e.message) } finally { setInvio(null) }
  }

  return (
    <div className="space-y-6">
      <Indietro />

      <header className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm text-grafite">{p.processo}</p>
            <h1 className="mt-1 text-[30px] font-semibold leading-tight">{p.titolo}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-grafite">
              {conclusa ? <Badge tono="pino">Conclusa</Badge> : <Badge tono="zafferano">In corso</Badge>}
              {!conclusa && p.prossimaScadenza && <BadgeScadenza s={p.prossimaScadenza} />}
              {p.avviataDa && <span className="inline-flex items-center gap-1.5"><UserRound className="size-4" /> {p.avviataDa}</span>}
              {p.struttura && <span className="inline-flex items-center gap-1.5" title="Struttura competente"><Building2 className="size-4" /> {p.struttura.nome}</span>}
              <span className="inline-flex items-center gap-1.5"><CalendarDays className="size-4" />
                {conclusa ? `${quando(p.inizio)} – ${quando(p.fine)}` : `dal ${quando(p.inizio)}`}
              </span>
            </div>
          </div>
          {p.taskId && (
            <Link to={`/attivita/${p.taskId}`}
                  className="inline-flex h-10 items-center gap-2 rounded-campo bg-verde-600 px-4 font-medium text-white hover:bg-verde-700">
              Lavora l'attività <ArrowRight className="size-4" />
            </Link>
          )}
        </div>
        <div className="rounded-pannello border border-linea bg-white px-5 pb-3 pt-4 shadow-pannello">
          <IterRail percorso={p.percorso} />
        </div>
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          {!conclusa && (p.stepCorrenti.length > 0 || attese.length > 0 || messaggi.length > 0) && (
            <Pannello titolo="A che punto è">
              <ul className="space-y-3">
                {p.stepCorrenti.map((s, i) => (
                  <li key={i} className="flex items-start gap-3">
                    <span className="mt-1.5 size-2.5 shrink-0 rounded-full border-2 border-zafferano-400 bg-zafferano-100" />
                    <div>
                      <p className="font-medium text-pino-900">{s.nome}</p>
                      <p className="text-sm text-grafite">
                        {s.assegnatario ? `In carico a ${s.assegnatario}` : `Disponibile per ${s.gruppi.join(', ') || 'il gruppo'}: chiunque del gruppo può lavorarla`}
                        {s.dal && `, dal ${quando(s.dal)}`}
                      </p>
                      {scadenze.filter(x => x.attivita && x.attivita === s.chiave).length > 0 && (
                        <ul className="mt-1.5 flex flex-wrap gap-1.5">
                          {scadenze.filter(x => x.attivita === s.chiave).map((x, j) => (
                            <li key={j} className="inline-flex items-center gap-1.5 text-[13px] text-grafite">
                              <BadgeScadenza s={x} /> <span>{quando(x.quando)}</span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </li>
                ))}
                {messaggi.map((m, i) => (
                  <li key={'m' + i} className="flex items-start gap-3">
                    <Inbox className="mt-0.5 size-4 shrink-0 text-verde-700" />
                    <div className="min-w-0 flex-1">
                      <p className="font-medium text-pino-900">{m.tipo === 'bordo' ? `Può arrivare: ${m.elemento}` : `In attesa: ${m.elemento}`}</p>
                      <p className="text-sm text-grafite">Arriva da un sistema esterno, oppure lo registri tu quando lo ricevi (es. una PEC).</p>
                    </div>
                    <Button misura="sm" variante="fantasma" icona={Inbox} attesa={invio === m.nome} onClick={() => registra(m)}>Registra arrivo</Button>
                  </li>
                ))}
                {attese.map((a, i) => (
                  <li key={'a' + i} className="flex items-start gap-3">
                    <Hourglass className="mt-0.5 size-4 shrink-0 text-grafite" />
                    <div>
                      <p className="font-medium text-pino-900">{a.nome}</p>
                      <p className="text-sm text-grafite">La pratica è in attesa e riprende da sola il {quando(a.quando)}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </Pannello>
          )}

          <Pannello titolo="Dati della pratica">
            {p.dati.length === 0
              ? <p className="text-grafite">Nessun dato registrato.</p>
              : (
                <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
                  {p.dati.map(d => (
                    <div key={d.nome} className={d.tipo === 'textarea' || d.tipo === 'tabella' || String(d.value).length > 60 ? 'sm:col-span-2' : ''}>
                      <dt className="text-[13px] text-grafite">{d.label}{d.tipo === 'tabella' && <span>, {d.value}</span>}</dt>
                      {d.tipo === 'tabella' ? (
                        <dd><TabellaLettura righe={d.righe} colonne={(d.colonne || []).map(c => ({ name: c.nome, label: c.label, type: c.tipo }))} /></dd>
                      ) : (
                        <dd className="mt-0.5 whitespace-pre-line break-words text-[15px] text-pino-900">
                          {d.tipo === 'date' ? giorno(d.value) : d.value}
                        </dd>
                      )}
                    </div>
                  ))}
                </dl>
              )}
          </Pannello>

          <Collegate madre={p.praticaMadre} figlie={p.praticheFiglie || []} />
        </div>

        <Approfondimenti pratica={p} onDocumenti={carica} />
      </div>
    </div>
  )
}

/** Pratiche aperte dai processi richiamati (figlie) o che hanno aperto questa (madre). */
function Collegate({ madre, figlie }) {
  if (!madre && figlie.length === 0) return null
  const voce = (c, ruolo) => (
    <li key={c.id}>
      <Link to={`/pratiche/${c.id}`} className="flex items-center gap-3 rounded-campo border border-linea bg-white px-3 py-2.5 hover:border-pino-200">
        <Workflow className="size-4 shrink-0 text-pino-700" />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[15px] font-medium text-pino-900">{c.processo}</span>
          <span className="block text-[13px] text-grafite">{ruolo}, dal {quando(c.inizio)}</span>
        </span>
        {c.stato === 'conclusa' ? <Badge tono="pino">Conclusa</Badge> : <Badge tono="zafferano">In corso</Badge>}
        <ArrowRight className="size-4 text-nebbia" />
      </Link>
    </li>
  )
  return (
    <Pannello titolo="Pratiche collegate">
      <ul className="space-y-2">
        {madre && voce(madre, 'Pratica che ha aperto questa')}
        {figlie.map(f => voce(f, 'Aperta da questa pratica'))}
      </ul>
    </Pannello>
  )
}

function Indietro() {
  return (
    <Link to="/pratiche" className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
      <ArrowLeft className="size-4" /> Pratiche
    </Link>
  )
}
