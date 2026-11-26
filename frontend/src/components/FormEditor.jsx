import { useEffect, useId, useMemo, useState } from 'react'
import {
  TextAlignStart, BookUser, Braces, Briefcase, CalendarDays, ChevronDown, ChevronUp, CircleCheck, Copy, Eye, Hash, Heading, ListChecks,
  Plus, Save, SquareCheck, Table2, Trash, Type, X
} from 'lucide-react'
import { api } from '../api.js'
import { cx, quando } from '../lib/format.js'
import DynamicForm from './DynamicForm.jsx'
import { SEZIONE, condizione, descriviCondizione, eSezione, valoriPossibili } from '../lib/formCondizioni.js'
import { FORMATI, descriviRegole } from '../lib/formRegole.js'

const NESSUNA_REGOLA = { min: undefined, max: undefined, minLunghezza: undefined, maxLunghezza: undefined,
  formato: undefined, espressione: undefined, messaggioFormato: undefined, colonne: undefined, minRighe: undefined, maxRighe: undefined,
  parte: undefined, tipoAnagrafica: undefined, chiave: undefined, cercabile: undefined, creaScheda: undefined }
/** Identificativi interni dell'editor (chiavi di React): basta un contatore, non serve casualità. */
let ultimoId = 0
const nuovoId = () => ++ultimoId
const TIPI_COLONNA = ['text', 'number', 'date', 'select', 'checkbox']
const colonnaIniziale = () => ({ _id: nuovoId(), _nomeAuto: true, name: 'descrizione', label: 'Descrizione', type: 'text' })
/** Proprietà in più quando un campo diventa di quel tipo. */
const perTipo = (tipo, f = {}) => ({
  ...(tipo === 'select' && !f.options ? { options: ['Opzione 1', 'Opzione 2'] } : {}),
  ...(tipo === 'tabella' ? { colonne: f.colonne?.length ? f.colonne : [colonnaIniziale()], titolo: undefined } : {})
})
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast } from './ui.jsx'
import { useGlossario } from '../lib/useGlossario.jsx'
import { gl } from '../lib/glossario.js'

const TIPI = [
  { id: 'text', label: 'Testo', icona: Type },
  { id: 'textarea', label: 'Testo lungo', icona: TextAlignStart },
  { id: 'number', label: 'Numero', icona: Hash },
  { id: 'date', label: 'Data', icona: CalendarDays },
  { id: 'select', label: 'Scelta', icona: ListChecks },
  { id: 'checkbox', label: 'Sì / no', icona: SquareCheck },
  { id: 'tabella', label: 'Tabella', icona: Table2 },
  { id: 'organizzazione', label: 'Organizzazione', icona: Briefcase },
  { id: 'anagrafica', label: 'Anagrafica', icona: BookUser }
]
/** I tipi di campo: le anagrafiche non hanno il campo Organizzazione (le organizzazioni esterne restano dei form). */
const tipiPer = (modo) => modo === 'anagrafica' ? TIPI.filter(t => t.id !== 'organizzazione') : TIPI
/** Tipi che in un'anagrafica possono essere chiave, ricercabili o titolo (come DefinizioneAnagrafica nel backend). */
const INDICIZZABILI = ['text', 'number', 'date', 'select']

const DOCUMENTI = [
  { id: '', label: 'Nessuno', aiuto: 'Il form non mostra documenti.' },
  { id: 'carica', label: 'Allegati all\'avvio', aiuto: `Solo per il form di avvio: chi apre ${gl('pratica', 'il')} allega i documenti.` },
  { id: 'lettura', label: 'In consultazione', aiuto: `Si vedono e si scaricano i documenti ${gl('pratica', 'del')}.` },
  { id: 'modifica', label: 'Modificabili', aiuto: 'Chi ha in carico l\'attività può aggiungere documenti e nuove versioni.' }
]

const NUOVO = { title: 'Nuovo form', description: '', submitLabel: 'Conferma', fields: [] }

/** "Numero di protocollo" → "numeroDiProtocollo" */
function nomeDaEtichetta(s) {
  const parole = s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9 ]/g, ' ').trim().split(/\s+/).filter(Boolean)
  const n = parole.map((p, i) => i === 0 ? p.toLowerCase() : p[0].toUpperCase() + p.slice(1).toLowerCase()).join('')
  return /^[a-zA-Z]/.test(n) ? n : n ? 'campo' + n : ''
}

/**
 * Costruttore visuale di un form, con anteprima dal vivo. Usato nella pagina Form e dentro l'editor dei processi.
 *  chiave:       formKey da aprire (null + nuovo=true per crearne uno)
 *  suggerimento: formKey proposto per un form nuovo
 *  onSalvato:    chiamato con il formKey dopo il salvataggio
 *  onEliminato:  chiamato dopo aver eliminato la versione su database
 *  anagrafica:   { definizione, onSalva(definizione), disabilitato } per i campi di un tipo di anagrafica
 *                (Studio → Anagrafiche): niente impostazioni del form, campi chiave e ricercabili, salva il chiamante
 */
