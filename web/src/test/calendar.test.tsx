import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CalendarEntry, CreateCalendarEntryInput } from '../api/types'
import { buildCalendarWindow, parseIsoDate } from '../utils/calendar'
import { ADMIN_ME, GUEST_ME, STAFF_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse, makeMockCalendarEntry } from './mockApi'
import { renderApp } from './renderApp'

const WINDOW = buildCalendarWindow(new Date(), 0)
const NEXT_WINDOW = buildCalendarWindow(new Date(), 1)
const PREV_WINDOW = buildCalendarWindow(new Date(), -1)
const DAY = WINDOW.days[0]

async function renderCalendar(calendar: CalendarEntry[]) {
  installApiMock({ me: STUDENT_ME, calendar })
  renderApp(['/calendar'])
  return screen.findByTestId('calendar-grid')
}

describe('модуль «Календарь»', () => {
  it('гость видит пустой календарь и не видит кнопку создания', async () => {
    installApiMock({ me: GUEST_ME, calendar: [] })
    renderApp(['/calendar'])

    expect(await screen.findByRole('heading', { name: 'Календарь' })).toBeInTheDocument()
    const grid = await screen.findByTestId('calendar-grid')
    expect(within(grid).getAllByTestId('calendar-day')).toHaveLength(14)
    expect(screen.queryByRole('button', { name: 'Создать мероприятие' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('окно по умолчанию начинается с понедельника и содержит 14 дней', async () => {
    const grid = await renderCalendar([])

    const cells = within(grid).getAllByTestId('calendar-day')
    expect(cells).toHaveLength(14)
    expect(within(cells[0]).getByText('Пн')).toBeInTheDocument()
    expect(parseIsoDate(WINDOW.from).getDay()).toBe(1)
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(WINDOW.from)
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(WINDOW.to)

    const requested = vi.mocked(globalThis.fetch).mock.calls.map(([url]) => String(url))
    expect(requested).toContain(`/api/calendar?from=${WINDOW.from}&to=${WINDOW.to}`)
  })

  it('навигация ±14 дней и «Текущая неделя» переключают окно', async () => {
    const user = userEvent.setup()
    await renderCalendar([])

    expect(screen.getByTestId('calendar-range')).toHaveTextContent(WINDOW.from)

    await user.click(screen.getByRole('button', { name: 'Две недели вперёд →' }))
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(NEXT_WINDOW.from)

    await user.click(screen.getByRole('button', { name: '← Две недели назад' }))
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(WINDOW.from)

    await user.click(screen.getByRole('button', { name: '← Две недели назад' }))
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(PREV_WINDOW.from)

    await user.click(screen.getByRole('button', { name: 'Текущая неделя' }))
    expect(screen.getByTestId('calendar-range')).toHaveTextContent(WINDOW.from)
  })

  it('студент создаёт мероприятие «для вас», оно сразу появляется в календаре', async () => {
    const user = userEvent.setup()
    const entries: CalendarEntry[] = []
    let captured: CreateCalendarEntryInput | null = null
    installApiMock({
      me: STUDENT_ME,
      calendar: () => jsonResponse(200, entries),
      createCalendarEntry: (body) => {
        captured = body as CreateCalendarEntryInput
        const created = makeMockCalendarEntry({ id: 'new', title: 'Новая пара', startsOn: DAY, mine: true })
        entries.push(created)
        return jsonResponse(201, created)
      },
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))
    await user.type(screen.getByLabelText('Название'), 'Новая пара')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured).toMatchObject({
      title: 'Новая пара',
      audience: 'ME',
      startsOn: DAY,
      from: WINDOW.from,
      to: WINDOW.to,
    })
    expect(await within(grid).findByText('Новая пара')).toBeInTheDocument()
  })

  it('студенту доступны только «для вас» и «для группы»', async () => {
    const user = userEvent.setup()
    await renderCalendar([])

    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))
    const select = screen.getByLabelText('Адресат')
    const optionValues = within(select)
      .getAllByRole('option')
      .map((option) => (option as HTMLOptionElement).value)

    expect(optionValues).toEqual(['ME', 'GROUP'])
    expect(within(select).queryByRole('option', { name: 'Для персонала' })).not.toBeInTheDocument()
  })

  it('студент создаёт мероприятие «для группы»', async () => {
    const user = userEvent.setup()
    let captured: CreateCalendarEntryInput | null = null
    installApiMock({
      me: STUDENT_ME,
      calendar: [],
      createCalendarEntry: (body) => {
        captured = body as CreateCalendarEntryInput
        return jsonResponse(201, makeMockCalendarEntry({ title: 'Для группы' }))
      },
    })
    renderApp(['/calendar'])
    await screen.findByTestId('calendar-grid')

    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))
    await user.type(screen.getByLabelText('Название'), 'Собрание')
    await user.selectOptions(screen.getByLabelText('Адресат'), 'GROUP')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured).toMatchObject({ audience: 'GROUP' })
  })

  it('персонал может выбрать адресата «для персонала»', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STAFF_ME, calendar: [] })
    renderApp(['/calendar'])
    await screen.findByTestId('calendar-grid')

    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))
    const select = screen.getByLabelText('Адресат')

    expect(within(select).getByRole('option', { name: 'Для персонала' })).toBeInTheDocument()
  })

  it('ошибка invalid_date показывается, введённые данные сохраняются', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      calendar: [],
      createCalendarEntry: () =>
        jsonResponse(400, { error: 'invalid_date', message: 'Дата вне окна' }),
    })
    renderApp(['/calendar'])
    await screen.findByTestId('calendar-grid')

    await user.click(screen.getByRole('button', { name: 'Создать мероприятие' }))
    await user.type(screen.getByLabelText('Название'), 'Пара вне окна')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Дата должна находиться в отображаемом окне календаря',
    )
    expect(screen.getByLabelText('Название')).toHaveValue('Пара вне окна')
  })

  it('мобильный список содержит те же дни и мероприятия в том же порядке, что и сетка', async () => {
    const entries = [
      makeMockCalendarEntry({ id: 'a', title: 'Позже', startsOn: DAY, startsAt: '18:00:00' }),
      makeMockCalendarEntry({ id: 'b', title: 'Раньше', startsOn: DAY, startsAt: '09:00:00' }),
      makeMockCalendarEntry({ id: 'c', title: 'Без времени', startsOn: DAY, startsAt: null }),
    ]
    installApiMock({ me: STUDENT_ME, calendar: entries })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')
    const list = screen.getByTestId('calendar-list')

    expect(within(list).getAllByTestId('calendar-list-day')).toHaveLength(14)

    const gridTitles = within(grid)
      .getAllByTestId('calendar-entry-title')
      .map((node) => node.textContent)
    const listTitles = within(list)
      .getAllByTestId('calendar-entry-title')
      .map((node) => node.textContent)

    expect(gridTitles).toEqual(['Раньше', 'Позже', 'Без времени'])
    expect(listTitles).toEqual(gridTitles)
  })

  it('в плитке мероприятия нет кнопки удаления', async () => {
    const grid = await renderCalendar([
      makeMockCalendarEntry({ id: 'own', title: 'Своё', mine: true }),
    ])

    expect(within(grid).getByText('Своё')).toBeInTheDocument()
    expect(within(grid).queryAllByRole('button', { name: 'Удалить' })).toHaveLength(0)
  })

  it('разные адресаты дают разные мягкие цвета заливки', async () => {
    const grid = await renderCalendar([
      makeMockCalendarEntry({ id: 'group', title: 'Для группы', audience: 'GROUP' }),
      makeMockCalendarEntry({ id: 'staff', title: 'Для персонала', audience: 'STAFF' }),
    ])

    const tiles = within(grid).getAllByTestId('calendar-entry')
    const groupTile = tiles.find((tile) => tile.dataset.audience === 'GROUP')
    const staffTile = tiles.find((tile) => tile.dataset.audience === 'STAFF')

    expect(groupTile).toBeDefined()
    expect(staffTile).toBeDefined()
    const groupColor = groupTile?.style.getPropertyValue('--entry-color')
    const staffColor = staffTile?.style.getPropertyValue('--entry-color')
    expect(groupColor).toBe('#cfe3ff')
    expect(staffColor).toBe('#d9dde3')
    expect(groupColor).not.toBe(staffColor)
  })

  it('клик по мероприятию открывает карточку', async () => {
    const user = userEvent.setup()
    const grid = await renderCalendar([
      makeMockCalendarEntry({ id: 'own', title: 'Своё', mine: true }),
    ])

    await user.click(within(grid).getByText('Своё'))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: 'Своё' })).toBeInTheDocument()
  })

  it('автор видит в карточке «Редактировать» и «Удалить»', async () => {
    const user = userEvent.setup()
    const grid = await renderCalendar([
      makeMockCalendarEntry({ id: 'own', title: 'Своё', mine: true }),
    ])

    await user.click(within(grid).getByText('Своё'))
    const dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByRole('button', { name: 'Редактировать' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Удалить' })).toBeInTheDocument()
  })

  it('чужое мероприятие: карточка показывает данные без кнопок действий', async () => {
    const user = userEvent.setup()
    const grid = await renderCalendar([
      makeMockCalendarEntry({
        id: 'foreign',
        title: 'Чужое',
        mine: false,
        audience: 'GROUP',
        authorDisplayName: 'Петров Пётр',
      }),
    ])

    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByText('Петров Пётр')).toBeInTheDocument()
    expect(within(dialog).getByText('Для группы')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Редактировать' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument()
  })

  it('староста видит чужое мероприятие, но не может его изменить или удалить', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STAFF_ME,
      calendar: [makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false })],
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByRole('heading', { name: 'Чужое' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Редактировать' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument()
  })

  it('карточка показывает прочерк, когда автор без имени', async () => {
    const user = userEvent.setup()
    const grid = await renderCalendar([
      makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false, authorDisplayName: null }),
    ])

    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')

    const term = within(dialog).getByText('Автор')
    const row = term.closest('.details__row') as HTMLElement
    expect(within(row).getByText('—')).toBeInTheDocument()
  })

  it('администратор видит действия на чужом мероприятии', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: ADMIN_ME,
      calendar: [makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false })],
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByRole('button', { name: 'Редактировать' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Удалить' })).toBeInTheDocument()
  })

  it('автор удаляет своё мероприятие с подтверждением', async () => {
    const user = userEvent.setup()
    const entries = [makeMockCalendarEntry({ id: 'own', title: 'Своё', mine: true })]
    const deleteCalendarEntry = vi.fn(() => {
      entries.length = 0
      return jsonResponse(204, undefined)
    })
    installApiMock({ me: STUDENT_ME, calendar: () => jsonResponse(200, entries), deleteCalendarEntry })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(within(grid).getByText('Своё'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => expect(deleteCalendarEntry).toHaveBeenCalledWith('own'))
    await waitFor(() => expect(within(grid).queryByText('Своё')).not.toBeInTheDocument())
  })

  it('отказ от подтверждения не удаляет мероприятие', async () => {
    const user = userEvent.setup()
    const deleteCalendarEntry = vi.fn(() => jsonResponse(204, undefined))
    installApiMock({
      me: ADMIN_ME,
      calendar: [makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false })],
      deleteCalendarEntry,
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    expect(deleteCalendarEntry).not.toHaveBeenCalled()
    expect(within(grid).getByText('Чужое')).toBeInTheDocument()
  })

  it('администратор удаляет чужое мероприятие', async () => {
    const user = userEvent.setup()
    const entries = [makeMockCalendarEntry({ id: 'foreign', title: 'Чужое', mine: false })]
    const deleteCalendarEntry = vi.fn(() => {
      entries.length = 0
      return jsonResponse(204, undefined)
    })
    installApiMock({ me: ADMIN_ME, calendar: () => jsonResponse(200, entries), deleteCalendarEntry })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(within(grid).getByText('Чужое'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => expect(deleteCalendarEntry).toHaveBeenCalledWith('foreign'))
    await waitFor(() => expect(within(grid).queryByText('Чужое')).not.toBeInTheDocument())
  })

  it('автор редактирует мероприятие, сетка показывает обновлённые данные', async () => {
    const user = userEvent.setup()
    let entries = [makeMockCalendarEntry({ id: 'own', title: 'Старое', mine: true })]
    let captured: { id: string; body: unknown } | null = null
    installApiMock({
      me: STUDENT_ME,
      calendar: () => jsonResponse(200, entries),
      updateCalendarEntry: (id, body) => {
        captured = { id, body }
        const patch = body as Partial<CalendarEntry>
        entries = entries.map((entry) =>
          entry.id === id ? { ...entry, title: patch.title ?? entry.title } : entry,
        )
        return jsonResponse(200, entries[0])
      },
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await user.click(within(grid).getByText('Старое'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Редактировать' }))

    const titleInput = screen.getByLabelText('Название')
    await user.clear(titleInput)
    await user.type(titleInput, 'Обновлённое')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured).toMatchObject({ id: 'own', body: { title: 'Обновлённое' } })
    expect(await within(grid).findByText('Обновлённое')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('ошибка calendar_forbidden при редактировании сохраняет введённые данные', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      calendar: [makeMockCalendarEntry({ id: 'own', title: 'Старое', mine: true })],
      updateCalendarEntry: () =>
        jsonResponse(403, { error: 'calendar_forbidden', message: 'Нет прав' }),
    })
    renderApp(['/calendar'])
    const grid = await screen.findByTestId('calendar-grid')

    await user.click(within(grid).getByText('Старое'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Редактировать' }))

    const titleInput = screen.getByLabelText('Название')
    await user.clear(titleInput)
    await user.type(titleInput, 'Новый вариант')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Недостаточно прав для изменения мероприятия',
    )
    expect(screen.getByLabelText('Название')).toHaveValue('Новый вариант')
  })
})
