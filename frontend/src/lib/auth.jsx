import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, hasCredentials, setCredentials, setOnUnauthorized } from '../api.js'

const AuthContext = createContext(null)

/**
 * Due modi di accesso:
 *  - locale: nome utente e password di Orditogram (Basic Auth, conservata per la scheda);
 *  - sessione: accesso con il provider dell'ente (Keycloak, Microsoft Entra ID), il server tiene la sessione.
 */
export function AuthProvider({ children }) {
  const [me, setMe] = useState(null)
  const [modo, setModo] = useState(null)
  const [pronto, setPronto] = useState(false)

  const logout = useCallback(() => {
    const eraSessione = modo === 'sessione'
    setCredentials(null)
    setMe(null)
    setModo(null)
    // con il provider si esce anche da lì (e si torna alla pagina di accesso)
    if (eraSessione) window.location.href = '/api/auth/logout'
  }, [modo])

  useEffect(() => { setOnUnauthorized(() => { setCredentials(null); setMe(null); setModo(null) }) }, [])

  // all'apertura: credenziali locali della scheda, oppure una sessione del provider già aperta
  useEffect(() => {
    const locale = hasCredentials()
    api.me()
      .then(u => { setMe(u); setModo(locale ? 'locale' : 'sessione') })
      .catch(() => { if (locale) setCredentials(null) })
      .finally(() => setPronto(true))
  }, [])

  const login = async (user, pass) => {
    setCredentials(user, pass)
    try {
      setMe(await api.me())
      setModo('locale')
    } catch (e) {
      setCredentials(null)
      throw e
    }
  }

  /** Cambia la propria password (login locale) e continua con la nuova, senza rifare l'accesso. */
  const cambiaPassword = async (attuale, nuova) => {
    await api.cambiaPassword(attuale, nuova)
    setCredentials(me.username, nuova)
    setMe(await api.me())
  }

  const ricarica = async () => setMe(await api.me())

  return <AuthContext.Provider value={{ me, modo, pronto, login, logout, cambiaPassword, ricarica }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
