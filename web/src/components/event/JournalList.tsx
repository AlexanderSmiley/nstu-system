import type { QueueEntry } from '../../api/types'
import { formatEventDate } from '../../utils/date'

interface JournalListProps {
  entries: readonly QueueEntry[]
}

/** Passed entries, ordered by pass time (design.md D17). */
export function JournalList({ entries }: JournalListProps) {
  const sorted = [...entries].sort((a, b) => {
    const left = a.passedAt ?? a.createdAt
    const right = b.passedAt ?? b.createdAt
    return left.localeCompare(right)
  })

  if (sorted.length === 0) {
    return <p className="empty-state">Пока никто не сдал</p>
  }

  return (
    <ul className="journal-list">
      {sorted.map((entry) => (
        <li key={entry.id} className="journal-item" data-testid="journal-item">
          <span className="journal-item__name">{entry.name}</span>
          <span className="journal-item__time">{formatEventDate(entry.passedAt)}</span>
        </li>
      ))}
    </ul>
  )
}
