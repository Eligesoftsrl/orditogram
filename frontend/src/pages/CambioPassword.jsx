import { useId, useState } from 'react'
import { KeyRound } from 'lucide-react'
import { useAuth } from '../lib/auth.jsx'
import { erroreNuovaPassword, MINIMO_PASSWORD } from '../lib/referente.js'
import Logo from '../components/Logo.jsx'
import { Button, Errore, Pannello, useToast } from '../components/ui.jsx'

/**
 * Cambio della propria password. "obbligatorio": primo accesso con una password data da altri (es. dal referente
 * dell'organizzazione), finché non la cambia l'utente non vede altro.
 */
export default function CambioPassword({ obbligatorio = false }) {
  const uid = useId()
  const toast = useToast()
  const { me, cambiaPassword, logout } = useAuth()
  const [attuale, setAttuale] = useState('')
  const [nuova, setNuova] = useState('')
  const [conferma, setConferma] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)

  const salva = async (e) => {
    e.preventDefault()
    const problema = erroreNuovaPassword(nuova, conferma, me.username, attuale)
    if (problema) { setErrore(problema); return }
    setAttesa(true); setErrore('')
    try {
      await cambiaPassword(attuale, nuova)
      toast.ok('Password cambiata')
      setAttuale(''); setNuova(''); setConferma('')
    } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  const modulo = (
    <form onSubmit={salva} className="space-y-4">
      <div>
        <label className="etichetta" htmlFor={`${uid}-a`}>{obbligatorio ? 'Password ricevuta' : 'Password attuale'}</label>
        <input id={`${uid}-a`} type="password" className="campo" autoComplete="current-password" required
               value={attuale} onChange={e => setAttuale(e.target.value)} />
      </div>
      <div>
        <label className="etichetta" htmlFor={`${uid}-n`}>Nuova password</label>
        <input id={`${uid}-n`} type="password" className="campo" autoComplete="new-password" required
               aria-describedby={`${uid}-h`} value={nuova} onChange={e => setNuova(e.target.value)} />
        <p id={`${uid}-h`} className="mt-1 text-sm text-grafite">Almeno {MINIMO_PASSWORD} caratteri, senza il tuo nome utente. Meglio una frase che una parola.</p>
      </div>
      <div>
        <label className="etichetta" htmlFor={`${uid}-c`}>Ripeti la nuova password</label>
        <input id={`${uid}-c`} type="password" className="campo" autoComplete="new-password" required
               value={conferma} onChange={e => setConferma(e.target.value)} />
      </div>
      <Errore>{errore}</Errore>
      <div className="flex items-center gap-2 pt-2">
        {obbligatorio && <Button type="button" variante="fantasma" onClick={logout}>Esci</Button>}
        <Button type="submit" variante="primario" icona={KeyRound} attesa={attesa} className="ml-auto">Cambia password</Button>
      </div>
    </form>
  )

  if (!obbligatorio) return (
    <div className="max-w-lg space-y-6">
      <h1 className="text-[34px] font-semibold leading-tight">Cambia password</h1>
      <Pannello>{modulo}</Pannello>
    </div>
  )

  return (
    <main className="flex min-h-screen items-center justify-center bg-carta px-6 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8"><Logo chiaro={false} /></div>
        <h1 className="text-3xl font-semibold">Scegli la tua password</h1>
        <p className="mt-2 text-grafite">
          Ciao {me.fullName}. Sei entrato con una password temporanea: prima di continuare scegline una che conosci solo tu.
        </p>
        <div className="mt-6">{modulo}</div>
      </div>
    </main>
  )
}
