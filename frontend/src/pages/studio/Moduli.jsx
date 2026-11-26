import { useEffect, useState } from 'react'
import { BadgeCheck, CircleOff, Flag, Landmark, PenLine, Power } from 'lucide-react'
import { api } from '../../api.js'
import { quando } from '../../lib/format.js'
import { useInfo } from '../../lib/useGlossario.jsx'
import { Badge, Button, Caricamento, Dialogo, Errore, useToast } from '../../components/ui.jsx'
import { cx } from '../../lib/format.js'

const ICONE = { italia: Flag, 'firma-digitale': PenLine, pa: Landmark }
const TINTE = { italia: 'bg-acqua-50', 'firma-digitale': 'bg-lilla-50', pa: 'bg-verde-50' }

/**
 * Studio → Moduli (solo amministratore): le funzioni di paese e di settore. Il nucleo funziona sempre; un modulo
 * spento non cancella dati, cambia le regole da qui in avanti. Spegnere si conferma in una finestra.
 */
export default function Moduli() {
  const toast = useToast()
  const { ricarica } = useInfo()
  const [moduli, setModuli] = useState(null)
  const [errore, setErrore] = useState('')
  const [daSpegnere, setDaSpegnere] = useState(null)
  const [attesa, setAttesa] = useState(null)

  useEffect(() => { api.moduli().then(setModuli).catch(e => setErrore(e.message)) }, [])

  const imposta = async (m, acceso) => {
    setAttesa(m.codice); setErrore('')
    try {
      setModuli(await api.impostaModulo(m.codice, acceso))
      await ricarica()
      toast.ok(acceso ? `Modulo ${m.nome} acceso` : `Modulo ${m.nome} spento`)
      setDaSpegnere(null)
    } catch (e) { setErrore(e.message) } finally { setAttesa(null) }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-[34px] font-semibold leading-tight">Moduli</h1>
        <p className="mt-1 max-w-2xl text-grafite">
          Il nucleo di Orditogram vale per ogni settore. I moduli aggiungono le regole di un paese o di un settore:
          lascia acceso solo quello che serve a questa installazione. Spegnere un modulo non cancella niente.
        </p>
      </header>

      <Errore>{errore}</Errore>
      {!moduli ? (!errore && <Caricamento />) : (
        <ul className="in-sequenza grid gap-4 lg:grid-cols-3">
          {moduli.map((m, i) => {
            const Icona = ICONE[m.codice] || Power
            return (
              <li key={m.codice} className={cx('flex flex-col rounded-pannello border border-linea p-5 shadow-pannello',
                m.acceso ? TINTE[m.codice] || 'bg-white' : 'bg-white', i === 0 && 'lg:col-span-2')}>
                <div className="flex items-start justify-between gap-3">
                  <span className="grid size-11 place-items-center rounded-blocco bg-white text-verde-700"><Icona className="size-5" aria-hidden="true" /></span>
                  {m.acceso ? <Badge tono="verde" icona={BadgeCheck}>acceso</Badge> : <Badge icona={CircleOff}>spento</Badge>}
                </div>
                <h2 className="mt-4 text-xl font-semibold">{m.nome}</h2>
                <p className="mt-1 text-pino-800">{m.descrizione}</p>
                <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-pino-800">
                  {m.porta.map(p => <li key={p}>{p}</li>)}
                </ul>
                {!m.acceso && m.spentoIl && <p className="mt-3 text-sm text-grafite">Spento il {quando(m.spentoIl)}{m.spentoDa ? ` da ${m.spentoDa}` : ''}.</p>}
                <div className="mt-auto pt-5">
                  {m.acceso
                    ? <Button icona={Power} onClick={() => setDaSpegnere(m)}>Spegni</Button>
                    : <Button variante="primario" icona={Power} attesa={attesa === m.codice} onClick={() => imposta(m, true)}>Accendi</Button>}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <Dialogo aperto={!!daSpegnere} onClose={() => setDaSpegnere(null)} larghezza="max-w-lg"
               titolo={daSpegnere ? `Spegnere il modulo ${daSpegnere.nome}?` : ''}>
        {daSpegnere && (
          <div className="space-y-4">
            <p className="text-pino-800">Da subito, per tutti, smette di funzionare:</p>
            <ul className="list-disc space-y-1 pl-5 text-pino-800">{daSpegnere.porta.map(p => <li key={p}>{p}</li>)}</ul>
            <p className="rounded-blocco bg-carta px-3 py-2 text-sm text-grafite">
              I dati già salvati restano. Puoi riaccenderlo quando vuoi; la scelta resta nel registro.
            </p>
            <div className="flex flex-wrap justify-end gap-2">
              <Button onClick={() => setDaSpegnere(null)}>Annulla</Button>
              <Button variante="pericolo" icona={Power} attesa={attesa === daSpegnere.codice} onClick={() => imposta(daSpegnere, false)}>Spegni</Button>
            </div>
          </div>
        )}
      </Dialogo>
    </div>
  )
}
