import { useEffect, useId, useState } from 'react'
import { Briefcase, Copy, UserPlus } from 'lucide-react'
import { api } from '../api.js'
import { fa } from '../lib/format.js'
import { azioniPersona, statoPersona } from '../lib/referente.js'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../components/ui.jsx'
import { useGlossario } from '../lib/useGlossario.jsx'
import { gl } from '../lib/glossario.js'

const VUOTA = { nome: '', cognome: '', email: '' }

/**
 * Portale → La mia organizzazione: il referente vede le persone della sua organizzazione, ne aggiunge,
 * le disattiva, le riattiva o le toglie. Chi vede quali pratiche non cambia: lo decide l'organizzazione.
 */
export default function MiaOrganizzazione() {
  const g = useGlossario()
  const [lista, setLista] = useState(null)
  const [errore, setErrore] = useState('')
  const carica = () => api.mieOrganizzazioni().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])

  if (!lista) return errore ? <Errore>{errore}</Errore> : <Caricamento />
  return (
    <div className="space-y-6">
      <IntestazionePagina percorso={[{ label: 'La mia organizzazione' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">La mia organizzazione</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Come referente, decidi chi lavora per la tua organizzazione. Le persone che aggiungi vedono {gl('pratica', 'i')} in cui
            l'organizzazione è parte.
          </p>
        </div>
      </IntestazionePagina>
      {lista.length === 0
        ? <Pannello><Vuoto icona={Briefcase} titolo="Non sei referente di nessuna organizzazione">Se pensi sia un errore, contatta {g('ente', 'il')}.</Vuoto></Pannello>
        : lista.map(o => <Organizzazione key={o.id} org={o} onCambio={carica} />)}
    </div>
  )
}

function Organizzazione({ org, onCambio }) {
  const toast = useToast()
  const [nuova, setNuova] = useState(null)
  const [creata, setCreata] = useState(null)
  const [errore, setErrore] = useState('')

  const agisci = async (fn, ok) => {
    setErrore('')
    try { await fn(); toast.ok(ok); onCambio() } catch (e) { setErrore(e.message) }
  }

  return (
    <Pannello corpoClassName="p-0" titolo={org.nome}
              azioni={<Button variante="primario" misura="sm" icona={UserPlus} onClick={() => setNuova({ ...VUOTA })}>Aggiungi persona</Button>}>
      {errore && <div className="border-b border-linea p-4"><Errore>{errore}</Errore></div>}
      <ul className="divide-y divide-linea">
        {org.persone.map(p => {
          const stato = statoPersona(p)
          const azioni = azioniPersona(p)
          const nome = [p.nome, p.cognome].filter(Boolean).join(' ') || p.username
          return (
            <li key={p.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
              <span className="min-w-0 flex-1">
                <span className="block font-medium text-pino-900">{nome}{p.io && ' (tu)'}</span>
                <span className="block text-[13px] text-grafite">
                  {p.email || p.username}
                  {p.ultimoAccesso && ` · ultimo accesso ${fa(p.ultimoAccesso)}`}
                </span>
              </span>
              {p.referente && <Badge tono="pino">Referente</Badge>}
              {p.altreOrganizzazioni && <Badge>Anche in altre organizzazioni</Badge>}
              <Badge tono={stato.tono}>{stato.testo}</Badge>
              {azioni.attiva && (p.attivo
                ? <Button misura="sm" onClick={() => confirm(`Disattivare ${nome}? Non potrà più entrare.`) && agisci(() => api.disattivaPersona(org.id, p.id), `${nome} disattivata`)}>Disattiva</Button>
                : <Button misura="sm" onClick={() => agisci(() => api.riattivaPersona(org.id, p.id), `${nome} riattivata`)}>Riattiva</Button>)}
              {azioni.togli && (
                <Button misura="sm" variante="fantasma" onClick={() => confirm(`Togliere ${nome} da ${org.nome}?`) && agisci(() => api.togliPersona(org.id, p.id), `${nome} tolta da ${org.nome}`)}>Togli</Button>
              )}
            </li>
          )
        })}
      </ul>
      <NuovaPersona org={org} dati={nuova} onClose={() => setNuova(null)}
                    onCreata={(esito) => { setNuova(null); setCreata(esito); onCambio() }} />
      <PasswordCreata esito={creata} onClose={() => setCreata(null)} />
    </Pannello>
  )
}

function NuovaPersona({ org, dati, onClose, onCreata }) {
  const uid = useId()
  const [p, setP] = useState(VUOTA)
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)
  useEffect(() => { if (dati) { setP(dati); setErrore('') } }, [dati])
  const set = (k, v) => setP(x => ({ ...x, [k]: v }))

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try { onCreata(await api.aggiungiPersona(org.id, p)) } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  return (
    <Dialogo aperto={!!dati} onClose={onClose} titolo="Aggiungi persona" sottotitolo={org.nome} larghezza="max-w-lg">
      <form onSubmit={salva} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="etichetta" htmlFor={`${uid}-n`}>Nome *</label>
            <input id={`${uid}-n`} className="campo" required value={p.nome} onChange={e => set('nome', e.target.value)} />
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-c`}>Cognome *</label>
            <input id={`${uid}-c`} className="campo" required value={p.cognome} onChange={e => set('cognome', e.target.value)} />
          </div>
        </div>
        <div>
          <label className="etichetta" htmlFor={`${uid}-e`}>Email *</label>
          <input id={`${uid}-e`} type="email" className="campo" required value={p.email} onChange={e => set('email', e.target.value)}
                 aria-describedby={`${uid}-h`} />
          <p id={`${uid}-h`} className="mt-1 text-sm text-grafite">È anche il nome con cui entra.</p>
        </div>
        <Errore>{errore}</Errore>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button type="button" variante="fantasma" onClick={onClose}>Annulla</Button>
          <Button type="submit" variante="primario" attesa={attesa}>Aggiungi</Button>
        </div>
      </form>
    </Dialogo>
  )
}

function PasswordCreata({ esito, onClose }) {
  const g = useGlossario()
  const toast = useToast()
  if (!esito) return null
  const c = esito.collega
  return (
    <Dialogo aperto onClose={onClose} titolo="Persona aggiunta" larghezza="max-w-lg">
      <div className="space-y-4">
        {esito.passwordTemporanea ? (<>
          <p>Comunica a {c.nome} questi dati, di persona o per telefono. La password si vede <strong>solo ora</strong>: al primo accesso dovrà sceglierne una sua.</p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-campo bg-carta p-4">
            <dt className="text-grafite">Nome utente</dt><dd className="font-mono">{c.username}</dd>
            <dt className="text-grafite">Password</dt>
            <dd className="flex items-center gap-2 font-mono">
              {esito.passwordTemporanea}
              <button type="button" className="rounded-md p-1 text-grafite hover:bg-white" aria-label="Copia la password"
                      onClick={() => navigator.clipboard?.writeText(esito.passwordTemporanea).then(() => toast.ok('Password copiata'))}>
                <Copy className="size-4" />
              </button>
            </dd>
          </dl>
        </>) : (
          <p>{c.nome} entra con l'accesso {g('ente', 'del')} usando l'indirizzo {c.email}: al primo accesso sarà già nella tua organizzazione.</p>
        )}
        <div className="flex justify-end"><Button variante="primario" onClick={onClose}>Fatto</Button></div>
      </div>
    </Dialogo>
  )
}
