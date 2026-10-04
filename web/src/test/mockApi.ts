import { vi } from 'vitest'
import { DEFAULT_PREFERENCES, mergePreferences, normalizePreferences } from '../api/preferences'
import type {
  CalendarEntry,
  CreatedUser,
  Event,
  EventDetail,
  Me,
  Note,
  NoteAttachmentInfo,
  NotesResponse,
  Preferences,
  Profile,
  QueueEntry,
  QueueResponse,
  SiteInfo,
  StudentProfile,
  UpdatePreferencesInput,
  User,
} from '../api/types'

export interface MockRequest {
  url: string
  method: string
  body: unknown
  headers: Headers
}

export type MockHandler = (request: MockRequest) => Response | undefined

/** Stateful configuration of the site icon endpoints used by the icon tests. */
export interface SiteIconMockOptions {
  /** Whether an icon exists; a function makes it dynamic across upload/reset. */
  exists?: boolean | (() => boolean)
  contentType?: string
  sizeBytes?: number
  etag?: string
  /** `PUT /api/admin/site/icon` — receives the multipart `FormData`. */
  onUpload?: (formData: FormData | undefined) => Response
  /** `DELETE /api/admin/site/icon` — defaults to an idempotent `204`. */
  onReset?: () => Response
}

export function jsonResponse(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

export interface ApiMockOptions {
  me?: Me | null | (() => Me | null)
  site?: SiteInfo
  /** Site icon state for `GET /api/site/icon` and the admin upload/reset. */
  siteIcon?: SiteIconMockOptions
  events?: Event[]
  /** `GET /api/events/history` — the server already filters by role. */
  eventHistory?: Event[]
  /** `GET /api/events/by-slug/{slug}` — defaults to `404`. */
  eventBySlug?: EventDetail | ((slug: string) => Response)
  /**
   * `GET /api/events/{id}` — event detail carrying the journal when visible;
   * defaults to `404`. The by-slug projection never includes the journal, so the
   * journal screen resolves the slug first and then loads the detail by id.
   */
  eventById?: EventDetail | ((id: string) => Response)
  /** `GET /api/events/{id}/queue` — defaults to an empty `OPEN` queue. */
  queue?: QueueResponse | ((eventId: string, request: MockRequest) => Response)
  users?: User[]
  studentProfile?: StudentProfile
  /**
   * Initial per-account preferences for `GET /api/students/me/preferences`
   * (defaults when omitted). The mock keeps them in memory, so a successful
   * `PATCH` is visible on the next `GET` (the settings tests rely on that).
   */
  preferences?: Preferences
  /** `PATCH /api/students/me/preferences` — defaults to a stateful merge. */
  updatePreferences?: (body: unknown) => Response
  login?: (body: { username?: string; password?: string }) => Response
  /** `POST /api/auth/refresh` — defaults to a successful rotation. */
  refresh?: () => Response
  guest?: Me
  password?: (body: { oldPassword?: string; newPassword?: string }) => Response
  createUser?: (body: unknown) => Response
  resetPassword?: (id: string) => Response
  updateSite?: (body: { name?: string }) => Response
  /** `POST /api/events` — defaults to `201` with an event built from the body. */
  createEvent?: (body: unknown) => Response
  /** `PATCH /api/events/{id}` — defaults to `200` with an event built from the body. */
  updateEvent?: (id: string, body: unknown) => Response
  /** `GET /api/calendar?from=&to=` — the server already filters by role/audience. */
  calendar?: CalendarEntry[] | ((from: string, to: string) => Response)
  /** `POST /api/calendar` — defaults to `201` with an entry built from the body. */
  createCalendarEntry?: (body: unknown) => Response
  /** `PATCH /api/calendar/{id}` — defaults to `200` with an entry built from the body. */
  updateCalendarEntry?: (id: string, body: unknown) => Response
  /** `DELETE /api/calendar/{id}` — defaults to `204`. */
  deleteCalendarEntry?: (id: string) => Response
  /**
   * `GET /api/notes` — a function makes the response dynamic across
   * create/update/upload, which the notes tests rely on.
   */
  notes?: NotesResponse | (() => NotesResponse)
  /** `POST /api/notes` — defaults to `201` with a note built from the body. */
  createNote?: (body: unknown) => Response
  /** `PATCH /api/notes/{id}` — defaults to `200` with a note built from the body. */
  updateNote?: (id: string, body: unknown) => Response
  /** `DELETE /api/notes/{id}` — defaults to `204`. */
  deleteNote?: (id: string) => Response
  /** `POST /api/notes/{id}/attachments` — receives the multipart `FormData`. */
  uploadNoteAttachment?: (noteId: string, formData: FormData | undefined) => Response
  /** `DELETE /api/notes/{id}/attachments/{attachmentId}` — defaults to `204`. */
  deleteNoteAttachment?: (noteId: string, attachmentId: string) => Response
  /** Custom handler, checked before every built-in; return `undefined` to fall through. */
  handler?: MockHandler
}

const DEFAULT_GUEST: Me = {
  subject: 'guest:00000000-0000-0000-0000-000000000000',
  roles: ['GUEST'],
  displayName: null,
  mustChangePassword: false,
  guest: true,
}

const DEFAULT_PROFILE: Profile = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'student',
  displayName: 'Студент',
  role: 'STUDENT',
  mustChangePassword: false,
}

