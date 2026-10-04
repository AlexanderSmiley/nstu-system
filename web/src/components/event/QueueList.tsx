import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { ApiError } from '../../api/http'
import { moveEntry, pauseEntry, removeEntry, resumeEntry } from '../../api/queue'
import { eventQueueQueryKey } from '../../api/queryKeys'
import type { QueueEntry } from '../../api/types'
import type { QueueQueryData } from '../../hooks/useQueue'
import { entryStatusLabel, isOwnEntry } from '../../utils/event'
import { activeEntries, computeReorder, reorderEntries } from '../../utils/queue'

interface QueueListProps {
  eventId: string
  entries: readonly QueueEntry[]
  subject: string | null | undefined
  isStaff: boolean
  readOnly: boolean
  /**
   * Reports whether a drag or a pending reorder mutation is in progress so the
   * parent can suspend queue polling (design.md D4).
   */
  onActivityChange?: (active: boolean) => void
}

interface ReorderVariables {
  entryId: string
  overId: string
  position: number
}

function actionError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Не удалось выполнить действие. Попробуйте позже.'
}

interface SortableRowProps {
  entry: QueueEntry
  disabled: boolean
  showHandle: boolean
  children: ReactNode
}

/**
 * One sortable queue row. The drag activator is a dedicated handle so the
 * pause/remove buttons keep working with a pointer; `KeyboardSensor` +
 * `sortableKeyboardCoordinates` make the same handle operable from the keyboard.
 */
function SortableRow({ entry, disabled, showHandle, children }: SortableRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: entry.id, disabled })

  return (
    <li
      ref={setNodeRef}
      className="queue-item"
      data-testid="queue-item"
      data-dragging={isDragging ? 'true' : undefined}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      {showHandle && (
        <button
          type="button"
          ref={setActivatorNodeRef}
          className="queue-item__handle"
          aria-label={`Переместить запись ${entry.name}`}
          disabled={disabled}
          {...attributes}
          {...listeners}
        >
          <span aria-hidden="true">⠿</span>
        </button>
      )}
      <span className="queue-item__position">{entry.position}</span>
      <span className="queue-item__name">{entry.name}</span>
      <span className={`badge badge--${entry.status.toLowerCase()}`}>
        {entryStatusLabel(entry.status)}
      </span>
      {children}
    </li>
  )
}

/**
 * Active queue ordered by position. Staff/admins can reorder rows by dragging
 * (mouse/touch) or with the keyboard; participants only act on their own entry.
 * Reordering is disabled on a read-only (non-`OPEN`) event, optimistic, rolled
 * back on error and serialized so only one mutation runs at a time (design.md
 * D4). The up/down buttons were removed — dragging and the keyboard fully
 * replace them.
 */
export function QueueList({
  eventId,
  entries,
  subject,
  isStaff,
  readOnly,
  onActivityChange,
}: QueueListProps) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)

  const queryKey = eventQueueQueryKey(eventId)

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(eventId) })
  }

  const pause = useMutation({
    mutationFn: (entryId: string) => pauseEntry(eventId, entryId),
    onSuccess: invalidate,
    onError: (mutationError) => setError(actionError(mutationError)),
  })
  const resume = useMutation({
    mutationFn: (entryId: string) => resumeEntry(eventId, entryId),
    onSuccess: invalidate,
    onError: (mutationError) => setError(actionError(mutationError)),
  })
  const remove = useMutation({
    mutationFn: (entryId: string) => removeEntry(eventId, entryId),
    onSuccess: invalidate,
    onError: (mutationError) => setError(actionError(mutationError)),
  })
  const move = useMutation({
    mutationFn: ({ entryId, position }: ReorderVariables) => moveEntry(eventId, entryId, position),
    onMutate: async ({ entryId, overId }) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<QueueQueryData>(queryKey)
      if (previous) {
        queryClient.setQueryData<QueueQueryData>(queryKey, {
          ...previous,
          response: {
            ...previous.response,
            queue: reorderEntries(previous.response.queue, entryId, overId),
          },
        })
      }
      return { previous }
    },
    onError: (mutationError, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous)
      }
      setError(actionError(mutationError))
    },
    onSuccess: invalidate,
  })

  const sorted = activeEntries(entries)
  const canEdit = isStaff && !readOnly
  const busy = pause.isPending || resume.isPending || move.isPending || remove.isPending

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const interactionActive = dragging || move.isPending
  useEffect(() => {
    onActivityChange?.(interactionActive)
    return () => {
      if (interactionActive) {
        onActivityChange?.(false)
      }
    }
  }, [interactionActive, onActivityChange])

  const handleDragEnd = (event: DragEndEvent) => {
    setDragging(false)
    const { active, over } = event
    if (!over || move.isPending) {
      return
    }
    const change = computeReorder(sorted, String(active.id), String(over.id))
    if (!change) {
      return
    }
    setError(null)
    move.mutate({ ...change, overId: String(over.id) })
  }

  if (sorted.length === 0) {
    return <p className="empty-state">Очередь пуста</p>
  }

  return (
    <div className="queue">
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={() => setDragging(true)}
        onDragEnd={handleDragEnd}
        onDragCancel={() => setDragging(false)}
      >
        <SortableContext
          items={sorted.map((entry) => entry.id)}
          strategy={verticalListSortingStrategy}
        >
          <ol className="queue-list">
            {sorted.map((entry) => {
              const own = isOwnEntry(entry, subject)
              return (
                <SortableRow
                  key={entry.id}
                  entry={entry}
                  disabled={!canEdit || busy}
                  showHandle={canEdit}
                >
                  {isStaff && !readOnly && (
                    <span className="queue-item__actions">
                      {entry.status === 'WAITING' && (
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => pause.mutate(entry.id)}
                          disabled={busy}
                        >
                          Приостановить
                        </button>
                      )}
                      {entry.status === 'PAUSED' && (
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => resume.mutate(entry.id)}
                          disabled={busy}
                        >
                          Вернуть
                        </button>
                      )}
                      <button
                        type="button"
                        className="button button--danger button--small"
                        onClick={() => remove.mutate(entry.id)}
                        disabled={busy}
                      >
                        Удалить
                      </button>
                    </span>
                  )}
                  {!isStaff && !readOnly && own && (
                    <span className="queue-item__actions">
                      <button
                        type="button"
                        className="button button--secondary button--small"
                        onClick={() => remove.mutate(entry.id)}
                        disabled={busy}
                      >
                        Выйти из очереди
                      </button>
                    </span>
                  )}
                </SortableRow>
              )
            })}
          </ol>
        </SortableContext>
      </DndContext>
    </div>
  )
}
