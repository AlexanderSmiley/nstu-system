import type { Role } from '../api/types'

/** A tile on the home screen. The registry is code so new modules need no shell changes. */
export interface AppModule {
  id: string
  title: string
  description?: string
  path: string
  requiredRole?: Role
}

/** MVP has exactly one module (access spec "Главная страница как набор модулей"). */
export const APP_MODULES: readonly AppModule[] = [
  {
    id: 'events',
    title: 'События',
    description: 'Очереди и журнал сдач',
    path: '/events',
  },
]

export function modulesForRole(role: Role | null): readonly AppModule[] {
  return APP_MODULES.filter(
    (module) => module.requiredRole === undefined || module.requiredRole === role,
  )
}
