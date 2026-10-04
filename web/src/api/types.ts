/** Application role names as returned by the backend (without the ROLE_ prefix). */
export type Role = 'GUEST' | 'STUDENT' | 'STAFF' | 'ADMIN'

/** Body of `GET /api/auth/me`. */
export interface Me {
  subject: string
  roles: string[]
  displayName: string | null
  mustChangePassword: boolean
  guest: boolean
  /** Additive field; absent on older servers, so it must stay optional. */
  username?: string
  /** Additive field; absent on older servers, so it must stay optional. */
  email?: string | null
  /** Additive single-role field; `roles[]` remains the source of truth. */
  role?: string
}

/** Body of `POST /api/auth/login|refresh|password`. */
export interface Profile {
  id: string
  username: string
  displayName: string
  role: string
  mustChangePassword: boolean
}

/** Body of `GET /api/site`. */
export interface SiteInfo {
  name: string
}

/**
 * Metadata of the stored site icon (`GET /api/site/icon` and the admin upload).
 * `updatedAt`/`updatedBy` come from the upload response; `etag` from the public
 * GET and is used to bust the browser cache when the icon changes.
 */
export interface SiteIconInfo {
  contentType: string | null
  sizeBytes: number | null
  etag?: string | null
  updatedAt?: string | null
  updatedBy?: string | null
}

/** Event lifecycle status (design.md D15). */
export type EventStatus = 'OPEN' | 'CLOSED' | 'ARCHIVED'

/** Entry availability level of an event. */
export type EventAvailability = 'GUEST+' | 'STUDENT+' | 'STAFF+'

/** Unit a queue entry represents. */
export type EntryUnit = 'BRIGADE' | 'PERSON'

/** Who may see the event journal (event-management spec). */
export type JournalVisibility = 'STAFF' | 'EVERYONE'

/**
 * Body of `POST /api/events` (design.md D2). Only `title` is required; omitted
 * optional fields fall back to the server defaults (limit 27, unit `BRIGADE`,
 * availability `GUEST+`, journal `STAFF`, retention 14).
 */
export interface CreateEventInput {
  title: string
  description?: string | null
  availability?: string
  startsAt?: string | null
  entryLimit?: number
  entryUnit?: string
  journalVisibility?: JournalVisibility
  retentionDays?: number
  /** Ignored by the server on create (the slug is generated); kept for parity. */
  slug?: string
}

/**
 * Body of `PATCH /api/events/{id}` (design.md D2). An absent field means "leave
 * unchanged"; `slug` and `retentionDays` are accepted from administrators only.
 */
export interface UpdateEventInput {
  title?: string
  description?: string | null
  availability?: string
  startsAt?: string | null
  entryLimit?: number
  entryUnit?: string
  journalVisibility?: JournalVisibility
  retentionDays?: number
  slug?: string
}

/** One element of `GET /api/events`. */
export interface Event {
  id: string
  title: string
  description: string | null
  availability: string
  startsAt: string | null
  entryLimit: number
  entryUnit: string
  journalVisibility: JournalVisibility
  retentionDays: number
  slug: string
  status: string
  groupId: string
  createdAt: string
  updatedAt: string
  /** Present on history/archived payloads (design.md D15). */
  closedAt?: string | null
  /** Present on archived payloads (design.md D15). */
  archivedAt?: string | null
}

/** Status of a queue entry. */
export type QueueEntryStatus = 'WAITING' | 'PAUSED' | 'PASSED'

/** How an entry was created. */
export type QueueEntryOrigin = 'JOIN' | 'STAFF' | 'CARRY_OVER'

/** One element of the queue/journal returned by `GET /api/events/{id}/queue`. */
export interface QueueEntry {
  id: string
  position: number
  name: string
  status: QueueEntryStatus
  origin: QueueEntryOrigin
  holderAccountId: string | null
  guestRef: string | null
  passedAt: string | null
  createdAt: string
}

/** Body of `GET /api/events/{id}/queue`. */
export interface QueueResponse {
  eventId: string
  eventStatus: string
  entryLimit: number
  entryUnit: string
  queue: QueueEntry[]
  /** Omitted when the current user may not see the journal. */
  journal?: QueueEntry[]
}

/** `Event` plus the journal, when the caller is allowed to see it. */
export interface EventDetail extends Event {
  journal?: QueueEntry[]
}

/** Item of the `added` array returned by `POST /api/events/{id}/carry-over`. */
export interface CarryOverAdded {
  sourceEntryId: string
  targetEntryId: string
  name: string
}

