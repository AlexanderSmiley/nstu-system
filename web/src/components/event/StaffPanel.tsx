import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { advanceQueue } from '../../api/queue'
import { eventDetailQueryKey, eventQueueQueryKey } from '../../api/queryKeys'
import { CarryOverPanel } from './CarryOverPanel'
import { StaffAddForm } from './StaffAddForm'

interface StaffPanelProps {
  eventId: string
  slug: string
  status: string
}

function staffError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'queue_empty') {
      return 'Очередь пуста'
    }
    return error.message
  }
  return 'Не удалось выполнить действие. Попробуйте позже.'
}

/**
 * Compact staff/admin controls shown next to the queue (design.md D3): advance,
 * manual add and carry-over, plus links to the settings and journal screens.
 * Lifecycle actions (close/open/archive) live on the settings screen.
 */
export function StaffPanel({ eventId, slug, status }: StaffPanelProps) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(eventId) }),
      queryClient.invalidateQueries({ queryKey: eventDetailQueryKey(slug) }),
    ])
  }

  const advance = useMutation({
    mutationFn: () => advanceQueue(eventId),
    onSuccess: invalidate,
    onError: (mutationError) => setError(staffError(mutationError)),
  })

  const readOnly = status !== 'OPEN'
  const busy = advance.isPending

  return (
    <section className="panel" aria-label="Управление очередью">
      <h2>Управление</h2>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="staff-actions">
        <button
          type="button"
          className="button button--primary"
          onClick={() => advance.mutate()}
          disabled={readOnly || busy}
        >
          Далее
        </button>
        <Link className="button button--secondary" to={`/e/${encodeURIComponent(slug)}/settings`}>
          Настройки события
        </Link>
        <Link className="button button--secondary" to={`/e/${encodeURIComponent(slug)}/journal`}>
          Журнал сдач
        </Link>
      </div>
      {!readOnly && (
        <>
          <StaffAddForm eventId={eventId} />
          <CarryOverPanel eventId={eventId} disabled={false} />
        </>
      )}
    </section>
  )
}
