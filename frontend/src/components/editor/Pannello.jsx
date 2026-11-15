import { is } from 'bpmn-js/lib/util/ModelUtil'
import { useEffect, useState } from 'react'
import {
  ArrowRight, BellRing, Bot, Boxes, Copy, Mail, ChevronRight, CircleDot, GitBranch, Hourglass, Info, KeyRound, Pencil, Plus,
  Square, Timer, Trash, TriangleAlert, UserRound, Workflow, X
} from 'lucide-react'
import { cx } from '../../lib/format.js'
import { Badge } from '../ui.jsx'
import { Etichetta, InserisciDato, SceltaGruppi, Sezione, TestoDifferito } from './campi.jsx'
import { componi, descrivi, famiglia, OPERATORI, scomponi } from './condizioni.js'
import { CALENDARI, componiTimer, descriviTimer, scomponiTimer, tipoBordo, UNITA } from './timer.js'
import { api } from '../../api.js'
import { aggiungiBordo, bordiDi, puoFallire } from './bordo.js'
import { chiaveDaNome } from '../../lib/bpmn/orditogramModeler.js'

/**
 * Pannello guidato dell'editor: mostra e modifica le proprietà dell'elemento selezionato
 * (o del processo, se non è selezionato nulla) scrivendo gli attributi activiti: nel BPMN.
 */
export default function Pannello({ modeler, elemento, dati, onForm, onSeleziona }) {
  if (!modeler || !elemento) return null
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const bo = elemento.businessObject
  const radice = elemento === modeler.get('canvas').getRootElement()

  const set = (props) => radice
    ? modeling.updateModdleProperties(elemento, bo, props)
    : modeling.updateProperties(elemento, props)
  const ctx = { modeling, moddle, elemento, bo, set, dati, onForm, onSeleziona, modeler }

  let Contenuto = Generico
  let titolo = 'Elemento', Icona = Square
  if (radice) { Contenuto = Processo; titolo = 'Processo'; Icona = Workflow }
  else if (is(elemento, 'bpmn:StartEvent')) { Contenuto = Inizio; titolo = 'Inizio'; Icona = CircleDot }
  else if (is(elemento, 'bpmn:EndEvent')) { Contenuto = Fine; titolo = 'Fine'; Icona = CircleDot }
  else if (is(elemento, 'bpmn:UserTask')) { Contenuto = AttivitaUtente; titolo = 'Attività di una persona'; Icona = UserRound }
  else if (is(elemento, 'bpmn:ServiceTask')) { Contenuto = AttivitaAutomatica; titolo = 'Attività automatica'; Icona = Bot }
  else if (is(elemento, 'bpmn:ExclusiveGateway')) { Contenuto = Decisione; titolo = 'Decisione'; Icona = GitBranch }
  else if (is(elemento, 'bpmn:ParallelGateway')) { Contenuto = Parallelo; titolo = 'Attività in parallelo'; Icona = GitBranch }
  else if (is(elemento, 'bpmn:BoundaryEvent')) {
    const t = tipoBordo(bo)
    Contenuto = Bordo
    ;[titolo, Icona] = {
      scadenza: ['Scadenza', Timer], sollecito: ['Sollecito', BellRing], errore: ['Se non riesce', TriangleAlert],
      messaggio: ['Messaggio sul bordo', Mail]
    }[t] || ['Evento sul bordo', CircleDot]
  }
  else if (is(elemento, 'bpmn:IntermediateCatchEvent') && tipoBordo(bo) === 'messaggio') { Contenuto = MessaggioInArrivo; titolo = 'Messaggio in arrivo'; Icona = Mail }
  else if (is(elemento, 'bpmn:IntermediateCatchEvent')) { Contenuto = Attesa; titolo = 'Attesa'; Icona = Hourglass }
  else if (is(elemento, 'bpmn:SubProcess')) { Contenuto = Sottoprocesso; titolo = 'Sottoprocesso'; Icona = Boxes }
  else if (is(elemento, 'bpmn:SequenceFlow')) { Contenuto = Freccia; titolo = 'Freccia'; Icona = ArrowRight }
  else if (is(elemento, 'bpmn:TextAnnotation')) { Contenuto = Nota; titolo = 'Nota'; Icona = Pencil }
  else if (is(elemento, 'bpmn:Task')) { Contenuto = TaskGenerico; titolo = 'Attività'; Icona = Square }

  return (
    <div>
      <header className="flex items-center gap-3 border-b border-linea px-5 py-4">
        <span className="inline-flex size-9 items-center justify-center rounded-campo bg-pino-900 text-white"><Icona className="size-[18px]" /></span>
        <div className="min-w-0">
          <p className="text-[13px] text-grafite">{titolo}</p>
          <p className="truncate font-display text-lg font-semibold leading-tight">{bo.name || (radice ? bo.id : 'Senza nome')}</p>
        </div>
        {!radice && (
          <button onClick={() => onSeleziona(null)} className="ml-auto text-[13px] font-medium text-verde-700 hover:underline">Processo</button>
        )}
      </header>
      <Contenuto key={elemento.id} {...ctx} />
    </div>
  )
}

// ================================================================= tipi di elemento

function Nome({ bo, set, etichetta = 'Nome', placeholder, aiuto }) {
  return (
    <div>
      <Etichetta htmlFor="nome">{etichetta}</Etichetta>
      <TestoDifferito id="nome" value={bo.name} placeholder={placeholder} onCommit={v => set({ name: v || undefined })} />
      {aiuto && <p className="mt-1 text-[13px] text-grafite">{aiuto}</p>}
    </div>
  )
}

function Processo({ bo, set, moddle, dati }) {
  const descrizione = bo.documentation?.[0]?.text || ''
  const avvio = (bo.candidateStarterGroups || '').split(',').map(s => s.trim()).filter(Boolean)
  return (
    <>
      <Sezione>
        <Nome bo={bo} set={set} etichetta="Nome del processo" aiuto="È il nome che gli utenti vedono quando avviano una pratica." />
        <div>
          <Etichetta>Identificativo</Etichetta>
          <p className="rounded-campo bg-carta px-3 py-2 font-mono text-sm text-pino-800">{bo.id}</p>
          <p className="mt-1 text-[13px] text-grafite">Non si cambia: le versioni successive restano collegate a questo identificativo.</p>
        </div>
        <div>
          <Etichetta htmlFor="descr">Descrizione</Etichetta>
          <TestoDifferito id="descr" multiline value={descrizione} placeholder="A cosa serve questo processo"
                          onCommit={v => set({ documentation: v ? [moddle.create('bpmn:Documentation', { text: v })] : [] })} />
        </div>
      </Sezione>
      <Sezione titolo="Chi può avviarlo" aiuto="Se non scegli nessun gruppo, tutti gli utenti possono avviare questo processo.">
        <SceltaGruppi valori={avvio} gruppi={dati.gruppi} vuoto="Tutti gli utenti"
                      onChange={g => set({ candidateStarterGroups: g.length ? g.join(',') : undefined })} />
      </Sezione>
      <Sezione titolo="Come si disegna">
        <ul className="space-y-1.5 text-[13px] text-grafite">
          <li>Trascina gli elementi dalla barra a sinistra, oppure seleziona un elemento e usa le icone accanto per aggiungere il passo successivo.</li>
          <li>Seleziona un elemento per configurarlo qui: chi lo lavora, quale form, cosa fa.</li>
          <li>Doppio clic su un elemento per cambiarne il nome direttamente nel diagramma.</li>
        </ul>
      </Sezione>
    </>
  )
}

