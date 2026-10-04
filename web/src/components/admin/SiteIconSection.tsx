import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  DEFAULT_SITE_ICON,
  SITE_ICON_PATH,
  fetchSiteIcon,
  resetSiteIcon,
  uploadSiteIcon,
} from '../../api/site'
import { ApiError } from '../../api/http'
import { SITE_ICON_QUERY_KEY } from '../../api/queryKeys'

/** Mirrors `nstu.site.icon.max-bytes`; the server is the source of truth. */
const MAX_BYTES = 256 * 1024

const ACCEPTED_TYPES = '.png,.ico,.svg,image/png,image/x-icon,image/svg+xml'

function iconErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'invalid_icon') {
      return 'Недопустимый файл. Разрешены PNG, ICO или SVG без скриптов.'
    }
    if (error.code === 'icon_too_large' || error.status === 413) {
      return 'Файл слишком большой. Максимальный размер — 256 КБ.'
    }
    if (error.status === 403) {
      return 'Недостаточно прав'
    }
    return error.message
  }
  return 'Не удалось сохранить иконку'
}

function formatSize(size: number | null): string | null {
  if (size === null) {
    return null
  }
  return `${(size / 1024).toFixed(1)} КБ`
}

/**
 * Creates a local object URL for the picked file. Returns `null` when object
 * URLs are unavailable or reject the file (e.g. a foreign `File` under jsdom);
 * the server preview still works after a successful upload.
 */
function createPreviewUrl(file: File): string | null {
  try {
    if (typeof URL.createObjectURL === 'function') {
      return URL.createObjectURL(file)
    }
  } catch {
    // Fall through to no local preview.
  }
  return null
}

function revokePreviewUrl(url: string | null): void {
  if (!url) {
    return
  }
  try {
    URL.revokeObjectURL(url)
  } catch {
    // Nothing to revoke in environments without object-URL support.
  }
}

/**
 * «Иконка сайта» section inside «Администрирование → Общее»: file picker,
 * preview, upload and reset (change add-site-icon). Only reachable behind the
 * ADMIN guard; the server enforces the same role.
 */
export function SiteIconSection() {
  const queryClient = useQueryClient()
  const iconQuery = useQuery({
    queryKey: SITE_ICON_QUERY_KEY,
    queryFn: fetchSiteIcon,
    staleTime: Infinity,
  })

  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  // Keeps the current object URL accessible for revocation without triggering
  // a state update in an effect.
  const previewUrlRef = useRef<string | null>(null)

  useEffect(
    () => () => {
      revokePreviewUrl(previewUrlRef.current)
    },
    [],
  )

  const hasIcon = iconQuery.data != null
  const serverVersion = iconQuery.data?.etag ?? iconQuery.data?.updatedAt ?? null

  const clearSelectedFile = () => {
    revokePreviewUrl(previewUrlRef.current)
    previewUrlRef.current = null
    setFile(null)
    setPreviewUrl(null)
  }

  const upload = useMutation({
    mutationFn: (selected: File) => uploadSiteIcon(selected),
    onSuccess: async () => {
      setSaved(true)
      setError(null)
      clearSelectedFile()
      await queryClient.invalidateQueries({ queryKey: SITE_ICON_QUERY_KEY })
    },
    onError: (mutationError) => {
      setSaved(false)
      setError(iconErrorMessage(mutationError))
    },
  })

  const reset = useMutation({
    mutationFn: resetSiteIcon,
    onSuccess: async () => {
      setSaved(false)
      setError(null)
      clearSelectedFile()
      await queryClient.invalidateQueries({ queryKey: SITE_ICON_QUERY_KEY })
    },
    onError: (mutationError) => {
      setSaved(false)
      setError(iconErrorMessage(mutationError))
    },
  })

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSaved(false)
    setError(null)
    const selected = event.target.files?.[0] ?? null
    revokePreviewUrl(previewUrlRef.current)
    const url = selected ? createPreviewUrl(selected) : null
    previewUrlRef.current = url
    setFile(selected)
    setPreviewUrl(url)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file) {
      setError('Выберите файл иконки')
      return
    }
    if (file.size > MAX_BYTES) {
      setSaved(false)
      setError('Файл слишком большой. Максимальный размер — 256 КБ.')
      return
    }
    upload.mutate(file)
  }

  const serverPreview = serverVersion
    ? `${SITE_ICON_PATH}?v=${encodeURIComponent(serverVersion)}`
    : SITE_ICON_PATH
  const previewSrc = previewUrl ?? (hasIcon ? serverPreview : DEFAULT_SITE_ICON)
  const sizeLabel = formatSize(previewUrl ? file?.size ?? null : iconQuery.data?.sizeBytes ?? null)

  return (
    <section className="admin-section" aria-label="Иконка сайта">
      <h2>Иконка сайта</h2>
      <form className="admin-form admin-form--stacked" onSubmit={handleSubmit} noValidate>
        <label className="field" htmlFor="site-icon-file">
          <span className="field__label">Файл иконки</span>
          <input
            id="site-icon-file"
            name="site-icon-file"
            type="file"
            accept={ACCEPTED_TYPES}
            onChange={handleFileChange}
          />
        </label>
        <div className="site-icon-preview">
          <img className="site-icon-preview__image" src={previewSrc} alt="Предпросмотр иконки" />
          {sizeLabel && <span className="field__hint">Размер: {sizeLabel}</span>}
        </div>
        <p className="field__hint">Допустимы PNG, ICO и SVG размером не более 256 КБ.</p>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {saved && !error && <p className="form-success">Иконка сохранена</p>}
        <div className="admin-form__actions">
          <button type="submit" className="button button--primary" disabled={!file || upload.isPending}>
            Загрузить
          </button>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => reset.mutate()}
            disabled={!hasIcon || reset.isPending}
          >
            Сбросить
          </button>
        </div>
      </form>
    </section>
  )
}
