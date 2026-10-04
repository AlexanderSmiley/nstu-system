import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSession } from '../auth/session'

/** Redirects anonymous visitors to the login screen, preserving the target. */
export function RequireAuth() {
  const { isAuthenticated } = useSession()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
