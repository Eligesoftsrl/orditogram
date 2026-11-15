import { useEffect, useId, useMemo, useState } from 'react'
import { Plus, Search, UserPlus, Users, X } from 'lucide-react'
import { api } from '../../api.js'
import { useAuth } from '../../lib/auth.jsx'
import { cx } from '../../lib/format.js'
import { Avatar, Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'

const VUOTO = { id: null, username: '', firstName: '', lastName: '', email: '', enabled: true, admin: false, groups: [], password: '' }

/** Studio: utenti e gruppi. I gruppi sono quelli usati nei BPMN (candidateGroups, candidateStarterGroups). */
export default function Utenti() {
  const uid = useId()
  const { me } = useAuth()
  const toast = useToast()
  const [lista, setLista] = useState(null)
  const [edit, setEdit] = useState(null)
  const [cerca, setCerca] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const carica = () => api.users().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])

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
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Utenti</h1>
          <p className="mt-1 max-w-2xl text-grafite">Chi accede e a quali gruppi appartiene. I gruppi decidono chi vede le attività e chi può avviare i processi.</p>
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
                     { titolo: 'Amministratore', valore: u => u.admin ? 'Sì' : 'No' },
                     { titolo: 'Attivo', valore: u => u.enabled ? 'Sì' : 'No' }
                   ]} />
          <Button variante="primario" icona={UserPlus} onClick={() => { setErrore(''); setEdit({ ...VUOTO }) }}>Nuovo utente</Button>
        </div>
      </header>

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
                          {u.admin && <Badge tono="pino">admin</Badge>}
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
                <Gruppi valori={edit.groups} noti={gruppi} onChange={g => set('groups', g)} />
                <div className="space-y-2 rounded-campo bg-carta px-3.5 py-3">
                  <label className="flex items-center gap-2 text-[15px]"><input type="checkbox" className="size-4 accent-verde-600" checked={edit.enabled} onChange={e => set('enabled', e.target.checked)} /> Può accedere</label>
                  <label className="flex items-center gap-2 text-[15px]"><input type="checkbox" className="size-4 accent-verde-600" checked={edit.admin} onChange={e => set('admin', e.target.checked)} /> Amministratore: gestisce processi, form e utenti</label>
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

function Gruppi({ valori, noti, onChange }) {
  const [nuovo, setNuovo] = useState('')
  const aggiungi = (g) => { const v = g.trim(); if (v && !valori.includes(v)) onChange([...valori, v]); setNuovo('') }
  const suggeriti = noti.filter(g => !valori.includes(g))
  return (
    <div>
      <p className="etichetta">Gruppi</p>
      <div className="flex flex-wrap gap-2">
        {valori.length === 0 && <span className="text-sm text-grafite">Nessun gruppo</span>}
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
