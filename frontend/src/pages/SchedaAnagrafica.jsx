import { useCallback, useEffect, useId, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Archive, ArchiveRestore, ArrowLeft, ArrowRight, BookUser, Workflow } from 'lucide-react'
import { api } from '../api.js'
import { quando } from '../lib/format.js'
import { eSezione } from '../lib/formCondizioni.js'
import { campiCambiati, valoriScheda } from '../lib/anagrafiche.js'
import DynamicForm from '../components/DynamicForm.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Schede, useToast } from '../components/ui.jsx'
import { gl } from '../lib/glossario.js'

const OPERAZIONI = { CREATA: 'Creata', MODIFICATA: 'Modificata', CESSATA: 'Cessata', RIATTIVATA: 'Riattivata' }

/** Una scheda di anagrafica: dati (modificabili da chi può), versioni, schede collegate e pratiche che la usano. */
export default function SchedaAnagrafica() {
  const { tipo: codice, id } = useParams()
  const uid = useId()
  const toast = useToast()
  const [tipo, setTipo] = useState(null)
  const [scheda, setScheda] = useState(null)
  const [errore, setErrore] = useState('')
  const [vista, setVista] = useState('dati')
  const [nota, setNota] = useState('')
  const [attesa, setAttesa] = useState(false)
  const [errori, setErrori] = useState(null)
  const [cambioStato, setCambioStato] = useState(false)
  const [giro, setGiro] = useState(0)   // ricrea il form dopo il salvataggio

  const carica = useCallback(() => Promise.all([api.tipoAnagrafica(codice), api.scheda(codice, id)])
    .then(([t, s]) => { setTipo(t); setScheda(s); setErrore('') }).catch(e => setErrore(e.message)), [codice, id])
  useEffect(() => { void carica() }, [carica])

  const salva = async (valori) => {
    setAttesa(true); setErrore(''); setErrori(null)
    try {
      const s = await api.modificaScheda(codice, id, scheda.versione, valori, nota.trim() || null)
      setScheda(s); setNota(''); setGiro(g => g + 1)
      toast.ok(s.versione === scheda.versione ? 'Nessun cambiamento da salvare' : 'Scheda salvata')
    } catch (e) {
      setErrore(e.stato === 409 ? `${e.message}. Le modifiche non sono state salvate.` : e.message)
      if (e.campi) setErrori(e.campi)
    } finally { setAttesa(false) }
  }

  if (!scheda || !tipo) return errore ? <div className="space-y-4"><Indietro codice={codice} /><Errore>{errore}</Errore></div> : <Caricamento />
  const modifica = scheda.azioni.includes('MODIFICA') && tipo.attivo
  const attiva = scheda.stato === 'ATTIVA'
  const collegamenti = Object.entries(scheda.collegamenti || {})
  const etichetta = Object.fromEntries((tipo.definizione.fields || []).map(f => [f.name, f.label || f.name]))

  return (
    <div className="space-y-6">
      <Indietro codice={codice} nome={tipo.nome} />
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-grafite">{tipo.nome}</p>
          <h1 className="flex flex-wrap items-center gap-3 text-[30px] font-semibold leading-tight">
            {scheda.titolo}
            {!attiva && <Badge tono="mattone">cessata</Badge>}
          </h1>
          <p className="mt-1 text-[15px] text-grafite">
            Versione {scheda.versione + 1}, modificata {quando(scheda.modificataIl)}{scheda.modificataDa ? ` da ${scheda.modificataDa}` : ''}
          </p>
        </div>
        {modifica && (
          <Button variante={attiva ? 'pericolo' : 'secondario'} icona={attiva ? Archive : ArchiveRestore} onClick={() => setCambioStato(true)}>
            {attiva ? 'Cessa la scheda' : 'Riattiva la scheda'}
          </Button>
        )}
      </header>

      <Schede attiva={vista} onChange={setVista} voci={[
        { id: 'dati', label: 'Dati' },
        { id: 'versioni', label: 'Versioni', conteggio: scheda.versione + 1 },
        { id: 'collegamenti', label: 'Collegamenti', conteggio: collegamenti.length + (scheda.citataDa || []).length }
      ]} />

      {vista === 'dati' && (
        <Pannello>
          <Errore>{errore}</Errore>
          {modifica && attiva ? (
            <DynamicForm key={`${scheda.versione}-${giro}`} form={{ fields: tipo.definizione.fields, submitLabel: 'Salva le modifiche' }}
                         initialValues={valoriScheda(scheda)} onSubmit={salva} busy={attesa} erroriServer={errori}>
              <div className="border-t border-linea pt-5">
                <label className="etichetta" htmlFor={`${uid}-nota`}>Nota sulla modifica</label>
                <input id={`${uid}-nota`} className="campo" maxLength={500} value={nota} onChange={e => setNota(e.target.value)}
                       placeholder="Facoltativa, resta nelle versioni. Es. aggiornato l'indirizzo dalla visura" />
              </div>
            </DynamicForm>
          ) : (
            <>
              <DynamicForm form={{ fields: tipo.definizione.fields.map(f => eSezione(f) ? f : { ...f, readonly: true }) }}
                           initialValues={valoriScheda(scheda)} disabled onSubmit={() => {}} />
              {!attiva && <p className="mt-4 text-[15px] text-grafite">La scheda è cessata: non si modifica e non si sceglie nei form.</p>}
            </>
          )}
        </Pannello>
      )}

      {vista === 'versioni' && <Versioni codice={codice} id={id} tipo={tipo} />}

      {vista === 'collegamenti' && (
        <div className="grid items-start gap-6 lg:grid-cols-2">
          <Pannello titolo="Questa scheda indica">
            {collegamenti.length === 0 ? <p className="text-sm text-grafite">Nessuna scheda collegata.</p> : (
              <ul className="space-y-2">
                {collegamenti.map(([campo, c]) => (
                  <Voce key={campo} etichetta={etichetta[campo] || campo} titolo={c.visibile ? c.titolo : `Scheda n. ${c.id} (non visibile)`}
                        cessata={c.stato === 'CESSATA'} to={c.visibile ? `/anagrafiche/${c.tipo}/${c.id}` : null} />
                ))}
              </ul>
            )}
          </Pannello>
          <Pannello titolo="La citano">
            {(scheda.citataDa || []).length === 0 ? <p className="text-sm text-grafite">Nessuna scheda la cita.</p> : (
              <ul className="space-y-2">
                {scheda.citataDa.map(c => (
                  <Voce key={`${c.tipo}-${c.id}-${c.campo}`} etichetta={`${c.tipoNome}, ${c.etichetta}`} titolo={c.titolo}
                        cessata={c.stato === 'CESSATA'} to={`/anagrafiche/${c.tipo}/${c.id}`} />
                ))}
              </ul>
            )}
          </Pannello>
          <PraticheCollegate codice={codice} id={id} />
        </div>
      )}

      {cambioStato && <CambioStato scheda={scheda} codice={codice} onClose={() => setCambioStato(false)}
                                   onFatto={s => { setScheda(s); setCambioStato(false); setGiro(g => g + 1) }} />}
    </div>
  )
}