function defaultQueue(eventId: string): QueueResponse {
  return {
    eventId,
    eventStatus: 'OPEN',
    entryLimit: 27,
    entryUnit: 'BRIGADE',
    queue: [],
  }
}

/** Minimal event payload used by the create/update defaults. */
export function makeMockEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 'created-event',
    title: 'Новое событие',
    description: null,
    availability: 'GUEST+',
    startsAt: null,
    entryLimit: 27,
    entryUnit: 'BRIGADE',
    journalVisibility: 'STAFF',
    retentionDays: 14,
    slug: 'new-event',
    status: 'OPEN',
    groupId: 'g1',
    createdAt: new Date(0).toISOString(),
    updatedAt: new Date(0).toISOString(),
    ...overrides,
  }
}

/** Minimal calendar entry payload used by the create default and the tests. */
export function makeMockCalendarEntry(overrides: Partial<CalendarEntry> = {}): CalendarEntry {
  return {
    id: 'calendar-entry',
    title: 'Мероприятие',
    description: null,
    startsOn: '2026-10-07',
    startsAt: null,
    audience: 'ME',
    authorAccountId: 'author',
    authorDisplayName: null,
    mine: true,
    ...overrides,
  }
}

/** Minimal note payload used by the create/update defaults and the tests. */
export function makeMockNote(overrides: Partial<Note> = {}): Note {
  return {
    id: 'note-1',
    title: 'Заметка',
    body: null,
    createdAt: new Date(0).toISOString(),
    updatedAt: new Date(0).toISOString(),
    attachments: [],
    ...overrides,
  }
}

/** Minimal attachment payload used by the upload default and the tests. */
export function makeMockNoteAttachment(overrides: Partial<NoteAttachmentInfo> = {}): NoteAttachmentInfo {
  return {
    id: 'attachment-1',
    fileName: 'file.txt',
    contentType: 'text/plain',
    sizeBytes: 5,
    createdAt: new Date(0).toISOString(),
    ...overrides,
  }
}

function makeEntry(name: string): QueueEntry {
  return {
    id: `entry-${name}`,
    position: 1,
    name,
    status: 'WAITING',
    origin: 'JOIN',
    holderAccountId: null,
    guestRef: null,
    passedAt: null,
    createdAt: new Date(0).toISOString(),
  }
}

/**
 * Replaces `global.fetch` with a deterministic router for the endpoints the
 * frontend uses. Unmocked requests throw so tests fail loudly on accidental
 * network access.
 */
