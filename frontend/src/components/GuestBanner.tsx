import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'

/**
 * Conversion prompt shown only to guest learners. Expands into a claim form that upgrades the
 * anonymous account in place (email+password added to the same user id — all progress carries
 * over). If the email already belongs to an account we can't merge, so we tell them to log in.
 */
export function GuestBanner() {
  const { t } = useTranslation()
  const { user, claim } = useAuth()
  const [open, setOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!user?.isGuest) return null

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await claim({ email, password, displayName: displayName || undefined })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'EMAIL_ALREADY_REGISTERED') {
        setError(t('guest.emailTaken'))
      } else {
        setError(err instanceof ApiError ? err.message : t('auth.genericError'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="guest-banner" aria-label={t('guest.title')}>
      <p className="guest-banner__lead">{t('guest.lead')}</p>

      {!open ? (
        <button type="button" className="guest-banner__cta" onClick={() => setOpen(true)}>
          {t('guest.save')}
        </button>
      ) : (
        <form className="auth__form guest-banner__form" onSubmit={(event) => void onSubmit(event)}>
          <label>
            {t('auth.displayName')}
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              autoComplete="name"
            />
          </label>
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
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
            />
          </label>

          {error && (
            <p className="auth__error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="auth__submit" disabled={submitting}>
            {submitting ? t('auth.submitting') : t('guest.createAccount')}
          </button>
        </form>
      )}
    </section>
  )
}
