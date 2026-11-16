import { api } from '../api.js'
import { createContext, useContext, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { FolderOpen, Inbox, ListChecks, LogOut, Menu, Plug, Plus, ScrollText, ShieldCheck, Users, Workflow, X } from 'lucide-react'
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
  const { me, logout } = useAuth()
  const [menu, setMenu] = useState(false)
  const [avvio, setAvvio] = useState({ aperto: false, chiave: null })
  const location = useLocation()
  const apriAvvio = (chiave = null) => setAvvio({ aperto: true, chiave })

  const nav = [
    { to: '/', label: 'Scrivania', icona: Inbox, end: true },
    { to: '/pratiche', label: 'Pratiche', icona: FolderOpen }
  ]
  const icone = { processi: Workflow, form: ListChecks, utenti: Users, ruoli: ShieldCheck, integrazioni: Plug, registro: ScrollText }
  const studio = sezioniStudio(me).map(s => ({ ...s, icona: icone[s.chiave] }))

  const Voce = ({ v }) => (
    <NavLink to={v.to} end={v.end} onClick={() => setMenu(false)}
      className={({ isActive }) => cx('group flex items-center gap-3 rounded-campo px-3 py-2 text-[15px] font-medium transition-colors',
        isActive ? 'bg-white/10 text-white' : 'text-pino-200 hover:bg-white/5 hover:text-white')}>
      {({ isActive }) => (<>
        <v.icona className={cx('size-[18px]', isActive ? 'text-zafferano-400' : 'text-pino-300 group-hover:text-pino-200')} />
        {v.label}
      </>)}
    </NavLink>
  )

  const barra = (
    <div className="flex h-full flex-col bg-pino-900 px-3 py-5">
      <div className="flex items-center justify-between px-2">
        <Logo />
        <button className="rounded-md p-1.5 text-pino-200 lg:hidden" onClick={() => setMenu(false)} aria-label="Chiudi menu"><X className="size-5" /></button>
      </div>

      <Button variante="primario" icona={Plus} className="mx-1 mt-7 bg-verde-500 hover:bg-verde-600" onClick={() => { setMenu(false); apriAvvio() }}>
        Nuova pratica
      </Button>

      <nav className="mt-6 space-y-0.5" aria-label="Lavoro">
        {nav.map(v => <Voce key={v.to} v={v} />)}
      </nav>

      {studio.length > 0 && (
        <nav className="mt-8 space-y-0.5" aria-label="Studio">
          <p className="px-3 pb-2 text-[13px] font-medium text-pino-300">Studio</p>
          {studio.map(v => <Voce key={v.to} v={v} />)}
        </nav>
      )}

      <div className="mt-auto flex items-center gap-3 rounded-campo px-2 py-2">
        <Avatar nome={me.fullName} className="bg-pino-600" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[15px] font-medium text-white">{me.fullName}</p>
          <p className="truncate text-[13px] text-pino-300">{descriviUtente(me)}</p>
        </div>
        <button onClick={logout} className="rounded-md p-2 text-pino-300 hover:bg-white/10 hover:text-white" aria-label="Esci" title="Esci">
          <LogOut className="size-[18px]" />
        </button>
      </div>
      {versione && <p className="px-2 pt-1 text-[11px] text-pino-400" title={versione.build ? `Build ${versione.build}` : ''}>Orditogram {versione.versione}</p>}
    </div>
  )

  return (
    <AvvioContext.Provider value={apriAvvio}>
      <div className="min-h-screen lg:pl-[248px]">
        <aside className="fixed inset-y-0 left-0 z-30 hidden w-[248px] lg:block">{barra}</aside>

        {menu && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <button type="button" aria-label="Chiudi il menu" className="absolute inset-0 cursor-default bg-pino-950/50" onClick={() => setMenu(false)} />
            <aside className="absolute inset-y-0 left-0 w-[272px] animate-entra">{barra}</aside>
          </div>
        )}

        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-linea/70 bg-carta/85 px-4 backdrop-blur sm:px-8">
          <button className="rounded-md p-2 text-pino-800 lg:hidden" onClick={() => setMenu(true)} aria-label="Apri menu"><Menu className="size-5" /></button>
          <span className="lg:hidden"><Logo chiaro={false} /></span>
          <div className="ml-auto flex items-center gap-2">
            <Button variante="primario" misura="sm" icona={Plus} className="lg:hidden" onClick={() => apriAvvio()}>Nuova</Button>
            <NotificationBell />
          </div>
        </header>

        <main key={location.pathname}
              className={location.pathname.startsWith('/studio/editor') ? '' : 'mx-auto max-w-[1280px] px-4 pb-16 pt-6 sm:px-8'}>
          <Outlet />
        </main>
      </div>
      <StartDialog aperto={avvio.aperto} chiaveIniziale={avvio.chiave} onClose={() => setAvvio({ aperto: false, chiave: null })} />
    </AvvioContext.Provider>
  )
}