function SceltaForm({ bo, set, dati, onForm, tipo }) {
  const formKey = bo.formKey || ''
  const esiste = dati.forms.includes(formKey)
  const suggerimento = `${dati.chiave}-${(bo.name || tipo).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}`
  return (
    <div className="space-y-2">
      <select className="campo" value={esiste || !formKey ? formKey : '__mancante'} onChange={e => set({ formKey: e.target.value || undefined })}>
        <option value="">Nessun form</option>
        {!esiste && formKey && <option value="__mancante">{formKey} (non esiste ancora)</option>}
        {gruppiForm(dati).map(g => (
          <optgroup key={g.nome} label={g.nome}>
            {g.forms.map(f => <option key={f.formKey} value={f.formKey}>{f.titolo ? `${f.titolo} (${f.formKey})` : f.formKey}</option>)}
          </optgroup>
        ))}
      </select>
      <div className="flex flex-wrap gap-2">
        {formKey && (
          <button type="button" onClick={() => onForm({ chiave: formKey, nuovo: !esiste, applica: k => set({ formKey: k }) })}
                  className="inline-flex h-8 items-center gap-1.5 rounded-campo border border-linea bg-white px-3 text-sm font-medium hover:bg-carta">
            <Pencil className="size-3.5" /> {esiste ? 'Modifica il form' : 'Crea questo form'}
          </button>
        )}
        <button type="button" onClick={() => onForm({ chiave: null, nuovo: true, suggerimento, applica: k => set({ formKey: k }) })}
                className="inline-flex h-8 items-center gap-1.5 rounded-campo border border-linea bg-white px-3 text-sm font-medium hover:bg-carta">
          <Plus className="size-3.5" /> Nuovo form
        </button>
      </div>
    </div>
  )
}

/** Form per la scelta: prima quelli di questo processo, poi per tipologia di pratica, poi i non usati. */
function gruppiForm(dati) {
  const info = dati.formsInfo || dati.forms.map(formKey => ({ formKey }))
  const gruppi = new Map([['__questo', { nome: 'Usati in questo processo', forms: [] }]])
  const altri = { nome: 'Non usati in nessun processo', forms: [] }
  for (const f of info) {
    const uso = f.uso || []
    if (uso.some(u => u.chiave === dati.chiave)) { gruppi.get('__questo').forms.push(f); continue }
    if (!uso.length) { altri.forms.push(f); continue }
    const u = uso[0]
    if (!gruppi.has(u.chiave)) gruppi.set(u.chiave, { nome: u.nome, forms: [] })
    gruppi.get(u.chiave).forms.push(f)
  }
  const ordinati = [...gruppi.entries()].filter(([k]) => k !== '__questo').map(([, g]) => g).sort((a, b) => a.nome.localeCompare(b.nome, 'it'))
  return [gruppi.get('__questo'), ...ordinati, altri].filter(g => g.forms.length)
}

function Inizio(ctx) {
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Nuova richiesta" /></Sezione>
      <Sezione titolo="Form di avvio" aiuto="I dati che chiede chi apre la pratica. Può anche chiedere di allegare documenti.">
        <SceltaForm {...ctx} tipo="avvio" />
      </Sezione>
    </>
  )
}

function Fine(ctx) {
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Pratica accettata" aiuto="Il nome aiuta a leggere il diagramma: è l'esito con cui si chiude la pratica." /></Sezione>
      <Sezione><Suggerimento>Quando la pratica arriva qui è conclusa e compare tra le pratiche concluse.</Suggerimento></Sezione>
    </>
  )
}

function AttivitaUtente(ctx) {
  const { bo, set, dati } = ctx
  const gruppi = (bo.candidateGroups || '').split(',').map(s => s.trim()).filter(Boolean)
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Verifica pratica" aiuto="Usa un verbo: è il nome che l'utente vede nella sua scrivania." /></Sezione>
      <Sezione titolo="Chi la lavora" aiuto="L'attività compare a tutti gli utenti di questi gruppi: il primo che la prende in carico la lavora.">
        <SceltaGruppi valori={gruppi} gruppi={dati.gruppi} onChange={g => set({ candidateGroups: g.length ? g.join(',') : undefined })} />
      </Sezione>
      <Sezione titolo="Form" aiuto="Cosa vede e cosa compila l'utente. Dal form decidi anche se può consultare o modificare i documenti.">
        <SceltaForm {...ctx} tipo="attivita" />
      </Sezione>
      <Scadenze {...ctx} />
    </>
  )
}

function AttivitaAutomatica(ctx) {
  const { bo, set, dati, moddle, modeling, elemento } = ctx
  const impl = bo.implementation && bo.implementation !== '##WebService' ? bo.implementation : ''
  const connettore = dati.connettori.find(c => c.id === impl)
  const valori = leggiCampi(bo)

  const scriviCampi = (nuovi) => {
    const altri = (bo.extensionElements?.values || []).filter(v => v.$type !== 'activiti:Field')
    const campi = Object.entries(nuovi).filter(([, v]) => v !== '' && v != null)
      .map(([name, stringValue]) => moddle.create('activiti:Field', { name, stringValue }))
    const valoriExt = [...altri, ...campi]
    modeling.updateProperties(elemento, {
      extensionElements: valoriExt.length ? moddle.create('bpmn:ExtensionElements', { values: valoriExt }) : undefined
    })
  }

  const scegli = (c) => {
    const predefiniti = {}
    c.parametri.forEach(p => { if (p.predefinito) predefiniti[p.nome] = p.predefinito })
    modeling.updateProperties(elemento, { implementation: c.id })
    scriviCampi(predefiniti)
  }

  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Notifica al dirigente" /></Sezione>
      <Sezione titolo="Cosa fa" aiuto="Orditogram la esegue da solo, senza che nessuno debba intervenire.">
        <div className="space-y-2">
          {dati.connettori.map(c => (
            <button key={c.id} type="button" onClick={() => c.id !== impl && scegli(c)}
                    className={cx('flex w-full items-start gap-3 rounded-campo border px-3 py-2.5 text-left transition-colors',
                      c.id === impl ? 'border-verde-600 bg-verde-50' : 'border-linea bg-white hover:border-pino-200')}>
              <Bot className={cx('mt-0.5 size-4 shrink-0', c.id === impl ? 'text-verde-700' : 'text-grafite')} />
              <span>
                <span className="block text-[15px] font-medium text-pino-900">{c.nome}</span>
                <span className="block text-[13px] text-grafite">{c.descrizione}</span>
              </span>
            </button>
          ))}
          {impl && !connettore && <Suggerimento tono="avviso">Il connettore "{impl}" non è nel catalogo di questa installazione.</Suggerimento>}
        </div>
      </Sezione>
      {connettore && connettore.parametri.length > 0 && (
        <Sezione titolo="Impostazioni">
          {connettore.parametri.map(p => (
            <Parametro key={p.nome} p={p} valore={valori[p.nome] ?? ''} dati={dati}
                       onChange={v => scriviCampi({ ...valori, [p.nome]: v })} />
          ))}
        </Sezione>
      )}
      <Scadenze {...ctx} />
    </>
  )
}

