export interface ApiErrorBody {
  error?: string
  code?: string
  message?: string
}

/**
 * Error thrown for any non-2xx API response. The backend uses a uniform body
 * `{ "error": "<code>", "message": "<text>" }`; `code` is exposed so callers can
 * branch on a stable identifier instead of parsing the localised message.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Некорректный запрос',
  401: 'Требуется вход в систему',
  403: 'Недостаточно прав',
  404: 'Не найдено',
  500: 'Ошибка сервера',
}

/** Auth endpoints whose own `401` must never start the refresh cycle. */
const NO_REFRESH_PATHS = new Set(['/api/auth/login', '/api/auth/refresh', '/api/auth/guest'])

type SessionExpiredHandler = () => void

let sessionExpiredHandler: SessionExpiredHandler | null = null

/** In-flight refresh, shared by every concurrent `401` (single-flight). */
let refreshing: Promise<boolean> | null = null

/**
 * Registers the callback invoked when the session cannot be refreshed (the
 * refresh request itself failed). The default is a no-op so this module never
 * navigates on its own; the application wires this to reset its session state.
 */
export function setSessionExpiredHandler(handler: SessionExpiredHandler | null): void {
  sessionExpiredHandler = handler
}

function isRefreshCandidate(path: string): boolean {
  const [pathname] = path.split('?')
  return !NO_REFRESH_PATHS.has(pathname)
}

async function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

function notifySessionExpired(): void {
  sessionExpiredHandler?.()
}

/**
 * Performs a request and, on a `401` from a non-auth endpoint, refreshes the
 * session once and repeats the original request. The retry is limited to a
 * single attempt, and concurrent `401`s share the same in-flight refresh
 * (single-flight) instead of stampeding `POST /api/auth/refresh`.
 */
async function performFetch(path: string, init: RequestInit): Promise<Response> {
  let response = await fetch(path, { ...init, credentials: 'include' })
  if (response.status === 401 && isRefreshCandidate(path)) {
    const refreshed = await refreshSession()
    if (refreshed) {
      response = await fetch(path, { ...init, credentials: 'include' })
    } else {
      notifySessionExpired()
    }
  }
  return response
}

/**
 * Thin `fetch` wrapper for the SPA: always sends the session cookie
 * (`credentials: 'include'`), serialises JSON bodies, normalises errors and
 * transparently refreshes an expired access token once.
 */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await performFetch(path, { ...init, headers: buildHeaders(init) })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return parseBody<T>(response)
}

/** Result of a conditional GET that honours `ETag`/`If-None-Match`. */
export interface ConditionalResult<T> {
  /** `true` when the server replied `304` and `data` must be reused. */
  notModified: boolean
  data: T | null
  etag: string | null
}

/**
 * Conditional GET used by the queue polling (design.md D20). A `304` is a valid
 * outcome rather than an error: the caller keeps its previous payload.
 */
export async function conditionalRequest<T>(
  path: string,
  etag: string | null,
): Promise<ConditionalResult<T>> {
  const headers = new Headers({ Accept: 'application/json' })
  if (etag) {
    headers.set('If-None-Match', etag)
  }
  const response = await performFetch(path, { headers })
  if (response.status === 304) {
    return { notModified: true, data: null, etag }
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return {
    notModified: false,
    data: await parseBody<T>(response),
    etag: response.headers.get('ETag'),
  }
}

function buildHeaders(init: RequestInit): Headers {
  const headers = new Headers(init.headers)
  if (typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

async function toApiError(response: Response): Promise<ApiError> {
  const body = await readErrorBody(response)
  const code = body?.error ?? body?.code ?? 'request_failed'
  const message = body?.message ?? FALLBACK_MESSAGES[response.status] ?? 'Произошла ошибка'
  return new ApiError(response.status, code, message)
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}

async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  if (text.length === 0) {
    return undefined as T
  }
  return JSON.parse(text) as T
}
