import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

describe('доступ к интерфейсу только после входа', () => {
  it('аноним на "/" видит экран входа без оболочки', async () => {
    installApiMock({ me: null })
    renderApp(['/'])

    expect(await screen.findByLabelText('Имя пользователя')).toBeInTheDocument()
    expect(screen.getByLabelText('Пароль')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Войти как гость' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Основная навигация' })).not.toBeInTheDocument()
  })

  it('аноним на "/events" перенаправляется на экран входа', async () => {
    installApiMock({ me: null })
    renderApp(['/events'])

    expect(await screen.findByLabelText('Имя пользователя')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()
  })

  it('экран входа показывает название сайта из GET /api/site', async () => {
    installApiMock({ me: null, site: { name: 'Тестовый портал' } })
    renderApp(['/login'])

    expect(await screen.findByRole('heading', { name: 'Тестовый портал' })).toBeInTheDocument()
  })
})
