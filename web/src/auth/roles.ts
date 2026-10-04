import type { Role } from '../api/types'

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Администратор',
  STAFF: 'Персонал',
  STUDENT: 'Студент',
  GUEST: 'Гость',
}

export function roleLabel(role: Role | null): string {
  return role ? ROLE_LABELS[role] : 'Неизвестно'
}

/** Staff and administrators manage events and queues (access spec D10). */
export function isStaffRole(role: Role | null): boolean {
  return role === 'STAFF' || role === 'ADMIN'
}
