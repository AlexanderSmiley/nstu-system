import { request } from './http'
import type {
  CalendarEntry,
  CreateCalendarEntryInput,
  UpdateCalendarEntryInput,
} from './types'

/** `GET /api/calendar?from=&to=` — visible entries of the given window. */
export function fetchCalendar(from: string, to: string): Promise<CalendarEntry[]> {
  const query = new URLSearchParams({ from, to }).toString()
  return request<CalendarEntry[]>(`/api/calendar?${query}`)
}

/**
 * `POST /api/calendar` — `STUDENT` and above; responds `201` with the created
 * entry. `from`/`to` carry the displayed window so the server can reject a date
 * outside of it with `invalid_date`.
 */
export function createCalendarEntry(input: CreateCalendarEntryInput): Promise<CalendarEntry> {
  return request<CalendarEntry>('/api/calendar', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/**
 * `PATCH /api/calendar/{id}` — author or administrator; responds `200` with the
 * updated entry. `from`/`to` carry the displayed window so the server can reject
 * a date outside of it with `invalid_date`.
 */
export function updateCalendarEntry(
  id: string,
  input: UpdateCalendarEntryInput,
): Promise<CalendarEntry> {
  return request<CalendarEntry>(`/api/calendar/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** `DELETE /api/calendar/{id}` — author or administrator; responds `204`. */
export function deleteCalendarEntry(id: string): Promise<void> {
  return request<void>(`/api/calendar/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
