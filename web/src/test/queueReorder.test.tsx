import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import type { MockedFunction } from 'vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { QueueEntry } from '../api/types'
import { makeEventDetail, makeQueue, makeQueueEntry, STAFF_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

const EVENT = makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' })

function makeEntries(): QueueEntry[] {
  return [
    makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' }),
    makeQueueEntry({ id: 'q2', position: 2, name: 'Бригада 2', status: 'PAUSED' }),
    makeQueueEntry({ id: 'q3', position: 3, name: 'Бригада 3', status: 'WAITING' }),
  ]
}

/**
 * jsdom has no layout, so `sortableKeyboardCoordinates` sees zero-sized rects and
 * never finds a drop target. Give every queue row a distinct vertical rect so the
 * real dnd-kit keyboard sensor can compute the next position (design.md D8).
 */
function stubQueueGeometry(): void {
  const original = Element.prototype.getBoundingClientRect
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (
    this: Element,
  ) {
    const row = this.closest('[data-testid="queue-item"]')
    if (row) {
      const rows = Array.from(document.querySelectorAll('[data-testid="queue-item"]'))
      const index = rows.indexOf(row)
      const top = index * 60
      return {
        x: 0,
        y: top,
        top,
        left: 0,
        right: 300,
        bottom: top + 50,
        width: 300,
        height: 50,
        toJSON: () => ({}),
      } as DOMRect
    }
    return original.call(this)
  })
  Element.prototype.scrollIntoView = () => undefined
}

function queueCalls(fetchMock: MockedFunction<typeof fetch>): number {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/events/e1/queue').length
}

function positionCalls(fetchMock: MockedFunction<typeof fetch>): [RequestInfo | URL, RequestInit?][] {
  return fetchMock.mock.calls.filter(
    ([url, init]) =>
      typeof url === 'string' &&
      /\/queue\/[^/]+\/position$/.test(url) &&
      (init as RequestInit | undefined)?.method === 'PATCH',
  ) as [RequestInfo | URL, RequestInit?][]
}

/** Names of the queue rows in their rendered order. */
function renderedOrder(): string[] {
  return Array.from(document.querySelectorAll('[data-testid="queue-item"] .queue-item__name')).map(
    (node) => node.textContent ?? '',
  )
}

async function flushMacrotask(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

/**
 * Performs a keyboard reorder: pick up `name` with Space, move it with an arrow
 * key and drop it with Space — the exact interaction a keyboard user performs.
 */
async function keyboardReorder(name: string, code: 'ArrowDown' | 'ArrowUp'): Promise<void> {
  const handle = screen.getByRole('button', { name: `Переместить запись ${name}` })
  fireEvent.keyDown(handle, { code: 'Space', key: ' ' })
  await flushMacrotask()
  fireEvent.keyDown(handle, { code, key: code })
  await act(async () => {
    await Promise.resolve()
  })
  fireEvent.keyDown(handle, { code: 'Space', key: ' ' })
  await act(async () => {
    await Promise.resolve()
  })
}

describe('перестановка очереди', () => {
  beforeEach(() => {
    stubQueueGeometry()
  })

  it('клавиатурная перестановка отправляет PATCH с новой позицией и не содержит кнопок перемещения', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: EVENT,
      queue: makeQueue({ eventId: 'e1', queue: makeEntries() }),
    })
    const fetchMock = vi.mocked(globalThis.fetch)
    renderApp(['/e/lab-works'])

    await screen.findByText('Бригада 1')
    await keyboardReorder('Бригада 1', 'ArrowDown')

    await waitFor(() => {
      expect(positionCalls(fetchMock)).toHaveLength(1)
    })
    const [url, init] = positionCalls(fetchMock)[0]
    expect(url).toBe('/api/events/e1/queue/q1/position')
    expect(JSON.parse(String(init?.body))).toEqual({ position: 2 })

    // The up/down buttons were removed in favour of dragging.
    expect(screen.queryByText('Вверх')).not.toBeInTheDocument()
    expect(screen.queryByText('Вниз')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Поднять|Опустить/ })).not.toBeInTheDocument()
  })

  it('ошибка сохранения возвращает прежний порядок и показывает сообщение', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: EVENT,
      queue: makeQueue({ eventId: 'e1', queue: makeEntries() }),
      handler: (request) => {
        if (request.method === 'PATCH' && request.url === '/api/events/e1/queue/q1/position') {
          return jsonResponse(409, {
            error: 'invalid_state',
            message: 'Сдавшую запись нельзя переставлять',
          })
        }
        return undefined
      },
    })
    const fetchMock = vi.mocked(globalThis.fetch)
    const originalImpl = fetchMock.getMockImplementation()
    // Hold the PATCH response so the optimistic order can be observed before the
    // rollback happens.
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    fetchMock.mockImplementation(async (input, init) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
      const method = (init?.method ?? 'GET').toUpperCase()
      if (method === 'PATCH' && /\/position$/.test(url)) {
        await gate
      }
      return originalImpl!(input, init)
    })

    renderApp(['/e/lab-works'])
    await screen.findByText('Бригада 1')
    await keyboardReorder('Бригада 1', 'ArrowDown')

    // Optimistic update: the moved row is displayed first while the save is in flight.
    await waitFor(() => expect(renderedOrder()).toEqual(['Бригада 2', 'Бригада 1', 'Бригада 3']))

    await act(async () => {
      release()
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('Сдавшую запись нельзя переставлять')
    await waitFor(() => expect(renderedOrder()).toEqual(['Бригада 1', 'Бригада 2', 'Бригада 3']))
  })

  it('перестановка недоступна студенту', async () => {
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: EVENT,
      queue: makeQueue({ eventId: 'e1', queue: makeEntries() }),
    })
    renderApp(['/e/lab-works'])

    await screen.findByText('Бригада 1')
    expect(screen.queryByRole('button', { name: /Переместить запись/ })).not.toBeInTheDocument()
  })

  it('перестановка недоступна в CLOSED', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED', queue: makeEntries() }),
    })
    renderApp(['/e/lab-works'])

    await screen.findByText('Бригада 1')
    expect(screen.queryByRole('button', { name: /Переместить запись/ })).not.toBeInTheDocument()
  })
})

describe('polling очереди во время перестановки', () => {
  beforeEach(() => {
    stubQueueGeometry()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('приостанавливается на время перетаскивания и делает один перезапрос после сохранения', async () => {
    vi.useFakeTimers()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: EVENT,
      queue: makeQueue({ eventId: 'e1', queue: makeEntries() }),
    })
    const fetchMock = vi.mocked(globalThis.fetch)
    renderApp(['/e/lab-works'])

    // Settle the session/detail chain the same way polling.test.tsx does.
    for (let i = 0; i < 3; i += 1) {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0)
      })
    }
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.getByText('Бригада 1')).toBeInTheDocument()
    const before = queueCalls(fetchMock)
    expect(before).toBeGreaterThanOrEqual(1)

    const handle = screen.getByRole('button', { name: 'Переместить запись Бригада 1' })
    fireEvent.keyDown(handle, { code: 'Space', key: ' ' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    // While the drag is active the 5-second polling must not fire.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(queueCalls(fetchMock)).toBe(before)

    fireEvent.keyDown(handle, { code: 'ArrowDown', key: 'ArrowDown' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    fireEvent.keyDown(handle, { code: 'Space', key: ' ' })
    for (let i = 0; i < 3; i += 1) {
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0)
      })
    }

    // After the successful save the queue is refetched exactly once.
    expect(queueCalls(fetchMock)).toBe(before + 1)
  })
})
