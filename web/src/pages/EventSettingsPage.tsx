import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { archiveEvent, closeEvent, fetchEventBySlug, openEvent, updateEvent } from '../api/events'
import { ApiError } from '../api/http'
import { eventDetailQueryKey, eventQueueQueryKey } from '../api/queryKeys'
import type { EventDetail, JournalVisibility, UpdateEventInput } from '../api/types'
import { useSession } from '../auth/session'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { LoadingScreen } from '../components/LoadingScreen'
import { useQueue } from '../hooks/useQueue'
import { eventStatusLabel } from '../utils/event'

/** Maps server error codes on the settings screen to actionable Russian text. */
function settingsErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'entry_unit_locked':
        return 'Единицу записи нельзя изменить, пока в очереди есть записи'
      case 'event_archived':
        return 'Архивное событие нельзя изменить'
      case 'slug_taken':
        return 'Такая ссылка уже используется'
      case 'invalid_slug':
        return 'Ссылка: только строчные латинские буквы, цифры и дефис'
      case 'invalid_entry_limit':
        return 'Лимит записей должен быть от 1 до 1000'
      case 'invalid_retention_days':
        return 'Срок хранения должен быть не менее 1 дня'
      case 'invalid_title':
        return 'Введите название события'
      case 'event_forbidden':
        return 'Недостаточно прав для этого изменения'
      default:
        return error.message
    }
  }
  return 'Не удалось выполнить действие. Попробуйте позже.'
}

/** Converts a server ISO timestamp to the `datetime-local` input value (local time). */
function toLocalDateTimeInput(value: string | null): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