export default function FormEditor({ chiave: chiaveIniziale, nuovo: nuovoIniziale = false, suggerimento = '', onSalvato, onEliminato, anagrafica }) {
  const uid = useId()
  const modo = anagrafica ? 'anagrafica' : 'form'
  const base = anagrafica ? {} : NUOVO
  const [catalogo, setCatalogo] = useState({})
  const [tipiAnagrafica, setTipiAnagrafica] = useState([])
  const [sorgente, setSorgente] = useState(null)
  const [chiave, setChiave] = useState(nuovoIniziale ? suggerimento : chiaveIniziale)
  const [nuovo, setNuovo] = useState(nuovoIniziale)
  const [form, setForm] = useState(() => anagrafica ? { fields: (anagrafica.definizione?.fields || []).map(conId) }
    : nuovoIniziale ? { ...NUOVO, fields: [] } : null)
  const [originale, setOriginale] = useState(() => anagrafica ? JSON.stringify(pulisci({ fields: anagrafica.definizione?.fields || [] })) : '')
  const [vistaJson, setVistaJson] = useState(false)
  const [testoJson, setTestoJson] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)
  const toast = useToast()

  // i tipi di anagrafica, per i campi che scelgono una scheda
  useEffect(() => { api.tipiAnagraficaAdmin().then(setTipiAnagrafica).catch(() => setTipiAnagrafica([])) }, [])

  // catalogo dei campi già usati negli altri form (per riusare gli stessi dati)
  useEffect(() => {
    if (anagrafica) return
    api.forms().then(async l => {
      setSorgente(l.find(f => f.formKey === chiaveIniziale)?.source || null)
      const tutti = await Promise.all(l.map(f => api.form(f.formKey).catch(() => null)))
      const c = {}
      tutti.forEach(def => def?.fields?.forEach(f => { if (!c[f.name]) c[f.name] = f }))
      setCatalogo(c)
    }).catch(() => {})
  }, [chiaveIniziale, anagrafica])

  const apri = async (k) => {
    setErrore(''); setVistaJson(false)
    try {
      const f = await api.form(k)
      const def = { ...NUOVO, ...f, fields: (f.fields || []).map(conId) }
      setChiave(k); setNuovo(false); setForm(def); setOriginale(JSON.stringify(pulisci(def)))
    } catch (e) { setErrore(e.message) }
  }
  useEffect(() => { if (!nuovoIniziale && chiaveIniziale) void apri(chiaveIniziale) }, [chiaveIniziale, nuovoIniziale])

  const json = useMemo(() => form ? JSON.stringify(pulisci(form), null, 2) : '', [form])
  const modificato = form && json && JSON.stringify(JSON.parse(json)) !== originale

  const aggiorna = (patch) => setForm(f => ({ ...f, ...patch }))
  const aggiornaCampo = (i, patch) => setForm(f => ({ ...f, fields: f.fields.map((x, j) => j === i ? { ...x, ...patch } : x) }))
  const sposta = (i, d) => setForm(f => {
    const a = [...f.fields]; const j = i + d
    if (j < 0 || j >= a.length) return f
    ;[a[i], a[j]] = [a[j], a[i]]
    return { ...f, fields: a }
  })
  const togli = (i) => setForm(f => ({ ...f, fields: f.fields.filter((_, j) => j !== i) }))
  const duplica = (i) => setForm(f => {
    const c = { ...f.fields[i], _id: nuovoId(), name: f.fields[i].name + 'Copia', label: f.fields[i].label + ' (copia)' }
    const a = [...f.fields]; a.splice(i + 1, 0, c)
    return { ...f, fields: a }
  })
  const aggiungi = (tipo) => setForm(f => ({
    ...f,
    fields: [...f.fields, tipo === SEZIONE
      ? { _id: nuovoId(), _aperto: true, type: SEZIONE, label: '' }
      : { _id: nuovoId(), _aperto: true, _nomeAuto: true, type: tipo, label: '', name: '', ...perTipo(tipo) }]
  }))

  const salva = async () => {
    setErrore(''); setAttesa(true)
    try {
      const testo = vistaJson ? testoJson : json
      const def = JSON.parse(testo)
      if (anagrafica) {
        await anagrafica.onSalva(def)
        setForm({ fields: (def.fields || []).map(conId) }); setOriginale(JSON.stringify(pulisci({ fields: def.fields || [] })))
        setVistaJson(false)
        return
      }
      await api.saveForm(chiave, testo)
      toast.ok(`Form "${chiave}" salvato: è già attivo nei processi che lo usano`)
      setSorgente('database')
      await apri(chiave)
      onSalvato?.(chiave)
    } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }

  const passaAJson = () => {
    if (!vistaJson) { setTestoJson(json); setVistaJson(true); return }
    try {
      const f = JSON.parse(testoJson)
      setForm({ ...base, ...f, fields: (f.fields || []).map(conId) })
      setVistaJson(false); setErrore('')
    } catch (e) { setErrore('JSON non valido: ' + e.message) }
  }

  const ripristina = async () => {
    if (!confirm(`Eliminare la versione salvata su database di "${chiave}"?`)) return
    try {
      await api.deleteForm(chiave)
      toast.ok('Versione su database eliminata')
      onEliminato?.(chiave)
    } catch (e) { setErrore(e.message) }
  }

  const anteprima = useMemo(() => {
    if (!form) return null
    const esempi = {}
    form.fields.filter(f => f.readonly && !eSezione(f)).forEach(f => {
      if (f.type === 'anagrafica') { esempi[f.name] = '1'; esempi[`${f.name}_titolo`] = `Esempio di ${(f.label || 'scheda').toLowerCase()}`; return }
      esempi[f.name] = f.type === 'tabella' ? [esempioRiga(f)]
        : f.type === 'checkbox' ? true : f.type === 'date' ? '2026-10-15' : (f.label ? `Esempio di ${f.label.toLowerCase()}` : 'Esempio')
    })
    return { def: pulisci(form), esempi }
  }, [form])

  if (!form) return errore ? <Errore>{errore}</Errore> : <Caricamento />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-pannello border border-linea bg-white px-4 py-3 shadow-pannello">
        {anagrafica ? (
          <div className="flex flex-1 items-center gap-2">
            <span className="font-display text-lg font-semibold">Campi della scheda</span>
            {modificato && <Badge tono="zafferano">modifiche non salvate</Badge>}
          </div>
        ) : nuovo ? (
          <label className="flex flex-1 items-center gap-3">
            <span className="text-sm font-medium text-pino-800">Nome del form</span>
            <input className="campo h-9 max-w-xs font-mono text-sm" placeholder="es. richiesta-ferie" value={chiave || ''}
                   onChange={e => setChiave(e.target.value.replace(/[^a-zA-Z0-9_-]/g, ''))} />
          </label>
        ) : (
          <div className="flex flex-1 items-center gap-2">
            <span className="font-display text-lg font-semibold">{chiave}</span>
            {modificato ? <Badge tono="zafferano">modifiche non salvate</Badge> : <Badge tono="verde" icona={CircleCheck}>salvato</Badge>}
          </div>
        )}
        <Button misura="sm" variante="fantasma" icona={Braces} onClick={passaAJson}>{vistaJson ? 'Torna al costruttore' : 'JSON'}</Button>
        {!nuovo && sorgente === 'database' && onEliminato && <Button misura="sm" variante="pericolo" onClick={ripristina}>Elimina da database</Button>}
        <Button misura="sm" variante="primario" icona={Save} disabled={anagrafica ? !!anagrafica.disabilitato : !chiave} attesa={attesa} onClick={salva}>Salva</Button>
      </div>
      <Errore>{errore}</Errore>

              <div className="grid items-start gap-4 xl:grid-cols-2">
                {vistaJson ? (
                  <Pannello titolo="JSON del form">
                    <textarea spellCheck={false} value={testoJson} onChange={e => setTestoJson(e.target.value)}
                              className="campo min-h-[520px] font-mono text-[13px] leading-relaxed" />
                  </Pannello>
                ) : (
                  <div className="space-y-4">
                    {!anagrafica && <Pannello titolo="Impostazioni">
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div className="sm:col-span-2">
                          <label className="etichetta" htmlFor={`${uid}-1`}>Titolo</label>
                          <input id={`${uid}-1`} className="campo" value={form.title || ''} onChange={e => aggiorna({ title: e.target.value })} />
                        </div>
                        <div className="sm:col-span-2">
                          <label className="etichetta" htmlFor={`${uid}-2`}>Istruzioni per chi compila</label>
                          <textarea id={`${uid}-2`} rows={2} className="campo" value={form.description || ''} onChange={e => aggiorna({ description: e.target.value })}
                                    placeholder="Es. Controlla dati e allegati prima di confermare." />
                        </div>
                        <div>
                          <label className="etichetta" htmlFor={`${uid}-3`}>Testo del pulsante</label>
                          <input id={`${uid}-3`} className="campo" value={form.submitLabel || ''} onChange={e => aggiorna({ submitLabel: e.target.value })} placeholder="Conferma" />
                        </div>
                        <div>
                          <label className="etichetta" htmlFor={`${uid}-4`}>Documenti {gl('pratica', 'del')}</label>
                          <select id={`${uid}-4`} className="campo" value={form.documenti || ''} onChange={e => aggiorna({ documenti: e.target.value || undefined })}>
                            {DOCUMENTI.map(d => <option key={d.id} value={d.id}>{d.label}</option>)}
                          </select>
                          <p className="mt-1 text-[13px] text-grafite">{DOCUMENTI.find(d => d.id === (form.documenti || ''))?.aiuto}</p>
                        </div>
                        {form.documenti === 'carica' && (
                          <label className="flex items-center gap-2 text-[15px] sm:col-span-2">
                            <input type="checkbox" className="size-4 accent-verde-600" checked={!!form.documentiObbligatori}
                                   onChange={e => aggiorna({ documentiObbligatori: e.target.checked || undefined })} />
                            Almeno un documento obbligatorio
                          </label>
                        )}
                        {(form.documenti === 'carica' || form.documenti === 'modifica') && (
                          <label className="flex items-center gap-2 text-[15px] sm:col-span-2">
                            <input type="checkbox" className="size-4 accent-verde-600" checked={form.documentiVisibilita === 'parti'}
                                   onChange={e => aggiorna({ documentiVisibilita: e.target.checked ? 'parti' : undefined })} />
                            I documenti caricati qui li vedono anche le parti esterne {gl('pratica', 'del')}
                          </label>
                        )}
                      </div>
                    </Pannello>}

                    <Pannello titolo={`Campi (${form.fields.filter(f => !eSezione(f)).length})`} corpoClassName="space-y-2 p-3">
                      {form.fields.length === 0 && <p className="px-2 py-3 text-sm text-grafite">Nessun campo. Aggiungine uno qui sotto.</p>}
                      {form.fields.map((f, i) => (
                        <CampoEditor key={f._id} f={f} i={i} n={form.fields.length} catalogo={catalogo} campi={form.fields}
                                     modo={modo} tipiAnagrafica={tipiAnagrafica}
                                     onChange={p => aggiornaCampo(i, p)} onSu={() => sposta(i, -1)} onGiu={() => sposta(i, 1)}
                                     onTogli={() => togli(i)} onDuplica={() => duplica(i)} />
                      ))}
                      <div className="rounded-campo border-2 border-dashed border-linea p-3">
                        <p className="mb-2 text-sm font-medium text-pino-800">Aggiungi un campo</p>
                        <div className="flex flex-wrap gap-2">
                          {tipiPer(modo).map(t => <Button key={t.id} misura="sm" icona={t.icona} onClick={() => aggiungi(t.id)}>{t.label}</Button>)}
                          <Button misura="sm" variante="fantasma" icona={Heading} onClick={() => aggiungi(SEZIONE)}>Sezione</Button>
                        </div>
                      </div>
                    </Pannello>
                  </div>
                )}

                <div className="xl:sticky xl:top-20">
                  <Pannello titolo="Anteprima" azioni={<span className="text-[13px] text-grafite">come la vedrà l'utente</span>} className="bg-white">
                    {anteprima && (anteprima.def.fields.length || anteprima.def.description
                      ? <DynamicForm key={json} form={anteprima.def} initialValues={anteprima.esempi} avvio anteprima onSubmit={() => toast.ok('Anteprima: nessun dato inviato')} />
                      : <p className="text-sm text-grafite">L'anteprima compare quando aggiungi il primo campo.</p>)}
                  </Pannello>
                </div>
              </div>
    </div>
  )
}

/** Identificativi interni per i campi e le colonne delle tabelle. */
function conId(x) {
  return { ...x, _id: nuovoId(), ...(Array.isArray(x.colonne) ? { colonne: x.colonne.map(c => ({ ...c, _id: nuovoId() })) } : {}) }
}

/** Una riga d'esempio per l'anteprima di una tabella in sola lettura. */
function esempioRiga(f) {
  return Object.fromEntries((f.colonne || []).map(c => [c.name,
    c.type === 'checkbox' ? true : c.type === 'number' ? '10' : c.type === 'date' ? '2026-10-15'
      : c.type === 'select' ? (c.options || [])[0] : (c.label || 'Esempio')]))
}

/** Le proprietà valorizzate di un campo o di una colonna, senza quelle interne dell'editor. */
function senzaVuoti(f) {
  const c = {}
  Object.entries(f).forEach(([k, v]) => {
    if (k.startsWith('_') || v === '' || v === undefined || v === false) return
    if (k === 'options' && f.type !== 'select') return
    c[k] = v
  })
  return c
}

/** Il form da salvare: senza le proprietà interne dell'editor e senza valori vuoti. */
function pulisci(form) {
  const out = {}
  ;['title', 'description', 'documenti', 'documentiObbligatori', 'documentiVisibilita', 'submitLabel'].forEach(k => {
    if (form[k] !== undefined && form[k] !== '' && form[k] !== false) out[k] = form[k]
  })
  out.fields = form.fields.map(f => {
    if (eSezione(f)) {
      const sez = { type: SEZIONE, label: f.label || '' }
      if (f.help) sez.help = f.help
      return sez
    }
    const c = senzaVuoti(f)
    if (f.type === 'tabella') c.colonne = (f.colonne || []).map(col => ({ type: 'text', ...senzaVuoti(col) }))
    else { delete c.colonne; delete c.minRighe; delete c.maxRighe }
    if (f.precompila === false) c.precompila = false
    if (!c.type) c.type = 'text'
    if (c.type !== 'organizzazione') delete c.parte
    if (c.type !== 'anagrafica') { delete c.tipoAnagrafica; delete c.creaScheda }
    if (Array.isArray(c.ruoliVisibili) && c.ruoliVisibili.length === 0) delete c.ruoliVisibili
    return c
  })
  return out
}

function CampoEditor({ f, i, n, catalogo, campi, modo, tipiAnagrafica, onChange, onSu, onGiu, onTogli, onDuplica }) {
  const g = useGlossario()
  const uid = useId()
  const [aperto, setAperto] = useState(!!f._aperto)
  if (eSezione(f)) return <SezioneEditor {...{ f, i, n, onChange, onSu, onGiu, onTogli, aperto, setAperto }} />
  const Tipo = TIPI.find(t => t.id === (f.type || 'text')) || TIPI[0]
  const cond = condizione(f)
  const listId = `catalogo-${f._id}`

  const cambiaEtichetta = (label) => onChange(f._nomeAuto || !f.name ? { label, name: nomeDaEtichetta(label), _nomeAuto: true } : { label })
  const cambiaNome = (name) => {
    const clean = name.replace(/[^a-zA-Z0-9_-]/g, '')
    const noto = catalogo[clean]
    // riuso di un dato già presente in altri form: propone etichetta e tipo
    if (noto && !f.label) onChange({ name: clean, label: noto.label, type: noto.type, options: noto.options,
      ...(noto.colonne ? { colonne: noto.colonne.map(c => ({ ...c, _id: nuovoId() })), minRighe: noto.minRighe, maxRighe: noto.maxRighe } : {}),
      _nomeAuto: false })
    else onChange({ name: clean, _nomeAuto: false })
  }

  return (
    <div className={cx('rounded-campo border bg-white', aperto ? 'border-pino-200 shadow-pannello' : 'border-linea')}>
      <div className="flex items-center gap-2 px-3 py-2">
        <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-carta text-pino-700"><Tipo.icona className="size-4" /></span>
        <button onClick={() => setAperto(!aperto)} className="min-w-0 flex-1 text-left">
          <span className="block truncate text-[15px] font-medium text-pino-900">{f.label || <span className="text-nebbia">Senza etichetta</span>}</span>
          <span className="block truncate text-xs text-grafite">
            {Tipo.label}{f.name ? `, ${f.name}` : ''}{f.required ? ', obbligatorio' : ''}{f.readonly ? ', sola lettura' : ''}
            {f.type === 'anagrafica' && f.tipoAnagrafica ? `, ${tipiAnagrafica.find(t => t.codice === f.tipoAnagrafica)?.nome || f.tipoAnagrafica}` : ''}
            {f.chiave ? ', chiave' : ''}{f.cercabile ? ', ricercabile' : ''}{modo === 'anagrafica' && f.titolo ? ', titolo' : ''}
            {cond ? `, ${descriviCondizione(cond, campi)}` : ''}
            {descriviRegole(f) ? `, ${descriviRegole(f)}` : ''}
          </span>
        </button>
        <div className="flex items-center">
          <IconBtn label="Sposta su" disabled={i === 0} onClick={onSu}><ChevronUp className="size-4" /></IconBtn>
          <IconBtn label="Sposta giù" disabled={i === n - 1} onClick={onGiu}><ChevronDown className="size-4" /></IconBtn>
          <IconBtn label="Duplica" onClick={onDuplica}><Copy className="size-4" /></IconBtn>
          <IconBtn label="Elimina" onClick={onTogli} pericolo><Trash className="size-4" /></IconBtn>
        </div>
      </div>

      {aperto && (
        <div className="grid gap-3 border-t border-linea px-3 py-3 sm:grid-cols-2">
          <div>
            <label className="etichetta" htmlFor={`${uid}-5`}>Etichetta</label>
            <input id={`${uid}-5`} className="campo" autoFocus={!f.label} value={f.label || ''} onChange={e => cambiaEtichetta(e.target.value)} placeholder="Es. Numero di protocollo" />
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-6`}>Nome del dato</label>
            <input id={`${uid}-6`} className="campo font-mono text-sm" list={listId} value={f.name || ''} onChange={e => cambiaNome(e.target.value)} placeholder="numeroDiProtocollo" />
            <datalist id={listId}>{Object.values(catalogo).map(c => <option key={c.name} value={c.name}>{c.label}</option>)}</datalist>
            <p className="mt-1 text-xs text-grafite">Stesso nome in più form = stesso dato {gl('pratica', 'del')}.</p>
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-7`}>Tipo</label>
            <select id={`${uid}-7`} className="campo" value={f.type || 'text'} onChange={e => onChange({ ...NESSUNA_REGOLA, type: e.target.value, ...perTipo(e.target.value, f),
              ...((modo === 'anagrafica' ? INDICIZZABILI : ['text', 'anagrafica']).includes(e.target.value) ? {} : { titolo: undefined }) })}>
              {tipiPer(modo).map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
            </select>
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-8`}>Aiuto per chi compila</label>
            <input id={`${uid}-8`} className="campo" value={f.help || ''} onChange={e => onChange({ help: e.target.value })} placeholder="Facoltativo" />
          </div>

          {f.type === 'organizzazione' && !f.readonly && (
            <div className="sm:col-span-2">
              <label className="etichetta" htmlFor={`${uid}-9`}>È la parte {gl('pratica', 'del')}</label>
              <input id={`${uid}-9`} className="campo font-mono text-sm" value={f.parte || ''} placeholder="Es. controparte"
                     onChange={e => onChange({ parte: e.target.value.replace(/[^a-zA-Z0-9_]/g, '') || undefined })} />
              <p className="mt-1 text-xs text-grafite">
                L'organizzazione scelta diventa questa parte: i suoi utenti esterni vedono {gl('pratica', 'il')} e lavorano le attività
                del BPMN assegnate al gruppo <span className="font-mono">parte:{f.parte || 'controparte'}</span>.
              </p>
            </div>
          )}

          {f.type === 'select' && (
            <div className="sm:col-span-2">
              <p className="etichetta">Opzioni</p>
              <Opzioni valori={f.options || []} onChange={options => onChange({ options })} />
            </div>
          )}

          {f.type === 'anagrafica' && (
            <div className="sm:col-span-2">
              <label className="etichetta" htmlFor={`${uid}-11`}>Scheda di quale anagrafica</label>
              <select id={`${uid}-11`} className="campo" value={f.tipoAnagrafica || ''} onChange={e => onChange({ tipoAnagrafica: e.target.value || undefined })}>
                <option value="">Scegli…</option>
                {f.tipoAnagrafica && !tipiAnagrafica.some(t => t.codice === f.tipoAnagrafica) && <option value={f.tipoAnagrafica}>{f.tipoAnagrafica}</option>}
                {tipiAnagrafica.map(t => <option key={t.codice} value={t.codice}>{t.nome}</option>)}
              </select>
              <p className="mt-1 text-xs text-grafite">
                {modo === 'anagrafica'
                  ? 'Collega la scheda a una scheda di un\'altra anagrafica (o della stessa), scelta tra quelle attive.'
                  : 'Chi compila sceglie una scheda attiva (serve la regola "sceglie" o "vede" dell\'anagrafica); ' + gl('pratica', 'il') + ' ne conserva una fotografia. Una scheda nuova la crea solo chi può modificare l\'anagrafica.'}
              </p>
            </div>
          )}

          <div className="flex flex-wrap gap-x-5 gap-y-2 sm:col-span-2">
            <Spunta checked={!!f.required} onChange={v => onChange({ required: v, ...(v ? { readonly: false } : {}) })}>Obbligatorio</Spunta>
            <Spunta checked={!!f.readonly} onChange={v => onChange({ readonly: v, ...(v ? { required: false } : {}) })}>Sola lettura</Spunta>
            {modo === 'form' && !f.readonly && <Spunta checked={f.precompila === false} onChange={v => onChange({ precompila: v ? false : undefined })}>Non riproporre il valore precedente</Spunta>}
            {modo === 'form' && !f.readonly && ['text', 'anagrafica'].includes(f.type) && <Spunta checked={!!f.titolo} onChange={v => onChange({ titolo: v })}>Usa come titolo {gl('pratica', 'del')}</Spunta>}
            {!f.readonly && f.type === 'anagrafica' && <Spunta checked={!!f.creaScheda} onChange={v => onChange({ creaScheda: v || undefined })}>Si può creare una scheda nuova da qui</Spunta>}
            {modo === 'anagrafica' && INDICIZZABILI.includes(f.type || 'text') && <>
              <Spunta checked={!!f.titolo} onChange={v => onChange({ titolo: v || undefined })}>Dà il nome alla scheda</Spunta>
              <Spunta checked={!!f.chiave} onChange={v => onChange({ chiave: v || undefined, ...(v ? { visibileSe: undefined } : {}) })}>Chiave: non si ripete tra le schede</Spunta>
              <Spunta checked={!!f.cercabile} onChange={v => onChange({ cercabile: v || undefined })}>Si trova con la ricerca</Spunta>
            </>}
          </div>

          {f.type === 'tabella' && <ColonneEditor f={f} onChange={onChange} />}

          {!f.readonly && !['tabella', 'organizzazione', 'anagrafica'].includes(f.type) && <RegoleEditor f={f} onChange={onChange} />}

          <fieldset className="space-y-2 rounded-campo bg-carta px-3 py-2.5 sm:col-span-2">
            <legend className="sr-only">Chi vede questo dato</legend>
            <p className="text-[13px] font-medium text-pino-900">{modo === 'anagrafica' ? 'Chi vede questo dato nella scheda' : `Chi vede questo dato ${gl('pratica', 'nel')}`}</p>
            {modo === 'form' && f.type !== 'anagrafica' && (
              <Spunta checked={!!f.visibileAlleParti} onChange={v => onChange({ visibileAlleParti: v || undefined })}>
                Anche le parti esterne ({g('esterno', 'nomi')}, organizzazioni)
              </Spunta>
            )}
            <div>
              <label className="text-[13px] text-grafite" htmlFor={`${uid}-10`}>Tra il personale, solo chi ha questi ruoli (codici separati da virgola; vuoto = tutti)</label>
              <input id={`${uid}-10`} className="campo mt-1 font-mono text-sm" placeholder="Es. auditor, responsabile"
                     value={(f.ruoliVisibili || []).join(', ')}
                     onChange={e => onChange({ ruoliVisibili: e.target.value.split(',').map(x => x.trim().toLowerCase()).filter(Boolean) })} />
            </div>
          </fieldset>

          <div className="sm:col-span-2">
            {!f.chiave && <CondizioneEditor f={f} campi={campi} onChange={visibileSe => onChange({ visibileSe })} />}
          </div>
        </div>
      )}
    </div>
  )
}

/** Regole di validazione secondo il tipo: limiti per numeri e date, lunghezza e formato per i testi. */
function RegoleEditor({ f, onChange }) {
  const uid = useId()
  const tipo = f.type || 'text'
  const v = (k) => f[k] ?? ''
  const imposta = (k) => (e) => onChange({ [k]: e.target.value.trim() === '' ? undefined : e.target.value })
  if (tipo === 'number') return (
    <div className="grid gap-3 sm:col-span-2 sm:grid-cols-2">
      <div><label className="etichetta" htmlFor={`${uid}-9`}>Valore minimo</label><input id={`${uid}-9`} className="campo" inputMode="decimal" value={v('min')} onChange={imposta('min')} placeholder="Nessuno" /></div>
      <div><label className="etichetta" htmlFor={`${uid}-10`}>Valore massimo</label><input id={`${uid}-10`} className="campo" inputMode="decimal" value={v('max')} onChange={imposta('max')} placeholder="Nessuno" /></div>
    </div>
  )
  if (tipo === 'date') return (
    <div className="grid gap-3 sm:col-span-2 sm:grid-cols-2">
      <div><label className="etichetta" htmlFor={`${uid}-11`}>Non prima del</label><input id={`${uid}-11`} className="campo" value={v('min')} onChange={imposta('min')} placeholder="oggi, oppure 2026-01-31" /></div>
      <div><label className="etichetta" htmlFor={`${uid}-12`}>Non dopo il</label><input id={`${uid}-12`} className="campo" value={v('max')} onChange={imposta('max')} placeholder="oggi, oppure 2026-12-31" /></div>
    </div>
  )
  if (tipo !== 'text' && tipo !== 'textarea') return null
  return (
    <div className="grid gap-3 sm:col-span-2 sm:grid-cols-2">
      <div><label className="etichetta" htmlFor={`${uid}-13`}>Caratteri minimi</label><input id={`${uid}-13`} className="campo" inputMode="numeric" value={v('minLunghezza')} onChange={imposta('minLunghezza')} placeholder="Nessuno" /></div>
      <div><label className="etichetta" htmlFor={`${uid}-14`}>Caratteri massimi</label><input id={`${uid}-14`} className="campo" inputMode="numeric" value={v('maxLunghezza')} onChange={imposta('maxLunghezza')} placeholder="Nessuno" /></div>
      {tipo === 'text' && (
        <div className="sm:col-span-2">
          <label className="etichetta" htmlFor={`${uid}-15`}>Formato</label>
          <select id={`${uid}-15`} className="campo" value={v('formato')} onChange={e => onChange({ formato: e.target.value || undefined,
            ...(e.target.value !== 'espressione' ? { espressione: undefined, messaggioFormato: undefined } : {}) })}>
            <option value="">Testo libero</option>
            {FORMATI.map(x => <option key={x.id} value={x.id}>{x.label}</option>)}
          </select>
          {['codiceFiscale', 'partitaIva', 'iban'].includes(f.formato) && <p className="mt-1 text-[13px] text-grafite">Si controlla anche il carattere di controllo.</p>}
        </div>
      )}
      {tipo === 'text' && f.formato === 'espressione' && (
        <>
          <div><label className="etichetta" htmlFor={`${uid}-16`}>Espressione regolare</label><input id={`${uid}-16`} className="campo font-mono text-sm" value={v('espressione')} onChange={imposta('espressione')} placeholder="[0-9]{4}/[0-9]+" /></div>
          <div><label className="etichetta" htmlFor={`${uid}-17`}>Messaggio se non va bene</label><input id={`${uid}-17`} className="campo" value={v('messaggioFormato')} onChange={imposta('messaggioFormato')} placeholder="Es. scrivi anno/numero, come 2026/123" /></div>
        </>
      )}
    </div>
  )
}

/** Colonne di una tabella di righe, con tipo, obbligo, opzioni e regole; numero di righe ammesse. */
function ColonneEditor({ f, onChange }) {
  const colonne = f.colonne || []
  const [aperta, setAperta] = useState(null)
  const cambia = (i, patch) => onChange({ colonne: colonne.map((c, j) => j === i ? { ...c, ...patch } : c) })
  const sposta = (i, d) => { const a = [...colonne]; [a[i], a[i + d]] = [a[i + d], a[i]]; onChange({ colonne: a }) }
  const aggiungi = () => {
    const c = { _id: nuovoId(), _nomeAuto: true, name: '', label: '', type: 'text' }
    onChange({ colonne: [...colonne, c] }); setAperta(c._id)
  }
  const righe = (k) => (e) => onChange({ [k]: e.target.value.trim() === '' ? undefined : e.target.value.replace(/[^0-9]/g, '') })
  return (
    <div className="space-y-3 rounded-campo bg-carta px-3 py-3 sm:col-span-2">
      <p className="text-[15px] font-medium text-pino-900">Colonne</p>
      <ul className="space-y-2">
        {colonne.map((c, i) => {
          const Tipo = TIPI.find(t => t.id === (c.type || 'text')) || TIPI[0]
          const dettagli = [Tipo.label, c.name, c.required && 'obbligatoria', descriviRegole(c)].filter(Boolean).join(', ')
          return (
            <li key={c._id ?? i} className="rounded-campo border border-linea bg-white">
              <div className="flex items-center gap-2 px-2.5 py-1.5">
                <Tipo.icona className="size-4 shrink-0 text-pino-700" />
                <button type="button" onClick={() => setAperta(aperta === c._id ? null : c._id)} className="min-w-0 flex-1 text-left">
                  <span className="block truncate text-[15px] text-pino-900">{c.label || <span className="text-nebbia">Senza etichetta</span>}</span>
                  <span className="block truncate text-xs text-grafite">{dettagli}</span>
                </button>
                <IconBtn label="Sposta a sinistra" disabled={i === 0} onClick={() => sposta(i, -1)}><ChevronUp className="size-4" /></IconBtn>
                <IconBtn label="Sposta a destra" disabled={i === colonne.length - 1} onClick={() => sposta(i, 1)}><ChevronDown className="size-4" /></IconBtn>
                <IconBtn label="Elimina la colonna" disabled={colonne.length === 1} pericolo
                         onClick={() => onChange({ colonne: colonne.filter((_, j) => j !== i) })}><Trash className="size-4" /></IconBtn>
              </div>
              {aperta === c._id && (
                <div className="grid gap-3 border-t border-linea px-2.5 py-3 sm:grid-cols-2">
                  <div>
                    <label className="etichetta" htmlFor={`col-etichetta-${c._id}`}>Etichetta</label>
                    <input id={`col-etichetta-${c._id}`} className="campo" autoFocus={!c.label} value={c.label || ''} placeholder="Es. Importo"
                           onChange={e => cambia(i, c._nomeAuto || !c.name ? { label: e.target.value, name: nomeDaEtichetta(e.target.value), _nomeAuto: true } : { label: e.target.value })} />
                  </div>
                  <div>
                    <label className="etichetta" htmlFor={`col-nome-${c._id}`}>Nome della colonna</label>
                    <input id={`col-nome-${c._id}`} className="campo font-mono text-sm" value={c.name || ''} placeholder="importo"
                           onChange={e => cambia(i, { name: e.target.value.replace(/[^a-zA-Z0-9_]/g, ''), _nomeAuto: false })} />
                  </div>
                  <div>
                    <label className="etichetta" htmlFor={`col-tipo-${c._id}`}>Tipo</label>
                    <select id={`col-tipo-${c._id}`} className="campo" value={c.type || 'text'}
                            onChange={e => cambia(i, { ...NESSUNA_REGOLA, type: e.target.value, ...perTipo(e.target.value, c) })}>
                      {TIPI.filter(t => TIPI_COLONNA.includes(t.id)).map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
                    </select>
                  </div>
                  <div className="flex items-end pb-2">
                    <Spunta checked={!!c.required} onChange={v => cambia(i, { required: v })}>Obbligatoria in ogni riga</Spunta>
                  </div>
                  {c.type === 'select' && (
                    <div className="sm:col-span-2">
                      <p className="etichetta">Opzioni</p>
                      <Opzioni valori={c.options || []} onChange={options => cambia(i, { options })} />
                    </div>
                  )}
                  <RegoleEditor f={c} onChange={patch => cambia(i, patch)} />
                </div>
              )}
            </li>
          )
        })}
      </ul>
      <div className="flex flex-wrap items-end gap-3">
        <Button misura="sm" icona={Plus} onClick={aggiungi} disabled={colonne.length >= 20}>Aggiungi colonna</Button>
        {!f.readonly && (
          <>
            <div className="w-32">
              <label className="etichetta" htmlFor={`righe-min-${f._id}`}>Righe minime</label>
              <input id={`righe-min-${f._id}`} className="campo h-9" inputMode="numeric" value={f.minRighe ?? ''} onChange={righe('minRighe')} placeholder="Nessuna" />
            </div>
            <div className="w-32">
              <label className="etichetta" htmlFor={`righe-max-${f._id}`}>Righe massime</label>
              <input id={`righe-max-${f._id}`} className="campo h-9" inputMode="numeric" value={f.maxRighe ?? ''} onChange={righe('maxRighe')} placeholder="500" />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

/** Titolo e istruzioni di una sezione del form. */
function SezioneEditor({ f, i, n, onChange, onSu, onGiu, onTogli, aperto, setAperto }) {
  const uid = useId()
  return (
    <div className={cx('rounded-campo border border-dashed bg-carta/60', aperto ? 'border-pino-200' : 'border-linea')}>
      <div className="flex items-center gap-2 px-3 py-2">
        <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-white text-pino-700"><Heading className="size-4" /></span>
        <button onClick={() => setAperto(!aperto)} className="min-w-0 flex-1 text-left">
          <span className="block truncate font-display text-[15px] font-semibold text-pino-900">{f.label || <span className="text-nebbia">Sezione senza titolo</span>}</span>
          <span className="block truncate text-xs text-grafite">Sezione: i campi seguenti, fino alla prossima sezione</span>
        </button>
        <div className="flex items-center">
          <IconBtn label="Sposta su" disabled={i === 0} onClick={onSu}><ChevronUp className="size-4" /></IconBtn>
          <IconBtn label="Sposta giù" disabled={i === n - 1} onClick={onGiu}><ChevronDown className="size-4" /></IconBtn>
          <IconBtn label="Elimina" onClick={onTogli} pericolo><Trash className="size-4" /></IconBtn>
        </div>
      </div>
      {aperto && (
        <div className="grid gap-3 border-t border-linea px-3 py-3 sm:grid-cols-2">
          <div>
            <label className="etichetta" htmlFor={`${uid}-18`}>Titolo della sezione</label>
            <input id={`${uid}-18`} className="campo" autoFocus={!f.label} value={f.label || ''} onChange={e => onChange({ label: e.target.value })} placeholder="Es. Dati del richiedente" />
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-19`}>Istruzioni</label>
            <input id={`${uid}-19`} className="campo" value={f.help || ''} onChange={e => onChange({ help: e.target.value })} placeholder="Facoltative" />
          </div>
        </div>
      )}
    </div>
  )
}

