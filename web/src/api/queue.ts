import { conditionalRequest, request } from './http'
import type { ConditionalResult } from './http'
import type { CarryOverResult, QueueEntry, QueueResponse } from './types'

/** Conditional `GET /api/events/{id}/queue`; supports `ETag`/`If-None-Match`. */
export function fetchQueue(
  eventId: string,
  etag: string | null,
): Promise<ConditionalResult<QueueResponse>> {
  return conditionalRequest<QueueResponse>(
    `/api/events/${encodeURIComponent(eventId)}/queue`,
    etag,
  )
}

/** `POST /api/events/{id}/queue` — join the queue; `name` is optional for accounts. */
export function joinQueue(eventId: string, name?: string): Promise<QueueEntry> {
  return request<QueueEntry>(`/api/events/${encodeURIComponent(eventId)}/queue`, {
    method: 'POST',
    body: JSON.stringify(name === undefined ? {} : { name }),
  })
}

/** `POST /api/events/{id}/queue/advance` — pass the first waiting entry. */
export function advanceQueue(eventId: string): Promise<void> {
  return request<void>(`/api/events/${encodeURIComponent(eventId)}/queue/advance`, {
    method: 'POST',
  })
}

/** `POST /api/events/{id}/queue/{entryId}/pause` — staff/admin only. */
export function pauseEntry(eventId: string, entryId: string): Promise<QueueEntry> {
  return request<QueueEntry>(
    `/api/events/${encodeURIComponent(eventId)}/queue/${encodeURIComponent(entryId)}/pause`,
    { method: 'POST' },
  )
}

/** `POST /api/events/{id}/queue/{entryId}/resume` — staff/admin only. */
export function resumeEntry(eventId: string, entryId: string): Promise<QueueEntry> {
  return request<QueueEntry>(
    `/api/events/${encodeURIComponent(eventId)}/queue/${encodeURIComponent(entryId)}/resume`,
    { method: 'POST' },
  )
}

/** `PATCH /api/events/{id}/queue/{entryId}/position` — staff/admin only. */
export function moveEntry(
  eventId: string,
  entryId: string,
  position: number,
): Promise<QueueEntry> {
  return request<QueueEntry>(
    `/api/events/${encodeURIComponent(eventId)}/queue/${encodeURIComponent(entryId)}/position`,
    { method: 'PATCH', body: JSON.stringify({ position }) },
  )
}

/** `DELETE /api/events/{id}/queue/{entryId}` — staff removes any, participants their own. */
export function removeEntry(eventId: string, entryId: string): Promise<void> {
  return request<void>(
    `/api/events/${encodeURIComponent(eventId)}/queue/${encodeURIComponent(entryId)}`,
    { method: 'DELETE' },
  )
}

/** `POST /api/events/{id}/queue/staff` — staff/admin add an entry on someone's behalf. */
export function addStaffEntry(eventId: string, name: string): Promise<QueueEntry> {
  return request<QueueEntry>(`/api/events/${encodeURIComponent(eventId)}/queue/staff`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

/** `POST /api/events/{id}/carry-over` — move the tail of `sourceEventId` into this event. */
export function carryOver(
  eventId: string,
  sourceEventId: string,
  entryIds?: string[],
): Promise<CarryOverResult> {
  const body: { sourceEventId: string; entryIds?: string[] } = { sourceEventId }
  if (entryIds && entryIds.length > 0) {
    body.entryIds = entryIds
  }
  return request<CarryOverResult>(`/api/events/${encodeURIComponent(eventId)}/carry-over`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