/** Converts a `datetime-local` value back to ISO, or `null` when the field is empty. */
function fromLocalDateTimeInput(value: string): string | null {
  if (value === '') {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

interface SettingsFormState {
  title: string
  description: string
  availability: string
  startsAt: string
  entryLimit: string
  entryUnit: string
  journalVisibility: JournalVisibility
  slug: string
  retentionDays: string
}

function initialFormState(event: EventDetail): SettingsFormState {
  return {
    title: event.title,
    description: event.description ?? '',
    availability: event.availability,
    startsAt: toLocalDateTimeInput(event.startsAt),
    entryLimit: String(event.entryLimit),
    entryUnit: event.entryUnit,
    journalVisibility: event.journalVisibility,
    slug: event.slug,
    retentionDays: String(event.retentionDays),
  }
}

function EventNotFound({ slug }: { slug: string }) {
  return (
    <section className="page">
      <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Настройки' }]} />
      <h1>Настройки события</h1>
      <p>{`Событие: ${slug}`}</p>
      <p className="form-error" role="alert">
        Событие не найдено
      </p>
    </section>
  )
}

function EventForbidden() {
  return (
    <section className="page">
      <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Настройки' }]} />
      <h1>Нет доступа</h1>
      <p className="form-error" role="alert">
        Нет доступа к настройкам этого события
      </p>
    </section>
  )
}

interface EventSettingsFormProps {
  event: EventDetail
  /** Effective status, possibly newer than the loaded detail (from the queue poll). */
  status: string
  /** Slug from the URL, used to invalidate the right detail query after a save. */
  routeSlug: string
  isAdmin: boolean
  /** Active (`WAITING`/`PAUSED`) entries; a non-zero count locks the entry unit. */
  activeEntryCount: number
}

/**
 * `/e/:slug/settings` — staff/admin event settings screen (design.md D1, D5).
 * Staff edit the event fields, administrators additionally see `slug` and
 * `retentionDays`. The entry unit is locked while the queue holds entries (the
 * server answers `409 entry_unit_locked`). Lifecycle actions moved here from the
 * event page. Server errors are shown without discarding entered values.
 */
function EventSettingsForm({
  event,
  status,
  routeSlug,
  isAdmin,
  activeEntryCount,
}: EventSettingsFormProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SettingsFormState>(() => initialFormState(event))
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const entryUnitLocked = activeEntryCount > 0
  const archived = status === 'ARCHIVED'
  const readOnly = archived

  const invalidateEvent = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: eventDetailQueryKey(routeSlug) }),
      queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(event.id) }),
    ])
  }

  const save = useMutation({
    mutationFn: (input: UpdateEventInput) => updateEvent(event.id, input),
    onSuccess: async (updated) => {
      setError(null)
      setNotice('Изменения сохранены')
      setForm((current) => ({ ...current, slug: updated.slug || current.slug }))
      if (updated.slug && updated.slug !== routeSlug) {
        navigate(`/e/${updated.slug}/settings`, { replace: true })
        return
      }
      await invalidateEvent()
    },
    onError: (mutationError) => setError(settingsErrorMessage(mutationError)),
  })

  const changeStatus = useMutation({
    mutationFn: (next: 'open' | 'close') =>
      next === 'open' ? openEvent(event.id) : closeEvent(event.id),
    onSuccess: async () => {
      setError(null)
      setNotice('Статус события обновлён')
      await invalidateEvent()
    },
    onError: (mutationError) => setError(settingsErrorMessage(mutationError)),
  })

  const archive = useMutation({
    mutationFn: () => archiveEvent(event.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: eventDetailQueryKey(routeSlug) })
      navigate('/events/history')
    },
    onError: (mutationError) => setError(settingsErrorMessage(mutationError)),
  })

  const update = <K extends keyof SettingsFormState>(key: K, value: SettingsFormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }

  const handleSubmit = (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault()
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

    const patch: UpdateEventInput = {
      title,
      description: form.description.trim() === '' ? null : form.description.trim(),
      availability: form.availability,
      startsAt: fromLocalDateTimeInput(form.startsAt),
      entryLimit,
      journalVisibility: form.journalVisibility,
    }
    // Never send a locked unit: the server would reject a changed value with 409.
    if (!entryUnitLocked) {
      patch.entryUnit = form.entryUnit
    }
    if (isAdmin) {
      const retentionDays = Number(form.retentionDays)
      if (!Number.isInteger(retentionDays) || retentionDays < 1) {
        setError('Срок хранения должен быть не менее 1 дня')
        return
      }
      patch.retentionDays = retentionDays
      patch.slug = form.slug.trim()
    }

    setError(null)
    setNotice(null)
    save.mutate(patch)
  }

  const handleArchive = () => {
    if (
      window.confirm('Архивировать событие? Очередь и журнал станут недоступны в обычном виде.')
    ) {
      archive.mutate()
    }
  }

  const busy = save.isPending || changeStatus.isPending || archive.isPending

  return (
    <section className="page">
      <Breadcrumbs
        items={[
          { label: 'События', to: '/events' },
          { label: event.title, to: `/e/${event.slug}` },
          { label: 'Настройки' },
        ]}
      />

      <header className="event-page__header">
        <h1>Настройки события</h1>
        <div className="event-page__badges">
          <span className={`badge badge--${status.toLowerCase()}`}>{eventStatusLabel(status)}</span>
        </div>
      </header>

      <form className="admin-form admin-form--stacked" onSubmit={handleSubmit} noValidate>
        <label className="field" htmlFor="settings-title">
          <span className="field__label">Название</span>
          <input
            id="settings-title"
            className="field__input"
            value={form.title}
            maxLength={200}
            onChange={(inputEvent) => update('title', inputEvent.target.value)}
          />
        </label>

        <label className="field" htmlFor="settings-description">
          <span className="field__label">Описание</span>
          <textarea
            id="settings-description"
            className="field__input"
            value={form.description}
            onChange={(inputEvent) => update('description', inputEvent.target.value)}
          />
        </label>

        <label className="field" htmlFor="settings-availability">
          <span className="field__label">Доступность</span>
          <select
            id="settings-availability"
            className="field__input"
            value={form.availability}
            onChange={(inputEvent) => update('availability', inputEvent.target.value)}
          >
            <option value="GUEST+">Для всех</option>
            <option value="STUDENT+">Для студентов</option>
            <option value="STAFF+">Только персонал</option>
          </select>
        </label>

        <label className="field" htmlFor="settings-starts-at">
          <span className="field__label">Начало</span>
          <input
            id="settings-starts-at"
            className="field__input"
            type="datetime-local"
            value={form.startsAt}
            onChange={(inputEvent) => update('startsAt', inputEvent.target.value)}
          />
        </label>

        <label className="field" htmlFor="settings-entry-limit">
          <span className="field__label">Лимит записей</span>
          <input
            id="settings-entry-limit"
            className="field__input"
            type="number"
            min={1}
            max={1000}
            value={form.entryLimit}
            onChange={(inputEvent) => update('entryLimit', inputEvent.target.value)}
          />
        </label>

        <label className="field" htmlFor="settings-entry-unit">
          <span className="field__label">Единица записи</span>
          <select
            id="settings-entry-unit"
            className="field__input"
            value={form.entryUnit}
            disabled={entryUnitLocked || readOnly}
            onChange={(inputEvent) => update('entryUnit', inputEvent.target.value)}
          >
            <option value="BRIGADE">Бригада</option>
            <option value="PERSON">Человек</option>
          </select>
        </label>
        {entryUnitLocked && (
          <p className="field__hint">
            В очереди есть записи — единицу записи изменить нельзя.
          </p>
        )}

        <label className="field" htmlFor="settings-journal-visibility">
          <span className="field__label">Видимость журнала</span>
          <select
            id="settings-journal-visibility"
            className="field__input"
            value={form.journalVisibility}
            onChange={(inputEvent) =>
              update('journalVisibility', inputEvent.target.value as JournalVisibility)
            }
          >
            <option value="STAFF">Только персонал</option>
            <option value="EVERYONE">Для всех</option>
          </select>
        </label>

        {isAdmin && (
          <label className="field" htmlFor="settings-slug">
            <span className="field__label">Короткая ссылка</span>
            <input
              id="settings-slug"
              className="field__input"
              value={form.slug}
              onChange={(inputEvent) => update('slug', inputEvent.target.value)}
            />
          </label>
        )}

        {isAdmin && (
          <label className="field" htmlFor="settings-retention-days">
            <span className="field__label">Срок хранения, дней</span>
            <input
              id="settings-retention-days"
              className="field__input"
              type="number"
              min={1}
              value={form.retentionDays}
              onChange={(inputEvent) => update('retentionDays', inputEvent.target.value)}
            />
          </label>
        )}

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {notice && (
          <p className="notice" role="status">
            {notice}
          </p>
        )}

        <button type="submit" className="button button--primary" disabled={busy || readOnly}>
          Сохранить
        </button>
      </form>

      <section className="panel" aria-label="Состояние события">
        <h2>Состояние</h2>
        <p>
          Текущий статус: <strong>{eventStatusLabel(status)}</strong>
        </p>
        <div className="staff-actions">
          {status === 'OPEN' && (
            <button
              type="button"
              className="button button--secondary"
              onClick={() => changeStatus.mutate('close')}
              disabled={busy}
            >
              Закрыть приём
            </button>
          )}
          {status === 'CLOSED' && (
            <button
              type="button"
              className="button button--secondary"
              onClick={() => changeStatus.mutate('open')}
              disabled={busy}
            >
              Открыть приём
            </button>
          )}
          {status === 'CLOSED' && (
            <button
              type="button"
              className="button button--danger"
              onClick={handleArchive}
              disabled={busy}
            >
              Архивировать
            </button>
          )}
        </div>
      </section>
    </section>
  )
}

