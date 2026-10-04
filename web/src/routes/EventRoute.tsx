import { useSession } from '../auth/session'
import { AppLayout } from '../components/layout/AppLayout'
import { EventPage } from '../pages/EventPage'
import { LoginPage } from '../pages/LoginPage'

/**
 * `/e/:slug` — the short event link. An anonymous visitor sees the login screen
 * without any event content; an authenticated user (including a guest) gets the
 * event page (design.md D25).
 */
export function EventRoute() {
  const { isAuthenticated } = useSession()

  if (!isAuthenticated) {
    return <LoginPage />
  }

  return (
    <AppLayout>
      <EventPage />
    </AppLayout>
  )
}
