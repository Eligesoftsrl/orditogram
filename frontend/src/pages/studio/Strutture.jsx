import { useEffect, useId, useMemo, useState } from 'react'
import { Building2, Plus, X } from 'lucide-react'
import { api } from '../../api.js'
import { cx } from '../../lib/format.js'
import { alberoStrutture, codiceDa } from '../../lib/permessi.js'
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../../components/ui.jsx'
import { useGlossario } from '../../lib/useGlossario.jsx'
import { gl } from '../../lib/glossario.js'

const VUOTA = { id: null, codice: '', nome: '', codiceFiscale: '', padreId: null, attiva: true }

/**
 * Studio: strutture dell'ente in un albero (dipartimento, uffici, filiali). Le persone si mettono nelle strutture
 * dalla pagina Utenti; le pratiche prendono la struttura di chi le avvia. Nei permessi di un processo,
 * "della propria struttura" e "anche di quelle sotto" seguono questo albero.
 */
export default function Strutture() {
  const g = useGlossario()
  const uid = useId()
  const toast = useToast()
  const [lista, setLista] = useState(null)
  const [edit, setEdit] = useState(null)
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const carica = () => api.strutture().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])

  const albero = useMemo(() => alberoStrutture(lista || []), [lista])
  const set = (k, v) => setEdit(p => ({ ...p, [k]: v }))

  // non si sceglie come struttura sopra sé stessa o una delle sue
  const sotto = useMemo(() => {
    if (!edit?.id) return new Set()
    const out = new Set([edit.id])
    let cresciuto = true
    while (cresciuto) {
      cresciuto = false
      for (const s of lista || []) if (s.padreId != null && out.has(s.padreId) && !out.has(s.id)) { out.add(s.id); cresciuto = true }
    }
    return out
  }, [edit?.id, lista])

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      const dati = { ...edit, codice: edit.codice || codiceDa(edit.nome) }
      if (edit.id) await api.modificaStruttura(edit.id, dati); else await api.creaStruttura(dati)
      toast.ok(`Struttura ${edit.nome} salvata`)
      setEdit(null); void carica()
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  const elimina = async () => {
    if (!confirm(`Eliminare la struttura ${edit.nome}? Le persone non ne faranno più parte.`)) return
    try { await api.eliminaStruttura(edit.id); toast.ok(`Struttura ${edit.nome} eliminata`); setEdit(null); void carica() }
    catch (err) { setErrore(err.message) }
  }

  return (
    <div className="space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Strutture' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Strutture</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Gli uffici {g('ente', 'del')}, uno dentro l'altro. Servono a dire chi vede {gl('pratica', 'i')} della propria struttura
            (o anche di quelle sotto) nell'accesso di un processo.
          </p>
        </div>
        <Button variante="primario" icona={Plus} onClick={() => { setErrore(''); setEdit({ ...VUOTA }) }}>Nuova struttura</Button>
      </IntestazionePagina>

      {!lista ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <Pannello corpoClassName="p-0" titolo="Albero" azioni={<span className="numeri text-sm text-grafite">{lista.length} strutture</span>}>
            {albero.length === 0 ? <Vuoto icona={Building2} titolo="Nessuna struttura">Crea la prima, ad esempio la direzione o il dipartimento.</Vuoto> : (
              <ul className="divide-y divide-linea">
                {albero.map(s => (
                  <li key={s.id}>
                    <button onClick={() => { setErrore(''); setEdit({ ...s, codiceFiscale: s.codiceFiscale || '' }) }}
                            style={{ paddingLeft: `${20 + s.livello * 24}px` }}
                            className={cx('flex w-full items-center gap-3 py-3 pr-5 text-left transition-colors',
                              edit?.id === s.id ? 'bg-verde-50/70' : 'hover:bg-carta', !s.attiva && 'opacity-55')}>
                      <Building2 className="size-[18px] shrink-0 text-pino-600" aria-hidden="true" />
                      <span className="min-w-0 flex-1 truncate font-medium text-pino-900">{s.nome}</span>
                      {!s.attiva && <Badge tono="mattone">disattivata</Badge>}
                      <span className="numeri text-[13px] text-grafite">{s.utenti} {s.utenti === 1 ? 'persona' : 'persone'}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Pannello>

          {edit ? (
            <Pannello titolo={edit.id ? 'Modifica struttura' : 'Nuova struttura'}
                      azioni={<button onClick={() => setEdit(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi"><X className="size-5" /></button>}
                      className="lg:sticky lg:top-20">
              <form onSubmit={salva} className="space-y-4">
                <div>
                  <label className="etichetta" htmlFor={`${uid}-n`}>Nome *</label>
                  <input id={`${uid}-n`} className="campo" required value={edit.nome} onChange={e => set('nome', e.target.value)}
                         placeholder="Es. Amministrazione" />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-c`}>Codice</label>
                    <input id={`${uid}-c`} className="campo font-mono text-sm" readOnly={!!edit.id}
                           value={edit.id ? edit.codice : (edit.codice || codiceDa(edit.nome))}
                           onChange={e => set('codice', e.target.value.toLowerCase())} />
                  </div>
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-f`}>Codice fiscale</label>
                    <input id={`${uid}-f`} className="campo" value={edit.codiceFiscale} onChange={e => set('codiceFiscale', e.target.value)} />
                  </div>
                </div>
                <div>
                  <label className="etichetta" htmlFor={`${uid}-p`}>Sta sotto</label>
                  <select id={`${uid}-p`} className="campo" value={edit.padreId ?? ''}
                          onChange={e => set('padreId', e.target.value === '' ? null : Number(e.target.value))}>
                    <option value="">Nessuna: è una struttura principale</option>
                    {albero.filter(s => !sotto.has(s.id)).map(s => (
                      <option key={s.id} value={s.id}>{'  '.repeat(s.livello)}{s.nome}</option>
                    ))}
                  </select>
                </div>
                <label className="flex items-center gap-2 text-[15px]">
                  <input type="checkbox" className="size-4 accent-verde-600" checked={edit.attiva} onChange={e => set('attiva', e.target.checked)} /> Attiva
                </label>
                <Errore>{errore}</Errore>
                <div className="flex items-center gap-2 border-t border-linea pt-4">
                  {edit.id && <Button type="button" variante="pericolo" onClick={elimina}>Elimina</Button>}
                  <Button type="button" variante="fantasma" className="ml-auto" onClick={() => setEdit(null)}>Annulla</Button>
                  <Button type="submit" variante="primario" attesa={attesa}>Salva</Button>
                </div>
              </form>
            </Pannello>
          ) : (
            <Pannello><Vuoto icona={Building2} titolo="Seleziona una struttura">Oppure creane una nuova e scegli sotto quale sta.</Vuoto></Pannello>
          )}
        </div>
      )}
    </div>
  )
}
