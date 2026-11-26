import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowRight, ChevronLeft, ChevronRight, CircleCheckBig, FolderOpen, Layers, Search, Timer } from 'lucide-react'
import { api } from '../api.js'
import { dataOraCompleta, fa, quando } from '../lib/format.js'
import IterRail from '../components/IterRail.jsx'
import { Avatar, Badge, Button, Caricamento, Cassetto, Errore, IntestazionePagina, TesseraFiltro, Vuoto } from '../components/ui.jsx'
import { BadgeScadenza } from '../components/Scadenza.jsx'
import Esporta from '../components/Esporta.jsx'
import AnteprimaPratica from '../components/AnteprimaPratica.jsx'
import { gl } from '../lib/glossario.js'

const COLONNE_EXPORT = [
  { titolo: 'Titolo', valore: p => p.titolo, larghezza: 40 },
  { titolo: 'Tipologia', valore: p => p.processo, larghezza: 24 },
  { titolo: 'Stato', valore: p => p.stato === 'conclusa' ? 'Conclusa' : 'In corso' },
  { titolo: 'A che punto è', valore: p => p.stato === 'conclusa' ? '' : (p.stepCorrenti || []).map(s => s.nome).join(', ') || ((p.messaggiAttesi || []).length ? `In attesa: ${p.messaggiAttesi[0].elemento}` : (p.scadenze || []).some(x => x.tipo === 'attesa') ? 'In attesa' : '') },
  { titolo: 'In carico a', valore: p => (p.stepCorrenti || []).map(s => s.assegnatario || 'disponibile per il gruppo').join(', ') },
  { titolo: 'Avviata da', valore: p => p.avviataDa },
  { titolo: 'Avvio', valore: p => dataOraCompleta(p.inizio) },
  { titolo: 'Conclusione', valore: p => dataOraCompleta(p.fine) },
  { titolo: 'Prossima scadenza', valore: p => p.prossimaScadenza ? `${dataOraCompleta(p.prossimaScadenza.quando)} (${p.prossimaScadenza.nome})` : '' }
]

const DIMENSIONI = [25, 50, 100]
// le tessere-filtro in cima all'elenco: tinta per argomento, non per stato (CLAUDE.md, Interfaccia)
const STATI = [
  { id: 'in-corso', label: 'In corso', icona: Timer, tono: 'acqua' },
  { id: 'concluse', label: 'Concluse', icona: CircleCheckBig, tono: 'lilla' },
  { id: 'tutte', label: 'Tutte', icona: Layers, tono: 'neutro' }
]
const MAX_ESPORTAZIONE = 20000

