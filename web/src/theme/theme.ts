import { useCallback, useSyncExternalStore } from 'react'

/** Supported colour schemes (design.md "Переключатель темы"). */
export type Theme = 'light' | 'dark'

/** `localStorage` key holding the user's explicit choice. */
export const THEME_STORAGE_KEY = 'nstu.theme'

const listeners = new Set<() => void>()

function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark'
}

/** The explicit user choice, or `null` when the system preference should apply. */
export function readStoredTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isTheme(value) ? value : null
  } catch {
    return null
  }
}

function prefersDarkScheme(): boolean {
  if (typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** Stored choice, falling back to the operating-system preference. */
export function resolveInitialTheme(): Theme {
  return readStoredTheme() ?? (prefersDarkScheme() ? 'dark' : 'light')
}

/** Applies a theme to `<html>` via `data-theme` and the native `color-scheme`. */
export function applyTheme(theme: Theme): void {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  root.style.colorScheme = theme
}

/** Applies the initial theme before the first paint (called from `main.tsx`). */
export function initTheme(): Theme {
  const theme = resolveInitialTheme()
  applyTheme(theme)
  return theme
}

/** Reads the theme currently applied to the document. */
export function getTheme(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

/** Sets, persists and broadcasts a theme change. */
export function setTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Storage can be unavailable (private mode); the in-memory theme still applies.
  }
  applyTheme(theme)
  for (const listener of listeners) {
    listener()
  }
}

function subscribeTheme(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Reads and toggles the active theme from React. */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const theme = useSyncExternalStore(subscribeTheme, getTheme, () => 'light' as const)
  const toggle = useCallback(() => {
    setTheme(getTheme() === 'dark' ? 'light' : 'dark')
  }, [])
  return { theme, toggle }
}
