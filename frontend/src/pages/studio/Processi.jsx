import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CircleX, Download, FileCode, FileUp, ListChecks, Package, PackageOpen, Pencil, Plus, TriangleAlert, Upload, Users, Workflow } from 'lucide-react'
import { api, scarica } from '../../api.js'
import { useAuth } from '../../lib/auth.jsx'
import { PERMESSI, puo } from '../../lib/permessi.js'
import { chiaveDaNome } from '../../lib/bpmn/orditogramModeler.js'
import { cx, dataOraCompleta, quando } from '../../lib/format.js'
import BpmnViewer from '../../components/BpmnViewer.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Vuoto, useToast } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'

/** Studio: i processi in produzione e il caricamento di nuovi BPMN, senza riavviare. */
export default function Processi() {
  const [lista, setLista] = useState(null)
  const [scelto, setScelto] = useState(null)
  const [xml, setXml] = useState('')
  const [carica, setCarica] = useState(false)
  const [nuovo, setNuovo] = useState(false)
  const [bozze, setBozze] = useState([])
  const [errore, setErrore] = useState('')
  const navigate = useNavigate()
  const toast = useToast()
  const inputPacchetto = useRef(null)

  const load = async (selezionaKey) => {
    try {
      const [l, b] = await Promise.all([api.adminProcesses(), api.bozze()])
      setLista(l)
      setBozze(b)
      const s = l.find(p => p.key === (selezionaKey || scelto?.key)) || l[0]
      if (s) void apri(s)
    } catch (e) { setErrore(e.message) }
  }
  useEffect(() => { void load() }, [])

  const apri = async (p) => {
    setScelto(p); setXml('')
    try { setXml(await api.processXml(p.id)) } catch (e) { setErrore(e.message) }
  }

  const importa = async (file, sovrascrivi = false) => {
    try {
      const r = await api.importaPacchetto(file, sovrascrivi)
      toast.ok(`Pacchetto importato come bozza: ${r.nome}. Controllalo e pubblicalo dall'editor.`)
      navigate(`/studio/editor/${r.chiave}`)
    } catch (e) {
      if (!sovrascrivi && /esistono già/.test(e.message)) {
        if (confirm(e.message + '\n\nSovrascriverli con quelli del pacchetto? I processi in produzione che li usano vedranno subito la nuova versione.')) void importa(file, true)
      } else toast.errore(e.message)
    }
  }

  const scaricaXml = () => {
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([xml], { type: 'text/xml' }))
    a.download = `${scelto.key}.bpmn20.xml`
    a.click()
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Processi</h1>
          <p className="mt-1 max-w-2xl text-grafite">I processi in produzione. Caricane uno nuovo o una nuova versione: le pratiche in corso proseguono sulla versione con cui sono partite.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Esporta nome="processi" titolo="Processi" disabled={!lista} righe={lista || []}
                   colonne={[
                     { titolo: 'Processo', valore: p => p.name },
                     { titolo: 'Identificativo', valore: p => p.key },
                     { titolo: 'Versione', valore: p => p.version },
                     { titolo: 'Pubblicato', valore: p => dataOraCompleta(p.deployedAt) },
                     { titolo: 'Chi può avviarlo', valore: p => (p.starterGroups || []).join(', ') || 'Tutti' },
                     { titolo: 'Gruppi', valore: p => (p.groups || []).join(', ') },
                     { titolo: 'Form', valore: p => (p.formKeys || []).join(', ') },
                     { titolo: 'Avvisi', valore: p => [...(p.errors || []), ...(p.warnings || [])].map(x => typeof x === 'string' ? x : x.messaggio).join('; ') }
                   ]} />
          <Button icona={PackageOpen} onClick={() => inputPacchetto.current?.click()}>Importa pacchetto</Button>
          <Button icona={Upload} onClick={() => setCarica(true)}>Carica BPMN</Button>
          <Button variante="primario" icona={Plus} onClick={() => setNuovo(true)}>Nuovo processo</Button>
          <input ref={inputPacchetto} type="file" accept=".zip" className="hidden"
                 onChange={e => { const f = e.target.files[0]; e.target.value = ''; if (f) void importa(f) }} />
        </div>
      </header>

      {bozze.filter(b => !b.inProduzione).length > 0 && (
        <div className="rounded-pannello border border-zafferano-100 bg-zafferano-50/60 p-4">
          <p className="text-[15px] font-medium text-zafferano-800">Processi in preparazione, non ancora pubblicati</p>
          <div className="mt-2 flex flex-wrap gap-2">
            {bozze.filter(b => !b.inProduzione).map(b => (
              <button key={b.chiave} onClick={() => navigate(`/studio/editor/${b.chiave}`)}
                      className="rounded-campo border border-zafferano-100 bg-white px-3.5 py-2 text-left hover:border-zafferano-400">
                <span className="block text-[15px] font-medium text-pino-900">{b.nome}</span>
                <span className="block text-xs text-grafite">{b.aggiornatoDa}, {quando(b.aggiornatoIl)}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <Errore>{errore}</Errore>
      {!lista && !errore && <Caricamento />}

      {lista && lista.length === 0 && (
        <Pannello><Vuoto icona={Workflow} titolo="Nessun processo" azione={<Button icona={Upload} onClick={() => setCarica(true)}>Carica il primo processo</Button>}>
          Carica un file BPMN con i suoi form per metterlo subito a disposizione degli utenti.
        </Vuoto></Pannello>
      )}

      {lista && lista.length > 0 && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.6fr)]">
          <ul className="overflow-hidden rounded-pannello border border-linea bg-white shadow-pannello">
            {lista.map(p => (
              <li key={p.id} className="border-b border-linea last:border-0">
                <button onClick={() => apri(p)}
                        className={cx('flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors',
                          scelto?.id === p.id ? 'bg-verde-50/70 shadow-[inset_3px_0_0_var(--color-verde-600)]' : 'hover:bg-carta')}>
                  <span className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-campo bg-carta text-pino-700"><Workflow className="size-[18px]" /></span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium text-pino-900">{p.name}</span>
                    <span className="block text-[13px] text-grafite">versione {p.version}, aggiornato {quando(p.deployedAt)}</span>
                  </span>
                  {p.bozza && <Badge tono="zafferano">bozza</Badge>}
                  {p.errors.length > 0 && <Badge tono="mattone">errori</Badge>}
                  {p.errors.length === 0 && p.warnings.length > 0 && <Badge tono="zafferano">{p.warnings.length} avvisi</Badge>}
                </button>
              </li>
            ))}
          </ul>

          {scelto && (
            <Pannello titolo={<div><h2 className="text-xl font-semibold">{scelto.name}</h2><p className="text-[13px] text-grafite">{scelto.key}, versione {scelto.version} di {scelto.versions}</p></div>}
                      azioni={<>
                        <Button misura="sm" variante="fantasma" icona={Download} disabled={!xml} onClick={scaricaXml}>BPMN</Button>
                        <Button misura="sm" variante="fantasma" icona={Package}
                                onClick={() => scarica(api.urlPacchetto(scelto.key)).catch(e => toast.errore(e.message))}>Esporta pacchetto</Button>
                        <Button misura="sm" variante="primario" icona={Pencil} onClick={() => navigate(`/studio/editor/${scelto.key}`)}>
                          {scelto.bozza ? 'Continua la bozza' : 'Modifica'}
                        </Button>
                      </>}>
              <div className="space-y-5">
                {xml ? <BpmnViewer xml={xml} altezza={360} /> : <Caricamento />}
                <dl className="grid gap-4 sm:grid-cols-3">
                  <Fatto icona={Users} label="Chi può avviarlo">{scelto.starterGroups?.length ? scelto.starterGroups.join(', ') : 'Tutti gli utenti'}</Fatto>
                  <Fatto icona={Users} label="Gruppi coinvolti">{scelto.groups.join(', ') || 'Nessuno'}</Fatto>
                  <Fatto icona={ListChecks} label="Form usati">{scelto.formKeys.join(', ') || 'Nessuno'}</Fatto>
                </dl>
                {(scelto.errors.length > 0 || scelto.warnings.length > 0) && (
                  <ul className="space-y-2">
                    {scelto.errors.map(w => <Avviso key={w} grave>{w}</Avviso>)}
                    {scelto.warnings.map(w => <Avviso key={w}>{w}</Avviso>)}
                  </ul>
                )}
              </div>
            </Pannello>
          )}
        </div>
      )}

      <NuovoProcesso aperto={nuovo} onClose={() => setNuovo(false)}
                     esistenti={[...(lista || []).map(p => p.key), ...bozze.map(b => b.chiave)]}
                     onCrea={(chiave, nome) => navigate(`/studio/editor/${chiave}?nome=${encodeURIComponent(nome)}`)} />
      <CaricaProcesso aperto={carica} onClose={() => setCarica(false)} onDeploy={(key) => { setCarica(false); void load(key) }} />
    </div>
  )
}

/** Nuovo processo: basta il nome, l'identificativo si ricava da solo. */
function NuovoProcesso({ aperto, onClose, esistenti, onCrea }) {
  const [nome, setNome] = useState('')
  const [chiave, setChiave] = useState('')
  const [manuale, setManuale] = useState(false)
  useEffect(() => { if (aperto) { setNome(''); setChiave(''); setManuale(false) } }, [aperto])
  const occupata = esistenti.includes(chiave)
  const valida = /^[A-Za-z_][\w.-]*$/.test(chiave)
  return (
    <Dialogo aperto={aperto} onClose={onClose} titolo="Nuovo processo" larghezza="max-w-lg"
             sottotitolo="Si apre l'editor con un primo schema da completare.">
      <form className="space-y-4" onSubmit={e => { e.preventDefault(); if (valida && !occupata && nome.trim()) onCrea(chiave, nome.trim()) }}>
        <div>
          <label className="etichetta" htmlFor="np-nome">Nome del processo</label>
          <input id="np-nome" className="campo" autoFocus value={nome} placeholder="Es. Richiesta di accesso agli atti"
                 onChange={e => { setNome(e.target.value); if (!manuale) setChiave(chiaveDaNome(e.target.value)) }} />
        </div>
        <div>
          <label className="etichetta" htmlFor="np-chiave">Identificativo</label>
          <input id="np-chiave" className="campo font-mono text-sm" value={chiave}
                 onChange={e => { setManuale(true); setChiave(e.target.value.replace(/[^\w.-]/g, '')) }} />
          <p className={cx('mt-1 text-[13px]', occupata || (chiave && !valida) ? 'text-mattone-600' : 'text-grafite')}>
            {occupata ? 'Esiste già un processo con questo identificativo.'
              : chiave && !valida ? 'Deve iniziare con una lettera.'
              : 'Serve al sistema per riconoscere il processo e le sue versioni. Non si cambia dopo.'}
          </p>
        </div>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button type="button" variante="fantasma" onClick={onClose}>Annulla</Button>
          <Button type="submit" variante="primario" disabled={!nome.trim() || !valida || occupata}>Apri l'editor</Button>
        </div>
      </form>
    </Dialogo>
  )
}

function Fatto({ icona: Icona, label, children }) {
  return (
    <div className="rounded-campo bg-carta px-3.5 py-3">
      <dt className="flex items-center gap-1.5 text-[13px] text-grafite"><Icona className="size-3.5" /> {label}</dt>
      <dd className="mt-1 text-[15px] text-pino-900">{children}</dd>
    </div>
  )
}

function Avviso({ grave, children }) {
  const Icona = grave ? CircleX : TriangleAlert
  return (
    <li className={cx('flex items-start gap-2 rounded-campo px-3 py-2 text-sm',
      grave ? 'bg-mattone-50 text-mattone-700' : 'bg-zafferano-50 text-zafferano-800')}>
      <Icona className="mt-0.5 size-4 shrink-0" /> {children}
    </li>
  )
}

/** Caricamento a caldo: BPMN + form; le extensions, se mancano, le genera il backend. */
function CaricaProcesso({ aperto, onClose, onDeploy }) {
  const [bpmn, setBpmn] = useState(null)
  const [forms, setForms] = useState([])
  const [ext, setExt] = useState(null)
  const [attesa, setAttesa] = useState(false)
  const [errore, setErrore] = useState('')
  const toast = useToast()
  const { me } = useAuth()

  useEffect(() => { if (aperto) { setBpmn(null); setForms([]); setExt(null); setErrore('') } }, [aperto])

  const deploy = async () => {
    setAttesa(true); setErrore('')
    const fd = new FormData()
    fd.append('bpmn', bpmn)
    forms.forEach(f => fd.append('forms', f))
    if (ext) fd.append('extensions', ext)
    try {
      const r = await api.deploy(fd)
      toast.ok(`${r.name} in produzione, versione ${r.version}` + (r.warnings.length ? `, con ${r.warnings.length} avvisi` : ''))
      onDeploy(r.key)
    } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }

  return (
    <Dialogo aperto={aperto} onClose={onClose} titolo="Carica processo"
             sottotitolo="Il processo è disponibile subito, senza riavviare il sistema.">
      <div className="space-y-4">
        <Scelta etichetta="Diagramma BPMN" obbligatorio accept=".bpmn,.xml" icona={FileCode}
                files={bpmn ? [bpmn] : []} onChange={fs => setBpmn(fs[0] || null)}
                aiuto="Un solo processo eseguibile per file. I file con lo stesso id diventano una nuova versione." />
        <Scelta etichetta="Form" multiplo accept=".json" icona={ListChecks}
                files={forms} onChange={setForms}
                aiuto="Un file .json per form, chiamato come il formKey usato nel BPMN. Quelli già presenti si possono omettere." />
        <Scelta etichetta="Extensions" accept=".json" icona={FileUp}
                files={ext ? [ext] : []} onChange={fs => setExt(fs[0] || null)}
                aiuto="Facoltativo: se manca viene generato dai campi dei form." />
        <Errore>{errore}</Errore>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button variante="fantasma" onClick={onClose}>Annulla</Button>
          <Button variante="primario" icona={Upload} disabled={!bpmn || !puo(me, PERMESSI.pubblica)} attesa={attesa} onClick={deploy}
                  title={puo(me, PERMESSI.pubblica) ? '' : 'Serve il permesso di pubblicazione'}>Metti in produzione</Button>
        </div>
      </div>
    </Dialogo>
  )
}

function Scelta({ etichetta, obbligatorio, multiplo, accept, icona: Icona, files, onChange, aiuto }) {
  const input = useRef(null)
  return (
    <div className="rounded-pannello border border-linea p-4">
      <div className="flex items-start gap-3">
        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-campo bg-carta text-pino-700"><Icona className="size-[18px]" /></span>
        <div className="min-w-0 flex-1">
          <p className="font-medium text-pino-900">{etichetta}{obbligatorio && <span className="text-mattone-600"> *</span>}</p>
          <p className="mt-0.5 text-[13px] text-grafite">{aiuto}</p>
          {files.length > 0 && <p className="mt-2 text-sm text-pino-800">{files.map(f => f.name).join(', ')}</p>}
        </div>
        <Button misura="sm" onClick={() => input.current?.click()}>{files.length ? 'Cambia' : 'Scegli'}</Button>
        <input ref={input} type="file" accept={accept} multiple={multiplo} className="hidden"
               onChange={e => { onChange(Array.from(e.target.files)); e.target.value = '' }} />
      </div>
    </div>
  )
}
