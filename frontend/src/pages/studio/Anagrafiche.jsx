import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, BookUser, ExternalLink, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { api } from '../../api.js'
import { cx, quando } from '../../lib/format.js'
import { codiceDa } from '../../lib/permessi.js'
import { AZIONI_ANAGRAFICA } from '../../lib/anagrafiche.js'
import FormEditor from '../../components/FormEditor.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Vuoto, useToast, IntestazionePagina } from '../../components/ui.jsx'
import { gl } from '../../lib/glossario.js'

const NUOVO = { codice: '', nome: '', descrizione: '', attivo: true, definizione: { fields: [] } }

/**
 * Studio → Anagrafiche: l'analista definisce i tipi di anagrafica (fornitori, fondi, immobili...), con i campi
 * come nei form più chiave, ricercabili e collegamenti, e decide chi consulta, modifica ed esporta le schede.
 * Le schede si gestiscono nella sezione Anagrafiche, fuori dallo Studio.
 */
export default function AnagraficheStudio() {
  const toast = useToast()
  const [lista, setLista] = useState(null)
  const [tipo, setTipo] = useState(null)        // il tipo aperto (NUOVO per uno nuovo)
  const [errore, setErrore] = useState('')
  const [accesso, setAccesso] = useState(false)

  const carica = () => api.tipiAnagraficaAdmin().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])

  const apri = async (codice) => {
    setErrore('')
    try { setTipo(await api.tipoAnagraficaAdmin(codice)) } catch (e) { setErrore(e.message) }
  }

  const elimina = async () => {
    if (!confirm(`Eliminare l'anagrafica ${tipo.nome}? Si può solo se non ha schede e nessun form la usa.`)) return
    try {
      await api.eliminaTipoAnagrafica(tipo.codice)
      toast.ok(`Anagrafica ${tipo.nome} eliminata`)
      setTipo(null); void carica()
    } catch (e) { setErrore(e.message) }
  }

  if (tipo) return (
    <EditorTipo key={tipo.codice || 'nuovo'} tipo={tipo} errore={errore} setErrore={setErrore}
                onIndietro={() => { setTipo(null); setErrore(''); void carica() }}
                onSalvato={(t) => { setTipo(t); void carica() }}
                onAccesso={() => setAccesso(true)} onElimina={elimina}>
      {accesso && <AccessoAnagrafica aperto={accesso} onClose={() => setAccesso(false)} tipo={tipo} />}
    </EditorTipo>
  )

  return (
    <div className="in-sequenza space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Anagrafiche' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Anagrafiche</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Elenchi di soggetti e oggetti che {gl('pratica', 'i')} usano: fornitori, fondi, immobili, contratti. Per ognuno
            decidi i campi, quali identificano la scheda, quali si cercano e chi può consultarle o modificarle.
          </p>
        </div>
        <Button variante="primario" icona={Plus} onClick={() => { setErrore(''); setTipo({ ...NUOVO }) }}>Nuova anagrafica</Button>
      </IntestazionePagina>
      <Errore>{errore}</Errore>

      {!lista ? (errore ? null : <Caricamento />) : lista.length === 0 ? (
        <Vuoto icona={BookUser} titolo="Nessuna anagrafica"
               azione={<Button variante="primario" icona={Plus} onClick={() => setTipo({ ...NUOVO })}>Crea la prima</Button>}>
          Crea un tipo di anagrafica, poi usalo nei form con il campo Anagrafica.
        </Vuoto>
      ) : (
        <Pannello corpoClassName="p-0" titolo="Tipi di anagrafica" azioni={<span className="numeri text-sm text-grafite">{lista.length}</span>}>
          <ul className="divide-y divide-linea">
            {lista.map(t => (
              <li key={t.codice}>
                <button onClick={() => apri(t.codice)}
                        className={cx('flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-carta', !t.attivo && 'opacity-60')}>
                  <BookUser className="size-[18px] shrink-0 text-pino-600" aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium text-pino-900">{t.nome}</span>
                    <span className="block truncate text-[13px] text-grafite">
                      <span className="font-mono">{t.codice}</span>{t.descrizione ? `, ${t.descrizione}` : ''}
                    </span>
                  </span>
                  {!t.attivo && <Badge tono="mattone">non attiva</Badge>}
                  <span className="numeri text-right text-[13px] text-grafite">
                    {t.schede ?? 0} {t.schede === 1 ? 'scheda' : 'schede'}
                    {t.modificatoIl && <span className="block">modificata {quando(t.modificatoIl)}</span>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Pannello>
      )}
    </div>
  )
}

/** Nome, codice, descrizione e campi di un tipo; il pulsante Salva dei campi salva tutto insieme. */
function EditorTipo({ tipo, errore, setErrore, onIndietro, onSalvato, onAccesso, onElimina, children }) {
  const uid = useId()
  const toast = useToast()
  const nuovo = !tipo.codice
  const [meta, setMeta] = useState({ codice: tipo.codice || '', nome: tipo.nome || '', descrizione: tipo.descrizione || '', attivo: tipo.attivo !== false })
  const set = (k, v) => setMeta(m => ({ ...m, [k]: v }))
  const codice = nuovo ? (meta.codice || codiceDa(meta.nome)) : tipo.codice

  const salva = async (definizione) => {
    setErrore('')
    if (!meta.nome.trim()) { const e = new Error('Indica il nome dell\'anagrafica'); document.getElementById(`${uid}-n`)?.focus(); throw e }
    const dati = { codice, nome: meta.nome.trim(), descrizione: meta.descrizione.trim() || null, attivo: meta.attivo, definizione }
    const salvato = nuovo ? await api.creaTipoAnagrafica(dati) : await api.modificaTipoAnagrafica(tipo.codice, dati)
    toast.ok(`Anagrafica ${salvato.nome} salvata`)
    if (nuovo) onSalvato(salvato)
  }

  return (
    <div className="space-y-5">
      <button onClick={onIndietro} className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
        <ArrowLeft className="size-4" /> Tutte le anagrafiche
      </button>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[30px] font-semibold leading-tight">{nuovo ? 'Nuova anagrafica' : tipo.nome}</h1>
          {!nuovo && <p className="mt-1 text-grafite">Versione {tipo.versione}, {tipo.schede ?? 0} {tipo.schede === 1 ? 'scheda' : 'schede'}</p>}
        </div>
        {!nuovo && (
          <div className="flex flex-wrap gap-2">
            <Link to={`/anagrafiche/${tipo.codice}`} className="inline-flex h-10 items-center gap-2 rounded-campo border border-linea bg-white px-4 text-[15px] font-medium text-pino-900 hover:bg-carta">
              <ExternalLink className="size-4" aria-hidden="true" /> Apri le schede
            </Link>
            <Button icona={ShieldCheck} onClick={onAccesso}>Chi può accedere</Button>
            {!tipo.schede && <Button variante="pericolo" icona={Trash2} onClick={onElimina}>Elimina</Button>}
          </div>
        )}
      </header>
      <Errore>{errore}</Errore>

      <Pannello titolo="Anagrafica">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="etichetta" htmlFor={`${uid}-n`}>Nome *</label>
            <input id={`${uid}-n`} className="campo" required aria-required="true" value={meta.nome} maxLength={200}
                   onChange={e => set('nome', e.target.value)} placeholder="Es. Fornitori" />
          </div>
          <div>
            <label className="etichetta" htmlFor={`${uid}-c`}>Codice</label>
            <input id={`${uid}-c`} className="campo font-mono text-sm" readOnly={!nuovo} aria-describedby={`${uid}-ca`}
                   value={codice} onChange={e => set('codice', e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} />
            <p id={`${uid}-ca`} className="mt-1 text-xs text-grafite">
              {nuovo ? 'Minuscole, numeri e trattini. Non si cambia più: lo usano i form e l\'API.' : 'Il codice non si cambia.'}
            </p>
          </div>
          <div className="sm:col-span-2">
            <label className="etichetta" htmlFor={`${uid}-d`}>Descrizione</label>
            <input id={`${uid}-d`} className="campo" value={meta.descrizione} maxLength={1000}
                   onChange={e => set('descrizione', e.target.value)} placeholder="A cosa serve, chi la tiene aggiornata" />
          </div>
          <label className="flex items-center gap-2 text-[15px] sm:col-span-2">
            <input type="checkbox" className="size-4 accent-verde-600" checked={meta.attivo} onChange={e => set('attivo', e.target.checked)} />
            Attiva: le schede si possono creare e scegliere nei form
          </label>
        </div>
        <p className="mt-3 text-[13px] text-grafite">Nome, codice e descrizione si salvano con il pulsante Salva dei campi, qui sotto.</p>
      </Pannello>

      <FormEditor anagrafica={{ definizione: tipo.definizione, onSalva: salva }} />
      {children}
    </div>
  )
}

/** Chi consulta, modifica ed esporta le schede di un tipo: regole per gruppo o ruolo, valgono subito. */
function AccessoAnagrafica({ aperto, onClose, tipo }) {
  const uid = useId()
  const toast = useToast()
  const [regole, setRegole] = useState(null)
  const [gruppi, setGruppi] = useState([])
  const [ruoli, setRuoli] = useState([])
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  useEffect(() => {
    api.permessiAnagrafica(tipo.codice).then(setRegole).catch(e => setErrore(e.message))
    api.gruppi().then(g => setGruppi((g || []).map(x => x.nome))).catch(() => {})
    api.ruoli().then(setRuoli).catch(() => setRuoli([]))
  }, [tipo.codice])

  const aggiungi = (azione) => setRegole(r => [...r, { azione, tipoSoggetto: 'GRUPPO', soggetto: '' }])
  const cambia = (i, k, v) => setRegole(r => r.map((x, j) => j === i ? { ...x, [k]: v } : x))
  const togli = (i) => setRegole(r => r.filter((_, j) => j !== i))

  const salva = async () => {
    setAttesa(true); setErrore('')
    try {
      await api.salvaPermessiAnagrafica(tipo.codice, regole.filter(r => r.soggetto.trim()).map(r => ({ ...r, soggetto: r.soggetto.trim() })))
      toast.ok('Accesso all\'anagrafica salvato')
      onClose()
    } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }

  return (
    <Dialogo aperto={aperto} onClose={onClose} titolo={`Chi può accedere: ${tipo.nome}`}
             sottotitolo={'Vale subito. Senza regole le schede le vede solo l\'amministratore. Agli utenti esterni vale solo "sceglie": non vedono mai i dati delle schede.'}>
      {!regole ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : (
        <div className="space-y-6">
          <datalist id={`${uid}-gruppi`}>{['esterni', ...gruppi.filter(g => g !== 'esterni')].map(g => <option key={g} value={g} />)}</datalist>
          <datalist id={`${uid}-ruoli`}>{ruoli.map(r => <option key={r.codice} value={r.codice}>{r.nome}</option>)}</datalist>
          {AZIONI_ANAGRAFICA.map(a => {
            const righe = regole.map((r, i) => ({ r, i })).filter(x => x.r.azione === a.codice)
            return (
              <fieldset key={a.codice} className="space-y-2">
                <legend className="text-[15px] font-semibold text-pino-900">{a.titolo}</legend>
                <p className="text-[13px] text-grafite">{a.aiuto}</p>
                {righe.length === 0 && <p className="rounded-campo bg-carta px-3 py-2 text-sm text-grafite">Nessuno</p>}
                {righe.map(({ r, i }) => (
                  <div key={i} className="flex flex-wrap items-center gap-2 rounded-campo bg-carta px-3 py-2">
                    <select className="campo h-9 w-auto py-0" aria-label="Gruppo o ruolo" value={r.tipoSoggetto}
                            onChange={e => cambia(i, 'tipoSoggetto', e.target.value)}>
                      <option value="GRUPPO">Gruppo</option>
                      <option value="RUOLO">Ruolo</option>
                    </select>
                    <input className="campo h-9 min-w-0 flex-1" aria-label={r.tipoSoggetto === 'RUOLO' ? 'Codice del ruolo' : 'Nome del gruppo'}
                           list={r.tipoSoggetto === 'RUOLO' ? `${uid}-ruoli` : `${uid}-gruppi`}
                           placeholder={r.tipoSoggetto === 'RUOLO' ? 'es. analista' : 'es. operatori'}
                           value={r.soggetto} onChange={e => cambia(i, 'soggetto', e.target.value)} />
                    <button type="button" onClick={() => togli(i)} className="rounded-md p-1.5 text-grafite hover:bg-white hover:text-mattone-600"
                            aria-label="Togli la regola"><Trash2 className="size-4" /></button>
                  </div>
                ))}
                <Button type="button" misura="sm" variante="fantasma" icona={Plus} onClick={() => aggiungi(a.codice)}>Aggiungi</Button>
              </fieldset>
            )
          })}
          <Errore>{errore}</Errore>
          <div className="flex justify-end gap-2 border-t border-linea pt-4">
            <Button variante="fantasma" onClick={onClose}>Annulla</Button>
            <Button variante="primario" attesa={attesa} onClick={salva}>Salva</Button>
          </div>
        </div>
      )}
    </Dialogo>
  )
}
