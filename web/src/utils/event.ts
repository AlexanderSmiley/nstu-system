import type { QueueEntry, QueueEntryStatus } from '../api/types'

/**
 * Whether an entry belongs to the current subject. Guests are identified by a
 * `guest:<uuid>` subject matching the entry's `guestRef` (design.md D11).
 */
export function isOwnEntry(entry: QueueEntry, subject: string | null | undefined): boolean {
  if (!subject) {
    return false
  }
  if (entry.holderAccountId && entry.holderAccountId === subject) {
    return true
  }
  if (entry.guestRef && subject === `guest:${entry.guestRef}`) {
    return true
  }
  return false
}

/** Human label of an event lifecycle status. */
export function eventStatusLabel(status: string): string {
  switch (status) {
    case 'OPEN':
      return 'Открыто'
    case 'CLOSED':
      return 'Закрыто'
    case 'ARCHIVED':
      return 'В архиве'
    default:
      return status
  }
}

/** Human label of an event availability level. */
export function availabilityLabel(availability: string): string {
  switch (availability) {
    case 'GUEST+':
      return 'Для всех'
    case 'STUDENT+':
      return 'Для студентов'
    case 'STAFF+':
      return 'Только персонал'
    default:
      return availability
  }
}

/** Human label of a queue entry status. */
export function entryStatusLabel(status: QueueEntryStatus): string {
  switch (status) {
    case 'WAITING':
      return 'Ожидание'
    case 'PAUSED':
      return 'На паузе'
    case 'PASSED':
      return 'Сдано'
  }
}

/** Label of the name field, driven by the event's entry unit (design.md D24). */
export function entryNameLabel(entryUnit: string): string {
  return entryUnit === 'PERSON' ? 'Имя участника' : 'Название бригады'
}

/** Reason text returned by carry-over `skipped` items, when it is a known code. */
export function carryOverReasonLabel(reason: string): string {
  switch (reason) {
    case 'name_taken':
      return 'имя уже занято'
    case 'queue_full':
      return 'очередь заполнена'
    case 'already_joined':
      return 'уже в очереди'
    case 'event_closed':
      return 'событие закрыто'
    default:
      return reason
  }
}