/**
 * Loads the event by its slug and renders the role-aware settings form. The
 * route is already guarded by `RequireRole`; this page additionally tolerates
 * `403`/`404` from the API without crashing.
 */
export function EventSettingsPage() {
  const { slug } = useParams<{ slug: string }>()
  const { role } = useSession()
  const isAdmin = role === 'ADMIN'

  const detailQuery = useQuery({
    queryKey: eventDetailQueryKey(slug ?? ''),
    queryFn: () => fetchEventBySlug(slug as string),
    enabled: Boolean(slug),
    retry: (failureCount, error) =>
      failureCount < 1 && !(error instanceof ApiError && error.status < 500),
  })

  const event = detailQuery.data
  const queueQuery = useQueue(event?.id)
  const response = queueQuery.data?.response
  const activeEntryCount = response?.queue.length ?? 0

  if (!slug || detailQuery.isPending) {
    return <LoadingScreen />
  }

  if (detailQuery.isError) {
    const error = detailQuery.error
    if (error instanceof ApiError && error.status === 404) {
      return <EventNotFound slug={slug} />
    }
    if (error instanceof ApiError && error.status === 403) {
      return <EventForbidden />
    }
    return (
      <section className="page">
        <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Настройки' }]} />
        <h1>Настройки события</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить событие
        </p>
      </section>
    )
  }

  if (!event) {
    return <LoadingScreen />
  }

  const status = response?.eventStatus ?? event.status

  return (
    <EventSettingsForm
      key={event.id}
      event={event}
      status={status}
      routeSlug={slug}
      isAdmin={isAdmin}
      activeEntryCount={activeEntryCount}
    />
  )
}
