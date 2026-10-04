import { request } from './http'
import type { SiteInfo } from './types'

/** Fallback shown while the public site name loads or if the request fails. */
export const DEFAULT_SITE_NAME = 'NSTU System'

/** `GET /api/site` — public, exposes only the site name (design.md D27). */
export function fetchSite(): Promise<SiteInfo> {
  return request<SiteInfo>('/api/site')
}
