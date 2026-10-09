/**
 * Builds the full short link of an event: `<origin>/e/<slug>`.
 * The server resolves the path via `GET /api/events/by-slug/{slug}`.
 */
export function buildEventShareUrl(slug: string): string {
  return `${window.location.origin}/e/${slug}`
}

/**
 * Copies `text` to the clipboard. Prefers the async Clipboard API and falls
 * back to a hidden `textarea` + `document.execCommand('copy')` when it is
 * unavailable or rejected (insecure context, permission denied). Resolves to
 * `true` only when the text actually reached the clipboard.
 */
export async function copyText(text: string): Promise<boolean> {
  const clipboard = (navigator as { clipboard?: Clipboard }).clipboard
  if (clipboard && typeof clipboard.writeText === 'function') {
    try {
      await clipboard.writeText(text)
      return true
    } catch {
      // Fall through to the legacy path.
    }
  }
  return fallbackCopy(text)
}

function fallbackCopy(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  textarea.style.opacity = '0'
  try {
    document.body.appendChild(textarea)
    textarea.select()
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    // Never leak the helper node when `execCommand` is missing or throws.
    textarea.remove()
  }
}

/** Whether the browser exposes the Web Share API (`navigator.share`). */
export function canUseWebShare(): boolean {
  return typeof (navigator as { share?: unknown }).share === 'function'
}
