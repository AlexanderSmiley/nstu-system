import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  DEFAULT_PREFERENCES,
  fetchPreferences,
  mergePreferences,
  normalizePreferences,
  updatePreferences,
} from '../api/preferences'
import { PREFERENCES_QUERY_KEY } from '../api/queryKeys'
import type { ModuleId, Preferences, UpdatePreferencesInput } from '../api/types'
import { useSession } from '../auth/session'

interface UsePreferencesResult {
  /** Always a complete object: defaults before the query resolves and for a guest. */
  preferences: Preferences
  /** True while an account's settings are being fetched for the first time. */
  isLoading: boolean
  /** True when the settings request failed and defaults are being shown. */
  isError: boolean
  isModuleEnabled: (id: ModuleId) => boolean
}

/**
 * Reads the caller's UI preferences. A guest never issues the request (a guest
 * has no settings — design.md D3); accounts get defaults until the query
 * resolves, so the shell renders without a spinner.
 */
export function usePreferences(): UsePreferencesResult {
  const { role } = useSession()
  const isAccount = role === 'STUDENT' || role === 'STAFF' || role === 'ADMIN'

  const query = useQuery({
    queryKey: PREFERENCES_QUERY_KEY,
    queryFn: fetchPreferences,
    enabled: isAccount,
    staleTime: 60_000,
  })

  const preferences = query.data ? normalizePreferences(query.data) : DEFAULT_PREFERENCES
  return {
    preferences,
    isLoading: isAccount && query.isPending,
    isError: query.isError,
    isModuleEnabled: (id: ModuleId) => preferences.modules[id] !== false,
  }
}

interface MutationContext {
  previous: Preferences | undefined
}

/**
 * Partial preferences update with an optimistic cache write. A rejected change
 * (e.g. `invalid_color`) rolls the previous values back so the form keeps the
 * stored settings, and the caller can surface the error without losing the
 * other fields.
 */
export function useUpdatePreferences() {
  const queryClient = useQueryClient()

  return useMutation<Preferences, Error, UpdatePreferencesInput, MutationContext>({
    mutationFn: updatePreferences,
    onMutate: async (input) => {
      await queryClient.cancelQueries({ queryKey: PREFERENCES_QUERY_KEY })
      const previous = queryClient.getQueryData<Preferences>(PREFERENCES_QUERY_KEY)
      const base = previous ? normalizePreferences(previous) : DEFAULT_PREFERENCES
      queryClient.setQueryData<Preferences>(PREFERENCES_QUERY_KEY, mergePreferences(base, input))
      return { previous }
    },
    onError: (_error, _input, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(PREFERENCES_QUERY_KEY, context.previous)
      } else {
        queryClient.setQueryData(PREFERENCES_QUERY_KEY, DEFAULT_PREFERENCES)
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: PREFERENCES_QUERY_KEY }),
  })
}
