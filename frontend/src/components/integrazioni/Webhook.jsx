import { useCallback, useEffect, useState } from 'react'
import {
  BellRing, ChevronLeft, ChevronRight, CircleCheck, CircleX, Clock, Eye, EyeOff, FlaskConical, Inbox, KeyRound, Pause,
  Pencil, Play, Plus, RefreshCw, RotateCcw, Send, Trash, Webhook as IconaWebhook
} from 'lucide-react'
import { api } from '../../api.js'
import { cx, dataOraCompleta, fa, quando } from '../../lib/format.js'
import { VERIFICA_FIRMA } from '../../lib/openapi.js'
import { Badge, Button, Caricamento, Dialogo, Errore, Pannello, Vuoto, useToast } from '../ui.jsx'
import { Caselle, Codice, CodiceASchede, Copia, SceltaProcessi } from './comuni.jsx'

export const STATI_CONSEGNA = {
  IN_CODA: ['In coda', 'neutro', Clock],
  IN_INVIO: ['In invio', 'neutro', Send],
  CONSEGNATA: ['Consegnata', 'verde', CircleCheck],
  DA_RIPETERE: ['Da ripetere', 'zafferano', RotateCcw],
  FALLITA: ['Fallita', 'mattone', CircleX]
}

function StatoConsegna({ stato }) {
  const [l, t, I] = STATI_CONSEGNA[stato] || [stato, 'neutro', Clock]
  return <Badge tono={t} icona={I}>{l}</Badge>
}

