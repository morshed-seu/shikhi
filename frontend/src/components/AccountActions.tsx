import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/useAuth'

const EXPORT_FILENAME = 'shikhi-export.json'

/**
 * US-13.4: registered learners get export + delete (delete requires an explicit confirm
 * step, never `window.confirm`). Guests get the claim CTA instead — but GuestBanner is
 * already rendered persistently in App (above every view, same as during practice), so it
 * is already visible while the profile is open. Rendering it again here would just
 * duplicate the same claim card on screen, so this renders nothing for guests.
 */
export function AccountActions() {
  const { t } = useTranslation()
  const { user, getToken, logout } = useAuth()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!user || user.isGuest) return null

  const download = () => {
    const token = getToken()
    if (!token || busy) return
    setBusy(true)
    setError(null)
    authApi
      .exportAccount(token)
      .then((data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = EXPORT_FILENAME
        link.click()
        URL.revokeObjectURL(url)
      })
      .catch(() => setError(t('profile.actions.exportError')))
      .finally(() => setBusy(false))
  }

  const deleteAccount = () => {
    const token = getToken()
    if (!token || busy) return
    setBusy(true)
    setError(null)
    authApi
      .deleteAccount(token)
      .then(() => logout())
      .catch(() => {
        setError(t('profile.actions.deleteError'))
        setBusy(false)
      })
  }

  return (
    <section className="account-actions" aria-label={t('profile.actions.title')}>
      <h3>{t('profile.actions.title')}</h3>

      {error && (
        <p className="auth__error" role="alert">
          {error}
        </p>
      )}

      <button type="button" className="account-actions__export" disabled={busy} onClick={download}>
        {t('profile.actions.export')}
      </button>

      {!confirmingDelete ? (
        <button
          type="button"
          className="account-actions__delete"
          disabled={busy}
          onClick={() => setConfirmingDelete(true)}
        >
          {t('profile.actions.delete')}
        </button>
      ) : (
        <div className="account-actions__confirm" role="group" aria-label={t('profile.actions.deleteConfirmPrompt')}>
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
            onClick={() => setConfirmingDelete(false)}
          >
            {t('profile.cancel')}
          </button>
        </div>
      )}
    </section>
  )
}
