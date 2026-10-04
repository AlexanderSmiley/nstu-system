import type { CSSProperties } from 'react'
import type { CalendarAudience, CalendarEntry } from '../api/types'

/** Monday-based weekday headers, matching the grid's column order. */
export const WEEKDAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'] as const

/** Human labels of the three calendar audiences, used by cards and selects. */
export const AUDIENCE_LABELS: Record<CalendarAudience, string> = {
  ME: 'Для вас',
  GROUP: 'Для группы',
  STAFF: 'Для персонала',
}

/** Localised label of an audience. */
export function audienceLabel(audience: CalendarAudience): string {
  return AUDIENCE_LABELS[audience]
}

/**
 * Fixed alpha of the tinted entry fill. Every audience/user colour is applied
 * with this same opacity over the surface, so a saturated choice cannot look
 * heavier than the defaults — only the hue changes (change UI group 5).
 *
 * It is exposed as an inline custom property as well, because jsdom does not
 * evaluate `color-mix`; tests can therefore assert that the alpha is identical
 * for every colour.
 */
export const ENTRY_FILL_ALPHA = '22%'

/**
 * Inline style of an entry tile. The raw audience colour and the shared alpha
 * are exposed as custom properties; the stylesheet turns them into a soft
 * `color-mix(in srgb, <color> var(--entry-alpha), transparent)` fill with an
 * opaque fallback (design.md D4, change UI group 5), so a saturated user colour
 * never "burns" and stays readable in both themes.
 */
export function entryFillStyle(
  audience: CalendarAudience,
  colors: Record<CalendarAudience, string>,
): CSSProperties {
  return {
    '--entry-color': colors[audience],
    '--entry-alpha': ENTRY_FILL_ALPHA,
  } as CSSProperties
}

/** A two-week window: inclusive ISO bounds plus the 14 day keys. */
export interface CalendarWindow {
  from: string
  to: string
  days: string[]
}

/** One day of the rendered calendar with its already-ordered entries. */
export interface CalendarDay {
  iso: string
  entries: CalendarEntry[]
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

/** Formats a `Date` as `YYYY-MM-DD` in local time (no UTC shift). */
export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * Parses `YYYY-MM-DD` into a local `Date`. `new Date('YYYY-MM-DD')` would parse
 * it as UTC and could shift the day in a negative-offset time zone, so the
 * components are split manually (design.md D6/D7 — dates carry no time zone).
 */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** Returns a new `Date` shifted by `days` (may be negative). */
export function addDays(date: Date, days: number): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  result.setDate(result.getDate() + days)
  return result
}

/** Monday of the week containing `date`, at local midnight. */
export function startOfWeekMonday(date: Date): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const offset = (result.getDay() + 6) % 7
  result.setDate(result.getDate() - offset)
  return result
}

/**
 * Builds the Monday-based two-week window shifted by `weekOffset` (each step is
 * 14 days). `weekOffset = 0` is the current week plus the next one.
 */
export function buildCalendarWindow(anchor: Date, weekOffset: number): CalendarWindow {
  const start = addDays(startOfWeekMonday(anchor), weekOffset * 14)
  const days = Array.from({ length: 14 }, (_, index) => toIsoDate(addDays(start, index)))
  return { from: days[0], to: days[days.length - 1], days }
}

/** Localised short weekday of an ISO day, e.g. `Пн`. */
export function weekdayLabel(iso: string): string {
  const date = parseIsoDate(iso)
  return WEEKDAY_LABELS[(date.getDay() + 6) % 7]
}

/** `DD.MM` label of an ISO day. */
export function formatDayLabel(iso: string): string {
  const date = parseIsoDate(iso)
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}`
}

/** `HH:mm` of a server time string (`HH:mm` or `HH:mm:ss`), or `null`. */
export function formatTime(value: string | null): string | null {
  return value ? value.slice(0, 5) : null
}

function compareEntries(left: CalendarEntry, right: CalendarEntry): number {
  const leftTime = left.startsAt ?? null
  const rightTime = right.startsAt ?? null
  if (leftTime !== null && rightTime !== null) {
    const byTime = leftTime.localeCompare(rightTime)
    if (byTime !== 0) {
      return byTime
    }
  } else if (leftTime !== null) {
    return -1
  } else if (rightTime !== null) {
    return 1
  }
  return left.title.localeCompare(right.title, 'ru')
}

/**
 * Distributes entries over the given day keys (entries outside the window are
 * ignored). Within a day entries are ordered by time, timeless ones last, then
 * by title — the exact order the grid and the mobile list both render.
 */
export function groupEntriesByDay(days: string[], entries: CalendarEntry[]): CalendarDay[] {
  const byDay = new Map<string, CalendarEntry[]>(days.map((iso) => [iso, []]))
  for (const entry of entries) {
    byDay.get(entry.startsOn)?.push(entry)
  }
  return days.map((iso) => ({
    iso,
    entries: (byDay.get(iso) ?? []).slice().sort(compareEntries),
  }))
}
