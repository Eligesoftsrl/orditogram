import { useEffect, useState } from 'react'
import { Download, ExternalLink, FileText } from 'lucide-react'
import { leggiFile, scarica } from '../api.js'
import { genereAnteprima } from '../lib/anteprima.js'
import { dimensione } from '../lib/format.js'
import { Button, Caricamento, Cassetto, Errore, useToast } from './ui.jsx'

/**
 * Anteprima di un documento nel cassetto laterale: PDF, immagini e testo si leggono senza lasciare la pagina.
 * `url` è il file da mostrare (per le buste .p7m il contenuto estratto), `urlScarica` quello da scaricare.
 */
export default function AnteprimaDocumento({ aperto, onClose, url, urlScarica, titolo, sottotitolo }) {
  const toast = useToast()
  const [file, setFile] = useState(null)
  const [testo, setTesto] = useState('')
  const [errore, setErrore] = useState('')

  useEffect(() => {
    if (!aperto || !url) return
    let vivo = true
    let letto = null
    setFile(null); setErrore(''); setTesto('')
    leggiFile(url).then(async f => {
      letto = f
      if (!vivo) return f.revoca()
      if (genereAnteprima(f.tipo, f.nome) === 'testo') {
        const t = await (await fetch(f.href)).text()
        if (vivo) setTesto(t.slice(0, 200_000))
      }
      if (vivo) setFile(f)
    }).catch(e => vivo && setErrore(e.message))
    return () => { vivo = false; letto?.revoca() }
  }, [aperto, url])

  const genere = file && genereAnteprima(file.tipo, file.nome)
  const piede = (
    <>
      {file && genere && (
        <Button variante="fantasma" icona={ExternalLink} onClick={() => scarica(url, { apri: true }).catch(e => toast.errore(e.message))}>
          Apri in una nuova scheda
        </Button>
      )}
      <Button variante="primario" icona={Download} onClick={() => scarica(urlScarica || url).catch(e => toast.errore(e.message))}>
        Scarica
      </Button>
    </>
  )

  return (
    <Cassetto aperto={aperto} onClose={onClose} titolo={titolo} icona={FileText} larghezza="max-w-[920px]" piede={piede}
              sottotitolo={sottotitolo || (file ? [file.nome !== titolo && file.nome, dimensione(file.byte)].filter(Boolean).join(' · ') : undefined)}>
      {errore ? <Errore>{errore}</Errore>
        : !file ? <Caricamento />
        : genere === 'pdf' ? (
          <iframe src={file.href} title={`Anteprima di ${file.nome}`}
                  className="h-[calc(100dvh-220px)] min-h-[420px] w-full animate-entra rounded-blocco border border-linea bg-carta" />
        ) : genere === 'immagine' ? (
          <div className="flex animate-entra justify-center rounded-blocco bg-carta p-4">
            <img src={file.href} alt={`Anteprima di ${file.nome}`} className="max-h-[calc(100dvh-260px)] max-w-full rounded-campo object-contain" />
          </div>
        ) : genere === 'testo' ? (
          <pre className="max-h-[calc(100dvh-240px)] animate-entra overflow-auto whitespace-pre-wrap break-words rounded-blocco bg-carta p-4 font-mono text-[13px] text-pino-900">{testo}</pre>
        ) : (
          <div className="rounded-blocco bg-carta px-5 py-8 text-center">
            <p className="font-medium text-pino-900">Questo tipo di file non si può vedere qui.</p>
            <p className="mt-1 text-sm text-grafite">Scaricalo e aprilo con il programma adatto.</p>
          </div>
        )}
    </Cassetto>
  )
}
