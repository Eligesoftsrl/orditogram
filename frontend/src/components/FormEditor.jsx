import { useEffect, useMemo, useState } from 'react'
import {
  TextAlignStart, Braces, CalendarDays, ChevronDown, ChevronUp, CircleCheck, Copy, Eye, Hash, Heading, ListChecks,
  Plus, Save, SquareCheck, Trash, Type, X
} from 'lucide-react'
import { api } from '../api.js'
import { cx, quando } from '../lib/format.js'
import DynamicForm from './DynamicForm.jsx'
import { SEZIONE, condizione, descriviCondizione, eSezione, valoriPossibili } from '../lib/formCondizioni.js'
import { Badge, Button, Caricamento, Errore, Pannello, Vuoto, useToast } from './ui.jsx'

const TIPI = [
  { id: 'text', label: 'Testo', icona: Type },
  { id: 'textarea', label: 'Testo lungo', icona: TextAlignStart },
  { id: 'number', label: 'Numero', icona: Hash },
  { id: 'date', label: 'Data', icona: CalendarDays },
  { id: 'select', label: 'Scelta', icona: ListChecks },
  { id: 'checkbox', label: 'Sì / no', icona: SquareCheck }
]

const DOCUMENTI = [
  { id: '', label: 'Nessuno', aiuto: 'Il form non mostra documenti.' },
  { id: 'carica', label: 'Allegati all\'avvio', aiuto: 'Solo per il form di avvio: chi apre la pratica allega i documenti.' },
  { id: 'lettura', label: 'In consultazione', aiuto: 'Si vedono e si scaricano i documenti della pratica.' },
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
 */
export default function FormEditor({ chiave: chiaveIniziale, nuovo: nuovoIniziale = false, suggerimento = '', onSalvato, onEliminato }) {
  const [catalogo, setCatalogo] = useState({})
  const [sorgente, setSorgente] = useState(null)
  const [chiave, setChiave] = useState(nuovoIniziale ? suggerimento : chiaveIniziale)
  const [nuovo, setNuovo] = useState(nuovoIniziale)
  const [form, setForm] = useState(nuovoIniziale ? { ...NUOVO, fields: [] } : null)
  const [originale, setOriginale] = useState('')
  const [vistaJson, setVistaJson] = useState(false)
  const [testoJson, setTestoJson] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)
  const toast = useToast()

  // catalogo dei campi già usati negli altri form (per riusare gli stessi dati)
  useEffect(() => {
    api.forms().then(async l => {
      setSorgente(l.find(f => f.formKey === chiaveIniziale)?.source || null)
      const tutti = await Promise.all(l.map(f => api.form(f.formKey).catch(() => null)))
      const c = {}
      tutti.forEach(def => def?.fields?.forEach(f => { if (!c[f.name]) c[f.name] = f }))
      setCatalogo(c)
    }).catch(() => {})
  }, [chiaveIniziale])

  const apri = async (k) => {
    setErrore(''); setVistaJson(false)
    try {
      const f = await api.form(k)
      const def = { ...NUOVO, ...f, fields: (f.fields || []).map(x => ({ ...x, _id: Math.random() })) }
      setChiave(k); setNuovo(false); setForm(def); setOriginale(JSON.stringify(pulisci(def)))
    } catch (e) { setErrore(e.message) }
  }
  useEffect(() => { if (!nuovoIniziale && chiaveIniziale) apri(chiaveIniziale) }, [chiaveIniziale, nuovoIniziale])

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
    const c = { ...f.fields[i], _id: Math.random(), name: f.fields[i].name + 'Copia', label: f.fields[i].label + ' (copia)' }
    const a = [...f.fields]; a.splice(i + 1, 0, c)
    return { ...f, fields: a }
  })
  const aggiungi = (tipo) => setForm(f => ({
    ...f,
    fields: [...f.fields, tipo === SEZIONE
      ? { _id: Math.random(), _aperto: true, type: SEZIONE, label: '' }
      : { _id: Math.random(), _aperto: true, _nomeAuto: true, type: tipo, label: '', name: '',
          ...(tipo === 'select' ? { options: ['Opzione 1', 'Opzione 2'] } : {}) }]
  }))

  const salva = async () => {
    setErrore(''); setAttesa(true)
    try {
      const testo = vistaJson ? testoJson : json
      JSON.parse(testo)
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
      setForm({ ...NUOVO, ...f, fields: (f.fields || []).map(x => ({ ...x, _id: Math.random() })) })
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
      esempi[f.name] = f.type === 'checkbox' ? true : f.type === 'date' ? '2026-10-15' : (f.label ? `Esempio di ${f.label.toLowerCase()}` : 'Esempio')
    })
    return { def: pulisci(form), esempi }
  }, [form])

  if (!form) return errore ? <Errore>{errore}</Errore> : <Caricamento />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-pannello border border-linea bg-white px-4 py-3 shadow-pannello">
        {nuovo ? (
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
        <Button misura="sm" variante="primario" icona={Save} disabled={!chiave} attesa={attesa} onClick={salva}>Salva</Button>
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
                    <Pannello titolo="Impostazioni">
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div className="sm:col-span-2">
                          <label className="etichetta">Titolo</label>
                          <input className="campo" value={form.title || ''} onChange={e => aggiorna({ title: e.target.value })} />
                        </div>
                        <div className="sm:col-span-2">
                          <label className="etichetta">Istruzioni per chi compila</label>
                          <textarea rows={2} className="campo" value={form.description || ''} onChange={e => aggiorna({ description: e.target.value })}
                                    placeholder="Es. Controlla dati e allegati prima di confermare." />
                        </div>
                        <div>
                          <label className="etichetta">Testo del pulsante</label>
                          <input className="campo" value={form.submitLabel || ''} onChange={e => aggiorna({ submitLabel: e.target.value })} placeholder="Conferma" />
                        </div>
                        <div>
                          <label className="etichetta">Documenti della pratica</label>
                          <select className="campo" value={form.documenti || ''} onChange={e => aggiorna({ documenti: e.target.value || undefined })}>
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
                      </div>
                    </Pannello>

                    <Pannello titolo={`Campi (${form.fields.filter(f => !eSezione(f)).length})`} corpoClassName="space-y-2 p-3">
                      {form.fields.length === 0 && <p className="px-2 py-3 text-sm text-grafite">Nessun campo. Aggiungine uno qui sotto.</p>}
                      {form.fields.map((f, i) => (
                        <CampoEditor key={f._id} f={f} i={i} n={form.fields.length} catalogo={catalogo} campi={form.fields}
                                     onChange={p => aggiornaCampo(i, p)} onSu={() => sposta(i, -1)} onGiu={() => sposta(i, 1)}
                                     onTogli={() => togli(i)} onDuplica={() => duplica(i)} />
                      ))}
                      <div className="rounded-campo border-2 border-dashed border-linea p-3">
                        <p className="mb-2 text-sm font-medium text-pino-800">Aggiungi un campo</p>
                        <div className="flex flex-wrap gap-2">
                          {TIPI.map(t => <Button key={t.id} misura="sm" icona={t.icona} onClick={() => aggiungi(t.id)}>{t.label}</Button>)}
                          <Button misura="sm" variante="fantasma" icona={Heading} onClick={() => aggiungi(SEZIONE)}>Sezione</Button>
                        </div>
                      </div>
                    </Pannello>
                  </div>
                )}

                <div className="xl:sticky xl:top-20">
                  <Pannello titolo="Anteprima" azioni={<span className="text-[13px] text-grafite">come la vedrà l'utente</span>} className="bg-white">
                    {anteprima && (anteprima.def.fields.length || anteprima.def.description
                      ? <DynamicForm key={json} form={anteprima.def} initialValues={anteprima.esempi} avvio onSubmit={() => toast.ok('Anteprima: nessun dato inviato')} />
                      : <p className="text-sm text-grafite">L'anteprima compare quando aggiungi il primo campo.</p>)}
                  </Pannello>
                </div>
              </div>
    </div>
  )
}

/** Il form da salvare: senza le proprietà interne dell'editor e senza valori vuoti. */
function pulisci(form) {
  const out = {}
  ;['title', 'description', 'documenti', 'documentiObbligatori', 'submitLabel'].forEach(k => {
    if (form[k] !== undefined && form[k] !== '' && form[k] !== false) out[k] = form[k]
  })
  out.fields = form.fields.map(f => {
    if (eSezione(f)) {
      const sez = { type: SEZIONE, label: f.label || '' }
      if (f.help) sez.help = f.help
      return sez
    }
    const c = {}
    Object.entries(f).forEach(([k, v]) => {
      if (k.startsWith('_') || v === '' || v === undefined || v === false) return
      if (k === 'options' && f.type !== 'select') return
      c[k] = v
    })
    if (f.precompila === false) c.precompila = false
    if (!c.type) c.type = 'text'
    return c
  })
  return out
}

function CampoEditor({ f, i, n, catalogo, campi, onChange, onSu, onGiu, onTogli, onDuplica }) {
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
    if (noto && !f.label) onChange({ name: clean, label: noto.label, type: noto.type, options: noto.options, _nomeAuto: false })
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
            {cond ? `, ${descriviCondizione(cond, campi)}` : ''}
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
            <label className="etichetta">Etichetta</label>
            <input className="campo" autoFocus={!f.label} value={f.label || ''} onChange={e => cambiaEtichetta(e.target.value)} placeholder="Es. Numero di protocollo" />
          </div>
          <div>
            <label className="etichetta">Nome del dato</label>
            <input className="campo font-mono text-sm" list={listId} value={f.name || ''} onChange={e => cambiaNome(e.target.value)} placeholder="numeroDiProtocollo" />
            <datalist id={listId}>{Object.values(catalogo).map(c => <option key={c.name} value={c.name}>{c.label}</option>)}</datalist>
            <p className="mt-1 text-xs text-grafite">Stesso nome in più form = stesso dato della pratica.</p>
          </div>
          <div>
            <label className="etichetta">Tipo</label>
            <select className="campo" value={f.type || 'text'} onChange={e => onChange({ type: e.target.value, ...(e.target.value === 'select' && !f.options ? { options: ['Opzione 1', 'Opzione 2'] } : {}) })}>
              {TIPI.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
            </select>
          </div>
          <div>
            <label className="etichetta">Aiuto per chi compila</label>
            <input className="campo" value={f.help || ''} onChange={e => onChange({ help: e.target.value })} placeholder="Facoltativo" />
          </div>

          {f.type === 'select' && (
            <div className="sm:col-span-2">
              <label className="etichetta">Opzioni</label>
              <Opzioni valori={f.options || []} onChange={options => onChange({ options })} />
            </div>
          )}

          <div className="flex flex-wrap gap-x-5 gap-y-2 sm:col-span-2">
            <Spunta checked={!!f.required} onChange={v => onChange({ required: v, ...(v ? { readonly: false } : {}) })}>Obbligatorio</Spunta>
            <Spunta checked={!!f.readonly} onChange={v => onChange({ readonly: v, ...(v ? { required: false } : {}) })}>Sola lettura</Spunta>
            {!f.readonly && <Spunta checked={f.precompila === false} onChange={v => onChange({ precompila: v ? false : undefined })}>Non riproporre il valore precedente</Spunta>}
            {!f.readonly && f.type === 'text' && <Spunta checked={!!f.titolo} onChange={v => onChange({ titolo: v })}>Usa come titolo della pratica</Spunta>}
          </div>

          <div className="sm:col-span-2">
            <CondizioneEditor f={f} campi={campi} onChange={visibileSe => onChange({ visibileSe })} />
          </div>
        </div>
      )}
    </div>
  )
}

/** Titolo e istruzioni di una sezione del form. */
function SezioneEditor({ f, i, n, onChange, onSu, onGiu, onTogli, aperto, setAperto }) {
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
            <label className="etichetta">Titolo della sezione</label>
            <input className="campo" autoFocus={!f.label} value={f.label || ''} onChange={e => onChange({ label: e.target.value })} placeholder="Es. Dati del richiedente" />
          </div>
          <div>
            <label className="etichetta">Istruzioni</label>
            <input className="campo" value={f.help || ''} onChange={e => onChange({ help: e.target.value })} placeholder="Facoltative" />
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
