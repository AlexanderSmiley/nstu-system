import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/http'
import { joinQueue } from '../../api/queue'
import { eventQueueQueryKey } from '../../api/queryKeys'
import { entryNameLabel } from '../../utils/event'

function joinErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'invalid_name':
        return 'Введите имя записи'
      case 'profile_required':
        return 'Для вступления заполните профиль'
      case 'profile_unavailable':
        return 'Профиль недоступен, попробуйте позже'
      case 'name_taken':
        return 'Это имя уже занято'
      case 'already_joined':
        return 'Вы уже стоите в очереди'
      case 'queue_full':
        return 'Очередь заполнена'
      case 'event_closed':
        return 'Приём закрыт'
      default:
        return error.message
    }
  }
  return 'Не удалось встать в очередь. Попробуйте позже.'
}

interface JoinQueueFormProps {
  eventId: string
  entryUnit: string
  isGuest: boolean
}

/** Form to join the queue; guests must provide a name, accounts may leave it empty. */
export function JoinQueueForm({ eventId, entryUnit, isGuest }: JoinQueueFormProps) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (value: string) => joinQueue(eventId, value === '' ? undefined : value),
    onSuccess: async () => {
      setName('')
      setError(null)
      await queryClient.invalidateQueries({ queryKey: eventQueueQueryKey(eventId) })
    },
    onError: (mutationError) => setError(joinErrorMessage(mutationError)),
  })

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmed = name.trim()
    if (isGuest && trimmed === '') {
      setError('Введите имя записи')
      return
    }
    setError(null)
    mutation.mutate(trimmed)
  }

  return (
    <form className="join-form" onSubmit={handleSubmit} noValidate>
      <label className="field" htmlFor="join-name">
        <span className="field__label">{entryNameLabel(entryUnit)}</span>
        <input
          id="join-name"
          name="join-name"
          className="field__input"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      <p className="field__hint">
        {isGuest ? 'Для гостя имя обязательно.' : 'Можно оставить пустым — подставим ваше имя.'}
      </p>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="button button--primary" disabled={mutation.isPending}>
        Встать в очередь
      </button>
    </form>
  )
}
