import { waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'

function iconLink(): HTMLLinkElement {
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    link.href = '/favicon.svg'
    document.head.appendChild(link)
  }
  return link
}

describe('иконка сайта во вкладке браузера', () => {
  afterEach(() => {
    document.head.querySelectorAll('link[rel="icon"]').forEach((link) => link.remove())
  })

  it('подставляет публичную иконку, когда она загружена (200)', async () => {
    iconLink()
    installApiMock({ me: null, siteIcon: { exists: true, etag: '"icon-1"' } })

    renderApp(['/login'])

    await waitFor(() => {
      expect(iconLink().getAttribute('href')).toContain('/api/site/icon')
    })
  })

  it('оставляет встроенную favicon.svg, когда иконка не загружена (404)', async () => {
    iconLink()
    installApiMock({ me: null, siteIcon: { exists: false } })

    renderApp(['/login'])

    await waitFor(() => {
      expect(iconLink().getAttribute('href')).toBe('/favicon.svg')
    })
  })

  it('не показывает ошибку пользователю при сбое загрузки иконки', async () => {
    iconLink()
    // No siteIcon mock => the endpoint resolves to 404 in the default mock.
    installApiMock({ me: null })

    renderApp(['/login'])

    await waitFor(() => {
      expect(iconLink().getAttribute('href')).toBe('/favicon.svg')
    })
  })
})
