import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CreateEventInput } from '../api/types'
import { GUEST_ME, makeEventDetail, STAFF_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse, makeMockEvent } from './mockApi'
import { renderApp } from './renderApp'

describe('создание события', () => {
  it('студент не видит действие «Создать событие»', async () => {
    installApiMock({ me: STUDENT_ME, events: [] })
    renderApp(['/events'])

    await screen.findByRole('heading', { name: 'События' })
    expect(screen.queryByRole('link', { name: 'Создать событие' })).not.toBeInTheDocument()
  })

  it('гость не видит действие «Создать событие»', async () => {
    installApiMock({ me: GUEST_ME, events: [] })
    renderApp(['/events'])

    await screen.findByRole('heading', { name: 'События' })
    expect(screen.queryByRole('link', { name: 'Создать событие' })).not.toBeInTheDocument()
  })

  it('персонал создаёт событие с серверными дефолтами и попадает на него', async () => {
    const user = userEvent.setup()
    let captured: CreateEventInput | null = null
    installApiMock({
      me: STAFF_ME,
      events: [],
      createEvent: (body) => {
        captured = body as CreateEventInput
        return jsonResponse(201, makeMockEvent({ slug: 'new-event', title: 'Новая пара' }))
      },
      eventBySlug: (slug) =>
        jsonResponse(200, makeEventDetail({ id: 'created-event', slug, title: 'Новая пара' })),
    })
    renderApp(['/events'])

    await user.click(await screen.findByRole('link', { name: 'Создать событие' }))
    expect(await screen.findByRole('heading', { name: 'Создание события' })).toBeInTheDocument()

    await user.type(screen.getByLabelText('Название'), '  Новая пара  ')
    await user.click(screen.getByRole('button', { name: 'Создать событие' }))

    expect(await screen.findByRole('heading', { name: 'Новая пара' })).toBeInTheDocument()
    expect(captured).toMatchObject({
      title: 'Новая пара',
      description: null,
      availability: 'GUEST+',
      startsAt: null,
      entryLimit: 27,
      entryUnit: 'BRIGADE',
      journalVisibility: 'STAFF',
      retentionDays: 14,
    })
  })

  it('ошибка сервера показывается, данные формы сохраняются, повторная отправка возможна', async () => {
    const user = userEvent.setup()
    const createEvent = vi.fn(() =>
      jsonResponse(400, {
        error: 'invalid_entry_limit',
        message: 'Лимит записей не должен превышать 1000',
      }),
    )
    installApiMock({ me: STAFF_ME, createEvent })
    renderApp(['/events/new'])

    await screen.findByRole('heading', { name: 'Создание события' })
    await user.type(screen.getByLabelText('Название'), 'Тестовое событие')
    await user.click(screen.getByRole('button', { name: 'Создать событие' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Лимит записей должен быть от 1 до 1000')
    expect(screen.getByLabelText('Название')).toHaveValue('Тестовое событие')
    expect(createEvent).toHaveBeenCalledTimes(1)

    await user.click(screen.getByRole('button', { name: 'Создать событие' }))
    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(2))
    expect(screen.getByLabelText('Название')).toHaveValue('Тестовое событие')
  })

  it('кнопка «Создать событие» находится в шапке страницы', async () => {
    installApiMock({ me: STAFF_ME, events: [] })
    renderApp(['/events'])

    const link = await screen.findByRole('link', { name: 'Создать событие' })
    expect(link.closest('.page__header')).not.toBeNull()
  })

  it('форма создания использует вертикальную раскладку', async () => {
    installApiMock({ me: STAFF_ME })
    renderApp(['/events/new'])

    await screen.findByRole('heading', { name: 'Создание события' })
    const form = screen.getByRole('button', { name: 'Создать событие' }).closest('form')
    expect(form).not.toBeNull()
    expect(form).toHaveClass('admin-form', 'admin-form--stacked')
  })
})
