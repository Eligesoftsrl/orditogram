import { useEffect, useState } from 'react'
import { AlertTriangle, ChevronLeft, ChevronRight, Eye, ScrollText, Search } from 'lucide-react'
import { api } from '../../api.js'
import { dataOraCompleta, quando } from '../../lib/format.js'
import { Badge, Caricamento, Cassetto, Errore, Schede, Vuoto, IntestazionePagina } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'
import { gl } from '../../lib/glossario.js'
import { AZIONI, ESITI, LETTURE, ORIGINI, etichettaAzione, etichettaLettura } from '../../lib/registro.js'

export { AZIONI }
const DIMENSIONE = 50

/** Carica una pagina con i filtri (attesa breve mentre si scrive) e tutte le righe per l'esportazione. */
function usePagine(carica, filtri) {
  const [pagina, setPagina] = useState(0)
  const [ris, setRis] = useState(null)
  const [errore, setErrore] = useState('')
  const chiave = JSON.stringify(filtri)
  useEffect(() => { setPagina(0) }, [chiave])
  useEffect(() => {
    setRis(null)
    setErrore('')
    const t = setTimeout(() => carica({ ...filtri, pagina, dimensione: DIMENSIONE }).then(setRis).catch(e => setErrore(e.message)), 250)
    return () => clearTimeout(t)
  }, [chiave, pagina])
  const tutte = async () => {
    const out = []
    for (let p = 0; out.length < 50000; p++) {
      const r = await carica({ ...filtri, pagina: p, dimensione: 1000 })
      out.push(...r.righe)
      if (out.length >= r.totale || r.righe.length === 0) break
    }
    return out
  }
  return { pagina, setPagina, ris, errore, totale: ris?.totale ?? 0, tutte }
}

/** Studio: registro delle azioni di amministrazione, delle letture e degli errori, con ricerca ed esportazione. */
export default function Registro() {
  const [scheda, setScheda] = useState('azioni')
  return (
    <div className="space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Registro' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Registro</h1>
          <p className="mt-1 max-w-2xl text-grafite">
            Chi ha pubblicato processi, modificato form e utenti, chi è entrato e chi ha aperto {gl('pratica', 'i')}, documenti e schede,
            anche quando non poteva. Gli errori imprevisti hanno un codice da dare all'assistenza.
          </p>
        </div>
      </IntestazionePagina>
      <div className="rounded-pannello border border-linea bg-white shadow-pannello">
        <Schede className="px-3" attiva={scheda} onChange={setScheda} voci={[
          { id: 'azioni', label: 'Azioni' },
          { id: 'letture', label: 'Letture' },
          { id: 'errori', label: 'Errori' }
        ]} />
        {scheda === 'azioni' && <Azioni />}
        {scheda === 'letture' && <Letture />}
        {scheda === 'errori' && <Errori />}
      </div>
    </div>
  )
}

