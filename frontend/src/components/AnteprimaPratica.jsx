import { dataOraCompleta, quando } from '../lib/format.js'
import { Badge } from './ui.jsx'
import { BadgeScadenza } from './Scadenza.jsx'

/** Il riassunto della pratica nel pannello laterale: stato, a che punto è, chi ci lavora, il percorso. */
export default function AnteprimaPratica({ p }) {
  const conclusa = p.stato === 'conclusa'
  return (
    <div className="space-y-5">
      <div className="space-y-3 rounded-blocco bg-carta p-4">
        <div className="flex flex-wrap items-center gap-2">
          {conclusa ? <Badge tono="pino">Conclusa</Badge> : <Badge tono="blu">In corso</Badge>}
          {!conclusa && p.prossimaScadenza && <BadgeScadenza s={p.prossimaScadenza} />}
        </div>
        <dl className="grid grid-cols-[110px_minmax(0,1fr)] gap-x-3 gap-y-2 text-[15px]">
          {!conclusa && <>
            <dt className="text-grafite">Adesso</dt>
            <dd className="font-semibold">{p.stepCorrenti.map(s => s.nome).join(', ') || 'In attesa'}</dd>
            <dt className="text-grafite">In carico a</dt>
            <dd className="font-semibold">{p.stepCorrenti.map(s => s.assegnatario || 'disponibile per il gruppo').join(', ') || '—'}</dd>
          </>}
          {p.avviataDa && <><dt className="text-grafite">Avviata da</dt><dd className="font-semibold">{p.avviataDa}</dd></>}
          <dt className="text-grafite">Avvio</dt><dd className="font-semibold">{dataOraCompleta(p.inizio)}</dd>
          {conclusa && <><dt className="text-grafite">Conclusione</dt><dd className="font-semibold">{dataOraCompleta(p.fine)}</dd></>}
        </dl>
      </div>
      {p.percorso?.length > 0 && (
        <div className="space-y-3">
          <p className="text-xs font-semibold uppercase tracking-[.06em] text-grafite">Il percorso</p>
          <ol className="in-sequenza space-y-0">
            {p.percorso.map((f, i) => (
              <li key={i} className="relative grid grid-cols-[24px_minmax(0,1fr)] gap-3 pb-4 last:pb-0">
                {i < p.percorso.length - 1 && <span className="absolute left-[11px] top-6 bottom-0 w-0.5 bg-linea" aria-hidden="true" />}
                <span className={f.stato === 'corrente'
                  ? 'mt-0.5 size-6 animate-respiro rounded-full border-[3px] border-verde-600 bg-white'
                  : 'mt-0.5 size-6 rounded-full bg-verde-600'} aria-hidden="true" />
                <div>
                  <p className={f.stato === 'corrente' ? 'font-semibold text-verde-700' : 'font-semibold text-pino-900'}>{f.nome}</p>
                  <p className="text-[13px] text-grafite">
                    {f.stato === 'corrente' ? (f.chi ? `in corso, ${f.chi}` : 'in corso, in attesa di presa in carico') : [f.chi, f.quando && quando(f.quando)].filter(Boolean).join(', ')}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  )
}
