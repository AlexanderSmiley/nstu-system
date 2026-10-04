import { NavLink } from 'react-router-dom'
import { useSession } from '../../auth/session'
import { ThemeToggle } from '../ThemeToggle'

interface SidebarProps {
  open: boolean
  onNavigate: () => void
}

export function Sidebar({ open, onNavigate }: SidebarProps) {
  const { role } = useSession()

  const className = (active: boolean) =>
    active ? 'sidebar__link sidebar__link--active' : 'sidebar__link'

  return (
    <aside
      className={open ? 'sidebar sidebar--open' : 'sidebar'}
      data-testid="sidebar"
    >
      <nav aria-label="Основная навигация" className="sidebar__nav">
        <ul className="sidebar__list">
          <li>
            <NavLink to="/events" className={({ isActive }) => className(isActive)} onClick={onNavigate}>
              События
            </NavLink>
          </li>
          {role === 'ADMIN' && (
            <li>
              <NavLink to="/admin" className={({ isActive }) => className(isActive)} onClick={onNavigate}>
                Администрирование
              </NavLink>
            </li>
          )}
          <li>
            <NavLink to="/settings" className={({ isActive }) => className(isActive)} onClick={onNavigate}>
              Настройки
            </NavLink>
          </li>
        </ul>
      </nav>
      <div className="sidebar__footer">
        <ThemeToggle />
      </div>
    </aside>
  )
}
