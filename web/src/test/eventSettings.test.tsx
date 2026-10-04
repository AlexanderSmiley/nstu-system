import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MockedFunction } from 'vitest'
import type { EventDetail, UpdateEventInput } from '../api/types'
import {
  ADMIN_ME,
  GUEST_ME,
  makeEventDetail,
  makeQueue,
  makeQueueEntry,
  STAFF_ME,
  STUDENT_ME,
} from './fixtures'
import { installApiMock, jsonResponse, makeMockEvent } from './mockApi'
import { renderApp } from './renderApp'

function callsTo(fetchMock: MockedFunction<typeof fetch>, url: string): number {
  return fetchMock.mock.calls.filter(([calledUrl]) => calledUrl === url).length
}

/** Ограничивает запросы областью хлебных крошек (в сайдбаре тоже есть «Настройки»). */
function breadcrumbs() {
  return within(screen.getByRole('navigation', { name: 'Навигационная цепочка' }))
}

/** Event detail with the identifiers the settings route tests rely on. */
function settingsEvent(overrides: Partial<EventDetail> = {}): EventDetail {
  return makeEventDetail({ id: 'e1', slug: 'lab-works', ...overrides })
}

describe('настройки события /e/:slug/settings', () => {
  it('староста видит поля, но не короткую ссылку и срок хранения', async () => {
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent(),
      queue: makeQueue({ eventId: 'e1' }),
    })
    renderApp(['/e/lab-works/settings'])

    expect(await screen.findByRole('heading', { name: 'Настройки события' })).toBeInTheDocument()
    expect(await screen.findByLabelText('Название')).toHaveValue('Сдача лабораторных работ')
    expect(screen.getByLabelText('Описание')).toBeInTheDocument()
    expect(screen.getByLabelText('Доступность')).toBeInTheDocument()
    expect(screen.getByLabelText('Лимит записей')).toBeInTheDocument()
    expect(screen.getByLabelText('Единица записи')).toBeInTheDocument()
    expect(screen.getByLabelText('Видимость журнала')).toBeInTheDocument()
    expect(screen.queryByLabelText('Короткая ссылка')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Срок хранения, дней')).not.toBeInTheDocument()
    const crumbs = breadcrumbs()
    expect(crumbs.getByRole('link', { name: 'События' })).toHaveAttribute('href', '/events')
    expect(crumbs.getByRole('link', { name: 'Сдача лабораторных работ' })).toHaveAttribute(
      'href',
      '/e/lab-works',
    )
    expect(crumbs.getByText('Настройки', { selector: '[aria-current="page"]' })).toBeInTheDocument()
    expect(crumbs.queryByRole('link', { name: 'Настройки' })).not.toBeInTheDocument()
  })

  it('администратор дополнительно видит короткую ссылку и срок хранения', async () => {
    installApiMock({
      me: ADMIN_ME,
      eventBySlug: settingsEvent(),
      queue: makeQueue({ eventId: 'e1' }),
    })
    renderApp(['/e/lab-works/settings'])

    expect(await screen.findByLabelText('Короткая ссылка')).toHaveValue('lab-works')
    expect(screen.getByLabelText('Срок хранения, дней')).toHaveValue(14)
  })

  it('студент получает экран отсутствия прав и не раскрывает настройки', async () => {
    installApiMock({ me: STUDENT_ME })
    renderApp(['/e/lab-works/settings'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Название')).not.toBeInTheDocument()
  })

  it('гость получает экран отсутствия прав', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/e/lab-works/settings'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Название')).not.toBeInTheDocument()
  })

  it('единица записи заблокирована при непустой очереди', async () => {
    const entry = makeQueueEntry({ id: 'q1', position: 1, name: 'Бригада 1', status: 'WAITING' })
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent(),
      queue: makeQueue({ eventId: 'e1', queue: [entry] }),
    })
    renderApp(['/e/lab-works/settings'])

    const unit = await screen.findByLabelText('Единица записи')
    await waitFor(() => expect(unit).toBeDisabled())
    expect(await screen.findByText(/В очереди есть записи/)).toBeInTheDocument()
  })

  it('сохранение старостой отправляет PATCH без полей администратора', async () => {
    const user = userEvent.setup()
    const captured: { id: string; body: UpdateEventInput }[] = []
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent(),
      queue: makeQueue({ eventId: 'e1' }),
      updateEvent: (id, body) => {
        captured.push({ id, body: body as UpdateEventInput })
        return jsonResponse(200, makeMockEvent({ id, slug: 'lab-works', title: 'Новое название' }))
      },
    })
    renderApp(['/e/lab-works/settings'])

    const title = await screen.findByLabelText('Название')
    await user.clear(title)
    await user.type(title, 'Новое название')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => expect(captured).toHaveLength(1))
    expect(captured[0].id).toBe('e1')
    expect(captured[0].body).toMatchObject({
      title: 'Новое название',
      availability: 'GUEST+',
      entryLimit: 27,
      entryUnit: 'BRIGADE',
      journalVisibility: 'STAFF',
    })
    expect(captured[0].body).not.toHaveProperty('slug')
    expect(captured[0].body).not.toHaveProperty('retentionDays')
    expect(await screen.findByRole('status')).toHaveTextContent('Изменения сохранены')
  })

  it('ошибка 409 показывается, данные формы сохраняются', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent(),
      queue: makeQueue({ eventId: 'e1' }),
      updateEvent: () =>
        jsonResponse(409, {
          error: 'entry_unit_locked',
          message: 'Единицу записи нельзя изменить',
        }),
    })
    renderApp(['/e/lab-works/settings'])

    const title = await screen.findByLabelText('Название')
    await user.clear(title)
    await user.type(title, 'Изменённое название')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Единицу записи нельзя изменить, пока в очереди есть записи',
    )
    expect(screen.getByLabelText('Название')).toHaveValue('Изменённое название')
  })

  it('закрытие приёма переводит событие в режим просмотра через closeEvent', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent({ status: 'OPEN' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'OPEN' }),
    })
    renderApp(['/e/lab-works/settings'])

    const close = await screen.findByRole('button', { name: 'Закрыть приём' })
    const fetchMock = vi.mocked(globalThis.fetch)
    await user.click(close)

    await waitFor(() => expect(callsTo(fetchMock, '/api/events/e1/close')).toBe(1))
    expect(screen.queryByRole('button', { name: 'Архивировать' })).not.toBeInTheDocument()
  })

  it('закрытое событие предлагает «Открыть приём»', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent({ status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED' }),
    })
    renderApp(['/e/lab-works/settings'])

    const open = await screen.findByRole('button', { name: 'Открыть приём' })
    const fetchMock = vi.mocked(globalThis.fetch)
    await user.click(open)

    await waitFor(() => expect(callsTo(fetchMock, '/api/events/e1/open')).toBe(1))
  })

  it('староста может архивировать закрытое событие, запрашивает подтверждение', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    installApiMock({
      me: STAFF_ME,
      eventBySlug: settingsEvent({ status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED' }),
      eventHistory: [],
    })
    renderApp(['/e/lab-works/settings'])

    expect(await screen.findByRole('button', { name: 'Открыть приём' })).toBeInTheDocument()
    const archive = await screen.findByRole('button', { name: 'Архивировать' })
    await user.click(archive)

    expect(confirmSpy).toHaveBeenCalled()
    await waitFor(() =>
      expect(
        vi.mocked(globalThis.fetch).mock.calls.some(([url]) => url === '/api/events/e1/archive'),
      ).toBe(true),
    )
  })

  it('архивация доступна администратору, запрашивает подтверждение и уводит в историю', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    installApiMock({
      me: ADMIN_ME,
      eventBySlug: settingsEvent({ status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED' }),
      eventHistory: [],
    })
    renderApp(['/e/lab-works/settings'])

    const archive = await screen.findByRole('button', { name: 'Архивировать' })
    await user.click(archive)

    expect(confirmSpy).toHaveBeenCalled()
    await waitFor(() =>
      expect(
        vi.mocked(globalThis.fetch).mock.calls.some(([url]) => url === '/api/events/e1/archive'),
      ).toBe(true),
    )
    expect(await screen.findByRole('heading', { name: 'История событий' })).toBeInTheDocument()
  })

  it('архивация не выполняется при отказе от подтверждения', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    installApiMock({
      me: ADMIN_ME,
      eventBySlug: settingsEvent({ status: 'CLOSED' }),
      queue: makeQueue({ eventId: 'e1', eventStatus: 'CLOSED' }),
    })
    renderApp(['/e/lab-works/settings'])

    await user.click(await screen.findByRole('button', { name: 'Архивировать' }))

    expect(
      vi.mocked(globalThis.fetch).mock.calls.some(([url]) => url === '/api/events/e1/archive'),
    ).toBe(false)
  })
})
