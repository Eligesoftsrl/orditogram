import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarClock, ChartColumn, ChartNoAxesColumn, Gauge, Route, Table2 } from 'lucide-react'
import { Selettore } from './ui.jsx'
import { cx } from '../lib/format.js'
import { altezzaBarra, etichettaGiorno, etichettaSettimana, fasceAvanzamento } from '../lib/grafici.js'
import { gl } from '../lib/glossario.js'

/**
 * I grafici della scrivania, sui numeri delle sole pratiche che l'utente vede (GET /api/pratiche/indicatori).
 * Un solo tono (Blu Italia) per le grandezze; due serie solo in "aperte e chiuse", con legenda e valori.
 * Le barre crescono dalla base (ferme con "riduci il movimento"); ogni grafico ha la sua tabella a richiesta.
 */
export default function GraficiScrivania({ dati }) {
  if (!dati || (dati.inCorso === 0 && dati.concluse30 === 0)) return null
  return (
    <section aria-labelledby="grafici-titolo" className="space-y-4">
      <h2 id="grafici-titolo" className="text-xl font-bold">Come vanno {gl('pratica', 'i')}</h2>
      <div className="in-sequenza grid gap-4 lg:grid-cols-2 [&>*]:min-w-0">
        <DoveSiTrovano processi={dati.processi} />
        <Avanzamento dati={dati} />
        <Settimane settimane={dati.settimane} />
        <Termini termini={dati.termini} />
      </div>
    </section>
  )
}

function Riquadro({ titolo, icona: Icona, sottotitolo, tabella, children, className }) {
  const [vediTabella, setVediTabella] = useState(false)
  return (
    <section className={cx('rounded-pannello border border-linea bg-white p-5 shadow-pannello', className)}>
      <header className="mb-4 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-verde-50 text-verde-700"><Icona className="size-[18px]" aria-hidden="true" /></span>
          <div>
            <h3 className="text-[16px] font-semibold leading-tight">{titolo}</h3>
            {sottotitolo && <p className="text-[13px] text-grafite">{sottotitolo}</p>}
          </div>
        </div>
        {tabella && (
          <Selettore etichetta={`Come vedere: ${titolo}`} attiva={vediTabella ? 'tabella' : 'grafico'} onChange={(v) => setVediTabella(v === 'tabella')}
                     voci={[{ id: 'grafico', label: 'Grafico', icona: ChartNoAxesColumn }, { id: 'tabella', label: 'Tabella', icona: Table2 }]} />
        )}
      </header>
      {vediTabella ? <Tabella {...tabella} /> : children}
    </section>
  )
}

function Tabella({ colonne, righe }) {
  return (
    <div className="max-h-72 overflow-auto">
      <table className="w-full text-left text-sm">
        <thead><tr className="text-grafite">{colonne.map(c => <th key={c} className="px-2 py-1.5 font-semibold">{c}</th>)}</tr></thead>
        <tbody className="numeri">{righe.map((r, i) => <tr key={i} className="border-t border-linea">{r.map((v, j) => <td key={j} className="px-2 py-1.5">{v}</td>)}</tr>)}</tbody>
      </table>
    </div>
  )
}

/** Per ogni processo: la barra di avanzamento medio e quante pratiche sono ferme a ogni passo. */
function DoveSiTrovano({ processi }) {
  const conPratiche = processi.filter(p => p.inCorso > 0).slice(0, 4)
  const righe = conPratiche.flatMap(p => p.passi.map(s => [p.nome, s.nome, s.pratiche]))
  return (
    <Riquadro titolo="Dove si trovano" icona={Route} sottotitolo={`${gl('pratica', 'nomi')} in corso per passo del flusso`}
              tabella={{ colonne: ['Processo', 'Passo', gl('pratica', 'Nomi')], righe }}>
      {conPratiche.length === 0 ? <p className="text-grafite">Niente in corso.</p> : (
        <div className="space-y-6">
          {conPratiche.map(p => {
            const max = Math.max(1, ...p.passi.map(s => s.pratiche))
            return (
              <div key={p.chiave} className="space-y-2.5">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <Link to={`/pratiche?processo=${encodeURIComponent(p.chiave)}`} className="font-semibold text-pino-900 hover:underline">{p.nome}</Link>
                  <span className="numeri text-sm text-grafite">{p.inCorso} in corso{p.durataMediaGiorni != null && ` · chiuse in ${p.durataMediaGiorni < 1 ? 'meno di un giorno' : `${String(p.durataMediaGiorni).replace('.', ',')} giorni`} in media`}</span>
                </div>
                {p.avanzamentoMedio != null && (
                  <div className="flex items-center gap-3" role="img" aria-label={`Avanzamento medio stimato ${p.avanzamentoMedio}%`}>
                    <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-verde-50">
                      <div className="cresce-x h-full rounded-full bg-verde-600" style={{ width: `${p.avanzamentoMedio}%` }} />
                    </div>
                    <span className="numeri w-12 text-right text-sm font-semibold">{p.avanzamentoMedio}%</span>
                  </div>
                )}
                <ol className="space-y-1.5">
                  {p.passi.map((s, i) => (
                    <li key={s.chiave} className="grid grid-cols-[minmax(0,10rem)_minmax(0,1fr)_2.5rem] items-center gap-3 text-sm" title={`${s.nome}: ${s.pratiche} ${gl('pratica', 'nomi')}`}>
                      <span className="truncate text-pino-800">{s.nome}</span>
                      <span className="h-5 rounded-[6px] bg-carta">
                        <span className="cresce-x block h-full rounded-[6px] bg-verde-400" style={{ width: `${(s.pratiche / max) * 100}%`, '--i': i }} />
                      </span>
                      <span className="numeri text-right font-semibold">{s.pratiche}</span>
                    </li>
                  ))}
                </ol>
              </div>
            )
          })}
        </div>
      )}
    </Riquadro>
  )
}

