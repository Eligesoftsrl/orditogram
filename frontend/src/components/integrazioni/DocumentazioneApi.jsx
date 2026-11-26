import { useEffect, useMemo, useState } from 'react'
import { Download, KeyRound, Play, ShieldCheck, Webhook as IconaWebhook } from 'lucide-react'
import { api, chiamaApiPubblica } from '../../api.js'
import { cx } from '../../lib/format.js'
import { VERIFICA_FIRMA, campi, codice, compilaPercorso, esempio, sezioni } from '../../lib/openapi.js'
import { Badge, Button, Caricamento, Errore } from '../ui.jsx'
import { Codice, CodiceASchede, Copia, Metodo, Testo } from './comuni.jsx'
import { gl } from '../../lib/glossario.js'

const ERRORI = [
  ['400', 'richiesta_non_valida', 'Il corpo non è JSON valido o manca qualcosa (es. il processo)'],
  ['401', 'chiave_mancante / chiave_non_valida', 'Chiave assente, sbagliata, scaduta, sospesa o revocata'],
  ['403', 'permesso_mancante', 'La chiave non ha il permesso necessario'],
  ['404', 'non_trovato', 'Non esiste, oppure la chiave non può vederlo (processo non consentito)'],
  ['409', 'conflitto', `Operazione non possibile ora (es. ${gl('pratica', 'nome')} ${gl.agg('pratica', 'conclus')}, messaggio non atteso)`],
  ['422', 'dati_non_validi', 'Dati di avvio non validi: l\'elenco completo è in errore.campi'],
  ['429', 'troppe_richieste', 'Limite di richieste al minuto superato: riprova dopo Retry-After secondi']
]

/**
 * Documentazione dell'API pubblica generata dai processi pubblicati, con esempi di codice
 * e la possibilità di provare le chiamate con una chiave.
 */
