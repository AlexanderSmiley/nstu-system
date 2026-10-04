import type { ModuleId, Preferences, Role } from '../api/types'
import { DEFAULT_PREFERENCES } from '../api/preferences'

/** A tile on the home screen. The registry is code so new modules need no shell changes. */
export interface AppModule {
  id: ModuleId
  title: string
  description?: string
  path: string
  requiredRole?: Role
  /**
   * Explicit allowlist. When present, the module is shown only to those roles;
   * an undefined allowlist keeps the open (`requiredRole`-based) behaviour.
   */
  roles?: readonly Role[]
}

/** Modules on the home screen: events/queues, the two-week calendar and notes. */
export const APP_MODULES: readonly AppModule[] = [
  {
    id: 'events',
    title: 'События',
    description: 'Очереди и журнал сдач',
    path: '/events',
  },
  {
    id: 'calendar',
    title: 'Календарь',
    description: 'Мероприятия на две недели',
    path: '/calendar',
  },
  {
    id: 'notes',
    title: 'Заметки',
    description: 'Личные записи и файлы',
    path: '/notes',
    // Accounts only: a guest session must not see the module (access spec).
    roles: ['ADMIN', 'STAFF', 'STUDENT'],
  },
]

function isVisibleForRole(module: AppModule, role: Role | null): boolean {
  if (module.roles) {
    return role !== null && module.roles.includes(role)
  }
  return module.requiredRole === undefined || module.requiredRole === role
}

/**
 * Modules visible to the role, further filtered by the account's preferences
 * (design.md D7): a module switched off in the settings disappears both from the
 * home grid and from the sidebar. A guest has no preferences and therefore sees
 * every module its role allows.
 */
export function modulesForRole(
  role: Role | null,
  preferences: Preferences = DEFAULT_PREFERENCES,
): readonly AppModule[] {
  return APP_MODULES.filter(
    (module) => isVisibleForRole(module, role) && preferences.modules[module.id] !== false,
  )
}
