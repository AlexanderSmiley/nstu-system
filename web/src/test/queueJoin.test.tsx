import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { QueueEntry } from '../api/types'
import { GUEST_ME, makeEventDetail, makeQueue, makeQueueEntry, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

describe('вступление в очередь', () => {
  it('гость без имени получает ошибку валидации', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: GUEST_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', availability: 'GUEST+' }),
      queue: makeQueue({ eventId: 'e1' }),
    })
    renderApp(['/e/lab-works'])

    await screen.findByRole('heading', { name: 'Сдача лабораторных работ' })
    await screen.findByText('Очередь пуста')
    await user.click(screen.getByRole('button', { name: 'Встать в очередь' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Введите имя записи')
  })

  it('409 name_taken показывает понятное сообщение', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: GUEST_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works' }),
      queue: makeQueue({ eventId: 'e1' }),
      handler: (request) => {
        if (request.method === 'POST' && request.url === '/api/events/e1/queue') {
          return jsonResponse(409, { error: 'name_taken', message: 'Имя занято' })
        }
        return undefined
      },
    })
    renderApp(['/e/lab-works'])

    await screen.findByRole('heading', { name: 'Сдача лабораторных работ' })
    await user.type(screen.getByLabelText('Название бригады'), 'Бригада 1')
    await user.click(screen.getByRole('button', { name: 'Встать в очередь' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Это имя уже занято')
  })

  it('успех добавляет запись в список очереди', async () => {
    const user = userEvent.setup()
    let entries: QueueEntry[] = []
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works' }),
      handler: (request) => {
        if (request.method === 'GET' && request.url === '/api/events/e1/queue') {
          return jsonResponse(
            200,
            makeQueue({ eventId: 'e1', queue: entries }),
            { ETag: '"q1"' },
          )
        }
        if (request.method === 'POST' && request.url === '/api/events/e1/queue') {
          entries = [makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 7' })]
          return jsonResponse(201, entries[0])
        }
        return undefined
      },
    })
    renderApp(['/e/lab-works'])

    await screen.findByRole('heading', { name: 'Сдача лабораторных работ' })
    await screen.findByText('Очередь пуста')
    await user.type(screen.getByLabelText('Название бригады'), 'Бригада 7')
    await user.click(screen.getByRole('button', { name: 'Встать в очередь' }))

    expect(await screen.findByText('Бригада 7')).toBeInTheDocument()
  })
})
