import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import type { Event } from '../api/types'
import { ADMIN_ME, makeEvent, makeEventDetail, STAFF_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

function closedEvent() {
  return makeEvent({
    id: 'c1',
    title: 'Закрытое событие',
    status: 'CLOSED',
    slug: 'closed-event',
    closedAt: '2026-05-02T10:00:00Z',
  })
}

function archivedEvent() {
  return makeEvent({
    id: 'a1',
    title: 'Архивное событие',
    status: 'ARCHIVED',
    slug: 'archived-event',
    closedAt: '2026-01-01T10:00:00Z',
    archivedAt: '2026-01-15T10:00:00Z',
  })
}

function restoreCalls(fetchMock: MockedFunction<typeof fetch>): number {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/events/a1/restore').length
}

describe('раздел истории событий', () => {
  it('персонал видит только закрытые события и не видит архивных действий', async () => {
    installApiMock({ me: STAFF_ME, eventHistory: [closedEvent()] })
    renderApp(['/events/history'])

    expect(await screen.findByText('Закрытое событие')).toBeInTheDocument()
    expect(screen.getByText('Закрыто')).toBeInTheDocument()
    expect(screen.queryByText('Архивное событие')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Восстановить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Удалить безвозвратно' })).not.toBeInTheDocument()
  })

  it('администратор видит архив и «Восстановить» вызывает restoreEvent', async () => {
    const user = userEvent.setup()
    installApiMock({ me: ADMIN_ME, eventHistory: [closedEvent(), archivedEvent()] })
    renderApp(['/events/history'])

    expect(await screen.findByText('Архивное событие')).toBeInTheDocument()
    expect(screen.getByText('В архиве')).toBeInTheDocument()

    const fetchMock = vi.mocked(globalThis.fetch)
    await user.click(screen.getByRole('button', { name: 'Восстановить' }))

    await waitFor(() => {
      expect(restoreCalls(fetchMock)).toBe(1)
    })
  })

  it('клик по закрытому событию ведёт на страницу /e/<slug>', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventHistory: [closedEvent()],
      eventBySlug: makeEventDetail({ ...closedEvent() }),
    })
    renderApp(['/events/history'])

    const link = await screen.findByRole('link', { name: /Закрытое событие/ })
    expect(link).toHaveAttribute('href', '/e/closed-event')

    await user.click(link)

    expect(
      await screen.findByRole('heading', { name: 'Закрытое событие' }),
    ).toBeInTheDocument()
  })

  it('«Открыть приём» вызывает POST /api/events/{id}/open и событие переезжает в активные', async () => {
    const user = userEvent.setup()
    const closed = closedEvent()
    let history: Event[] = [closed]
    let active: Event[] = []
    installApiMock({
      me: STAFF_ME,
      events: () => active,
      eventHistory: () => history,
      handler: (request) => {
        if (request.method === 'POST' && request.url === '/api/events/c1/open') {
          const reopened: Event = { ...closed, status: 'OPEN' }
          history = []
          active = [reopened]
          return jsonResponse(200, reopened)
        }
        return undefined
      },
    })
    renderApp(['/events/history'])

    expect(await screen.findByText('Закрытое событие')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Открыть приём' }))

    const fetchMock = vi.mocked(globalThis.fetch)
    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(
          ([url, init]) =>
            url === '/api/events/c1/open' &&
            (init as RequestInit | undefined)?.method === 'POST',
        ),
      ).toBe(true)
    })

    // The event leaves the history list…
    expect(await screen.findByText('В истории пока ничего нет')).toBeInTheDocument()

    // …and appears among the active events.
    await user.click(screen.getByRole('link', { name: 'К активным событиям' }))
    expect(await screen.findByText('Закрытое событие')).toBeInTheDocument()
  })

  it('у архивного события нет ссылки и нет «Открыть приём»', async () => {
    installApiMock({ me: ADMIN_ME, eventHistory: [archivedEvent()] })
    renderApp(['/events/history'])

    expect(await screen.findByText('Архивное событие')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Архивное событие/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Открыть приём' })).not.toBeInTheDocument()
    // Admin-only actions remain available.
    expect(screen.getByRole('button', { name: 'Восстановить' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Удалить безвозвратно' })).toBeInTheDocument()
  })

  it('отказ 409 event_archived показывается, список не портится', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventHistory: [closedEvent()],
      handler: (request) => {
        if (request.method === 'POST' && request.url === '/api/events/c1/open') {
          return jsonResponse(409, {
            error: 'event_archived',
            message: 'Событие в архиве — открыть приём нельзя',
          })
        }
        return undefined
      },
    })
    renderApp(['/events/history'])

    expect(await screen.findByText('Закрытое событие')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Открыть приём' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Событие в архиве — открыть приём нельзя',
    )
    // The status did not change: the card is still in the history list.
    expect(screen.getByText('Закрытое событие')).toBeInTheDocument()
    expect(screen.getByText('Закрыто')).toBeInTheDocument()
  })
})
