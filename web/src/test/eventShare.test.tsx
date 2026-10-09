import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ADMIN_ME,
  GUEST_ME,
  makeEventDetail,
  makeQueue,
  STAFF_ME,
  STUDENT_ME,
} from './fixtures'
import { installApiMock } from './mockApi'
import { renderApp } from './renderApp'
import { buildEventShareUrl, canUseWebShare, copyText } from '../utils/share'

/**
 * jsdom implements neither the async Clipboard API, the Web Share API nor
 * `document.execCommand`. The tests install them as own configurable properties
 * and remove them in `afterEach` so no stub leaks into another test.
 */
function setNavigatorProperty(name: string, value: unknown): void {
  Object.defineProperty(navigator, name, { configurable: true, value })
}

function setDocumentProperty(name: string, value: unknown): void {
  Object.defineProperty(document, name, { configurable: true, value })
}

afterEach(() => {
  delete (navigator as { clipboard?: unknown; share?: unknown }).clipboard
  delete (navigator as { clipboard?: unknown; share?: unknown }).share
  delete (document as { execCommand?: unknown }).execCommand
})

const event = makeEventDetail({ id: 'e1', slug: 'lab-works', title: 'Лабораторные' })
const expectedUrl = `${window.location.origin}/e/lab-works`

function renderEventPage(me = STAFF_ME): void {
  installApiMock({
    me,
    eventBySlug: event,
    queue: makeQueue({ eventId: 'e1' }),
  })
  renderApp(['/e/lab-works'])
}

describe('утилиты share', () => {
  it('buildEventShareUrl собирает полную короткую ссылку', () => {
    expect(buildEventShareUrl('lab-works')).toBe(expectedUrl)
  })

  it('copyText использует Clipboard API и сообщает об успехе', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setNavigatorProperty('clipboard', { writeText })

    await expect(copyText('текст')).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('текст')
  })

  it('copyText откатывается на execCommand при отказе Clipboard API', async () => {
    setNavigatorProperty('clipboard', {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    })
    const execCommand = vi.fn().mockReturnValue(true)
    setDocumentProperty('execCommand', execCommand)

    await expect(copyText('текст')).resolves.toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('copyText возвращает false, когда недоступен и fallback', async () => {
    setNavigatorProperty('clipboard', {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    })
    setDocumentProperty('execCommand', () => {
      throw new Error('execCommand unavailable')
    })

    await expect(copyText('текст')).resolves.toBe(false)
  })

  it('canUseWebShare отражает наличие navigator.share', () => {
    delete (navigator as { share?: unknown }).share
    expect(canUseWebShare()).toBe(false)

    setNavigatorProperty('share', vi.fn())
    expect(canUseWebShare()).toBe(true)
  })
})

describe('блок «Поделиться» на странице события', () => {
  it('староста видит ссылку и действие «Скопировать», но без «Поделиться» без Web Share API', async () => {
    renderEventPage(STAFF_ME)

    expect(await screen.findByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Поделиться событием' })).toBeInTheDocument()
    expect(screen.getByText(expectedUrl)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Скопировать' })).toBeInTheDocument()
    // `navigator.share` is absent in jsdom → progressive enhancement is hidden.
    expect(screen.queryByRole('button', { name: 'Поделиться' })).not.toBeInTheDocument()
  })

  it('администратор видит ссылку и действие «Скопировать»', async () => {
    renderEventPage(ADMIN_ME)

    expect(await screen.findByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Поделиться событием' })).toBeInTheDocument()
    expect(screen.getByText(expectedUrl)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Скопировать' })).toBeInTheDocument()
  })

  it('студент не видит блок «Поделиться»', async () => {
    renderEventPage(STUDENT_ME)

    expect(await screen.findByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Поделиться событием' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Скопировать' })).not.toBeInTheDocument()
  })

  it('гость не видит блок «Поделиться»', async () => {
    renderEventPage(GUEST_ME)

    expect(await screen.findByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Поделиться событием' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Скопировать' })).not.toBeInTheDocument()
  })

  it('«Скопировать» кладёт ссылку в буфер и показывает подтверждение', async () => {
    const user = userEvent.setup()
    const writeText = vi.fn().mockResolvedValue(undefined)
    setNavigatorProperty('clipboard', { writeText })
    renderEventPage(STAFF_ME)

    await user.click(await screen.findByRole('button', { name: 'Скопировать' }))

    expect(writeText).toHaveBeenCalledWith(expectedUrl)
    expect(await screen.findByText('Ссылка скопирована')).toBeInTheDocument()
  })

  it('при отказе Clipboard API используется fallback и показывается подтверждение', async () => {
    const user = userEvent.setup()
    setNavigatorProperty('clipboard', {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    })
    const execCommand = vi.fn().mockReturnValue(true)
    setDocumentProperty('execCommand', execCommand)
    renderEventPage(STAFF_ME)

    await user.click(await screen.findByRole('button', { name: 'Скопировать' }))

    await waitFor(() => {
      expect(execCommand).toHaveBeenCalledWith('copy')
    })
    expect(await screen.findByText('Ссылка скопирована')).toBeInTheDocument()
  })

  it('если и fallback недоступен, показывается сообщение и страница не ломается', async () => {
    const user = userEvent.setup()
    setNavigatorProperty('clipboard', {
      writeText: vi.fn().mockRejectedValue(new Error('denied')),
    })
    setDocumentProperty('execCommand', vi.fn().mockReturnValue(false))
    renderEventPage(STAFF_ME)

    await user.click(await screen.findByRole('button', { name: 'Скопировать' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Не удалось скопировать — выделите ссылку вручную',
    )
    // The link stays visible for manual copying and the page is intact.
    expect(screen.getByText(expectedUrl)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Лабораторные' })).toBeInTheDocument()
  })

  it('«Поделиться» вызывает navigator.share с ожидаемыми url и title', async () => {
    const user = userEvent.setup()
    const share = vi.fn().mockResolvedValue(undefined)
    setNavigatorProperty('share', share)
    renderEventPage(STAFF_ME)

    const button = await screen.findByRole('button', { name: 'Поделиться' })
    await user.click(button)

    expect(share).toHaveBeenCalledTimes(1)
    expect(share).toHaveBeenCalledWith({ title: 'Лабораторные', url: expectedUrl })
  })
})
