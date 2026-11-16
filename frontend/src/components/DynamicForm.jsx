import { useEffect, useId, useRef, useState } from 'react'
import { api } from '../api.js'
import { FileUp, Paperclip, X } from 'lucide-react'
import { cx, dimensione, giorno } from '../lib/format.js'
import { Button, Errore } from './ui.jsx'
import { campiNascosti, eSezione, raggruppa, senzaNascosti } from '../lib/formCondizioni.js'
import { erroriForm, righePiene } from '../lib/formRegole.js'
import { CampoTabella, TabellaLettura, righeIniziali } from './CampoTabella.jsx'

/**
 * Form generato dalla definizione JSON (formKey del BPMN).
 *
 * Proprietà del form:   title, description, submitLabel,
 *                       documenti: "carica" (allegati all'avvio) | "lettura" | "modifica",
 *                       documentiObbligatori: true
 * Proprietà dei campi:  name, label, type (text, number, date, textarea, select, checkbox),
 *                       required, readonly, default, options, placeholder, help,
 *                       precompila: false (non riproporre il valore precedente, utile nei cicli),
 *                       visibileSe: { campo, valori } (il campo compare solo se l'altro ha uno di quei valori)
 * Regole:               min, max, minLunghezza, maxLunghezza, formato (vedi lib/formRegole.js), controllate
 *                       all'invio con il messaggio accanto al campo; il server le controlla di nuovo.
 * Sezioni:              { type: "sezione", label, help } divide i campi da compilare in gruppi con un titolo.
 * Tabelle:              { type: "tabella", colonne: [campi senza textarea], minRighe, maxRighe } raccoglie più righe
 *                       (vedi CampoTabella.jsx); le righe vuote non si inviano.
 * Organizzazioni:       { type: "organizzazione", parte: "controparte" } sceglie un'organizzazione esterna
 *                       dall'anagrafica (il valore è il suo id); con "parte" diventa una parte della pratica.
 *                       "organizzazioni" (id -> nome) serve a mostrare quelle già scelte.
 *
 * I campi in sola lettura diventano un riepilogo in alto; quelli da compilare restano campi.
 * I campi nascosti dalle condizioni non si mostrano, non sono obbligatori e non vengono inviati.
 */
