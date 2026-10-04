import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

describe('главная страница как реестр модулей', () => {
  it('в MVP ровно одна плитка «События» и переход открывает /events', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME, events: [] })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    expect(tiles).toHaveLength(1)
    expect(tiles[0]).toHaveTextContent('События')

    await user.click(tiles[0])
    expect(await screen.findByRole('heading', { name: 'События' })).toBeInTheDocument()
  })
})
