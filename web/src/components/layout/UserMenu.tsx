import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../../auth/session'
import { roleLabel } from '../../auth/roles'
import { useLogout } from '../../auth/useLogout'
import { UserIcon } from '../icons'

/**
 * User affordance in the header (access spec "Иконка пользователя и мини-инфо"):
 * profiles link for STUDENT/STAFF, a short note for GUEST/ADMIN, logout for all.
 */
export function UserMenu() {
  const { me, role } = useSession()
  const logout = useLogout()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }

    function handleMouseDown(event: MouseEvent) {
      const target = event.target as Node | null
      if (target && !containerRef.current?.contains(target)) {
        setOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  if (!me) {
    return null
  }

  const hasProfile = role === 'STUDENT' || role === 'STAFF'

  return (
    <div className="user-menu" ref={containerRef}>
      <button
        type="button"
        className="icon-button"
        aria-label="Пользователь"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <UserIcon />
      </button>

      {open && (
        <div className="user-menu__panel" data-testid="user-menu">
          {hasProfile ? (
            <div className="user-menu__profile">
              <span className="user-menu__name">{me.displayName ?? 'Профиль'}</span>
              <span className="user-menu__role">{roleLabel(role)}</span>
              <Link className="user-menu__link" to="/profile" onClick={() => setOpen(false)}>
                Профиль
              </Link>
            </div>
          ) : (
            <p className="user-menu__note">
              {role === 'ADMIN' ? 'Вы вошли как Администратор' : 'Вы вошли как гость'}
            </p>
          )}
          <button
            type="button"
            className="user-menu__logout"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
          >
            Выйти
          </button>
        </div>
      )}
    </div>
  )
}
