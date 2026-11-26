import { useEffect, useState } from 'react'
import { KeyRound } from 'lucide-react'
import { api } from '../api.js'
import { useAuth } from '../lib/auth.jsx'
import Logo from '../components/Logo.jsx'
import { Button, Errore } from '../components/ui.jsx'
import { useGlossario } from '../lib/useGlossario.jsx'
import { gl } from '../lib/glossario.js'

const FERMATE = ['Avvio', 'Verifica', 'Revisione', 'Accettata']

const PROVA = [
  { u: 'laura', ruolo: 'Dirigente', nome: 'Laura Verdi' },
  { u: 'paolo', ruolo: 'Analista', nome: 'Paolo Neri' },
  { u: 'giulia', ruolo: 'Revisore', nome: 'Giulia Russo' },
  { u: 'admin', ruolo: 'Amministratore', nome: 'admin / admin' }
]

export default function Login() {
  const g = useGlossario()
  const { login } = useAuth()
  const [user, setUser] = useState('')
  const [pass, setPass] = useState('')
  const [errore, setErrore] = useState('')
  const [attesa, setAttesa] = useState(false)
  const [metodi, setMetodi] = useState({ locale: true, oidc: [], utentiProva: false })
  const [versione, setVersione] = useState(null)

  useEffect(() => {
    api.info().then(i => setVersione(i.versione)).catch(() => {})
    api.metodiAccesso().then(setMetodi).catch(() => setMetodi({ locale: true, oidc: [], utentiProva: true }))
    // ritorno dal provider con un errore (utente disattivato, non abilitato, accesso annullato)
    const p = new URLSearchParams(window.location.search)
    if (p.get('errore')) {
      setErrore('Accesso con il provider non riuscito: l\'utente potrebbe non essere abilitato a Orditogram. Contatta l\'amministratore.')
      window.history.replaceState(null, '', window.location.pathname + window.location.hash)
    }
  }, [])

  const entra = async (e) => {
    e.preventDefault()
    setAttesa(true); setErrore('')
    try { await login(user.trim(), pass) } catch (err) { setErrore(err.message) } finally { setAttesa(false) }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      {/* --- il binario: una pratica che va in revisione, torna in verifica e viene accettata */}
      <section className="relative hidden overflow-hidden bg-pino-900 px-14 py-12 text-white lg:flex lg:flex-col">
        <Logo />
        <div className="my-auto max-w-xl">
          <h1 className="font-display text-[56px] font-semibold leading-[1.02] tracking-[-0.03em]">
            Ogni {gl('pratica')}<br />sa dove si trova.
          </h1>
          <p className="mt-5 max-w-md text-lg leading-relaxed text-pino-200">
            Disegni il processo, Orditogram lo fa funzionare: attività, documenti firmati, notifiche e storico, senza scrivere codice.
          </p>

          <div className="mt-16" aria-hidden="true">
            <div className="relative mx-3 h-1 rounded-full bg-pino-700">
              <div className="absolute inset-y-0 left-0 right-0 rounded-full bg-[linear-gradient(90deg,var(--color-verde-400),var(--color-verde-400)_66%,var(--color-pino-600))]" />
              {FERMATE.map((f, i) => (
                <span key={f} className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-pino-900 bg-verde-400"
                      style={{ left: `${(i / (FERMATE.length - 1)) * 100}%` }} />
              ))}
              <span className="absolute top-1/2 size-6 -translate-x-1/2 -translate-y-1/2 animate-treno rounded-full border-[3px] border-pino-900 bg-zafferano-400 shadow-[0_0_0_6px_rgb(242_169_59/0.18)]" />
            </div>
            <div className="relative mx-3 mt-5 h-6">
              {FERMATE.map((f, i) => (
                <span key={f} className="absolute -translate-x-1/2 whitespace-nowrap text-sm text-pino-200"
                      style={{ left: `${(i / (FERMATE.length - 1)) * 100}%` }}>{f}</span>
              ))}
            </div>
          </div>
        </div>
        <p className="text-sm text-pino-300">Spring Boot, Activiti, PostgreSQL. Open source, sui vostri server.{versione && ` Versione ${versione}.`}</p>
      </section>

      {/* --- accesso */}
      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-10 lg:hidden"><Logo chiaro={false} /></div>
          <h2 className="text-3xl font-semibold">Accedi</h2>
          <p className="mt-1.5 text-grafite">
            {metodi.oidc.length && !metodi.locale ? `Entra con l'account ${g('ente', 'del')}.` : metodi.oidc.length ? `Entra con l'account ${g('ente', 'del')} oppure con le credenziali di Orditogram.` : 'Entra con le credenziali che ti ha dato l\'amministratore.'}
          </p>

          {metodi.oidc.length > 0 && (
            <div className="mt-8 space-y-2">
              {metodi.oidc.map(o => (
                <a key={o.id} href={o.url}
                   className="flex h-11 w-full items-center justify-center gap-2 rounded-campo border border-pino-900 bg-pino-900 px-4 font-medium text-white hover:bg-pino-800">
                  <KeyRound className="size-4" /> Accedi con {o.nome}
                </a>
              ))}
            </div>
          )}
          {metodi.oidc.length > 0 && metodi.locale && (
            <div className="my-6 flex items-center gap-3 text-[13px] text-grafite"><span className="h-px flex-1 bg-linea" /> oppure <span className="h-px flex-1 bg-linea" /></div>
          )}
          {!metodi.locale && <div className="mt-4"><Errore>{errore}</Errore></div>}

          {metodi.locale && <form onSubmit={entra} className={metodi.oidc.length ? 'space-y-4' : 'mt-8 space-y-4'}>
            <div>
              <label htmlFor="u" className="etichetta">Nome utente</label>
              <input id="u" className="campo h-11" autoComplete="username" autoFocus value={user} onChange={e => setUser(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="p" className="etichetta">Password</label>
              <input id="p" type="password" className="campo h-11" autoComplete="current-password" value={pass} onChange={e => setPass(e.target.value)} required />
            </div>
            <Errore>{errore}</Errore>
            <Button type="submit" variante="primario" misura="lg" className="w-full" attesa={attesa}>Accedi</Button>
          </form>}

          {metodi.utentiProva && <div className="mt-10 border-t border-linea pt-6">
            <p className="text-sm font-medium text-pino-800">Utenti di prova</p>
            <p className="mt-0.5 text-[13px] text-grafite">Password "password", tranne admin.</p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {PROVA.map(p => (
                <button key={p.u} type="button"
                        onClick={() => { setUser(p.u); setPass(p.u === 'admin' ? 'admin' : 'password') }}
                        className="rounded-campo border border-linea bg-white px-3 py-2 text-left hover:border-pino-200">
                  <span className="block text-[15px] font-medium text-pino-900">{p.ruolo}</span>
                  <span className="block text-[13px] text-grafite">{p.nome}</span>
                </button>
              ))}
            </div>
          </div>}
        </div>
      </section>
    </div>
  )
}