function Parametro({ p, valore, dati, onChange }) {
  const id = `par-${p.nome}`
  const etichetta = <Etichetta htmlFor={id}>{p.etichetta}{p.obbligatorio && !p.predefinito && <span className="text-mattone-600"> *</span>}</Etichetta>
  const aiuto = p.aiuto && <p className="mt-1 text-[13px] text-grafite">{p.aiuto}</p>
  if (p.tipo === 'scelta') {
    const v = valore || p.predefinito || ''
    return (
      <div>
        {etichetta}
        {p.opzioni.length <= 5 && p.opzioni.every(o => o.length <= 8)
          ? (
            <div className="inline-flex flex-wrap rounded-campo bg-carta p-1" role="radiogroup" aria-label={p.etichetta}>
              {p.opzioni.map(o => (
                <button key={o} type="button" role="radio" aria-checked={v === o} onClick={() => onChange(o)}
                        className={cx('rounded-md px-3 py-1 font-mono text-[13px] font-medium',
                          v === o ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite hover:text-pino-900')}>
                  {o}
                </button>
              ))}
            </div>
          )
          : (
            <div className="space-y-1.5" role="radiogroup" aria-label={p.etichetta}>
              {p.opzioni.map(o => (
                <label key={o} className="flex items-center gap-2 text-[15px]">
                  <input type="radio" name={id} className="accent-verde-600" checked={v === o} onChange={() => onChange(o)} /> {o}
                </label>
              ))}
            </div>
          )}
        {aiuto}
      </div>
    )
  }
  if (p.tipo === 'credenziale') {
    const note = dati.credenziali || []
    const sconosciuta = valore && !note.some(c => c.nome === valore)
    return (
      <div>
        {etichetta}
        <div className="relative">
          <KeyRound className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-grafite" />
          <select id={id} className="campo pl-9" value={valore} onChange={e => onChange(e.target.value)}>
            <option value="">Nessuna (servizio aperto)</option>
            {sconosciuta && <option value={valore}>{valore} (non configurata su questo server)</option>}
            {note.map(c => <option key={c.nome} value={c.nome}>{c.nome}{c.descrizione ? ` · ${c.descrizione}` : ''}</option>)}
          </select>
        </div>
        {note.length === 0 && <p className="mt-1 text-[13px] text-grafite">Nessuna credenziale configurata: l'amministratore le aggiunge in application.yml (app.credenziali).</p>}
        {aiuto}
      </div>
    )
  }
  if (p.tipo === 'codice') {
    return (
      <div>
        {etichetta}
        <TestoDifferito id={id} value={valore} multiline mono rows={4} placeholder={p.aiuto} onCommit={onChange} />
        <div className="mt-1"><InserisciDato campi={dati.campi} onInserisci={t => onChange((valore || '') + t)} /></div>
      </div>
    )
  }
  if (p.tipo === 'mappatura') return <div>{etichetta}<EditorMappatura valore={valore} onChange={onChange} />{aiuto}</div>
  if (p.tipo === 'destinatariEmail') return <div>{etichetta}<EditorDestinatari valore={valore} dati={dati} onChange={onChange} /></div>
  if (p.tipo === 'destinatario') {
    const modo = valore === 'iniziatore' || !valore ? 'iniziatore' : valore.startsWith('gruppo:') ? 'gruppo' : 'utente'
    return (
      <div>
        <Etichetta>{p.etichetta}{p.obbligatorio && <span className="text-mattone-600"> *</span>}</Etichetta>
        <div className="space-y-2">
          {[['iniziatore', 'Chi ha avviato la pratica'], ['gruppo', 'Tutti gli utenti di un gruppo'], ['utente', 'Un utente preciso']].map(([k, l]) => (
            <label key={k} className="flex items-center gap-2 text-[15px]">
              <input type="radio" name={id} className="accent-verde-600" checked={modo === k}
                     onChange={() => onChange(k === 'iniziatore' ? 'iniziatore' : k === 'gruppo' ? `gruppo:${dati.gruppi[0]?.nome || ''}` : '')} />
              {l}
            </label>
          ))}
          {modo === 'gruppo' && (
            <select className="campo" value={valore.slice(7)} onChange={e => onChange(`gruppo:${e.target.value}`)}>
              {dati.gruppi.map(g => <option key={g.nome} value={g.nome}>{g.nome}</option>)}
            </select>
          )}
          {modo === 'utente' && <TestoDifferito value={valore} placeholder="Nome utente, es. laura" onCommit={onChange} />}
        </div>
      </div>
    )
  }
  return (
    <div>
      {etichetta}
      <TestoDifferito id={id} value={valore} multiline={p.tipo === 'testoLungo'} placeholder={p.aiuto} onCommit={onChange} />
      <div className="mt-1"><InserisciDato campi={dati.campi} onInserisci={t => onChange((valore ? valore + ' ' : '') + t)} /></div>
    </div>
  )
}

// ================================================================= editor dei parametri speciali

const NOME_DATO = /^[a-zA-Z][\w-]*$/

/** Righe "datoPratica = percorso.nella.risposta". */
function EditorMappatura({ valore, onChange }) {
  const leggi = (v) => (v || '').split('\n').map(r => r.trim()).filter(Boolean).map(r => {
    const i = r.indexOf('=')
    return i < 0 ? { dato: r, percorso: '' } : { dato: r.slice(0, i).trim(), percorso: r.slice(i + 1).trim() }
  })
  const [righe, setRighe] = useState(() => leggi(valore))
  useEffect(() => { setRighe(leggi(valore)) }, [valore])
  const scrivi = (rr) => {
    const testo = rr.filter(r => r.dato && r.percorso && NOME_DATO.test(r.dato)).map(r => `${r.dato} = ${r.percorso}`).join('\n')
    if (testo !== (valore || '')) onChange(testo)
  }
  const cambia = (i, patch) => setRighe(rr => rr.map((r, j) => j === i ? { ...r, ...patch } : r))
  return (
    <div className="space-y-2">
      {righe.length > 0 && (
        <div className="grid grid-cols-[1fr_auto_1fr_auto] items-center gap-x-1.5 gap-y-1.5 text-[13px] text-grafite">
          <span>Dato della pratica</span><span /><span>Dalla risposta</span><span />
          {righe.map((r, i) => (
            <FragmentoRiga key={i} r={r} onDato={v => cambia(i, { dato: v })} onPercorso={v => cambia(i, { percorso: v })}
                           onBlur={() => scrivi(righe)}
                           onTogli={() => { const rr = righe.filter((_, j) => j !== i); setRighe(rr); scrivi(rr) }} />
          ))}
        </div>
      )}
      <button type="button" onClick={() => setRighe([...righe, { dato: '', percorso: '' }])}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-verde-700 hover:underline">
        <Plus className="size-3.5" /> Salva un valore della risposta
      </button>
    </div>
  )
}

function FragmentoRiga({ r, onDato, onPercorso, onBlur, onTogli }) {
  const errato = r.dato && !NOME_DATO.test(r.dato)
  return (
    <>
      <input className={cx('campo h-9 font-mono text-sm', errato && 'border-mattone-600')} value={r.dato} placeholder="numeroProtocollo"
             aria-label="Dato della pratica" onChange={e => onDato(e.target.value)} onBlur={onBlur} />
      <span className="text-grafite">=</span>
      <input className="campo h-9 font-mono text-sm" value={r.percorso} placeholder="numero" aria-label="Percorso nella risposta"
             onChange={e => onPercorso(e.target.value)} onBlur={onBlur} />
      <button type="button" onClick={onTogli} aria-label="Togli" className="rounded-md p-1 text-grafite hover:bg-carta"><X className="size-4" /></button>
    </>
  )
}

const TIPI_DESTINATARIO = [
  ['iniziatore', 'Chi ha avviato la pratica'],
  ['gruppo', 'Gli utenti di un gruppo'],
  ['utente', 'Un utente di Orditogram'],
  ['campo', 'L\'indirizzo scritto in un campo del form'],
  ['email', 'Un indirizzo fisso']
]

function descriviDestinatario(t, campi) {
  if (t === 'iniziatore') return 'Chi ha avviato la pratica'
  const [k, ...resto] = t.split(':')
  const v = resto.join(':')
  if (k === 'gruppo') return `Gruppo ${v}`
  if (k === 'utente') return `Utente ${v}`
  if (k === 'campo') return `Campo "${campi.find(c => c.name === v)?.label || v}"`
  if (k === 'email') return v
  return t
}

/** Elenco di destinatari email: iniziatore, gruppo:x, utente:x, campo:x, email:indirizzo. */
function EditorDestinatari({ valore, dati, onChange }) {
  const lista = (valore || '').split(',').map(s => s.trim()).filter(Boolean)
  const [tipo, setTipo] = useState('campo')
  const [v, setV] = useState('')
  const campiEmail = [...dati.campi].sort((a, b) => (/mail/i.test(b.name) ? 1 : 0) - (/mail/i.test(a.name) ? 1 : 0))
  const aggiungi = () => {
    const t = tipo === 'iniziatore' ? 'iniziatore' : `${tipo}:${v.trim()}`
    if (tipo !== 'iniziatore' && !v.trim()) return
    if (!lista.includes(t)) onChange([...lista, t].join(', '))
    setV('')
  }
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {lista.length === 0 && <span className="text-sm text-mattone-600">Nessun destinatario</span>}
        {lista.map(t => (
          <span key={t} className="inline-flex items-center gap-1 rounded-full bg-verde-50 py-1 pl-3 pr-1.5 text-sm text-verde-700 ring-1 ring-verde-100">
            {descriviDestinatario(t, dati.campi)}
            <button type="button" onClick={() => onChange(lista.filter(x => x !== t).join(', '))} className="rounded-full p-0.5 hover:bg-white" aria-label="Togli">
              <X className="size-3.5" />
            </button>
          </span>
        ))}
      </div>
      <div className="space-y-2 rounded-campo bg-carta p-3">
        <select className="campo" value={tipo} onChange={e => { setTipo(e.target.value); setV('') }} aria-label="Tipo di destinatario">
          {TIPI_DESTINATARIO.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <div className="flex gap-2">
          {tipo === 'gruppo' && (
            <select className="campo h-9" value={v} onChange={e => setV(e.target.value)} aria-label="Gruppo">
              <option value="">Scegli un gruppo…</option>
              {dati.gruppi.map(g => <option key={g.nome} value={g.nome}>{g.nome}</option>)}
            </select>
          )}
          {tipo === 'campo' && (
            <select className="campo h-9" value={v} onChange={e => setV(e.target.value)} aria-label="Campo">
              <option value="">Scegli un campo…</option>
              {campiEmail.map(c => <option key={c.name} value={c.name}>{c.label}</option>)}
            </select>
          )}
          {(tipo === 'utente' || tipo === 'email') && (
            <input className="campo h-9" value={v} onChange={e => setV(e.target.value)}
                   placeholder={tipo === 'utente' ? 'Nome utente, es. laura' : 'ufficio@ente.it'}
                   onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); aggiungi() } }} />
          )}
          <button type="button" onClick={aggiungi}
                  className="inline-flex h-9 shrink-0 items-center gap-1 rounded-campo border border-linea bg-white px-3 text-sm font-medium hover:bg-carta">
            <Plus className="size-4" /> Aggiungi
          </button>
        </div>
      </div>
    </div>
  )
}

