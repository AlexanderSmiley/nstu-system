import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { logout } from '../api/auth'
import { clearSession } from './session'

/**
 * Ends the session: calls the API, then clears the React Query cache and returns
 * to the login screen. Runs on `onSettled` so a network hiccup cannot trap the
 * user inside the app.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: logout,
    onSettled: () => {
      clearSession(queryClient)
      navigate('/login', { replace: true })
    },
  })
}
