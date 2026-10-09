import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { deleteEvent, fetchEventHistory, openEvent, restoreEvent } from '../api/events'
import { ApiError } from '../api/http'
import { EVENT_HISTORY_QUERY_KEY, EVENTS_QUERY_KEY } from '../api/queryKeys'
import { useSession } from '../auth/session'
import { LoadingScreen } from '../components/LoadingScreen'
import { formatEventDate } from '../utils/date'
import { eventStatusLabel } from '../utils/event'

/**
 * `/events/history` — past events. The server already filters by role: staff see
 * `CLOSED`, admins see `CLOSED` and `ARCHIVED` (event-management spec). Closed
 * cards link to the event and offer "open the queue again"; archived rows expose
 * admin-only restore/delete actions and are not clickable.
 */
export function EventHistoryPage() {
  const { role } = useSession()
  const queryClient = useQueryClient()
  const isAdmin = role === 'ADMIN'

  const query = useQuery({ queryKey: EVENT_HISTORY_QUERY_KEY, queryFn: fetchEventHistory })

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: EVENT_HISTORY_QUERY_KEY })
  }

  // Reopening moves the event from history back to the active list, so both
  // queries must be invalidated (change add-event-share-and-history-actions D3).
  const reopen = useMutation({
    mutationFn: openEvent,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: EVENT_HISTORY_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: EVENTS_QUERY_KEY }),
      ])
    },
  })
  const restore = useMutation({ mutationFn: restoreEvent, onSuccess: invalidate })
  const remove = useMutation({ mutationFn: deleteEvent, onSuccess: invalidate })

  if (query.isPending) {
    return <LoadingScreen />
  }

  if (query.isError) {
    return (
      <section className="page">
        <h1>История событий</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить историю
        </p>
      </section>
    )
  }

  const events = query.data ?? []

  return (
    <section className="page">
      <h1>История событий</h1>
      <p>
        <Link className="link" to="/events">
          К активным событиям
        </Link>
      </p>
      {reopen.isError && (
        <p className="form-error" role="alert">
          {reopen.error instanceof ApiError ? reopen.error.message : 'Не удалось открыть приём'}
        </p>
      )}
      {restore.isError && (
        <p className="form-error" role="alert">
          {restore.error instanceof ApiError ? restore.error.message : 'Не удалось восстановить'}
        </p>
      )}
      {remove.isError && (
        <p className="form-error" role="alert">
          {remove.error instanceof ApiError
            ? remove.error.message
            : 'Не удалось удалить событие'}
        </p>
      )}
      {events.length === 0 ? (
        <p className="empty-state">В истории пока ничего нет</p>
      ) : (
        <ul className="history-grid">
          {events.map((event) => {
            const archived = event.status === 'ARCHIVED'
            const summary = (
              <>
                <span className="event-card__title">{event.title}</span>
                <span className="event-card__date">{formatEventDate(event.startsAt)}</span>
                <span className={`badge badge--${event.status.toLowerCase()}`}>
                  {eventStatusLabel(event.status)}
                </span>
                {event.closedAt && (
                  <span className="history-card__closed">
                    Закрыто: {formatEventDate(event.closedAt)}
                  </span>
                )}
              </>
            )
            return (
              <li key={event.id} className="history-card" data-testid="history-card">
                {archived ? (
                  summary
                ) : (
                  <Link className="history-card__link" to={`/e/${event.slug}`}>
                    {summary}
                  </Link>
                )}
                {!archived && event.status === 'CLOSED' && (
                  <span className="history-card__actions">
                    <button
                      type="button"
                      className="button button--secondary button--small"
                      onClick={() => reopen.mutate(event.id)}
                      disabled={reopen.isPending}
                    >
                      Открыть приём
                    </button>
                  </span>
                )}
                {isAdmin && archived && (
                  <span className="history-card__actions">
                    <button
                      type="button"
                      className="button button--secondary button--small"
                      onClick={() => restore.mutate(event.id)}
                      disabled={restore.isPending || remove.isPending}
                    >
                      Восстановить
                    </button>
                    <button
                      type="button"
                      className="button button--danger button--small"
                      onClick={() => {
                        if (
                          window.confirm(
                            'Удалить событие безвозвратно? Это действие необратимо.',
                          )
                        ) {
                          remove.mutate(event.id)
                        }
                      }}
                      disabled={restore.isPending || remove.isPending}
                    >
                      Удалить безвозвратно
                    </button>
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
