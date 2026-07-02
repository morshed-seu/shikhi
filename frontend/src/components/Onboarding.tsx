import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
 * A one-time welcome for signed-in learners (J1 activation). Explains the pilot placement
 * (everyone starts at Beginner/A1 — adaptive placement is post-pilot) and points them at the
 * course below. Dismissal is remembered.
 */
export function Onboarding() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [dismissed, setDismissed] = useState(alreadyDismissed)

  if (!user || dismissed) return null

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISS_KEY, '1')
    } catch {
      // Best-effort; dismissal simply won't persist across reloads.
    }
    setDismissed(true)
  }

  return (
    <section className="onboarding" aria-label={t('onboarding.title')}>
      <h2>{t('onboarding.title')}</h2>
      <p>{t('onboarding.welcome')}</p>
      <p className="onboarding__placement">{t('onboarding.placement')}</p>
      <button type="button" className="onboarding__dismiss" onClick={dismiss}>
        {t('onboarding.start')}
      </button>
    </section>
  )
}
