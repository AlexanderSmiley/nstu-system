import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { deleteEvent, fetchEventHistory, restoreEvent } from '../api/events'
import { ApiError } from '../api/http'
import { EVENT_HISTORY_QUERY_KEY } from '../api/queryKeys'
import { useSession } from '../auth/session'
import { LoadingScreen } from '../components/LoadingScreen'
import { formatEventDate } from '../utils/date'
import { eventStatusLabel } from '../utils/event'

/**
 * `/events/history` — past events. The server already filters by role: staff see
 * `CLOSED`, admins see `CLOSED` and `ARCHIVED` (event-management spec). Archived
 * rows expose admin-only restore/delete actions.
 */
export function EventHistoryPage() {
  const { role } = useSession()
  const queryClient = useQueryClient()
  const isAdmin = role === 'ADMIN'

  const query = useQuery({ queryKey: EVENT_HISTORY_QUERY_KEY, queryFn: fetchEventHistory })

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: EVENT_HISTORY_QUERY_KEY })
  }

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
          {events.map((event) => (
            <li key={event.id} className="history-card" data-testid="history-card">
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
              {isAdmin && event.status === 'ARCHIVED' && (
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
          ))}
        </ul>
      )}
    </section>
  )
}
