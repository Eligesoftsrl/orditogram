import { useEffect, useState } from 'react'
import { Ban, ChevronDown, KeyRound, Pause, Pencil, Play, Plus, RefreshCw, ShieldAlert, TriangleAlert } from 'lucide-react'
import { api } from '../../api.js'
import { cx, fa, giorno, quando } from '../../lib/format.js'
import { Badge, Button, Caricamento, Dialogo, Errore, Vuoto, useToast } from '../ui.jsx'
import { Caselle, Codice, Copia, SceltaProcessi } from './comuni.jsx'

function stato(c) {
  if (c.revocata) return ['Revocata', 'mattone']
  if (!c.attiva) return ['Sospesa', 'zafferano']
  if (!c.valida) return ['Scaduta', 'mattone']
  return ['Attiva', 'verde']
}

/** Chiavi dell'API pubblica: ogni sistema esterno ha la sua, con i suoi permessi. */
export default function ChiaviApi({ panoramica }) {
  const [lista, setLista] = useState(null)
  const [errore, setErrore] = useState('')
  const [modifica, setModifica] = useState(null)      // { nuova: true } | chiave
  const [valore, setValore] = useState(null)          // { chiave, valore, nuova }
  const [revocateAperte, setRevocateAperte] = useState(false)
  const toast = useToast()

  const carica = () => api.chiaviApi().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { carica() }, [])

  const esegui = async (fn, ok) => {
    try { const r = await fn(); if (ok) toast.ok(ok); await carica(); return r } catch (e) { toast.errore(e.message) }
  }
  const ruota = async (c) => {
    if (!confirm(`Generare un nuovo valore per "${c.nome}"? Quello attuale smette subito di funzionare: il sistema che lo usa va aggiornato.`)) return
    const r = await esegui(() => api.ruotaChiaveApi(c.id))
    if (r) setValore({ ...r, nuova: false })
  }
  const revoca = (c) => {
    if (!confirm(`Revocare definitivamente la chiave "${c.nome}"? Non si potrà riattivare.`)) return
    esegui(() => api.revocaChiaveApi(c.id), 'Chiave revocata')
  }
  const sospendi = (c) => esegui(() => api.modificaChiaveApi(c.id, {
    descrizione: c.descrizione, permessi: c.permessi, processi: c.processi, scadenza: c.scadenza, attiva: !c.attiva
  }), c.attiva ? 'Chiave sospesa' : 'Chiave riattivata')

  const attive = (lista || []).filter(c => !c.revocata)
  const revocate = (lista || []).filter(c => c.revocata)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <p className="max-w-2xl text-grafite">
          Ogni sistema che usa l'API (portale, gestionale, app) ha la sua chiave, con i permessi che servono e,
          se vuoi, solo per alcuni processi. Nello storico delle pratiche compare il nome del sistema.
        </p>
        <Button variante="primario" icona={Plus} onClick={() => setModifica({ nuova: true })}>Nuova chiave</Button>
      </div>

      <Errore>{errore}</Errore>
      {!lista && !errore && <Caricamento />}
      {lista && attive.length === 0 && (
        <div className="rounded-pannello border border-dashed border-linea bg-white">
          <Vuoto icona={KeyRound} titolo="Nessuna chiave"
                 azione={<Button variante="primario" icona={Plus} onClick={() => setModifica({ nuova: true })}>Crea la prima chiave</Button>}>
            Crea una chiave per il primo sistema che deve avviare pratiche o leggerne lo stato.
          </Vuoto>
        </div>
      )}

      <ul className="space-y-3">
        {attive.map(c => <Riga key={c.id} c={c} panoramica={panoramica} onModifica={() => setModifica(c)}
                               onRuota={() => ruota(c)} onSospendi={() => sospendi(c)} onRevoca={() => revoca(c)} />)}
      </ul>

      {revocate.length > 0 && (
        <section>
          <button type="button" onClick={() => setRevocateAperte(!revocateAperte)}
                  className="inline-flex items-center gap-1.5 text-sm font-medium text-grafite hover:text-pino-900">
            <ChevronDown className={cx('size-4 transition-transform', !revocateAperte && '-rotate-90')} />
            Chiavi revocate ({revocate.length})
          </button>
          {revocateAperte && <ul className="mt-3 space-y-3 opacity-75">{revocate.map(c => <Riga key={c.id} c={c} panoramica={panoramica} />)}</ul>}
        </section>
      )}

      {panoramica.sistemiConfigurati?.length > 0 && (
        <p className="flex items-start gap-2 rounded-campo border border-linea bg-white px-4 py-3 text-sm text-grafite">
          <ShieldAlert className="mt-0.5 size-4 shrink-0 text-pino-600" />
          <span>Configurati sul server (<code className="font-mono text-pino-900">app.messaggi.chiavi</code>), solo per inviare messaggi:{' '}
            {panoramica.sistemiConfigurati.map(s => <code key={s} className="mr-1 rounded bg-carta px-1 font-mono text-pino-900">{s}</code>)}.
            Puoi sostituirli con chiavi create qui, che si sospendono e si revocano senza riavviare.</span>
        </p>
      )}

      {modifica && (
        <DialogoChiave chiave={modifica.nuova ? null : modifica} panoramica={panoramica} onClose={() => setModifica(null)}
                       onSalvata={(r) => { setModifica(null); carica(); if (r?.valore) setValore({ ...r, nuova: true }) }} />
      )}
      {valore && <DialogoValore {...valore} panoramica={panoramica} onClose={() => setValore(null)} />}
    </div>
  )
}

