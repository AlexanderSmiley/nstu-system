import type { CalendarAudience, ModuleId, Preferences, UpdatePreferencesInput } from './types'
import { request } from './http'

/**
 * Client-side defaults, mirroring the server's `DEFAULT_PREFERENCES`
 * (design.md D4): every module on; white "for you", light blue "for group",
 * grey "for staff".
 *
 * They are used before the query resolves and for a guest session, which never
 * fetches preferences (a guest has none). Keeping them here means the shell can
 * render deterministically on the very first paint.
 */
export const DEFAULT_PREFERENCES: Preferences = {
  modules: { events: true, calendar: true, notes: true },
  calendarColors: { ME: '#ffffff', GROUP: '#cfe3ff', STAFF: '#d9dde3' },
}

/** `GET /api/students/me/preferences` — caller's settings (server fills defaults). */
export function fetchPreferences(): Promise<Preferences> {
  return request<Preferences>('/api/students/me/preferences')
}

/**
 * `PATCH /api/students/me/preferences` — partial update of modules and/or
 * colours. A rejected colour is answered with `400 invalid_color`; the caller
 * rolls the optimistic change back.
 */
export function updatePreferences(input: UpdatePreferencesInput): Promise<Preferences> {
  return request<Preferences>('/api/students/me/preferences', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/**
 * Merges a partial preference response/update over a base, filling every known
 * key with its default. Guards against an older/partial server payload so the
 * UI never ends up with an `undefined` module flag.
 */
export function mergePreferences(base: Preferences, patch: UpdatePreferencesInput): Preferences {
  return {
    modules: { ...base.modules, ...(patch.modules ?? {}) },
    calendarColors: { ...base.calendarColors, ...(patch.calendarColors ?? {}) },
  }
}

/** Coerces whichever subset the server returned into a full {@link Preferences}. */
export function normalizePreferences(value: Partial<Preferences> | null | undefined): Preferences {
  return mergePreferences(DEFAULT_PREFERENCES, value ?? {})
}

/** The audience colour used when the preference is missing. */
export function defaultColor(audience: CalendarAudience): string {
  return DEFAULT_PREFERENCES.calendarColors[audience]
}

/** Every module id in registry order, so a full settings form is easy to build. */
export const MODULE_IDS: readonly ModuleId[] = ['events', 'calendar', 'notes']
