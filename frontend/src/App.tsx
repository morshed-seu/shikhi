import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Locale } from './api/auth'
import { authApi } from './api/auth'
import { fetchHealth, type HealthStatus } from './api/health'
import { flushOutbox, pendingCount } from './api/outbox'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/useAuth'
import { AuthPanel } from './components/AuthPanel'
import { CurriculumMap } from './components/CurriculumMap'
import { LessonPlayer } from './components/LessonPlayer'
import { Onboarding } from './components/Onboarding'
import { OfflineBanner } from './components/OfflineBanner'
import { StatsBar } from './components/StatsBar'
import { changeLocale } from './i18n'
import './App.css'

type HealthState = HealthStatus | 'loading' | 'error'

function AppShell() {
  const { t, i18n } = useTranslation()
  const { user, getToken, setUiLocale } = useAuth()
  const [health, setHealth] = useState<HealthState>('loading')
  const [activeLessonId, setActiveLessonId] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  // Leaving a lesson bumps the refresh key so stats + the map re-pull progress.
  const exitLesson = () => {
    setActiveLessonId(null)
    setRefreshKey((k) => k + 1)
  }

  useEffect(() => {
    let cancelled = false
    fetchHealth()
      .then((h) => {
        if (!cancelled) setHealth(h)
      })
      .catch(() => {
        if (!cancelled) setHealth('error')
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Adopt the signed-in learner's saved language preference.
  useEffect(() => {
    if (user && (user.uiLocale === 'bn' || user.uiLocale === 'en') && user.uiLocale !== i18n.language) {
      changeLocale(user.uiLocale)
    }
  }, [user, i18n.language])

  // Flush any buffered offline progress on load, on reconnect, and after a lesson (refreshKey).
  useEffect(() => {
    const token = getToken()
    if (!user || !token || pendingCount() === 0) return
    const flush = () => {
      void flushOutbox(token).then((ok) => {
        if (ok) setRefreshKey((k) => k + 1)
      })
    }
    flush()
    window.addEventListener('online', flush)
    return () => window.removeEventListener('online', flush)
  }, [user, getToken, refreshKey])

  const toggleLanguage = () => {
    const next: Locale = i18n.language === 'bn' ? 'en' : 'bn'
    changeLocale(next)
    const token = getToken()
    if (user && token) {
      setUiLocale(next) // keep cached user in sync so the adopt-locale effect doesn't revert
      // Persist the preference to the profile (best-effort).
      void authApi.updateProfile(token, { uiLocale: next }).catch(() => undefined)
    }
  }

  let statusText: string
  let badgeModifier: string
  if (health === 'loading') {
    statusText = t('status.loading')
    badgeModifier = 'app__badge--loading'
  } else if (health === 'error') {
    statusText = t('status.error')
    badgeModifier = 'app__badge--error'
  } else {
    statusText = t('status.ok', { status: health.status })
    badgeModifier = 'app__badge--ok'
  }

  return (
    <main className="app">
      <header className="app__header">
        <h1>{t('app.title')}</h1>
        <button
          type="button"
          className="app__lang"
          onClick={toggleLanguage}
          aria-label={t('app.toggleLanguage')}
        >
          {i18n.language === 'bn' ? 'English' : 'বাংলা'}
        </button>
      </header>
      <p className="app__tagline">{t('app.tagline')}</p>
      <section className="app__status" aria-live="polite">
        <span className={`app__badge ${badgeModifier}`}>{statusText}</span>
      </section>
      <OfflineBanner />
      <AuthPanel />
      <StatsBar refreshKey={refreshKey} />
      {activeLessonId ? (
        <LessonPlayer lessonId={activeLessonId} onExit={exitLesson} />
      ) : (
        <>
          <Onboarding />
          <CurriculumMap onSelectLesson={setActiveLessonId} refreshKey={refreshKey} />
        </>
      )}
    </main>
  )
}

function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  )
}

export default App