function Riga({ c, panoramica, onModifica, onRuota, onSospendi, onRevoca }) {
  const [etichetta, tono] = stato(c)
  const nomiProcessi = Object.fromEntries((panoramica.processi || []).map(p => [p.chiave, p.nome]))
  return (
    <li className="rounded-pannello border border-linea bg-white p-4 shadow-pannello sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className={cx('mt-0.5 inline-flex size-10 shrink-0 items-center justify-center rounded-xl',
            tono === 'verde' ? 'bg-verde-50 text-verde-700' : 'bg-carta text-grafite')}>
            <KeyRound className="size-5" />
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-mono text-[16px] font-semibold text-pino-900">{c.nome}</h3>
              <Badge tono={tono}>{etichetta}</Badge>
              {c.scadenza && !c.revocata && <span className="text-xs text-grafite">scade il {giorno(c.scadenza)}</span>}
            </div>
            {c.descrizione && <p className="mt-0.5 text-sm text-grafite">{c.descrizione}</p>}
            <p className="mt-1 font-mono text-[13px] text-nebbia">{c.prefisso}<span className="tracking-widest">••••••••••••</span></p>
          </div>
        </div>
        <div className="text-right text-xs text-grafite">
          {c.ultimoUso
            ? <span title={quando(c.ultimoUso)}>Usata {fa(c.ultimoUso)}{c.ultimoIndirizzo && <> da <span className="font-mono">{c.ultimoIndirizzo}</span></>}</span>
            : <span className="text-nebbia">Mai usata</span>}
          <br /><span className="text-nebbia">creata {fa(c.creata)}{c.creataDa && ` da ${c.creataDa}`}</span>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-1.5">
        {c.permessi.map(p => (
          <span key={p} title={panoramica.permessi?.[p]} className="rounded-full bg-pino-900/5 px-2.5 py-0.5 font-mono text-[12px] text-pino-800 ring-1 ring-inset ring-linea">{p}</span>
        ))}
        <span className="mx-1 text-nebbia">·</span>
        {c.processi.length === 0
          ? <span className="text-sm text-grafite">tutti i processi</span>
          : c.processi.map(p => <span key={p} className="rounded-full bg-zafferano-50 px-2.5 py-0.5 text-[12px] text-zafferano-800 ring-1 ring-inset ring-zafferano-100" title={p}>{nomiProcessi[p] || p}</span>)}
      </div>

      {!c.revocata && (
        <div className="mt-4 flex flex-wrap gap-2 border-t border-linea pt-3">
          <Button misura="sm" icona={Pencil} onClick={onModifica}>Modifica</Button>
          <Button misura="sm" icona={RefreshCw} onClick={onRuota}>Nuovo valore</Button>
          <Button misura="sm" icona={c.attiva ? Pause : Play} onClick={onSospendi}>{c.attiva ? 'Sospendi' : 'Riattiva'}</Button>
          <Button misura="sm" variante="pericolo" icona={Ban} onClick={onRevoca} className="sm:ml-auto">Revoca</Button>
        </div>
      )}
    </li>
  )
}