function Azioni() {
  const [q, setQ] = useState('')
  const [azione, setAzione] = useState('')
  const p = usePagine(api.registro, { q, azione })
  return (
    <>
      <div className="flex flex-wrap items-center gap-2 border-b border-linea p-3">
        <select className="campo w-full sm:w-60" value={azione} onChange={e => setAzione(e.target.value)} aria-label="Tipo di azione">
          <option value="">Tutte le azioni</option>
          {Object.entries(AZIONI).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <Ricerca valore={q} onChange={setQ} segnaposto="Cerca utente, oggetto, dettagli" etichetta="Cerca nel registro" />
        <div className="sm:ml-auto">
          <Esporta nome="registro" titolo="Registro delle azioni" disabled={!p.totale}
                   sottotitolo={[azione && etichettaAzione(azione), q && `ricerca "${q}"`].filter(Boolean).join(', ')}
                   totale={p.totale} carica={p.tutte}
                   colonne={[
                     { titolo: 'Quando', valore: r => dataOraCompleta(r.quando) },
                     { titolo: 'Utente', valore: r => r.utenteNome ? `${r.utenteNome} (${r.utente})` : r.utente || '' },
                     { titolo: 'Azione', valore: r => etichettaAzione(r.azione) },
                     { titolo: 'Oggetto', valore: r => r.oggetto },
                     { titolo: 'Dettagli', valore: r => r.dettagli, larghezza: 50 },
                     { titolo: 'Indirizzo', valore: r => r.indirizzo }
                   ]} />
        </div>
      </div>
      <Risultati p={p} vuoto={<Vuoto icona={ScrollText} titolo="Nessuna azione registrata">Qui compaiono le azioni di amministrazione appena vengono fatte.</Vuoto>}
                 intestazioni={['Quando', 'Utente', 'Azione', 'Oggetto', 'Dettagli', 'Da']}
                 riga={r => (
                   <tr key={r.id} className="align-top hover:bg-carta/60">
                     <Quando valore={r.quando} />
                     <td className="px-4 py-2.5 text-pino-900">{r.utenteNome || r.utente || <span className="text-nebbia">sistema</span>}</td>
                     <td className="px-4 py-2.5"><Badge tono={AZIONI[r.azione]?.[1] || 'neutro'}>{etichettaAzione(r.azione)}</Badge></td>
                     <td className="px-4 py-2.5 font-mono text-[13px] text-pino-900">{r.oggetto}</td>
                     <td className="max-w-md whitespace-pre-line px-4 py-2.5 text-grafite">{r.dettagli}</td>
                     <Indirizzo valore={r.indirizzo} />
                   </tr>
                 )} />
    </>
  )
}

function Letture() {
  const [utente, setUtente] = useState('')
  const [oggetto, setOggetto] = useState('')
  const [azione, setAzione] = useState('')
  const [esito, setEsito] = useState('')
  const [dal, setDal] = useState('')
  const [al, setAl] = useState('')
  const p = usePagine(api.registroLetture, { utente: utente.trim(), oggetto: oggetto.trim(), azione, esito, dal, al })
  return (
    <>
      <div className="flex flex-wrap items-center gap-2 border-b border-linea p-3">
        <input className="campo w-full sm:w-44" value={utente} onChange={e => setUtente(e.target.value)} placeholder="Utente" aria-label="Utente" />
        <input className="campo w-full font-mono sm:w-56" value={oggetto} onChange={e => setOggetto(e.target.value)} placeholder="Identificativo" aria-label={`Identificativo ${gl('pratica', 'del')}, del documento o della scheda`} />
        <select className="campo w-full sm:w-52" value={azione} onChange={e => setAzione(e.target.value)} aria-label="Cosa è stato aperto">
          <option value="">Tutte le letture</option>
          {Object.keys(LETTURE).map(k => <option key={k} value={k}>{etichettaLettura(k)}</option>)}
        </select>
        <select className="campo w-full sm:w-40" value={esito} onChange={e => setEsito(e.target.value)} aria-label="Esito">
          <option value="">Ogni esito</option>
          {Object.entries(ESITI).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm text-grafite">Dal <input type="date" className="campo w-40" value={dal} onChange={e => setDal(e.target.value)} /></label>
        <label className="flex items-center gap-2 text-sm text-grafite">al <input type="date" className="campo w-40" value={al} onChange={e => setAl(e.target.value)} /></label>
        <div className="sm:ml-auto">
          <Esporta nome="registro-letture" titolo="Registro delle letture" disabled={!p.totale}
                   sottotitolo={[utente && `utente ${utente}`, oggetto && `oggetto ${oggetto}`, azione && etichettaLettura(azione),
                     esito && ESITI[esito][0], dal && `dal ${dal}`, al && `al ${al}`].filter(Boolean).join(', ')}
                   totale={p.totale} carica={p.tutte}
                   colonne={[
                     { titolo: 'Quando', valore: r => dataOraCompleta(r.quando) },
                     { titolo: 'Utente', valore: r => r.utente || '' },
                     { titolo: 'Lettura', valore: r => etichettaLettura(r.azione) },
                     { titolo: 'Oggetto', valore: r => r.oggetto },
                     { titolo: 'Dettagli', valore: r => r.dettagli },
                     { titolo: 'Esito', valore: r => ESITI[r.esito]?.[0] || r.esito },
                     { titolo: 'Indirizzo', valore: r => r.indirizzo }
                   ]} />
        </div>
      </div>
      <Risultati p={p} vuoto={<Vuoto icona={Eye} titolo="Nessuna lettura registrata">Qui compare chi apre {gl('pratica', 'i')}, attività, documenti e schede.</Vuoto>}
                 intestazioni={['Quando', 'Utente', 'Lettura', 'Oggetto', 'Esito', 'Da']}
                 riga={r => (
                   <tr key={r.id} className="align-top hover:bg-carta/60">
                     <Quando valore={r.quando} />
                     <td className="px-4 py-2.5 text-pino-900">{r.utente || <span className="text-nebbia">anonimo</span>}</td>
                     <td className="px-4 py-2.5 text-pino-900">{etichettaLettura(r.azione)}{r.dettagli && <span className="block text-[13px] text-grafite">{r.dettagli}</span>}</td>
                     <td className="px-4 py-2.5 font-mono text-[13px] text-pino-900">{r.oggetto}</td>
                     <td className="px-4 py-2.5"><Badge tono={ESITI[r.esito]?.[1] || 'neutro'}>{ESITI[r.esito]?.[0] || r.esito}</Badge></td>
                     <Indirizzo valore={r.indirizzo} />
                   </tr>
                 )} />
    </>
  )
}

function Errori() {
  const [q, setQ] = useState('')
  const [origine, setOrigine] = useState('')
  const [aperto, setAperto] = useState(null)
  const p = usePagine(api.registroErrori, { q: q.trim(), origine })
  return (
    <>
      <div className="flex flex-wrap items-center gap-2 border-b border-linea p-3">
        <Ricerca valore={q} onChange={setQ} segnaposto="Codice (E-…) o testo dell'errore" etichetta="Cerca negli errori" />
        <select className="campo w-full sm:w-52" value={origine} onChange={e => setOrigine(e.target.value)} aria-label="Origine">
          <option value="">Ogni origine</option>
          {Object.entries(ORIGINI).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <div className="sm:ml-auto">
          <Esporta nome="registro-errori" titolo="Errori imprevisti" disabled={!p.totale}
                   sottotitolo={[origine && ORIGINI[origine], q && `ricerca "${q}"`].filter(Boolean).join(', ')}
                   totale={p.totale} carica={p.tutte}
                   colonne={[
                     { titolo: 'Quando', valore: r => dataOraCompleta(r.quando) },
                     { titolo: 'Codice', valore: r => r.codice },
                     { titolo: 'Origine', valore: r => ORIGINI[r.origine] || r.origine },
                     { titolo: 'Utente', valore: r => r.utente || '' },
                     { titolo: 'Richiesta', valore: r => r.richiesta },
                     { titolo: 'Tipo', valore: r => r.tipo },
                     { titolo: 'Messaggio', valore: r => r.messaggio, larghezza: 60 }
                   ]} />
        </div>
      </div>
      <Risultati p={p} vuoto={<Vuoto icona={AlertTriangle} titolo="Nessun errore imprevisto">Bene così. Se qualcuno riceve un codice E-…, cercalo qui.</Vuoto>}
                 intestazioni={['Quando', 'Codice', 'Origine', 'Utente', 'Cosa è successo']}
                 riga={r => (
                   <tr key={r.id} className="cursor-pointer align-top hover:bg-carta/60" onClick={() => setAperto(r)}>
                     <Quando valore={r.quando} />
                     <td className="whitespace-nowrap px-4 py-2.5">
                       <button type="button" className="font-mono text-[13px] font-semibold text-verde-700 underline-offset-2 hover:underline"
                               onClick={e => { e.stopPropagation(); setAperto(r) }}>{r.codice}</button>
                     </td>
                     <td className="px-4 py-2.5 text-grafite">{ORIGINI[r.origine] || r.origine}</td>
                     <td className="px-4 py-2.5 text-pino-900">{r.utente || <span className="text-nebbia">sistema</span>}</td>
                     <td className="max-w-md px-4 py-2.5 text-grafite">
                       {r.richiesta && <span className="block font-mono text-[12px] text-pino-900">{r.richiesta}</span>}
                       <span className="line-clamp-2">{r.messaggio}</span>
                     </td>
                   </tr>
                 )} />
      <Cassetto aperto={!!aperto} onClose={() => setAperto(null)} icona={AlertTriangle} larghezza="max-w-[640px]"
                titolo={aperto?.codice} sottotitolo={aperto && `${ORIGINI[aperto.origine] || aperto.origine}, ${dataOraCompleta(aperto.quando)}`}>
        {aperto && (
          <dl className="space-y-4 text-sm">
            {[['Utente', aperto.utente], ['Richiesta', aperto.richiesta], ['Da', aperto.indirizzo], ['Tipo', aperto.tipo]].filter(([, v]) => v).map(([k, v]) => (
              <div key={k}><dt className="text-[13px] text-grafite">{k}</dt><dd className="break-all font-mono text-[13px] text-pino-900">{v}</dd></div>
            ))}
            <div><dt className="text-[13px] text-grafite">Messaggio</dt><dd className="whitespace-pre-wrap text-pino-900">{aperto.messaggio}</dd></div>
            {aperto.traccia && (
              <div>
                <dt className="text-[13px] text-grafite">Traccia per chi sviluppa</dt>
                <dd><pre className="mt-1 max-h-[50vh] overflow-auto rounded-blocco bg-carta p-3 font-mono text-[12px] leading-relaxed text-pino-900">{aperto.traccia}</pre></dd>
              </div>
            )}
          </dl>
        )}
      </Cassetto>
    </>
  )
}

function Ricerca({ valore, onChange, segnaposto, etichetta }) {
  return (
    <label className="relative w-full sm:w-80">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-nebbia" />
      <input className="campo pl-9" value={valore} onChange={e => onChange(e.target.value)} placeholder={segnaposto} aria-label={etichetta} />
    </label>
  )
}

const Quando = ({ valore }) => <td className="whitespace-nowrap px-4 py-2.5 text-grafite" title={dataOraCompleta(valore)}>{quando(valore)}</td>
const Indirizzo = ({ valore }) => <td className="whitespace-nowrap px-4 py-2.5 font-mono text-[12px] text-grafite">{valore}</td>

/** Tabella con caricamento, errore, vuoto e pagine. */
function Risultati({ p, vuoto, intestazioni, riga }) {
  const { ris, errore, totale, pagina, setPagina } = p
  const pagine = Math.max(1, Math.ceil(totale / DIMENSIONE))
  return (
    <>
      <Errore>{errore}</Errore>
      {!ris && !errore && <Caricamento />}
      {ris && ris.righe.length === 0 && vuoto}
      {ris && ris.righe.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-linea text-[13px] text-grafite">
              <tr>{intestazioni.map(t => <th key={t} className="px-4 py-2.5 font-medium">{t}</th>)}</tr>
            </thead>
            <tbody className="divide-y divide-linea">{ris.righe.map(riga)}</tbody>
          </table>
        </div>
      )}
      {ris && totale > DIMENSIONE && (
        <nav className="flex items-center justify-between gap-3 border-t border-linea px-4 py-3 text-sm text-grafite" aria-label="Pagine">
          <span className="numeri">{pagina * DIMENSIONE + 1}–{Math.min(totale, (pagina + 1) * DIMENSIONE)} di {totale.toLocaleString('it-IT')}</span>
          <div className="flex items-center gap-1">
            <button type="button" onClick={() => setPagina(x => x - 1)} disabled={pagina === 0} aria-label="Pagina precedente" className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronLeft className="size-4" /></button>
            <span className="numeri min-w-20 text-center text-pino-900">Pagina {pagina + 1} di {pagine}</span>
            <button type="button" onClick={() => setPagina(x => x + 1)} disabled={pagina + 1 >= pagine} aria-label="Pagina successiva" className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronRight className="size-4" /></button>
          </div>
        </nav>
      )}
    </>
  )
}
