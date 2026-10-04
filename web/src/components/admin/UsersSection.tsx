import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  blockUser,
  createUser,
  fetchUsers,
  resetUserPassword,
  unblockUser,
  updateUser,
} from '../../api/admin'
import { ApiError } from '../../api/http'
import { USERS_QUERY_KEY } from '../../api/queryKeys'
import type { User } from '../../api/types'

type ManagedRole = 'STAFF' | 'STUDENT'

interface Draft {
  displayName: string
  email: string
  role: ManagedRole
}

function roleName(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'Администратор'
    case 'STAFF':
      return 'Персонал'
    case 'STUDENT':
      return 'Студент'
    default:
      return role
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

interface CredentialsNotice {
  username: string
  password: string
}

/** Admin section «Пользователи»: create, edit, block and reset passwords. */
export function UsersSection() {
  const queryClient = useQueryClient()

  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<ManagedRole>('STUDENT')

  const [created, setCreated] = useState<CredentialsNotice | null>(null)
  const [reset, setReset] = useState<CredentialsNotice | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState<Draft>({ displayName: '', email: '', role: 'STUDENT' })
  const [error, setError] = useState<string | null>(null)

  const usersQuery = useQuery({ queryKey: USERS_QUERY_KEY, queryFn: fetchUsers })

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY })
  }

  const create = useMutation({
    mutationFn: () =>
      createUser({
        username: username.trim(),
        displayName: displayName.trim(),
        email: email.trim() === '' ? undefined : email.trim(),
        role,
      }),
    onSuccess: async (user) => {
      setCreated({ username: user.username, password: user.temporaryPassword })
      setReset(null)
      setUsername('')
      setDisplayName('')
      setEmail('')
      setRole('STUDENT')
      setError(null)
      await invalidate()
    },
    onError: (mutationError) => setError(errorMessage(mutationError, 'Не удалось создать пользователя')),
  })

  const update = useMutation({
    mutationFn: (id: string) =>
      updateUser(id, {
        displayName: draft.displayName,
        email: draft.email,
        role: draft.role,
      }),
    onSuccess: async () => {
      setEditingId(null)
      setError(null)
      await invalidate()
    },
    onError: (mutationError) => setError(errorMessage(mutationError, 'Не удалось сохранить')),
  })

  const toggleBlock = useMutation({
    mutationFn: (user: User) => (user.blocked ? unblockUser(user.id) : blockUser(user.id)),
    onSuccess: invalidate,
    onError: (mutationError) => setError(errorMessage(mutationError, 'Не удалось изменить блокировку')),
  })

  const resetPassword = useMutation({
    mutationFn: (user: User) => resetUserPassword(user.id),
    onSuccess: (data, user) => {
      setReset({ username: user.username, password: data.temporaryPassword })
      setCreated(null)
      setError(null)
    },
    onError: (mutationError) => setError(errorMessage(mutationError, 'Не удалось сбросить пароль')),
  })

  const handleCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (username.trim() === '' || displayName.trim() === '') {
      setError('Укажите имя пользователя и отображаемое имя')
      return
    }
    setError(null)
    create.mutate()
  }

  const startEdit = (user: User) => {
    setEditingId(user.id)
    setDraft({
      displayName: user.displayName,
      email: user.email ?? '',
      role: user.role === 'STAFF' ? 'STAFF' : 'STUDENT',
    })
  }

  const users = usersQuery.data ?? []

  return (
    <section className="admin-section" aria-label="Пользователи">
      <h2>Пользователи</h2>

      <form className="admin-form" onSubmit={handleCreate} noValidate>
        <label className="field" htmlFor="new-username">
          <span className="field__label">Имя пользователя</span>
          <input
            id="new-username"
            className="field__input"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </label>
        <label className="field" htmlFor="new-display-name">
          <span className="field__label">Отображаемое имя</span>
          <input
            id="new-display-name"
            className="field__input"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </label>
        <label className="field" htmlFor="new-email">
          <span className="field__label">Email</span>
          <input
            id="new-email"
            className="field__input"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label className="field" htmlFor="new-role">
          <span className="field__label">Роль</span>
          <select
            id="new-role"
            className="field__input"
            value={role}
            onChange={(event) => setRole(event.target.value as ManagedRole)}
          >
            <option value="STUDENT">Студент</option>
            <option value="STAFF">Персонал</option>
          </select>
        </label>
        <button type="submit" className="button button--primary" disabled={create.isPending}>
          Создать пользователя
        </button>
      </form>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {created && (
        <div className="temporary-password" data-testid="created-password">
          <p>
            Пароль для «{created.username}»: <code>{created.password}</code>
          </p>
          <p className="notice notice--warning">Скопируйте пароль сейчас — он показывается один раз.</p>
        </div>
      )}

      {reset && (
        <div className="temporary-password" data-testid="reset-password">
          <p>
            Новый пароль для «{reset.username}»: <code>{reset.password}</code>
          </p>
          <p className="notice notice--warning">Скопируйте пароль сейчас — он показывается один раз.</p>
        </div>
      )}

      {usersQuery.isPending ? (
        <p className="placeholder-note">Загрузка пользователей…</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Имя пользователя</th>
              <th>Отображаемое имя</th>
              <th>Email</th>
              <th>Роль</th>
              <th>Статус</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => {
              const editing = editingId === user.id
              return (
                <tr key={user.id} data-testid="user-row">
                  <td>{user.username}</td>
                  <td>
                    {editing ? (
                      <input
                        className="field__input"
                        aria-label={`Отображаемое имя ${user.username}`}
                        value={draft.displayName}
                        onChange={(event) =>
                          setDraft((value) => ({ ...value, displayName: event.target.value }))
                        }
                      />
                    ) : (
                      user.displayName
                    )}
                  </td>
                  <td>
                    {editing ? (
                      <input
                        className="field__input"
                        aria-label={`Email ${user.username}`}
                        value={draft.email}
                        onChange={(event) =>
                          setDraft((value) => ({ ...value, email: event.target.value }))
                        }
                      />
                    ) : (
                      (user.email ?? '—')
                    )}
                  </td>
                  <td>
                    {editing ? (
                      <select
                        className="field__input"
                        aria-label={`Роль ${user.username}`}
                        value={draft.role}
                        onChange={(event) =>
                          setDraft((value) => ({
                            ...value,
                            role: event.target.value as ManagedRole,
                          }))
                        }
                      >
                        <option value="STUDENT">Студент</option>
                        <option value="STAFF">Персонал</option>
                      </select>
                    ) : (
                      roleName(user.role)
                    )}
                  </td>
                  <td>{user.blocked ? 'Заблокирован' : 'Активен'}</td>
                  <td className="data-table__actions">
                    {editing ? (
                      <>
                        <button
                          type="button"
                          className="button button--primary button--small"
                          onClick={() => update.mutate(user.id)}
                          disabled={update.isPending}
                        >
                          Сохранить
                        </button>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => setEditingId(null)}
                        >
                          Отмена
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => startEdit(user)}
                        >
                          Изменить
                        </button>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => toggleBlock.mutate(user)}
                          disabled={toggleBlock.isPending}
                        >
                          {user.blocked ? 'Разблокировать' : 'Заблокировать'}
                        </button>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => resetPassword.mutate(user)}
                          disabled={resetPassword.isPending}
                        >
                          Сбросить пароль
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </section>
  )
}