// ================================================================= scadenze, solleciti, errori

const ICONE_BORDO = { scadenza: Timer, sollecito: BellRing, errore: TriangleAlert, messaggio: Mail }

/** Sezione delle attività e dei sottoprocessi: eventi agganciati al bordo e pulsanti per aggiungerli. */
function Scadenze({ modeler, elemento, dati, onSeleziona }) {
  const bordi = bordiDi(elemento)
  const errore = puoFallire(elemento)
  const haErrore = bordi.some(b => b.tipo === 'errore')
  const aggiungi = (tipo) => onSeleziona(aggiungiBordo(modeler, elemento, tipo))
  return (
    <Sezione titolo={errore ? 'Scadenze, solleciti ed errori' : 'Scadenze e solleciti'}
             aiuto="Una scadenza chiude l'attività quando il tempo finisce e porta la pratica su un'altra strada. Un sollecito avvisa senza chiuderla.">
      {bordi.length > 0 && (
        <ul className="space-y-2">
          {bordi.map(({ shape, tipo }) => {
            const Icona = ICONE_BORDO[tipo] || CircleDot
            const def = shape.businessObject.eventDefinitions?.[0]
            return (
              <li key={shape.id}>
                <button type="button" onClick={() => onSeleziona(shape)}
                        className="flex w-full items-center gap-3 rounded-campo border border-linea bg-white px-3 py-2.5 text-left hover:border-pino-200">
                  <Icona className={cx('size-4 shrink-0', tipo === 'errore' ? 'text-mattone-600' : tipo === 'sollecito' ? 'text-zafferano-500' : 'text-verde-700')} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium text-pino-900">{shape.businessObject.name || { scadenza: 'Scadenza', sollecito: 'Sollecito', errore: 'Se non riesce', messaggio: 'Messaggio' }[tipo] || 'Evento'}</span>
                    <span className="block text-[13px] text-grafite">
                      {tipo === 'errore' ? 'Strada da seguire se qualcosa non riesce'
                        : tipo === 'messaggio' ? `quando arriva "${def?.messageRef?.name || '…'}"`
                        : descriviTimer(def, dati.campi)}
                      {tipo === 'sollecito' && ', l\'attività resta aperta'}
                    </span>
                  </span>
                  <ChevronRight className="size-4 text-nebbia" />
                </button>
              </li>
            )
          })}
        </ul>
      )}
      <div className="flex flex-wrap gap-2">
        <BottoneBordo icona={Timer} onClick={() => aggiungi('scadenza')}>Scadenza</BottoneBordo>
        <BottoneBordo icona={BellRing} onClick={() => aggiungi('sollecito')}>Sollecito</BottoneBordo>
        {errore && !haErrore && <BottoneBordo icona={TriangleAlert} onClick={() => aggiungi('errore')}>Se non riesce</BottoneBordo>}
        <BottoneBordo icona={Mail} onClick={() => aggiungi('messaggio')}>Se arriva un messaggio</BottoneBordo>
      </div>
    </Sezione>
  )
}

function BottoneBordo({ icona: Icona, onClick, children }) {
  return (
    <button type="button" onClick={onClick}
            className="inline-flex h-8 items-center gap-1.5 rounded-campo border border-linea bg-white px-3 text-sm font-medium hover:bg-carta">
      <Plus className="size-3.5" /><Icona className="size-3.5 text-grafite" /> {children}
    </button>
  )
}

