import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, CircleAlert, ChevronRight, FolderOpen, Hand, Inbox, Plus, Route, Timer, Users } from 'lucide-react'
import { api } from '../api.js'
import { useAuth } from '../lib/auth.jsx'
import { fa, giorno, saluto } from '../lib/format.js'
import { contaScadenze } from '../lib/scadenze.js'
import { inPrimoPiano, tonoScadenza } from '../lib/scrivania.js'
import IterRail from '../components/IterRail.jsx'
import AnteprimaPratica from '../components/AnteprimaPratica.jsx'
import GraficiScrivania from '../components/GraficiScrivania.jsx'
import { useAvvio } from '../components/Shell.jsx'
import { Badge, Button, Caricamento, Cassetto, Errore, Pannello, Tessera, Vuoto, useToast } from '../components/ui.jsx'
import { BadgeScadenza, principale } from '../components/Scadenza.jsx'

/** Ctrl/Cmd, Maiusc o clic centrale: il collegamento si apre come pagina, non nel pannello. */
const apreLaPagina = (e) => e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0

/**
 * Scrivania a tessere ("bento"): la tessera blu grande con le prime attività, le altre con la loro tinta,
 * poi gli elenchi. Un clic su un'attività o una pratica apre il riassunto nel pannello da destra.
 */
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
  // pannello laterale: { titolo, processo, idPratica, attivita? } e la pratica caricata
  const [aperta, setAperta] = useState(null)
  const [dettaglio, setDettaglio] = useState(null)
  // i grafici arrivano dopo: se non ci sono (utente esterno, errore) la scrivania resta com'è
  const [indicatori, setIndicatori] = useState(null)

  const carica = useCallback(async () => {
    try {
      const [t, p] = await Promise.all([api.tasks(), api.pratiche('in-corso', '', { dimensione: 8 })])
      setTasks(t); setPratiche(p.righe); setTotalePratiche(p.totale)
    } catch (e) { setErrore(e.message) }
  }, [])
  useEffect(() => { carica() }, [carica])
  useEffect(() => { if (!me.esterno) api.indicatori().then(setIndicatori).catch(() => setIndicatori(null)) }, [me.esterno])

  useEffect(() => {
    if (!aperta) { setDettaglio(null); return }
    let annullato = false
    setDettaglio(null)
    api.pratica(aperta.idPratica).then(p => !annullato && setDettaglio(p)).catch(e => !annullato && setDettaglio({ errore: e.message }))
    return () => { annullato = true }
  }, [aperta])

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
  const conteggi = contaScadenze(tasks)
  const prime = inPrimoPiano(mie, 3)

  const apriAttivita = (e, t) => {
    if (apreLaPagina(e)) return
    e.preventDefault()
    setAperta({ titolo: t.pratica || 'Pratica senza titolo', processo: t.processo, idPratica: t.processInstanceId, attivita: t })
  }
  const apriPratica = (e, p) => {
    if (apreLaPagina(e)) return
    e.preventDefault()
    setAperta({ titolo: p.titolo, processo: p.processo, idPratica: p.id })
  }

  return (
    <div className="space-y-7">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-[15px] text-grafite">{giorno(new Date())}</p>
          <h1 className="mt-1 text-[36px] font-bold leading-tight tracking-tight">{saluto()}, {nome}</h1>
          <p className="mt-1.5 max-w-2xl text-[17px] text-pino-800">{riassunto(mie.length, libere.length)}</p>
        </div>
        <Button variante="primario" misura="lg" icona={Plus} className="hidden lg:inline-flex" onClick={() => apriAvvio()}>Nuova pratica</Button>
      </header>

      {/* bento: la tessera blu grande e quattro tessere di tinte diverse */}
      <div className="in-sequenza grid grid-cols-2 gap-4 lg:grid-cols-4" aria-label="Riepilogo">
        <Tessera tono="blu" grande icona={Inbox} etichetta="Tocca a te" valore={mie.length} className="col-span-2 lg:row-span-2"
                 pastiglia={libere.length ? `+${libere.length} nel gruppo` : null}
                 nota={mie.length ? null : 'Niente in carico a te: prendi un\'attività del gruppo per iniziare.'}>
          {prime.length > 0 && (
            <ul className="mt-auto space-y-2 pt-2">
              {prime.map(t => (
                <li key={t.id}>
                  <Link to={`/attivita/${t.id}`} onClick={(e) => apriAttivita(e, t)}
                        className="flex items-center justify-between gap-3 rounded-blocco bg-white/12 px-4 py-3 text-white transition-[background-color,transform] duration-200 hover:translate-x-0.5 hover:bg-white/20">
                    <span className="min-w-0">
                      <span className="block truncate font-semibold">{t.name}</span>
                      <span className="block truncate text-sm text-verde-50">{t.pratica || 'Pratica senza titolo'}</span>
                    </span>
                    <PastigliaScadenza t={t} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Tessera>
        <Tessera tono="lilla" icona={Users} etichetta="Del tuo gruppo" valore={libere.length}
                 nota={libere.length ? 'da prendere in carico' : 'nessuna in attesa'} />
        <Tessera tono="acqua" icona={Timer} etichetta="In scadenza" valore={conteggi.vicine}
                 nota={conteggi.vicine ? 'entro 2 giorni' : 'nulla nei prossimi 2 giorni'} />
        <Tessera tono={conteggi.scadute ? 'rosa' : 'neutro'} icona={CircleAlert} etichetta="Scadute" valore={conteggi.scadute}
                 nota={conteggi.scadute ? 'da recuperare' : 'tutto nei tempi'} />
        <Tessera tono="ambra" icona={FolderOpen} etichetta="Pratiche in corso" valore={totalePratiche}
                 nota="che puoi vedere" as={Link} to="/pratiche" />
      </div>

      <GraficiScrivania dati={indicatori} />

      <div className="in-sequenza grid gap-6 xl:grid-cols-[1.35fr_1fr] [&>*]:min-w-0">
        <div className="space-y-6">
          <Pannello titolo={<Titolo testo="Tocca a te" n={mie.length} accento />} corpoClassName="p-0">
            {mie.length === 0
              ? <Vuoto icona={Inbox} titolo="Niente in carico a te">Prendi in carico un'attività del tuo gruppo per iniziare.</Vuoto>
              : <ul className="in-sequenza divide-y divide-linea">{mie.map(t => <RigaAttivita key={t.id} t={t} onApri={apriAttivita} />)}</ul>}
          </Pannello>

          <Pannello titolo={<Titolo testo="Disponibili per il tuo gruppo" n={libere.length} />} corpoClassName="p-0">
            {libere.length === 0
              ? <Vuoto titolo="Nessuna attività in attesa">Quando arriva una nuova attività per i tuoi gruppi la trovi qui.</Vuoto>
              : (
                <ul className="in-sequenza divide-y divide-linea">
                  {libere.map(t => (
                    <RigaAttivita key={t.id} t={t} onApri={apriAttivita} azione={
                      <Button misura="sm" icona={Hand} attesa={presa === t.id} onClick={(e) => { e.preventDefault(); e.stopPropagation(); void prendi(t) }}>
                        Lavora
                      </Button>
                    } />
                  ))}
                </ul>
              )}
          </Pannello>
        </div>

        <Pannello titolo={<Titolo testo="Pratiche in corso" n={totalePratiche} />}
                  azioni={<Link to="/pratiche" className="text-sm font-semibold text-verde-700 hover:underline">Tutte le pratiche</Link>}
                  corpoClassName="p-0">
          {pratiche.length === 0
            ? <Vuoto icona={Route} titolo="Nessuna pratica in corso" azione={<Button misura="sm" icona={Plus} onClick={() => apriAvvio()}>Avvia una pratica</Button>}>
                Le pratiche che avvii o su cui lavori compaiono qui, con il loro percorso.
              </Vuoto>
            : (
              <ul className="in-sequenza divide-y divide-linea">
                {pratiche.slice(0, 8).map(p => (
                  <li key={p.id}>
                    <Link to={`/pratiche/${p.id}`} onClick={(e) => apriPratica(e, p)} className="block px-5 py-4 transition-colors hover:bg-verde-50/60">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-pino-900">{p.titolo}</p>
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

      <Cassetto aperto={!!aperta} onClose={() => setAperta(null)} icona={aperta?.attivita ? Inbox : FolderOpen}
                titolo={aperta?.titolo || ''} sottotitolo={aperta?.attivita ? `${aperta.attivita.name}, ${aperta.processo || ''}` : aperta?.processo}
                piede={aperta && <>
                  <Button onClick={() => navigate(`/pratiche/${aperta.idPratica}`)}>Apri la pratica</Button>
                  {aperta.attivita && (aperta.attivita.mine
                    ? <Button variante="primario" icona={ArrowRight} onClick={() => navigate(`/attivita/${aperta.attivita.id}`)}>Lavora l'attività</Button>
                    : !aperta.attivita.assignee && <Button variante="primario" icona={Hand} attesa={presa === aperta.attivita.id} onClick={() => prendi(aperta.attivita)}>Prendi e lavora</Button>)}
                </>}>
        {!dettaglio ? <Caricamento /> : dettaglio.errore ? <Errore>{dettaglio.errore}</Errore> : <AnteprimaPratica p={dettaglio} />}
      </Cassetto>
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

/** La scadenza sulla tessera blu: pastiglia bianca, con parola e simbolo (mai il solo colore). */
function PastigliaScadenza({ t }) {
  const s = principale(t.scadenze)
  if (!s || s.tipo !== 'scadenza') return <span className="shrink-0 text-sm text-verde-50">{fa(t.createdDate)}</span>
  const tono = tonoScadenza(t)
  const testo = tono === 'mattone' ? '! scaduta' : `◷ ${fa(s.quando)}`
  return <span className="shrink-0 whitespace-nowrap rounded-full bg-white px-2.5 py-0.5 text-[13px] font-semibold text-pino-950">{testo}</span>
}

function Titolo({ testo, n, accento }) {
  return (
    <h2 className="flex items-center gap-2 text-lg font-semibold">
      {accento && <span className="size-2 animate-respiro rounded-full bg-verde-600" />}
      {testo}
      <span className="numeri text-[15px] font-normal text-grafite">{n}</span>
    </h2>
  )
}

function RigaAttivita({ t, azione, onApri }) {
  return (
    <li>
      <Link to={`/attivita/${t.id}`} onClick={(e) => onApri(e, t)} className="flex items-center gap-4 px-5 py-3.5 transition-colors hover:bg-verde-50/60">
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-pino-900">{t.pratica || 'Pratica senza titolo'}</p>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <Badge tono={t.mine ? 'blu' : 'neutro'}>{t.name}</Badge>
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
