import { useEffect, useId, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, BookUser, ChevronLeft, ChevronRight, Plus, Search, Upload, X } from 'lucide-react'
import { api } from '../api.js'
import { quando } from '../lib/format.js'
import { STATI_SCHEDA, colonneEsportazione, descriviImportazione } from '../lib/anagrafiche.js'
import DynamicForm from '../components/DynamicForm.jsx'
import Esporta from '../components/Esporta.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../components/ui.jsx'

const DIMENSIONE = 25

/** Le schede di un tipo di anagrafica: ricerca, nuova scheda, importazione da CSV ed esportazione. */
export default function SchedeAnagrafica() {
  const { tipo: codice } = useParams()
  const uid = useId()
  const toast = useToast()
  const navigate = useNavigate()
  const fileRef = useRef(null)
  const [tipo, setTipo] = useState(null)
  const [testo, setTesto] = useState('')
  const [cercato, setCercato] = useState('')
  const [stato, setStato] = useState('ATTIVA')
  const [pagina, setPagina] = useState(0)
  const [risultato, setRisultato] = useState(null)
  const [errore, setErrore] = useState('')
  const [nuova, setNuova] = useState(false)
  const [esito, setEsito] = useState(null)
  const [importa, setImporta] = useState(false)

  useEffect(() => { api.tipoAnagrafica(codice).then(setTipo).catch(e => setErrore(e.message)) }, [codice])
  // la ricerca parte poco dopo aver smesso di scrivere
  useEffect(() => { const t = setTimeout(() => setCercato(testo.trim()), 300); return () => clearTimeout(t) }, [testo])
  useEffect(() => { setPagina(0) }, [cercato, stato])
  const carica = () => api.cercaSchede(codice, { testo: cercato, stato, pagina, dimensione: DIMENSIONE })
    .then(r => { setRisultato(r); setErrore('') }).catch(e => setErrore(e.message))
  useEffect(() => { if (tipo) void carica() }, [tipo, cercato, stato, pagina])

  const importaFile = async (file) => {
    if (!file) return
    setErrore(''); setEsito(null); setImporta(true)
    try {
      const e = await api.importaSchede(codice, file)
      setEsito(e)
      toast.ok(`Importazione: ${descriviImportazione(e)}`)
      void carica()
    } catch (e) { setErrore(e.message) } finally { setImporta(false); if (fileRef.current) fileRef.current.value = '' }
  }

  if (!tipo) return errore ? <div className="space-y-4"><Indietro /><Errore>{errore}</Errore></div> : <Caricamento />
  const modifica = tipo.azioni.includes('MODIFICA') && tipo.attivo
  const esporta = tipo.azioni.includes('ESPORTA')
  const totale = risultato?.totale ?? 0
  const pagine = Math.max(1, Math.ceil(totale / DIMENSIONE))

  return (
    <div className="in-sequenza space-y-6">
      <Indietro />
      <IntestazionePagina percorso={[{ label: 'Anagrafiche', to: '/anagrafiche' }, { label: tipo.nome }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">{tipo.nome}</h1>
          {tipo.descrizione && <p className="mt-1 max-w-2xl text-grafite">{tipo.descrizione}</p>}
          {!tipo.attivo && <p className="mt-1 text-[15px] text-mattone-700">Anagrafica non attiva: le schede si consultano ma non si creano né si modificano.</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {modifica && <>
            <input ref={fileRef} id={`${uid}-file`} type="file" accept=".csv,text/csv" className="sr-only" tabIndex={-1}
                   onChange={e => importaFile(e.target.files[0])} />
            <Button icona={Upload} attesa={importa} onClick={() => fileRef.current?.click()}>Importa CSV</Button>
          </>}
          {esporta && (
            <Esporta nome={tipo.codice} titolo={tipo.nome} colonne={colonneEsportazione(tipo.definizione)}
                     carica={() => api.esportaSchede(codice).then(e => e.righe)} />
          )}
          {modifica && <Button variante="primario" icona={Plus} onClick={() => setNuova(true)}>Nuova scheda</Button>}
        </div>
      </IntestazionePagina>

      {esito && (
        <Pannello titolo="Importazione" azioni={<button onClick={() => setEsito(null)} className="rounded-md p-1.5 text-grafite hover:bg-carta" aria-label="Chiudi l'esito dell'importazione"><X className="size-5" /></button>}>
          <p role="status">{descriviImportazione(esito)}.</p>
          {esito.ignorate?.length > 0 && <p className="mt-1 text-sm text-grafite">Colonne non importate: {esito.ignorate.join(', ')}.</p>}
          {esito.scartate?.length > 0 && (
            <ul className="mt-2 list-disc space-y-0.5 pl-5 text-sm text-mattone-700">{esito.scartate.map(r => <li key={r}>{r}</li>)}</ul>
          )}
        </Pannello>
      )}
      {modifica && !esito && (
        <p className="text-[13px] text-grafite">
          Il CSV ha una riga di intestazione con le etichette o i nomi dei campi, separati da punto e virgola. Le righe con
          una chiave già presente aggiornano la scheda; una cella vuota svuota il campo. Il file esportato si reimporta così com'è.
        </p>
      )}

      <Pannello corpoClassName="p-0" titolo="Schede" azioni={risultato && <span className="numeri text-sm text-grafite">{totale.toLocaleString('it-IT')}</span>}>
        <div className="flex flex-wrap gap-3 border-b border-linea p-3">
          <label className="relative block min-w-56 flex-1">
            <span className="sr-only">Cerca tra le schede</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-grafite" aria-hidden="true" />
            <input type="search" className="campo pl-9" placeholder="Cerca per nome o per i campi ricercabili" maxLength={100}
                   value={testo} onChange={e => setTesto(e.target.value)} />
          </label>
          <label className="flex items-center gap-2 text-sm text-grafite">
            Mostra
            <select className="campo h-10 w-auto" value={stato} onChange={e => setStato(e.target.value)}>
              {STATI_SCHEDA.map(s => <option key={s.codice} value={s.codice}>{s.nome}</option>)}
            </select>
          </label>
        </div>
        <Errore>{errore}</Errore>
        {!risultato ? <Caricamento /> : risultato.schede.length === 0 ? (
          <Vuoto icona={BookUser} titolo={cercato ? 'Nessun risultato' : 'Nessuna scheda'}>
            {cercato ? 'Prova con un\'altra ricerca.' : modifica ? 'Crea la prima scheda o importa un file CSV.' : 'Non ci sono ancora schede.'}
          </Vuoto>
        ) : (
          <ul className="divide-y divide-linea">
            {risultato.schede.map(s => (
              <li key={s.id}>
                <Link to={`/anagrafiche/${codice}/${s.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-carta">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium text-pino-900">{s.titolo}</span>
                    <span className="block text-[13px] text-grafite">Modificata {quando(s.modificataIl)}</span>
                  </span>
                  {s.stato === 'CESSATA' && <Badge tono="mattone">cessata</Badge>}
                  <ArrowRight className="size-4 text-nebbia" aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        )}
        {totale > DIMENSIONE && (
          <nav aria-label="Pagine" className="flex items-center justify-end gap-2 border-t border-linea px-4 py-2.5 text-sm">
            <button type="button" onClick={() => setPagina(p => p - 1)} disabled={pagina === 0} aria-label="Pagina precedente"
                    className="rounded-md p-1.5 text-pino-800 hover:bg-carta disabled:opacity-30"><ChevronLeft className="size-4" /></button>
            <span className="numeri min-w-20 text-center text-pino-900">Pagina {pagina + 1} di {pagine}</span>
            <button type="button" onClick={() => setPagina(p => p + 1)} disabled={pagina + 1 >= pagine} aria-label="Pagina successiva"
                    className="rounded-md p-1.5 text-pino-800 hover:bg-carta disabled:opacity-30"><ChevronRight className="size-4" /></button>
          </nav>
        )}
      </Pannello>

      {nuova && <NuovaScheda tipo={tipo} onClose={() => setNuova(false)}
                             onCreata={s => { toast.ok(`Scheda ${s.titolo} creata`); navigate(`/anagrafiche/${codice}/${s.id}`) }} />}
    </div>
  )
}

function NuovaScheda({ tipo, onClose, onCreata }) {
  const [attesa, setAttesa] = useState(false)
  const [errori, setErrori] = useState(null)
  const [errore, setErrore] = useState('')
  const crea = async (valori) => {
    setAttesa(true); setErrore('')
    try { onCreata(await api.creaScheda(tipo.codice, valori)) }
    catch (e) { setErrore(e.message); if (e.campi) setErrori(e.campi) }
    finally { setAttesa(false) }
  }
  return (
    <Dialogo aperto onClose={onClose} titolo={`Nuova scheda: ${tipo.nome}`} larghezza="max-w-3xl">
      <div className="space-y-4">
        <Errore>{errore}</Errore>
        <DynamicForm form={{ fields: tipo.definizione.fields, submitLabel: 'Crea la scheda' }} onSubmit={crea} busy={attesa} erroriServer={errori} />
      </div>
    </Dialogo>
  )
}

function Indietro() {
  return (
    <Link to="/anagrafiche" className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
      <ArrowLeft className="size-4" /> Anagrafiche
    </Link>
  )
}
