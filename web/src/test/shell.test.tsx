import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DEFAULT_SITE_NAME } from '../api/site'
import { ADMIN_ME, GUEST_ME, STAFF_ME, STUDENT_ME, STUDENT_PROFILE } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function requestedStudentProfile(): boolean {
  return vi
    .mocked(globalThis.fetch)
    .mock.calls.some(([url]) => url === '/api/students/me')
}

describe('оболочка интерфейса и иконка пользователя', () => {
  it('сайдбар студента содержит «События» и «Настройки», но не «Администрирование»', async () => {
    installApiMock({ me: STUDENT_ME, studentProfile: STUDENT_PROFILE })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const nav = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(nav).getByRole('link', { name: 'События' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Настройки' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Администрирование' })).not.toBeInTheDocument()
  })

  it('гамбургер открывает сайдбар', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const sidebar = screen.getByTestId('sidebar')
    expect(sidebar).not.toHaveClass('sidebar--open')
    await user.click(screen.getByRole('button', { name: 'Меню' }))
    expect(sidebar).toHaveClass('sidebar--open')
  })

  it('иконка студента ведёт в профиль', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, studentProfile: STUDENT_PROFILE })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    await user.click(screen.getByRole('link', { name: 'Профиль' }))

    expect(await screen.findByRole('heading', { name: 'Профиль' })).toBeInTheDocument()
    expect(screen.getByText(STUDENT_PROFILE.fullName)).toBeInTheDocument()
    expect(screen.getByText(STUDENT_PROFILE.groupName as string)).toBeInTheDocument()
    expect(screen.queryByText(STUDENT_PROFILE.groupId as string)).not.toBeInTheDocument()
  })

  it('староста видит название группы без идентификатора', async () => {
    installApiMock({ me: STAFF_ME, studentProfile: STUDENT_PROFILE })
    renderApp(['/profile'])

    expect(await screen.findByText(STUDENT_PROFILE.groupName as string)).toBeInTheDocument()
    expect(screen.queryByText(STUDENT_PROFILE.groupId as string)).not.toBeInTheDocument()
  })

  it('при отсутствии названия группы показывается прочерк', async () => {
    installApiMock({
      me: STUDENT_ME,
      studentProfile: { ...STUDENT_PROFILE, groupName: null },
    })
    renderApp(['/profile'])

    const term = await screen.findByText('Группа')
    const row = term.closest('.details__row') as HTMLElement
    expect(within(row).getByText('—')).toBeInTheDocument()
  })

  it('профиль администратора не запрашивает данные профиля студента', async () => {
    installApiMock({ me: ADMIN_ME })
    renderApp(['/profile'])

    expect(await screen.findByRole('heading', { name: 'Профиль' })).toBeInTheDocument()
    expect(requestedStudentProfile()).toBe(false)
  })

  it('профиль гостя не запрашивает данные профиля студента', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/profile'])

    expect(await screen.findByRole('heading', { name: 'Профиль' })).toBeInTheDocument()
    expect(requestedStudentProfile()).toBe(false)
  })

  it('сайдбар администратора содержит «Администрирование», иконка показывает мини-инфо', async () => {
    const user = userEvent.setup()
    installApiMock({ me: ADMIN_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const nav = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(nav).getByRole('link', { name: 'Администрирование' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    expect(screen.getByText('Вы вошли как Администратор')).toBeInTheDocument()
  })

  it('иконка гостя показывает мини-инфо «Вы вошли как гость»', async () => {
    const user = userEvent.setup()
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    expect(screen.getByText('Вы вошли как гость')).toBeInTheDocument()
  })

  it('выход доступен всем и возвращает на экран входа', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, studentProfile: STUDENT_PROFILE })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    await user.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(await screen.findByLabelText('Имя пользователя')).toBeInTheDocument()
  })

  it('название сайта в шапке — ссылка на главную', async () => {
    installApiMock({ me: STUDENT_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const title = screen.getByRole('link', { name: DEFAULT_SITE_NAME })
    expect(title).toHaveAttribute('href', '/')
  })

  it('клик по названию сайта с «/events» возвращает на главную', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/events'])

    expect(await screen.findByRole('heading', { name: 'События' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: DEFAULT_SITE_NAME }))

    expect(await screen.findByTestId('module-grid')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Главная' })).toBeInTheDocument()
  })

  it('клик по свободному месту закрывает мини-инфо', async () => {
    const user = userEvent.setup()
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    expect(screen.getByTestId('user-menu')).toBeInTheDocument()

    await user.click(screen.getByRole('heading', { name: 'Главная' }))
    expect(screen.queryByTestId('user-menu')).not.toBeInTheDocument()
  })

  it('Escape закрывает мини-инфо', async () => {
    const user = userEvent.setup()
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    await user.click(screen.getByRole('button', { name: 'Пользователь' }))
    expect(screen.getByTestId('user-menu')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByTestId('user-menu')).not.toBeInTheDocument()
  })

  it('повторный клик по иконке закрывает мини-инфо', async () => {
    const user = userEvent.setup()
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const button = screen.getByRole('button', { name: 'Пользователь' })
    await user.click(button)
    expect(screen.getByTestId('user-menu')).toBeInTheDocument()

    await user.click(button)
    expect(screen.queryByTestId('user-menu')).not.toBeInTheDocument()
  })
})
