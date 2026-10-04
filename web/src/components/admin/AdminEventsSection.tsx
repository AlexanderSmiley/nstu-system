import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { deleteEvent, fetchEventHistory, fetchEvents, restoreEvent } from '../../api/events'
import { ApiError } from '../../api/http'
import { EVENT_HISTORY_QUERY_KEY, EVENTS_QUERY_KEY } from '../../api/queryKeys'
import type { Event } from '../../api/types'
import { formatEventDate } from '../../utils/date'
import { eventStatusLabel } from '../../utils/event'

function mergeEvents(...groups: readonly Event[][]): Event[] {
  const byId = new Map<string, Event>()
  for (const group of groups) {
    for (const event of group) {
      byId.set(event.id, event)
    }
  }
  return [...byId.values()]
}

/** Admin section «События»: every event, including `CLOSED` and `ARCHIVED`. */
export function AdminEventsSection() {
  const queryClient = useQueryClient()

  const activeQuery = useQuery({ queryKey: EVENTS_QUERY_KEY, queryFn: fetchEvents })
  const historyQuery = useQuery({ queryKey: EVENT_HISTORY_QUERY_KEY, queryFn: fetchEventHistory })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: EVENTS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: EVENT_HISTORY_QUERY_KEY }),
    ])
  }

  const restore = useMutation({ mutationFn: restoreEvent, onSuccess: invalidate })
  const remove = useMutation({ mutationFn: deleteEvent, onSuccess: invalidate })

  const isPending = activeQuery.isPending || historyQuery.isPending
  const events = mergeEvents(activeQuery.data ?? [], historyQuery.data ?? [])

  return (
    <section className="admin-section" aria-label="События">
      <h2>События</h2>
      {restore.isError && (
        <p className="form-error" role="alert">
          {restore.error instanceof ApiError ? restore.error.message : 'Не удалось восстановить'}
        </p>
      )}
      {remove.isError && (
        <p className="form-error" role="alert">
          {remove.error instanceof ApiError ? remove.error.message : 'Не удалось удалить событие'}
        </p>
      )}
      {isPending ? (
        <p className="placeholder-note">Загрузка событий…</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Название</th>
              <th>Статус</th>
              <th>Дата</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {events.map((event) => (
              <tr key={event.id} data-testid="admin-event-row">
                <td>{event.title}</td>
                <td>
                  <span className={`badge badge--${event.status.toLowerCase()}`}>
                    {eventStatusLabel(event.status)}
                  </span>
                </td>
                <td>{formatEventDate(event.startsAt)}</td>
                <td className="data-table__actions">
                  {event.status !== 'ARCHIVED' && (
                    <Link className="button button--secondary button--small" to={`/e/${event.slug}`}>
                      Открыть
                    </Link>
                  )}
                  {event.status === 'ARCHIVED' && (
                    <>
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
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