function Voce({ etichetta, titolo, cessata, to, icona: Icona = BookUser }) {
  const corpo = (
    <>
      <Icona className="size-4 shrink-0 text-pino-700" aria-hidden="true" />
      <span className="min-w-0 flex-1">
        <span className="block text-[13px] text-grafite">{etichetta}</span>
        <span className="block truncate text-[15px] font-medium text-pino-900">{titolo}</span>
      </span>
      {cessata && <Badge tono="mattone">cessata</Badge>}
      {to && <ArrowRight className="size-4 text-nebbia" aria-hidden="true" />}
    </>
  )
  return (
    <li>
      {to ? <Link to={to} className="flex items-center gap-3 rounded-campo border border-linea bg-white px-3 py-2.5 hover:border-pino-200">{corpo}</Link>
        : <div className="flex items-center gap-3 rounded-campo border border-linea bg-carta px-3 py-2.5">{corpo}</div>}
    </li>
  )
}

/** Le pratiche che hanno scelto questa scheda, tra quelle che l'utente vede. */
function PraticheCollegate({ codice, id }) {
  const [pratiche, setPratiche] = useState(null)
  const [errore, setErrore] = useState('')
  useEffect(() => { api.praticheScheda(codice, id).then(setPratiche).catch(e => setErrore(e.message)) }, [codice, id])
  return (
    <Pannello titolo={`${gl('pratica', 'Nomi')} che la usano`} className="lg:col-span-2">
      {!pratiche ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : pratiche.length === 0 ? (
        <p className="text-sm text-grafite">Nessun elemento che puoi vedere tra {gl('pratica', 'i')} usa questa scheda.</p>
      ) : (
        <ul className="space-y-2">
          {pratiche.map(p => (
            <Voce key={`${p.id}-${p.campo}`} icona={Workflow} etichetta={`${p.processo}, ${p.conclusa ? 'conclusa' : 'in corso'}, dal ${quando(p.collegataIl)}`}
                  titolo={p.titolo || p.id} to={`/pratiche/${p.id}`} />
          ))}
        </ul>
      )}
    </Pannello>
  )
}

