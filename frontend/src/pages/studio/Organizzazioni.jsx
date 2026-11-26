import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { Briefcase, Plus, Search, Upload, X } from 'lucide-react'
import { api } from '../../api.js'
import { cx } from '../../lib/format.js'
import { codiceDa } from '../../lib/permessi.js'
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast } from '../../components/ui.jsx'
import { useGlossario } from '../../lib/useGlossario.jsx'
import { gl } from '../../lib/glossario.js'

const VUOTA = { id: null, codice: '', nome: '', codiceFiscale: '', attiva: true }

/**
 * Studio: anagrafica delle organizzazioni esterne (enti, aziende, studi, istituti). Possono essere parti delle
 * pratiche, scelte nei campi Organizzazione dei form; i loro utenti esterni vedono le pratiche in cui sono parte
 * e lavorano le attività assegnate a quella parte. Si caricano anche da un file CSV.
 */
export default function Organizzazioni() {
  const uid = useId()
  const toast = useToast()
  const fileRef = useRef(null)
  const [lista, setLista] = useState(null)
  const [edit, setEdit] = useState(null)
  const [cerca, setCerca] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)
  const [esitoImport, setEsitoImport] = useState(null)

  const carica = () => api.organizzazioniAdmin().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])
  const set = (k, v) => setEdit(p => ({ ...p, [k]: v }))

  const filtrate = useMemo(() => {
    const q = cerca.trim().toLowerCase()
    return (lista || []).filter(o => !q || o.nome.toLowerCase().includes(q) || o.codice.includes(q)
      || (o.codiceFiscale || '').toLowerCase().includes(q))
  }, [lista, cerca])

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      const dati = { ...edit, codice: edit.codice || codiceDa(edit.nome) }
      if (edit.id) await api.modificaOrganizzazione(edit.id, dati); else await api.creaOrganizzazione(dati)
      toast.ok(`Organizzazione ${edit.nome} salvata`)
      setEdit(null); void carica()
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  const elimina = async () => {
    if (!confirm(`Eliminare l'organizzazione ${edit.nome}?`)) return
    try { await api.eliminaOrganizzazione(edit.id); toast.ok(`Organizzazione ${edit.nome} eliminata`); setEdit(null); void carica() }
    catch (err) { setErrore(err.message) }
  }

  const importa = async (file) => {
    if (!file) return
    setErrore(''); setEsitoImport(null)
    try {
      const esito = await api.importaOrganizzazioni(file)
      setEsitoImport(esito)
      toast.ok(`${esito.create} create, ${esito.aggiornate} aggiornate`)
      void carica()
    } catch (err) { setErrore(err.message) } finally { if (fileRef.current) fileRef.current.value = '' }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Organizzazioni</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Enti, aziende e studi esterni che possono essere parti {gl('pratica', 'dei')}. I loro utenti entrano come utenti
            esterni: vedono solo {gl('pratica', 'i')} in cui la loro organizzazione è parte.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input ref={fileRef} type="file" accept=".csv,text/csv" className="hidden" onChange={e => importa(e.target.files[0])} />
          <Button icona={Upload} onClick={() => fileRef.current?.click()}>Importa CSV</Button>
          <Button variante="primario" icona={Plus} onClick={() => { setErrore(''); setEdit({ ...VUOTA }) }}>Nuova organizzazione</Button>
        </div>
      </header>

      {esitoImport && (
        <Pannello titolo="Importazione" azioni={<button onClick={() => setEsitoImport(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi"><X className="size-5" /></button>}>
          <p>{esitoImport.create} nuove, {esitoImport.aggiornate} aggiornate{esitoImport.scartate.length ? `, ${esitoImport.scartate.length} righe scartate:` : '.'}</p>
          {esitoImport.scartate.length > 0 && (
            <ul className="mt-2 list-disc pl-5 text-sm text-mattone-700">{esitoImport.scartate.map(r => <li key={r}>{r}</li>)}</ul>
          )}
        </Pannello>
      )}

      {!lista ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
          <Pannello corpoClassName="p-0" titolo="Anagrafica" azioni={<span className="numeri text-sm text-grafite">{lista.length} organizzazioni</span>}>
            <div className="border-b border-linea p-3">
              <label className="relative block">
                <span className="sr-only">Cerca</span>
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-grafite" aria-hidden="true" />
                <input className="campo pl-9" placeholder="Nome, codice o codice fiscale" value={cerca} onChange={e => setCerca(e.target.value)} />
              </label>
            </div>
            {filtrate.length === 0 ? (
              <Vuoto icona={Briefcase} titolo={lista.length ? 'Nessun risultato' : 'Nessuna organizzazione'}>
                {lista.length ? 'Prova con un\'altra ricerca.' : 'Creane una o importa un file CSV con le colonne codice;nome;codiceFiscale.'}
              </Vuoto>
            ) : (
              <ul className="divide-y divide-linea">
                {filtrate.map(o => (
                  <li key={o.id}>
                    <button onClick={() => { setErrore(''); setEdit({ ...o, codiceFiscale: o.codiceFiscale || '' }) }}
                            className={cx('flex w-full items-center gap-3 px-5 py-3 text-left transition-colors',
                              edit?.id === o.id ? 'bg-verde-50/70' : 'hover:bg-carta', !o.attiva && 'opacity-55')}>
                      <Briefcase className="size-[18px] shrink-0 text-pino-600" aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium text-pino-900">{o.nome}</span>
                        {o.codiceFiscale && <span className="block text-[13px] text-grafite">{o.codiceFiscale}</span>}
                      </span>
                      {!o.attiva && <Badge tono="mattone">disattivata</Badge>}
                      <span className="numeri text-right text-[13px] text-grafite">
                        {o.utenti} {o.utenti === 1 ? 'utente' : 'utenti'}
                        {o.referenti > 0 && <span className="block">{o.referenti} {o.referenti === 1 ? 'referente' : 'referenti'}</span>}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Pannello>

          {edit ? (
            <Pannello titolo={edit.id ? 'Modifica organizzazione' : 'Nuova organizzazione'}
                      azioni={<button onClick={() => setEdit(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi"><X className="size-5" /></button>}
                      className="lg:sticky lg:top-20">
              <form onSubmit={salva} className="space-y-4">
                <div>
                  <label className="etichetta" htmlFor={`${uid}-n`}>Nome *</label>
                  <input id={`${uid}-n`} className="campo" required value={edit.nome} onChange={e => set('nome', e.target.value)}
                         placeholder="Es. Studio legale Bianchi" />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-c`}>Codice</label>
                    <input id={`${uid}-c`} className="campo font-mono text-sm" readOnly={!!edit.id}
                           value={edit.id ? edit.codice : (edit.codice || codiceDa(edit.nome))}
                           onChange={e => set('codice', e.target.value.toLowerCase())} />
                  </div>
                  <div>
                    <label className="etichetta" htmlFor={`${uid}-f`}>Codice fiscale o partita IVA</label>
                    <input id={`${uid}-f`} className="campo" value={edit.codiceFiscale} onChange={e => set('codiceFiscale', e.target.value)} />
                  </div>
                </div>
                <label className="flex items-center gap-2 text-[15px]">
                  <input type="checkbox" className="size-4 accent-verde-600" checked={edit.attiva} onChange={e => set('attiva', e.target.checked)} />
                  Attiva (si può scegliere nei form)
                </label>
                <p className="text-sm text-grafite">
                  Gli utenti dell'organizzazione si aggiungono dalla pagina Utenti, come utenti esterni, oppure li aggiunge
                  il suo referente dal portale.
                </p>
                <Errore>{errore}</Errore>
                <div className="flex items-center gap-2 border-t border-linea pt-4">
                  {edit.id && <Button type="button" variante="pericolo" onClick={elimina}>Elimina</Button>}
                  <Button type="button" variante="fantasma" className="ml-auto" onClick={() => setEdit(null)}>Annulla</Button>
                  <Button type="submit" variante="primario" attesa={attesa}>Salva</Button>
                </div>
              </form>
              {edit.id && <PersoneReferenti org={edit} onCambio={carica} />}
            </Pannello>
          ) : (
            <Pannello>
              <Errore>{errore}</Errore>
              <Vuoto icona={Briefcase} titolo="Seleziona un'organizzazione">Oppure creane una nuova, o importa un file CSV.</Vuoto>
            </Pannello>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Le persone dell'organizzazione e chi ne è referente. Il referente gestisce da solo i colleghi dal portale
 * (La mia organizzazione); resta un utente esterno, senza ruoli né permessi.
 */
function PersoneReferenti({ org, onCambio }) {
  const g = useGlossario()
  const toast = useToast()
  const [persone, setPersone] = useState(null)
  const [scelti, setScelti] = useState(new Set())
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const mostra = (lista) => { setPersone(lista); setScelti(new Set(lista.filter(p => p.referente).map(p => p.id))) }
  useEffect(() => { api.personeOrganizzazione(org.id).then(mostra).catch(e => setErrore(e.message)) }, [org.id])

  const cambiato = persone && persone.some(p => p.referente !== scelti.has(p.id))
  const salva = async () => {
    setAttesa(true); setErrore('')
    try { mostra(await api.impostaReferenti(org.id, [...scelti])); toast.ok('Referenti salvati'); onCambio() }
    catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }
  const scegli = (id, si) => setScelti(s => { const n = new Set(s); if (si) n.add(id); else n.delete(id); return n })

  return (
    <section className="mt-6 border-t border-linea pt-5" aria-labelledby={`ref-${org.id}`}>
      <h3 id={`ref-${org.id}`} className="font-semibold">Persone e referenti</h3>
      <p className="mt-1 text-sm text-grafite">
        Il referente aggiunge, disattiva e toglie le persone della sua organizzazione dal portale, senza passare {g('ente', 'dal')}.
      </p>
      {!persone ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : persone.length === 0 ? (
        <p className="mt-3 text-sm text-grafite">Nessuna persona: aggiungi prima un utente esterno dalla pagina Utenti.</p>
      ) : (<>
        <ul className="mt-3 space-y-1.5">
          {persone.map(p => {
            const nome = [p.nome, p.cognome].filter(Boolean).join(' ') || p.username
            return (
              <li key={p.id}>
                <label className={cx('flex items-center gap-2 text-[15px]', !p.attivo && 'opacity-60')}>
                  <input type="checkbox" className="size-4 accent-verde-600" checked={scelti.has(p.id)} onChange={e => scegli(p.id, e.target.checked)} />
                  <span className="min-w-0 flex-1 truncate">{nome} <span className="text-[13px] text-grafite">{p.username}</span></span>
                  {!p.attivo && <Badge tono="mattone">disattivata</Badge>}
                </label>
              </li>
            )
          })}
        </ul>
        <Errore>{errore}</Errore>
        <div className="mt-3 flex justify-end">
          <Button misura="sm" variante="primario" disabled={!cambiato} attesa={attesa} onClick={salva}>Salva referenti</Button>
        </div>
      </>)}
    </section>
  )
}
