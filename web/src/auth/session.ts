import type { QueryClient } from '@tanstack/react-query'
import { useQuery } from '@tanstack/react-query'
import { fetchMe } from '../api/auth'
import { ME_QUERY_KEY } from '../api/queryKeys'
import type { Me, Role } from '../api/types'

/**
 * Marks the client-side session as gone and drops the rest of the React Query
 * cache. Used both by the explicit logout and by the `sessionExpiredHandler`
 * when `POST /api/auth/refresh` is rejected.
 *
 * Order matters: `['me']` is written first so that the guards immediately see
 * an anonymous session and redirect to `/login`, unmounting the protected
 * screens. Only afterwards are the other queries removed. Calling
 * `queryClient.clear()` instead would also destroy the currently observed
 * `['me']` query; its re-mount would refetch `GET /api/auth/me` and could
 * resurrect the dead session (and re-trigger the failing request), leaving the
 * UI stuck on the loader.
 */
export function clearSession(queryClient: QueryClient): void {
  queryClient.setQueryData(ME_QUERY_KEY, null)
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_QUERY_KEY[0] })
}

const ROLE_ORDER: readonly Role[] = ['ADMIN', 'STAFF', 'STUDENT', 'GUEST']

/** Highest-privilege role of the session, or `null` when the roles are unknown. */
export function primaryRole(me: Me | null | undefined): Role | null {
  if (!me) {
    return null
  }
  for (const role of ROLE_ORDER) {
    if (me.roles.includes(role)) {
      return role
    }
  }
  return me.guest ? 'GUEST' : null
}

export interface Session {
  me: Me | null
  role: Role | null
  isLoading: boolean
  isAuthenticated: boolean
  mustChangePassword: boolean
}

/**
 * Single source of truth for the current session, backed by `GET /api/auth/me`.
 * A guest session is authenticated as well: only `null` means "anonymous".
 */
export function useSession(): Session {
  const query = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: fetchMe,
    retry: false,
    staleTime: 30_000,
  })
  const me = query.data ?? null
  return {
    me,
    role: primaryRole(me),
    isLoading: query.isPending,
    isAuthenticated: me !== null,
    mustChangePassword: me?.mustChangePassword === true,
  }
}
