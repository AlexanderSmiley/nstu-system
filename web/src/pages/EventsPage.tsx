import { useQuery } from '@tanstack/react-query'
import { Link, NavLink } from 'react-router-dom'
import { fetchEvents } from '../api/events'
import { EVENTS_QUERY_KEY } from '../api/queryKeys'
import { isStaffRole } from '../auth/roles'
import { useSession } from '../auth/session'
import { LoadingScreen } from '../components/LoadingScreen'
import { formatEventDate } from '../utils/date'

function tabClass(active: boolean): string {
  return active ? 'tabs__link tabs__link--active' : 'tabs__link'
}

/** `/events` — single-colour cards with the title and date of visible events. */
export function EventsPage() {
  const query = useQuery({ queryKey: EVENTS_QUERY_KEY, queryFn: fetchEvents })
  const { role } = useSession()
  const canCreate = isStaffRole(role)

  if (query.isPending) {
    return <LoadingScreen />
  }

  if (query.isError) {
    return (
      <section className="page">
        <h1>События</h1>
        <p className="form-error" role="alert">
          Не удалось загрузить события
        </p>
      </section>
    )
  }

  const events = query.data ?? []

  return (
    <section className="page">
      <div className="page__header">
        <h1>События</h1>
        {canCreate && (
          <Link to="/events/new" className="button button--primary">
            Создать событие
          </Link>
        )}
      </div>
      <nav className="tabs" aria-label="Разделы событий">
        <NavLink to="/events" end className={({ isActive }) => tabClass(isActive)}>
          Активные
        </NavLink>
        <NavLink to="/events/history" className={({ isActive }) => tabClass(isActive)}>
          История
        </NavLink>
      </nav>
      {events.length === 0 ? (
        <p className="empty-state">Нет доступных событий</p>
      ) : (
        <ul className="event-grid">
          {events.map((event) => (
            <li key={event.id}>
              <Link to={`/e/${event.slug}`} className="event-card" data-testid="event-card">
                <span className="event-card__title">{event.title}</span>
                <span className="event-card__date">{formatEventDate(event.startsAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
