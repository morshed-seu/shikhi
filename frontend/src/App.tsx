import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Locale } from './api/auth'
import { authApi } from './api/auth'
import { fetchHealth, type HealthStatus } from './api/health'
import { flushOutbox, pendingCount } from './api/outbox'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/useAuth'
import { AuthPanel } from './components/AuthPanel'
import { GuestBanner } from './components/GuestBanner'
import { Onboarding } from './components/Onboarding'
import { OfflineBanner } from './components/OfflineBanner'
import { PracticeHero } from './components/PracticeHero'
import { PracticePlayer } from './components/PracticePlayer'
import { ProfileView } from './components/ProfileView'
import { ReviewPanel } from './components/ReviewPanel'
import { StatsBar } from './components/StatsBar'
import { VocabularyBrowser } from './components/VocabularyBrowser'
import { useTheme } from './hooks/useTheme'
import { changeLocale } from './i18n'
import { DeleteAccountPage } from './pages/DeleteAccountPage'
import { PrivacyPolicyPage } from './pages/PrivacyPolicyPage'
import './App.css'

type HealthState = HealthStatus | 'loading' | 'error'

function AppShell() {
  const { t, i18n } = useTranslation()
  const { theme, toggleTheme } = useTheme()
  const { user, getToken, setUiLocale } = useAuth()
  const [health, setHealth] = useState<HealthState>('loading')
  const [view, setView] = useState<'home' | 'practice' | 'profile'>('home')
  const [refreshKey, setRefreshKey] = useState(0)

  const exitPractice = () => {
    setView('home')
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
        <div className="app__actions">
          <button
            type="button"
            className="app__theme"
            onClick={toggleTheme}
            aria-label={t('app.toggleTheme')}
            aria-pressed={theme === 'dark'}
          >
            {theme === 'dark' ? '☀️' : '🌙'}
          </button>
          <button
            type="button"
            className="app__lang"
            onClick={toggleLanguage}
            aria-label={t('app.toggleLanguage')}
          >
            {i18n.language === 'bn' ? 'English' : 'বাংলা'}
          </button>
          {user && (
            <button
              type="button"
              className="app__profile"
              onClick={() => setView('profile')}
              aria-label={t('profile.open')}
            >
              👤
            </button>
          )}
        </div>
      </header>
      <p className="app__tagline">{t('app.tagline')}</p>
      <section className="app__status" aria-live="polite">
        <span className={`app__badge ${badgeModifier}`}>{statusText}</span>
      </section>
      <OfflineBanner />
      <AuthPanel />
      <GuestBanner />
      <StatsBar refreshKey={refreshKey} onOpenProfile={() => setView('profile')} />
      {view === 'practice' ? (
        <PracticePlayer onExit={exitPractice} />
      ) : view === 'profile' ? (
        <ProfileView onBack={() => setView('home')} />
      ) : (
        <>
          <Onboarding />
          <PracticeHero refreshKey={refreshKey} onStart={() => setView('practice')} />
          <ReviewPanel refreshKey={refreshKey} />
          {/* Curriculum map hidden from the web client — the guided lesson tree is
              intentionally not surfaced here, matching the Android home. Practice +
              review + vocabulary remain the home surface. */}
          <VocabularyBrowser />
        </>
      )}
    </main>
  )
}

// Minimal pathname-based routing (no router library — just these two standalone,
// directly-linkable Play Store compliance pages; everything else stays the single-page
// app it always was). Cloudflare Pages serves index.html for any path (SPA fallback),
// so reading location.pathname at startup is enough — no history/route-matching needed.
function App() {
  // Strip one trailing slash so /privacy/ matches too; '/' itself stays the app root.
  const path = window.location.pathname.replace(/(.)\/$/, '$1')
  if (path === '/privacy') {
    return <PrivacyPolicyPage />
  }
  if (path === '/delete-account') {
    return (
      <AuthProvider>
        <DeleteAccountPage />
      </AuthProvider>
    )
  }
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  )
}

export default App
