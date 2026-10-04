import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import { ADMIN_ME, makeEvent, STAFF_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

function callsTo(
  fetchMock: MockedFunction<typeof fetch>,
  url: string,
  method?: string,
): number {
  return fetchMock.mock.calls.filter(
    ([calledUrl, init]) =>
      calledUrl === url &&
      (method === undefined || (init as RequestInit | undefined)?.method === method),
  ).length
}

describe('раздел «Администрирование»', () => {
  it('студенту недоступен и показывает 403-экран', async () => {
    installApiMock({ me: STUDENT_ME })
    renderApp(['/admin'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('недостаточно прав')
    expect(screen.queryByText('Название сайта')).not.toBeInTheDocument()
  })

  it('староста не видит «Администрирование» в сайдбаре', async () => {
    installApiMock({ me: STAFF_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const nav = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(nav).queryByRole('link', { name: 'Администрирование' })).not.toBeInTheDocument()
  })

  it('староста получает экран отсутствия прав на прямом /admin/events', async () => {
    installApiMock({ me: STAFF_ME })
    renderApp(['/admin/events'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()
  })

  it('администратор видит три блока и переключается между ними без смешения', async () => {
    const user = userEvent.setup()
    installApiMock({ me: ADMIN_ME, site: { name: 'Портал' }, users: [], events: [], eventHistory: [] })
    renderApp(['/admin/general'])

    const nav = await screen.findByRole('navigation', { name: 'Разделы администрирования' })
    expect(within(nav).getByRole('link', { name: 'Общее' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Управление пользователями' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Управление событиями' })).toBeInTheDocument()

    expect(await screen.findByRole('heading', { name: 'Название сайта' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Пользователи' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()

    await user.click(within(nav).getByRole('link', { name: 'Управление пользователями' }))
    expect(await screen.findByRole('heading', { name: 'Пользователи' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Название сайта' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()

    await user.click(within(nav).getByRole('link', { name: 'Управление событиями' }))
    expect(await screen.findByRole('heading', { name: 'События' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Название сайта' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Пользователи' })).not.toBeInTheDocument()
  })

  it('в блоке событий полный список, «Восстановить» и «Удалить безвозвратно»', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const open = makeEvent({ id: 'o1', title: 'Открытое событие', status: 'OPEN', slug: 'open-event' })
    const closed = makeEvent({
      id: 'c1',
      title: 'Закрытое событие',
      status: 'CLOSED',
      slug: 'closed-event',
    })
    const archived = makeEvent({
      id: 'a1',
      title: 'Архивное событие',
      status: 'ARCHIVED',
      slug: 'archived-event',
    })
    installApiMock({ me: ADMIN_ME, events: [open], eventHistory: [closed, archived] })
    renderApp(['/admin/events'])

    expect(await screen.findByText('Открытое событие')).toBeInTheDocument()
    expect(screen.getByText('Закрытое событие')).toBeInTheDocument()
    expect(screen.getByText('Архивное событие')).toBeInTheDocument()
    expect(screen.getByText('Открыто')).toBeInTheDocument()
    expect(screen.getByText('Закрыто')).toBeInTheDocument()
    expect(screen.getByText('В архиве')).toBeInTheDocument()

    // Non-archived rows keep a transition to the event; archived ones do not.
    const eventLinks = screen.getAllByRole('link', { name: 'Открыть' })
    expect(eventLinks).toHaveLength(2)
    expect(eventLinks.map((link) => link.getAttribute('href'))).toEqual(
      expect.arrayContaining(['/e/open-event', '/e/closed-event']),
    )

    const fetchMock = vi.mocked(globalThis.fetch)
    await user.click(screen.getByRole('button', { name: 'Восстановить' }))
    await waitFor(() => expect(callsTo(fetchMock, '/api/events/a1/restore', 'POST')).toBe(1))

    await user.click(screen.getByRole('button', { name: 'Удалить безвозвратно' }))
    expect(confirmSpy).toHaveBeenCalled()
    await waitFor(() => expect(callsTo(fetchMock, '/api/events/a1', 'DELETE')).toBe(1))
  })

  it('удаление архивного события не выполняется при отказе от подтверждения', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const archived = makeEvent({
      id: 'a1',
      title: 'Архивное событие',
      status: 'ARCHIVED',
      slug: 'archived-event',
    })
    installApiMock({ me: ADMIN_ME, eventHistory: [archived] })
    renderApp(['/admin/events'])

    await screen.findByText('Архивное событие')
    await user.click(screen.getByRole('button', { name: 'Удалить безвозвратно' }))

    expect(
      vi.mocked(globalThis.fetch).mock.calls.some(([url]) => url === '/api/events/a1'),
    ).toBe(false)
  })

  it('создание пользователя показывает temporaryPassword один раз', async () => {
    const user = userEvent.setup()
    installApiMock({ me: ADMIN_ME, users: [] })
    renderApp(['/admin/users'])

    await screen.findByRole('heading', { name: 'Администрирование' })
    await user.type(screen.getByLabelText('Имя пользователя'), 'staff1')
    await user.type(screen.getByLabelText('Отображаемое имя'), 'Стафф Иванов')
    await user.click(screen.getByRole('button', { name: 'Создать пользователя' }))

    const notice = await screen.findByTestId('created-password')
    expect(notice).toHaveTextContent('Temp-1234-Abc')
    expect(notice).toHaveTextContent('показывается один раз')
  })

  it('смена названия сайта обновляет хедер', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: ADMIN_ME,
      site: { name: 'Старое название' },
      updateSite: (body) => jsonResponse(200, { name: body.name ?? '' }),
    })
    renderApp(['/admin/general'])

    const input = await screen.findByLabelText('Название')
    await waitFor(() => expect(input).toHaveValue('Старое название'))
    await user.clear(input)
    await user.type(input, 'Новое название')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByText('Новое название')).toBeInTheDocument()
  })
})