/** Evento agganciato al bordo: scadenza, sollecito o strada di errore. */
function Bordo(ctx) {
  const { elemento, bo, modeling, onSeleziona } = ctx
  const tipo = tipoBordo(bo)
  const host = elemento.host
  const def = bo.eventDefinitions?.[0]
  const senzaUscita = (elemento.outgoing || []).length === 0
  const interrompente = bo.cancelActivity !== false

  const cambiaInterruzione = (si) => {
    modeling.updateProperties(elemento, { cancelActivity: si })
    // una scadenza che chiude l'attività scatta una volta sola: il ciclo diventa una durata
    const t = scomponiTimer(def)
    if (si && t.modo === 'ripeti') scriviTimer(ctx, def, { modo: 'dopo', n: t.n, unita: t.unita })
    if (!bo.name || ['Termine', 'Sollecito', 'Scadenza'].includes(bo.name)) modeling.updateProperties(elemento, { name: si ? 'Termine' : 'Sollecito' })
  }

  return (
    <>
      <Sezione>
        <Nome {...ctx} placeholder={tipo === 'errore' ? 'Es. Protocollo non disponibile' : 'Es. Termine di 30 giorni'} />
        {host && (
          <p className="text-[13px] text-grafite">
            Sul bordo di <button type="button" onClick={() => onSeleziona(host)} className="font-medium text-verde-700 hover:underline">{host.businessObject.name || 'attività senza nome'}</button>
          </p>
        )}
      </Sezione>
      {def?.$type === 'bpmn:TimerEventDefinition' && (
        <>
          <Sezione titolo="Cosa succede quando scatta">
            <div className="space-y-2" role="radiogroup">
              <Opzione attiva={interrompente} onClick={() => cambiaInterruzione(true)} icona={Timer}
                       titolo="Chiude l'attività" testo="La pratica lascia l'attività e segue la freccia che parte da qui (es. silenzio-assenso, archiviazione)." />
              <Opzione attiva={!interrompente} onClick={() => cambiaInterruzione(false)} icona={BellRing}
                       titolo="Lascia l'attività aperta" testo="È un promemoria: la pratica segue anche la freccia da qui (es. un sollecito) ma l'attività resta da completare." />
            </div>
          </Sezione>
          <Sezione titolo="Quando scatta" aiuto="Il tempo parte quando la pratica arriva all'attività.">
            <EditorTimer key={elemento.id} {...ctx} def={def} ripetibile={!interrompente} />
          </Sezione>
        </>
      )}
      {tipo === 'messaggio' && (
        <>
          <Sezione titolo="Cosa succede quando arriva">
            <div className="space-y-2" role="radiogroup">
              <Opzione attiva={interrompente} onClick={() => modeling.updateProperties(elemento, { cancelActivity: true })} icona={Mail}
                       titolo="Chiude l'attività" testo="Es. il cittadino rinuncia: l'attività si chiude e la pratica segue la freccia che parte da qui." />
              <Opzione attiva={!interrompente} onClick={() => modeling.updateProperties(elemento, { cancelActivity: false })} icona={BellRing}
                       titolo="Lascia l'attività aperta" testo="Es. arriva un documento in più: si avvisa qualcuno, l'attività resta da completare." />
            </div>
          </Sezione>
          <EditorMessaggio {...ctx} def={def} />
        </>
      )}
      {tipo === 'errore' && (
        <Sezione>
          <Suggerimento>
            {host && is(host, 'bpmn:SubProcess')
              ? 'Se un\'attività automatica dentro il sottoprocesso non riesce (per esempio un servizio esterno non risponde), il sottoprocesso si interrompe e la pratica segue la freccia che parte da qui.'
              : 'Se l\'attività automatica non riesce (per esempio un servizio esterno non risponde o restituisce un errore), la pratica segue la freccia che parte da qui. Il motivo viene annotato nello storico.'}
          </Suggerimento>
        </Sezione>
      )}
      <Sezione>
        {senzaUscita && <Suggerimento tono="avviso">Collega questo evento al passo da eseguire: usa le icone accanto all'evento nel diagramma.</Suggerimento>}
        <button type="button" onClick={() => { const h = host; modeling.removeElements([elemento]); onSeleziona(h || null) }}
                className="inline-flex items-center gap-1.5 text-[13px] font-medium text-mattone-600 hover:underline">
          <Trash className="size-3.5" /> Togli dal bordo
        </button>
      </Sezione>
    </>
  )
}

function Opzione({ attiva, onClick, icona: Icona, titolo, testo }) {
  return (
    <button type="button" role="radio" aria-checked={attiva} onClick={onClick}
            className={cx('flex w-full items-start gap-3 rounded-campo border px-3 py-2.5 text-left transition-colors',
              attiva ? 'border-verde-600 bg-verde-50' : 'border-linea bg-white hover:border-pino-200')}>
      <Icona className={cx('mt-0.5 size-4 shrink-0', attiva ? 'text-verde-700' : 'text-grafite')} />
      <span>
        <span className="block text-[15px] font-medium text-pino-900">{titolo}</span>
        <span className="block text-[13px] text-grafite">{testo}</span>
      </span>
    </button>
  )
}

function scriviTimer({ modeling, moddle, elemento }, def, t) {
  const { tipo, body, calendario } = componiTimer(t)
  const fe = (b) => b ? moddle.create('bpmn:FormalExpression', { body: b }) : undefined
  modeling.updateModdleProperties(elemento, def, {
    businessCalendarName: calendario || undefined,
    timeDuration: tipo === 'timeDuration' ? fe(body) : undefined,
    timeDate: tipo === 'timeDate' ? fe(body) : undefined,
    timeCycle: tipo === 'timeCycle' ? fe(body) : undefined
  })
}

/** Tempo in parole: dopo N giorni, alla data di un campo, ripeti ogni N giorni. */
function EditorTimer(ctx) {
  const { def, dati, ripetibile } = ctx
  const t = scomponiTimer(def)
  const scrivi = (n) => scriviTimer(ctx, def, n)
  const base = { n: t.n || 1, unita: t.unita || 'giorni' }
  const date = dati.campi.filter(c => c.type === 'date')
  const campiData = date.length ? date : dati.campi
  const modi = [['dopo', 'Dopo un certo tempo'], ['data', 'Alla data scritta in un campo'], ...(ripetibile ? [['ripeti', 'Ogni tot tempo, più volte']] : [])]

  return (
    <div className="space-y-3">
      <select className="campo" aria-label="Tipo di tempo" value={t.modo === 'vuoto' ? '' : t.modo}
              onChange={e => {
                const m = e.target.value
                if (m === 'dopo') scrivi({ modo: 'dopo', ...base })
                if (m === 'ripeti') scrivi({ modo: 'ripeti', ...base, volte: 3 })
                if (m === 'data') scrivi({ modo: 'data', campo: date[0]?.name || '' })
                if (m === 'avanzato') scrivi({ modo: 'avanzato', tipo: 'timeDuration', testo: def?.timeDuration?.body || def?.timeDate?.body || def?.timeCycle?.body || 'P30D' })
              }}>
        {t.modo === 'vuoto' && <option value="">Scegli…</option>}
        {modi.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        <option value="avanzato">Espressione avanzata (ISO 8601)</option>
      </select>

      {(t.modo === 'dopo' || t.modo === 'ripeti') && (
        <div className="flex items-center gap-2">
          <span className="text-[15px] text-pino-900">{t.modo === 'dopo' ? 'Dopo' : 'Ogni'}</span>
          <NumeroDifferito value={t.n} onCommit={n => scrivi({ ...t, n })} label="Quantità" />
          <select className="campo h-9 w-auto" value={t.unita} aria-label="Unità" onChange={e => scrivi({ ...t, unita: e.target.value })}>
            {UNITA.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
        </div>
      )}
      {t.modo === 'dopo' && (
        <div>
          <label className="etichetta" htmlFor={`cal-${ctx.elemento.id}`}>Come si contano</label>
          <select id={`cal-${ctx.elemento.id}`} className="campo" value={t.calendario || ''} onChange={e => scrivi({ ...t, calendario: e.target.value })}>
            {Object.entries(CALENDARI).filter(([k]) => k !== 'lavorativi' || t.unita === 'giorni').map(([k, l]) => (
              <option key={k} value={k}>{l[0].toUpperCase() + l.slice(1)}</option>
            ))}
          </select>
          {t.unita === 'giorni' && <AnteprimaTermine giorni={t.n} modo={t.calendario || 'calendario'} />}
        </div>
      )}
      {t.modo === 'ripeti' && (
        <div className="flex items-center gap-2 text-[15px] text-pino-900">
          <span>Al massimo</span>
          <NumeroDifferito value={t.volte || ''} vuoto onCommit={v => scrivi({ ...t, volte: v || null })} label="Numero massimo di volte" />
          <span>volte</span>
          <span className="text-[13px] text-grafite">(vuoto: finché l'attività è aperta)</span>
        </div>
      )}
      {t.modo === 'data' && (
        <div>
          <select className="campo" value={t.campo} aria-label="Campo con la data" onChange={e => scrivi({ modo: 'data', campo: e.target.value })}>
            <option value="">Scegli un campo…</option>
            {campiData.map(c => <option key={c.name} value={c.name}>{c.label}</option>)}
          </select>
          {date.length === 0 && <p className="mt-1 text-[13px] text-zafferano-800">Nessun campo di tipo data nei form del processo: aggiungine uno per usare una scadenza a data fissa.</p>}
          <label className="mt-2 flex items-start gap-2 text-[13px] text-pino-900">
            <input type="checkbox" className="mt-0.5 size-4 accent-verde-600" checked={t.calendario === 'proroga'}
                   onChange={e => scrivi({ ...t, calendario: e.target.checked ? 'proroga' : '' })} />
            Se la data cade in un giorno non lavorativo, slitta al primo giorno lavorativo
          </label>
        </div>
      )}
      {t.modo === 'avanzato' && (
        <div className="space-y-2">
          <select className="campo h-9" value={t.tipo} aria-label="Tipo di espressione" onChange={e => scrivi({ ...t, tipo: e.target.value })}>
            <option value="timeDuration">Durata (es. P30D, PT4H)</option>
            <option value="timeDate">Data (es. 2026-12-31 o ${'{'}campo{'}'})</option>
            {ripetibile && <option value="timeCycle">Ciclo (es. R3/P5D)</option>}
          </select>
          <TestoDifferito mono value={t.testo} onCommit={v => scrivi({ ...t, testo: v })} />
        </div>
      )}
      {t.modo !== 'vuoto' && (
        <p className="rounded-campo bg-carta px-3 py-2 text-[13px] text-pino-800">Scatta <b className="font-medium">{descriviTimer(def, dati.campi)}</b>.</p>
      )}
    </div>
  )
}

/** "Se la pratica arrivasse oggi, scadrebbe il …": calcolato dal server con il calendario dell'ente. */
function AnteprimaTermine({ giorni, modo }) {
  const [data, setData] = useState(null)
  useEffect(() => {
    let vivo = true
    setData(null)
    api.anteprimaTermine(giorni, modo).then(r => vivo && setData(r.scadenza)).catch(() => {})
    return () => { vivo = false }
  }, [giorni, modo])
  if (!data) return null
  const testo = new Intl.DateTimeFormat('it-IT', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(data + 'T12:00:00'))
  return (
    <div className="mt-1 text-[13px] text-grafite">
      <p>Se la pratica arrivasse oggi, scadrebbe {testo}.</p>
      {modo !== 'calendario' && <Festivita />}
    </div>
  )
}

function Festivita() {
  const [aperto, setAperto] = useState(false)
  const [cal, setCal] = useState(null)
  useEffect(() => { if (aperto && !cal) api.calendario().then(setCal).catch(() => {}) }, [aperto, cal])
  const fmt = new Intl.DateTimeFormat('it-IT', { weekday: 'short', day: 'numeric', month: 'long' })
  return (
    <details className="mt-1" onToggle={e => setAperto(e.currentTarget.open)}>
      <summary className="cursor-pointer font-medium text-verde-700">Giorni non lavorativi dell'ente</summary>
      {cal && (
        <ul className="mt-1 space-y-0.5">
          <li>Ogni {cal.giorniNonLavorativi.map(g => ({ SATURDAY: 'sabato', SUNDAY: 'domenica' }[g] || g.toLowerCase())).join(' e ')}</li>
          {cal.festivita.map(f => <li key={f.data}>{fmt.format(new Date(f.data + 'T12:00:00'))}: {f.nome}</li>)}
          <li className="pt-1 text-nebbia">Le festività dell'ente si aggiungono in application.yml (app.calendario.festivita).</li>
        </ul>
      )}
    </details>
  )
}

function NumeroDifferito({ value, onCommit, label, vuoto }) {
  const [v, setV] = useState(String(value ?? ''))
  useEffect(() => { setV(String(value ?? '')) }, [value])
  const commit = () => {
    const n = parseInt(v, 10)
    if (vuoto && v.trim() === '') { if (value !== '') onCommit(null); return }
    if (!Number.isFinite(n) || n < 1) { setV(String(value ?? '')); return }
    if (n !== value) onCommit(n)
  }
  return (
    <input type="number" min="1" inputMode="numeric" aria-label={label} className="campo h-9 w-20" value={v}
           onChange={e => setV(e.target.value)} onBlur={commit}
           onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); commit() } }} />
  )
}

