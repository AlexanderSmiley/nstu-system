import { Link } from 'react-router-dom'
import { ModuleIcon } from '../components/icons'
import { useSession } from '../auth/session'
import { usePreferences } from '../hooks/usePreferences'
import { modulesForRole } from '../modules/registry'

/** `/` — module registry filtered by role and the account's preferences. */
export function HomePage() {
  const { role } = useSession()
  const { preferences } = usePreferences()
  const modules = modulesForRole(role, preferences)

  return (
    <section className="page">
      <h1>Главная</h1>
      {modules.length === 0 ? (
        <p className="placeholder-note" data-testid="home-empty">
          Все модули отключены. Включите нужные в разделе «Настройки».
        </p>
      ) : (
        <ul className="module-grid" data-testid="module-grid" aria-label="Модули системы">
          {modules.map((module) => (
            <li key={module.id}>
              <Link to={module.path} className="module-tile" data-testid="module-tile">
                <ModuleIcon />
                <span className="module-tile__title">{module.title}</span>
                {module.description && (
                  <span className="module-tile__description">{module.description}</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