/** Item of the `skipped` array returned by `POST /api/events/{id}/carry-over`. */
export interface CarryOverSkipped {
  sourceEntryId: string
  name: string
  reason: string
}

/** Body of `POST /api/events/{id}/carry-over`. */
export interface CarryOverResult {
  added: CarryOverAdded[]
  skipped: CarryOverSkipped[]
}

/** Account row of `GET /api/users`. */
export interface User {
  id: string
  username: string
  displayName: string
  email: string | null
  role: string
  blocked: boolean
  mustChangePassword: boolean
  createdAt?: string
  updatedAt?: string
}

/** Body of `POST /api/users` — `temporaryPassword` is shown exactly once. */
export interface CreatedUser extends User {
  temporaryPassword: string
}

/** Input for `POST /api/users`. */
export interface CreateUserInput {
  username: string
  displayName: string
  email?: string
  role: 'STAFF' | 'STUDENT'
}

/** Input for `PATCH /api/users/{id}`. */
export interface UpdateUserInput {
  displayName?: string
  email?: string
  role?: 'STAFF' | 'STUDENT'
}

/** Body of `GET /api/students/me` (student and staff roles only). */
export interface StudentProfile {
  accountId: string
  fullName: string
  groupId: string | null
  /** Additive field: display name of the group; `null` when unavailable. */
  groupName?: string | null
  contacts: Record<string, unknown> | null
}

/** Audience of a calendar entry (change add-calendar-module). */
export type CalendarAudience = 'ME' | 'GROUP' | 'STAFF'

/** One element of `GET /api/calendar` (dates are plain ISO strings, no time zone). */
export interface CalendarEntry {
  id: string
  title: string
  description: string | null
  startsOn: string
  startsAt: string | null
  audience: CalendarAudience
  authorAccountId: string
  /**
   * Display name of the author snapshotted at creation
   * (change add-preferences-and-calendar-ui; design.md D5). `null` when it could
   * not be resolved; the card then shows a dash instead of a name.
   */
  authorDisplayName: string | null
  /** `true` when the caller is the author; drives the edit/delete actions. */
  mine: boolean
}

/**
 * Body of `POST /api/calendar`. `from`/`to` are the calendar window currently
 * displayed by the client; the server rejects a `startsOn` outside of it with
 * `invalid_date`, which is how the spec's "date must be in the shown window"
 * rule is enforced server-side.
 */
export interface CreateCalendarEntryInput {
  title: string
  description?: string | null
  startsOn: string
  startsAt?: string | null
  audience: CalendarAudience
  from: string
  to: string
}

/**
 * Body of `PATCH /api/calendar/{id}` (design.md D6). Every field is optional; the
 * client always sends the displayed window (`from`/`to`) so the server can apply
 * the same "date inside the shown window" rule as on creation.
 */
export interface UpdateCalendarEntryInput {
  title?: string
  description?: string | null
  startsOn?: string
  startsAt?: string | null
  audience?: CalendarAudience
  from: string
  to: string
}

/** Module identifiers persisted in the per-account preferences. */
export type ModuleId = 'events' | 'calendar' | 'notes'

/** Per-account UI preferences returned by `GET/PATCH /api/students/me/preferences`. */
export interface Preferences {
  /** Enabled state of every home-screen module. */
  modules: Record<ModuleId, boolean>
  /** Soft fill colour keyed by calendar audience (change D4). */
  calendarColors: Record<CalendarAudience, string>
}

/** Partial update for `PATCH /api/students/me/preferences` (design.md D3). */
export interface UpdatePreferencesInput {
  modules?: Partial<Record<ModuleId, boolean>>
  calendarColors?: Partial<Record<CalendarAudience, string>>
}

/** Metadata of one attachment of a note (change add-notes-module). */
export interface NoteAttachmentInfo {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

/**
 * One personal note of `GET /api/notes`. The `body` is plain text and must be
 * rendered as text (`white-space: pre-wrap`), never as markup.
 */
export interface Note {
  id: string
  title: string
  body: string | null
  createdAt: string
  updatedAt: string
  attachments: NoteAttachmentInfo[]
}

/** Attachment storage usage of the account. */
export interface NotesQuota {
  usedBytes: number
  limitBytes: number
}

/** Body of `GET /api/notes`. */
export interface NotesResponse {
  notes: Note[]
  quota: NotesQuota
}

/** Input for `POST /api/notes`. */
export interface CreateNoteInput {
  title: string
  body?: string | null
}

/** Input for `PATCH /api/notes/{id}`; an absent field keeps its current value. */
export interface UpdateNoteInput {
  title?: string
  body?: string | null
}
