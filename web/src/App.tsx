import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { AdminEventsPage } from './pages/AdminEventsPage'
import { AdminGeneralPage } from './pages/AdminGeneralPage'
import { AdminPage } from './pages/AdminPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { CreateEventPage } from './pages/CreateEventPage'
import { EventHistoryPage } from './pages/EventHistoryPage'
import { EventJournalPage } from './pages/EventJournalPage'
import { EventSettingsPage } from './pages/EventSettingsPage'
import { EventsPage } from './pages/EventsPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { PasswordChangePage } from './pages/PasswordChangePage'
import { ProfilePage } from './pages/ProfilePage'
import { SettingsPage } from './pages/SettingsPage'
import { EventRoute } from './routes/EventRoute'
import { RequireAuth } from './routes/RequireAuth'
import { RequireRole } from './routes/RequireRole'
import { SessionGate } from './routes/SessionGate'

function App() {
  return (
    <SessionGate>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/password/change" element={<PasswordChangePage />} />
        <Route path="/e/:slug" element={<EventRoute />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route index element={<HomePage />} />
            <Route path="events" element={<EventsPage />} />
            <Route path="events/history" element={<EventHistoryPage />} />
            <Route
              path="events/new"
              element={
                <RequireRole role="STAFF">
                  <CreateEventPage />
                </RequireRole>
              }
            />
            <Route
              path="e/:slug/settings"
              element={
                <RequireRole role="STAFF">
                  <EventSettingsPage />
                </RequireRole>
              }
            />
            {/* Journal visibility is decided by the event at page level (design.md D1). */}
            <Route path="e/:slug/journal" element={<EventJournalPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route
              path="admin"
              element={
                <RequireRole role="ADMIN">
                  <AdminPage />
                </RequireRole>
              }
            >
              <Route index element={<Navigate to="/admin/general" replace />} />
              <Route path="general" element={<AdminGeneralPage />} />
              <Route path="users" element={<AdminUsersPage />} />
              <Route path="events" element={<AdminEventsPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </SessionGate>
  )
}

export default App
