import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { fetchEventBySlug } from '../api/events'
import { ApiError } from '../api/http'
import { eventDetailQueryKey } from '../api/queryKeys'
import { isStaffRole } from '../auth/roles'
import { useSession } from '../auth/session'
import { JoinQueueForm } from '../components/event/JoinQueueForm'
import { QueueList } from '../components/event/QueueList'
import { StaffPanel } from '../components/event/StaffPanel'
import { LoadingScreen } from '../components/LoadingScreen'
import { useQueue } from '../hooks/useQueue'
import { formatEventDate } from '../utils/date'
import { availabilityLabel, entryNameLabel, eventStatusLabel } from '../utils/event'

function EventNotFound({ slug }: { slug: string }) {
  return (
    <section className="page">
      <h1>Страница события</h1>
      <p>{`Событие: ${slug}`}</p>
      <p className="form-error" role="alert">
        Событие не найдено
      </p>
      <Link className="link" to="/events">
        К списку событий
      </Link>
    </section>
  )
}

function EventForbidden() {
  return (
    <section className="page">
      <h1>Нет доступа</h1>
      <p className="form-error" role="alert">
        Нет доступа к этому событию
      </p>
      <Link className="link" to="/events">
        К списку событий
      </Link>
    </section>
  )
}

/**
 * `/e/:slug` — the event page (design.md D3). The queue is the primary content:
 * the header with the event summary is followed by the live queue and the join
 * form; staff get a compact control panel next to it. The full surrender journal
 * is not duplicated here — it lives on `/e/:slug/journal`. Lifecycle actions
 * (close/open/archive) moved to `/e/:slug/settings`. A `CLOSED` event is
 * read-only.
 */
export function EventPage() {
  const { slug } = useParams<{ slug: string }>()
  const { me, role } = useSession()
  // Suspends the 5-second queue polling while a drag/mutation is in flight.
  const [queuePollingPaused, setQueuePollingPaused] = useState(false)

  const detailQuery = useQuery({
    queryKey: eventDetailQueryKey(slug ?? ''),
    queryFn: () => fetchEventBySlug(slug as string),
    enabled: Boolean(slug),
    retry: (failureCount, error) =>
      failureCount < 1 && !(error instanceof ApiError && error.status < 500),
  })

  const event = detailQuery.data
  const queueQuery = useQueue(event?.id, { paused: queuePollingPaused })

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
        <h1>Страница события</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить событие
        </p>
        <Link className="link" to="/events">
          К списку событий
        </Link>
      </section>
    )
  }

  if (!event) {
    return <LoadingScreen />
  }

  const response = queueQuery.data?.response
  const status: string = response?.eventStatus ?? event.status
  const readOnly = status !== 'OPEN'
  const isStaff = isStaffRole(role)
  const queue = response?.queue ?? []
  const journalVisible = response?.journal !== undefined

  return (
    <section className="page event-page">
      <Link className="link" to="/events">
        К списку событий
      </Link>

      <header className="event-page__header">
        <h1>{event.title}</h1>
        <div className="event-page__badges">
          <span className={`badge badge--${status.toLowerCase()}`}>
            {eventStatusLabel(status)}
          </span>
          {status === 'CLOSED' && <span className="badge badge--closed">приём закрыт</span>}
          {status === 'ARCHIVED' && <span className="badge badge--archived">в архиве</span>}
        </div>
      </header>

      {event.description && <p className="event-page__description">{event.description}</p>}

      <dl className="details">
        <div className="details__row">
          <dt>Доступность</dt>
          <dd>{availabilityLabel(event.availability)}</dd>
        </div>
        <div className="details__row">
          <dt>Начало</dt>
          <dd>{formatEventDate(event.startsAt)}</dd>
        </div>
        <div className="details__row">
          <dt>Единица записи</dt>
          <dd>{entryNameLabel(event.entryUnit)}</dd>
        </div>
        <div className="details__row">
          <dt>Лимит записей</dt>
          <dd>{event.entryLimit}</dd>
        </div>
      </dl>

      {readOnly && (
        <p className="notice">Приём закрыт — очередь доступна только для просмотра.</p>
      )}

      {isStaff && <StaffPanel eventId={event.id} slug={event.slug} status={status} />}

      <section className="panel" aria-label="Очередь">
        <h2>Очередь</h2>
        {queueQuery.isPending ? (
          <p className="placeholder-note">Загрузка очереди…</p>
        ) : (
          <QueueList
            eventId={event.id}
            entries={queue}
            subject={me?.subject}
            isStaff={isStaff}
            readOnly={readOnly}
            onActivityChange={setQueuePollingPaused}
          />
        )}

        <div className="join-block">
          <h3>Вступление в очередь</h3>
          {readOnly ? (
            <p className="placeholder-note">Приём закрыт</p>
          ) : (
            <JoinQueueForm
              eventId={event.id}
              entryUnit={event.entryUnit}
              isGuest={me?.guest === true}
            />
          )}
        </div>
      </section>

      {!isStaff && journalVisible && (
        <p>
          <Link className="link" to={`/e/${event.slug}/journal`}>
            Журнал сдач
          </Link>
        </p>
      )}
    </section>
  )
}
