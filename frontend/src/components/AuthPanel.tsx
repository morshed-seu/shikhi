import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'

type Mode = 'login' | 'register'

export function AuthPanel() {
  const { t } = useTranslation()
  const { user, loading, login, register, logout } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (loading) {
    return <p className="app__badge app__badge--loading">{t('auth.loading')}</p>
  }

  if (user) {
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
          <input
            type="password"
            required
            minLength={mode === 'register' ? 8 : undefined}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />
        </label>

        {error && (
          <p className="auth__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="auth__submit" disabled={submitting}>
          {submitting ? t('auth.submitting') : t(mode === 'login' ? 'auth.login' : 'auth.register')}
        </button>
      </form>
    </section>
  )
}
