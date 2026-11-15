import React from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
// font serviti dall'applicazione: nessuna chiamata a server esterni (privacy, reti chiuse delle PA)
import '@fontsource-variable/bricolage-grotesque/opsz.css'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-sans/400-italic.css'
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
import ProcessEditor from './pages/studio/ProcessEditor.jsx'

function App() {
  const { me, pronto } = useAuth()
  if (!pronto) return <Caricamento testo="Apertura di Orditogram…" />
  if (!me) return <Login />
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Scrivania />} />
        <Route path="pratiche" element={<Pratiche />} />
        <Route path="pratiche/:id" element={<PraticaPage />} />
        <Route path="attivita/:id" element={<TaskPage />} />
        {me.admin && <>
          <Route path="studio/processi" element={<Processi />} />
          <Route path="studio/form" element={<FormStudio />} />
          <Route path="studio/utenti" element={<Utenti />} />
          <Route path="studio/registro" element={<Registro />} />
          <Route path="studio/integrazioni" element={<Integrazioni />} />
          <Route path="studio/editor/:chiave" element={<ProcessEditor />} />
        </>}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </HashRouter>
  </React.StrictMode>
)