/** "Mostra solo se": il campo compare quando un campo a scelta o sì/no ha uno dei valori indicati. */
function CondizioneEditor({ f, campi, onChange }) {
  const sorgenti = campi.filter(x => !eSezione(x) && x.name && x.name !== f.name && valoriPossibili(x).length > 0)
  const cond = condizione(f)
  const sorgente = cond && campi.find(x => x.name === cond.campo)
  const possibili = valoriPossibili(sorgente)

  if (!cond) {
    if (sorgenti.length === 0) return null
    return (
      <button type="button" onClick={() => {
        const s = sorgenti[0]
        onChange({ campo: s.name, valori: [valoriPossibili(s)[0].valore] })
      }} className="inline-flex items-center gap-1.5 text-sm font-medium text-verde-700 hover:underline">
        <Eye className="size-4" /> Mostra solo se un'altra risposta ha un certo valore
      </button>
    )
  }

  const scegliValore = (v, si) => {
    const valori = si ? [...cond.valori, v] : cond.valori.filter(x => x !== v)
    onChange({ campo: cond.campo, valori })
  }

  return (
    <div className="space-y-2 rounded-campo bg-carta px-3 py-3">
      <div className="flex flex-wrap items-center gap-2 text-[15px]">
        <Eye className="size-4 text-pino-700" />
        <span>Mostra solo se</span>
        <select className="campo h-9 w-auto" value={cond.campo} onChange={e => {
          const s = campi.find(x => x.name === e.target.value)
          onChange({ campo: e.target.value, valori: [valoriPossibili(s)[0]?.valore].filter(Boolean) })
        }}>
          {!sorgenti.some(s => s.name === cond.campo) && <option value={cond.campo}>{cond.campo} (non trovato)</option>}
          {sorgenti.map(s => <option key={s.name} value={s.name}>{s.label || s.name}</option>)}
        </select>
        <span>è</span>
        <button type="button" onClick={() => onChange(undefined)} className="ml-auto text-sm text-grafite hover:text-mattone-600">Togli condizione</button>
      </div>
      <div className="flex flex-wrap gap-x-5 gap-y-2">
        {possibili.map(p => (
          <Spunta key={p.valore} checked={cond.valori.includes(p.valore)} onChange={si => scegliValore(p.valore, si)}>{p.etichetta}</Spunta>
        ))}
      </div>
      {cond.valori.length === 0 && <p className="text-[13px] text-mattone-600">Scegli almeno un valore.</p>}
      {f.required && <p className="text-[13px] text-grafite">Il campo è obbligatorio solo quando è visibile.</p>}
    </div>
  )
}

