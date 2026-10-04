import type { ReactNode } from 'react'
import type { Role } from '../api/types'
import { useSession } from '../auth/session'
import { ForbiddenPage } from '../pages/ForbiddenPage'

interface RequireRoleProps {
  role: Role
  children: ReactNode
}

/** Privilege order used by the guards (`ADMIN` passes every `STAFF+` guard). */
const ROLE_RANK: Record<Role, number> = {
  GUEST: 0,
  STUDENT: 1,
  STAFF: 2,
  ADMIN: 3,
}

/**
 * Client-side UX guard; the server remains authoritative (access spec D10).
 * A role below the required one renders the 403 screen instead of silently
 * redirecting; a higher role passes by hierarchy (design.md D1).
 */
export function RequireRole({ role, children }: RequireRoleProps) {
  const { role: currentRole } = useSession()

  if (currentRole === null || ROLE_RANK[currentRole] < ROLE_RANK[role]) {
    return <ForbiddenPage />
  }

  return <>{children}</>
}
