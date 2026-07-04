import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CEFR_LEVELS, type CefrLevel, setLevel } from '../api/practice'
import { useAuth } from '../auth/useAuth'

const DISMISS_KEY = 'shikhi.onboarded'

function alreadyDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISS_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * A one-time welcome for signed-in learners (J1 activation). Since E12, it doubles as
 * self-placement: pick a CEFR band (saved via PUT /stats/level) or skip and stay at A1.
 * Dismissal is remembered.
 */
export function Onboarding() {
  const { t } = useTranslation()
  const { user, getToken } = useAuth()
  const [dismissed, setDismissed] = useState(alreadyDismissed)
  const [saving, setSaving] = useState(false)

  if (!user || dismissed) return null

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISS_KEY, '1')
    } catch {
      // Best-effort; dismissal simply won't persist across reloads.
    }
    setDismissed(true)
  }

  const place = (band: CefrLevel) => {
    const token = getToken()
    if (!token || saving) return
    setSaving(true)
    // Placement is best-effort: the account default is A1 either way.
    setLevel(token, band)
      .catch(() => undefined)
      .finally(() => {
        setSaving(false)
        dismiss()
      })
  }

  return (
    <section className="onboarding" aria-label={t('onboarding.title')}>
      <h2>{t('onboarding.title')}</h2>
      <p>{t('onboarding.welcome')}</p>
      <p className="onboarding__placement">{t('onboarding.placement')}</p>
      <div className="onboarding__levels" role="group" aria-label={t('practice.pickLevel')}>
        {CEFR_LEVELS.map((band) => (
          <button
            key={band}
            type="button"
            className="onboarding__level"
            disabled={saving}
            onClick={() => place(band)}
          >
            <span className="onboarding__level-code">{band}</span>
            <span className="onboarding__level-name">{t(`practice.bands.${band}`)}</span>
          </button>
        ))}
      </div>
      <button type="button" className="onboarding__dismiss" disabled={saving} onClick={dismiss}>
        {t('onboarding.skip')}
      </button>
    </section>
  )
}
