import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { createEvent } from '../api/events'
import { ApiError } from '../api/http'
import type { CreateEventInput, JournalVisibility } from '../api/types'

/** Server defaults mirrored by the form (CreateEventRequest / EventService). */
const DEFAULTS = {
  availability: 'GUEST+',
  entryLimit: '27',
  entryUnit: 'BRIGADE',
  journalVisibility: 'STAFF',
  retentionDays: '14',
} as const

interface FormState {
  title: string
  description: string
  availability: string
  startsAt: string
  entryLimit: string
  entryUnit: string
  journalVisibility: JournalVisibility
  retentionDays: string
}

const INITIAL_FORM: FormState = {
  title: '',
  description: '',
  availability: DEFAULTS.availability,
  startsAt: '',
  entryLimit: DEFAULTS.entryLimit,
  entryUnit: DEFAULTS.entryUnit,
  journalVisibility: DEFAULTS.journalVisibility,
  retentionDays: DEFAULTS.retentionDays,
}

function createErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'invalid_entry_limit':
        return 'Лимит записей должен быть от 1 до 1000'
      case 'event_forbidden':
        return 'Недостаточно прав для создания события'
      default:
        return error.message
    }
  }
  return 'Не удалось создать событие. Попробуйте позже.'
}

/**
 * `/events/new` — staff/admin event creation form (design.md D2), guarded by
 * `RequireRole role="STAFF"`. A server rejection keeps every entered value so
 * the user can correct one field and resubmit.
 */
export function CreateEventPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (input: CreateEventInput) => createEvent(input),
    onSuccess: (created) => {
      navigate(`/e/${created.slug}`)
    },
    onError: (mutationError) => setError(createErrorMessage(mutationError)),
  })

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const title = form.title.trim()
    if (title === '') {
      setError('Введите название события')
      return
    }
    const entryLimit = Number(form.entryLimit)
    if (!Number.isInteger(entryLimit) || entryLimit < 1 || entryLimit > 1000) {
      setError('Лимит записей должен быть от 1 до 1000')
      return
    }
    const retentionDays = Number(form.retentionDays)
    if (!Number.isInteger(retentionDays) || retentionDays < 1) {
      setError('Срок хранения должен быть не менее 1 дня')
      return
    }
    setError(null)
    mutation.mutate({
      title,
      description: form.description.trim() === '' ? null : form.description.trim(),
      availability: form.availability,
      startsAt: form.startsAt === '' ? null : new Date(form.startsAt).toISOString(),
      entryLimit,
      entryUnit: form.entryUnit,
      journalVisibility: form.journalVisibility,
      retentionDays,
    })
  }

  return (
    <section className="page">
      <h1>Создание события</h1>
      <form className="admin-form admin-form--stacked" onSubmit={handleSubmit} noValidate>
        <label className="field" htmlFor="event-title">
          <span className="field__label">Название</span>
          <input
            id="event-title"
            className="field__input"
            value={form.title}
            maxLength={200}
            onChange={(event) => update('title', event.target.value)}
          />
        </label>

        <label className="field" htmlFor="event-description">
          <span className="field__label">Описание</span>
          <textarea
            id="event-description"
            className="field__input"
            value={form.description}
            onChange={(event) => update('description', event.target.value)}
          />
        </label>

        <label className="field" htmlFor="event-availability">
          <span className="field__label">Доступность</span>
          <select
            id="event-availability"
            className="field__input"
            value={form.availability}
            onChange={(event) => update('availability', event.target.value)}
          >
            <option value="GUEST+">Для всех</option>
            <option value="STUDENT+">Для студентов</option>
            <option value="STAFF+">Только персонал</option>
          </select>
        </label>

        <label className="field" htmlFor="event-starts-at">
          <span className="field__label">Начало</span>
          <input
            id="event-starts-at"
            className="field__input"
            type="datetime-local"
            value={form.startsAt}
            onChange={(event) => update('startsAt', event.target.value)}
          />
        </label>

        <label className="field" htmlFor="event-entry-limit">
          <span className="field__label">Лимит записей</span>
          <input
            id="event-entry-limit"
            className="field__input"
            type="number"
            min={1}
            max={1000}
            value={form.entryLimit}
            onChange={(event) => update('entryLimit', event.target.value)}
          />
        </label>

        <label className="field" htmlFor="event-entry-unit">
          <span className="field__label">Единица очереди</span>
          <select
            id="event-entry-unit"
            className="field__input"
            value={form.entryUnit}
            onChange={(event) => update('entryUnit', event.target.value)}
          >
            <option value="BRIGADE">Бригада</option>
            <option value="PERSON">Человек</option>
          </select>
        </label>

        <label className="field" htmlFor="event-journal-visibility">
          <span className="field__label">Видимость журнала</span>
          <select
            id="event-journal-visibility"
            className="field__input"
            value={form.journalVisibility}
            onChange={(event) =>
              update('journalVisibility', event.target.value as JournalVisibility)
            }
          >
            <option value="STAFF">Только персонал</option>
            <option value="EVERYONE">Для всех</option>
          </select>
        </label>

        <label className="field" htmlFor="event-retention-days">
          <span className="field__label">Срок хранения, дней</span>
          <input
            id="event-retention-days"
            className="field__input"
            type="number"
            min={1}
            value={form.retentionDays}
            onChange={(event) => update('retentionDays', event.target.value)}
          />
        </label>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="button button--primary" disabled={mutation.isPending}>
          Создать событие
        </button>
      </form>
    </section>
  )
}
