import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mergePreferences } from '../api/preferences'
import type { Preferences, UpdatePreferencesInput } from '../api/types'
import { GUEST_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse } from './mockApi'
import { renderApp } from './renderApp'

const DEFAULT_PREFS: Preferences = {
  modules: { events: true, calendar: true, notes: true },
  calendarColors: { ME: '#ffffff', GROUP: '#cfe3ff', STAFF: '#d9dde3' },
}

function requestedPreferences(): boolean {
  return vi
    .mocked(globalThis.fetch)
    .mock.calls.some(([url]) => url === '/api/students/me/preferences')
}

describe('страница «Настройки»', () => {
  it('показывает значения по умолчанию: все модули включены, цвета дефолтные', async () => {
    installApiMock({ me: STUDENT_ME })
    renderApp(['/settings'])

    expect(await screen.findByRole('heading', { name: 'Настройки' })).toBeInTheDocument()
    expect(screen.getByLabelText('События')).toBeChecked()
    expect(screen.getByLabelText('Календарь')).toBeChecked()
    expect(screen.getByLabelText('Заметки')).toBeChecked()

    expect(screen.getByLabelText('Ваш цвет')).toHaveValue('#ffffff')
    expect(screen.getByLabelText('Цвет группы')).toHaveValue('#cfe3ff')
    expect(screen.getByLabelText('Цвет персонала')).toHaveValue('#d9dde3')
  })

  it('выключение модуля без перезагрузки скрывает его в меню и на главной', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/settings'])

    const eventsToggle = await screen.findByLabelText('События')
    await user.click(eventsToggle)
    await waitFor(() => expect(eventsToggle).not.toBeChecked())

    await user.click(screen.getByRole('link', { name: 'NSTU System' }))
    await screen.findByRole('heading', { name: 'Главная' })

    const grid = screen.getByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    expect(tiles).toHaveLength(2)
    expect(within(grid).queryByText('События')).not.toBeInTheDocument()
    expect(within(grid).getByText('Календарь')).toBeInTheDocument()
    expect(within(grid).getByText('Заметки')).toBeInTheDocument()

    const nav = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(nav).queryByRole('link', { name: 'События' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Календарь' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Настройки' })).toBeInTheDocument()
  })

  it('смена цвета сохраняется и не перезагружает страницу', async () => {
    installApiMock({ me: STUDENT_ME })
    renderApp(['/settings'])

    const groupColor = (await screen.findByLabelText('Цвет группы')) as HTMLInputElement
    expect(groupColor).toHaveValue('#cfe3ff')

    fireEvent.change(groupColor, { target: { value: '#ff0000' } })

    await waitFor(() => expect(groupColor).toHaveValue('#ff0000'))
    expect(screen.getByLabelText('Ваш цвет')).toHaveValue('#ffffff')
    expect(screen.getByLabelText('Цвет персонала')).toHaveValue('#d9dde3')
  })

  it('«Сбросить» возвращает только этот цвет, остальные не меняются', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/settings'])

    const groupColor = (await screen.findByLabelText('Цвет группы')) as HTMLInputElement
    const staffColor = screen.getByLabelText('Цвет персонала') as HTMLInputElement

    fireEvent.change(groupColor, { target: { value: '#ff0000' } })
    await waitFor(() => expect(groupColor).toHaveValue('#ff0000'))
    fireEvent.change(staffColor, { target: { value: '#00ff00' } })
    await waitFor(() => expect(staffColor).toHaveValue('#00ff00'))

    await user.click(screen.getByRole('button', { name: 'Сбросить Цвет группы' }))

    await waitFor(() => expect(groupColor).toHaveValue('#cfe3ff'))
    expect(staffColor).toHaveValue('#00ff00')
    expect(screen.getByLabelText('Ваш цвет')).toHaveValue('#ffffff')
  })

  it('ошибка invalid_color показывается, остальные значения не сбрасываются', async () => {
    installApiMock({
      me: STUDENT_ME,
      preferences: { ...DEFAULT_PREFS, calendarColors: { ...DEFAULT_PREFS.calendarColors, ME: '#112233' } },
      updatePreferences: (body) => {
        const patch = body as UpdatePreferencesInput
        if (patch.calendarColors?.GROUP && patch.calendarColors.GROUP !== '#cfe3ff') {
          return jsonResponse(400, { error: 'invalid_color', message: 'Недопустимый цвет' })
        }
        return jsonResponse(200, mergePreferences(DEFAULT_PREFS, patch))
      },
    })
    renderApp(['/settings'])

    const groupColor = (await screen.findByLabelText('Цвет группы')) as HTMLInputElement
    fireEvent.change(groupColor, { target: { value: '#ff0000' } })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Недопустимый цвет. Используйте формат #RRGGBB',
    )
    await waitFor(() => expect(groupColor).toHaveValue('#cfe3ff'))
    expect(screen.getByLabelText('Ваш цвет')).toHaveValue('#112233')
    expect(screen.getByLabelText('Цвет персонала')).toHaveValue('#d9dde3')
  })

  it('гость не видит пункт «Настройки» и не запрашивает настройки', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    const nav = screen.getByRole('navigation', { name: 'Основная навигация' })
    expect(within(nav).queryByRole('link', { name: 'Настройки' })).not.toBeInTheDocument()
    expect(requestedPreferences()).toBe(false)
  })

  it('прямой переход гостя на /settings показывает экран отказа', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/settings'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
    expect(screen.queryByText('Модульность')).not.toBeInTheDocument()
    expect(requestedPreferences()).toBe(false)
  })
})
