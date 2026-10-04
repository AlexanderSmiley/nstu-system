import { request } from './http'
import type { CreateEventInput, Event, EventDetail, UpdateEventInput } from './types'

/** `GET /api/events` — active (`OPEN`) events visible to the caller's role. */
export function fetchEvents(): Promise<Event[]> {
  return request<Event[]>('/api/events')
}

/**
 * `GET /api/events/history` — past events: only `CLOSED` for staff and above,
 * `CLOSED` plus `ARCHIVED` for admins, empty for students and guests.
 */
export function fetchEventHistory(): Promise<Event[]> {
  return request<Event[]>('/api/events/history')
}

/** `GET /api/events/{id}` — full event with the journal when visible. */
export function fetchEventById(id: string): Promise<EventDetail> {
  return request<EventDetail>(`/api/events/${encodeURIComponent(id)}`)
}

/** `GET /api/events/by-slug/{slug}` — public short link resolution (design.md D25). */
export function fetchEventBySlug(slug: string): Promise<EventDetail> {
  return request<EventDetail>(`/api/events/by-slug/${encodeURIComponent(slug)}`)
}

/** `POST /api/events` — staff/admin only; responds `201` with the created event. */
export function createEvent(input: CreateEventInput): Promise<Event> {
  return request<Event>('/api/events', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** `PATCH /api/events/{id}` — staff (own fields) / admin (`slug`, `retentionDays`). */
export function updateEvent(id: string, input: UpdateEventInput): Promise<Event> {
  return request<Event>(`/api/events/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** `POST /api/events/{id}/close` — staff/admin only. */
export function closeEvent(id: string): Promise<Event> {
  return request<Event>(`/api/events/${encodeURIComponent(id)}/close`, { method: 'POST' })
}

/** `POST /api/events/{id}/open` — staff/admin only. */
export function openEvent(id: string): Promise<Event> {
  return request<Event>(`/api/events/${encodeURIComponent(id)}/open`, { method: 'POST' })
}

/** `POST /api/events/{id}/archive` — staff/admin only, `CLOSED` events only. */
export function archiveEvent(id: string): Promise<Event> {
  return request<Event>(`/api/events/${encodeURIComponent(id)}/archive`, { method: 'POST' })
}

/** `POST /api/events/{id}/restore` — admin only. */
export function restoreEvent(id: string): Promise<Event> {
  return request<Event>(`/api/events/${encodeURIComponent(id)}/restore`, { method: 'POST' })
}

/** `DELETE /api/events/{id}` — admin only, irreversibly removes an archived event. */
export function deleteEvent(id: string): Promise<void> {
  return request<void>(`/api/events/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