export default function DynamicForm({ form, initialValues = {}, organizzazioni = {}, onSubmit, disabled, busy, avvio, mostraTitolo = true }) {
  const campi = form.fields || []
  const lettura = campi.filter(f => f.readonly && !eSezione(f))
  const modificabili = campi.filter(f => !f.readonly && !eSezione(f))
  const conAllegati = avvio && form.documenti === 'carica'

  const [values, setValues] = useState(() => {
    const v = {}
    modificabili.forEach(f => {
      if (f.type === 'tabella') {
        v[f.name] = righeIniziali(f, f.precompila === false ? null : initialValues[f.name])
        return
      }
      const vuoto = f.type === 'checkbox' ? false : (f.default ?? '')
      v[f.name] = f.precompila === false ? vuoto : (initialValues[f.name] ?? vuoto)
    })
    return v
  })
  const [files, setFiles] = useState([])
  const [errore, setErrore] = useState('')
  const [erroriCampi, setErroriCampi] = useState({})
  const set = (name, value) => {
    setValues(prev => ({ ...prev, [name]: value }))
    setErroriCampi(prev => { if (!prev[name]) return prev; const { [name]: _, ...resto } = prev; return resto })
  }

  const submit = (e) => {
    e.preventDefault()
    if (conAllegati && form.documentiObbligatori && files.length === 0) {
      setErrore('Allega almeno un documento')
      return
    }
    const sbagliati = erroriForm(campi, values, campiNascosti(campi, { ...initialValues, ...values }))
    setErroriCampi(sbagliati)
    if (Object.keys(sbagliati).length > 0) {
      setErrore(Object.keys(sbagliati).length === 1 ? 'Un campo va corretto' : `${Object.keys(sbagliati).length} campi vanno corretti`)
      document.getElementById(`campo-${Object.keys(sbagliati)[0]}`)?.focus()
      return
    }
    setErrore('')
    const daInviare = { ...values }
    campi.forEach(f => { if (f.type === 'tabella' && f.name in daInviare) daInviare[f.name] = righePiene(f, daInviare[f.name]) })
    onSubmit(senzaNascosti(campi, daInviare, initialValues), files)
  }

  const nascosti = campiNascosti(campi, { ...initialValues, ...values })
  const gruppi = raggruppa(campi.filter(f => !f.readonly && !nascosti.has(f.name)))

  const riepilogo = lettura.filter(f => {
    if (nascosti.has(f.name)) return false
    const v = initialValues[f.name]
    if (f.type === 'tabella') return Array.isArray(v) && v.length > 0
    return v !== undefined && v !== null && String(v).trim() !== ''
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      {((mostraTitolo && form.title) || form.description) && (
        <div>
          {mostraTitolo && form.title && <h3 className="text-lg font-semibold">{form.title}</h3>}
          {form.description && <p className="mt-1 max-w-prose text-[15px] text-grafite">{form.description}</p>}
        </div>
      )}

      {riepilogo.length > 0 && (
        <dl className="grid gap-x-6 gap-y-3 rounded-pannello bg-carta px-4 py-4 sm:grid-cols-2">
          {riepilogo.map(f => (
            <div key={f.name} className={cx(['textarea', 'tabella'].includes(f.type) && 'sm:col-span-2')}>
              <dt className="text-[13px] text-grafite">{f.label || f.name}</dt>
              {f.type === 'tabella' ? (
                <dd><TabellaLettura colonne={f.colonne} righe={initialValues[f.name]} /></dd>
              ) : (
                <dd className="mt-0.5 whitespace-pre-line break-words text-[15px] text-pino-900">
                  {f.type === 'organizzazione' ? (organizzazioni[initialValues[f.name]] || 'Organizzazione non più attiva') : mostraValore(f, initialValues[f.name])}
                </dd>
              )}
            </div>
          ))}
        </dl>
      )}

      {gruppi.map((g, i) => (
        <section key={g.sezione ? `sezione-${i}` : 'campi'} className={cx(g.sezione && 'border-t border-linea pt-5')}>
          {g.sezione && (
            <div className="mb-4">
              <h4 className="font-display text-[17px] font-semibold text-pino-900">{g.sezione.label}</h4>
              {g.sezione.help && <p className="mt-0.5 max-w-prose text-[14px] text-grafite">{g.sezione.help}</p>}
            </div>
          )}
          <div className="grid gap-x-5 gap-y-4 sm:grid-cols-2">
            {g.campi.map((f, i) => (
              <Campo key={f.name || `senza-nome-${i}`} f={f} value={values[f.name]} onChange={v => set(f.name, v)} disabled={disabled} errore={erroriCampi[f.name]}
                     nomeIniziale={organizzazioni[values[f.name]]} />
            ))}
          </div>
        </section>
      ))}

      {conAllegati && <Allegati files={files} setFiles={setFiles} obbligatori={form.documentiObbligatori} disabled={disabled} />}

      <Errore>{errore}</Errore>

      {!disabled && (
        <div className="flex items-center justify-end gap-3 border-t border-linea pt-5">
          <Button type="submit" variante="primario" misura="lg" attesa={busy}>
            {form.submitLabel || 'Completa'}
          </Button>
        </div>
      )}
    </form>
  )
}

function mostraValore(f, v) {
  if (f.type === 'checkbox') return v === true ? 'Sì' : 'No'
  if (f.type === 'date') return giorno(v)
  return String(v)
}

function Campo({ f, value, onChange, disabled, errore, nomeIniziale }) {
  if (f.type === 'tabella') return <CampoTabella f={f} value={value} onChange={onChange} disabled={disabled} errore={errore} />
  if (f.type === 'organizzazione') return <CampoOrganizzazione f={f} value={value} onChange={onChange} disabled={disabled} errore={errore} nomeIniziale={nomeIniziale} />
  const id = `campo-${f.name}`
  const largo = f.type === 'textarea'
  const idErrore = `${id}-errore`
  if (errore) errore = errore[0].toUpperCase() + errore.slice(1)
  const messaggio = errore && <p id={idErrore} role="alert" className="mt-1 text-[13px] font-medium text-mattone-600">{errore}</p>
  const accessibilita = { 'aria-invalid': errore ? true : undefined, 'aria-describedby': errore ? idErrore : undefined }
  const limiti = f.type === 'number' || f.type === 'date'
    ? { min: f.min === 'oggi' ? new Date().toLocaleDateString('sv-SE') : f.min, max: f.max === 'oggi' ? new Date().toLocaleDateString('sv-SE') : f.max }
    : { maxLength: f.maxLunghezza || undefined }
  const etichetta = (
    <label htmlFor={id} className="etichetta">
      {f.label || f.name}{f.required && <span className="text-mattone-600"> *</span>}
    </label>
  )
  const aiuto = f.help && <p className="mt-1 text-[13px] text-grafite">{f.help}</p>

  if (f.type === 'checkbox') {
    return (
      <label htmlFor={id} className="flex cursor-pointer items-start gap-3 rounded-campo border border-linea bg-white px-3.5 py-3 sm:col-span-2 has-[:checked]:border-verde-500 has-[:checked]:bg-verde-50">
        <input id={id} type="checkbox" checked={!!value} disabled={disabled} required={f.required} {...accessibilita}
               onChange={e => onChange(e.target.checked)} className="mt-0.5 size-4 accent-verde-600" />
        <span>
          <span className="text-[15px] font-medium text-pino-900">{f.label || f.name}</span>
          {f.help && <span className="block text-[13px] text-grafite">{f.help}</span>}
          {errore && <span id={idErrore} role="alert" className="block text-[13px] font-medium text-mattone-600">{errore}</span>}
        </span>
      </label>
    )
  }

  if (f.type === 'select' && (f.options || []).length <= 4) {
    // poche opzioni: pulsanti affiancati, una scelta a colpo d'occhio
    return (
      <fieldset className="sm:col-span-2">
        <legend className="etichetta">{f.label || f.name}{f.required && <span className="text-mattone-600"> *</span>}</legend>
        <div className="flex flex-wrap gap-2">
          {f.options.map(o => (
            <label key={o} className={cx('cursor-pointer rounded-campo border px-4 py-2.5 text-[15px] font-medium transition-colors',
              value === o ? 'border-verde-600 bg-verde-600 text-white' : 'border-linea bg-white text-pino-900 hover:border-pino-200',
              disabled && 'pointer-events-none opacity-60')}>
              <input type="radio" name={f.name} value={o} checked={value === o} required={f.required} disabled={disabled}
                     onChange={() => onChange(o)} className="sr-only" />
              {o}
            </label>
          ))}
        </div>
        {messaggio}
        {aiuto}
      </fieldset>
    )
  }

  return (
    <div className={cx(largo && 'sm:col-span-2')}>
      {etichetta}
      {f.type === 'textarea' ? (
        <textarea id={id} rows={4} className={cx('campo resize-y', errore && 'border-mattone-600!')} value={value ?? ''} required={f.required} disabled={disabled}
                  placeholder={f.placeholder} onChange={e => onChange(e.target.value)} {...limiti} {...accessibilita} />
      ) : f.type === 'select' ? (
        <select id={id} className="campo" value={value ?? ''} required={f.required} disabled={disabled}
                onChange={e => onChange(e.target.value)}>
          <option value="">Scegli…</option>
          {(f.options || []).map(o => <option key={o} value={o}>{o}</option>)}
        </select>
      ) : (
        <input id={id} className={cx('campo', errore && 'border-mattone-600!')}
               type={f.type === 'number' ? 'number' : f.type === 'date' ? 'date' : f.formato === 'email' ? 'email' : 'text'}
               inputMode={['cap', 'partitaIva'].includes(f.formato) ? 'numeric' : f.formato === 'telefono' ? 'tel' : undefined}
               step={f.type === 'number' ? 'any' : undefined}
               value={value ?? ''} required={f.required} disabled={disabled} placeholder={f.placeholder}
               onChange={e => onChange(e.target.value)} {...limiti} {...accessibilita} />
      )}
      {messaggio}
      {aiuto}
    </div>
  )
}

/**
 * Scelta di un'organizzazione esterna dall'anagrafica: si cerca per nome o codice fiscale e si sceglie dall'elenco.
 * Il valore inviato è l'id; il server controlla che l'organizzazione esista e sia attiva.
 */
function CampoOrganizzazione({ f, value, onChange, disabled, errore, nomeIniziale }) {
  const uid = useId()
  const id = `campo-${f.name}`
  const [cerca, setCerca] = useState('')
  const [risultati, setRisultati] = useState([])
  const [problema, setProblema] = useState('')
  const [scelta, setScelta] = useState(value ? { id: value, nome: nomeIniziale || `Organizzazione ${value}` } : null)

  useEffect(() => {
    const t = setTimeout(() => {
      api.cercaOrganizzazioni(cerca).then(r => { setRisultati(r); setProblema('') }).catch(e => setProblema(e.message))
    }, 250)
    return () => clearTimeout(t)
  }, [cerca])

  const opzioni = scelta && !risultati.some(o => String(o.id) === String(scelta.id)) ? [scelta, ...risultati] : risultati
  return (
    <fieldset className="sm:col-span-2">
      <legend className="etichetta">{f.label || f.name}{f.required && <span className="text-mattone-600"> *</span>}</legend>
      <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <label className="block">
          <span className="sr-only">Cerca {f.label || f.name}</span>
          <input type="search" className="campo" placeholder="Cerca per nome o codice fiscale" value={cerca} disabled={disabled}
                 onChange={e => setCerca(e.target.value)} aria-controls={id} />
        </label>
        <label className="block">
          <span className="sr-only">Scegli {f.label || f.name}</span>
          <select id={id} className={cx('campo', errore && 'border-mattone-600!')} value={value ?? ''} disabled={disabled}
                  aria-invalid={errore ? true : undefined} aria-describedby={errore ? `${uid}-e` : undefined}
                  onChange={e => {
                    const o = opzioni.find(x => String(x.id) === e.target.value)
                    setScelta(o || null)
                    onChange(o ? String(o.id) : '')
                  }}>
            <option value="">{opzioni.length ? 'Scegli…' : 'Nessuna organizzazione trovata'}</option>
            {opzioni.map(o => <option key={o.id} value={String(o.id)}>{o.nome}{o.codiceFiscale ? ` (${o.codiceFiscale})` : ''}</option>)}
          </select>
        </label>
      </div>
      {errore && <p id={`${uid}-e`} role="alert" className="mt-1 text-[13px] font-medium text-mattone-600">{errore[0].toUpperCase() + errore.slice(1)}</p>}
      {problema && <p className="mt-1 text-[13px] text-mattone-600">{problema}</p>}
      {f.help && <p className="mt-1 text-[13px] text-grafite">{f.help}</p>}
    </fieldset>
  )
}

/** Area per trascinare o scegliere i documenti da allegare all'avvio. */
function Allegati({ files, setFiles, obbligatori, disabled }) {
  const input = useRef(null)
  const [sopra, setSopra] = useState(false)
  const aggiungi = (lista) => {
    const nuovi = Array.from(lista)
    setFiles(prev => [...prev, ...nuovi.filter(n => !prev.some(p => p.name === n.name && p.size === n.size))])
  }
  return (
    <div>
      <p className="etichetta">Documenti{obbligatori && <span className="text-mattone-600"> *</span>}</p>
      <div
        onDragOver={e => { e.preventDefault(); setSopra(true) }}
        onDragLeave={() => setSopra(false)}
        onDrop={e => { e.preventDefault(); setSopra(false); aggiungi(e.dataTransfer.files) }}
        onClick={() => !disabled && input.current?.click()}
        role="button" tabIndex={disabled ? -1 : 0} aria-disabled={disabled || undefined}
        onKeyDown={e => { if (!disabled && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); input.current?.click() } }}
        className={cx('flex cursor-pointer flex-col items-center rounded-pannello border-2 border-dashed px-6 py-7 text-center transition-colors',
          sopra ? 'border-verde-500 bg-verde-50' : 'border-linea bg-carta/60 hover:border-pino-200')}>
        <FileUp className="size-6 text-pino-600" />
        <p className="mt-2 text-[15px] font-medium text-pino-900">Trascina qui i documenti o scegli dal computer</p>
        <p className="mt-0.5 text-[13px] text-grafite">PDF firmati PAdES, buste .p7m CAdES e altri formati. Le firme vengono verificate al caricamento.</p>
        <input ref={input} type="file" multiple className="hidden" onChange={e => { aggiungi(e.target.files); e.target.value = '' }} />
      </div>
      {files.length > 0 && (
        <ul className="mt-3 divide-y divide-linea rounded-campo border border-linea bg-white">
          {files.map((f, i) => (
            <li key={i} className="flex items-center gap-3 px-3 py-2 text-[15px]">
              <Paperclip className="size-4 text-grafite" />
              <span className="min-w-0 flex-1 truncate">{f.name}</span>
              <span className="numeri text-[13px] text-grafite">{dimensione(f.size)}</span>
              <button type="button" onClick={() => setFiles(files.filter((_, j) => j !== i))}
                      className="rounded p-1 text-grafite hover:bg-carta hover:text-mattone-600" aria-label={`Togli ${f.name}`}>
                <X className="size-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
