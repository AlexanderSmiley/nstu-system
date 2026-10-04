import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { EventDetail, QueueEntry } from '../api/types'
import {
  ADMIN_ME,
  GUEST_ME,
  makeEventDetail,
  makeQueueEntry,
  STAFF_ME,
  STUDENT_ME,
} from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

function journalEvent(overrides: Partial<EventDetail> = {}): EventDetail {
  return makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные работы', ...overrides })
}

/** Ограничивает запросы областью хлебных крошек (в сайдбаре тоже есть «События»). */
function breadcrumbs() {
  return within(screen.getByRole('navigation', { name: 'Навигационная цепочка' }))
}

function passedEntry(overrides: Partial<QueueEntry> = {}): QueueEntry {
  return makeQueueEntry({
    id: 'j1',
    position: 1,
    name: 'Бригада А',
    status: 'PASSED',
    passedAt: '2026-05-02T10:00:00Z',
    ...overrides,
  })
}

describe('журнал сдач /e/:slug/journal', () => {
  it('староста видит прошедшие сдачу записи с именем', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: journalEvent(),
      eventById: journalEvent({ journal: [passedEntry()] }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByRole('heading', { name: 'Журнал сдач' })).toBeInTheDocument()
    expect(await screen.findByText('Бригада А')).toBeInTheDocument()
    expect(screen.getAllByTestId('journal-item')).toHaveLength(1)
    const crumbs = breadcrumbs()
    expect(crumbs.getByRole('link', { name: 'События' })).toHaveAttribute('href', '/events')
    expect(crumbs.getByRole('link', { name: 'Лабораторные работы' })).toHaveAttribute(
      'href',
      '/e/lab-works',
    )
    expect(crumbs.getByText('Журнал', { selector: '[aria-current="page"]' })).toBeInTheDocument()
    expect(crumbs.queryByRole('link', { name: 'Журнал' })).not.toBeInTheDocument()
  })

  it('администратор видит журнал всегда', async () => {
    installApiMock({
      me: ADMIN_ME,
      eventBySlug: journalEvent(),
      eventById: journalEvent({ journal: [passedEntry({ name: 'Бригада Б' })] }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText('Бригада Б')).toBeInTheDocument()
    expect(screen.getAllByTestId('journal-item')).toHaveLength(1)
  })

  it('студент видит журнал, открытый для всех', async () => {
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: journalEvent({ journalVisibility: 'EVERYONE' }),
      eventById: journalEvent({ journalVisibility: 'EVERYONE', journal: [passedEntry()] }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText('Бригада А')).toBeInTheDocument()
    expect(screen.queryByText(/скрыт/)).not.toBeInTheDocument()
  })

  it('студент при журнале только для персонала видит сообщение об отсутствии доступа', async () => {
    installApiMock({
      me: STUDENT_ME,
      eventBySlug: journalEvent({ journalVisibility: 'STAFF' }),
      eventById: journalEvent({ journalVisibility: 'STAFF' }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText(/Журнал сдач скрыт/)).toBeInTheDocument()
    expect(screen.queryByTestId('journal-item')).not.toBeInTheDocument()
    const crumbs = breadcrumbs()
    expect(crumbs.getByRole('link', { name: 'События' })).toHaveAttribute('href', '/events')
    expect(crumbs.getByRole('link', { name: 'Лабораторные работы' })).toHaveAttribute(
      'href',
      '/e/lab-works',
    )
  })

  it('гость при журнале только для персонала видит сообщение об отсутствии доступа', async () => {
    installApiMock({
      me: GUEST_ME,
      eventBySlug: journalEvent({ journalVisibility: 'STAFF' }),
      eventById: journalEvent({ journalVisibility: 'STAFF' }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText(/Журнал сдач скрыт/)).toBeInTheDocument()
    expect(screen.queryByTestId('journal-item')).not.toBeInTheDocument()
  })

  it('записи упорядочены по времени сдачи', async () => {
    const later = passedEntry({ id: 'j2', name: 'Поздняя', passedAt: '2026-05-03T10:00:00Z' })
    const earlier = passedEntry({ id: 'j1', name: 'Ранняя', passedAt: '2026-05-01T10:00:00Z' })
    installApiMock({
      me: STAFF_ME,
      eventBySlug: journalEvent(),
      eventById: journalEvent({ journal: [later, earlier] }),
    })
    renderApp(['/e/lab-works/journal'])

    const items = await screen.findAllByTestId('journal-item')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Ранняя')
    expect(items[1]).toHaveTextContent('Поздняя')
  })

  it('журнал закрытого события доступен по прямой ссылке', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: journalEvent({ status: 'CLOSED' }),
      eventById: journalEvent({ status: 'CLOSED', journal: [passedEntry()] }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText('Бригада А')).toBeInTheDocument()
    expect(screen.getByText('Закрыто')).toBeInTheDocument()
  })

  it('архивное событие показывает экран «Событие не найдено»', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: () =>
        jsonResponse(404, { error: 'event_not_found', message: 'Событие не найдено' }),
    })
    renderApp(['/e/lab-works/journal'])

    expect(await screen.findByText('Событие не найдено')).toBeInTheDocument()
    expect(screen.queryByTestId('journal-item')).not.toBeInTheDocument()
    // Крошки деградируют: промежуточной ссылки на событие нет.
    const crumbs = breadcrumbs()
    expect(crumbs.getByRole('link', { name: 'События' })).toHaveAttribute('href', '/events')
    expect(crumbs.getByText('Журнал', { selector: '[aria-current="page"]' })).toBeInTheDocument()
    expect(crumbs.queryByRole('link', { name: 'Лабораторные работы' })).not.toBeInTheDocument()
  })
})
