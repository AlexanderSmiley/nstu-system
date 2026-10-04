import { request } from './http'
import type { SiteIconInfo, SiteInfo } from './types'

/** Fallback shown while the public site name loads or if the request fails. */
export const DEFAULT_SITE_NAME = 'NSTU System'

/** Built-in icon used until an administrator uploads a custom one. */
export const DEFAULT_SITE_ICON = '/favicon.svg'

/** Public endpoint serving the stored icon (with `ETag`/`If-None-Match`). */
export const SITE_ICON_PATH = '/api/site/icon'

const ADMIN_SITE_ICON_PATH = '/api/admin/site/icon'

/** `GET /api/site` — public, exposes only the site name (design.md D27). */
export function fetchSite(): Promise<SiteInfo> {
  return request<SiteInfo>('/api/site')
}

/**
 * `GET /api/site/icon` — public. Returns `null` when no icon is stored (`404`)
 * or when the request fails, so callers fall back to the built-in favicon
 * without surfacing an error (design.md D4). The bytes are not read; only the
 * caching metadata is needed.
 */
export async function fetchSiteIcon(): Promise<SiteIconInfo | null> {
  try {
    const response = await fetch(SITE_ICON_PATH, { credentials: 'include' })
    if (!response.ok) {
      return null
    }
    return {
      contentType: response.headers.get('Content-Type'),
      sizeBytes: parseContentLength(response.headers.get('Content-Length')),
      etag: response.headers.get('ETag'),
    }
  } catch {
    return null
  }
}

/**
 * `PUT /api/admin/site/icon` — admin only. The multipart field name is `file`
 * (must match the controller's `@RequestPart("file")`).
 */
export function uploadSiteIcon(file: File): Promise<SiteIconInfo> {
  const body = new FormData()
  body.append('file', file)
  return request<SiteIconInfo>(ADMIN_SITE_ICON_PATH, { method: 'PUT', body })
}

/** `DELETE /api/admin/site/icon` — admin only, idempotent. */
export function resetSiteIcon(): Promise<void> {
  return request<void>(ADMIN_SITE_ICON_PATH, { method: 'DELETE' })
}

function parseContentLength(value: string | null): number | null {
  if (!value) {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}