/** Webhook: indirizzi esterni avvisati quando succede qualcosa, con consegne e ricevitore di prova. */
export default function Webhook({ panoramica }) {
  const [lista, setLista] = useState(null)
  const [errore, setErrore] = useState('')
  const [modifica, setModifica] = useState(null)
  const [segreto, setSegreto] = useState(null)       // { webhook, segreto, nuovo }
  const [aggiornamento, setAggiornamento] = useState(0)
  const toast = useToast()

  const carica = useCallback(() => api.webhook().then(setLista).catch(e => setErrore(e.message)), [])
  useEffect(() => { carica() }, [carica, aggiornamento])

  const ricarica = () => setAggiornamento(n => n + 1)
  const prova = async (w) => {
    try {
      const c = await api.provaWebhook(w.id)
      if (c.stato === 'CONSEGNATA') toast.ok(`Prova consegnata a "${w.nome}": risposta ${c.codiceRisposta} in ${c.durataMs} ms`)
      else toast.errore(`Prova non consegnata: ${c.errore || 'risposta ' + c.codiceRisposta}`)
    } catch (e) { toast.errore(e.message) }
    ricarica()
  }
  const alterna = async (w) => {
    try { await api.modificaWebhook(w.id, { attivo: !w.attivo }); toast.ok(w.attivo ? 'Webhook sospeso: gli avvisi restano in coda' : 'Webhook riattivato') } catch (e) { toast.errore(e.message) }
    ricarica()
  }
  const elimina = async (w) => {
    if (!confirm(`Eliminare il webhook "${w.nome}" e tutte le sue consegne?`)) return
    try { await api.eliminaWebhook(w.id); toast.ok('Webhook eliminato') } catch (e) { toast.errore(e.message) }
    ricarica()
  }
  const mostraSegreto = async (w) => {
    try { setSegreto({ webhook: w, segreto: (await api.segretoWebhook(w.id)).segreto }) } catch (e) { toast.errore(e.message) }
  }

  const eventi = panoramica.eventi || {}
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <p className="max-w-2xl text-grafite">
          Orditogram avvisa i tuoi sistemi quando una pratica parte, un'attività è pronta, arriva un messaggio o c'è un errore.
          Ogni avviso è firmato; se il destinatario non risponde, Orditogram riprova per 24 ore.
        </p>
        <Button variante="primario" icona={Plus} onClick={() => setModifica({ nuovo: true })}>Nuovo webhook</Button>
      </div>

      {!panoramica.cifratura && (
        <p className="rounded-campo border border-zafferano-100 bg-zafferano-50 px-4 py-3 text-sm text-zafferano-800">
          I segreti dei webhook sono salvati in chiaro: in produzione imposta <code className="font-mono">ORDITOGRAM_CHIAVE_CIFRATURA</code> (README, "Webhook").
        </p>
      )}

      <Errore>{errore}</Errore>
      {!lista && !errore && <Caricamento />}
      {lista && lista.length === 0 && (
        <div className="rounded-pannello border border-dashed border-linea bg-white">
          <Vuoto icona={IconaWebhook} titolo="Nessun webhook"
                 azione={<Button variante="primario" icona={Plus} onClick={() => setModifica({ nuovo: true })}>Aggiungi un webhook</Button>}>
            {panoramica.ricevitoreProva ? 'Per provare senza un sistema esterno usa il ricevitore di prova di Orditogram.' : 'Indica l\'indirizzo del sistema da avvisare.'}
          </Vuoto>
        </div>
      )}

      <ul className="grid gap-3 lg:grid-cols-2">
        {(lista || []).map(w => (
          <li key={w.id} className={cx('flex min-w-0 flex-col rounded-pannello border bg-white p-4 shadow-pannello sm:p-5', w.attivo ? 'border-linea' : 'border-dashed border-linea opacity-80')}>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-[17px] font-semibold text-pino-900">{w.nome}</h3>
                  {!w.attivo && <Badge tono="zafferano">Sospeso</Badge>}
                </div>
                <p className="mt-0.5 truncate font-mono text-[13px] text-grafite" title={w.url}>{w.url}</p>
                {w.descrizione && <p className="mt-1 text-sm text-grafite">{w.descrizione}</p>}
              </div>
              <Esito w={w} />
            </div>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {w.eventi.length === 0
                ? <span className="rounded-full bg-verde-50 px-2.5 py-0.5 text-[12px] font-medium text-verde-700 ring-1 ring-inset ring-verde-100">tutti gli eventi</span>
                : w.eventi.map(e => <span key={e} title={eventi[e]} className="rounded-full bg-pino-900/5 px-2.5 py-0.5 font-mono text-[12px] text-pino-800 ring-1 ring-inset ring-linea">{e}</span>)}
              {w.processi.length > 0 && <span className="rounded-full bg-zafferano-50 px-2.5 py-0.5 text-[12px] text-zafferano-800 ring-1 ring-inset ring-zafferano-100">solo {w.processi.join(', ')}</span>}
            </div>
            <div className="flex-1" />
            <div className="mt-4 flex flex-wrap gap-2 border-t border-linea pt-3">
              <Button misura="sm" icona={FlaskConical} onClick={() => prova(w)}>Prova</Button>
              <Button misura="sm" icona={Pencil} onClick={() => setModifica(w)}>Modifica</Button>
              <Button misura="sm" icona={KeyRound} onClick={() => mostraSegreto(w)}>Segreto</Button>
              <Button misura="sm" icona={w.attivo ? Pause : Play} onClick={() => alterna(w)}>{w.attivo ? 'Sospendi' : 'Riattiva'}</Button>
              <Button misura="sm" variante="pericolo" icona={Trash} onClick={() => elimina(w)} aria-label={`Elimina ${w.nome}`} title="Elimina" className="ml-auto" />
            </div>
          </li>
        ))}
      </ul>

      {lista && lista.length > 0 && <Consegne webhook={lista} eventi={eventi} aggiornamento={aggiornamento} onCambio={ricarica} />}
      {panoramica.ricevitoreProva && <Ricevitore indirizzo={panoramica.ricevitoreProva} aggiornamento={aggiornamento} />}

      <Pannello titolo="Come verificare la firma">
        <div className="space-y-3">
          <p className="text-sm text-grafite">
            Ogni avviso ha l'intestazione <code className="font-mono text-pino-900">X-Orditogram-Firma: t=…,v1=…</code>: è l'HMAC-SHA256 di
            <code className="mx-1 font-mono text-pino-900">t.corpo</code> calcolato con il segreto del webhook. Verificala sul corpo così come arriva,
            prima di interpretarlo, e rifiuta gli avvisi troppo vecchi. Lo stesso evento può arrivare due volte: usa il campo <code className="font-mono text-pino-900">id</code>.
          </p>
          <CodiceASchede voci={VERIFICA_FIRMA} />
        </div>
      </Pannello>

      {modifica && (
        <DialogoWebhook webhook={modifica.nuovo ? null : modifica} panoramica={panoramica} onClose={() => setModifica(null)}
                        onSalvato={(w) => { setModifica(null); ricarica(); if (w.segreto) setSegreto({ webhook: w, segreto: w.segreto, nuovo: true }) }} />
      )}
      {segreto && <DialogoSegreto {...segreto} onClose={() => setSegreto(null)} onRigenerato={(s) => setSegreto(x => ({ ...x, segreto: s }))} />}
    </div>
  )
}

