import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { CalendarAudience, CalendarEntry, Preferences } from '../api/types'
import { ENTRY_FILL_ALPHA, entryFillStyle } from '../utils/calendar'
import { STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse, makeMockCalendarEntry } from './mockApi'
import { renderApp } from './renderApp'

const LONG_DESCRIPTION = 'Очень длинное описание мероприятия. '.repeat(10)
const COLORS: Record<CalendarAudience, string> = {
  ME: '#ff0000',
  GROUP: '#00ff00',
  STAFF: '#0000ff',
}

function customPreferences(calendarColors: Record<CalendarAudience, string>): Preferences {
  return {
    modules: { events: true, calendar: true, notes: true },
    calendarColors,
  }
}

async function openCard(user: ReturnType<typeof userEvent.setup>, entry: CalendarEntry) {
  installApiMock({ me: STUDENT_ME, calendar: [entry] })
  renderApp(['/calendar'])
  const grid = await screen.findByTestId('calendar-grid')
  await user.click(within(grid).getByText(entry.title))
  return screen.findByRole('dialog')
}

describe('заливка мероприятий', () => {
  it('любой выбранный цвет применяется с одной и той же альфой', () => {
    const styles = (['ME', 'GROUP', 'STAFF'] as const).map(
      (audience) => entryFillStyle(audience, COLORS) as Record<string, string>,
    )

    expect(styles.map((style) => style['--entry-alpha'])).toEqual([
      ENTRY_FILL_ALPHA,
      ENTRY_FILL_ALPHA,
      ENTRY_FILL_ALPHA,
    ])
    expect(styles.map((style) => style['--entry-color'])).toEqual([
      '#ff0000',
      '#00ff00',
      '#0000ff',
    ])
  })

  it('в DOM выбранные цвета дают одинаковую альфу и разные оттенки', async () => {
    const entries = [
      makeMockCalendarEntry({ id: 'me', title: 'Моё', audience: 'ME' }),
      makeMockCalendarEntry({ id: 'grp', title: 'Групповое', audience: 'GROUP' }),
      makeMockCalendarEntry({ id: 'stf', title: 'Персоналу', audience: 'STAFF' }),
    ]
    installApiMock({
      me: STUDENT_ME,
      calendar: entries,
      preferences: customPreferences(COLORS),
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await waitFor(() => {
      const colors = within(grid)
        .getAllByTestId('calendar-entry')
        .map((tile) => tile.style.getPropertyValue('--entry-color'))
      expect(colors).toContain('#ff0000')
    })

    const tiles = within(grid).getAllByTestId('calendar-entry')
    const alphas = tiles.map((tile) => tile.style.getPropertyValue('--entry-alpha'))
    const colors = tiles.map((tile) => tile.style.getPropertyValue('--entry-color'))

    expect(new Set(alphas)).toEqual(new Set([ENTRY_FILL_ALPHA]))
    expect(new Set(colors).size).toBe(3)
  })
})

describe('карточка мероприятия: длинное описание', () => {
  it('свёрнуто и раскрывается вертикально кнопкой «Развернуть»', async () => {
    const user = userEvent.setup()
    const dialog = await openCard(
      user,
      makeMockCalendarEntry({ id: 'long', title: 'Длинное', mine: true, description: LONG_DESCRIPTION }),
    )

    const collapsed = within(dialog).getByTestId('calendar-entry-description')
    expect(collapsed).toHaveAttribute('data-clamped', 'true')
    expect(collapsed.textContent).toBe(LONG_DESCRIPTION)
    expect(collapsed.className).not.toContain('calendar-card__description--expanded')

    await user.click(within(dialog).getByRole('button', { name: 'Развернуть' }))

    const expanded = within(dialog).getByTestId('calendar-entry-description')
    expect(expanded).toHaveAttribute('data-clamped', 'false')
    expect(expanded.className).toContain('calendar-card__description--expanded')
    expect(expanded.textContent).toBe(LONG_DESCRIPTION)

    await user.click(within(dialog).getByRole('button', { name: 'Свернуть' }))
    expect(within(dialog).getByTestId('calendar-entry-description')).toHaveAttribute(
      'data-clamped',
      'true',
    )
    expect(within(dialog).getByRole('button', { name: 'Развернуть' })).toBeInTheDocument()
  })

  it('короткое описание не получает кнопку раскрытия', async () => {
    const user = userEvent.setup()
    const dialog = await openCard(
      user,
      makeMockCalendarEntry({ id: 'short', title: 'Короткое', mine: true, description: 'Коротко' }),
    )

    expect(within(dialog).getByTestId('calendar-entry-description')).toHaveTextContent('Коротко')
    expect(within(dialog).queryByRole('button', { name: 'Развернуть' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Свернуть' })).not.toBeInTheDocument()
  })
})

describe('карточка мероприятия: действия', () => {
  it('кнопки вынесены в отдельный подвал и не вложены в описание', async () => {
    const user = userEvent.setup()
    const dialog = await openCard(
      user,
      makeMockCalendarEntry({ id: 'long', title: 'Длинное', mine: true, description: LONG_DESCRIPTION }),
    )

    const footer = within(dialog).getByTestId('calendar-card-footer')
    const description = within(dialog).getByTestId('calendar-entry-description')
    const editButton = within(footer).getByRole('button', { name: 'Редактировать' })

    expect(description.contains(footer)).toBe(false)
    expect(description.contains(editButton)).toBe(false)
    expect(within(footer).getByRole('button', { name: 'Удалить' })).toBeInTheDocument()
  })

  it('чужая карточка без действий не имеет подвала', async () => {
    const user = userEvent.setup()
    const dialog = await openCard(
      user,
      makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false, description: 'Коротко' }),
    )

    expect(within(dialog).queryByTestId('calendar-card-footer')).not.toBeInTheDocument()
  })
})

describe('форма мероприятия: описание', () => {
  it('поле ограничено 150 символами и показывает подсказку-счётчик', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, calendar: [] })
    renderApp(['/calendar'])
    await screen.findByTestId('calendar-grid')
    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))

    const textarea = screen.getByLabelText('Описание')
    expect(textarea).toHaveAttribute('maxlength', '150')
    expect(screen.getByText(/до 150 символов/)).toBeInTheDocument()
  })

  it('ошибка invalid_description показывается без потери введённого текста', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      calendar: [],
      createCalendarEntry: () =>
        jsonResponse(400, { error: 'invalid_description', message: 'Слишком длинное описание' }),
    })
    renderApp(['/calendar'])
    await screen.findByTestId('calendar-grid')
    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))

    await user.type(screen.getByLabelText('Название'), 'Пара')
    await user.type(screen.getByLabelText('Описание'), 'Длинное описание')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Описание слишком длинное')
    expect(screen.getByLabelText('Описание')).toHaveValue('Длинное описание')
  })
})
