import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { fetchEventById, fetchEventBySlug } from '../api/events'
import { ApiError } from '../api/http'
import { eventDetailByIdQueryKey, eventDetailQueryKey } from '../api/queryKeys'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { JournalList } from '../components/event/JournalList'
import { LoadingScreen } from '../components/LoadingScreen'
import { eventStatusLabel } from '../utils/event'

function JournalNotFound({ slug }: { slug: string }) {
  return (
    <section className="page">
      <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Журнал' }]} />
      <h1>Журнал сдач</h1>
      <p>{`Событие: ${slug}`}</p>
      <p className="form-error" role="alert">
        Событие не найдено
      </p>
    </section>
  )
}

function JournalForbidden() {
  return (
    <section className="page">
      <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Журнал' }]} />
      <h1>Нет доступа</h1>
      <p className="form-error" role="alert">
        Нет доступа к журналу этого события
      </p>
    </section>
  )
}

/**
 * `/e/:slug/journal` — surrender journal screen (design.md D1, D3). Visibility is
 * decided by the event setting at page level, not by a role guard: staff and
 * admins always receive the journal, students and guests only when the journal is
 * open to everyone. The backend omits the `journal` key when the caller may not
 * see it, so its absence (not the role) drives the access message.
 *
 * The short link itself never carries the journal, so the slug is resolved first
 * and the detail is then loaded by id (`GET /api/events/{id}`). Archived events
 * are `404` on the by-slug lookup and surface the "event not found" screen.
 */
export function EventJournalPage() {
  const { slug } = useParams<{ slug: string }>()

  const detailQuery = useQuery({
    queryKey: eventDetailQueryKey(slug ?? ''),
    queryFn: () => fetchEventBySlug(slug as string),
    enabled: Boolean(slug),
    retry: (failureCount, error) =>
      failureCount < 1 && !(error instanceof ApiError && error.status < 500),
  })

  const event = detailQuery.data

  const journalQuery = useQuery({
    queryKey: eventDetailByIdQueryKey(event?.id ?? ''),
    queryFn: () => fetchEventById(event?.id as string),
    enabled: Boolean(event?.id),
    retry: (failureCount, error) =>
      failureCount < 1 && !(error instanceof ApiError && error.status < 500),
  })

  if (!slug || detailQuery.isPending) {
    return <LoadingScreen />
  }

  if (detailQuery.isError) {
    const error = detailQuery.error
    if (error instanceof ApiError && error.status === 404) {
      return <JournalNotFound slug={slug} />
    }
    if (error instanceof ApiError && error.status === 403) {
      return <JournalForbidden />
    }
    return (
      <section className="page">
        <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Журнал' }]} />
        <h1>Журнал сдач</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить журнал
        </p>
      </section>
    )
  }

  if (!event || journalQuery.isPending) {
    return <LoadingScreen />
  }

  if (journalQuery.isError) {
    const error = journalQuery.error
    if (error instanceof ApiError && error.status === 403) {
      return <JournalForbidden />
    }
    return (
      <section className="page">
        <Breadcrumbs items={[{ label: 'События', to: '/events' }, { label: 'Журнал' }]} />
        <h1>Журнал сдач</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить журнал
        </p>
      </section>
    )
  }

  const status = journalQuery.data?.status ?? event.status
  const journal = journalQuery.data?.journal

  return (
    <section className="page">
      <Breadcrumbs
        items={[
          { label: 'События', to: '/events' },
          { label: event.title, to: `/e/${event.slug}` },
          { label: 'Журнал' },
        ]}
      />

      <header className="event-page__header">
        <h1>Журнал сдач</h1>
        <div className="event-page__badges">
          <span className={`badge badge--${status.toLowerCase()}`}>{eventStatusLabel(status)}</span>
        </div>
      </header>

      <p className="event-page__description">{event.title}</p>

      {journal === undefined ? (
        <p className="notice" role="status">
          Журнал сдач скрыт: он доступен только персоналу события.
        </p>
      ) : (
        <section className="panel" aria-label="Журнал сдач">
          <h2>Прошедшие сдачу</h2>
          <JournalList entries={journal} />
        </section>
      )}
    </section>
  )
}
