import { act } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import { makeEventDetail, makeQueue, STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function queueCalls(fetchMock: MockedFunction<typeof fetch>): number {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/events/e1/queue').length
}

async function flush(): Promise<void> {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0)
  })
}

describe('polling очереди', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('повторяет запрос очереди каждые 5 секунд', async () => {
    vi.useFakeTimers()
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works' }),
      queue: makeQueue({ eventId: 'e1' }),
    })
    const fetchMock = vi.mocked(globalThis.fetch)
    renderApp(['/e/lab-works'])

    for (let i = 0; i < 3; i += 1) {
      await flush()
    }

    // Settle the session/detail chain; the queue query starts and is fetched.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    await flush()

    const before = queueCalls(fetchMock)
    expect(before).toBeGreaterThanOrEqual(1)

    // The next interval must issue another queue request.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    await flush()

    expect(queueCalls(fetchMock)).toBeGreaterThan(before)
  })
})
