import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../../api.js'
import { Caricamento, Errore, Schede } from '../../components/ui.jsx'
import ChiaviApi from '../../components/integrazioni/ChiaviApi.jsx'
import Webhook from '../../components/integrazioni/Webhook.jsx'
import DocumentazioneApi from '../../components/integrazioni/DocumentazioneApi.jsx'
import { gl } from '../../lib/glossario.js'

const SCHEDE = [
  { id: 'chiavi', label: 'Chiavi API' },
  { id: 'webhook', label: 'Webhook' },
  { id: 'documentazione', label: 'Documentazione API' }
]

/**
 * Studio → Integrazioni: come gli altri sistemi parlano con Orditogram (API pubblica con chiavi)
 * e come Orditogram avvisa gli altri sistemi (webhook). La documentazione si genera dai processi pubblicati.
 */
export default function Integrazioni() {
  const [parametri, setParametri] = useSearchParams()
  const scheda = SCHEDE.some(s => s.id === parametri.get('scheda')) ? parametri.get('scheda') : 'chiavi'
  const [panoramica, setPanoramica] = useState(null)
  const [errore, setErrore] = useState('')

  useEffect(() => { api.integrazioni().then(setPanoramica).catch(e => setErrore(e.message)) }, [])

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-[34px] font-semibold leading-tight">Integrazioni</h1>
        <p className="mt-1 max-w-3xl text-grafite">
          Portali, gestionali e app avviano {gl('pratica', 'nomi')} e ne seguono lo stato con l'API pubblica; Orditogram li avvisa con i webhook.
          Tutto quello che l'analista pubblica è subito disponibile anche per le integrazioni.
        </p>
      </header>
      <Schede voci={SCHEDE} attiva={scheda} onChange={(id) => setParametri({ scheda: id }, { replace: true })} />
      <Errore>{errore}</Errore>
      {!panoramica && !errore && <Caricamento />}
      {panoramica && scheda === 'chiavi' && <ChiaviApi panoramica={panoramica} />}
      {panoramica && scheda === 'webhook' && <Webhook panoramica={panoramica} />}
      {panoramica && scheda === 'documentazione' && <DocumentazioneApi panoramica={panoramica} />}
    </div>
  )
}
