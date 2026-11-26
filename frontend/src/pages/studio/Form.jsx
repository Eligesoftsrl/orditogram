import { useEffect, useMemo, useState } from 'react'
import { ChevronDown, ChevronRight, FolderOpen, ListChecks, Plus, Search, Workflow } from 'lucide-react'
import { api } from '../../api.js'
import { cx, dataOraCompleta } from '../../lib/format.js'
import FormEditor from '../../components/FormEditor.jsx'
import { Button, Caricamento, Errore, Pannello, Vuoto, IntestazionePagina } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'
import { gl } from '../../lib/glossario.js'

const SENZA_PROCESSO = '__nessuno'

/**
 * Studio: i form raggruppati per tipologia di pratica (il processo che li usa), con ricerca,
 * e il costruttore visuale a destra. Un form usato da più processi compare in ciascun gruppo.
 */
export default function FormStudio() {
  const [lista, setLista] = useState(null)
  const [scelta, setScelta] = useState(null)   // { chiave, nuovo, n }
  const [errore, setErrore] = useState('')
  const [cerca, setCerca] = useState('')
  const [chiusi, setChiusi] = useState(() => new Set())

  const carica = () => api.forms().then(setLista).catch(e => setErrore(e.message))
  useEffect(() => { void carica() }, [])

  const gruppi = useMemo(() => raggruppa(lista || [], cerca), [lista, cerca])
  const apri = (k) => setScelta({ chiave: k, nuovo: false, n: Date.now() })
  const alterna = (g) => setChiusi(s => { const n = new Set(s); n.has(g) ? n.delete(g) : n.add(g); return n })

  return (
    <div className="in-sequenza space-y-6">
      <IntestazionePagina percorso={[{ label: 'Studio' }, { label: 'Form' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Form</h1>
          <p className="mt-1 max-w-2xl text-grafite">Le schermate che gli utenti compilano, raggruppate per tipologia di {gl('pratica', 'nome')}. Li puoi creare qui o direttamente dall'editor dei processi.</p>
        </div>
        <div className="flex gap-2">
          {lista && (
            <Esporta nome="form" titolo="Form"
                     colonne={[
                       { titolo: 'Form', valore: f => f.formKey },
                       { titolo: 'Titolo', valore: f => f.titolo },
                       { titolo: 'Campi', valore: f => f.campi },
                       { titolo: 'Usato in', valore: f => (f.uso || []).map(u => `${u.nome}: ${u.passo}`).join('; ') },
                       { titolo: 'Origine', valore: f => f.source === 'database' ? 'Studio' : 'Progetto' },
                       { titolo: 'Versione', valore: f => f.versione ?? '' },
                       { titolo: 'Modificato', valore: f => dataOraCompleta(f.updatedAt) }
                     ]}
                     righe={lista} />
          )}
          <Button variante="primario" icona={Plus} onClick={() => setScelta({ chiave: null, nuovo: true, n: Date.now() })}>Nuovo form</Button>
        </div>
      </IntestazionePagina>

      <Errore>{errore}</Errore>
      {!lista && !errore && <Caricamento />}

      {lista && (
        <div className="grid items-start gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
          <aside aria-label="Form disponibili" className="overflow-hidden rounded-pannello border border-linea bg-white shadow-pannello lg:sticky lg:top-6">
            <div className="border-b border-linea p-3">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-grafite" />
                <input className="campo h-9 pl-9" value={cerca} onChange={e => setCerca(e.target.value)}
                       placeholder="Cerca per nome, titolo, processo" aria-label="Cerca form" />
              </div>
              <p className="mt-2 px-1 text-xs text-grafite">{lista.length} form in {gruppi.filter(g => g.chiave !== SENZA_PROCESSO).length} tipologie di {gl('pratica')}</p>
            </div>
            <div className="max-h-[calc(100vh-15rem)] overflow-y-auto py-1">
              {gruppi.length === 0 && <p className="px-4 py-6 text-center text-sm text-grafite">Nessun form trovato.</p>}
              {gruppi.map(g => {
                const chiuso = chiusi.has(g.chiave) && !cerca
                const Icona = g.chiave === SENZA_PROCESSO ? FolderOpen : Workflow
                return (
                  <section key={g.chiave}>
                    <button type="button" onClick={() => alterna(g.chiave)} aria-expanded={!chiuso}
                            className="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-carta">
                      {chiuso ? <ChevronRight className="size-4 text-grafite" /> : <ChevronDown className="size-4 text-grafite" />}
                      <Icona className="size-4 text-verde-700" />
                      <span className="min-w-0 flex-1 truncate text-[13px] font-semibold uppercase tracking-wide text-pino-800">{g.nome}</span>
                      {g.bozza && <span className="rounded bg-zafferano-50 px-1.5 text-[11px] font-medium text-zafferano-800">bozza</span>}
                      <span className="text-xs text-grafite">{g.voci.length}</span>
                    </button>
                    {!chiuso && (
                      <ul className="pb-1">
                        {g.voci.map(({ f, passi }) => {
                          const attivo = scelta?.chiave === f.formKey && !scelta?.nuovo
                          return (
                            <li key={f.formKey}>
                              <button type="button" onClick={() => apri(f.formKey)} aria-current={attivo ? 'true' : undefined}
                                      className={cx('block w-full border-l-2 py-2 pl-9 pr-3 text-left transition-colors',
                                        attivo ? 'border-verde-600 bg-verde-50' : 'border-transparent hover:bg-carta')}>
                                <span className={cx('block truncate text-[15px]', attivo ? 'font-medium text-verde-700' : 'text-pino-900')}>{f.titolo || f.formKey}</span>
                                <span className="block truncate text-xs text-grafite">
                                  {passi.length ? passi.join(' · ') : <span className="font-mono">{f.formKey}</span>}
                                </span>
                              </button>
                            </li>
                          )
                        })}
                      </ul>
                    )}
                  </section>
                )
              })}
            </div>
          </aside>

          <div className="min-w-0">
            {!scelta
              ? <Pannello><Vuoto icona={ListChecks} titolo="Scegli un form o creane uno nuovo">Aggiungi i campi, scegli quali sono obbligatori o in sola lettura e guarda subito il risultato.</Vuoto></Pannello>
              : <FormEditor key={scelta.n} chiave={scelta.chiave} nuovo={scelta.nuovo}
                            onSalvato={(k) => { void carica(); setScelta(s => s.nuovo ? { chiave: k, nuovo: false, n: Date.now() } : s) }}
                            onEliminato={() => { void carica(); setScelta(null) }} />}
          </div>
        </div>
      )}
    </div>
  )
}

/** [{ chiave, nome, bozza, voci: [{ f, passi }] }]: processi in ordine alfabetico, poi i form non usati. */
function raggruppa(lista, cerca) {
  const q = cerca.trim().toLowerCase()
  const gruppi = new Map()
  const aggiungi = (chiave, nome, bozza, f, passo) => {
    if (!gruppi.has(chiave)) gruppi.set(chiave, { chiave, nome, bozza, voci: new Map() })
    const g = gruppi.get(chiave)
    if (!g.voci.has(f.formKey)) g.voci.set(f.formKey, { f, passi: [] })
    if (passo && !g.voci.get(f.formKey).passi.includes(passo)) g.voci.get(f.formKey).passi.push(passo)
  }
  for (const f of lista) {
    const testo = [f.formKey, f.titolo, ...(f.uso || []).flatMap(u => [u.nome, u.passo])].join(' ').toLowerCase()
    if (q && !testo.includes(q)) continue
    if (!f.uso?.length) aggiungi(SENZA_PROCESSO, 'Non usati in nessun processo', false, f, null)
    for (const u of f.uso || []) aggiungi(u.chiave, u.nome, u.bozza, f, u.passo)
  }
  return [...gruppi.values()]
    // dentro un processo i form restano nell'ordine del flusso (avvio, poi le attività)
    .map(g => ({ ...g, voci: [...g.voci.values()] }))
    .sort((a, b) => a.chiave === SENZA_PROCESSO ? 1 : b.chiave === SENZA_PROCESSO ? -1 : a.nome.localeCompare(b.nome, 'it'))
}
