import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request, setSessionExpiredHandler } from '../api/http'
import { clearSession } from '../auth/session'
import { STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') {
    return input
  }
  if (input instanceof URL) {
    return input.toString()
  }
  return input.url
}

function unauthorized(): Response {
  return new Response(JSON.stringify({ error: 'unauthorized' }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' },
  })
}

function ok(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('прозрачное обновление сессии', () => {
  afterEach(() => {
    setSessionExpiredHandler(null)
  })

  it('после успешного refresh повторяет исходный запрос', async () => {
    let dataCalls = 0
    let refreshCalls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = urlOf(input)
        if (url === '/api/auth/refresh') {
          refreshCalls += 1
          return new Response(null, { status: 200 })
        }
        dataCalls += 1
        return dataCalls === 1 ? unauthorized() : ok({ value: 42 })
      }),
    )

    await expect(request<{ value: number }>('/api/events')).resolves.toEqual({ value: 42 })
    expect(refreshCalls).toBe(1)
    expect(dataCalls).toBe(2)
  })

  it('при провале refresh сбрасывает сессию и не повторяет запрос бесконечно', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    let dataCalls = 0
    let refreshCalls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = urlOf(input)
        if (url === '/api/auth/refresh') {
          refreshCalls += 1
          return unauthorized()
        }
        dataCalls += 1
        return unauthorized()
      }),
    )

    await expect(request('/api/events')).rejects.toBeInstanceOf(ApiError)
    expect(refreshCalls).toBe(1)
    expect(dataCalls).toBe(1)
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('параллельные 401 используют один общий refresh', async () => {
    let refreshCalls = 0
    const perPath = new Map<string, number>()
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = urlOf(input)
        if (url === '/api/auth/refresh') {
          refreshCalls += 1
          await new Promise((resolve) => setTimeout(resolve, 10))
          return new Response(null, { status: 200 })
        }
        const calls = (perPath.get(url) ?? 0) + 1
        perPath.set(url, calls)
        return calls === 1 ? unauthorized() : ok({ url })
      }),
    )

    const [first, second] = await Promise.all([
      request<{ url: string }>('/api/a'),
      request<{ url: string }>('/api/b'),
    ])

    expect(refreshCalls).toBe(1)
    expect(first).toEqual({ url: '/api/a' })
    expect(second).toEqual({ url: '/api/b' })
  })

  it('сброс сессии возвращает пользователя на экран входа', async () => {
    installApiMock({
      me: STUDENT_ME,
      refresh: () => unauthorized(),
      handler: (mockRequest) => (mockRequest.url === '/api/events' ? unauthorized() : undefined),
    })
    const rendered = renderApp(['/events'])
    setSessionExpiredHandler(() => clearSession(rendered.queryClient))

    expect(await screen.findByRole('button', { name: 'Войти' })).toBeInTheDocument()
    expect(screen.queryByText('Нет доступных событий')).not.toBeInTheDocument()
  })
})
