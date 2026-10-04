import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchMyStudentProfile } from '../api/students'
import { LOGIN_PROFILE_QUERY_KEY, STUDENT_PROFILE_QUERY_KEY } from '../api/queryKeys'
import type { Profile } from '../api/types'
import { roleLabel } from '../auth/roles'
import { useSession } from '../auth/session'

/** `/profile` — read-only view for STUDENT/STAFF (editing is out of MVP scope). */
export function ProfilePage() {
  const { me, role } = useSession()
  const queryClient = useQueryClient()
  const loginProfile = queryClient.getQueryData<Profile>(LOGIN_PROFILE_QUERY_KEY)

  const profileQuery = useQuery({
    queryKey: STUDENT_PROFILE_QUERY_KEY,
    queryFn: fetchMyStudentProfile,
    retry: false,
    enabled: role === 'STUDENT' || role === 'STAFF',
  })

  return (
    <section className="page">
      <h1>Профиль</h1>
      <dl className="details">
        <div className="details__row">
          <dt>Имя пользователя</dt>
          <dd>{me?.username ?? loginProfile?.username ?? '—'}</dd>
        </div>
        {me?.email && (
          <div className="details__row">
            <dt>Email</dt>
            <dd>{me.email}</dd>
          </div>
        )}
        <div className="details__row">
          <dt>Отображаемое имя</dt>
          <dd>{me?.displayName ?? '—'}</dd>
        </div>
        <div className="details__row">
          <dt>Роль</dt>
          <dd>{roleLabel(role)}</dd>
        </div>
        {profileQuery.data && (
          <>
            <div className="details__row">
              <dt>ФИО</dt>
              <dd>{profileQuery.data.fullName}</dd>
            </div>
            <div className="details__row">
              <dt>Группа</dt>
              <dd>{profileQuery.data.groupName ?? '—'}</dd>
            </div>
          </>
        )}
      </dl>
      {profileQuery.isError && (
        <p className="placeholder-note">Данные профиля студента недоступны.</p>
      )}
    </section>
  )
}
