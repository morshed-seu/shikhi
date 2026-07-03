import { useCallback, useEffect, useState } from 'react'

/**
 * Light/dark theme with a manual override. Defaults to the OS preference and,
 * once the user toggles, persists their choice and stamps `data-theme` on
 * <html> so the CSS token overrides in index.css win over `prefers-color-scheme`.
 */
export type Theme = 'light' | 'dark'

const THEME_KEY = 'shikhi.theme'

function systemTheme(): Theme {
  if (typeof window === 'undefined' || !window.matchMedia) return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function storedTheme(): Theme | null {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme
  }
}

export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const [theme, setTheme] = useState<Theme>(() => storedTheme() ?? systemTheme())

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  const toggleTheme = useCallback(() => {
    setTheme((prev) => {
      const next: Theme = prev === 'dark' ? 'light' : 'dark'
      try {
        localStorage.setItem(THEME_KEY, next)
      } catch {
        // Persistence is best-effort; the in-memory switch still applies.
      }
      return next
    })
  }, [])

  return { theme, toggleTheme }
}
