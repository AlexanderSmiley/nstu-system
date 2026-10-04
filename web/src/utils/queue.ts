import type { QueueEntry } from '../api/types'

/** A pending reorder: the moved entry and its new 1-based server position. */
export interface QueueReorder {
  entryId: string
  position: number
}

/** Statuses that occupy a position in the active queue (design.md D4). */
function isActiveEntry(entry: QueueEntry): boolean {
  return entry.status === 'WAITING' || entry.status === 'PAUSED'
}

/**
 * Active entries (`WAITING`/`PAUSED`) ordered by ascending server position.
 * `PASSED` rows keep their historical position and never take part in reordering.
 */
export function activeEntries(entries: readonly QueueEntry[]): QueueEntry[] {
  return entries
    .filter(isActiveEntry)
    .slice()
    .sort((a, b) => a.position - b.position)
}

/**
 * Computes where an entry dragged from `activeId` onto `overId` should land
 * (design.md D4). Only active (`WAITING`/`PAUSED`) entries participate; any
 * other rows are ignored.
 *
 * <p>The returned `position` is the 1-based target position among the active
 * entries, i.e. exactly the value expected by
 * `PATCH /api/events/{id}/queue/{entryId}/position` (server inserts the moved
 * entry at `position - 1` after removing it from the current order).</p>
 *
 * @returns `{ entryId, position }`, or `null` when nothing changes: the two ids
 *   are equal, either id is unknown/inactive, or the entry already occupies the
 *   dropped slot. A `null` result means "do not send a request".
 */
export function computeReorder(
  entries: readonly QueueEntry[],
  activeId: string,
  overId: string,
): QueueReorder | null {
  if (!activeId || !overId || activeId === overId) {
    return null
  }
  const active = activeEntries(entries)
  const fromIndex = active.findIndex((entry) => entry.id === activeId)
  const overIndex = active.findIndex((entry) => entry.id === overId)
  if (fromIndex === -1 || overIndex === -1 || fromIndex === overIndex) {
    return null
  }
  const position = overIndex + 1
  if (position === active[fromIndex].position) {
    return null
  }
  return { entryId: activeId, position }
}

/**
 * Applies an optimistic reorder to `entries`, mirroring `computeReorder` (same
 * `arrayMove` semantics as the server). Returns the active entries in their new
 * order with renumbered 1-based positions, followed by any non-active rows
 * (which keep their historical position). Returns the original array reference
 * when the move is a no-op so React Query does not re-render needlessly.
 */
export function reorderEntries(
  entries: readonly QueueEntry[],
  activeId: string,
  overId: string,
): QueueEntry[] {
  const change = computeReorder(entries, activeId, overId)
  if (!change) {
    return entries as QueueEntry[]
  }
  const active = activeEntries(entries)
  const fromIndex = active.findIndex((entry) => entry.id === activeId)
  const overIndex = active.findIndex((entry) => entry.id === overId)
  const reordered = active.slice()
  const [moved] = reordered.splice(fromIndex, 1)
  reordered.splice(overIndex, 0, moved)
  const reorderedIds = new Set(reordered.map((entry) => entry.id))
  const others = entries.filter((entry) => !reorderedIds.has(entry.id))
  return [
    ...reordered.map((entry, index) => ({ ...entry, position: index + 1 })),
    ...others,
  ]
}
