import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { creaGlossario } from './glossario.js'

const GlossarioContext = createContext({ g: creaGlossario(null), info: null, ricarica: () => {} })

/**
 * Carica una volta /api/info (versione e glossario, pubblico: serve anche alla pagina di accesso).
 * Finché non risponde valgono le parole predefinite.
 */
export function GlossarioProvider({ children }) {
  const [info, setInfo] = useState(null)
  const ricarica = () => api.info().then(setInfo).catch(() => {})
  useEffect(() => { ricarica() }, [])
  const valore = useMemo(() => ({ g: creaGlossario(info?.glossario), info, ricarica }), [info])
  return <GlossarioContext.Provider value={valore}>{children}</GlossarioContext.Provider>
}

/** g('ente', 'del') → "dell'ente" con le parole dell'installazione (vedi lib/glossario.js). */
export const useGlossario = () => useContext(GlossarioContext).g

/** Versione e glossario come arrivano da /api/info, più ricarica() dopo una modifica nello Studio. */
export const useInfo = () => useContext(GlossarioContext)