function Opzioni({ valori, onChange }) {
  const [nuova, setNuova] = useState('')
  const aggiungi = () => { const v = nuova.trim(); if (v && !valori.includes(v)) onChange([...valori, v]); setNuova('') }
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {valori.map(o => (
          <span key={o} className="inline-flex items-center gap-1 rounded-full bg-carta py-1 pl-3 pr-1.5 text-sm ring-1 ring-linea">
            {o}
            <button onClick={() => onChange(valori.filter(x => x !== o))} className="rounded-full p-0.5 text-grafite hover:bg-white hover:text-mattone-600" aria-label={`Togli ${o}`}>
              <X className="size-3.5" />
            </button>
          </span>
        ))}
      </div>
      <div className="flex gap-2">
        <input className="campo h-9" value={nuova} placeholder="Nuova opzione" onChange={e => setNuova(e.target.value)}
               onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); aggiungi() } }} />
        <Button misura="sm" icona={Plus} onClick={aggiungi}>Aggiungi</Button>
      </div>
    </div>
  )
}

function Spunta({ checked, onChange, children }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-[15px] text-pino-900">
      <input type="checkbox" className="size-4 accent-verde-600" checked={checked} onChange={e => onChange(e.target.checked)} />
      {children}
    </label>
  )
}

function IconBtn({ label, onClick, disabled, pericolo, children }) {
  return (
    <button onClick={onClick} disabled={disabled} aria-label={label} title={label}
            className={cx('rounded-md p-1.5 text-grafite disabled:opacity-30', pericolo ? 'hover:bg-mattone-50 hover:text-mattone-600' : 'hover:bg-carta hover:text-pino-900')}>
      {children}
    </button>
  )
}
