import { request } from './http'
import type { StudentProfile } from './types'

/** `GET /api/students/me` — own profile for student and staff roles. */
export function fetchMyStudentProfile(): Promise<StudentProfile> {
  return request<StudentProfile>('/api/students/me')
}
