import { api } from '../api.js'
import { createContext, useContext, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Blocks, BookA, BookUser, Building2, Briefcase, Database, FolderOpen, Inbox, KeyRound, ListChecks, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Plug, Plus, ScrollText, ShieldCheck, Users, Workflow, X } from 'lucide-react'
import { useAuth } from '../lib/auth.jsx'
import { useInfo } from '../lib/useGlossario.jsx'
import { cx } from '../lib/format.js'
import { descriviUtente, sezioniStudio } from '../lib/permessi.js'
import Logo from './Logo.jsx'
import NotificationBell from './NotificationBell.jsx'
import StartDialog from './StartDialog.jsx'
import { Avatar, Button } from './ui.jsx'
import { gl, nuovaPratica } from '../lib/glossario.js'

const AvvioContext = createContext(() => {})
/** Apre la finestra "Nuova pratica" da qualunque pagina. */
export const useAvvio = () => useContext(AvvioContext)

export default function Shell() {
  const { info: versione } = useInfo()
  const { me, modo, logout } = useAuth()
  // la sezione Anagrafiche compare a chi vede almeno un tipo (il server decide; gli esterni ricevono 403)
  const [conAnagrafiche, setConAnagrafiche] = useState(false)
  useEffect(() => {
    if (me.esterno) return
    api.tipiAnagrafica().then(t => setConAnagrafiche(t.length > 0)).catch(() => setConAnagrafiche(false))
  }, [me])
  const [menu, setMenu] = useState(false)
  // menu laterale stretto (solo icone) o largo; la scelta resta nel browser
  const [stretto, setStretto] = useState(() => { try { return localStorage.getItem('orditogram.menuStretto') === '1' } catch { return false } })
  const cambiaLarghezza = () => setStretto(v => { try { localStorage.setItem('orditogram.menuStretto', v ? '0' : '1') } catch { /* niente memoria */ } return !v })
  const [avvio, setAvvio] = useState({ aperto: false, chiave: null })
  const location = useLocation()
  const apriAvvio = (chiave = null) => setAvvio({ aperto: true, chiave })

  const nav = [
    { to: '/', label: 'Scrivania', icona: Inbox, end: true },
    { to: '/pratiche', label: gl('pratica', 'Nomi'), icona: FolderOpen },
    ...(conAnagrafiche ? [{ to: '/anagrafiche', label: 'Anagrafiche', icona: BookUser }] : []),
    // il referente gestisce le persone della sua organizzazione
    ...(me.referenteDi?.length ? [{ to: '/organizzazione', label: 'La mia organizzazione', icona: Briefcase }] : [])
  ]
  const icone = { processi: Workflow, form: ListChecks, anagrafiche: Database, utenti: Users, ruoli: ShieldCheck, strutture: Building2, organizzazioni: Briefcase, integrazioni: Plug, registro: ScrollText, glossario: BookA, moduli: Blocks }
  const studio = sezioniStudio(me).map(s => ({ ...s, icona: icone[s.chiave] }))

  // `ridotta`: solo l'icona, con il nome come etichetta accessibile e suggerimento
  // voce attiva come nella proposta approvata: fondo azzurro, testo blu e barretta blu a sinistra
  const Voce = ({ v, ridotta }) => (
    <NavLink to={v.to} end={v.end} onClick={() => setMenu(false)} aria-label={ridotta ? v.label : undefined} title={ridotta ? v.label : undefined}
      className={({ isActive }) => cx('group relative flex h-11 items-center gap-3 rounded-blocco text-[15px] font-semibold transition-[background-color,color] duration-200',
        ridotta ? 'justify-center px-0' : 'px-3',
        isActive ? 'bg-verde-50 text-verde-700 before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-verde-600'
          : 'text-pino-800 hover:bg-carta hover:text-verde-700')}>
      <v.icona className="size-[19px] shrink-0" />
      {!ridotta && <span className="truncate">{v.label}</span>}
    </NavLink>
  )

  // menu laterale chiaro sotto l'intestazione blu (proposta grafica approvata, linguaggio di Designers Italia)
  const barra = (ridotta, nelCassetto = false) => (
    <div className="flex h-full flex-col overflow-y-auto overflow-x-hidden border-r border-linea bg-white px-3 py-5 text-pino-800">
      {nelCassetto && (
        <div className="mb-2 flex items-center justify-between px-1">
          <Logo chiaro={false} />
          <button className="rounded-full p-1.5 text-grafite hover:bg-carta" onClick={() => setMenu(false)} aria-label="Chiudi menu"><X className="size-5" /></button>
        </div>
      )}

      <Button variante="primario" icona={Plus} className={cx(nelCassetto ? 'mt-5' : 'mt-1', ridotta ? 'mx-auto w-11 px-0' : 'mx-1')}
              aria-label={ridotta ? nuovaPratica() : undefined} title={ridotta ? nuovaPratica() : undefined}
              onClick={() => { setMenu(false); apriAvvio() }}>
        {!ridotta && nuovaPratica()}
      </Button>

      <nav className="mt-6 space-y-1" aria-label="Lavoro">
        {nav.map(v => <Voce key={v.to} v={v} ridotta={ridotta} />)}
      </nav>

      {studio.length > 0 && (
        <nav className="mt-7 space-y-1" aria-label="Studio">
          {ridotta ? <span className="mx-auto mb-2 block h-px w-8 bg-linea" aria-hidden="true" />
            : <p className="px-3 pb-2 text-xs font-semibold uppercase tracking-[.06em] text-grafite">Studio</p>}
          {studio.map(v => <Voce key={v.to} v={v} ridotta={ridotta} />)}
        </nav>
      )}

      <div className="mt-auto space-y-2 pt-6">
        <div className={cx('flex items-center gap-3 rounded-blocco bg-carta py-2.5', ridotta ? 'flex-col px-1' : 'px-2.5')}>
          <Avatar nome={me.fullName} className="bg-verde-600" />
          {!ridotta && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-[15px] font-semibold text-pino-900">{me.fullName}</p>
              <p className="truncate text-[13px] text-grafite">{descriviUtente(me)}</p>
            </div>
          )}
          {modo === 'locale' && (
            <NavLink to="/password" onClick={() => setMenu(false)} className="rounded-full p-2 text-grafite hover:bg-white hover:text-verde-700"
                     aria-label="Cambia password" title="Cambia password">
              <KeyRound className="size-[18px]" />
            </NavLink>
          )}
          <button onClick={logout} className="rounded-full p-2 text-grafite hover:bg-white hover:text-verde-700" aria-label="Esci" title="Esci">
            <LogOut className="size-[18px]" />
          </button>
        </div>
        <button type="button" onClick={cambiaLarghezza} aria-expanded={!ridotta} aria-controls="menu-laterale"
                className={cx('hidden h-10 w-full items-center gap-2 rounded-blocco px-3 text-sm font-semibold text-grafite hover:bg-carta hover:text-verde-700 lg:flex', ridotta && 'justify-center px-0')}
                aria-label={ridotta ? 'Allarga il menu' : 'Stringi il menu'} title={ridotta ? 'Allarga il menu' : 'Stringi il menu'}>
          {ridotta ? <PanelLeftOpen className="size-[18px]" /> : <><PanelLeftClose className="size-[18px]" /> Stringi il menu</>}
        </button>
        {versione && !ridotta && <p className="px-2 text-[11px] text-grafite" title={versione.build ? `Build ${versione.build}` : ''}>Orditogram {versione.versione}</p>}
      </div>
    </div>
  )
  const larghezza = stretto ? 'lg:w-[84px]' : 'lg:w-[256px]'

  return (
    <AvvioContext.Provider value={apriAvvio}>
      <a href="#contenuto" className="sr-only z-[70] rounded-campo bg-white px-4 py-2 font-semibold text-verde-700 focus:not-sr-only focus:fixed focus:left-4 focus:top-4">
        Vai al contenuto
      </a>
      {/* intestazione blu a tutta larghezza, come nei siti della PA; sotto, il menu laterale chiaro */}
      <header className="fixed inset-x-0 top-0 z-40 bg-verde-600 text-white shadow-[0_1px_0_rgb(0_0_0/.08)]">
        <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
          <button className="rounded-full p-2 hover:bg-white/15 lg:hidden" onClick={() => setMenu(true)} aria-label="Apri menu"><Menu className="size-5" /></button>
          <Logo />
          <div className="ml-auto flex items-center gap-2">
            <Button variante="chiaro" misura="sm" icona={Plus} className="lg:hidden" onClick={() => apriAvvio()}>Nuova</Button>
            <NotificationBell suBlu />
          </div>
        </div>
      </header>

      <div className={cx('min-h-screen pt-16 transition-[padding] duration-300 ease-[var(--ease-morbido)]', stretto ? 'lg:pl-[84px]' : 'lg:pl-[256px]')}>
        <aside id="menu-laterale" aria-label="Menu" className={cx('fixed bottom-0 left-0 top-16 z-30 hidden transition-[width] duration-300 ease-[var(--ease-morbido)] lg:block', larghezza)}>{barra(stretto)}</aside>

        {menu && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <button type="button" aria-label="Chiudi il menu" className="absolute inset-0 animate-velo cursor-default bg-pino-950/40" onClick={() => setMenu(false)} />
            <aside className="absolute inset-y-0 left-0 w-[280px] animate-[cassetto-sinistra_.28s_var(--ease-morbido)]">{barra(false, true)}</aside>
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
