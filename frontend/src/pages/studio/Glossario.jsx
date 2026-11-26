import { useEffect, useId, useMemo, useState } from 'react'
import { BookA, RotateCcw, Save } from 'lucide-react'
import { api } from '../../api.js'
import { creaGlossario } from '../../lib/glossario.js'
import { useInfo } from '../../lib/useGlossario.jsx'
import { Badge, Button, Caricamento, Errore, Pannello, useToast } from '../../components/ui.jsx'

/**
 * Studio → Glossario (solo amministratore): le parole con cui questa installazione chiama ente, ufficio e
 * utenti esterni. Un'azienda sceglie "azienda", "Assistenza", "cliente"; un ente pubblico lascia i predefiniti.
 * Vale subito per tutti, anche nella pagina di accesso. Ogni modifica va nel registro.
 */
export default function Glossario() {
  const uid = useId()
  const toast = useToast()
  const { ricarica } = useInfo()
  const [termini, setTermini] = useState(null)
  const [bozza, setBozza] = useState({})
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const prendi = (lista) => {
    setTermini(lista)
    setBozza(Object.fromEntries(lista.map(t => [t.chiave, { singolare: t.singolare, plurale: t.plurale, genere: t.genere }])))
  }
  useEffect(() => { api.glossario().then(prendi).catch(e => setErrore(e.message)) }, [])

  const anteprima = useMemo(() => creaGlossario(bozza), [bozza])
  const cambiato = termini?.some(t => {
    const b = bozza[t.chiave] || {}
    return b.singolare !== t.singolare || b.plurale !== t.plurale || b.genere !== t.genere
  })
  const set = (chiave, campo, valore) => setBozza(p => ({ ...p, [chiave]: { ...p[chiave], [campo]: valore } }))
  const predefinito = (t) => setBozza(p => ({ ...p, [t.chiave]: { singolare: t.singolarePredefinito, plurale: t.pluralePredefinito, genere: t.generePredefinito } }))

  const salva = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try {
      prendi(await api.salvaGlossario(bozza))
      await ricarica()
      toast.ok('Glossario salvato: le nuove parole valgono subito per tutti')
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-[34px] font-semibold leading-tight">Glossario</h1>
        <p className="mt-1 max-w-2xl text-grafite">
          Le parole con cui Orditogram chiama chi lo usa e chi entra da fuori. Un'azienda può scegliere "azienda",
          "Assistenza" e "cliente"; un ente pubblico lascia quelle predefinite. Valgono subito per tutti.
        </p>
      </header>

      {!termini ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : (
        <form onSubmit={salva} className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
          <Pannello titolo="Parole" corpoClassName="in-sequenza space-y-4 p-5"
                    azioni={<Button variante="primario" icona={Save} type="submit" attesa={attesa} disabled={!cambiato}>Salva</Button>}>
            {termini.map(t => {
              const b = bozza[t.chiave] || {}
              const uguale = b.singolare === t.singolarePredefinito && b.plurale === t.pluralePredefinito && b.genere === t.generePredefinito
              return (
                <fieldset key={t.chiave} className="rounded-blocco bg-carta p-4">
                  <legend className="sr-only">{t.nome}</legend>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-semibold text-pino-900">{t.nome}</p>
                    {uguale ? <Badge>predefinito</Badge>
                      : <Button variante="fantasma" misura="sm" icona={RotateCcw} type="button" onClick={() => predefinito(t)}>
                          Torna a "{t.singolarePredefinito}"
                        </Button>}
                  </div>
                  <p className="mt-0.5 text-sm text-grafite">{t.aiuto}</p>
                  <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
                    <div>
                      <label className="etichetta" htmlFor={`${uid}-${t.chiave}-s`}>Singolare</label>
                      <input id={`${uid}-${t.chiave}-s`} className="campo" required maxLength={60} value={b.singolare || ''}
                             onChange={e => set(t.chiave, 'singolare', e.target.value)} />
                    </div>
                    <div>
                      <label className="etichetta" htmlFor={`${uid}-${t.chiave}-p`}>Plurale</label>
                      <input id={`${uid}-${t.chiave}-p`} className="campo" required maxLength={60} value={b.plurale || ''}
                             onChange={e => set(t.chiave, 'plurale', e.target.value)} />
                    </div>
                    <div>
                      <label className="etichetta" htmlFor={`${uid}-${t.chiave}-g`}>Genere</label>
                      <select id={`${uid}-${t.chiave}-g`} className="campo" value={b.genere || 'M'} onChange={e => set(t.chiave, 'genere', e.target.value)}>
                        <option value="M">maschile</option>
                        <option value="F">femminile</option>
                      </select>
                    </div>
                  </div>
                </fieldset>
              )
            })}
            <Errore>{errore}</Errore>
          </Pannello>

          <Pannello titolo={<h2 className="flex items-center gap-2 text-lg font-semibold"><BookA className="size-5 text-verde-600" aria-hidden="true" /> Come si leggerà</h2>}
                    corpoClassName="space-y-3 p-5 text-[15px]">
            <p className="rounded-blocco bg-acqua-50 px-4 py-3">Entra con l'account {anteprima('ente', 'del')}.</p>
            <p className="rounded-blocco bg-lilla-50 px-4 py-3">{anteprima('esterno', 'Nome')} o referente di un'organizzazione: vede solo le sue pratiche.</p>
            <p className="rounded-blocco bg-carta px-4 py-3">Nello storico della pratica gli utenti esterni leggono: <strong>{anteprima('ufficio')}</strong> ha completato l'attività.</p>
          </Pannello>
        </form>
      )}
    </div>
  )
}
