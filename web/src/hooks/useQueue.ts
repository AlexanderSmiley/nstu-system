import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/http'
import { fetchQueue } from '../api/queue'
import { eventQueueQueryKey } from '../api/queryKeys'
import type { QueueResponse } from '../api/types'

export interface QueueQueryData {
  response: QueueResponse
  etag: string | null
}

export interface UseQueueOptions {
  /**
   * Suspends the 5-second polling while a queue mutation is in progress
   * (design.md D4). One-off refetches triggered by `invalidateQueries` still run.
   */
  paused?: boolean
}

/**
 * Polls `GET /api/events/{id}/queue` every 5 seconds (design.md D20). The
 * previous payload is kept on `304 Not Modified`, so an unchanged queue does not
 * re-render the list. Polling can be suspended with `paused` while a drag is in
 * flight so the periodic refresh does not overwrite the optimistic order.
 */
export function useQueue(eventId: string | undefined, options: UseQueueOptions = {}) {
  const queryClient = useQueryClient()
  const { paused = false } = options

  return useQuery({
    queryKey: eventQueueQueryKey(eventId ?? ''),
    enabled: Boolean(eventId),
    queryFn: async (): Promise<QueueQueryData> => {
      const id = eventId as string
      const key = eventQueueQueryKey(id)
      const previous = queryClient.getQueryData<QueueQueryData>(key)
      const result = await fetchQueue(id, previous?.etag ?? null)
      if (result.notModified && previous) {
        return previous
      }
      return { response: result.data as QueueResponse, etag: result.etag }
    },
    refetchInterval: paused ? false : 5000,
    retry: (failureCount, error) =>
      failureCount < 1 && !(error instanceof ApiError && error.status < 500),
  })
}
