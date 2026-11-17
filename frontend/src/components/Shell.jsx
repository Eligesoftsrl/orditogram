import { api } from '../api.js'
import { createContext, useContext, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { BookUser, Building2, Briefcase, Database, FolderOpen, Inbox, KeyRound, ListChecks, LogOut, Menu, Plug, Plus, ScrollText, ShieldCheck, Users, Workflow, X } from 'lucide-react'
import { useAuth } from '../lib/auth.jsx'
import { cx } from '../lib/format.js'
import { descriviUtente, sezioniStudio } from '../lib/permessi.js'
import Logo from './Logo.jsx'
import NotificationBell from './NotificationBell.jsx'
import StartDialog from './StartDialog.jsx'
import { Avatar, Button } from './ui.jsx'

const AvvioContext = createContext(() => {})
/** Apre la finestra "Nuova pratica" da qualunque pagina. */
export const useAvvio = () => useContext(AvvioContext)

export default function Shell() {
  const [versione, setVersione] = useState(null)
  useEffect(() => { api.info().then(setVersione).catch(() => {}) }, [])
  const { me, modo, logout } = useAuth()
  // la sezione Anagrafiche compare a chi vede almeno un tipo (il server decide; gli esterni ricevono 403)
  const [conAnagrafiche, setConAnagrafiche] = useState(false)
  useEffect(() => {
    if (me.esterno) return
    api.tipiAnagrafica().then(t => setConAnagrafiche(t.length > 0)).catch(() => setConAnagrafiche(false))
  }, [me])
  const [menu, setMenu] = useState(false)
  const [avvio, setAvvio] = useState({ aperto: false, chiave: null })
  const location = useLocation()
  const apriAvvio = (chiave = null) => setAvvio({ aperto: true, chiave })

  const nav = [
    { to: '/', label: 'Scrivania', icona: Inbox, end: true },
    { to: '/pratiche', label: 'Pratiche', icona: FolderOpen },
    ...(conAnagrafiche ? [{ to: '/anagrafiche', label: 'Anagrafiche', icona: BookUser }] : []),
    // il referente gestisce le persone della sua organizzazione
    ...(me.referenteDi?.length ? [{ to: '/organizzazione', label: 'La mia organizzazione', icona: Briefcase }] : [])
  ]
  const icone = { processi: Workflow, form: ListChecks, anagrafiche: Database, utenti: Users, ruoli: ShieldCheck, strutture: Building2, organizzazioni: Briefcase, integrazioni: Plug, registro: ScrollText }
  const studio = sezioniStudio(me).map(s => ({ ...s, icona: icone[s.chiave] }))

  const Voce = ({ v }) => (
    <NavLink to={v.to} end={v.end} onClick={() => setMenu(false)}
      className={({ isActive }) => cx('group flex items-center gap-3 rounded-blocco px-3 py-2 text-[15px] font-semibold transition-colors',
        isActive ? 'bg-verde-50 text-verde-700 shadow-[inset_3px_0_0_var(--color-verde-600)]' : 'text-pino-900 hover:bg-carta')}>
      {({ isActive }) => (<>
        <v.icona className={cx('size-[18px]', isActive ? 'text-verde-600' : 'text-grafite group-hover:text-pino-900')} />
        {v.label}
      </>)}
    </NavLink>
  )

  // menu laterale chiaro, come la sidebar di Bootstrap Italia
  const barra = (
    <div className="flex h-full flex-col overflow-y-auto border-r border-linea bg-white px-3 py-5">
      <div className="flex items-center justify-between px-2 lg:hidden">
        <Logo chiaro={false} />
        <button className="rounded-full p-1.5 text-grafite hover:bg-carta" onClick={() => setMenu(false)} aria-label="Chiudi menu"><X className="size-5" /></button>
      </div>

      <Button variante="primario" icona={Plus} className="mx-1 mt-6 lg:mt-1" onClick={() => { setMenu(false); apriAvvio() }}>
        Nuova pratica
      </Button>

      <nav className="mt-6 space-y-0.5" aria-label="Lavoro">
        {nav.map(v => <Voce key={v.to} v={v} />)}
      </nav>

      {studio.length > 0 && (
        <nav className="mt-7 space-y-0.5" aria-label="Studio">
          <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-[.06em] text-grafite">Studio</p>
          {studio.map(v => <Voce key={v.to} v={v} />)}
        </nav>
      )}

      <div className="mt-auto pt-6">
        <div className="flex items-center gap-3 rounded-blocco bg-carta px-2.5 py-2.5">
          <Avatar nome={me.fullName} className="bg-verde-600" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-[15px] font-semibold text-pino-900">{me.fullName}</p>
            <p className="truncate text-[13px] text-grafite">{descriviUtente(me)}</p>
          </div>
          {modo === 'locale' && (
            <NavLink to="/password" onClick={() => setMenu(false)} className="rounded-full p-2 text-grafite hover:bg-white hover:text-pino-900"
                     aria-label="Cambia password" title="Cambia password">
              <KeyRound className="size-[18px]" />
            </NavLink>
          )}
          <button onClick={logout} className="rounded-full p-2 text-grafite hover:bg-white hover:text-pino-900" aria-label="Esci" title="Esci">
            <LogOut className="size-[18px]" />
          </button>
        </div>
        {versione && <p className="px-2 pt-2 text-[11px] text-grafite" title={versione.build ? `Build ${versione.build}` : ''}>Orditogram {versione.versione}</p>}
      </div>
    </div>
  )

  return (
    <AvvioContext.Provider value={apriAvvio}>
      <a href="#contenuto" className="sr-only z-[70] rounded-campo bg-white px-4 py-2 font-semibold text-verde-700 focus:not-sr-only focus:fixed focus:left-4 focus:top-4">
        Vai al contenuto
      </a>
      {/* intestazione a fasce come nei siti della PA */}
      <header className="fixed inset-x-0 top-0 z-30 bg-verde-600 text-white">
        <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
          <button className="rounded-full p-2 hover:bg-white/15 lg:hidden" onClick={() => setMenu(true)} aria-label="Apri menu"><Menu className="size-5" /></button>
          <Logo />
          <div className="ml-auto flex items-center gap-2">
            <Button variante="chiaro" misura="sm" icona={Plus} className="lg:hidden" onClick={() => apriAvvio()}>Nuova</Button>
            <NotificationBell suBlu />
          </div>
        </div>
      </header>

      <div className="min-h-screen pt-16 lg:pl-[256px]">
        <aside className="fixed bottom-0 left-0 top-16 z-20 hidden w-[256px] lg:block">{barra}</aside>

        {menu && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <button type="button" aria-label="Chiudi il menu" className="absolute inset-0 animate-velo cursor-default bg-pino-950/40" onClick={() => setMenu(false)} />
            <aside className="absolute inset-y-0 left-0 w-[280px] animate-[cassetto-sinistra_.28s_var(--ease-morbido)]">{barra}</aside>
          </div>
        )}

        <main id="contenuto" tabIndex={-1} key={location.pathname}
              className={location.pathname.startsWith('/studio/editor') ? 'outline-none' : 'mx-auto max-w-[1280px] px-4 pb-16 pt-7 outline-none sm:px-8'}>
          <Outlet />
        </main>
      </div>
      <StartDialog aperto={avvio.aperto} chiaveIniziale={avvio.chiave} onClose={() => setAvvio({ aperto: false, chiave: null })} />
    </AvvioContext.Provider>
  )
}
