import { describe, expect, it } from 'vitest'
import type { QueueEntry, QueueEntryStatus } from '../api/types'
import { activeEntries, computeReorder, reorderEntries } from './queue'

function makeEntry(
  id: string,
  position: number,
  status: QueueEntryStatus = 'WAITING',
): QueueEntry {
  return {
    id,
    position,
    name: `Запись ${id}`,
    status,
    origin: 'JOIN',
    holderAccountId: null,
    guestRef: null,
    passedAt: null,
    createdAt: '2026-04-01T00:00:00Z',
  }
}

function ids(entries: readonly QueueEntry[]): string[] {
  return entries.map((entry) => entry.id)
}

const list = [
  makeEntry('a', 1),
  makeEntry('b', 2),
  makeEntry('c', 3),
  makeEntry('d', 4),
]

describe('computeReorder', () => {
  it('перемещает запись вверх', () => {
    expect(computeReorder(list, 'c', 'b')).toEqual({ entryId: 'c', position: 2 })
  })

  it('перемещает запись вниз', () => {
    expect(computeReorder(list, 'b', 'c')).toEqual({ entryId: 'b', position: 3 })
  })

  it('перемещает запись в начало', () => {
    expect(computeReorder(list, 'd', 'a')).toEqual({ entryId: 'd', position: 1 })
  })

  it('перемещает запись в конец', () => {
    expect(computeReorder(list, 'a', 'd')).toEqual({ entryId: 'a', position: 4 })
  })

  it('возвращает null при перетаскивании на то же место', () => {
    expect(computeReorder(list, 'b', 'b')).toBeNull()
    expect(computeReorder(list, 'a', 'a')).toBeNull()
  })

  it('возвращает null для пустого и одноэлементного списка', () => {
    expect(computeReorder([], 'a', 'b')).toBeNull()
    expect(computeReorder([makeEntry('a', 1)], 'a', 'a')).toBeNull()
  })

  it('возвращает null для неизвестных идентификаторов', () => {
    expect(computeReorder(list, 'missing', 'a')).toBeNull()
    expect(computeReorder(list, 'a', 'missing')).toBeNull()
  })

  it('игнорирует сдавшие записи', () => {
    const withPassed = [
      makeEntry('a', 1),
      makeEntry('p', 2, 'PASSED'),
      makeEntry('b', 3),
      makeEntry('c', 4),
    ]
    // `p` is not active: dropping `a` onto it is a no-op, and `p` is skipped when
    // counting positions.
    expect(computeReorder(withPassed, 'a', 'p')).toBeNull()
    expect(computeReorder(withPassed, 'a', 'c')).toEqual({ entryId: 'a', position: 3 })
    expect(ids(activeEntries(withPassed))).toEqual(['a', 'b', 'c'])
  })

  it('учитывает записи на паузе как активные', () => {
    const withPaused = [makeEntry('a', 1), makeEntry('b', 2, 'PAUSED'), makeEntry('c', 3)]
    expect(computeReorder(withPaused, 'c', 'b')).toEqual({ entryId: 'c', position: 2 })
  })
})

describe('reorderEntries', () => {
  it('переставляет записи и перенумеровывает позиции', () => {
    const reordered = reorderEntries(list, 'a', 'd')
    expect(ids(reordered)).toEqual(['b', 'c', 'd', 'a'])
    expect(reordered.map((entry) => entry.position)).toEqual([1, 2, 3, 4])
  })

  it('сохраняет исходный массив при отсутствии изменений', () => {
    expect(reorderEntries(list, 'b', 'b')).toBe(list)
  })
})