/** Nome del messaggio (anche indirizzo dell'API), dati che porta e come inviarlo. */
function EditorMessaggio({ modeler, modeling, moddle, elemento, bo, def }) {
  const nome = def?.messageRef?.name || def?.messageRef?.id || ''
  const datiExt = (bo.extensionElements?.values || []).find(v => v.$type === 'orditogram:DatiMessaggio')
  const datiTesto = datiExt?.body || ''
  const [copiato, setCopiato] = useState(false)

  const impostaNome = (v) => {
    const grezzo = (v || '').trim()
    // "pagamento ricevuto" → "pagamentoRicevuto"; un nome già valido resta com'è
    const n = /^[a-zA-Z][\w-]*$/.test(grezzo) ? grezzo : chiaveDaNome(grezzo)
    if (!n) { modeling.updateModdleProperties(elemento, def, { messageRef: undefined }); return }
    const defs = modeler.getDefinitions()
    let msg = (defs.rootElements || []).find(r => r.$type === 'bpmn:Message' && (r.name === n || r.id === n))
    if (!msg) {
      msg = moddle.create('bpmn:Message', { id: /^[a-zA-Z_]/.test(n) ? n : 'm_' + n, name: n })
      modeling.updateModdleProperties(elemento, defs, { rootElements: [...(defs.rootElements || []), msg] })
    }
    modeling.updateModdleProperties(elemento, def, { messageRef: msg })
  }
  const impostaDati = (v) => {
    const testo = (v || '').split(/[,\s]+/).filter(x => /^[a-zA-Z][\w-]*$/.test(x)).join(', ')
    const altri = (bo.extensionElements?.values || []).filter(x => x.$type !== 'orditogram:DatiMessaggio')
    const valori = testo ? [...altri, moddle.create('orditogram:DatiMessaggio', { body: testo })] : altri
    modeling.updateProperties(elemento, { extensionElements: valori.length ? moddle.create('bpmn:ExtensionElements', { values: valori }) : undefined })
  }

  const esempio = `POST ${window.location.origin}/api/messaggi/${nome || 'nomeMessaggio'}
X-API-Key: <chiave del sistema>
Content-Type: application/json

{
  "cerca": { "numeroProtocollo": "2026/000123" },
  "dati": { ${(datiTesto ? datiTesto.split(', ') : ['esempio']).map(d => `"${d}": "…"`).join(', ')} }
}`
  return (
    <>
      <Sezione titolo="Quale messaggio" aiuto="Un nome semplice, senza spazi: è anche l'indirizzo a cui un sistema esterno lo invia.">
        <div>
          <Etichetta htmlFor="msg-nome">Nome del messaggio</Etichetta>
          <TestoDifferito id="msg-nome" mono value={nome} placeholder="integrazioniRicevute" onCommit={impostaNome} />
        </div>
        <div>
          <Etichetta htmlFor="msg-dati">Dati che porta (facoltativo)</Etichetta>
          <TestoDifferito id="msg-dati" mono value={datiTesto} placeholder="esitoPagamento, notaCittadino" onCommit={impostaDati} />
          <p className="mt-1 text-[13px] text-grafite">Diventano dati della pratica: puoi usarli nei form, nei testi e nelle decisioni.</p>
        </div>
      </Sezione>
      <Sezione titolo="Come arriva">
        <p className="text-[13px] text-pino-800">Un operatore lo registra dalla scheda della pratica, oppure un sistema esterno (portale, PEC, pagamenti) lo invia così:</p>
        <div className="relative">
          <pre className="overflow-x-auto rounded-campo bg-pino-900 p-3 pt-10 font-mono text-[12px] leading-relaxed text-white">{esempio}</pre>
          <button type="button" onClick={() => { navigator.clipboard?.writeText(esempio); setCopiato(true); setTimeout(() => setCopiato(false), 1500) }}
                  className="absolute right-2 top-2 inline-flex items-center gap-1 rounded-md bg-white/10 px-2 py-1 text-[12px] text-white hover:bg-white/20">
            <Copy className="size-3.5" /> {copiato ? 'Copiato' : 'Copia'}
          </button>
        </div>
        <p className="text-[13px] text-grafite">La pratica si trova con "cerca" (uno o più suoi dati) o con "pratica" (il suo identificativo). Le chiavi dei sistemi si configurano in application.yml (app.messaggi.chiavi).</p>
      </Sezione>
    </>
  )
}

