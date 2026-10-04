import { NavLink, Outlet } from 'react-router-dom'

function tabClass(active: boolean): string {
  return active ? 'tabs__link tabs__link--active' : 'tabs__link'
}

/**
 * `/admin` — admin container (access spec «Блочная структура раздела
 * «Администрирование»», design.md D6). The three blocks live on their own
 * subroutes (`general`, `users`, `events`) and are rendered through the outlet.
 */
export function AdminPage() {
  return (
    <section className="page">
      <h1>Администрирование</h1>
      <nav className="tabs" aria-label="Разделы администрирования">
        <NavLink to="/admin/general" className={({ isActive }) => tabClass(isActive)}>
          Общее
        </NavLink>
        <NavLink to="/admin/users" className={({ isActive }) => tabClass(isActive)}>
          Управление пользователями
        </NavLink>
        <NavLink to="/admin/events" className={({ isActive }) => tabClass(isActive)}>
          Управление событиями
        </NavLink>
      </nav>
      <Outlet />
    </section>
  )
}
