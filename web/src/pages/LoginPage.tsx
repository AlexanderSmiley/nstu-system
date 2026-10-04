import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import type { Location } from 'react-router-dom'
import { login, loginAsGuest } from '../api/auth'
import { ApiError } from '../api/http'
import { LOGIN_PROFILE_QUERY_KEY, ME_QUERY_KEY } from '../api/queryKeys'
import { useSession } from '../auth/session'
import { useSiteName } from '../hooks/useSiteName'
import { LoadingScreen } from '../components/LoadingScreen'

interface FromState {
  from?: { pathname?: string; search?: string }
}

function resolveFrom(location: Location): string {
  const state = location.state as FromState | null
  const from = state?.from
  if (from?.pathname && from.pathname !== '/login') {
    return `${from.pathname}${from.search ?? ''}`
  }
  if (location.pathname !== '/login') {
    return `${location.pathname}${location.search}`
  }
  return '/'
}

/**
 * Maps a failed login to a user-facing message. The server's stable error code is
 * the source of truth: a `403` is only a block when it explicitly says
 * `account_blocked`. Every other `403` (e.g. `password_change_required` from a
 * leftover restricted cookie while the session is being re-established) falls back
 * to the server `message`, never to the misleading "account blocked" text.
 */
function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'account_blocked') {
      return 'Аккаунт заблокирован'
    }
    if (error.code === 'password_change_required') {
      return 'Нужно сменить пароль. Перейдите к смене пароля.'
    }
    if (error.status === 401) {
      return 'Неверные данные'
    }
    return error.message
  }
  return 'Не удалось войти. Попробуйте позже.'
}

/**
 * `/login` — the only public screen (identity spec "Доступ к интерфейсу только
 * после входа"). Anonymous visitors land here; authenticated users are sent back
 * to the requested route.
 */
export function LoginPage() {
  const { isAuthenticated, isLoading } = useSession()
  const siteName = useSiteName()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const from = useMemo(() => resolveFrom(location), [location])

  const loginMutation = useMutation({
    mutationFn: () => login(username.trim(), password),
    onSuccess: async (profile) => {
      queryClient.setQueryData(LOGIN_PROFILE_QUERY_KEY, profile)
      await queryClient.refetchQueries({ queryKey: ME_QUERY_KEY })
      navigate(from, { replace: true })
    },
    onError: (mutationError) => setError(loginErrorMessage(mutationError)),
  })

  const guestMutation = useMutation({
    mutationFn: loginAsGuest,
    onSuccess: (me) => {
      queryClient.setQueryData(ME_QUERY_KEY, me)
      navigate(from, { replace: true })
    },
    onError: (mutationError) =>
      setError(mutationError instanceof ApiError ? mutationError.message : 'Не удалось войти как гость'),
  })

  if (isLoading) {
    return <LoadingScreen />
  }

  if (isAuthenticated) {
    return <Navigate to={from} replace />
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    loginMutation.mutate()
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <h1 className="login-card__title">{siteName}</h1>
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="field" htmlFor="username">
            <span className="field__label">Имя пользователя</span>
            <input
              id="username"
              name="username"
              className="field__input"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>
          <label className="field" htmlFor="password">
            <span className="field__label">Пароль</span>
            <input
              id="password"
              name="password"
              type="password"
              className="field__input"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="button button--primary" disabled={loginMutation.isPending}>
            Войти
          </button>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => {
              setError(null)
              guestMutation.mutate()
            }}
            disabled={guestMutation.isPending}
          >
            Войти как гость
          </button>
        </form>
      </div>
    </div>
  )
}
