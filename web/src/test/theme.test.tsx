import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { THEME_STORAGE_KEY } from '../theme/theme'
import { STUDENT_ME } from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

describe('переключатель темы', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.style.colorScheme = ''
  })

  it('переключает data-theme и сохраняет выбор в localStorage', async () => {
    const user = userEvent.setup()
    installApiMock({ me: STUDENT_ME })
    renderApp(['/'])

    await screen.findByTestId('module-grid')
    expect(document.documentElement.getAttribute('data-theme')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Включить тёмную тему' }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Включить светлую тему' }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
  })
})
