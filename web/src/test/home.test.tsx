import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Preferences } from '../api/types'
import { STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

const DEFAULT_COLORS = { ME: '#ffffff', GROUP: '#cfe3ff', STAFF: '#d9dde3' }

function preferences(modules: Preferences['modules']): Preferences {
  return { modules, calendarColors: DEFAULT_COLORS }
}

describe('главная страница как реестр модулей', () => {
  it('реестр содержит плитки «События», «Календарь» и «Заметки», переход открывает /events', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, events: [], calendar: [], notes: { notes: [], quota: { usedBytes: 0, limitBytes: 10485760 } } })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    expect(tiles).toHaveLength(3)
    expect(tiles[0]).toHaveTextContent('События')
    expect(tiles[1]).toHaveTextContent('Календарь')
    expect(tiles[2]).toHaveTextContent('Заметки')

    await user.click(tiles[0])
    expect(await screen.findByRole('heading', { name: 'События' })).toBeInTheDocument()
  })

  it('переход по плитке «Календарь» открывает календарь, а не список событий', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, events: [], calendar: [] })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    await user.click(tiles[1])

    expect(await screen.findByRole('heading', { name: 'Календарь' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'События' })).not.toBeInTheDocument()
  })

  it('переход по плитке «Заметки» открывает модуль заметок', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    await user.click(tiles[2])

    expect(await screen.findByRole('heading', { name: 'Заметки' })).toBeInTheDocument()
  })

  it('выключенный модуль не показывается на главной', async () => {
    installApiMock({
      me: STUDENT_ME,
      events: [],
      calendar: [],
      preferences: preferences({ events: true, calendar: true, notes: false }),
    })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    await waitFor(() => expect(within(grid).getAllByTestId('module-tile')).toHaveLength(2))
    expect(within(grid).getByText('События')).toBeInTheDocument()
    expect(within(grid).getByText('Календарь')).toBeInTheDocument()
    expect(within(grid).queryByText('Заметки')).not.toBeInTheDocument()
  })

  it('при всех выключенных модулях плиток нет, показано пустое состояние', async () => {
    installApiMock({
      me: STUDENT_ME,
      preferences: preferences({ events: false, calendar: false, notes: false }),
    })
    renderApp(['/'])

    expect(await screen.findByTestId('home-empty')).toBeInTheDocument()
    expect(screen.queryByTestId('module-grid')).not.toBeInTheDocument()
    expect(screen.queryAllByTestId('module-tile')).toHaveLength(0)
  })
})
