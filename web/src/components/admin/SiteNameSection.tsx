import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateSiteName } from '../../api/admin'
import { ApiError } from '../../api/http'
import { SITE_QUERY_KEY } from '../../api/queryKeys'
import { useSiteName } from '../../hooks/useSiteName'

/** Admin section «Название сайта»; saving updates the header and login screen. */
export function SiteNameSection() {
  const queryClient = useQueryClient()
  const siteName = useSiteName()
  const [draft, setDraft] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // `null` means "not edited yet": the value follows the async site name.
  const value = draft ?? siteName

  const mutation = useMutation({
    mutationFn: (name: string) => updateSiteName(name),
    onSuccess: (data) => {
      queryClient.setQueryData(SITE_QUERY_KEY, data)
      setDraft(null)
      setSaved(true)
      setError(null)
    },
    onError: (mutationError) => {
      setSaved(false)
      setError(mutationError instanceof ApiError ? mutationError.message : 'Не удалось сохранить')
    },
  })

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmed = value.trim()
    if (trimmed === '') {
      setError('Название не может быть пустым')
      return
    }
    setSaved(false)
    mutation.mutate(trimmed)
  }

  return (
    <section className="admin-section" aria-label="Название сайта">
      <h2>Название сайта</h2>
      <form className="admin-form" onSubmit={handleSubmit} noValidate>
        <label className="field" htmlFor="site-name">
          <span className="field__label">Название</span>
          <input
            id="site-name"
            name="site-name"
            className="field__input"
            value={value}
            maxLength={120}
            onChange={(event) => setDraft(event.target.value)}
          />
        </label>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {saved && !error && <p className="form-success">Название сохранено</p>}
        <button type="submit" className="button button--primary" disabled={mutation.isPending}>
          Сохранить
        </button>
      </form>
    </section>
  )
}
