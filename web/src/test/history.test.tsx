import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import { ADMIN_ME, makeEvent, STAFF_ME } from './fixtures'
import { installApiMock } from './mockApi'
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
})
