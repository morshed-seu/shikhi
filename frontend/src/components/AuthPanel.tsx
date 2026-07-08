import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'

type Mode = 'login' | 'register'

// Remembering the email (not the password) prefills the login form across visits.
// We deliberately never persist the password: localStorage is XSS-reachable, mirroring
// the refresh-token caveat in AuthProvider. Browsers still offer their own credential save.
const REMEMBERED_EMAIL_KEY = 'shikhi.rememberedEmail'

function readRememberedEmail(): string {
  try {
    return localStorage.getItem(REMEMBERED_EMAIL_KEY) ?? ''
  } catch {
    return ''
  }
}

export function AuthPanel() {
  const { t, i18n } = useTranslation()
  const { user, loading, login, register, startGuest, logout } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const remembered = readRememberedEmail()
  const [email, setEmail] = useState(remembered)
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [rememberMe, setRememberMe] = useState(remembered !== '')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (loading) {
    return <p className="app__badge app__badge--loading">{t('auth.loading')}</p>
  }

  if (user) {
    // A guest gets a lightweight badge; the claim prompt lives in GuestBanner.
    if (user.isGuest) {
      return (
        <section className="auth" aria-label={t('auth.profileTitle')}>
          <p className="auth__greeting">{t('auth.guestBadge')}</p>
          <button type="button" className="auth__submit" onClick={() => void logout()}>
            {t('auth.logout')}
          </button>
        </section>
      )
    }
    return (
      <section className="auth" aria-label={t('auth.profileTitle')}>
        <p className="auth__greeting">
          {t('auth.greeting', { name: user.displayName ?? t('auth.learner') })}
        </p>
        <p className="auth__roles">{user.roles.join(', ')}</p>
        <button type="button" className="auth__submit" onClick={() => void logout()}>
          {t('auth.logout')}
        </button>
      </section>
    )
  }

  const onStartGuest = async () => {
    setError(null)
    setSubmitting(true)
    try {
      await startGuest(i18n.language === 'en' ? 'en' : 'bn')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('auth.genericError'))
    } finally {
      setSubmitting(false)
    }
  }

  const persistRememberedEmail = () => {
    try {
      if (rememberMe) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, email)
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY)
      }
    } catch {
      // Persistence is best-effort; a failed write just means no prefill next visit.
    }
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login({ email, password })
      } else {
        await register({ email, password, displayName: displayName || undefined })
      }
      persistRememberedEmail()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('auth.genericError'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth" aria-label={t('auth.formTitle')}>
      <div className="auth__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'login'}
          onClick={() => setMode('login')}
        >
          {t('auth.login')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'register'}
          onClick={() => setMode('register')}
        >
          {t('auth.register')}
        </button>
      </div>

      <form className="auth__form" onSubmit={(event) => void onSubmit(event)}>
        {mode === 'register' && (
          <label>
            {t('auth.displayName')}
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              autoComplete="name"
            />
          </label>
        )}
        <label>
          {t('auth.email')}
          <input
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
        </label>
        <label>
          {t('auth.password')}
          <span className="auth__password">
            <input
              type={showPassword ? 'text' : 'password'}
              required
              minLength={mode === 'register' ? 8 : undefined}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
            <button
              type="button"
              className="auth__password-toggle"
              onClick={() => setShowPassword((shown) => !shown)}
              aria-pressed={showPassword}
              aria-label={t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')}
            >
              {t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')}
            </button>
          </span>
        </label>

        {mode === 'login' && (
          <label className="auth__remember">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
            />
            {t('auth.rememberMe')}
          </label>
        )}

        {error && (
          <p className="auth__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="auth__submit" disabled={submitting}>
          {submitting ? t('auth.submitting') : t(mode === 'login' ? 'auth.login' : 'auth.register')}
        </button>
      </form>

      <div className="auth__guest">
        <span className="auth__guest-sep">{t('auth.or')}</span>
        <button
          type="button"
          className="auth__guest-cta"
          onClick={() => void onStartGuest()}
          disabled={submitting}
        >
          {t('auth.guestCta')}
        </button>
      </div>
    </section>
  )
}
