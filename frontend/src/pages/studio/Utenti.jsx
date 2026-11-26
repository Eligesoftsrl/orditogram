import { useEffect, useId, useMemo, useState } from 'react'
import { Search, UserPlus, Users, X } from 'lucide-react'
import { api } from '../../api.js'
import { useAuth } from '../../lib/auth.jsx'
import { cx } from '../../lib/format.js'
import { Avatar, Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'
import Gruppi from '../../components/Gruppi.jsx'
import { alberoStrutture } from '../../lib/permessi.js'
import { useGlossario } from '../../lib/useGlossario.jsx'
import { gl } from '../../lib/glossario.js'

const VUOTO = { id: null, username: '', firstName: '', lastName: '', email: '', enabled: true, tipo: 'INTERNO', ruoli: [], groups: [], organizzazioni: [], password: '' }

/**
 * Studio: utenti, gruppi e ruoli. I gruppi sono quelli usati nei BPMN (candidateGroups, candidateStarterGroups);
 * i ruoli danno i permessi dello Studio e dell'amministrazione (vedi Ruoli.jsx).
 */
export default function Utenti() {
  const g = useGlossario()
  const uid = useId()
  const { me } = useAuth()
  const toast = useToast()
  const [lista, setLista] = useState(null)
  const [edit, setEdit] = useState(null)
  const [cerca, setCerca] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const [ruoli, setRuoli] = useState([])
  const [strutture, setStrutture] = useState([])
  const [esterne, setEsterne] = useState([])

  const carica = () => api.users().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => {
    void carica()
    api.ruoli().then(setRuoli).catch(() => {})
    api.strutture().then(setStrutture).catch(() => {})
    api.organizzazioniAdmin().then(setEsterne).catch(() => {})
  }, [])
  const nomeRuolo = (codice) => ruoli.find(r => r.codice === codice)?.nome || codice

  const gruppi = useMemo(() => [...new Set((lista || []).flatMap(u => u.groups))].sort((a, b) => a.localeCompare(b, 'it')), [lista])
  const filtrati = (lista || []).filter(u =>
    !cerca || `${u.firstName} ${u.lastName} ${u.username} ${u.groups.join(' ')}`.toLowerCase().includes(cerca.toLowerCase()))

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      if (edit.id) await api.updateUser(edit.id, edit); else await api.createUser(edit)
      toast.ok(`Utente ${edit.username} salvato`)
      setEdit(null); void carica()
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  const elimina = async () => {
    if (!confirm(`Eliminare l'utente ${edit.username}?`)) return
    try { await api.deleteUser(edit.id); toast.ok(`Utente ${edit.username} eliminato`); setEdit(null); void carica() }
    catch (err) { setErrore(err.message) }
  }

  const set = (k, v) => setEdit(p => ({ ...p, [k]: v }))

  return (
    <div className="space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Utenti' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Utenti</h1>
          <p className="mt-1 max-w-2xl text-grafite">Chi accede, a quali gruppi appartiene e con quali ruoli. I gruppi decidono chi lavora le attività dei processi, i ruoli che cosa si può fare nello Studio.</p>
        </div>
        <div className="flex gap-2">
          <Esporta nome="utenti" titolo="Utenti" disabled={!lista} sottotitolo={cerca ? `ricerca "${cerca}"` : ''}
                   righe={filtrati}
                   colonne={[
                     { titolo: 'Utente', valore: u => u.username },
                     { titolo: 'Nome', valore: u => u.firstName },
                     { titolo: 'Cognome', valore: u => u.lastName },
                     { titolo: 'Email', valore: u => u.email },
                     { titolo: 'Gruppi', valore: u => (u.groups || []).join(', ') },
                     { titolo: 'Ruoli', valore: u => [...(u.ruoli || []), ...(u.ruoliDaGruppi || [])].map(nomeRuolo).join(', ') },
                     { titolo: 'Attivo', valore: u => u.enabled ? 'Sì' : 'No' }
                   ]} />
          <Button variante="primario" icona={UserPlus} onClick={() => { setErrore(''); setEdit({ ...VUOTO }) }}>Nuovo utente</Button>
        </div>
      </IntestazionePagina>

      {!lista ? <Caricamento /> : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <Pannello corpoClassName="p-0" titolo={
            <label className="relative w-full max-w-xs">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-nebbia" />
              <input className="campo h-9 pl-9" placeholder="Cerca per nome o gruppo" value={cerca} onChange={e => setCerca(e.target.value)} aria-label="Cerca utenti" />
            </label>
          } azioni={<span className="numeri text-sm text-grafite">{filtrati.length} utenti</span>}>
            {filtrati.length === 0 ? <Vuoto icona={Users} titolo="Nessun utente trovato" /> : (
              <ul className="divide-y divide-linea">
                {filtrati.map(u => {
                  const nome = [u.firstName, u.lastName].filter(Boolean).join(' ') || u.username
                  return (
                    <li key={u.id}>
                      <button onClick={() => { setErrore(''); setEdit({ ...u, password: '' }) }}
                              className={cx('flex w-full items-center gap-3 px-5 py-3 text-left transition-colors',
                                edit?.id === u.id ? 'bg-verde-50/70' : 'hover:bg-carta', !u.enabled && 'opacity-55')}>
                        <Avatar nome={nome} />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-medium text-pino-900">{nome}</span>
                          <span className="block truncate text-[13px] text-grafite">{u.username}{u.email ? `, ${u.email}` : ''}</span>
                        </span>
                        <span className="hidden flex-wrap justify-end gap-1 sm:flex">
                          {u.tipo === 'ESTERNO' && <Badge tono="zafferano">esterno</Badge>}
                          {[...(u.ruoli || []), ...(u.ruoliDaGruppi || [])].map(r => <Badge key={`r-${r}`} tono="pino">{nomeRuolo(r)}</Badge>)}
                          {!u.enabled && <Badge tono="mattone">disattivato</Badge>}
                          {u.groups.map(g => <Badge key={g}>{g}</Badge>)}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </Pannello>

          {edit ? (
            <Pannello titolo={edit.id ? 'Modifica utente' : 'Nuovo utente'}
                      azioni={<button onClick={() => setEdit(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi"><X className="size-5" /></button>}
                      className="lg:sticky lg:top-20">
              <form onSubmit={salva} className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div><label className="etichetta" htmlFor={`${uid}-1`}>Nome</label><input id={`${uid}-1`} className="campo" value={edit.firstName || ''} onChange={e => set('firstName', e.target.value)} /></div>
                  <div><label className="etichetta" htmlFor={`${uid}-2`}>Cognome</label><input id={`${uid}-2`} className="campo" value={edit.lastName || ''} onChange={e => set('lastName', e.target.value)} /></div>
                  <div><label className="etichetta" htmlFor={`${uid}-3`}>Nome utente *</label><input id={`${uid}-3`} className="campo" required readOnly={!!edit.id} value={edit.username} onChange={e => set('username', e.target.value.trim())} /></div>
                  <div><label className="etichetta" htmlFor={`${uid}-4`}>Email</label><input id={`${uid}-4`} className="campo" type="email" value={edit.email || ''} onChange={e => set('email', e.target.value)} placeholder="per le notifiche via email" /></div>
                  <div className="sm:col-span-2">
                    <label className="etichetta" htmlFor={`${uid}-5`}>{edit.id ? 'Nuova password' : 'Password *'}</label>
                    <input id={`${uid}-5`} className="campo" type="password" required={!edit.id} value={edit.password} onChange={e => set('password', e.target.value)}
                           placeholder={edit.id ? 'Lascia vuoto per non cambiarla' : ''} autoComplete="new-password" />
                  </div>
                </div>
                <fieldset>
                  <legend className="etichetta">Tipo di utente</legend>
                  <div className="grid gap-2 sm:grid-cols-2">
                    {[['INTERNO', 'Personale', `Lavora ${g('pratica', 'i')} ${g('ente', 'del')}`],
                      ['ESTERNO', 'Esterno', `${g('esterno', 'Nome')} o referente di un'organizzazione: vede solo ${g('pratica', 'i')} in cui è parte`]].map(([v, t, d]) => (
                      <label key={v} className={cx('flex cursor-pointer items-start gap-2 rounded-campo border px-3 py-2.5',
                        (edit.tipo || 'INTERNO') === v ? 'border-verde-500 bg-verde-50/60' : 'border-linea')}>
                        <input type="radio" name={`${uid}-tipo`} className="mt-1 accent-verde-600" checked={(edit.tipo || 'INTERNO') === v}
                               onChange={() => setEdit(p => ({ ...p, tipo: v, ruoli: [], organizzazioni: [] }))} />
                        <span><span className="font-medium text-pino-900">{t}</span><span className="block text-[13px] text-grafite">{d}</span></span>
                      </label>
                    ))}
                  </div>
                </fieldset>
                <Gruppi valori={edit.groups} noti={gruppi} onChange={g => set('groups', g)} />
                {edit.tipo === 'ESTERNO' ? (
                  <SceltaOrganizzazioni organizzazioni={esterne} scelte={edit.organizzazioni || []} onChange={v => set('organizzazioni', v)} />
                ) : (<>
                  <SceltaRuoli ruoli={ruoli} scelti={edit.ruoli || []} daGruppi={edit.id ? (edit.ruoliDaGruppi || []) : []}
                               onChange={r => set('ruoli', r)} />
                  {strutture.length > 0 && (
                    <SceltaStrutture strutture={strutture} scelte={edit.organizzazioni || []} onChange={v => set('organizzazioni', v)} />
                  )}
                </>)}
                <div className="space-y-2 rounded-campo bg-carta px-3.5 py-3">
                  <label className="flex items-center gap-2 text-[15px]"><input type="checkbox" className="size-4 accent-verde-600" checked={edit.enabled} onChange={e => set('enabled', e.target.checked)} /> Può accedere</label>
                </div>
                <Errore>{errore}</Errore>
                <div className="flex items-center gap-2 border-t border-linea pt-4">
                  {edit.id && edit.username !== me.username && <Button type="button" variante="pericolo" onClick={elimina}>Elimina</Button>}
                  <Button type="button" variante="fantasma" className="ml-auto" onClick={() => setEdit(null)}>Annulla</Button>
                  <Button type="submit" variante="primario" attesa={attesa}>Salva</Button>
                </div>
              </form>
            </Pannello>
          ) : (
            <Pannello><Vuoto icona={Users} titolo="Seleziona un utente">Oppure creane uno nuovo e assegnalo ai gruppi che usi nei processi.</Vuoto></Pannello>
          )}
        </div>
      )}
    </div>
  )
}

/** Ruoli assegnati direttamente; quelli che arrivano dai gruppi si vedono ma si cambiano nella pagina Ruoli. */
function SceltaRuoli({ ruoli, scelti, daGruppi, onChange }) {
  const cambia = (codice, si) => onChange(si ? [...scelti, codice] : scelti.filter(c => c !== codice))
  return (
    <fieldset>
      <legend className="etichetta">Ruoli</legend>
      {ruoli.length === 0 ? <p className="text-sm text-grafite">Nessun ruolo disponibile</p> : (
        <div className="space-y-1.5 rounded-campo bg-carta px-3.5 py-3">
          {ruoli.map(r => {
            const dalGruppo = daGruppi.includes(r.codice) && !scelti.includes(r.codice)
            return (
              <label key={r.codice} className="flex items-start gap-2 text-[15px]">
                <input type="checkbox" className="mt-1 size-4 accent-verde-600" checked={scelti.includes(r.codice) || dalGruppo}
                       disabled={dalGruppo} onChange={e => cambia(r.codice, e.target.checked)} />
                <span>
                  <span className="font-medium text-pino-900">{r.nome}</span>
                  {dalGruppo && <span className="text-grafite"> (da un gruppo)</span>}
                  {r.descrizione && <span className="block text-[13px] text-grafite">{r.descrizione}</span>}
                </span>
              </label>
            )
          })}
        </div>
      )}
    </fieldset>
  )
}

/** Strutture della persona, in ordine d'albero: le pratiche che avvia prendono la sua struttura (se è una sola). */
function SceltaStrutture({ strutture, scelte, onChange }) {
  const cambia = (id, si) => onChange(si ? [...scelte, id] : scelte.filter(x => x !== id))
  return (
    <fieldset>
      <legend className="etichetta">Strutture</legend>
      <div className="max-h-56 space-y-1 overflow-y-auto rounded-campo bg-carta px-3.5 py-3">
        {alberoStrutture(strutture).map(s => (
          <label key={s.id} className="flex items-center gap-2 text-[15px]" style={{ paddingLeft: `${s.livello * 18}px` }}>
            <input type="checkbox" className="size-4 accent-verde-600" checked={scelte.includes(s.id)}
                   onChange={e => cambia(s.id, e.target.checked)} />
            <span className={s.attiva ? '' : 'text-grafite'}>{s.nome}{s.attiva ? '' : ' (disattivata)'}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}

/** Organizzazioni esterne dell'utente esterno: vede le pratiche in cui sono parte e ne lavora le attività. */
function SceltaOrganizzazioni({ organizzazioni, scelte, onChange }) {
  const g = useGlossario()
  const cambia = (id, si) => onChange(si ? [...scelte, id] : scelte.filter(x => x !== id))
  return (
    <fieldset>
      <legend className="etichetta">Organizzazioni</legend>
      {organizzazioni.length === 0 ? (
        <p className="text-sm text-grafite">Nessuna organizzazione esterna: creale nella pagina Organizzazioni. Un {g('esterno')} può anche non averne.</p>
      ) : (
        <div className="max-h-56 space-y-1 overflow-y-auto rounded-campo bg-carta px-3.5 py-3">
          {organizzazioni.map(o => (
            <label key={o.id} className="flex items-center gap-2 text-[15px]">
              <input type="checkbox" className="size-4 accent-verde-600" checked={scelte.includes(o.id)}
                     onChange={e => cambia(o.id, e.target.checked)} />
              <span className={o.attiva ? '' : 'text-grafite'}>{o.nome}{o.attiva ? '' : ' (disattivata)'}</span>
            </label>
          ))}
        </div>
      )}
      <p className="mt-1.5 text-[13px] text-grafite">Un utente esterno non ha ruoli: entra nel gruppo automatico "esterni".</p>
    </fieldset>
  )
}
