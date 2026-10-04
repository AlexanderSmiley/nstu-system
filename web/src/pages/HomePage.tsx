import { Link } from 'react-router-dom'
import { ModuleIcon } from '../components/icons'
import { useSession } from '../auth/session'
import { modulesForRole } from '../modules/registry'

/** `/` — module registry; the shell stays unchanged when modules are added. */
export function HomePage() {
  const { role } = useSession()
  const modules = modulesForRole(role)

  return (
    <section className="page">
      <h1>Главная</h1>
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
    </section>
  )
}
