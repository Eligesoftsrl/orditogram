import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Le chiamate /api vengono girate al backend Spring Boot (niente CORS da configurare)
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // /oauth2 e /login/oauth2: accesso con Keycloak / Entra ID (l'host resta localhost:5173, come registrato sul provider)
    proxy: {
      '/api': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080'
    }
  }
})