function DialogoChiave({ chiave, panoramica, onClose, onSalvata }) {
  const [nome, setNome] = useState(chiave?.nome || '')
  const [descrizione, setDescrizione] = useState(chiave?.descrizione || '')
  const [permessi, setPermessi] = useState(chiave?.permessi || ['pratiche:avvia', 'pratiche:leggi'])
  const [processi, setProcessi] = useState(chiave?.processi || [])
  const [scadenza, setScadenza] = useState(chiave?.scadenza || '')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const salva = async (e) => {
    e.preventDefault()
    setErrore(''); setAttesa(true)
    const dati = { nome, descrizione, permessi, processi, scadenza: scadenza || null, attiva: chiave ? chiave.attiva : true }
    try {
      onSalvata(chiave ? await api.modificaChiaveApi(chiave.id, dati) : await api.creaChiaveApi(dati))
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }
  return (
    <Dialogo aperto onClose={onClose} titolo={chiave ? `Chiave ${chiave.nome}` : 'Nuova chiave API'}
             sottotitolo={chiave ? 'I permessi cambiano subito, senza cambiare il valore della chiave.' : 'Il valore si vede una volta sola, appena creata.'}>
      <form onSubmit={salva} className="space-y-5">
        {!chiave && (
          <label className="block">
            <span className="etichetta">Nome del sistema</span>
            <input className="campo font-mono" value={nome} onChange={e => setNome(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-'))}
                   placeholder="portale-cittadino" required autoFocus maxLength={60} />
            <span className="mt-1 block text-xs text-grafite">Minuscole, numeri e trattini. Compare nello storico delle pratiche.</span>
          </label>
        )}
        <label className="block">
          <span className="etichetta">Descrizione <span className="font-normal text-nebbia">(facoltativa)</span></span>
          <input className="campo" value={descrizione} onChange={e => setDescrizione(e.target.value)} placeholder="Es. Portale dei servizi online, fornitore Rossi Srl" />
        </label>
        <div>
          <span className="etichetta">Permessi</span>
          <Caselle voci={Object.entries(panoramica.permessi || {}).map(([id, aiuto]) => ({ id, label: id, aiuto }))}
                   valori={permessi} onChange={setPermessi} />
        </div>
        <div>
          <span className="etichetta">Processi</span>
          <SceltaProcessi processi={panoramica.processi || []} valori={processi} onChange={setProcessi} />
        </div>
        <label className="block sm:w-64">
          <span className="etichetta">Scadenza <span className="font-normal text-nebbia">(facoltativa)</span></span>
          <input type="date" className="campo" value={scadenza} onChange={e => setScadenza(e.target.value)} />
        </label>
        <Errore>{errore}</Errore>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button type="button" onClick={onClose}>Annulla</Button>
          <Button type="submit" variante="primario" attesa={attesa} disabled={!permessi.length}>{chiave ? 'Salva' : 'Crea la chiave'}</Button>
        </div>
      </form>
    </Dialogo>
  )
}

function DialogoValore({ chiave, valore, nuova, panoramica, onClose }) {
  const base = `${panoramica.indirizzoBase}/api/v1`
  return (
    <Dialogo aperto onClose={onClose} titolo={nuova ? 'Chiave creata' : 'Nuovo valore generato'}
             sottotitolo={`Sistema ${chiave.nome}`}>
      <div className="space-y-4">
        <div className="flex items-start gap-2.5 rounded-campo border border-zafferano-100 bg-zafferano-50 px-3.5 py-3 text-sm text-zafferano-800">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" />
          <span><strong>Copiala adesso.</strong> Orditogram conserva solo un'impronta della chiave: chiudendo questa finestra non sarà più possibile rivederla. Se la perdi, genera un nuovo valore.</span>
        </div>
        <div className="flex items-center gap-2 rounded-campo border border-linea bg-carta p-2 pl-3">
          <code className="min-w-0 flex-1 break-all font-mono text-[14px] text-pino-900">{valore}</code>
          <Copia testo={valore} etichetta="Copia la chiave" />
        </div>
        <div>
          <p className="etichetta">Prova subito</p>
          <Codice titolo="cURL" codice={`curl ${base}/io \\\n  -H 'X-API-Key: ${valore}'`} />
        </div>
        <div className="flex justify-end border-t border-linea pt-4">
          <Button variante="primario" onClick={onClose}>Ho copiato la chiave</Button>
        </div>
      </div>
    </Dialogo>
  )
}
