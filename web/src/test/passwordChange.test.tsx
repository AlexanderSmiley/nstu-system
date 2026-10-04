import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { MUST_CHANGE_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

describe('обязательная смена пароля', () => {
  it('при mustChangePassword любой маршрут рендерит страницу смены пароля', async () => {
    installApiMock({ me: MUST_CHANGE_ME })
    renderApp(['/'])

    expect(await screen.findByRole('heading', { name: 'Смена пароля' })).toBeInTheDocument()
    expect(screen.queryByTestId('module-grid')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Выйти' })).toBeInTheDocument()
  })

  it('уйти на /events без смены пароля нельзя', async () => {
    installApiMock({ me: MUST_CHANGE_ME })
    renderApp(['/events'])

    expect(await screen.findByRole('heading', { name: 'Смена пароля' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()
  })

  it('клиентская валидация отклоняет слишком короткий пароль', async () => {
    const user = userEvent.setup()
    installApiMock({ me: MUST_CHANGE_ME })
    renderApp(['/password/change'])

    await screen.findByRole('heading', { name: 'Смена пароля' })
    await user.type(screen.getByLabelText('Текущий пароль'), 'TempPass1')
    await user.type(screen.getByLabelText('Новый пароль'), 'short')
    await user.type(screen.getByLabelText('Повторите новый пароль'), 'short')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('не менее 8 символов')
  })

  it('ошибка invalid_old_password показывается пользователю', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: MUST_CHANGE_ME,
      password: () =>
        jsonResponse(400, { error: 'invalid_old_password', message: 'Неверный текущий пароль' }),
    })
    renderApp(['/password/change'])

    await screen.findByRole('heading', { name: 'Смена пароля' })
    await user.type(screen.getByLabelText('Текущий пароль'), 'WrongPass1')
    await user.type(screen.getByLabelText('Новый пароль'), 'NewPass123')
    await user.type(screen.getByLabelText('Повторите новый пароль'), 'NewPass123')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный текущий пароль')
  })

  it('после успешной смены пароля открывается главная', async () => {
    const user = userEvent.setup()
    let mustChange = true
    installApiMock({
      me: () => (mustChange ? MUST_CHANGE_ME : STUDENT_ME),
      password: () => {
        mustChange = false
        return jsonResponse(200, {
          id: STUDENT_ME.subject,
          username: 'student',
          displayName: STUDENT_ME.displayName,
          role: 'STUDENT',
          mustChangePassword: false,
        })
      },
    })
    renderApp(['/password/change'])

    await screen.findByRole('heading', { name: 'Смена пароля' })
    await user.type(screen.getByLabelText('Текущий пароль'), 'TempPass1')
    await user.type(screen.getByLabelText('Новый пароль'), 'NewPass123')
    await user.type(screen.getByLabelText('Повторите новый пароль'), 'NewPass123')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))

    expect(await screen.findByTestId('module-grid')).toBeInTheDocument()
  })
})
