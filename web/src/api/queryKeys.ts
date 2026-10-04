/** Centralised React Query keys so invalidation and cache writes stay in sync. */
export const ME_QUERY_KEY = ['me'] as const
export const SITE_QUERY_KEY = ['site'] as const
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
