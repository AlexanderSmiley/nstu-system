import { ApiError, request } from './http'
import type { Me, Profile } from './types'

/**
 * `GET /api/auth/me`. A 401 means "no session" rather than an error, so it is
 * mapped to `null` to keep the guard from treating an anonymous visitor as a
 * failure.
 */
export function fetchMe(): Promise<Me | null> {
  return request<Me>('/api/auth/me').catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  })
}

export function login(username: string, password: string): Promise<Profile> {
  return request<Profile>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function loginAsGuest(): Promise<Me> {
  return request<Me>('/api/auth/guest', { method: 'POST' })
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}

export function changePassword(oldPassword: string, newPassword: string): Promise<Profile> {
  return request<Profile>('/api/auth/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}
