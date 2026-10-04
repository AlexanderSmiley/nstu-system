/** Centralised React Query keys so invalidation and cache writes stay in sync. */
export const ME_QUERY_KEY = ['me'] as const
export const SITE_QUERY_KEY = ['site'] as const
export const SITE_ICON_QUERY_KEY = ['site', 'icon'] as const
export const EVENTS_QUERY_KEY = ['events'] as const
export const EVENT_HISTORY_QUERY_KEY = ['events', 'history'] as const
export const STUDENT_PROFILE_QUERY_KEY = ['student-profile'] as const
export const LOGIN_PROFILE_QUERY_KEY = ['login-profile'] as const
export const USERS_QUERY_KEY = ['users'] as const

/** Query key of a single event resolved by slug (`/e/:slug`). */
export function eventDetailQueryKey(slug: string) {
  return ['event', 'slug', slug] as const
}

/** Query key of the event detail with the journal (`GET /api/events/{id}`). */
export function eventDetailByIdQueryKey(id: string) {
  return ['event', 'id', id] as const
}

/** Query key of the polled queue/journal of an event (design.md D20). */
export function eventQueueQueryKey(eventId: string) {
  return ['event', eventId, 'queue'] as const
}

/** Prefix of every calendar window query; invalidating it refreshes all windows. */
export const CALENDAR_QUERY_KEY = ['calendar'] as const

/** Query key of one displayed two-week calendar window. */
export function calendarQueryKey(from: string, to: string) {
  return ['calendar', from, to] as const
}

/** Query key of the caller's notes and quota (change add-notes-module). */
export const NOTES_QUERY_KEY = ['notes'] as const

/** Query key of the caller's per-account UI preferences (change add-preferences-and-calendar-ui). */
export const PREFERENCES_QUERY_KEY = ['preferences'] as const