export function installApiMock(options: ApiMockOptions = {}): void {
  const resolveMe = (): Me | null => {
    if (typeof options.me === 'function') {
      return options.me()
    }
    return options.me ?? null
  }

  // Stateful preferences so an optimistic PATCH is confirmed by the next GET.
  let preferencesState: Preferences = normalizePreferences(options.preferences ?? DEFAULT_PREFERENCES)

  const handlers: MockHandler[] = [
    (request) => {
      if (request.method === 'GET' && request.url === '/api/site') {
        return jsonResponse(200, options.site ?? { name: 'NSTU System' })
      }
      return undefined
    },
    (request) => {
      if ((request.method === 'GET' || request.method === 'HEAD') && request.url === '/api/site/icon') {
        const icon = options.siteIcon
        const exists =
          typeof icon?.exists === 'function' ? icon.exists() : icon?.exists ?? false
        if (!exists) {
          return new Response(null, { status: 404 })
        }
        const headers: Record<string, string> = {
          'Content-Type': icon?.contentType ?? 'image/png',
          ETag: icon?.etag ?? '"test-icon"',
        }
        if (icon?.sizeBytes !== undefined) {
          headers['Content-Length'] = String(icon.sizeBytes)
        }
        return new Response(null, { status: 200, headers })
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/auth/me') {
        const me = resolveMe()
        return me
          ? jsonResponse(200, me)
          : jsonResponse(401, { error: 'unauthorized', message: 'Требуется вход' })
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/auth/login') {
        return options.login
          ? options.login(request.body as { username?: string; password?: string })
          : jsonResponse(200, DEFAULT_PROFILE)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/auth/refresh') {
        return options.refresh ? options.refresh() : jsonResponse(200, DEFAULT_PROFILE)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/auth/guest') {
        return jsonResponse(200, options.guest ?? DEFAULT_GUEST)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/auth/logout') {
        return jsonResponse(200, undefined)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/auth/password') {
        if (options.password) {
          return options.password(request.body as { oldPassword?: string; newPassword?: string })
        }
        return jsonResponse(200, DEFAULT_PROFILE)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url.startsWith('/api/calendar')) {
        const url = new URL(request.url, 'http://localhost')
        if (url.pathname !== '/api/calendar') {
          return undefined
        }
        const from = url.searchParams.get('from') ?? ''
        const to = url.searchParams.get('to') ?? ''
        if (typeof options.calendar === 'function') {
          return options.calendar(from, to)
        }
        return jsonResponse(200, options.calendar ?? [])
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/calendar') {
        if (options.createCalendarEntry) {
          return options.createCalendarEntry(request.body)
        }
        const body = (request.body ?? {}) as Partial<CalendarEntry>
        return jsonResponse(201, makeMockCalendarEntry(body))
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/calendar\/([^/]+)$/.exec(request.url)
      if (!match) {
        return undefined
      }
      const id = decodeURIComponent(match[1])
      if (request.method === 'PATCH') {
        if (options.updateCalendarEntry) {
          return options.updateCalendarEntry(id, request.body)
        }
        const body = (request.body ?? {}) as Partial<CalendarEntry>
        return jsonResponse(200, makeMockCalendarEntry({ ...body, id }))
      }
      if (request.method === 'DELETE') {
        if (options.deleteCalendarEntry) {
          return options.deleteCalendarEntry(id)
        }
        return jsonResponse(204, undefined)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/notes') {
        const notes = typeof options.notes === 'function' ? options.notes() : options.notes
        return jsonResponse(
          200,
          notes ?? { notes: [], quota: { usedBytes: 0, limitBytes: 10485760 } },
        )
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/notes') {
        if (options.createNote) {
          return options.createNote(request.body)
        }
        const body = (request.body ?? {}) as { title?: string; body?: string | null }
        return jsonResponse(
          201,
          makeMockNote({ id: 'created-note', title: (body.title ?? '').trim(), body: body.body ?? null }),
        )
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/notes\/([^/]+)\/attachments$/.exec(request.url)
      if (match && request.method === 'POST') {
        const noteId = decodeURIComponent(match[1])
        if (options.uploadNoteAttachment) {
          return options.uploadNoteAttachment(noteId, request.body as FormData | undefined)
        }
        return jsonResponse(201, makeMockNoteAttachment())
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/notes\/([^/]+)\/attachments\/([^/]+)$/.exec(request.url)
      if (!match) {
        return undefined
      }
      const noteId = decodeURIComponent(match[1])
      const attachmentId = decodeURIComponent(match[2])
      if (request.method === 'DELETE') {
        if (options.deleteNoteAttachment) {
          return options.deleteNoteAttachment(noteId, attachmentId)
        }
        return jsonResponse(204, undefined)
      }
      if (request.method === 'GET') {
        return new Response('attachment', {
          status: 200,
          headers: { 'Content-Type': 'application/octet-stream' },
        })
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/notes\/([^/]+)$/.exec(request.url)
      if (!match) {
        return undefined
      }
      const noteId = decodeURIComponent(match[1])
      if (request.method === 'PATCH') {
        if (options.updateNote) {
          return options.updateNote(noteId, request.body)
        }
        const body = (request.body ?? {}) as { title?: string; body?: string | null }
        return jsonResponse(
          200,
          makeMockNote({ id: noteId, title: body.title ?? 'Заметка', body: body.body ?? null }),
        )
      }
      if (request.method === 'DELETE') {
        if (options.deleteNote) {
          return options.deleteNote(noteId)
        }
        return jsonResponse(204, undefined)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/events') {
        return jsonResponse(200, options.events ?? [])
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/events') {
        if (options.createEvent) {
          return options.createEvent(request.body)
        }
        const body = (request.body ?? {}) as Partial<Event>
        return jsonResponse(201, makeMockEvent(body))
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/events/history') {
        return jsonResponse(200, options.eventHistory ?? [])
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/events\/by-slug\/([^/]+)$/.exec(request.url)
      if (!match || request.method !== 'GET') {
        return undefined
      }
      if (typeof options.eventBySlug === 'function') {
        return options.eventBySlug(decodeURIComponent(match[1]))
      }
      if (options.eventBySlug) {
        return jsonResponse(200, options.eventBySlug)
      }
      return jsonResponse(404, { error: 'event_not_found', message: 'Событие не найдено' })
    },
    (request) => {
      const match = /^\/api\/events\/([^/]+)$/.exec(request.url)
      if (!match || request.method !== 'GET') {
        return undefined
      }
      const eventId = decodeURIComponent(match[1])
      if (typeof options.eventById === 'function') {
        return options.eventById(eventId)
      }
      if (options.eventById) {
        return jsonResponse(200, options.eventById)
      }
      return jsonResponse(404, { error: 'event_not_found', message: 'Событие не найдено' })
    },
    (request) => {
      const match = /^\/api\/events\/([^/]+)\/queue$/.exec(request.url)
      if (!match || request.method !== 'GET') {
        return undefined
      }
      const eventId = decodeURIComponent(match[1])
      if (typeof options.queue === 'function') {
        return options.queue(eventId, request)
      }
      const body = options.queue ?? defaultQueue(eventId)
      const etag = `"q-${body.eventId}"`
      if (request.headers.get('If-None-Match') === etag) {
        return new Response(null, { status: 304, headers: { ETag: etag } })
      }
      return jsonResponse(200, body, { ETag: etag })
    },
    (request) => {
      const match = /^\/api\/events\/([^/]+)\/queue$/.exec(request.url)
      if (!match || request.method !== 'POST') {
        return undefined
      }
      const name = (request.body as { name?: string } | undefined)?.name ?? 'Новая запись'
      return jsonResponse(201, makeEntry(name))
    },
    (request) => {
      if (/^\/api\/events\/[^/]+\/queue\/advance$/.test(request.url) && request.method === 'POST') {
        return jsonResponse(200, undefined)
      }
      return undefined
    },
    (request) => {
      if (
        /^\/api\/events\/[^/]+\/queue\/[^/]+\/(pause|resume)$/.test(request.url) &&
        request.method === 'POST'
      ) {
        return jsonResponse(200, makeEntry('Запись'))
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/events\/[^/]+\/queue\/[^/]+\/position$/.test(request.url) && request.method === 'PATCH') {
        return jsonResponse(200, makeEntry('Запись'))
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/events\/[^/]+\/queue\/[^/]+$/.test(request.url) && request.method === 'DELETE') {
        return jsonResponse(204, undefined)
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/events\/[^/]+\/queue\/staff$/.test(request.url) && request.method === 'POST') {
        const name = (request.body as { name?: string } | undefined)?.name ?? 'Запись'
        return jsonResponse(201, makeEntry(name))
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/events\/[^/]+\/carry-over$/.test(request.url) && request.method === 'POST') {
        return jsonResponse(200, { added: [], skipped: [] })
      }
      return undefined
    },
    (request) => {
      if (
        /^\/api\/events\/[^/]+\/(close|open|archive|restore)$/.test(request.url) &&
        request.method === 'POST'
      ) {
        return jsonResponse(200, options.events?.[0] ?? { id: 'event', status: 'OPEN' })
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/events\/([^/]+)$/.exec(request.url)
      if (match && request.method === 'PATCH') {
        if (options.updateEvent) {
          return options.updateEvent(decodeURIComponent(match[1]), request.body)
        }
        const body = (request.body ?? {}) as Partial<Event>
        return jsonResponse(200, makeMockEvent({ id: decodeURIComponent(match[1]), ...body }))
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/events\/[^/]+$/.test(request.url) && request.method === 'DELETE') {
        return jsonResponse(204, undefined)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/users') {
        return jsonResponse(200, options.users ?? [])
      }
      return undefined
    },
    (request) => {
      if (request.method === 'POST' && request.url === '/api/users') {
        if (options.createUser) {
          return options.createUser(request.body)
        }
        const body = request.body as { username?: string; role?: string; displayName?: string }
        const created: CreatedUser = {
          id: 'created-user',
          username: body.username ?? 'user',
          displayName: body.displayName ?? 'Пользователь',
          email: null,
          role: body.role ?? 'STUDENT',
          blocked: false,
          mustChangePassword: true,
          temporaryPassword: 'Temp-1234-Abc',
        }
        return jsonResponse(201, created)
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/users\/([^/]+)$/.exec(request.url)
      if (match && request.method === 'PATCH') {
        return jsonResponse(200, { id: decodeURIComponent(match[1]), ...(request.body as object) })
      }
      return undefined
    },
    (request) => {
      if (/^\/api\/users\/[^/]+\/(block|unblock)$/.test(request.url) && request.method === 'POST') {
        return jsonResponse(200, undefined)
      }
      return undefined
    },
    (request) => {
      const match = /^\/api\/users\/([^/]+)\/reset-password$/.exec(request.url)
      if (match && request.method === 'POST') {
        if (options.resetPassword) {
          return options.resetPassword(decodeURIComponent(match[1]))
        }
        return jsonResponse(200, { temporaryPassword: 'Reset-99-Zz' })
      }
      return undefined
    },
    (request) => {
      if (request.method === 'PUT' && request.url === '/api/admin/site') {
        if (options.updateSite) {
          return options.updateSite(request.body as { name?: string })
        }
        const body = request.body as { name?: string }
        return jsonResponse(200, { name: body.name ?? 'Site' })
      }
      return undefined
    },
    (request) => {
      if (request.method === 'PUT' && request.url === '/api/admin/site/icon') {
        if (options.siteIcon?.onUpload) {
          return options.siteIcon.onUpload(request.body as FormData | undefined)
        }
        return jsonResponse(200, {
          contentType: 'image/png',
          sizeBytes: 12,
          updatedAt: new Date(0).toISOString(),
          updatedBy: null,
        })
      }
      return undefined
    },
    (request) => {
      if (request.method === 'DELETE' && request.url === '/api/admin/site/icon') {
        if (options.siteIcon?.onReset) {
          return options.siteIcon.onReset()
        }
        return jsonResponse(204, undefined)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/students/me/preferences') {
        return jsonResponse(200, preferencesState)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'PATCH' && request.url === '/api/students/me/preferences') {
        if (options.updatePreferences) {
          return options.updatePreferences(request.body)
        }
        const patch = (request.body ?? {}) as UpdatePreferencesInput
        preferencesState = mergePreferences(preferencesState, patch)
        return jsonResponse(200, preferencesState)
      }
      return undefined
    },
    (request) => {
      if (request.method === 'GET' && request.url === '/api/students/me') {
        return options.studentProfile
          ? jsonResponse(200, options.studentProfile)
          : jsonResponse(404, { error: 'profile_not_found', message: 'Профиль не найден' })
      }
      return undefined
    },
  ]

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
      const method = (init?.method ?? 'GET').toUpperCase()
      const body =
        typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : init?.body
      const headers = new Headers(init?.headers)
      const request: MockRequest = { url, method, body, headers }
      if (options.handler) {
        const custom = options.handler(request)
        if (custom) {
          return custom
        }
      }
      for (const handler of handlers) {
        const response = handler(request)
        if (response) {
          return response
        }
      }
      throw new Error(`Unmocked request: ${method} ${url}`)
    }),
  )
}
