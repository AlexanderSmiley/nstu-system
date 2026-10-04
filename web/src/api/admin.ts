import { request } from './http'
import type { CreatedUser, CreateUserInput, SiteInfo, UpdateUserInput, User } from './types'

/** `PUT /api/admin/site` — admin only; the new name is applied across all screens. */
export function updateSiteName(name: string): Promise<SiteInfo> {
  return request<SiteInfo>('/api/admin/site', {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

/** `GET /api/users` — admin only. */
export function fetchUsers(): Promise<User[]> {
  return request<User[]>('/api/users')
}

/** `POST /api/users` — admin only; returns the one-time temporary password. */
export function createUser(input: CreateUserInput): Promise<CreatedUser> {
  return request<CreatedUser>('/api/users', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** `PATCH /api/users/{id}` — admin only. */
export function updateUser(id: string, patch: UpdateUserInput): Promise<User> {
  return request<User>(`/api/users/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  })
}

/** `POST /api/users/{id}/block` — admin only. */
export function blockUser(id: string): Promise<void> {
  return request<void>(`/api/users/${encodeURIComponent(id)}/block`, { method: 'POST' })
}

/** `POST /api/users/{id}/unblock` — admin only. */
export function unblockUser(id: string): Promise<void> {
  return request<void>(`/api/users/${encodeURIComponent(id)}/unblock`, { method: 'POST' })
}

/** `POST /api/users/{id}/reset-password` — admin only; returns a new one-time password. */
export function resetUserPassword(id: string): Promise<{ temporaryPassword: string }> {
  return request<{ temporaryPassword: string }>(
    `/api/users/${encodeURIComponent(id)}/reset-password`,
    { method: 'POST' },
  )
}
