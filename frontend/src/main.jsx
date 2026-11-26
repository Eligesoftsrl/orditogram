import React from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
// font serviti dall'applicazione: nessuna chiamata a server esterni (privacy, reti chiuse delle PA)
// Titillium Web e Roboto Mono: i caratteri del kit di Designers Italia (AgID)
import '@fontsource/titillium-web/400.css'
import '@fontsource/titillium-web/400-italic.css'
import '@fontsource/titillium-web/600.css'
import '@fontsource/titillium-web/700.css'
import '@fontsource/roboto-mono/400.css'
import './index.css'
import { AuthProvider, useAuth } from './lib/auth.jsx'
import { Caricamento, ToastProvider } from './components/ui.jsx'
import Shell from './components/Shell.jsx'
import Login from './pages/Login.jsx'
import Scrivania from './pages/Scrivania.jsx'
import Pratiche from './pages/Pratiche.jsx'
import PraticaPage from './pages/PraticaPage.jsx'
import TaskPage from './pages/TaskPage.jsx'
import Processi from './pages/studio/Processi.jsx'
import FormStudio from './pages/studio/Form.jsx'
import Utenti from './pages/studio/Utenti.jsx'
import Registro from './pages/studio/Registro.jsx'
import Integrazioni from './pages/studio/Integrazioni.jsx'
import Ruoli from './pages/studio/Ruoli.jsx'
import Strutture from './pages/studio/Strutture.jsx'
import Organizzazioni from './pages/studio/Organizzazioni.jsx'
import { PERMESSI, puo } from './lib/permessi.js'
import ProcessEditor from './pages/studio/ProcessEditor.jsx'
import CambioPassword from './pages/CambioPassword.jsx'
import MiaOrganizzazione from './pages/MiaOrganizzazione.jsx'
import AnagraficheStudio from './pages/studio/Anagrafiche.jsx'
import Glossario from './pages/studio/Glossario.jsx'
import { GlossarioProvider, useGlossario } from './lib/useGlossario.jsx'
import Anagrafiche from './pages/Anagrafiche.jsx'
import SchedeAnagrafica from './pages/SchedeAnagrafica.jsx'
import SchedaAnagrafica from './pages/SchedaAnagrafica.jsx'

function App() {
  // i testi con le parole del glossario (gl) si ridisegnano quando /api/info risponde o il glossario cambia
  useGlossario()
  const { me, pronto } = useAuth()
  if (!pronto) return <Caricamento testo="Apertura di Orditogram…" />
  if (!me) return <Login />
  // password temporanea (data dal referente): prima di tutto va cambiata
  if (me.passwordDaCambiare) return <CambioPassword obbligatorio />
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Scrivania />} />
        <Route path="pratiche" element={<Pratiche />} />
        <Route path="pratiche/:id" element={<PraticaPage />} />
        <Route path="attivita/:id" element={<TaskPage />} />
        <Route path="password" element={<CambioPassword />} />
        {!me.esterno && <>
          <Route path="anagrafiche" element={<Anagrafiche />} />
          <Route path="anagrafiche/:tipo" element={<SchedeAnagrafica />} />
          <Route path="anagrafiche/:tipo/:id" element={<SchedaAnagrafica />} />
        </>}
        {me.referenteDi?.length > 0 && <Route path="organizzazione" element={<MiaOrganizzazione />} />}
        {puo(me, PERMESSI.processi) && <>
          <Route path="studio/processi" element={<Processi />} />
          <Route path="studio/editor/:chiave" element={<ProcessEditor />} />
        </>}
        {puo(me, PERMESSI.form) && <Route path="studio/form" element={<FormStudio />} />}
        {puo(me, PERMESSI.anagrafiche) && <Route path="studio/anagrafiche" element={<AnagraficheStudio />} />}
        {puo(me, PERMESSI.utenti) && <>
          <Route path="studio/utenti" element={<Utenti />} />
          <Route path="studio/ruoli" element={<Ruoli />} />
          <Route path="studio/strutture" element={<Strutture />} />
        </>}
        {puo(me, PERMESSI.organizzazioni) && <Route path="studio/organizzazioni" element={<Organizzazioni />} />}
        {puo(me, PERMESSI.registro) && <Route path="studio/registro" element={<Registro />} />}
        {puo(me, PERMESSI.integrazioni) && <Route path="studio/integrazioni" element={<Integrazioni />} />}
        {me.admin && <Route path="studio/glossario" element={<Glossario />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <ToastProvider>
        <GlossarioProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </GlossarioProvider>
      </ToastProvider>
    </HashRouter>
  </React.StrictMode>
)
