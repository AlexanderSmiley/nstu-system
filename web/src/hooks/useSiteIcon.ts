import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DEFAULT_SITE_ICON, SITE_ICON_PATH, fetchSiteIcon } from '../api/site'
import { SITE_ICON_QUERY_KEY } from '../api/queryKeys'

/**
 * Applies the site icon as the browser tab icon (design.md D4).
 *
 * <p>Called once at the shell level so it also runs on the login and forced
 * password-change screens, independently of the session. When an icon is stored
 * the `<link rel="icon">` points at the public endpoint with a version query
 * parameter taken from the `ETag` (or `updatedAt`) so the browser re-fetches it
 * after a change; otherwise the built-in `/favicon.svg` is kept. Failures are
 * silent: the tab simply keeps the default icon.</p>
 */
export function useSiteIcon(): void {
  const { data } = useQuery({
    queryKey: SITE_ICON_QUERY_KEY,
    queryFn: fetchSiteIcon,
    staleTime: Infinity,
  })

  useEffect(() => {
    if (data) {
      const version = data.etag ?? data.updatedAt ?? null
      const href = version
        ? `${SITE_ICON_PATH}?v=${encodeURIComponent(version)}`
        : SITE_ICON_PATH
      applyIconHref(href, data.contentType)
    } else {
      applyIconHref(DEFAULT_SITE_ICON, 'image/svg+xml')
    }
  }, [data])
}

/** Finds (or creates) the `rel="icon"` link and updates it in place. */
function applyIconHref(href: string, type?: string | null): void {
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.setAttribute('href', href)
  if (type) {
    link.setAttribute('type', type)
  }
}
