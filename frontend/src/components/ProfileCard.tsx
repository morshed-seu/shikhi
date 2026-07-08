import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi, type Identity, type Locale, type User } from '../api/auth'
import type { Stats } from '../api/sessions'
import { useAuth } from '../auth/useAuth'
import { changeLocale } from '../i18n'

interface Props {
  user: User
  identities: Identity[]
  stats: Stats
}

/**
 * US-13.1/13.2: who the app thinks I am, plus the two editable fields (display name, UI
 * language) — same `PATCH /me` the guest-claim and language-toggle flows already use.
 */
export function ProfileCard({ user, identities, stats }: Props) {
  const { t, i18n } = useTranslation()
  const { getToken, setUiLocale, refreshUser } = useAuth()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(user.displayName ?? '')
  const [saving, setSaving] = useState(false)

  const email = identities.find((identity) => identity.provider === 'EMAIL')

  const startEdit = () => {
    setName(user.displayName ?? '')
    setEditing(true)
  }

  const cancelEdit = () => {
    setName(user.displayName ?? '')
    setEditing(false)
  }

  const saveName = () => {
    const token = getToken()
    if (!token || saving) return
    setSaving(true)
    authApi
      .updateProfile(token, { displayName: name.trim() })
      .then((updated) => {
        // Locale is owned by the optimistic local flow (setUiLocale + fire-and-forget
        // PATCH); a name save must never race it. This response may still carry the old
        // uiLocale, and adopting it wholesale would flip the UI language back — so keep
        // the currently-cached locale and take everything else from the server.
        refreshUser({ ...updated, uiLocale: user.uiLocale })
        setEditing(false)
      })
      .catch(() => undefined)
      .finally(() => setSaving(false))
  }

  const onLocaleChange = (locale: Locale) => {
    changeLocale(locale)
    const token = getToken()
    if (!token) return
    setUiLocale(locale) // keep cached user in sync, same as the header language toggle
    void authApi.updateProfile(token, { uiLocale: locale }).catch(() => undefined)
  }

  const joined = user.joinedAt
    ? new Intl.DateTimeFormat(i18n.language === 'bn' ? 'bn-BD' : 'en-US', {
        dateStyle: 'medium',
      }).format(new Date(user.joinedAt))
    : null

  return (
    <section className="profile-card" aria-label={t('profile.cardTitle')}>
      <div className="profile-card__name">
        {editing ? (
          <>
            <input
              className="profile-card__name-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              aria-label={t('auth.displayName')}
              autoFocus
            />
            <button
              type="button"
              className="profile-card__name-save"
              disabled={saving}
              onClick={saveName}
            >
              {t('profile.save')}
            </button>
            <button type="button" className="profile-card__name-cancel" onClick={cancelEdit}>
              {t('profile.cancel')}
            </button>
          </>
        ) : (
          <>
            <h2>{user.displayName || t('auth.learner')}</h2>
            <button
              type="button"
              className="profile-card__edit"
              onClick={startEdit}
              aria-label={t('profile.editName')}
            >
              ✎
            </button>
          </>
        )}
      </div>

      <div className="profile-card__meta">
        <span className="profile-card__level">{stats.cefrLevel}</span>
        {user.isGuest ? (
          <span className="profile-card__guest-badge">{t('auth.guestBadge')}</span>
        ) : (
          email && <span className="profile-card__email">{email.maskedRef}</span>
        )}
        {joined && <span className="profile-card__joined">{t('profile.joined', { date: joined })}</span>}
      </div>

      <label className="profile-card__locale">
        {t('profile.language')}
        <select
          value={i18n.language === 'en' ? 'en' : 'bn'}
          onChange={(event) => onLocaleChange(event.target.value as Locale)}
        >
          <option value="bn">বাংলা</option>
          <option value="en">English</option>
        </select>
      </label>
    </section>
  )
}
