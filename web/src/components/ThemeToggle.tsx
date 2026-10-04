import { useTheme } from '../theme/theme'

/** Sidebar switch between the light and dark themes (access spec "Переключатель темы"). */
export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      className="theme-toggle"
      aria-pressed={isDark}
      aria-label={isDark ? 'Включить светлую тему' : 'Включить тёмную тему'}
      onClick={toggle}
    >
      {isDark ? 'Светлая тема' : 'Тёмная тема'}
    </button>
  )
}