/** Quante pratiche in corso sono a che punto: quattro fasce, dal blu chiaro al blu pieno. */
function Avanzamento({ dati }) {
  const fasce = fasceAvanzamento(dati.avanzamento)
  const max = Math.max(1, ...fasce.map(f => f.n))
  // dal chiaro allo scuro, tutti almeno 3:1 sul bianco (WCAG 1.4.11)
  const toni = ['bg-verde-400', 'bg-verde-500', 'bg-verde-600', 'bg-verde-700']
  return (
    <Riquadro titolo="Avanzamento" icona={Gauge} sottotitolo="stima: attività fatte sul totale del processo"
              tabella={{ colonne: ['Avanzamento', gl('pratica', 'Nomi')], righe: fasce.map(f => [f.etichetta, f.n]) }}>
      <div className="flex h-40 items-end gap-3" role="img" aria-label={fasce.map(f => `${f.etichetta}: ${f.n}`).join(', ')}>
        {fasce.map((f, i) => (
          <div key={f.etichetta} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5" title={`${f.etichetta}: ${f.n} ${gl('pratica', 'nomi')}`}>
            <span className="numeri text-sm font-semibold">{f.n}</span>
            <span className={cx('cresce-y block w-full rounded-t-[6px] rounded-b-[2px]', toni[i])} style={{ height: altezzaBarra(f.n, max, 110), '--i': i }} />
            <span className="text-[13px] text-grafite">{f.etichetta}</span>
          </div>
        ))}
      </div>
    </Riquadro>
  )
}

/** Aperte e chiuse nelle ultime otto settimane: due barre affiancate, con legenda. */
function Settimane({ settimane }) {
  const max = Math.max(1, ...settimane.flatMap(s => [s.aperte, s.chiuse]))
  return (
    <Riquadro titolo="Aperte e chiuse" icona={ChartColumn} sottotitolo="ultime 8 settimane"
              tabella={{ colonne: ['Settimana', 'Aperte', 'Chiuse'], righe: settimane.map(s => [etichettaSettimana(s.dal), s.aperte, s.chiuse]) }}>
      <div className="mb-3 flex gap-4 text-[13px] text-pino-800">
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-verde-600" aria-hidden="true" /> aperte</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-pino-300" aria-hidden="true" /> chiuse</span>
      </div>
      <div className="flex h-36 items-end gap-2" role="img"
           aria-label={settimane.map(s => `settimana del ${etichettaSettimana(s.dal)}: ${s.aperte} aperte, ${s.chiuse} chiuse`).join('; ')}>
        {settimane.map((s, i) => (
          <div key={s.dal} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5" title={`Settimana del ${etichettaSettimana(s.dal)}: ${s.aperte} aperte, ${s.chiuse} chiuse`}>
            <div className="flex min-h-0 w-full flex-1 items-end justify-center gap-[2px]">
              <span className="cresce-y block w-1/2 max-w-4 rounded-t-[4px] bg-verde-600" style={{ height: altezzaBarra(s.aperte, max, 112), '--i': i }} />
              <span className="cresce-y block w-1/2 max-w-4 rounded-t-[4px] bg-pino-300" style={{ height: altezzaBarra(s.chiuse, max, 112), '--i': i }} />
            </div>
            <span className="text-[11px] text-grafite">{etichettaSettimana(s.dal)}</span>
          </div>
        ))}
      </div>
    </Riquadro>
  )
}

/** Termini e solleciti dei processi nei prossimi 14 giorni; la prima colonna comprende quelli già passati. */
function Termini({ termini }) {
  const max = Math.max(1, ...termini.map(t => t.termini))
  const totale = termini.reduce((a, t) => a + t.termini, 0)
  return (
    <Riquadro titolo="Termini in arrivo" icona={CalendarClock} sottotitolo={`${totale} nei prossimi 14 giorni (termini e solleciti)`}
              tabella={{ colonne: ['Giorno', 'Termini'], righe: termini.map((t, i) => [etichettaGiorno(t.giorno, i), t.termini]) }}>
      <div className="flex h-36 items-end gap-1.5" role="img" aria-label={termini.map((t, i) => `${etichettaGiorno(t.giorno, i)}: ${t.termini}`).join(', ')}>
        {termini.map((t, i) => (
          <div key={t.giorno} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1" title={`${etichettaGiorno(t.giorno, i)}: ${t.termini}`}>
            {t.termini > 0 && <span className="numeri text-[11px] font-semibold">{t.termini}</span>}
            <span className={cx('cresce-y block w-full rounded-t-[4px]', i === 0 ? 'bg-zafferano-500' : 'bg-verde-400')}
                  style={{ height: altezzaBarra(t.termini, max, 100), '--i': i }} />
            <span className={cx('whitespace-nowrap text-[11px]', i === 0 ? 'font-semibold text-zafferano-800' : 'text-grafite', i % 2 && 'max-sm:invisible')}>{i === 0 ? 'oggi' : etichettaGiorno(t.giorno, i).split(' ')[0]}</span>
          </div>
        ))}
      </div>
      <p className="mt-2 text-[13px] text-grafite"><span className="font-semibold text-zafferano-800">◷ oggi</span> comprende anche i termini già passati.</p>
    </Riquadro>
  )
}
