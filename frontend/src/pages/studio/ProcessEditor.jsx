import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import {
  ArrowLeft, ChevronDown, CirclePlay, ChevronUp, CircleCheck, CircleX, CloudUpload, Download, LoaderCircle,
  Maximize, Redo2, TriangleAlert, Undo2, ZoomIn, ZoomOut
} from 'lucide-react'
import { api } from '../../api.js'
import { cx, quando } from '../../lib/format.js'
import activitiModdle from '../../lib/bpmn/activiti.json'
import orditogramModdle from '../../lib/bpmn/orditogram.json'
import { orditogramModule, italianoModule, xmlIniziale } from '../../lib/bpmn/orditogramModeler.js'
import { EVENTI, simulazioneModule, traduciSimulazione } from '../../lib/bpmn/simulazione.js'
import PannelloSimulazione from '../../components/editor/PannelloSimulazione.jsx'
import Pannello from '../../components/editor/Pannello.jsx'
import FormEditor from '../../components/FormEditor.jsx'
import { Button, Dialogo, Errore, useToast } from '../../components/ui.jsx'

const DATI_SISTEMA = [
  { name: 'avviataDa', label: 'Avviata da', type: 'text', origine: 'sistema' },
  { name: 'chiusaDa', label: 'Ultima attività completata da', type: 'text', origine: 'sistema' }
]

/**
 * Editor dei processi: si disegna il flusso, si configura ogni passo dal pannello,
 * Orditogram controlla in continuo e salva la bozza da solo. "Pubblica" lo mette in produzione.
 */
