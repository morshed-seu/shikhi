import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { AuthPanel } from '../components/AuthPanel'
import { LegalPageHeader } from '../components/LegalPageHeader'
import './legal.css'

/**
 * Public account-deletion page at /delete-account (Play Store data-safety requirement,
 * US-PS.2 — a web URL where a user can request deletion without reinstalling the app).
 *
 * - Signed in (non-guest): delete right here, reusing the same authApi.deleteAccount call
 *   and explicit-confirm pattern as AccountActions.tsx.
 * - Signed in as a guest: guest sessions aren't a saved account yet; explain that and let
 *   them log out (via AuthPanel) to sign in to the account they want to delete.
 * - Signed out: explain the two deletion paths and offer the existing sign-in form.
 */
export function DeleteAccountPage() {
  const { t } = useTranslation()
  const { user, loading, getToken, logout } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [deleted, setDeleted] = useState(false)

  const deleteAccount = () => {
    const token = getToken()
    if (!token || busy) return
    setBusy(true)
    setError(null)
    authApi
      .deleteAccount(token)
      .then(() => {
        setDeleted(true)
        // The server-side logout POST will likely fail here (the account was just deleted
        // and the token invalidated). AuthProvider.logout() still clears the local session
        // in its finally block, so swallow that rejection — the success message must show
        // and the outer .catch must not treat it as a failed deletion.
        return logout().catch(() => undefined)
      })
      .catch(() => {
        setError(t('deleteAccount.deleteError'))
        setBusy(false)
      })
  }

  return (
    <main className="legal">
      <LegalPageHeader current="delete-account" />

      <article className="legal__doc">
        <h1>{t('deleteAccount.title')}</h1>
        <p>{t('deleteAccount.intro')}</p>
        <p className="legal__warning">{t('deleteAccount.whatIsDeleted')}</p>

        {deleted ? (
          <p className="legal__success" role="status">
            {t('deleteAccount.deleteSuccess')}
          </p>
        ) : loading ? (
          <p className="app__badge app__badge--loading">{t('deleteAccount.loading')}</p>
        ) : user && !user.isGuest ? (
          <section className="account-actions" aria-label={t('deleteAccount.title')}>
            <p>{t('deleteAccount.signedInAs', { name: user.displayName ?? t('auth.learner') })}</p>

            {error && (
              <p className="auth__error" role="alert">
                {error}
              </p>
            )}

            {!confirming ? (
              <button
                type="button"
                className="account-actions__delete"
                disabled={busy}
                onClick={() => setConfirming(true)}
              >
                {t('deleteAccount.deleteButton')}
              </button>
            ) : (
              <div
                className="account-actions__confirm"
                role="group"
                aria-label={t('profile.actions.deleteConfirmPrompt')}
              >
                <p>{t('profile.actions.deleteConfirmPrompt')}</p>
                <button
                  type="button"
                  className="account-actions__delete-confirm"
                  disabled={busy}
                  onClick={deleteAccount}
                >
                  {t('profile.actions.deleteConfirm')}
                </button>
                <button
                  type="button"
                  className="account-actions__delete-cancel"
                  disabled={busy}
                  onClick={() => setConfirming(false)}
                >
                  {t('profile.cancel')}
                </button>
              </div>
            )}
          </section>
        ) : (
          <section>
            {user?.isGuest ? (
              <p>{t('deleteAccount.guestNotice')}</p>
            ) : (
              <p>{t('deleteAccount.notSignedIn.lead')}</p>
            )}
            <AuthPanel />
          </section>
        )}
      </article>
    </main>
  )
}
