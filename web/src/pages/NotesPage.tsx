import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createNote,
  deleteNote,
  deleteNoteAttachment,
  fetchNotes,
  noteAttachmentDownloadUrl,
  updateNote,
  uploadNoteAttachment,
} from '../api/notes'
import { ApiError } from '../api/http'
import { NOTES_QUERY_KEY } from '../api/queryKeys'
import type { Note, NoteAttachmentInfo } from '../api/types'
import { LoadingScreen } from '../components/LoadingScreen'
import { formatBytes, formatUpdatedAt } from '../utils/notes'

/** Maps a server error code to a user-facing message (change add-notes-module). */
function noteErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'invalid_note':
        return 'Название заметки не может быть пустым'
      case 'attachment_too_large':
        return 'Файл слишком большой. Максимальный размер — 10 МБ.'
      case 'note_quota_exceeded':
        return 'Превышена квота вложений (10 МБ). Удалите файлы, чтобы загрузить новые.'
      case 'invalid_attachment':
        return 'Недопустимый файл или имя файла'
      case 'note_not_found':
        return 'Заметка не найдена'
      default:
        return error.message
    }
  }
  return 'Не удалось выполнить действие. Попробуйте позже.'
}

/** `/notes` — personal notes: list, editor, attachments and the quota indicator. */
export function NotesPage() {
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const query = useQuery({ queryKey: NOTES_QUERY_KEY, queryFn: fetchNotes })
  const notes = query.data?.notes ?? []
  const quota = query.data?.quota

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)

  const currentNote = notes.find((note) => note.id === selectedId) ?? null

  const invalidate = () => queryClient.invalidateQueries({ queryKey: NOTES_QUERY_KEY })

  const create = useMutation({
    mutationFn: (input: { title: string; body: string }) => createNote(input),
    onSuccess: async (note) => {
      setError(null)
      setStatus('Заметка сохранена')
      setIsNew(false)
      setSelectedId(note.id)
      setTitle(note.title)
      setBody(note.body ?? '')
      await invalidate()
    },
    // The draft is deliberately left untouched so the user can fix the title.
    onError: (mutationError) => setError(noteErrorMessage(mutationError)),
  })

  const update = useMutation({
    mutationFn: (input: { id: string; title: string; body: string }) =>
      updateNote(input.id, { title: input.title, body: input.body }),
    onSuccess: async (note) => {
      setError(null)
      setStatus('Заметка сохранена')
      setTitle(note.title)
      setBody(note.body ?? '')
      await invalidate()
    },
    onError: (mutationError) => setError(noteErrorMessage(mutationError)),
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteNote(id),
    onSuccess: async () => {
      setError(null)
      setStatus(null)
      setIsNew(false)
      setSelectedId(null)
      setTitle('')
      setBody('')
      await invalidate()
    },
    onError: (mutationError) => setError(noteErrorMessage(mutationError)),
  })

  const upload = useMutation({
    mutationFn: (input: { noteId: string; file: File }) =>
      uploadNoteAttachment(input.noteId, input.file),
    onSuccess: async () => {
      setError(null)
      setStatus('Вложение загружено')
      await invalidate()
    },
    onError: (mutationError) => setError(noteErrorMessage(mutationError)),
  })

  const removeAttachment = useMutation({
    mutationFn: (input: { noteId: string; attachmentId: string }) =>
      deleteNoteAttachment(input.noteId, input.attachmentId),
    onSuccess: async () => {
      setError(null)
      setStatus('Вложение удалено')
      await invalidate()
    },
    onError: (mutationError) => setError(noteErrorMessage(mutationError)),
  })

  const openNew = () => {
    setIsNew(true)
    setSelectedId(null)
    setTitle('')
    setBody('')
    setError(null)
    setStatus(null)
  }

  const selectNote = (note: Note) => {
    setIsNew(false)
    setSelectedId(note.id)
    setTitle(note.title)
    setBody(note.body ?? '')
    setError(null)
    setStatus(null)
  }

  const save = () => {
    setError(null)
    setStatus(null)
    if (isNew) {
      create.mutate({ title, body })
    } else if (selectedId) {
      update.mutate({ id: selectedId, title, body })
    }
  }

  const confirmDelete = () => {
    if (selectedId && window.confirm('Удалить заметку вместе с вложениями?')) {
      remove.mutate(selectedId)
    }
  }

  const handleFile = (file: File | null) => {
    if (!file || !selectedId) {
      return
    }
    setError(null)
    setStatus(null)
    upload.mutate({ noteId: selectedId, file })
  }

  const confirmDeleteAttachment = (attachment: NoteAttachmentInfo) => {
    if (selectedId && window.confirm(`Удалить вложение «${attachment.fileName}»?`)) {
      removeAttachment.mutate({ noteId: selectedId, attachmentId: attachment.id })
    }
  }

  const editorOpen = isNew || selectedId !== null
  const pending = create.isPending || update.isPending

  return (
    <section className="page">
      <div className="page__header">
        <h1>Заметки</h1>
        <button type="button" className="button button--primary" onClick={openNew}>
          Новая заметка
        </button>
      </div>

      {query.isPending ? (
        <LoadingScreen />
      ) : query.isError ? (
        <p className="form-error" role="alert">
          Не удалось загрузить заметки
        </p>
      ) : (
        <div className="notes-layout">
          <div className="notes-list" data-testid="notes-list">
            {quota && (
              <p className="notes-quota" data-testid="notes-quota">
                Занято {formatBytes(quota.usedBytes)} из {formatBytes(quota.limitBytes)}
              </p>
            )}
            {notes.length === 0 ? (
              <p className="empty-state">Пока нет заметок</p>
            ) : (
              <ul className="notes-list__items">
                {notes.map((note) => (
                  <li key={note.id}>
                    <button
                      type="button"
                      className={
                        note.id === selectedId
                          ? 'notes-list__item notes-list__item--active'
                          : 'notes-list__item'
                      }
                      data-testid="note-list-item"
                      onClick={() => selectNote(note)}
                    >
                      <span className="notes-list__title">{note.title}</span>
                      <span className="notes-list__meta">
                        {formatUpdatedAt(note.updatedAt)} · вложений: {note.attachments.length}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="notes-editor">
            {!editorOpen ? (
              <p className="empty-state">Выберите заметку или создайте новую</p>
            ) : (
              <>
                <label className="field" htmlFor="note-title">
                  <span className="field__label">Название</span>
                  <input
                    id="note-title"
                    className="field__input"
                    value={title}
                    maxLength={200}
                    onChange={(event) => setTitle(event.target.value)}
                  />
                </label>

                <label className="field" htmlFor="note-body">
                  <span className="field__label">Текст</span>
                  <textarea
                    id="note-body"
                    className="field__input notes-editor__body"
                    value={body}
                    onChange={(event) => setBody(event.target.value)}
                  />
                </label>

                {error && (
                  <p className="form-error" role="alert">
                    {error}
                  </p>
                )}
                {status && !error && <p className="form-success">{status}</p>}

                <div className="admin-form__actions">
                  <button
                    type="button"
                    className="button button--primary"
                    onClick={save}
                    disabled={pending}
                  >
                    Сохранить
                  </button>
                  {selectedId && !isNew && (
                    <button
                      type="button"
                      className="button button--danger"
                      onClick={confirmDelete}
                      disabled={remove.isPending}
                    >
                      Удалить
                    </button>
                  )}
                </div>

                {selectedId && !isNew && (
                  <section className="notes-attachments" aria-label="Вложения">
                    <h2>Вложения</h2>
                    {currentNote && currentNote.attachments.length > 0 ? (
                      <ul className="notes-attachments__list">
                        {currentNote.attachments.map((attachment) => (
                          <li
                            key={attachment.id}
                            className="notes-attachment"
                            data-testid="note-attachment"
                          >
                            <span className="notes-attachment__name">{attachment.fileName}</span>
                            <span className="field__hint">{formatBytes(attachment.sizeBytes)}</span>
                            <a
                              className="link"
                              href={noteAttachmentDownloadUrl(selectedId, attachment.id)}
                              download={attachment.fileName}
                            >
                              Скачать
                            </a>
                            <button
                              type="button"
                              className="button button--danger button--small"
                              onClick={() => confirmDeleteAttachment(attachment)}
                              disabled={removeAttachment.isPending}
                            >
                              Удалить
                            </button>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="empty-state">Вложений нет</p>
                    )}

                    <label className="field" htmlFor="note-attachment-file">
                      <span className="field__label">Прикрепить файл</span>
                      <input
                        id="note-attachment-file"
                        ref={fileInputRef}
                        type="file"
                        onChange={(event) => {
                          const file = event.target.files?.[0] ?? null
                          handleFile(file)
                          event.target.value = ''
                        }}
                      />
                    </label>
                    <p className="field__hint">
                      Не более 10 МБ на файл; общий лимит на пользователя — 10 МБ.
                    </p>
                  </section>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
