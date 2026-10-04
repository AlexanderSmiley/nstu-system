import { NavLink } from 'react-router-dom'
import { useSession } from '../../auth/session'
import { usePreferences } from '../../hooks/usePreferences'
import { modulesForRole } from '../../modules/registry'
import { ThemeToggle } from '../ThemeToggle'

interface SidebarProps {
  open: boolean
  onNavigate: () => void
}

export function Sidebar({ open, onNavigate }: SidebarProps) {
  const { role } = useSession()
  const { preferences } = usePreferences()
  const modules = modulesForRole(role, preferences)
  // Settings and administration are not toggleable modules: they follow the role.
  const isAccount = role === 'STUDENT' || role === 'STAFF' || role === 'ADMIN'

  const className = (active: boolean) =>
    active ? 'sidebar__link sidebar__link--active' : 'sidebar__link'

  return (
    <aside
      className={open ? 'sidebar sidebar--open' : 'sidebar'}
      data-testid="sidebar"
    >
      <nav aria-label="Основная навигация" className="sidebar__nav">
        <ul className="sidebar__list">
          {modules.map((module) => (
            <li key={module.id}>
              <NavLink
                to={module.path}
                className={({ isActive }) => className(isActive)}
                onClick={onNavigate}
              >
                {module.title}
              </NavLink>
            </li>
          ))}
          {isAccount && (
            <li>
              <NavLink to="/settings" className={({ isActive }) => className(isActive)} onClick={onNavigate}>
                Настройки
              </NavLink>
            </li>
          )}
          {role === 'ADMIN' && (
            <li>
              <NavLink to="/admin" className={({ isActive }) => className(isActive)} onClick={onNavigate}>
                Администрирование
              </NavLink>
            </li>
          )}
        </ul>
      </nav>
      <div className="sidebar__footer">
        <ThemeToggle />
      </div>
    </aside>
  )
}
