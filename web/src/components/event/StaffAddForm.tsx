import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/http'
import { addStaffEntry } from '../../api/queue'
import { eventQueueQueryKey } from '../../api/queryKeys'

function addErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'name_taken':
        return 'Это имя уже занято'
      case 'queue_full':
        return 'Очередь заполнена'
      case 'event_closed':
        return 'Приём закрыт'
      case 'invalid_name':
        return 'Введите имя записи'
      default:
        return error.message
    }
  }
  return 'Не удалось добавить запись'
}

interface StaffAddFormProps {
  eventId: string
}

/** Staff adds a queue entry on someone's behalf (origin `STAFF`). */
export function StaffAddForm({ eventId }: StaffAddFormProps) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => addStaffEntry(eventId, name.trim()),
    onSuccess: async () => {
      setName('')
      setError(null)
      await queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(eventId) })
    },
    onError: (mutationError) => setError(addErrorMessage(mutationError)),
  })

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Введите имя записи')
      return
    }
    setError(null)
    mutation.mutate()
  }

  return (
    <form className="staff-add-form" onSubmit={handleSubmit} noValidate>
      <span className="field__label">Добавить запись</span>
      <input
        className="field__input"
        aria-label="Имя новой записи"
        value={name}
        onChange={(event) => setName(event.target.value)}
      />
      <button type="submit" className="button button--secondary" disabled={mutation.isPending}>
        Добавить запись
      </button>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
