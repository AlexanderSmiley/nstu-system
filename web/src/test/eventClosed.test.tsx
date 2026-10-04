import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { makeEventDetail, makeQueue, makeQueueEntry, STAFF_ME, STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

describe('закрытое событие', () => {
  it('показывает бейдж «приём закрыт» и отключает вступление', async () => {
    const entry = makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED', queue: [entry], journal: [] }),
    })
    renderApp(['/e/lab-works'])

    expect(await screen.findByText('приём закрыт')).toBeInTheDocument()
    expect(await screen.findByText('Бригада 1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Встать в очередь' })).not.toBeInTheDocument()
  })

  it('персонал видит «Далее», но кнопка неактивна в CLOSED', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: makeEventDetail({ id: 'e1', slug: 'lab-works', status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED' }),
    })
    renderApp(['/e/lab-works'])

    expect(await screen.findByText('приём закрыт')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Приостановить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Добавить запись' })).not.toBeInTheDocument()
    // Lifecycle actions left the event page for the settings screen.
    expect(screen.queryByRole('button', { name: 'Открыть приём' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Архивировать' })).not.toBeInTheDocument()
  })
})
