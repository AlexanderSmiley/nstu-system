import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import { setSessionExpiredHandler } from './api/http'
import { clearSession } from './auth/session'
import './index.css'
import { initTheme } from './theme/theme'

const queryClient = new QueryClient()

// When the access token expires and the refresh token is no longer valid, the
// session is gone: mark `['me']` as anonymous and drop the remaining cache so
// the existing `RequireAuth`/`SessionGate` guards render the login screen.
// No hard reload.
setSessionExpiredHandler(() => clearSession(queryClient))

initTheme()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
