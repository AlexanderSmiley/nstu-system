import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

/**
 * A `403` from `POST /api/auth/login` must not automatically mean "account
 * blocked": the only block signal is the `account_blocked` error code. Every
 * other `403` shows the server message, and `password_change_required` guides the
 * user to the password-change screen.
 */
async function submitLogin(login: () => Response) {
  installApiMock({ me: null, login })
  const user = userEvent.setup()
  renderApp(['/login'])

  await screen.findByLabelText('Имя пользователя')
  await user.type(screen.getByLabelText('Имя пользователя'), 'student')
  await user.type(screen.getByLabelText('Пароль'), 'Staff-Pass1')
  await user.click(screen.getByRole('button', { name: 'Войти' }))
}

describe('сообщения об ошибке входа', () => {
  it('403 с кодом account_blocked показывает «Аккаунт заблокирован»', async () => {
    await submitLogin(() =>
      jsonResponse(403, { error: 'account_blocked', message: 'Учётная запись заблокирована' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Аккаунт заблокирован')
  })

  it('403 с другим кодом показывает сообщение сервера, а не «заблокирован»', async () => {
    await submitLogin(() =>
      jsonResponse(403, { error: 'forbidden', message: 'Доступ к системе ограничен' }),
    )

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Доступ к системе ограничен')
    expect(alert).not.toHaveTextContent('заблокирован')
  })

  it('403 с кодом password_change_required предлагает сменить пароль', async () => {
    await submitLogin(() =>
      jsonResponse(403, {
        error: 'password_change_required',
        message: 'Требуется смена пароля',
      }),
    )

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Нужно сменить пароль')
    expect(alert).not.toHaveTextContent('заблокирован')
  })

  it('если сервер не прислал код, показывает текст сообщения без слова «заблокирован»', async () => {
    await submitLogin(() => jsonResponse(403, { message: 'Обратитесь к администратору' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Обратитесь к администратору')
    expect(alert).not.toHaveTextContent('заблокирован')
  })
})
