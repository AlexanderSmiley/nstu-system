import { useQuery } from '@tanstack/react-query'
import { DEFAULT_SITE_NAME, fetchSite } from '../api/site'
import { SITE_QUERY_KEY } from '../api/queryKeys'

/** Public site name shown in the header and on the login screen. */
export function useSiteName(): string {
  const query = useQuery({
    queryKey: SITE_QUERY_KEY,
    queryFn: fetchSite,
    staleTime: Infinity,
  })
  return query.data?.name ?? DEFAULT_SITE_NAME
}