function MessaggioInArrivo(ctx) {
  const def = ctx.bo.eventDefinitions?.[0]
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Pagamento ricevuto" aiuto="La pratica si ferma qui finché il messaggio non arriva." /></Sezione>
      <EditorMessaggio {...ctx} def={def} />
      <Sezione><Suggerimento>Per non aspettare all'infinito, metti il messaggio dopo una decisione o aggiungi una scadenza all'attività precedente.</Suggerimento></Sezione>
    </>
  )
}

function Attesa(ctx) {
  const { bo } = ctx
  const def = bo.eventDefinitions?.[0]
  if (def?.$type !== 'bpmn:TimerEventDefinition') {
    return <><Sezione><Nome {...ctx} /></Sezione><Sezione><Suggerimento tono="avviso">Orditogram gestisce solo le attese a tempo. Cambia tipo con l'icona della chiave inglese.</Suggerimento></Sezione></>
  }
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Attesa di 10 giorni per le opposizioni" aiuto="La pratica resta ferma qui, poi prosegue da sola." /></Sezione>
      <Sezione titolo="Quanto aspetta"><EditorTimer key={ctx.elemento.id} {...ctx} def={def} ripetibile={false} /></Sezione>
      <Sezione><Suggerimento>Durante l'attesa la pratica è visibile tra le pratiche in corso, con la data in cui riprenderà.</Suggerimento></Sezione>
    </>
  )
}

function Sottoprocesso(ctx) {
  const { elemento } = ctx
  const figli = (elemento.children || []).filter(c => c.type !== 'label' && !c.waypoints)
  const avvii = figli.filter(c => is(c, 'bpmn:StartEvent')).length
  return (
    <>
      <Sezione><Nome {...ctx} placeholder="Es. Richiesta di integrazioni" aiuto="Raggruppa più passi: una scadenza o un errore sul bordo vale per tutti." /></Sezione>
      <Sezione>
        <Suggerimento>
          Disegna i passi dentro il riquadro, con un inizio e almeno una fine. Quando la pratica arriva alla fine del sottoprocesso
          prosegue sulla freccia in uscita dal riquadro.
        </Suggerimento>
        {avvii !== 1 && <Suggerimento tono="avviso">Il sottoprocesso deve contenere esattamente un inizio.</Suggerimento>}
        <p className="text-[13px] text-grafite">{figli.length} {figli.length === 1 ? 'elemento' : 'elementi'} all'interno.</p>
      </Sezione>
      <Scadenze {...ctx} />
    </>
  )
}

function leggiCampi(bo) {
  const out = {}
  ;(bo.extensionElements?.values || []).filter(v => v.$type === 'activiti:Field')
    .forEach(v => { out[v.name] = v.stringValue ?? v.expression ?? '' })
  return out
}

function Decisione(ctx) {
  const { elemento, bo, dati, onSeleziona } = ctx
  const uscite = elemento.outgoing || []
  return (
    <>
      <Sezione><Nome {...ctx} etichetta="Domanda" placeholder="Es. Esito della verifica?" aiuto="La domanda a cui risponde la decisione: rende il diagramma leggibile." /></Sezione>
      <Sezione titolo="Strade possibili" aiuto="Seleziona una freccia per impostarne la condizione. La pratica segue la prima condizione vera.">
        {uscite.length < 2 && <Suggerimento tono="avviso">Una decisione serve con almeno due frecce in uscita.</Suggerimento>}
        <ul className="space-y-2">
          {uscite.map(f => {
            const fbo = f.businessObject
            const predefinita = bo.default === fbo
            const cond = fbo.conditionExpression?.body
            return (
              <li key={f.id}>
                <button type="button" onClick={() => onSeleziona(f)}
                        className="flex w-full items-center gap-3 rounded-campo border border-linea bg-white px-3 py-2.5 text-left hover:border-pino-200">
                  <span className="min-w-0 flex-1">
                    <span className="block text-[15px] font-medium text-pino-900">
                      verso {f.target?.businessObject?.name || 'elemento senza nome'}
                    </span>
                    <span className={cx('block text-[13px]', predefinita || cond ? 'text-grafite' : 'text-mattone-600')}>
                      {predefinita ? 'Altrimenti (se nessuna condizione è vera)' : cond ? descrivi(cond, dati.campi) : 'Manca la condizione'}
                    </span>
                  </span>
                  {predefinita && <Badge>altrimenti</Badge>}
                  <ChevronRight className="size-4 text-nebbia" />
                </button>
              </li>
            )
          })}
        </ul>
      </Sezione>
    </>
  )
}

