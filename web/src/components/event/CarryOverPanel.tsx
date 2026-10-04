import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/http'
import { fetchEventHistory } from '../../api/events'
import { carryOver } from '../../api/queue'
import { EVENT_HISTORY_QUERY_KEY, eventQueueQueryKey } from '../../api/queryKeys'
import type { CarryOverResult } from '../../api/types'
import { carryOverReasonLabel } from '../../utils/event'

interface CarryOverPanelProps {
  eventId: string
  disabled: boolean
}

/**
 * Moves the tail of a past event into this one (design.md D18). The source is
 * picked from the visible history; `added`/`skipped` are shown afterwards.
 */
export function CarryOverPanel({ eventId, disabled }: CarryOverPanelProps) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [sourceEventId, setSourceEventId] = useState('')
  const [result, setResult] = useState<CarryOverResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const historyQuery = useQuery({
    queryKey: EVENT_HISTORY_QUERY_KEY,
    queryFn: fetchEventHistory,
    enabled: open,
  })

  const mutation = useMutation({
    mutationFn: () => carryOver(eventId, sourceEventId),
    onSuccess: async (data) => {
      setResult(data)
      setError(null)
      await queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(eventId) })
    },
    onError: (mutationError) =>
      setError(mutationError instanceof ApiError ? mutationError.message : 'Не удалось перенести хвост'),
  })

  const sources = (historyQuery.data ?? []).filter((event) => event.id !== eventId)

  return (
    <div className="carry-over">
      <button
        type="button"
        className="button button--secondary"
        onClick={() => setOpen((value) => !value)}
        disabled={disabled}
      >
        Перенести хвост
      </button>
      {open && (
        <div className="carry-over__panel">
          <label className="field" htmlFor="carry-over-source">
            <span className="field__label">Исходное событие</span>
            <select
              id="carry-over-source"
              className="field__input"
              value={sourceEventId}
              onChange={(event) => setSourceEventId(event.target.value)}
            >
              <option value="">Выберите событие</option>
              {sources.map((event) => (
                <option key={event.id} value={event.id}>
                  {event.title}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="button button--primary"
            onClick={() => mutation.mutate()}
            disabled={sourceEventId === '' || mutation.isPending}
          >
            Перенести
          </button>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          {result && (
            <div className="carry-over__result" data-testid="carry-over-result">
              <p>Перенесено: {result.added.length}</p>
              {result.added.length > 0 && (
                <ul>
                  {result.added.map((item) => (
                    <li key={item.sourceEntryId}>{item.name}</li>
                  ))}
                </ul>
              )}
              {result.skipped.length > 0 && (
                <>
                  <p>Пропущено: {result.skipped.length}</p>
                  <ul>
                    {result.skipped.map((item) => (
                      <li key={item.sourceEntryId}>
                        {item.name} — {carryOverReasonLabel(item.reason)}
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
