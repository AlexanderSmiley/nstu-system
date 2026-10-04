import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Event } from '../api/types'
import { formatEventDate } from '../utils/date'
import { STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function makeEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 'e1',
    title: 'Сдача лабораторных работ',
    description: null,
    availability: 'STUDENT+',
    startsAt: '2026-05-01T12:00:00Z',
    entryLimit: 27,
    entryUnit: 'BRIGADE',
    journalVisibility: 'STAFF',
    retentionDays: 14,
    slug: 'lab-works',
    status: 'OPEN',
    groupId: 'g1',
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z',
    ...overrides,
  }
}

describe('модуль «События»', () => {
  it('рендерит карточки с названием и датой', async () => {
    const event = makeEvent()
    installApiMock({ me: STUDENT_ME, events: [event] })
    renderApp(['/events'])

    const cards = await screen.findAllByTestId('event-card')
    expect(cards).toHaveLength(1)
    expect(cards[0]).toHaveTextContent(event.title)
    expect(cards[0]).toHaveTextContent(formatEventDate(event.startsAt))
  })

  it('пустой ответ показывает пустое состояние', async () => {
    installApiMock({ me: STUDENT_ME, events: [] })
    renderApp(['/events'])

    expect(await screen.findByText('Нет доступных событий')).toBeInTheDocument()
    expect(screen.queryByTestId('event-card')).not.toBeInTheDocument()
  })

  it('событие без даты помечается как «Дата не указана»', async () => {
    installApiMock({ me: STUDENT_ME, events: [makeEvent({ startsAt: null })] })
    renderApp(['/events'])

    expect(await screen.findByText('Дата не указана')).toBeInTheDocument()
  })

  it('клик по карточке ведёт на /e/{slug}', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, events: [makeEvent()] })
    renderApp(['/events'])

    await user.click(await screen.findByTestId('event-card'))

    expect(await screen.findByRole('heading', { name: 'Страница события' })).toBeInTheDocument()
    expect(screen.getByText('Событие: lab-works')).toBeInTheDocument()
  })
})