function Parallelo(ctx) {
  return (
    <>
      <Sezione><Nome {...ctx} /></Sezione>
      <Sezione>
        <Suggerimento>
          Con più frecce in uscita, tutte le attività collegate partono insieme. Usane un altro per riunirle:
          la pratica prosegue solo quando sono state completate tutte.
        </Suggerimento>
      </Sezione>
    </>
  )
}

function Freccia({ elemento, bo, set, modeling, moddle, dati, onSeleziona }) {
  const sorgente = elemento.source
  const daDecisione = sorgente && is(sorgente, 'bpmn:ExclusiveGateway')
  const predefinita = daDecisione && sorgente.businessObject.default === bo
  const espressione = bo.conditionExpression?.body || ''
  const parsed = scomponi(espressione)
  const avanzata = espressione && !parsed

  const scriviCondizione = (body, etichetta) => {
    const props = { conditionExpression: body ? moddle.create('bpmn:FormalExpression', { body }) : undefined }
    if (etichetta && !bo.name) props.name = etichetta
    modeling.updateProperties(elemento, props)
  }
  const rendiPredefinita = (si) => {
    if (si) {
      modeling.updateProperties(elemento, { conditionExpression: undefined, ...(!bo.name ? { name: 'Altrimenti' } : {}) })
      modeling.updateProperties(sorgente, { default: bo })
    } else {
      modeling.updateProperties(sorgente, { default: undefined })
    }
  }

  return (
    <>
      <Sezione>
        <Nome bo={bo} set={set} etichetta="Etichetta sulla freccia" placeholder="Es. Accettata" />
        <p className="text-[13px] text-grafite">
          Da <b className="font-medium text-pino-900">{sorgente?.businessObject?.name || 'senza nome'}</b> a <b className="font-medium text-pino-900">{elemento.target?.businessObject?.name || 'senza nome'}</b>
        </p>
      </Sezione>
      {!daDecisione && (
        <Sezione><Suggerimento>Le frecce normali non hanno condizioni: la pratica passa sempre di qui. Per scegliere una strada usa una decisione.</Suggerimento></Sezione>
      )}
      {daDecisione && (
        <Sezione titolo="Quando la pratica passa di qui">
          <label className="flex items-center gap-2 text-[15px]">
            <input type="checkbox" className="size-4 accent-verde-600" checked={predefinita} onChange={e => rendiPredefinita(e.target.checked)} />
            Altrimenti: se nessun'altra condizione è vera
          </label>
          {!predefinita && (avanzata
            ? (
              <div>
                <Etichetta>Condizione avanzata</Etichetta>
                <TestoDifferito mono value={espressione} onCommit={v => scriviCondizione(v)} />
                <button type="button" onClick={() => scriviCondizione('')} className="mt-1 text-[13px] font-medium text-verde-700 hover:underline">Ricomincia con il costruttore guidato</button>
              </div>
            )
            : <CostruttoreCondizione key={elemento.id} valore={parsed} campi={dati.campi} onChange={scriviCondizione} />)}
          <button type="button" onClick={() => onSeleziona(sorgente)} className="text-[13px] font-medium text-verde-700 hover:underline">Torna alla decisione</button>
        </Sezione>
      )}
    </>
  )
}

function CostruttoreCondizione({ valore, campi, onChange }) {
  // stato locale: una condizione incompleta (es. campo scelto, valore da scrivere) non va ancora nel BPMN
  const chiaveEsterna = valore ? `${valore.campo}|${valore.operatore}|${valore.valore}` : ''
  const [c, setC] = useState(valore || { campo: '', operatore: '', valore: '' })
  useEffect(() => { if (valore) setC(valore) }, [chiaveEsterna])

  const campo = campi.find(x => x.name === c.campo)
  const operatori = OPERATORI[famiglia(campo?.type)]
  const senzaValore = (op) => ['pieno', 'vuoto', 'vero', 'falso'].includes(op)

  const aggiorna = (patch) => {
    const n = { ...c, ...patch }
    const f = campi.find(x => x.name === n.campo)
    if (patch.campo !== undefined) {
      const ops = OPERATORI[famiglia(f?.type)]
      if (!ops.some(([k]) => k === n.operatore)) n.operatore = ops[0][0]
      n.valore = f?.type === 'select' ? (f.options?.[0] || '') : ''
    }
    setC(n)
    const completa = n.campo && n.operatore && (senzaValore(n.operatore) || n.valore !== '')
    if (completa) onChange(componi({ ...n, tipo: f?.type }), senzaValore(n.operatore) ? null : n.valore)
  }

  if (campi.length === 0) {
    return <Suggerimento tono="avviso">Per costruire una condizione collega prima dei form alle attività: le condizioni usano i loro campi.</Suggerimento>
  }
  return (
    <div className="space-y-2 rounded-campo bg-carta p-3">
      <div>
        <Etichetta>Se il campo</Etichetta>
        <select className="campo" value={c.campo} onChange={e => aggiorna({ campo: e.target.value })}>
          <option value="">Scegli un campo…</option>
          {campi.map(f => <option key={f.name} value={f.name}>{f.label}{f.origine ? ` (${f.origine})` : ''}</option>)}
        </select>
      </div>
      {c.campo && (
        <div className="grid gap-2 sm:grid-cols-2">
          <select className="campo" value={c.operatore} onChange={e => aggiorna({ operatore: e.target.value })} aria-label="Operatore">
            {operatori.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          {!senzaValore(c.operatore) && (
            campo?.type === 'select'
              ? (
                <select className="campo" value={c.valore} onChange={e => aggiorna({ valore: e.target.value })} aria-label="Valore">
                  {(campo.options || []).map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              )
              : <TestoDifferito value={c.valore} placeholder="Valore" onCommit={v => aggiorna({ valore: v })} />
          )}
        </div>
      )}
      {c.campo && !(senzaValore(c.operatore) || c.valore !== '') && (
        <p className="text-[13px] text-zafferano-800">Scrivi il valore per completare la condizione.</p>
      )}
    </div>
  )
}

function Nota({ bo, set }) {
  return (
    <Sezione>
      <Etichetta htmlFor="nota">Testo della nota</Etichetta>
      <TestoDifferito id="nota" multiline value={bo.text} onCommit={v => set({ text: v })} />
      <p className="text-[13px] text-grafite">Le note servono a chi legge il diagramma: non cambiano il funzionamento.</p>
    </Sezione>
  )
}

function TaskGenerico(ctx) {
  return (
    <>
      <Sezione><Nome {...ctx} /></Sezione>
      <Sezione><Suggerimento tono="avviso">Scegli il tipo con l'icona della chiave inglese accanto all'elemento: attività di una persona o attività automatica.</Suggerimento></Sezione>
    </>
  )
}

function Generico(ctx) {
  return <Sezione><Nome {...ctx} /></Sezione>
}

function Suggerimento({ tono, children }) {
  return (
    <p className={cx('flex items-start gap-2 rounded-campo px-3 py-2 text-[13px]',
      tono === 'avviso' ? 'bg-zafferano-50 text-zafferano-800' : 'bg-carta text-pino-800')}>
      <Info className="mt-0.5 size-4 shrink-0" /> <span>{children}</span>
    </p>
  )
}