export default function DocumentazioneApi({ panoramica }) {
  const [doc, setDoc] = useState(null)
  const [errore, setErrore] = useState('')
  const [chiave, setChiave] = useState('')

  useEffect(() => { api.openapi().then(setDoc).catch(e => setErrore(e.message)) }, [])
  const gruppi = useMemo(() => (doc ? sezioni(doc) : []), [doc])
  const base = doc?.servers?.[0]?.url || `${panoramica.indirizzoBase}/api/v1`

  const scarica = () => {
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'orditogram-openapi.json'
    a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 5000)
  }
  const vai = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  if (errore) return <Errore>{errore}</Errore>
  if (!doc) return <Caricamento testo="Genero la documentazione dai processi pubblicati…" />

  const eventoEsempio = esempio(doc, { $ref: '#/components/schemas/Evento' })
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[250px_minmax(0,1fr)]">
      <nav aria-label="Indice dell'API" className="hidden max-h-[calc(100vh-7rem)] overflow-y-auto rounded-pannello border border-linea bg-white p-3 text-sm shadow-pannello lg:sticky lg:top-6 lg:block">
        <button type="button" onClick={() => vai('api-inizio')} className="mb-1 block w-full rounded-md px-2 py-1.5 text-left font-medium text-pino-900 hover:bg-carta">Per iniziare</button>
        {gruppi.map(g => (
          <div key={g.tag} className="mt-2">
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-nebbia">{g.tag}</p>
            {g.operazioni.map(o => (
              <button key={o.id} type="button" onClick={() => vai(`op-${o.id}`)}
                      className="flex w-full items-center gap-2 rounded-md px-2 py-1 text-left hover:bg-carta">
                <Metodo metodo={o.metodo} className="w-10 text-[10px]" />
                <span className="truncate text-pino-800" title={o.percorso}>{o.sommario.replace(/^Avvia: /, '')}</span>
              </button>
            ))}
          </div>
        ))}
        <p className="mt-3 px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-nebbia">Altro</p>
        <button type="button" onClick={() => vai('api-webhook')} className="block w-full rounded-md px-2 py-1 text-left text-pino-800 hover:bg-carta">Webhook</button>
        <button type="button" onClick={() => vai('api-errori')} className="block w-full rounded-md px-2 py-1 text-left text-pino-800 hover:bg-carta">Errori</button>
      </nav>

      <div className="min-w-0 space-y-6">
        <section id="api-inizio" className="scroll-mt-6 overflow-hidden rounded-pannello border border-pino-900 bg-pino-900 text-white shadow-pannello">
          <div className="p-6 sm:p-7">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-zafferano-400">API pubblica · versione 1</p>
                <h2 className="mt-1 text-2xl font-semibold">{doc.info?.title}</h2>
                <p className="mt-2 max-w-2xl text-[15px] text-pino-200">
                  Generata dai processi pubblicati: quando l'analista pubblica una nuova versione, percorsi e campi si aggiornano da soli.
                </p>
              </div>
              <Button variante="secondario" icona={Download} onClick={scarica}>openapi.json</Button>
            </div>
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <div className="rounded-campo bg-white/5 p-3 ring-1 ring-white/10">
                <p className="text-xs text-pino-300">Indirizzo</p>
                <div className="mt-0.5 flex items-center gap-2"><code className="min-w-0 flex-1 truncate font-mono text-[14px]">{base}</code><Copia testo={base} scuro /></div>
              </div>
              <div className="rounded-campo bg-white/5 p-3 ring-1 ring-white/10">
                <p className="text-xs text-pino-300">Autenticazione</p>
                <p className="mt-0.5 font-mono text-[14px]">X-API-Key: iter_…</p>
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 bg-pino-950/40 px-6 py-4 sm:px-7">
            <label className="flex flex-wrap items-center gap-3">
              <span className="inline-flex items-center gap-2 text-sm font-medium"><KeyRound className="size-4 text-zafferano-400" /> Chiave per provare</span>
              <input type="password" value={chiave} onChange={e => setChiave(e.target.value.trim())} placeholder="iter_…  (resta solo in questa pagina)"
                     className="h-9 min-w-0 flex-1 rounded-campo border border-white/15 bg-white/10 px-3 font-mono text-sm text-white placeholder:text-pino-300 focus:border-zafferano-400 focus:outline-none" />
              {chiave && <Badge tono="verde" icona={ShieldCheck}>pronta</Badge>}
            </label>
          </div>
        </section>

        <div className="rounded-pannello border border-linea bg-white p-5 shadow-pannello">
          <Testo md={doc.info?.description} />
          <div className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
            {[
              ['1', 'Crea una chiave', 'In "Chiavi API", con i permessi che servono al sistema.'],
              ['2', `Avvia ${gl('pratica', 'un')}`, 'POST sul percorso del processo, con i dati del form di avvio.'],
              ['3', `Segui ${gl('pratica', 'il')}`, 'Leggi lo stato, oppure ricevi gli avvisi con un webhook.']
            ].map(([n, t, d]) => (
              <div key={n} className="flex gap-3 rounded-campo bg-carta p-3">
                <span className="inline-flex size-7 shrink-0 items-center justify-center rounded-full bg-verde-600 text-sm font-semibold text-white">{n}</span>
                <span><span className="block font-medium text-pino-900">{t}</span><span className="text-grafite">{d}</span></span>
              </div>
            ))}
          </div>
        </div>

        {gruppi.map(g => (
          <section key={g.tag} className="space-y-3">
            <div className="flex items-baseline gap-3 pt-2">
              <h2 className="text-[22px] font-semibold">{g.tag}</h2>
              <p className="text-sm text-grafite">{g.descrizione}</p>
            </div>
            {g.operazioni.map(o => <Operazione key={o.id} doc={doc} op={o} base={base} chiave={chiave} />)}
          </section>
        ))}

        <section id="api-webhook" className="scroll-mt-6 space-y-3 rounded-pannello border border-linea bg-white p-5 shadow-pannello">
          <h2 className="flex items-center gap-2 text-[22px] font-semibold"><IconaWebhook className="size-5 text-verde-600" /> Webhook</h2>
          <p className="text-[15px] text-grafite">
            Configurati nella scheda "Webhook". Orditogram invia una POST con questo corpo; le intestazioni <code className="font-mono text-pino-900">X-Orditogram-Evento</code>,
            <code className="mx-1 font-mono text-pino-900">X-Orditogram-Consegna</code> e <code className="font-mono text-pino-900">X-Orditogram-Firma</code> dicono cosa è successo,
            quale consegna è e chi la firma. Rispondi 2xx entro 10 secondi; altrimenti Orditogram riprova dopo 1, 5, 30 minuti, 2, 6, 12 e 24 ore.
          </p>
          <div className="grid gap-x-6 gap-y-1.5 text-sm sm:grid-cols-2">
            {Object.entries(panoramica.eventi || {}).map(([k, d]) => (
              <div key={k} className="flex gap-2"><code className="shrink-0 font-mono text-[13px] text-pino-900">{k}</code><span className="text-grafite">{d}</span></div>
            ))}
          </div>
          <Codice titolo="Esempio di corpo" codice={JSON.stringify(eventoEsempio, null, 2)} altezza="max-h-72" />
          <CodiceASchede voci={VERIFICA_FIRMA} />
        </section>

        <section id="api-errori" className="scroll-mt-6 rounded-pannello border border-linea bg-white p-5 shadow-pannello">
          <h2 className="text-[22px] font-semibold">Errori</h2>
          <p className="mt-1 text-[15px] text-grafite">Sempre nel formato <code className="font-mono text-pino-900">{'{"errore": {"codice", "messaggio", "campi"}}'}</code>. Il codice non cambia tra le versioni: confronta quello, non il messaggio.</p>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <tbody className="divide-y divide-linea">
                {ERRORI.map(([s, c, d]) => (
                  <tr key={s}><td className="numeri py-2 pr-4 font-mono text-pino-900">{s}</td><td className="py-2 pr-4 font-mono text-[13px] text-pino-900">{c}</td><td className="py-2 text-grafite">{d}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  )
}

function Operazione({ doc, op, base, chiave }) {
  const [prova, setProva] = useState(false)
  const righe = op.corpo ? campi(doc, op.corpo.schema) : []
  const parametri = op.parametri
  const esempioCorpo = op.corpo?.esempio
  const url = base + op.percorso
  const conDocumenti = op.corpo?.multipart && !op.corpo?.json
  const voci = {
    cURL: codice('curl', { metodo: op.metodo, url, corpo: esempioCorpo }),
    JavaScript: codice('javascript', { metodo: op.metodo, url, corpo: esempioCorpo }),
    Python: codice('python', { metodo: op.metodo, url, corpo: esempioCorpo })
  }
  if (op.corpo?.multipart && op.corpo?.json) {
    voci['cURL + file'] = codice('curl', { metodo: op.metodo, url, corpo: esempioCorpo, multipart: true })
    voci['JS + file'] = codice('javascript', { metodo: op.metodo, url, corpo: esempioCorpo, multipart: true })
  }
  return (
    <article id={`op-${op.id}`} className="scroll-mt-6 overflow-hidden rounded-pannello border border-linea bg-white shadow-pannello">
      <header className="flex flex-wrap items-center gap-3 border-b border-linea bg-carta/50 px-5 py-3">
        <Metodo metodo={op.metodo} />
        <code className="min-w-0 break-all font-mono text-[14px] font-medium text-pino-900">{op.percorso}</code>
        <span className="text-[15px] text-grafite">{op.sommario}</span>
      </header>
      <div className="grid gap-5 p-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="min-w-0 space-y-4">
          <Testo md={op.descrizione} className="text-sm" />
          {parametri.length > 0 && (
            <div>
              <h4 className="mb-1.5 text-[13px] font-semibold uppercase tracking-wide text-nebbia">Parametri</h4>
              <ul className="divide-y divide-linea rounded-campo border border-linea text-sm">
                {parametri.map(p => (
                  <li key={p.in + p.name} className="flex flex-wrap items-baseline gap-x-2 px-3 py-2">
                    <code className="font-mono text-[13px] font-medium text-pino-900">{p.name}</code>
                    <span className="text-xs text-nebbia">{p.in === 'path' ? 'nel percorso' : 'nella query'}{p.required ? ', obbligatorio' : ''}</span>
                    <span className="w-full text-grafite">{p.description}{p.schema?.enum && <> — {p.schema.enum.join(', ')}</>}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {righe.length > 0 && (
            <div>
              <h4 className="mb-1.5 text-[13px] font-semibold uppercase tracking-wide text-nebbia">Corpo</h4>
              <Campi righe={righe} />
            </div>
          )}
          <div>
            <h4 className="mb-1.5 text-[13px] font-semibold uppercase tracking-wide text-nebbia">Risposte</h4>
            <ul className="flex flex-wrap gap-1.5">
              {op.risposte.map(r => (
                <li key={r.codice} title={r.descrizione}
                    className={cx('rounded-md px-2 py-0.5 font-mono text-[12px] ring-1 ring-inset',
                      r.codice.startsWith('2') ? 'bg-verde-50 text-verde-700 ring-verde-100' : 'bg-carta text-grafite ring-linea')}>
                  {r.codice} <span className="font-sans">{r.descrizione}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <div className="min-w-0 space-y-3">
          <CodiceASchede voci={voci} />
          {!conDocumenti && (
            <Button misura="sm" variante={prova ? 'scuro' : 'secondario'} icona={Play} onClick={() => setProva(!prova)}>
              {prova ? 'Chiudi la prova' : 'Prova questa chiamata'}
            </Button>
          )}
          {prova && <Prova op={op} chiave={chiave} esempioCorpo={esempioCorpo} />}
        </div>
      </div>
    </article>
  )
}

function Campi({ righe, livello = 0 }) {
  return (
    <ul className={cx('divide-y divide-linea text-sm', livello === 0 ? 'rounded-campo border border-linea' : 'ml-4 border-l border-linea')}>
      {righe.map(r => (
        <li key={r.nome} className="px-3 py-2">
          <div className="flex flex-wrap items-baseline gap-x-2">
            <code className="font-mono text-[13px] font-medium text-pino-900">{r.nome}</code>
            <span className="text-xs text-nebbia">{r.tipo}</span>
            {r.obbligatorio && <span className="text-xs font-medium text-mattone-600">obbligatorio</span>}
          </div>
          {r.descrizione && <p className="text-grafite">{r.descrizione}</p>}
          {r.valori && <p className="mt-0.5 flex flex-wrap gap-1">{r.valori.map(v => <code key={String(v)} className="rounded bg-carta px-1.5 font-mono text-[12px] text-pino-900 ring-1 ring-linea">{String(v)}</code>)}</p>}
          {r.figli.length > 0 && <div className="mt-1.5"><Campi righe={r.figli} livello={livello + 1} /></div>}
        </li>
      ))}
    </ul>
  )
}

function Prova({ op, chiave, esempioCorpo }) {
  const segnaposto = [...op.percorso.matchAll(/\{(\w+)\}/g)].map(m => m[1])
  const query = op.parametri.filter(p => p.in === 'query')
  const [valori, setValori] = useState({})
  const [corpo, setCorpo] = useState(esempioCorpo != null ? JSON.stringify(esempioCorpo, null, 2) : '')
  const [risposta, setRisposta] = useState(null)
  const [attesa, setAttesa] = useState(false)
  const [errore, setErrore] = useState('')

  const invia = async () => {
    setErrore(''); setRisposta(null)
    if (!chiave) { setErrore('Inserisci una chiave in alto ("Chiave per provare").'); return }
    let percorso = compilaPercorso(op.percorso, valori)
    if (/\{\w+\}/.test(percorso)) { setErrore('Compila i parametri del percorso.'); return }
    const qs = query.filter(p => valori[p.name]).map(p => `${p.name}=${encodeURIComponent(valori[p.name])}`).join('&')
    if (qs) percorso += `?${qs}`
    let dati
    if (op.metodo !== 'GET' && corpo.trim()) {
      try { dati = JSON.parse(corpo) } catch { setErrore('Il corpo non è un JSON valido.'); return }
    }
    setAttesa(true)
    try { setRisposta(await chiamaApiPubblica(op.metodo, percorso, chiave, dati)) } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }
  return (
    <div className="space-y-3 rounded-campo border border-linea bg-carta/60 p-3">
      {[...segnaposto.map(n => ({ name: n, in: 'path' })), ...query].map(p => (
        <label key={p.in + p.name} className="flex items-center gap-2 text-sm">
          <code className="w-28 shrink-0 font-mono text-[13px] text-pino-900">{p.name}</code>
          <input className="campo h-9 text-sm" value={valori[p.name] || ''} placeholder={p.in === 'path' ? 'obbligatorio' : 'facoltativo'}
                 onChange={e => setValori({ ...valori, [p.name]: e.target.value })} />
        </label>
      ))}
      {op.metodo !== 'GET' && (
        <textarea className="campo min-h-40 resize-y font-mono text-[12.5px] leading-relaxed" value={corpo} onChange={e => setCorpo(e.target.value)}
                  aria-label="Corpo della richiesta" spellCheck={false} />
      )}
      <div className="flex items-center gap-3">
        <Button misura="sm" variante="primario" icona={Play} attesa={attesa} onClick={invia}>Invia</Button>
        {op.metodo === 'POST' && <span className="text-xs text-grafite">Attenzione: la chiamata è vera (crea {gl('pratica', 'nomi')}, consegna messaggi).</span>}
      </div>
      <Errore>{errore}</Errore>
      {risposta && (
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-sm">
            <Badge tono={risposta.ok ? 'verde' : 'mattone'}>{risposta.stato}</Badge>
            <span className="numeri text-grafite">{risposta.durata} ms</span>
          </p>
          <Codice titolo="Risposta" codice={typeof risposta.dati === 'string' ? risposta.dati : JSON.stringify(risposta.dati, null, 2)} altezza="max-h-80" />
        </div>
      )}
    </div>
  )
}

