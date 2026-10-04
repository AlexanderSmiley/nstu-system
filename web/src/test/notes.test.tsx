import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Note, NoteAttachmentInfo, NotesResponse } from '../api/types'
import { formatBytes } from '../utils/notes'
import { GUEST_ME, STUDENT_ME } from './fixtures'
import { installApiMock, jsonResponse, makeMockNote, makeMockNoteAttachment } from './mockApi'
import { renderApp } from './renderApp'

const LIMIT = 100 * 1024 * 1024

function quotaResponse(notes: Note[], attachments: NoteAttachmentInfo[] = []): NotesResponse {
  return {
    notes,
    quota: {
      usedBytes: attachments.reduce((sum, attachment) => sum + attachment.sizeBytes, 0),
      limitBytes: LIMIT,
    },
  }
}

describe('модуль «Заметки»', () => {
  it('гость не видит карточку «Заметки» на главной', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/'])

    const grid = await screen.findByTestId('module-grid')
    const tiles = within(grid).getAllByTestId('module-tile')
    expect(tiles).toHaveLength(2)
    expect(within(grid).queryByText('Заметки')).not.toBeInTheDocument()
  })

  it('гость по прямому адресу /notes получает экран отсутствия прав', async () => {
    installApiMock({ me: GUEST_ME })
    renderApp(['/notes'])

    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument()
  })

  it('показывает список заметок и создаёт новую', async () => {
    const user = userEvent.setup()
    let notes = [makeMockNote({ id: 'n1', title: 'Первая заметка' })]
    installApiMock({
      me: STUDENT_ME,
      notes: () => quotaResponse(notes),
      createNote: (body) => {
        const input = body as { title?: string; body?: string | null }
        const created = makeMockNote({
          id: 'n2',
          title: (input.title ?? '').trim(),
          body: input.body ?? null,
        })
        notes = [created, ...notes]
        return jsonResponse(201, created)
      },
    })
    renderApp(['/notes'])

    expect(await screen.findByText('Первая заметка')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Новая заметка' }))
    await user.type(screen.getByLabelText('Название'), 'Вторая заметка')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByText('Вторая заметка')).toBeInTheDocument()
  })

  it('пустое название показывает invalid_note и сохраняет введённый текст', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      notes: () => ({ notes: [], quota: { usedBytes: 0, limitBytes: LIMIT } }),
      createNote: () =>
        jsonResponse(400, { error: 'invalid_note', message: 'Название заметки не может быть пустым' }),
    })
    renderApp(['/notes'])

    await user.click(await screen.findByRole('button', { name: 'Новая заметка' }))
    const body = screen.getByLabelText('Текст')
    await user.type(body, 'важный текст')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Название заметки не может быть пустым',
    )
    expect(body).toHaveValue('важный текст')
  })

  it('загружает, скачивает и удаляет вложение, обновляя индикатор квоты', async () => {
    const user = userEvent.setup()
    let attachments: NoteAttachmentInfo[] = []
    const note = makeMockNote({ id: 'n1', title: 'С файлами' })
    installApiMock({
      me: STUDENT_ME,
      notes: () => quotaResponse([{ ...note, attachments }], attachments),
      uploadNoteAttachment: (_noteId, formData) => {
        const file = formData?.get('file') as File | null
        const attachment = makeMockNoteAttachment({
          id: 'a1',
          fileName: file?.name ?? 'file.txt',
          sizeBytes: file?.size ?? 0,
        })
        attachments = [...attachments, attachment]
        return jsonResponse(201, attachment)
      },
      deleteNoteAttachment: (_noteId, attachmentId) => {
        attachments = attachments.filter((item) => item.id !== attachmentId)
        return jsonResponse(204, undefined)
      },
    })
    renderApp(['/notes'])

    await user.click(await screen.findByTestId('note-list-item'))
    expect(screen.getByTestId('notes-quota')).toHaveTextContent('0 Б')

    const file = new File(['hello world'], 'report.txt', { type: 'text/plain' })
    await user.upload(screen.getByLabelText('Прикрепить файл'), file)

    expect(await screen.findByText('report.txt')).toBeInTheDocument()
    expect(screen.getByTestId('notes-quota')).toHaveTextContent(formatBytes(file.size))
    expect(screen.getByRole('link', { name: 'Скачать' })).toHaveAttribute(
      'href',
      '/api/notes/n1/attachments/a1',
    )

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const attachmentRow = screen.getByTestId('note-attachment')
    await user.click(within(attachmentRow).getByRole('button', { name: 'Удалить' }))

    expect(await screen.findByText('Вложений нет')).toBeInTheDocument()
    expect(screen.getByTestId('notes-quota')).toHaveTextContent('0 Б')
  })

  it('показывает ошибку attachment_too_large', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      notes: () => quotaResponse([makeMockNote({ id: 'n1', title: 'Заметка' })]),
      uploadNoteAttachment: () =>
        jsonResponse(413, { error: 'attachment_too_large', message: 'Слишком большой файл' }),
    })
    renderApp(['/notes'])

    await user.click(await screen.findByTestId('note-list-item'))
    await user.upload(screen.getByLabelText('Прикрепить файл'), new File(['x'], 'big.bin'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Файл слишком большой')
  })

  it('показывает ошибку note_quota_exceeded', async () => {
    const user = userEvent.setup()
    installApiMock({
      me: STUDENT_ME,
      notes: () =>
        quotaResponse(
          [makeMockNote({ id: 'n1', title: 'Заметка' })],
          [makeMockNoteAttachment({ id: 'a1', fileName: 'old.bin', sizeBytes: LIMIT })],
        ),
      uploadNoteAttachment: () =>
        jsonResponse(409, { error: 'note_quota_exceeded', message: 'Превышена квота' }),
    })
    renderApp(['/notes'])

    await user.click(await screen.findByTestId('note-list-item'))
    await user.upload(screen.getByLabelText('Прикрепить файл'), new File(['x'], 'new.bin'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Превышена квота вложений')
  })
})
