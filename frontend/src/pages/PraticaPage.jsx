import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, BookUser, Briefcase, Building2, CalendarDays, Hourglass, Inbox, UserRound, Workflow } from 'lucide-react'
import { api } from '../api.js'
import { giorno, quando } from '../lib/format.js'
import Specchietto from '../components/Specchietto.jsx'
import Approfondimenti from '../components/Approfondimenti.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, useToast } from '../components/ui.jsx'
import { BadgeScadenza } from '../components/Scadenza.jsx'
import { TabellaLettura } from '../components/CampoTabella.jsx'

/** Scheda di una pratica: dove si trova, i suoi dati, documenti e storico. */
export default function PraticaPage() {
  const { id } = useParams()
  const [p, setP] = useState(null)
  const [errore, setErrore] = useState('')
  const [invio, setInvio] = useState(null)
  const [daConfermare, setDaConfermare] = useState(null)
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
    setDaConfermare(null)
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
              {conclusa ? <Badge tono="pino">Conclusa</Badge> : <Badge tono="blu">In corso</Badge>}
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
        <Specchietto p={p} />
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          {!conclusa && (p.stepCorrenti.length > 0 || attese.length > 0 || messaggi.length > 0) && (
            <Pannello titolo="A che punto è">
              <ul className="space-y-3">
                {p.stepCorrenti.map((s, i) => (
                  <li key={i} className="flex items-start gap-3">
                    <span className="mt-1.5 size-2.5 shrink-0 rounded-full border-[3px] border-verde-600 bg-white animate-respiro" />
                    <div>
                      <p className="font-medium text-pino-900">{s.nome}</p>
                      <p className="text-sm text-grafite">
                        {s.assegnatario ? `In carico a ${s.assegnatario}`
                          : s.gruppi.length ? `Disponibile per ${s.gruppi.join(', ')}: chiunque del gruppo può lavorarla` : 'In lavorazione'}
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
                    <Button misura="sm" variante="fantasma" icona={Inbox} attesa={invio === m.nome} onClick={() => setDaConfermare(m)}>Registra arrivo</Button>
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

          <Parti parti={p.parti || []} />

          <AnagraficheCollegate voci={p.anagrafiche || []} />

          <Collegate madre={p.praticaMadre} figlie={p.praticheFiglie || []} />
        </div>

        <Approfondimenti pratica={p} onDocumenti={carica} />
      </div>

      <Dialogo aperto={!!daConfermare} onClose={() => setDaConfermare(null)} larghezza="max-w-lg"
               titolo={daConfermare ? `Registrare l'arrivo di "${daConfermare.elemento}"?` : ''}>
        <div className="space-y-4">
          <p className="text-pino-800">La pratica prosegue subito dal punto previsto nel processo, come se il messaggio fosse arrivato da un sistema esterno.</p>
          <p className="rounded-blocco bg-carta px-3 py-2 text-sm text-grafite">Resta nello storico della pratica con il tuo nome e l'ora.</p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button onClick={() => setDaConfermare(null)}>Annulla</Button>
            <Button variante="primario" icona={Inbox} onClick={() => registra(daConfermare)}>Registra l'arrivo</Button>
          </div>
        </div>
      </Dialogo>
    </div>
  )
}

/** Le parti della pratica: chi la riguarda dall'esterno (richiedente, controparte...), persona o organizzazione. */
function Parti({ parti }) {
  if (parti.length === 0) return null
  return (
    <Pannello titolo="Parti">
      <ul className="space-y-2.5">
        {parti.map(x => (
          <li key={x.ruolo} className="flex items-start gap-3">
            {x.utente ? <UserRound className="mt-0.5 size-4 shrink-0 text-pino-700" /> : <Briefcase className="mt-0.5 size-4 shrink-0 text-pino-700" />}
            <div className="min-w-0">
              <p className="text-[13px] text-grafite">{x.etichetta}</p>
              <p className="text-[15px] font-medium text-pino-900">{[x.utente, x.organizzazione].filter(Boolean).join(', ')}</p>
            </div>
          </li>
        ))}
      </ul>
    </Pannello>
  )
}

/**
 * Le schede di anagrafica scelte nei form: nome e dati come erano quando sono state scelte (la fotografia),
 * con un avviso se oggi la scheda ha un altro nome o è cessata. Il link c'è solo se l'utente può vederla.
 */
function AnagraficheCollegate({ voci }) {
  if (voci.length === 0) return null
  return (
    <Pannello titolo="Anagrafiche">
      <ul className="space-y-2.5">
        {voci.map(a => {
          const avvisi = [
            a.visibile && a.titoloAttuale && a.titoloAttuale !== a.titolo && `oggi si chiama ${a.titoloAttuale}`,
            a.visibile && a.statoAttuale === 'CESSATA' && 'oggi è cessata'
          ].filter(Boolean)
          const corpo = (
            <>
              <BookUser className="mt-0.5 size-4 shrink-0 text-pino-700" aria-hidden="true" />
              <span className="min-w-0 flex-1">
                <span className="block text-[13px] text-grafite">{a.etichetta}{a.tipoNome ? `, ${a.tipoNome}` : ''}</span>
                <span className="block text-[15px] font-medium text-pino-900">{a.titolo}</span>
                {a.collegataIl && <span className="block text-[13px] text-grafite">Dati della versione {a.versione}, scelta {quando(a.collegataIl)}</span>}
                {avvisi.length > 0 && <span className="block text-[13px] text-zafferano-800">{avvisi.join(', ')}</span>}
              </span>
            </>
          )
          return (
            <li key={a.campo}>
              {a.visibile && a.id
                ? <Link to={`/anagrafiche/${a.tipo}/${a.id}`} className="flex items-start gap-3 rounded-campo px-1 py-1 hover:bg-carta">{corpo}<ArrowRight className="mt-1 size-4 text-nebbia" aria-hidden="true" /></Link>
                : <div className="flex items-start gap-3 px-1 py-1">{corpo}</div>}
            </li>
          )
        })}
      </ul>
    </Pannello>
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
        {c.stato === 'conclusa' ? <Badge tono="pino">Conclusa</Badge> : <Badge tono="blu">In corso</Badge>}
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
