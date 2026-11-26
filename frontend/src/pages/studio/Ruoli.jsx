import { useEffect, useId, useMemo, useState } from 'react'
import { Lock, Plus, ShieldCheck, X } from 'lucide-react'
import { api } from '../../api.js'
import { cx } from '../../lib/format.js'
import { codiceDa } from '../../lib/permessi.js'
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../../components/ui.jsx'
import Gruppi from '../../components/Gruppi.jsx'
import { useGlossario } from '../../lib/useGlossario.jsx'

const VUOTO = { id: null, codice: '', nome: '', descrizione: '', permessi: [], gruppi: [], predefinito: false, bloccato: false }

/**
 * Studio: ruoli. Un ruolo è un nome per un insieme di permessi; si assegna alle persone (pagina Utenti)
 * o a un gruppo, anche del provider di accesso dell'ente: chi è nel gruppo riceve il ruolo.
 */
export default function Ruoli() {
  const g = useGlossario()
  const uid = useId()
  const toast = useToast()
  const [lista, setLista] = useState(null)
  const [catalogo, setCatalogo] = useState([])
  const [gruppiNoti, setGruppiNoti] = useState([])
  const [edit, setEdit] = useState(null)
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const carica = () => api.ruoli().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => {
    void carica()
    api.catalogoPermessi().then(setCatalogo).catch(() => {})
    api.gruppi().then(g => setGruppiNoti((g || []).map(x => x.nome))).catch(() => {})
  }, [])

  const nomePermesso = useMemo(() => Object.fromEntries(catalogo.map(p => [p.codice, p.nome])), [catalogo])
  const set = (k, v) => setEdit(p => ({ ...p, [k]: v }))

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      if (edit.id) await api.modificaRuolo(edit.id, edit)
      else await api.creaRuolo({ ...edit, codice: edit.codice || codiceDa(edit.nome) })
      toast.ok(`Ruolo ${edit.nome} salvato`)
      setEdit(null); void carica()
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  const elimina = async () => {
    if (!confirm(`Eliminare il ruolo ${edit.nome}? Chi lo ha lo perde subito.`)) return
    try { await api.eliminaRuolo(edit.id); toast.ok(`Ruolo ${edit.nome} eliminato`); setEdit(null); void carica() }
    catch (err) { setErrore(err.message) }
  }

  const permesso = (codice, si) => set('permessi', si ? [...edit.permessi, codice] : edit.permessi.filter(c => c !== codice))

  return (
    <div className="in-sequenza space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Ruoli' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Ruoli</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Che cosa può fare chi lavora nello Studio e nell'amministrazione. Assegna un ruolo alle persone dalla pagina
            Utenti, oppure a un gruppo: chi è nel gruppo lo riceve, anche se arriva dal sistema di accesso {g('ente', 'del')}.
          </p>
        </div>
        <Button variante="primario" icona={Plus} onClick={() => { setErrore(''); setEdit({ ...VUOTO }) }}>Nuovo ruolo</Button>
      </IntestazionePagina>

      {!lista ? <Caricamento /> : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <Pannello corpoClassName="p-0" titolo="Ruoli" azioni={<span className="numeri text-sm text-grafite">{lista.length} ruoli</span>}>
            <ul className="divide-y divide-linea">
              {lista.map(r => (
                <li key={r.id}>
                  <button onClick={() => { setErrore(''); setEdit({ ...r, descrizione: r.descrizione || '' }) }}
                          className={cx('flex w-full items-start gap-3 px-5 py-3 text-left transition-colors',
                            edit?.id === r.id ? 'bg-verde-50/70' : 'hover:bg-carta')}>
                    <ShieldCheck className="mt-0.5 size-5 shrink-0 text-pino-600" aria-hidden="true" />
                    <span className="min-w-0 flex-1">
                      <span className="block font-medium text-pino-900">{r.nome}</span>
                      <span className="block text-[13px] text-grafite">
                        {r.bloccato ? 'Tutti i permessi' : (r.permessi.map(p => nomePermesso[p] || p).join(', ') || 'Nessun permesso')}
                      </span>
                    </span>
                    <span className="hidden flex-wrap justify-end gap-1 sm:flex">
                      {r.gruppi.map(g => <Badge key={g}>{g}</Badge>)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </Pannello>

          {edit ? (
            <Pannello titolo={edit.id ? 'Modifica ruolo' : 'Nuovo ruolo'}
                      azioni={<button onClick={() => setEdit(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi"><X className="size-5" /></button>}
                      className="lg:sticky lg:top-20">
              <form onSubmit={salva} className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-n`}>Nome *</label>
                    <input id={`${uid}-n`} className="campo" required readOnly={edit.bloccato} value={edit.nome}
                           onChange={e => set('nome', e.target.value)} />
                  </div>
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-c`}>Codice</label>
                    <input id={`${uid}-c`} className="campo font-mono text-sm" readOnly={!!edit.id}
                           value={edit.id ? edit.codice : (edit.codice || codiceDa(edit.nome))}
                           onChange={e => set('codice', e.target.value.toLowerCase())}
                           aria-describedby={`${uid}-ch`} />
                    <p id={`${uid}-ch`} className="mt-1 text-[13px] text-grafite">Non cambia più dopo il salvataggio.</p>
                  </div>
                  <div className="sm:col-span-2">
                    <label className="etichetta" htmlFor={`${uid}-d`}>Descrizione</label>
                    <input id={`${uid}-d`} className="campo" readOnly={edit.bloccato} value={edit.descrizione}
                           onChange={e => set('descrizione', e.target.value)} placeholder="Chi lo riceve e per fare cosa" />
                  </div>
                </div>

                <fieldset>
                  <legend className="etichetta">Permessi</legend>
                  {edit.bloccato ? (
                    <p className="flex items-center gap-2 rounded-campo bg-carta px-3.5 py-3 text-[15px] text-grafite">
                      <Lock className="size-4" aria-hidden="true" /> L'amministratore ha sempre tutti i permessi, anche quelli futuri.
                    </p>
                  ) : (
                    <div className="space-y-1.5 rounded-campo bg-carta px-3.5 py-3">
                      {catalogo.map(p => (
                        <label key={p.codice} className="flex items-start gap-2 text-[15px]">
                          <input type="checkbox" className="mt-1 size-4 accent-verde-600" checked={edit.permessi.includes(p.codice)}
                                 onChange={e => permesso(p.codice, e.target.checked)} />
                          <span>
                            <span className="font-medium text-pino-900">{p.nome}</span>
                            <span className="block text-[13px] text-grafite">{p.descrizione}</span>
                          </span>
                        </label>
                      ))}
                    </div>
                  )}
                </fieldset>

                <Gruppi titolo="Gruppi che ricevono il ruolo" vuoto="Nessun gruppo: si assegna solo alle persone"
                        valori={edit.gruppi} noti={gruppiNoti} onChange={g => set('gruppi', g)} />

                <Errore>{errore}</Errore>
                <div className="flex items-center gap-2 border-t border-linea pt-4">
                  {edit.id && !edit.predefinito && <Button type="button" variante="pericolo" onClick={elimina}>Elimina</Button>}
                  <Button type="button" variante="fantasma" className="ml-auto" onClick={() => setEdit(null)}>Annulla</Button>
                  <Button type="submit" variante="primario" attesa={attesa}>Salva</Button>
                </div>
              </form>
            </Pannello>
          ) : (
            <Pannello><Vuoto icona={ShieldCheck} titolo="Seleziona un ruolo">Oppure creane uno nuovo, ad esempio per chi controlla senza modificare.</Vuoto></Pannello>
          )}
        </div>
      )}
    </div>
  )
}
