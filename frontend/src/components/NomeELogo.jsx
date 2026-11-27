import { useEffect, useId, useRef, useState } from 'react'
import { ImagePlus, Save, Trash2 } from 'lucide-react'
import { api } from '../api.js'
import { useInfo } from '../lib/useGlossario.jsx'
import Logo from './Logo.jsx'
import { Badge, Button, Caricamento, Errore, Pannello, useToast } from './ui.jsx'

/**
 * Nome dell'applicazione e logo del cliente (solo amministratore). Senza, restano quelli di Orditogram.
 * Il logo è un'immagine PNG, JPEG o WebP fino a 512 KB; valgono subito per tutti, pagina di accesso compresa.
 */
export default function NomeELogo() {
  const uid = useId()
  const toast = useToast()
  const file = useRef(null)
  const { ricarica } = useInfo()
  const [stato, setStato] = useState(null)
  const [nome, setNome] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState('')

  const prendi = (s) => { setStato(s); setNome(s.nomePredefinito ? '' : s.nome) }
  useEffect(() => {
    // una risposta arrivata dopo l'uscita dalla pagina (o il doppio avvio di React in sviluppo) non tocca ciò che si scrive
    let attivo = true
    api.personalizzazione().then(s => attivo && prendi(s)).catch(e => attivo && setErrore(e.message))
    return () => { attivo = false }
  }, [])

  const esegui = async (quale, azione, messaggio) => {
    setAttesa(quale); setErrore('')
    try {
      prendi(await azione())
      await ricarica()
      toast.ok(messaggio)
    } catch (e) { setErrore(e.message) } finally { setAttesa('') }
  }

  const salvaNome = (e) => {
    e.preventDefault()
    esegui('nome', () => api.salvaNomeApplicazione(nome), nome.trim() ? 'Nome salvato' : 'Torna il nome Orditogram')
  }
  const scegli = (e) => {
    const f = e.target.files?.[0]
    e.target.value = ''
    if (f) esegui('logo', () => api.caricaLogo(f), 'Logo salvato: lo vedono subito tutti')
  }

  if (!stato) return errore ? <Errore>{errore}</Errore> : <Caricamento />
  const cambiato = (stato.nomePredefinito ? '' : stato.nome) !== nome.trim()

  return (
    <Pannello titolo="Nome e logo" corpoClassName="grid gap-5 p-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <form onSubmit={salvaNome} className="space-y-3">
        <div>
          <label className="etichetta" htmlFor={`${uid}-nome`}>Nome dell'applicazione</label>
          <input id={`${uid}-nome`} className="campo" maxLength={60} placeholder="Orditogram" value={nome}
                 aria-describedby={`${uid}-nome-aiuto`} onChange={e => setNome(e.target.value)} />
          <p id={`${uid}-nome-aiuto`} className="mt-1 text-sm text-grafite">
            Compare nel menu, nella pagina di accesso e nel titolo della finestra. Vuoto: Orditogram.
          </p>
        </div>
        <Button variante="primario" icona={Save} type="submit" attesa={attesa === 'nome'} disabled={!cambiato}>Salva il nome</Button>
      </form>

      <div className="space-y-3">
        <p className="etichetta">Logo</p>
        <div className="flex flex-wrap items-center gap-3 rounded-blocco bg-carta p-4">
          <Logo chiaro={false} />
          {!stato.logo && <Badge>predefinito</Badge>}
        </div>
        <p className="text-sm text-grafite">Un'immagine PNG, JPEG o WebP fino a 512 KB, meglio orizzontale e su fondo trasparente.</p>
        <div className="flex flex-wrap gap-2">
          <input ref={file} id={`${uid}-logo`} type="file" accept="image/png,image/jpeg,image/webp" className="sr-only" onChange={scegli} />
          <Button variante="secondario" icona={ImagePlus} type="button" attesa={attesa === 'logo'} onClick={() => file.current?.click()}>
            {stato.logo ? 'Cambia il logo' : 'Carica il logo'}
          </Button>
          {stato.logo && (
            <Button variante="fantasma" icona={Trash2} type="button" attesa={attesa === 'togli'}
                    onClick={() => esegui('togli', api.togliLogo, 'Torna il marchio di Orditogram')}>
              Torna al marchio di Orditogram
            </Button>
          )}
        </div>
      </div>
      <div className="md:col-span-2"><Errore>{errore}</Errore></div>
    </Pannello>
  )
}