/** Tutte le pratiche visibili all'utente: ricerca e pagine sul server, filtro per tipologia. */
export default function Pratiche() {
  const [stato, setStato] = useState('in-corso')
  const [q, setQ] = useState('')
  // ?processo=<chiave> arriva dai grafici della scrivania
  const [cerca] = useSearchParams()
  const [processo, setProcesso] = useState(() => cerca.get('processo') || '')
  const [pagina, setPagina] = useState(0)
  const [dimensione, setDimensione] = useState(25)
  const [risultato, setRisultato] = useState(null)   // { righe, totale }
  const [tipologie, setTipologie] = useState([])
  const [errore, setErrore] = useState('')
  const lista = risultato?.righe || null
  // anteprima nel pannello laterale; Ctrl/Cmd + clic o clic centrale aprono la pagina come prima
  const [anteprima, setAnteprima] = useState(null)
  const navigate = useNavigate()

  useEffect(() => { api.tipologie().then(setTipologie).catch(() => {}) }, [])
  // i numeri delle tessere-filtro: stessa ricerca e tipologia, un conteggio per stato (una riga sola per chiamata)
  const [conteggi, setConteggi] = useState({})
  useEffect(() => {
    let annullato = false
    const t = setTimeout(() => {
      Promise.all(STATI.map(st => api.pratiche(st.id, q, { processo, dimensione: 1 }).then(r => [st.id, r.totale]).catch(() => [st.id, null])))
        .then(v => !annullato && setConteggi(Object.fromEntries(v)))
    }, q ? 250 : 0)
    return () => { annullato = true; clearTimeout(t) }
  }, [q, processo])
  // un filtro nuovo riparte dalla prima pagina
  useEffect(() => { setPagina(0) }, [stato, q, processo, dimensione])
  useEffect(() => {
    setRisultato(null); setErrore('')
    const t = setTimeout(() => {
      api.pratiche(stato, q, { processo, pagina, dimensione }).then(setRisultato).catch(e => setErrore(e.message))
    }, q ? 250 : 0)
    return () => clearTimeout(t)
  }, [stato, q, processo, pagina, dimensione])

  // esportazione: tutte le righe della ricerca, a blocchi da 1000
  const caricaTutte = async () => {
    const out = []
    for (let p = 0; out.length < MAX_ESPORTAZIONE; p++) {
      const r = await api.pratiche(stato, q, { processo, pagina: p, dimensione: 1000, esporta: true })
      out.push(...r.righe)
      if (out.length >= r.totale || r.righe.length === 0) break
    }
    return out
  }
  const totale = risultato?.totale ?? 0
  const pagine = Math.max(1, Math.ceil(totale / dimensione))

  return (
    <div className="in-sequenza space-y-6">
      <IntestazionePagina percorso={[{ label: gl('pratica', 'Nomi') }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">{gl('pratica', 'Nomi')}</h1>
          <p className="mt-1 text-grafite">{gl('pratica', 'I')} che hai avviato, su cui hai lavorato o che aspettano il tuo gruppo.</p>
        </div>
        <Esporta nome="pratiche" titolo={gl('pratica', 'Nomi')} disabled={!lista || totale === 0}
                 sottotitolo={[{ 'in-corso': 'In corso', concluse: 'Concluse', tutte: 'Tutte' }[stato],
                   processo && (tipologie.find(t => t.chiave === processo)?.nome || processo), q && `ricerca "${q}"`].filter(Boolean).join(', ')}
                 colonne={COLONNE_EXPORT} totale={totale} carica={caricaTutte} />
      </IntestazionePagina>

      <div className="in-sequenza grid gap-3 sm:grid-cols-3" role="group" aria-label={`Quali ${gl('pratica', 'nomi')} vedere`}>
        {STATI.map(st => (
          <TesseraFiltro key={st.id} tono={st.tono} icona={st.icona} attiva={stato === st.id} onClick={() => setStato(st.id)}
                         etichetta={st.id === 'tutte' ? `Tutt${gl.agg('pratica', '', true)} ${gl('pratica', 'i')}` : st.label} valore={conteggi[st.id]} />
        ))}
      </div>

      <div className="rounded-pannello border border-linea bg-white shadow-pannello">
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
          <h2 className="text-lg font-semibold">{stato === 'tutte' ? `Tutt${gl.agg('pratica', '', true)} ${gl('pratica', 'i')}` : `${gl('pratica', 'Nomi')} ${stato === 'concluse' ? gl.agg('pratica', 'conclus', true) : 'in corso'}`}</h2>
          <div className="flex w-full flex-wrap gap-2 sm:w-auto">
            {tipologie.length > 1 && (
              <select className="campo w-full rounded-full sm:w-56" value={processo} onChange={e => setProcesso(e.target.value)} aria-label={`Tipologia di ${gl('pratica', 'nome')}`}>
                <option value="">Tutte le tipologie</option>
                {tipologie.map(t => <option key={t.chiave} value={t.chiave}>{t.nome}</option>)}
              </select>
            )}
            <label className="relative w-full sm:w-72">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-nebbia" />
              <input className="campo rounded-full pl-9" placeholder="Cerca per titolo o tipologia" value={q} onChange={e => setQ(e.target.value)} aria-label={`Cerca ${gl('pratica', 'nomi')}`} />
            </label>
          </div>
        </div>

        <div className="border-t border-linea">
          {errore && <div className="p-4"><Errore>{errore}</Errore></div>}
          {!lista && !errore && <Caricamento />}
          {lista && lista.length === 0 && (
            <Vuoto icona={FolderOpen} titolo={q ? `Nessun risultato per "${q}"` : 'Ancora niente qui'}>
              {q || processo ? 'Prova con un\'altra parola o un\'altra tipologia.' : `${gl('pratica', 'I')} compaiono appena le avvii o ci lavori.`}
            </Vuoto>
          )}
          {lista && lista.length > 0 && (
            <ul className="in-sequenza divide-y divide-linea">
              {lista.map(p => (
                <li key={p.id}>
                  <Link to={`/pratiche/${p.id}`} aria-haspopup="dialog"
                        onClick={(e) => { if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return; e.preventDefault(); setAnteprima(p) }}
                        className="grid gap-x-6 gap-y-2 px-5 py-[18px] transition-colors hover:bg-verde-50/60 md:grid-cols-[minmax(0,2fr)_auto_minmax(0,1.4fr)_auto] md:items-center">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-pino-900">{p.titolo}</p>
                      <p className="mt-0.5 truncate text-[13px] text-grafite">{p.processo}{p.avviataDa ? `, avviata da ${p.avviataDa}` : ''}</p>
                    </div>
                    <IterRail percorso={p.percorso} compatto />
                    <div className="min-w-0 text-sm">
                      {p.stato === 'conclusa'
                        ? <span className="text-grafite">Conclusa il {quando(p.fine)}</span>
                        : p.stepCorrenti.length === 0 && p.messaggiAttesi?.some(x => x.tipo === 'attesa')
                        ? <span className="text-grafite">In attesa: {p.messaggiAttesi.find(x => x.tipo === 'attesa').elemento}</span>
                        : p.stepCorrenti.length === 0 && p.scadenze?.some(x => x.tipo === 'attesa')
                        ? <span className="text-grafite">In attesa, riprende il {quando(p.scadenze.find(x => x.tipo === 'attesa').quando)}</span>
                        : <>
                            <span className="block font-semibold text-pino-900">{p.stepCorrenti.map(s => s.nome).join(', ')}</span>
                            {p.stepCorrenti[0] && (
                              <span className="mt-1 flex items-center gap-2 text-grafite">
                                {p.stepCorrenti[0].assegnatario
                                  ? <><Avatar nome={p.stepCorrenti[0].assegnatario} className="size-6 bg-verde-600 text-[11px]" />{p.stepCorrenti[0].assegnatario}</>
                                  : 'Disponibile per il gruppo'}
                              </span>
                            )}
                          </>}
                    </div>
                    <div className="flex items-center gap-3 md:justify-end">
                      {p.stato === 'conclusa' ? <Badge tono="pino">Conclusa</Badge>
                        : p.prossimaScadenza ? <BadgeScadenza s={p.prossimaScadenza} conNome={false} />
                        : <Badge tono="blu">In corso</Badge>}
                      <span className="whitespace-nowrap text-[13px] text-grafite">{fa(p.inizio)}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
        {lista && totale > 0 && (
          <nav className="flex flex-wrap items-center justify-between gap-3 border-t border-linea px-5 py-3 text-sm text-grafite" aria-label="Pagine">
            <span className="numeri">
              {pagina * dimensione + 1}–{Math.min(totale, (pagina + 1) * dimensione)} di {totale.toLocaleString('it-IT')} {totale === 1 ? gl('pratica') : gl('pratica', 'nomi')}
            </span>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2">
                Per pagina
                <select className="campo h-8 w-auto py-0" value={dimensione} onChange={e => setDimensione(+e.target.value)}>
                  {DIMENSIONI.map(d => <option key={d} value={d}>{d}</option>)}
                </select>
              </label>
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => setPagina(p => p - 1)} disabled={pagina === 0} aria-label="Pagina precedente"
                        className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronLeft className="size-4" /></button>
                <span className="numeri min-w-20 text-center text-pino-900">Pagina {pagina + 1} di {pagine}</span>
                <button type="button" onClick={() => setPagina(p => p + 1)} disabled={pagina + 1 >= pagine} aria-label="Pagina successiva"
                        className="inline-flex size-8 items-center justify-center rounded-md hover:bg-carta disabled:opacity-35"><ChevronRight className="size-4" /></button>
              </div>
            </div>
          </nav>
        )}
      </div>

      <Cassetto aperto={!!anteprima} onClose={() => setAnteprima(null)} icona={FolderOpen}
                titolo={anteprima?.titolo || ''} sottotitolo={anteprima?.processo}
                piede={<>
                  <Button onClick={() => setAnteprima(null)}>Chiudi</Button>
                  <Button variante="primario" icona={ArrowRight} onClick={() => navigate(`/pratiche/${anteprima.id}`)}>Apri la pratica</Button>
                </>}>
        {anteprima && <AnteprimaPratica p={anteprima} />}
      </Cassetto>
    </div>
  )
}
