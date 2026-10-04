import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteCalendarEntry } from '../../api/calendar'
import { CALENDAR_QUERY_KEY } from '../../api/queryKeys'
import type { CalendarEntry } from '../../api/types'
import { audienceLabel, formatDayLabel, formatTime } from '../../utils/calendar'
import { CalendarEntryForm } from './CalendarEntryForm'

/** Descriptions longer than this get a collapse/expand toggle. */
const DESCRIPTION_CLAMP_THRESHOLD = 120

interface CalendarEntryCardProps {
  entry: CalendarEntry
  /** Author or `ADMIN`; controls the edit/delete buttons (calendar spec). */
  canManage: boolean
  from: string
  to: string
  days: string[]
  canChooseStaff: boolean
  onClose: () => void
}

/**
 * Modal card of one calendar entry: details for every viewer, actions only for
 * the author or an administrator. "Edit" swaps the card body for the shared form
 * (design.md D7); "Delete" asks for confirmation before issuing the request.
 */
export function CalendarEntryCard({
  entry,
  canManage,
  from,
  to,
  days,
  canChooseStaff,
  onClose,
}: CalendarEntryCardProps) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [expanded, setExpanded] = useState(false)

  const deleteMutation = useMutation({
    mutationFn: () => deleteCalendarEntry(entry.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: CALENDAR_QUERY_KEY })
      onClose()
    },
  })

  const time = formatTime(entry.startsAt)
  const description = entry.description
  const showDescriptionToggle = (description?.length ?? 0) > DESCRIPTION_CLAMP_THRESHOLD

  const handleDelete = () => {
    if (window.confirm('Удалить мероприятие?')) {
      deleteMutation.mutate()
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal" role="dialog" aria-modal="true" aria-label={entry.title}>
        <div className="modal__header">
          <h2 className="modal__title">{entry.title}</h2>
          <button
            type="button"
            className="button button--secondary button--small"
            onClick={onClose}
            aria-label="Закрыть карточку"
          >
            Закрыть
          </button>
        </div>

        {editing && canManage ? (
          <CalendarEntryForm
            mode="edit"
            entry={entry}
            from={from}
            to={to}
            days={days}
            canChooseStaff={canChooseStaff}
            onClose={onClose}
          />
        ) : (
          <>
            <dl className="details">
              <div className="details__row">
                <dt>Дата</dt>
                <dd>{formatDayLabel(entry.startsOn)}</dd>
              </div>
              <div className="details__row">
                <dt>Время</dt>
                <dd>{time ?? '—'}</dd>
              </div>
              <div className="details__row">
                <dt>Адресат</dt>
                <dd>{audienceLabel(entry.audience)}</dd>
              </div>
              <div className="details__row">
                <dt>Автор</dt>
                <dd>{entry.authorDisplayName ?? '—'}</dd>
              </div>
              <div className="details__row">
                <dt>Описание</dt>
                <dd>
                  {description ? (
                    <>
                      <p
                        className={
                          expanded
                            ? 'calendar-card__description calendar-card__description--expanded'
                            : 'calendar-card__description'
                        }
                        data-testid="calendar-entry-description"
                        data-clamped={!expanded}
                      >
                        {description}
                      </p>
                      {showDescriptionToggle && (
                        <button
                          type="button"
                          className="calendar-card__description-toggle"
                          aria-expanded={expanded}
                          onClick={() => setExpanded((value) => !value)}
                        >
                          {expanded ? 'Свернуть' : 'Развернуть'}
                        </button>
                      )}
                    </>
                  ) : (
                    '—'
                  )}
                </dd>
              </div>
            </dl>

            {canManage && (
              <div className="calendar-card__footer" data-testid="calendar-card-footer">
                <button
                  type="button"
                  className="button button--secondary button--small"
                  onClick={() => setEditing(true)}
                >
                  Редактировать
                </button>
                <button
                  type="button"
                  className="button button--danger button--small"
                  onClick={handleDelete}
                  disabled={deleteMutation.isPending}
                >
                  Удалить
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
