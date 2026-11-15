import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, ScrollText, Search } from 'lucide-react'
import { api } from '../../api.js'
import { dataOraCompleta, quando } from '../../lib/format.js'
import { Badge, Caricamento, Errore, Vuoto } from '../../components/ui.jsx'
import Esporta from '../../components/Esporta.jsx'

export const AZIONI = {
  PROCESSO_PUBBLICATO: ['Processo pubblicato', 'verde'],
  FORM_SALVATO: ['Form salvato', 'neutro'],
  FORM_ELIMINATO: ['Form eliminato', 'mattone'],
  UTENTE_CREATO: ['Utente creato', 'verde'],
  UTENTE_MODIFICATO: ['Utente modificato', 'neutro'],
  UTENTE_ELIMINATO: ['Utente eliminato', 'mattone'],
  BOZZA_SCARTATA: ['Bozza scartata', 'neutro'],
  PACCHETTO_ESPORTATO: ['Pacchetto esportato', 'neutro'],
  PACCHETTO_IMPORTATO: ['Pacchetto importato', 'zafferano'],
  ACCESSO_PROVIDER: ['Accesso con provider', 'neutro'],
  ACCESSO_NEGATO: ['Accesso negato', 'mattone'],
  CHIAVE_API_CREATA: ['Chiave API creata', 'verde'],
  CHIAVE_API_MODIFICATA: ['Chiave API modificata', 'neutro'],
  CHIAVE_API_RUOTATA: ['Chiave API rinnovata', 'zafferano'],
  CHIAVE_API_REVOCATA: ['Chiave API revocata', 'mattone'],
  WEBHOOK_CREATO: ['Webhook creato', 'verde'],
  WEBHOOK_MODIFICATO: ['Webhook modificato', 'neutro'],
  WEBHOOK_ELIMINATO: ['Webhook eliminato', 'mattone'],
  WEBHOOK_SEGRETO_LETTO: ['Segreto webhook letto', 'zafferano'],
  WEBHOOK_SEGRETO_RIGENERATO: ['Segreto webhook rigenerato', 'zafferano']
}
const etichetta = (a) => AZIONI[a]?.[0] || a
const DIMENSIONE = 50

/** Studio: registro delle azioni di amministrazione e di sicurezza, con ricerca ed esportazione. */
export default function Registro() {
  const [q, setQ] = useState('')
  const [azione, setAzione] = useState('')
  const [pagina, setPagina] = useState(0)
  const [ris, setRis] = useState(null)
  const [errore, setErrore] = useState('')

  useEffect(() => { setPagina(0) }, [q, azione])
  useEffect(() => {
    setRis(null)
    const t = setTimeout(() => api.registro({ q, azione, pagina, dimensione: DIMENSIONE }).then(setRis).catch(e => setErrore(e.message)), q ? 250 : 0)
    return () => clearTimeout(t)
  }, [q, azione, pagina])

  const caricaTutte = async () => {
    const out = []
    for (let p = 0; out.length < 50000; p++) {
      const r = await api.registro({ q, azione, pagina: p, dimensione: 1000 })
      out.push(...r.righe)
      if (out.length >= r.totale || r.righe.length === 0) break
    }
    return out
  }
  const totale = ris?.totale ?? 0
  const pagine = Math.max(1, Math.ceil(totale / DIMENSIONE))

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Registro</h1>
          <p className="mt-1 max-w-2xl text-grafite">Chi ha pubblicato processi, modificato form e utenti, importato pacchetti; gli accessi negati. Le azioni sulle pratiche sono nello storico di ciascuna pratica.</p>
        </div>
        <Esporta nome="registro" titolo="Registro delle azioni" disabled={!totale}
                 sottotitolo={[azione && etichetta(azione), q && `ricerca "${q}"`].filter(Boolean).join(', ')}
                 totale={totale} carica={caricaTutte}
                 colonne={[
                   { titolo: 'Quando', valore: r => dataOraCompleta(r.quando) },
                   { titolo: 'Utente', valore: r => r.utenteNome ? `${r.utenteNome} (${r.utente})` : r.utente || '' },
                   { titolo: 'Azione', valore: r => etichetta(r.azione) },
                   { titolo: 'Oggetto', valore: r => r.oggetto },
                   { titolo: 'Dettagli', valore: r => r.dettagli, larghezza: 50 },
                   { titolo: 'Indirizzo', valore: r => r.indirizzo }
                 ]} />
      </header>

      <div className="rounded-pannello border border-linea bg-white shadow-pannello">
        <div className="flex flex-wrap gap-2 border-b border-linea p-3">
          <select className="campo w-full sm:w-60" value={azione} onChange={e => setAzione(e.target.value)} aria-label="Tipo di azione">
            <option value="">Tutte le azioni</option>
            {Object.entries(AZIONI).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <label className="relative w-full sm:w-80">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-nebbia" />
            <input className="campo pl-9" value={q} onChange={e => setQ(e.target.value)} placeholder="Cerca utente, oggetto, dettagli" aria-label="Cerca nel registro" />
          </label>
        </div>
        <Errore>{errore}</Errore>
        {!ris && !errore && <Caricamento />}
        {ris && ris.righe.length === 0 && <Vuoto icona={ScrollText} titolo="Nessuna azione registrata">Qui compaiono le azioni di amministrazione appena vengono fatte.</Vuoto>}
        {ris && ris.righe.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-linea text-[13px] text-grafite">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Quando</th>
                  <th className="px-4 py-2.5 font-medium">Utente</th>
                  <th className="px-4 py-2.5 font-medium">Azione</th>
                  <th className="px-4 py-2.5 font-medium">Oggetto</th>
                  <th className="px-4 py-2.5 font-medium">Dettagli</th>
                  <th className="px-4 py-2.5 font-medium">Da</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-linea">
                {ris.righe.map(r => (
                  <tr key={r.id} className="align-top hover:bg-carta/60">
                    <td className="whitespace-nowrap px-4 py-2.5 text-grafite" title={dataOraCompleta(r.quando)}>{quando(r.quando)}</td>
                    <td className="px-4 py-2.5 text-pino-900">{r.utenteNome || r.utente || <span className="text-nebbia">sistema</span>}</td>
                    <td className="px-4 py-2.5"><Badge tono={AZIONI[r.azione]?.[1] || 'neutro'}>{etichetta(r.azione)}</Badge></td>
                    <td className="px-4 py-2.5 font-mono text-[13px] text-pino-900">{r.oggetto}</td>
                    <td className="max-w-md whitespace-pre-line px-4 py-2.5 text-grafite">{r.dettagli}</td>
                    <td className="whitespace-nowrap px-4 py-2.5 font-mono text-[12px] text-grafite">{r.indirizzo}</td>
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
      </div>
    </div>
  )
}
