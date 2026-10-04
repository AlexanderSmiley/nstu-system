import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createCalendarEntry, updateCalendarEntry } from '../../api/calendar'
import { ApiError } from '../../api/http'
import { CALENDAR_QUERY_KEY } from '../../api/queryKeys'
import type {
  CalendarAudience,
  CalendarEntry,
  CreateCalendarEntryInput,
  UpdateCalendarEntryInput,
} from '../../api/types'

interface CalendarEntryFormProps {
  /** `edit` seeds the fields from `entry` and `PATCH`es instead of `POST`ing. */
  mode: 'create' | 'edit'
  entry?: CalendarEntry
  from: string
  to: string
  days: string[]
  canChooseStaff: boolean
  onClose: () => void
}

/** Maps a stable server error code to a localised, actionable message. */
function calendarErrorMessage(error: unknown, mode: 'create' | 'edit'): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'invalid_date':
        return 'Дата должна находиться в отображаемом окне календаря'
      case 'invalid_audience':
        return 'Этот адресат недоступен для вашей роли'
      case 'invalid_title':
        return 'Введите название мероприятия'
      case 'invalid_description':
        return 'Описание слишком длинное (до 150 символов)'
      case 'calendar_forbidden':
        return 'Недостаточно прав для изменения мероприятия'
      case 'calendar_not_found':
        return 'Мероприятие не найдено'
      case 'forbidden':
        return mode === 'edit'
          ? 'Недостаточно прав для изменения мероприятия'
          : 'Недостаточно прав для создания мероприятия'
      default:
        return error.message
    }
  }
  return mode === 'edit'
    ? 'Не удалось сохранить изменения. Попробуйте позже.'
    : 'Не удалось создать мероприятие. Попробуйте позже.'
}

/**
 * The shared create/edit form (design.md D7: editing reuses the creation form).
 * A rejected submission keeps every entered value so a single field can be
 * corrected; on success the calendar queries are invalidated.
 */
export function CalendarEntryForm({
  mode,
  entry,
  from,
  to,
  days,
  canChooseStaff,
  onClose,
}: CalendarEntryFormProps) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(entry?.title ?? '')
  const [startsOn, setStartsOn] = useState(entry?.startsOn ?? days[0] ?? from)
  const [startsAt, setStartsAt] = useState(entry?.startsAt ? entry.startsAt.slice(0, 5) : '')
  const [description, setDescription] = useState(entry?.description ?? '')
  const [audience, setAudience] = useState<CalendarAudience>(entry?.audience ?? 'ME')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (payload: CreateCalendarEntryInput & UpdateCalendarEntryInput) =>
      mode === 'edit' && entry
        ? updateCalendarEntry(entry.id, payload)
        : createCalendarEntry(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CALENDAR_QUERY_KEY })
      onClose()
    },
    onError: (mutationError) => setError(calendarErrorMessage(mutationError, mode)),
  })

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmedTitle = title.trim()
    if (trimmedTitle === '') {
      setError('Введите название мероприятия')
      return
    }
    if (startsOn === '') {
      setError('Выберите дату')
      return
    }
    setError(null)
    mutation.mutate({
      title: trimmedTitle,
      description: description.trim() === '' ? null : description.trim(),
      startsOn,
      startsAt: startsAt === '' ? null : startsAt,
      audience,
      from,
      to,
    })
  }

  const titleId = mode === 'edit' ? 'calendar-edit-title' : 'calendar-title'
  const dateId = mode === 'edit' ? 'calendar-edit-date' : 'calendar-date'
  const timeId = mode === 'edit' ? 'calendar-edit-time' : 'calendar-time'
  const descriptionId = mode === 'edit' ? 'calendar-edit-description' : 'calendar-description'
  const audienceId = mode === 'edit' ? 'calendar-edit-audience' : 'calendar-audience'

  return (
    <form className="admin-form admin-form--stacked calendar-form" onSubmit={handleSubmit} noValidate>
      <label className="field" htmlFor={titleId}>
        <span className="field__label">Название</span>
        <input
          id={titleId}
          className="field__input"
          value={title}
          maxLength={200}
          onChange={(event) => setTitle(event.target.value)}
        />
      </label>

      <label className="field" htmlFor={dateId}>
        <span className="field__label">Дата</span>
        <input
          id={dateId}
          className="field__input"
          type="date"
          min={from}
          max={to}
          value={startsOn}
          onChange={(event) => setStartsOn(event.target.value)}
        />
      </label>

      <label className="field" htmlFor={timeId}>
        <span className="field__label">Время</span>
        <input
          id={timeId}
          className="field__input"
          type="time"
          value={startsAt}
          onChange={(event) => setStartsAt(event.target.value)}
        />
      </label>

      {/* The hint sits outside the <label> so it does not become part of the
          field's accessible name. */}
      <div className="field">
        <label className="field__label" htmlFor={descriptionId}>
          Описание
        </label>
        <textarea
          id={descriptionId}
          className="field__input"
          value={description}
          maxLength={150}
          onChange={(event) => setDescription(event.target.value)}
        />
        <span className="field__hint">до 150 символов — {description.length}/150</span>
      </div>

      <label className="field" htmlFor={audienceId}>
        <span className="field__label">Адресат</span>
        <select
          id={audienceId}
          className="field__input"
          value={audience}
          onChange={(event) => setAudience(event.target.value as CalendarAudience)}
        >
          <option value="ME">Для вас</option>
          <option value="GROUP">Для группы</option>
          {canChooseStaff && <option value="STAFF">Для персонала</option>}
        </select>
      </label>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <div className="admin-form__actions">
        <button type="submit" className="button button--primary" disabled={mutation.isPending}>
          Сохранить
        </button>
        <button type="button" className="button button--secondary" onClick={onClose}>
          Отмена
        </button>
      </div>
    </form>
  )
}
