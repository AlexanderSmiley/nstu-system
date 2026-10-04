import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router-dom'
import { changePassword } from '../api/auth'
import { ApiError } from '../api/http'
import { ME_QUERY_KEY } from '../api/queryKeys'
import { useSession } from '../auth/session'
import { validateNewPassword } from '../auth/passwordPolicy'
import { useLogout } from '../auth/useLogout'

function passwordChangeErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'invalid_old_password') {
      return 'Неверный текущий пароль'
    }
    return error.message
  }
  return 'Не удалось сменить пароль. Попробуйте позже.'
}

/**
 * `/password/change` — forced by {@link SessionGate} while
 * `mustChangePassword` is set; also reachable voluntarily. The only way out
 * without changing the password is logging out.
 */
export function PasswordChangePage() {
  const { isAuthenticated, mustChangePassword } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logout = useLogout()

  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => changePassword(oldPassword, newPassword),
    onSuccess: async () => {
      await queryClient.refetchQueries({ queryKey: ME_QUERY_KEY })
      navigate('/', { replace: true })
    },
    onError: (mutationError) => setError(passwordChangeErrorMessage(mutationError)),
  })

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const validationError = validateNewPassword(newPassword, oldPassword, confirmation)
    if (validationError) {
      setError(validationError)
      return
    }
    setError(null)
    mutation.mutate()
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <h1 className="login-card__title">Смена пароля</h1>
        {mustChangePassword && (
          <p className="notice">Для продолжения работы необходимо сменить временный пароль.</p>
        )}
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="field" htmlFor="old-password">
            <span className="field__label">Текущий пароль</span>
            <input
              id="old-password"
              name="old-password"
              type="password"
              className="field__input"
              autoComplete="current-password"
              value={oldPassword}
              onChange={(event) => setOldPassword(event.target.value)}
            />
          </label>
          <label className="field" htmlFor="new-password">
            <span className="field__label">Новый пароль</span>
            <input
              id="new-password"
              name="new-password"
              type="password"
              className="field__input"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </label>
          <label className="field" htmlFor="confirm-password">
            <span className="field__label">Повторите новый пароль</span>
            <input
              id="confirm-password"
              name="confirm-password"
              type="password"
              className="field__input"
              autoComplete="new-password"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </label>
          <p className="field__hint">Не менее 8 символов, буквы и цифры, отличается от текущего.</p>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="button button--primary" disabled={mutation.isPending}>
            Сменить пароль
          </button>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
          >
            Выйти
          </button>
        </form>
      </div>
    </div>
  )
}