export default function ProcessEditor() {
  const { chiave } = useParams()
  const [search] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()

  const contenitore = useRef(null)
  const modelerRef = useRef(null)
  const [modeler, setModeler] = useState(null)
  const [selezionato, setSelezionato] = useState(null)
  const [, setVersione] = useState(0)

  const [info, setInfo] = useState(null)          // { bozza, aggiornatoIl, aggiornatoDa, inProduzione }
  const [stato, setStato] = useState('caricamento') // caricamento | salvato | modificato | salvataggio | errore
  const [errore, setErrore] = useState('')
  const [problemi, setProblemi] = useState({ errors: [], warnings: [], inCorso: true })
  const [mostraProblemi, setMostraProblemi] = useState(true)
  const [storia, setStoria] = useState({ undo: false, redo: false })
  const [pubblicazione, setPubblicazione] = useState(false)
  const [formAperto, setFormAperto] = useState(null)
  const [simulando, setSimulando] = useState(false)

  const [dati, setDati] = useState({ chiave, gruppi: [], connettori: [], forms: [], campi: [], credenziali: [] })
  const cacheForm = useRef({})
  const connettoriRef = useRef([])

  // ------------------------------------------------------------ dati di supporto
  const caricaForms = useCallback(async () => {
    const l = await api.forms()
    setDati(d => ({ ...d, forms: l.map(f => f.formKey), formsInfo: l }))
  }, [])

  useEffect(() => {
    Promise.all([api.gruppi(), api.connettori(), api.forms(), api.credenziali().catch(() => []), api.adminProcesses().catch(() => [])])
      .then(([gruppi, connettori, forms, credenziali, processi]) => {
        connettoriRef.current = connettori
        setDati(d => ({ ...d, gruppi, connettori, credenziali, forms: forms.map(f => f.formKey), formsInfo: forms,
          processi: processi.map(p => ({ key: p.key, name: p.name, version: p.version })) }))
        aggiornaCampi()
      })
      .catch(e => setErrore(e.message))
  }, [])

  /** Campi disponibili per condizioni e messaggi: quelli dei form usati nel diagramma. */
  const aggiornaCampi = useCallback(async () => {
    const m = modelerRef.current
    if (!m) return
    const chiavi = [...new Set(m.get('elementRegistry').filter(e => e.businessObject?.formKey).map(e => e.businessObject.formKey))]
    const campi = []
    const visti = new Set()
    for (const k of chiavi) {
      if (!(k in cacheForm.current)) cacheForm.current[k] = await api.form(k).catch(() => null)
      ;(cacheForm.current[k]?.fields || []).forEach(f => {
        if (!f.name || visti.has(f.name)) return
        visti.add(f.name)
        campi.push({ name: f.name, label: f.label || f.name, type: f.type || 'text', options: f.options, origine: k })
      })
    }
    // dati scritti dalle attività automatiche (es. il numero di protocollo restituito da un'API)
    m.get('elementRegistry').filter(e => e.businessObject?.$type === 'bpmn:ServiceTask').forEach(e => {
      const bo = e.businessObject
      const c = connettoriRef.current.find(x => x.id === bo.implementation)
      if (!c) return
      const campiBo = {}
      ;(bo.extensionElements?.values || []).filter(v => v.$type === 'activiti:Field').forEach(v => { campiBo[v.name] = v.stringValue ?? '' })
      c.parametri.filter(p => p.tipo === 'mappatura').forEach(p => {
        ;(campiBo[p.nome] || '').split('\n').forEach(riga => {
          const nome = riga.split('=')[0].trim()
          if (!/^[a-zA-Z][\w-]*$/.test(nome) || visti.has(nome)) return
          visti.add(nome)
          campi.push({ name: nome, label: nome, type: 'text', origine: bo.name || 'attività automatica' })
        })
      })
    })
    // dati portati dai messaggi in arrivo
    m.get('elementRegistry').forEach(e => {
      const ext = (e.businessObject?.extensionElements?.values || []).find(v => v.$type === 'orditogram:DatiMessaggio')
      ;(ext?.body || '').split(/[,\s]+/).forEach(nome => {
        if (!/^[a-zA-Z][\w-]*$/.test(nome) || visti.has(nome)) return
        visti.add(nome)
        campi.push({ name: nome, label: nome, type: 'text', origine: 'messaggio ' + (e.businessObject.name || '') })
      })
    })
    DATI_SISTEMA.forEach(d => { if (!visti.has(d.name)) campi.push(d) })
    setDati(d => ({ ...d, campi }))
  }, [])

  // ------------------------------------------------------------ validazione e bozza (con ritardo)
  const timerValida = useRef(null)
  const timerSalva = useRef(null)
  const marcati = useRef([])

  const valida = useCallback(async () => {
    const m = modelerRef.current
    if (!m) return
    try {
      const { xml } = await m.saveXML({ format: true })
      const r = await api.valida(xml)
      setProblemi({ errors: r.errors || [], warnings: r.warnings || [], inCorso: false })
      const canvas = m.get('canvas')
      const registro = m.get('elementRegistry')
      marcati.current.forEach(([id, cls]) => { if (registro.get(id)) canvas.removeMarker(id, cls) })
      marcati.current = []
      const conErrore = new Set((r.errors || []).map(p => p.elementId).filter(Boolean))
      ;(r.warnings || []).forEach(p => p.elementId && !conErrore.has(p.elementId) && registro.get(p.elementId) && (canvas.addMarker(p.elementId, 'orditogram-avviso'), marcati.current.push([p.elementId, 'orditogram-avviso'])))
      ;(r.errors || []).forEach(p => p.elementId && registro.get(p.elementId) && (canvas.addMarker(p.elementId, 'orditogram-errore'), marcati.current.push([p.elementId, 'orditogram-errore'])))
    } catch (e) {
      setProblemi({ errors: [{ messaggio: 'Controllo non riuscito: ' + e.message }], warnings: [], inCorso: false })
    }
  }, [])

  const salvaBozza = useCallback(async () => {
    const m = modelerRef.current
    if (!m) return
    setStato('salvataggio')
    try {
      const { xml } = await m.saveXML({ format: true })
      const nome = m.get('canvas').getRootElement().businessObject.name || chiave
      const r = await api.salvaBozza(chiave, xml, nome)
      setInfo(i => ({ ...i, bozza: true, aggiornatoIl: r.aggiornatoIl, aggiornatoDa: r.aggiornatoDa }))
      setStato('salvato')
    } catch (e) {
      setStato('errore')
      toast.errore('Bozza non salvata: ' + e.message)
    }
  }, [chiave, toast])

  const programma = useCallback(() => {
    setStato('modificato')
    setProblemi(p => ({ ...p, inCorso: true }))
    clearTimeout(timerValida.current)
    clearTimeout(timerSalva.current)
    timerValida.current = setTimeout(valida, 900)
    timerSalva.current = setTimeout(salvaBozza, 1800)
  }, [valida, salvaBozza])

  // ------------------------------------------------------------ creazione del modeler e caricamento
  useEffect(() => {
    const m = new BpmnModeler({
      container: contenitore.current,
      moddleExtensions: { activiti: activitiModdle, orditogram: orditogramModdle },
      additionalModules: [orditogramModule, italianoModule, simulazioneModule]
    })
    modelerRef.current = m
    window.__orditogramModeler = m   // utile per il debug dalla console
    setModeler(m)

    const eventBus = m.get('eventBus')
    const smettiDiTradurre = traduciSimulazione(contenitore.current)
    eventBus.on(EVENTI.modo, ({ active }) => setSimulando(!!active))
    eventBus.on('selection.changed', ({ newSelection }) => setSelezionato(newSelection.length === 1 ? newSelection[0] : null))
    eventBus.on('elements.changed', () => setVersione(v => v + 1))
    eventBus.on('commandStack.changed', () => {
      const cs = m.get('commandStack')
      setStoria({ undo: cs.canUndo(), redo: cs.canRedo() })
    })

    let annullato = false
    void (async () => {
      let xml, informazioni
      try {
        const b = await api.bozza(chiave)
        xml = b.xml
        informazioni = { bozza: true, aggiornatoIl: b.aggiornatoIl, aggiornatoDa: b.aggiornatoDa, inProduzione: b.inProduzione }
      } catch {
        try {
          xml = await api.processXmlPerChiave(chiave)
          const lista = await api.adminProcesses()
          informazioni = { bozza: false, inProduzione: lista.find(p => p.key === chiave)?.version }
        } catch {
          xml = xmlIniziale(chiave, search.get('nome') || chiave)
          informazioni = { bozza: false, inProduzione: null, nuovo: true }
        }
      }
      if (annullato) return
      try {
        await m.importXML(xml)
        m.get('canvas').zoom('fit-viewport', 'auto')
        setInfo(informazioni)
        setStato(informazioni.bozza ? 'salvato' : 'pulito')
        // dopo l'import iniziale le modifiche avviano controllo e salvataggio
        eventBus.on('commandStack.changed', () => { programma() })
        await aggiornaCampi()
        valida()
        if (informazioni.nuovo) salvaBozza()
      } catch (e) {
        setErrore('Il diagramma non si può aprire: ' + e.message)
        setStato('errore')
      }
    })()

    return () => {
      annullato = true
      clearTimeout(timerValida.current)
      clearTimeout(timerSalva.current)
      smettiDiTradurre()
      m.destroy()
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chiave])

  // i campi disponibili cambiano quando si collegano o tolgono form
  useEffect(() => {
    if (!modeler) return
    const eb = modeler.get('eventBus')
    // attenzione: in bpmn-js un listener che restituisce un valore ferma l'evento per gli altri
    const h = () => { aggiornaCampi() }
    eb.on('commandStack.changed', h)
    return () => eb.off('commandStack.changed', h)
  }, [modeler, aggiornaCampi])

  // avviso se si chiude la pagina con modifiche non salvate
  useEffect(() => {
    const h = (e) => { if (stato === 'modificato' || stato === 'salvataggio') { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', h)
    return () => window.removeEventListener('beforeunload', h)
  }, [stato])

  // ------------------------------------------------------------ azioni
  const seleziona = (el) => {
    const m = modelerRef.current
    if (!m) return
    if (!el) { m.get('selection').select(null); setSelezionato(null); return }
    const elemento = typeof el === 'string' ? m.get('elementRegistry').get(el) : el
    if (!elemento) return
    m.get('selection').select(elemento)
    if (elemento.waypoints || elemento.x !== undefined) m.get('canvas').scrollToElement(elemento, { top: 120, bottom: 120, left: 120, right: 420 })
  }

  const scaricaBpmn = async () => {
    const { xml } = await modelerRef.current.saveXML({ format: true })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([xml], { type: 'text/xml' }))
    a.download = `${chiave}.bpmn20.xml`
    a.click()
  }

  const pubblica = async () => {
    setPubblicazione(true)
    try {
      clearTimeout(timerSalva.current)
      const { xml } = await modelerRef.current.saveXML({ format: true })
      const r = await api.pubblicaXml(chiave, xml)
      setStato('salvato')
      toast.ok(`${r.name} pubblicato, versione ${r.version}: gli utenti possono già usarlo`)
      navigate('/studio/processi')
    } catch (e) {
      toast.errore(e.message)
      setPubblicazione(false)
    }
  }

  const scartaBozza = async () => {
    if (!confirm('Scartare la bozza e tornare alla versione in produzione?')) return
    clearTimeout(timerSalva.current)
    await api.scartaBozza(chiave)
    toast.ok('Bozza scartata')
    navigate(0)
  }

  const zoom = (d) => {
    const canvas = modelerRef.current.get('canvas')
    if (d === 0) canvas.zoom('fit-viewport', 'auto')
    else canvas.zoom(canvas.zoom() * (d > 0 ? 1.2 : 1 / 1.2))
  }

  const radice = modeler && stato !== 'caricamento' ? modeler.get('canvas').getRootElement() : null
  const elementoPannello = selezionato && selezionato.type !== 'label' ? selezionato : (selezionato?.labelTarget || radice)
  const nomeProcesso = radice?.businessObject?.name || chiave
  const nErrori = problemi.errors.length
  const nAvvisi = problemi.warnings.length

  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col">
      {/* --- barra superiore */}
      <div className="flex flex-wrap items-center gap-3 border-b border-linea bg-white px-4 py-2.5 sm:px-6">
        <Link to="/studio/processi" className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
          <ArrowLeft className="size-4" /> Processi
        </Link>
        <span className="h-5 w-px bg-linea" />
        <div className="min-w-0">
          <p className="truncate font-display text-lg font-semibold leading-tight">{nomeProcesso}</p>
          <p className="text-xs text-grafite">
            {info?.inProduzione ? `In produzione la versione ${info.inProduzione}` : 'Non ancora pubblicato'}
            {info?.bozza && <>, bozza {stato === 'salvato' && info.aggiornatoIl ? `salvata ${quando(info.aggiornatoIl)}` : ''}</>}
          </p>
        </div>
        <StatoSalvataggio stato={stato} />

        <div className="ml-auto flex flex-wrap items-center gap-1.5">
          <button onClick={() => modelerRef.current.get('toggleMode').toggleMode()} disabled={stato === 'caricamento'}
                  className={cx('inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-sm font-medium',
                    simulando ? 'bg-zafferano-400 text-pino-900 hover:brightness-95' : 'text-pino-800 ring-1 ring-linea hover:bg-carta')}>
            <CirclePlay className="size-4" /> {simulando ? 'Esci dalla simulazione' : 'Simula'}
          </button>
          <span className="mx-1 h-5 w-px bg-linea" />
          <IconBtn label="Annulla" disabled={!storia.undo || simulando} onClick={() => modelerRef.current.get('commandStack').undo()}><Undo2 className="size-4" /></IconBtn>
          <IconBtn label="Ripeti" disabled={!storia.redo || simulando} onClick={() => modelerRef.current.get('commandStack').redo()}><Redo2 className="size-4" /></IconBtn>
          <span className="mx-1 h-5 w-px bg-linea" />
          <IconBtn label="Rimpicciolisci" onClick={() => zoom(-1)}><ZoomOut className="size-4" /></IconBtn>
          <IconBtn label="Adatta alla finestra" onClick={() => zoom(0)}><Maximize className="size-4" /></IconBtn>
          <IconBtn label="Ingrandisci" onClick={() => zoom(1)}><ZoomIn className="size-4" /></IconBtn>
          <span className="mx-1 h-5 w-px bg-linea" />
          {info?.bozza && info?.inProduzione && <Button misura="sm" variante="fantasma" onClick={scartaBozza}>Scarta bozza</Button>}
          <Button misura="sm" variante="fantasma" icona={Download} onClick={scaricaBpmn}>BPMN</Button>
          <Button misura="sm" variante="primario" icona={CloudUpload} attesa={pubblicazione}
                  disabled={nErrori > 0 || problemi.inCorso || stato === 'caricamento'}
                  title={nErrori > 0 ? 'Correggi gli errori prima di pubblicare' : ''}
                  onClick={pubblica}>
            Pubblica
          </Button>
        </div>
      </div>

      {errore && <div className="p-4"><Errore>{errore}</Errore></div>}

      {/* --- tela + pannello */}
      <div className="flex min-h-0 flex-1">
        <div className="relative isolate min-w-0 flex-1">
          <div ref={contenitore} className="orditogram-editor absolute inset-0 bg-[radial-gradient(circle,var(--color-linea)_1px,transparent_1px)] bg-[size:20px_20px]" />

          {/* problemi */}
          {simulando && (
            <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center">
              <p className="mt-3 rounded-full bg-zafferano-400 px-4 py-1.5 text-sm font-medium text-pino-900 shadow-sollevato">
                Simulazione: il diagramma non si modifica, nessun dato viene salvato
              </p>
            </div>
          )}
          <div className={cx('absolute inset-x-4 bottom-4 z-10 sm:left-auto sm:w-[460px]', simulando && 'hidden')}>
            <div className="overflow-hidden rounded-pannello border border-linea bg-white shadow-sollevato">
              <button onClick={() => setMostraProblemi(!mostraProblemi)} className="flex w-full items-center gap-3 px-4 py-2.5 text-left">
                {problemi.inCorso ? <LoaderCircle className="size-4 animate-spin text-grafite" />
                  : nErrori ? <CircleX className="size-4 text-mattone-600" />
                  : nAvvisi ? <TriangleAlert className="size-4 text-zafferano-500" />
                  : <CircleCheck className="size-4 text-verde-600" />}
                <span className="flex-1 text-[15px] font-medium text-pino-900">
                  {problemi.inCorso ? 'Controllo in corso…'
                    : nErrori ? `${nErrori} ${nErrori === 1 ? 'errore' : 'errori'} da correggere${nAvvisi ? `, ${nAvvisi} avvisi` : ''}`
                    : nAvvisi ? `Pronto da pubblicare, ${nAvvisi} ${nAvvisi === 1 ? 'avviso' : 'avvisi'}`
                    : 'Pronto da pubblicare'}
                </span>
                {(nErrori + nAvvisi) > 0 && (mostraProblemi ? <ChevronDown className="size-4 text-grafite" /> : <ChevronUp className="size-4 text-grafite" />)}
              </button>
              {mostraProblemi && (nErrori + nAvvisi) > 0 && (
                <ul className="max-h-52 divide-y divide-linea overflow-y-auto border-t border-linea">
                  {problemi.errors.map((p, i) => <Problema key={'e' + i} p={p} grave onClick={() => p.elementId ? seleziona(p.elementId) : seleziona(null)} />)}
                  {problemi.warnings.map((p, i) => <Problema key={'w' + i} p={p} onClick={() => p.elementId ? seleziona(p.elementId) : seleziona(null)} />)}
                </ul>
              )}
            </div>
          </div>
        </div>

        <aside className="hidden w-[380px] shrink-0 overflow-y-auto border-l border-linea bg-white md:block">
          {simulando
            ? <PannelloSimulazione modeler={modeler} onEsci={() => modelerRef.current.get('toggleMode').toggleMode(false)} />
            : elementoPannello && (
              <Pannello modeler={modeler} elemento={elementoPannello} dati={dati} onSeleziona={seleziona}
                        onForm={setFormAperto} />
            )}
        </aside>
      </div>

      {/* --- costruttore del form, senza lasciare l'editor */}
      <Dialogo aperto={!!formAperto} onClose={() => setFormAperto(null)} larghezza="max-w-[1200px]"
               titolo={formAperto?.nuovo ? 'Nuovo form' : `Form ${formAperto?.chiave || ''}`}
               sottotitolo="Salva il form: viene collegato subito a questo passo del processo.">
        {formAperto && (
          <FormEditor chiave={formAperto.chiave} nuovo={formAperto.nuovo} suggerimento={formAperto.suggerimento || formAperto.chiave || ''}
                      onSalvato={async (k) => {
                        delete cacheForm.current[k]
                        formAperto.applica(k)
                        await caricaForms()
                        await aggiornaCampi()
                        setFormAperto(null)
                      }} />
        )}
      </Dialogo>
    </div>
  )
}

function StatoSalvataggio({ stato }) {
  const voci = {
    caricamento: [LoaderCircle, 'Apertura…', 'text-grafite', true],
    pulito: [null, 'Nessuna modifica', 'text-grafite'],
    modificato: [null, 'Modifiche in corso', 'text-grafite'],
    salvataggio: [LoaderCircle, 'Salvataggio bozza…', 'text-grafite', true],
    salvato: [CircleCheck, 'Bozza al sicuro', 'text-verde-700'],
    errore: [CircleX, 'Bozza non salvata', 'text-mattone-600']
  }
  const [Icona, testo, colore, gira] = voci[stato] || voci.salvato
  return (
    <span className={cx('hidden items-center gap-1.5 text-[13px] sm:inline-flex', colore)}>
      {Icona && <Icona className={cx('size-3.5', gira && 'animate-spin')} />} {testo}
    </span>
  )
}

function Problema({ p, grave, onClick }) {
  return (
    <li>
      <button onClick={onClick} className="flex w-full items-start gap-2.5 px-4 py-2 text-left text-sm hover:bg-carta">
        {grave ? <CircleX className="mt-0.5 size-4 shrink-0 text-mattone-600" /> : <TriangleAlert className="mt-0.5 size-4 shrink-0 text-zafferano-500" />}
        <span className="text-pino-900">{p.messaggio}</span>
      </button>
    </li>
  )
}

function IconBtn({ label, onClick, disabled, children }) {
  return (
    <button onClick={onClick} disabled={disabled} aria-label={label} title={label}
            className="inline-flex size-8 items-center justify-center rounded-md text-pino-800 hover:bg-carta disabled:opacity-35">
      {children}
    </button>
  )
}
