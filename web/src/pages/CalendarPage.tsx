import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchCalendar } from '../api/calendar'
import { calendarQueryKey } from '../api/queryKeys'
import type { CalendarAudience, CalendarEntry } from '../api/types'
import { useSession } from '../auth/session'
import { CalendarEntryCard } from '../components/calendar/CalendarEntryCard'
import { CalendarEntryForm } from '../components/calendar/CalendarEntryForm'
import { LoadingScreen } from '../components/LoadingScreen'
import { usePreferences } from '../hooks/usePreferences'
import {
  buildCalendarWindow,
  entryFillStyle,
  formatDayLabel,
  formatTime,
  groupEntriesByDay,
  weekdayLabel,
  WEEKDAY_LABELS,
} from '../utils/calendar'

interface EntryItemProps {
  entry: CalendarEntry
  colors: Record<CalendarAudience, string>
  onOpen: (entry: CalendarEntry) => void
}

/**
 * One entry, rendered identically in the grid and in the mobile list. It carries
 * no delete control (calendar spec): actions live in the card. Clicking opens
 * the card; the soft fill is derived from the viewer's audience colours.
 */
function CalendarEntryItem({ entry, colors, onOpen }: EntryItemProps) {
  const time = formatTime(entry.startsAt)

  return (
    <li className="calendar-entry-item">
      <button
        type="button"
        className="calendar-entry"
        data-testid="calendar-entry"
        data-audience={entry.audience}
        style={entryFillStyle(entry.audience, colors)}
        onClick={() => onOpen(entry)}
      >
        {time && <span className="calendar-entry__time">{time}</span>}
        <span className="calendar-entry__title" data-testid="calendar-entry-title">
          {entry.title}
        </span>
      </button>
    </li>
  )
}

/** `/calendar` — two-week calendar: grid on desktop, list on a phone (CSS only). */
export function CalendarPage() {
  const { role } = useSession()
  const { preferences } = usePreferences()
  const canCreate = role === 'STUDENT' || role === 'STAFF' || role === 'ADMIN'
  const canChooseStaff = role === 'STAFF' || role === 'ADMIN'
  const isAdmin = role === 'ADMIN'

  const [weekOffset, setWeekOffset] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [selected, setSelected] = useState<CalendarEntry | null>(null)

  const today = useMemo(() => new Date(), [])
  const window = useMemo(() => buildCalendarWindow(today, weekOffset), [today, weekOffset])

  const query = useQuery({
    queryKey: calendarQueryKey(window.from, window.to),
    queryFn: () => fetchCalendar(window.from, window.to),
  })
  const days = useMemo(
    () => groupEntriesByDay(window.days, query.data ?? []),
    [window.days, query.data],
  )

  return (
    <section className="page">
      <div className="page__header">
        <h1>Календарь</h1>
        {canCreate && (
          <button
            type="button"
            className="button button--primary"
            onClick={() => setFormOpen((open) => !open)}
          >
            Создать мероприятие
          </button>
        )}
      </div>

      <nav className="calendar-nav" aria-label="Навигация календаря">
        <button
          type="button"
          className="button button--secondary"
          onClick={() => setWeekOffset((offset) => offset - 1)}
        >
          ← Две недели назад
        </button>
        <button
          type="button"
          className="button button--secondary"
          onClick={() => setWeekOffset(0)}
        >
          Текущая неделя
        </button>
        <button
          type="button"
          className="button button--secondary"
          onClick={() => setWeekOffset((offset) => offset + 1)}
        >
          Две недели вперёд →
        </button>
      </nav>

      <p className="calendar-range" data-testid="calendar-range">
        {window.from} — {window.to}
      </p>

      {formOpen && canCreate && (
        <CalendarEntryForm
          mode="create"
          from={window.from}
          to={window.to}
          days={window.days}
          canChooseStaff={canChooseStaff}
          onClose={() => setFormOpen(false)}
        />
      )}

      {query.isPending ? (
        <LoadingScreen />
      ) : query.isError ? (
        <p className="form-error" role="alert">
          Не удалось загрузить календарь
        </p>
      ) : (
        <>
          <div className="calendar-grid" data-testid="calendar-grid">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label} className="calendar-grid__weekday">
                {label}
              </div>
            ))}
            {days.map((day) => (
              <div key={day.iso} className="calendar-day" data-testid="calendar-day">
                <div className="calendar-day__header">
                  <span className="calendar-day__weekday">{weekdayLabel(day.iso)}</span>
                  <span className="calendar-day__date">{formatDayLabel(day.iso)}</span>
                </div>
                <ul className="calendar-day__entries">
                  {day.entries.map((entry) => (
                    <CalendarEntryItem
                      key={entry.id}
                      entry={entry}
                      colors={preferences.calendarColors}
                      onOpen={setSelected}
                    />
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <ol className="calendar-list" data-testid="calendar-list">
            {days.map((day) => (
              <li key={day.iso} className="calendar-list__day" data-testid="calendar-list-day">
                <div className="calendar-list__header">
                  <span className="calendar-list__weekday">{weekdayLabel(day.iso)}</span>
                  <span className="calendar-list__date">{formatDayLabel(day.iso)}</span>
                </div>
                <ul className="calendar-list__entries">
                  {day.entries.map((entry) => (
                    <CalendarEntryItem
                      key={entry.id}
                      entry={entry}
                      colors={preferences.calendarColors}
                      onOpen={setSelected}
                    />
                  ))}
                </ul>
              </li>
            ))}
          </ol>
        </>
      )}

      {selected && (
        <CalendarEntryCard
          entry={selected}
          canManage={selected.mine || isAdmin}
          from={window.from}
          to={window.to}
          days={window.days}
          canChooseStaff={canChooseStaff}
          onClose={() => setSelected(null)}
        />
      )}
    </section>
  )
}
