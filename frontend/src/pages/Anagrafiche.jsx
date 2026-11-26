import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, BookUser } from 'lucide-react'
import { api } from '../api.js'
import { Badge, Caricamento, Errore, Vuoto, IntestazionePagina } from '../components/ui.jsx'
import { gl } from '../lib/glossario.js'

const AZIONI = { MODIFICA: 'puoi modificare', ESPORTA: 'puoi esportare' }

/** Le anagrafiche che l'utente può consultare: fornitori, fondi, immobili... secondo quelle definite nello Studio. */
export default function Anagrafiche() {
  const [tipi, setTipi] = useState(null)
  const [errore, setErrore] = useState('')
  useEffect(() => { api.tipiAnagrafica().then(setTipi).catch(e => setErrore(e.message)) }, [])

  return (
    <div className="space-y-6">
      <IntestazionePagina percorso={[{ label: 'Anagrafiche' }]}>
        <div>
          <h1 className="text-[34px] font-semibold leading-tight">Anagrafiche</h1>
          <p className="mt-1 max-w-2xl text-grafite">Gli elenchi che {gl('pratica', 'i')} usano. Cerca una scheda, consultala o aggiornala.</p>
        </div>
      </IntestazionePagina>
      {!tipi ? (errore ? <Errore>{errore}</Errore> : <Caricamento />) : tipi.length === 0 ? (
        <Vuoto icona={BookUser} titolo="Nessuna anagrafica">Non hai accesso ad alcuna anagrafica.</Vuoto>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {tipi.map(t => (
            <li key={t.codice}>
              <Link to={`/anagrafiche/${t.codice}`}
                    className="flex h-full flex-col rounded-pannello border border-linea bg-white p-5 shadow-pannello hover:border-pino-200">
                <span className="flex items-center gap-2">
                  <BookUser className="size-5 text-pino-600" aria-hidden="true" />
                  <span className="font-display text-lg font-semibold text-pino-900">{t.nome}</span>
                  <ArrowRight className="ml-auto size-4 text-nebbia" aria-hidden="true" />
                </span>
                {t.descrizione && <span className="mt-2 text-[15px] text-grafite">{t.descrizione}</span>}
                <span className="mt-auto flex flex-wrap gap-1.5 pt-3">
                  {!t.attivo && <Badge tono="mattone">non attiva</Badge>}
                  {Object.entries(AZIONI).filter(([a]) => t.azioni.includes(a)).map(([a, v]) => <Badge key={a}>{v}</Badge>)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
