import { request } from './http'
import type {
  CreateNoteInput,
  Note,
  NoteAttachmentInfo,
  NotesResponse,
  UpdateNoteInput,
} from './types'

/** `GET /api/notes` — the caller's notes together with the storage quota. */
export function fetchNotes(): Promise<NotesResponse> {
  return request<NotesResponse>('/api/notes')
}

/** `POST /api/notes` — creates a note; a blank title yields `invalid_note`. */
export function createNote(input: CreateNoteInput): Promise<Note> {
  return request<Note>('/api/notes', { method: 'POST', body: JSON.stringify(input) })
}

/** `PATCH /api/notes/{id}` — partial update of title and/or body. */
export function updateNote(id: string, input: UpdateNoteInput): Promise<Note> {
  return request<Note>(`/api/notes/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** `DELETE /api/notes/{id}` — deletes the note and all of its attachments. */
export function deleteNote(id: string): Promise<void> {
  return request<void>(`/api/notes/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * `POST /api/notes/{id}/attachments` — multipart upload (field name `file`).
 * `request()` leaves `FormData` bodies untouched and lets the browser set the
 * multipart `Content-Type` (same pattern as the site icon upload).
 */
export function uploadNoteAttachment(noteId: string, file: File): Promise<NoteAttachmentInfo> {
  const body = new FormData()
  body.append('file', file)
  return request<NoteAttachmentInfo>(`${attachmentsPath(noteId)}`, { method: 'POST', body })
}

/** `DELETE /api/notes/{id}/attachments/{attachmentId}`. */
export function deleteNoteAttachment(noteId: string, attachmentId: string): Promise<void> {
  return request<void>(attachmentPath(noteId, attachmentId), { method: 'DELETE' })
}

/**
 * Absolute URL of an attachment download. It is used as a direct link so the
 * browser sends the session cookie and honours the server's
 * `Content-Disposition: attachment` (and the `nosniff` hardening) itself.
 */
export function noteAttachmentDownloadUrl(noteId: string, attachmentId: string): string {
  return attachmentPath(noteId, attachmentId)
}

function attachmentsPath(noteId: string): string {
  return `/api/notes/${encodeURIComponent(noteId)}/attachments`
}

function attachmentPath(noteId: string, attachmentId: string): string {
  return `${attachmentsPath(noteId)}/${encodeURIComponent(attachmentId)}`
}
