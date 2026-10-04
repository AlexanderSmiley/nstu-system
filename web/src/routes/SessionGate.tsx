import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useSession } from '../auth/session'
import { LoadingScreen } from '../components/LoadingScreen'

const PASSWORD_CHANGE_PATH = '/password/change'

interface SessionGateProps {
  children: ReactNode
}

/**
 * Global guard (identity spec "Доступ к интерфейсу только после входа"):
 * shows a loader until the session is known and, while a password change is
 * pending, forces every route except the change-password screen onto that
 * screen. Anonymous visitors are handled by the per-route guards.
 */
export function SessionGate({ children }: SessionGateProps) {
  const { isLoading, isAuthenticated, mustChangePassword } = useSession()
  const location = useLocation()

  if (isLoading) {
    return <LoadingScreen />
  }

  if (isAuthenticated && mustChangePassword && location.pathname !== PASSWORD_CHANGE_PATH) {
    return <Navigate to={PASSWORD_CHANGE_PATH} replace />
  }

  return <>{children}</>
}