function Esito({ w }) {
  const u = w.ultima
  return (
    <div className="shrink-0 text-right text-xs">
      {!u && <span className="text-nebbia">Nessun avviso ancora</span>}
      {u && u.stato === 'CONSEGNATA' && (
        <span className="inline-flex items-center gap-1 text-verde-700" title={dataOraCompleta(u.ultimoTentativo)}>
          <CircleCheck className="size-3.5" /> {u.codiceRisposta} · {fa(u.ultimoTentativo)}
        </span>
      )}
      {u && u.stato !== 'CONSEGNATA' && (
        <span className="inline-flex items-center gap-1 text-mattone-700" title={u.errore || ''}>
          <CircleX className="size-3.5" /> {u.codiceRisposta || 'errore'} · {fa(u.ultimoTentativo)}
        </span>
      )}
      <div className="mt-1 space-x-2 text-grafite">
        {w.inCoda > 0 && <span className="numeri">{w.inCoda} in coda</span>}
        {w.fallite > 0 && <span className="numeri text-mattone-700">{w.fallite} fallite</span>}
      </div>
    </div>
  )
}

function DialogoWebhook({ webhook, panoramica, onClose, onSalvato }) {
  const [nome, setNome] = useState(webhook?.nome || '')
  const [url, setUrl] = useState(webhook?.url || '')
  const [descrizione, setDescrizione] = useState(webhook?.descrizione || '')
  const [tutti, setTutti] = useState(!webhook || webhook.eventi.length === 0)
  const [eventi, setEventi] = useState(webhook?.eventi?.length ? webhook.eventi : ['pratica.avviata', 'pratica.conclusa', 'attivita.creata'])
  const [processi, setProcessi] = useState(webhook?.processi || [])
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const salva = async (e) => {
    e.preventDefault()
    setErrore(''); setAttesa(true)
    const dati = { nome, url, descrizione, eventi: tutti ? [] : eventi, processi }
    try {
      onSalvato(webhook ? await api.modificaWebhook(webhook.id, dati) : await api.creaWebhook({ ...dati, attivo: true }))
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }
  return (
    <Dialogo aperto onClose={onClose} titolo={webhook ? `Webhook ${webhook.nome}` : 'Nuovo webhook'} larghezza="max-w-3xl"
             sottotitolo="Orditogram invia una POST con il JSON dell'evento a questo indirizzo.">
      <form onSubmit={salva} className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-[1fr_2fr]">
          <label className="block">
            <span className="etichetta">Nome</span>
            <input className="campo" value={nome} onChange={e => setNome(e.target.value)} placeholder="Gestionale protocollo" required maxLength={80} autoFocus={!webhook} />
          </label>
          <label className="block">
            <span className="etichetta">Indirizzo</span>
            <input className="campo font-mono text-[14px]" value={url} onChange={e => setUrl(e.target.value)} placeholder="https://gestionale.ente.it/orditogram/eventi" required />
            {panoramica.ricevitoreProva && (
              <button type="button" onClick={() => setUrl(panoramica.ricevitoreProva)} className="mt-1 text-xs font-medium text-verde-700 hover:underline">
                Usa il ricevitore di prova di Orditogram
              </button>
            )}
          </label>
        </div>
        <label className="block">
          <span className="etichetta">Descrizione <span className="font-normal text-nebbia">(facoltativa)</span></span>
          <input className="campo" value={descrizione} onChange={e => setDescrizione(e.target.value)} placeholder="Chi gestisce il sistema, cosa fa con gli avvisi" />
        </label>
        <div>
          <span className="etichetta">Eventi</span>
          <div className="mb-2 inline-flex rounded-campo border border-linea bg-carta p-0.5 text-sm">
            <button type="button" onClick={() => setTutti(true)} className={cx('rounded-[7px] px-3 py-1.5 font-medium', tutti ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite')}>Tutti</button>
            <button type="button" onClick={() => setTutti(false)} className={cx('rounded-[7px] px-3 py-1.5 font-medium', !tutti ? 'bg-white text-pino-900 shadow-sm' : 'text-grafite')}>Solo alcuni</button>
          </div>
          {!tutti && <Caselle voci={Object.entries(panoramica.eventi || {}).map(([id, aiuto]) => ({ id, label: id, aiuto }))} valori={eventi} onChange={setEventi} />}
          {tutti && <p className="text-sm text-grafite">Anche gli eventi che verranno aggiunti nelle prossime versioni di Orditogram.</p>}
        </div>
        <div>
          <span className="etichetta">Pratiche di</span>
          <SceltaProcessi processi={panoramica.processi || []} valori={processi} onChange={setProcessi} />
        </div>
        <Errore>{errore}</Errore>
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Button type="button" onClick={onClose}>Annulla</Button>
          <Button type="submit" variante="primario" attesa={attesa} disabled={!tutti && !eventi.length}>{webhook ? 'Salva' : 'Crea il webhook'}</Button>
        </div>
      </form>
    </Dialogo>
  )
}

function DialogoSegreto({ webhook, segreto, nuovo, onClose, onRigenerato }) {
  const [visibile, setVisibile] = useState(!!nuovo)
  const toast = useToast()
  const rigenera = async () => {
    if (!confirm('Generare un nuovo segreto? Il sistema che riceve gli avvisi dovrà usare quello nuovo, altrimenti rifiuterà le firme.')) return
    try { onRigenerato((await api.rigeneraSegretoWebhook(webhook.id)).segreto); setVisibile(true); toast.ok('Nuovo segreto generato') } catch (e) { toast.errore(e.message) }
  }
  return (
    <Dialogo aperto onClose={onClose} titolo={nuovo ? 'Webhook creato' : `Segreto di ${webhook.nome}`}
             sottotitolo="Serve al sistema che riceve gli avvisi per verificarne la firma.">
      <div className="space-y-4">
        <div className="flex items-center gap-2 rounded-campo border border-linea bg-carta p-2 pl-3">
          <code className="min-w-0 flex-1 break-all font-mono text-[14px] text-pino-900">{visibile ? segreto : '•'.repeat(32)}</code>
          <button type="button" onClick={() => setVisibile(!visibile)} className="rounded-md p-1.5 text-grafite hover:bg-white" aria-label={visibile ? 'Nascondi' : 'Mostra'}>
            {visibile ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
          </button>
          <Copia testo={segreto} />
        </div>
        {nuovo && <p className="text-sm text-grafite">Il segreto resta consultabile qui (solo per gli amministratori; ogni lettura finisce nel registro).</p>}
        <div className="flex flex-wrap justify-between gap-2 border-t border-linea pt-4">
          <Button icona={RefreshCw} onClick={rigenera}>Genera un nuovo segreto</Button>
          <Button variante="primario" onClick={onClose}>Fatto</Button>
        </div>
      </div>
    </Dialogo>
  )
}

const DIMENSIONE = 20

function Consegne({ webhook, eventi, aggiornamento, onCambio }) {
  const [filtri, setFiltri] = useState({ webhook: '', stato: '', tipo: '' })
  const [pagina, setPagina] = useState(0)
  const [ris, setRis] = useState(null)
  const [errore, setErrore] = useState('')
  const [dettaglio, setDettaglio] = useState(null)
  const [attesa, setAttesa] = useState(false)
  const toast = useToast()

  const carica = useCallback(() => api.consegne({ ...filtri, pagina, dimensione: DIMENSIONE }).then(setRis).catch(e => setErrore(e.message)), [filtri, pagina])
  useEffect(() => { carica() }, [carica, aggiornamento])
  useEffect(() => { setPagina(0) }, [filtri])

  const apri = async (c) => {
    try { setDettaglio(await api.consegna(c.id)) } catch (e) { toast.errore(e.message) }
  }
  const ripeti = async () => {
    setAttesa(true)
    try {
      const c = await api.ripetiConsegna(dettaglio.id)
      setDettaglio(c)
      c.stato === 'CONSEGNATA' ? toast.ok('Consegnata') : toast.errore(`Non consegnata: ${c.errore || c.codiceRisposta}`)
      onCambio()
    } catch (e) { toast.errore(e.message) } finally { setAttesa(false) }
  }
  const totale = ris?.totale ?? 0
  const pagine = Math.max(1, Math.ceil(totale / DIMENSIONE))
  const nomeWebhook = Object.fromEntries(webhook.map(w => [w.id, w.nome]))

  return (
    <section className="rounded-pannello border border-linea bg-white shadow-pannello">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-linea px-5 py-3.5">
        <h2 className="flex items-center gap-2 text-[17px] font-semibold"><BellRing className="size-4 text-verde-600" /> Consegne</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select className="campo h-9 w-44 text-sm" value={filtri.webhook} onChange={e => setFiltri({ ...filtri, webhook: e.target.value })} aria-label="Webhook">
            <option value="">Tutti i webhook</option>
            {webhook.map(w => <option key={w.id} value={w.id}>{w.nome}</option>)}
          </select>
          <select className="campo h-9 w-40 text-sm" value={filtri.stato} onChange={e => setFiltri({ ...filtri, stato: e.target.value })} aria-label="Stato">
            <option value="">Tutti gli stati</option>
            {Object.entries(STATI_CONSEGNA).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <select className="campo h-9 w-48 text-sm" value={filtri.tipo} onChange={e => setFiltri({ ...filtri, tipo: e.target.value })} aria-label="Evento">
            <option value="">Tutti gli eventi</option>
            {Object.keys(eventi).map(k => <option key={k} value={k}>{k}</option>)}
            <option value="prova">prova</option>
          </select>
          <Button misura="sm" icona={RefreshCw} onClick={carica} aria-label="Aggiorna" />
        </div>
      </header>
      <Errore>{errore}</Errore>
      {!ris && !errore && <Caricamento />}
      {ris && ris.righe.length === 0 && <Vuoto icona={Inbox} titolo="Nessuna consegna">Gli avvisi compaiono qui appena succede qualcosa nelle pratiche. Prova con il pulsante "Prova" di un webhook.</Vuoto>}
      {ris && ris.righe.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-linea text-[13px] text-grafite">
              <tr>
                <th className="px-4 py-2.5 font-medium">Quando</th>
                <th className="px-4 py-2.5 font-medium">Webhook</th>
                <th className="px-4 py-2.5 font-medium">Evento</th>
                <th className="px-4 py-2.5 font-medium">Stato</th>
                <th className="px-4 py-2.5 font-medium">Risposta</th>
                <th className="px-4 py-2.5 text-right font-medium">Tentativi</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-linea">
              {ris.righe.map(c => (
                <tr key={c.id} onClick={() => apri(c)} className="cursor-pointer hover:bg-carta/60">
                  <td className="whitespace-nowrap px-4 py-2.5 text-grafite" title={dataOraCompleta(c.creata)}>{quando(c.creata)}</td>
                  <td className="px-4 py-2.5 text-pino-900">{c.webhookNome || nomeWebhook[c.webhook]}</td>
                  <td className="px-4 py-2.5 font-mono text-[13px] text-pino-900">{c.tipo}</td>
                  <td className="px-4 py-2.5"><StatoConsegna stato={c.stato} /></td>
                  <td className="max-w-xs truncate px-4 py-2.5 text-grafite" title={c.errore || ''}>
                    {c.codiceRisposta && <span className={cx('numeri mr-1.5 font-mono', c.codiceRisposta < 300 ? 'text-verde-700' : 'text-mattone-700')}>{c.codiceRisposta}</span>}
                    {c.errore && !c.codiceRisposta ? c.errore : c.durataMs != null && <span className="numeri text-nebbia">{c.durataMs} ms</span>}
                    {c.prossimoTentativo && <span className="ml-1.5 text-xs text-zafferano-800">riprova {fa(c.prossimoTentativo)}</span>}
                  </td>
                  <td className="numeri px-4 py-2.5 text-right text-grafite">{c.tentativi}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {ris && totale > DIMENSIONE && (
        <nav className="flex items-center justify-between gap-3 border-t border-linea px-4 py-3 text-sm text-grafite" aria-label="Pagine">
          <span className="numeri">{pagina * DIMENSIONE + 1}–{Math.min(totale, (pagina + 1) * DIMENSIONE)} di {totale.toLocaleString('it-IT')}</span>
          <div className="flex items-center gap-1">
            <button type="button" onClick={() => setPagina(p => p - 1)} disabled={pagina === 0} aria-label="Pagina precedente" className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronLeft className="size-4" /></button>
            <span className="numeri min-w-20 text-center text-pino-900">Pagina {pagina + 1} di {pagine}</span>
            <button type="button" onClick={() => setPagina(p => p + 1)} disabled={pagina + 1 >= pagine} aria-label="Pagina successiva" className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronRight className="size-4" /></button>
          </div>
        </nav>
      )}

      {dettaglio && (
        <Dialogo aperto onClose={() => setDettaglio(null)} larghezza="max-w-3xl"
                 titolo={`Evento ${dettaglio.tipo}`}
                 sottotitolo={`Consegna ${dettaglio.id} · ${nomeWebhook[dettaglio.webhook] || 'webhook eliminato'} · evento ${dettaglio.evento}`}>
          <div className="space-y-4">
            <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
              <div><dt className="text-grafite">Stato</dt><dd className="mt-0.5"><StatoConsegna stato={dettaglio.stato} /></dd></div>
              <div><dt className="text-grafite">Tentativi</dt><dd className="numeri mt-0.5 text-pino-900">{dettaglio.tentativi}</dd></div>
              <div><dt className="text-grafite">Risposta</dt><dd className="numeri mt-0.5 font-mono text-pino-900">{dettaglio.codiceRisposta ?? '—'}{dettaglio.durataMs != null && <span className="text-grafite"> · {dettaglio.durataMs} ms</span>}</dd></div>
              <div><dt className="text-grafite">{dettaglio.prossimoTentativo ? 'Prossimo tentativo' : 'Ultimo tentativo'}</dt><dd className="mt-0.5 text-pino-900">{quando(dettaglio.prossimoTentativo || dettaglio.ultimoTentativo) || '—'}</dd></div>
            </dl>
            {dettaglio.errore && <Errore>{dettaglio.errore}</Errore>}
            <Codice titolo="Corpo inviato" codice={JSON.stringify(dettaglio.payload, null, 2)} altezza="max-h-72" />
            {dettaglio.risposta && <Codice titolo="Risposta del destinatario" codice={dettaglio.risposta} altezza="max-h-40" />}
            <div className="flex justify-end gap-2 border-t border-linea pt-4">
              {['DA_RIPETERE', 'FALLITA', 'IN_CODA'].includes(dettaglio.stato) && <Button icona={RotateCcw} attesa={attesa} onClick={ripeti}>Ripeti adesso</Button>}
              <Button variante="primario" onClick={() => setDettaglio(null)}>Chiudi</Button>
            </div>
          </div>
        </Dialogo>
      )}
    </section>
  )
}

function Ricevitore({ indirizzo, aggiornamento }) {
  const [lista, setLista] = useState(null)
  const [aperto, setAperto] = useState(null)
  const carica = useCallback(() => api.ricevuti().then(setLista).catch(() => setLista([])), [])
  useEffect(() => { carica() }, [carica, aggiornamento])
  const svuota = async () => { await api.svuotaRicevuti(); carica() }
  return (
    <Pannello titolo={<h2 className="flex items-center gap-2 text-[17px] font-semibold"><FlaskConical className="size-4 text-zafferano-500" /> Ricevitore di prova</h2>}
              azioni={<><Button misura="sm" icona={RefreshCw} onClick={carica}>Aggiorna</Button>{lista?.length > 0 && <Button misura="sm" onClick={svuota}>Svuota</Button>}</>}>
      <p className="mb-3 text-sm text-grafite">
        Un finto sistema esterno dentro Orditogram (attivo con i servizi di prova): usa <code className="break-all font-mono text-pino-900">{indirizzo}</code> come
        indirizzo di un webhook e qui vedi cosa arriva e se la firma è valida. Con <code className="font-mono text-pino-900">?simula=errore</code> risponde 503.
      </p>
      {lista && lista.length === 0 && <p className="py-4 text-center text-sm text-nebbia">Ancora niente.</p>}
      <ul className="divide-y divide-linea">
        {(lista || []).map((r, i) => (
          <li key={i} className="py-2">
            <button type="button" onClick={() => setAperto(aperto === i ? null : i)} className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 text-left text-sm">
              <span className="numeri w-28 shrink-0 text-grafite">{quando(r.quando)}</span>
              <span className="font-mono text-pino-900">{r.evento || '(senza evento)'}</span>
              <Badge tono={r.esitoFirma?.startsWith('Firma valida') ? 'verde' : 'mattone'}>{r.esitoFirma}</Badge>
            </button>
            {aperto === i && <Codice className="mt-2" titolo="Corpo ricevuto" codice={formatta(r.corpo)} altezza="max-h-64" />}
          </li>
        ))}
      </ul>
    </Pannello>
  )
}

function formatta(s) {
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
}
