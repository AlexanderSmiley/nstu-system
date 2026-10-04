import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import {
  GUEST_ME,
  makeEventDetail,
  makeQueue,
  makeQueueEntry,
  STAFF_ME,
  STUDENT_ME,
} from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function queueCallCount(fetchMock: MockedFunction<typeof fetch>): number {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/events/e1/queue').length
}

describe('страница события /e/:slug', () => {
  it('студент видит очередь и форму вступления, но не кнопки персонала', async () => {
    const event = makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' })
    const entry = makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: event,
      queue: makeQueue({ eventId: 'e1', queue: [entry] }),
    })
    renderApp(['/e/lab-works'])

    expect(await screen.findByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
    expect(await screen.findByText('Бригада 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Встать в очередь' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Далее' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Приостановить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Добавить запись' })).not.toBeInTheDocument()
    // Staff-only navigation is hidden from participants.
    expect(screen.queryByRole('link', { name: 'Настройки события' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Управление очередью' })).not.toBeInTheDocument()
  })

  it('гость не видит панель персонала, но видит очередь', async () => {
    const entry = makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })
    installApiMock({
      me: GUEST_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' }),
      queue: makeQueue({ eventId: 'e1', queue: [entry] }),
    })
    renderApp(['/e/lab-works'])

    expect(await screen.findByText('Бригада 1')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Очередь' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Управление очередью' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Далее' })).not.toBeInTheDocument()
  })

  it('панель персонала идёт перед очередью, полного журнала на странице нет', async () => {
    const passed = makeQueueEntry({
      id: 'j1',
      position: 2,
      name: 'Сдавшая бригада',
      status: 'PASSED',
      passedAt: '2026-05-02T10:00:00Z',
    })
    const event = makeEventDetail({
      id: 'e1',
      slug: 'lab-works',
      title: 'Лабораторные',
      journal: [passed],
    })
    installApiMock({
      me: STAFF_ME,
      eventBySlug: event,
      queue: makeQueue({
        eventId: 'e1',
        queue: [makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })],
      }),
    })
    renderApp(['/e/lab-works'])

    const staffRegion = await screen.findByRole('region', { name: 'Управление очередью' })
    const queueRegion = await screen.findByRole('region', { name: 'Очередь' })
    expect(
      staffRegion.compareDocumentPosition(queueRegion) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()

    // The full journal is not duplicated on the event page.
    expect(screen.queryByTestId('journal-item')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Журнал сдач' })).not.toBeInTheDocument()

    expect(screen.getByRole('link', { name: 'Настройки события' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Журнал сдач' })).toBeInTheDocument()
  })

  it('персонал видит управление очередью, «Далее» вызывает advance', async () => {
    const user = userEvent.setup()
    const event = makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' })
    const entry = makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })
    installApiMock({
      me: STAFF_ME,
      eventBySlug: event,
      queue: makeQueue({ eventId: 'e1', queue: [entry] }),
    })
    renderApp(['/e/lab-works'])

    const advance = await screen.findByRole('button', { name: 'Далее' })
    await screen.findByText('Бригада 1')
    expect(screen.getByRole('button', { name: 'Приостановить' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Удалить' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Добавить запись' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Настройки события' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Журнал сдач' })).toBeInTheDocument()
    // Lifecycle actions moved to the settings screen.
    expect(screen.queryByRole('button', { name: 'Закрыть приём' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Архивировать' })).not.toBeInTheDocument()

    const fetchMock = vi.mocked(globalThis.fetch)
    await user.click(advance)

    await waitFor(() => {
      const called = fetchMock.mock.calls.some(
        ([url, init]) =>
          url === '/api/events/e1/queue/advance' &&
          (init as RequestInit | undefined)?.method === 'POST',
      )
      expect(called).toBe(true)
    })
    expect(queueCallCount(fetchMock)).toBeGreaterThanOrEqual(1)
  })

  it('переход «Настройки события» открывает экран настроек', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' }),
      queue: makeQueue({ eventId: 'e1' }),
    })
    renderApp(['/e/lab-works'])

    await user.click(await screen.findByRole('link', { name: 'Настройки события' }))

    expect(await screen.findByRole('heading', { name: 'Настройки события' })).toBeInTheDocument()
  })

  it('переход «Журнал сдач» открывает экран журнала', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' }),
      queue: makeQueue({ eventId: 'e1' }),
    })
    renderApp(['/e/lab-works'])

    await user.click(await screen.findByRole('link', { name: 'Журнал сдач' }))

    expect(await screen.findByRole('heading', { name: 'Журнал сдач' })).toBeInTheDocument()
  })
})