/** Le versioni della scheda, dalla più recente, con chi l'ha cambiata, la nota e i campi cambiati. */
function Versioni({ codice, id, tipo }) {
  const [versioni, setVersioni] = useState(null)
  const [errore, setErrore] = useState('')
  useEffect(() => { api.versioniScheda(codice, id).then(setVersioni).catch(e => setErrore(e.message)) }, [codice, id])
  if (!versioni) return errore ? <Errore>{errore}</Errore> : <Caricamento />
  const perNumero = Object.fromEntries(versioni.map(v => [v.numero, v]))
  return (
    <Pannello corpoClassName="p-0">
      <ol className="divide-y divide-linea">
        {versioni.map(v => {
          const cambiati = v.operazione === 'MODIFICATA' ? campiCambiati(tipo.definizione, perNumero[v.numero - 1]?.dati, v.dati) : []
          return (
            <li key={v.numero} className="px-5 py-3">
              <p className="flex flex-wrap items-baseline gap-x-2 text-[15px]">
                <span className="numeri font-semibold text-pino-900">Versione {v.numero}</span>
                <span className="text-pino-900">{OPERAZIONI[v.operazione] || v.operazione}</span>
                <span className="text-grafite">{quando(v.il)}{v.utenteNome || v.utente ? `, ${v.utenteNome || v.utente}` : ''}</span>
              </p>
              {cambiati.length > 0 && <p className="mt-0.5 text-[13px] text-grafite">Cambiati: {cambiati.join(', ')}</p>}
              {v.nota && <p className="mt-0.5 text-[13px] italic text-grafite">{v.nota}</p>}
            </li>
          )
        })}
      </ol>
    </Pannello>
  )
}

function CambioStato({ scheda, codice, onClose, onFatto }) {
  const uid = useId()
  const toast = useToast()
  const [nota, setNota] = useState('')
  const [attesa, setAttesa] = useState(false)
  const [errore, setErrore] = useState('')
  const cessa = scheda.stato === 'ATTIVA'
  const conferma = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      const s = cessa ? await api.cessaScheda(codice, scheda.id, scheda.versione, nota.trim() || null)
        : await api.riattivaScheda(codice, scheda.id, scheda.versione, nota.trim() || null)
      toast.ok(cessa ? 'Scheda cessata' : 'Scheda riattivata')
      onFatto(s)
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }
  return (
    <Dialogo aperto onClose={onClose} titolo={cessa ? `Cessare ${scheda.titolo}?` : `Riattivare ${scheda.titolo}?`}
             sottotitolo={cessa ? 'La scheda resta consultabile con le sue versioni, ma non si modifica e non si sceglie più nei form. Si può riattivare.'
               : 'La scheda torna modificabile e si può di nuovo scegliere nei form.'}>
      <form onSubmit={conferma} className="space-y-4">
        <div>
          <label className="etichetta" htmlFor={`${uid}-n`}>Motivo</label>
          <input id={`${uid}-n`} className="campo" maxLength={500} value={nota} onChange={e => setNota(e.target.value)}
                 placeholder={cessa ? 'Es. società cancellata dal registro delle imprese' : 'Facoltativo'} />
        </div>
        <Errore>{errore}</Errore>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button type="button" variante="fantasma" onClick={onClose}>Annulla</Button>
          <Button type="submit" variante={cessa ? 'pericolo' : 'primario'} attesa={attesa}>{cessa ? 'Cessa' : 'Riattiva'}</Button>
        </div>
      </form>
    </Dialogo>
  )
}

function Indietro({ codice, nome }) {
  return (
    <Link to={`/anagrafiche/${codice}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
      <ArrowLeft className="size-4" /> {nome || 'Anagrafica'}
    </Link>
  )
}
