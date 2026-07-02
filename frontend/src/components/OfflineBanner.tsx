import { useTranslation } from 'react-i18next'
import { useOnlineStatus } from '../hooks/useOnlineStatus'

/** A polite, always-announced banner shown while the browser is offline (M5). */
export function OfflineBanner() {
  const { t } = useTranslation()
  const online = useOnlineStatus()

  return (
    <div className="offline" role="status" aria-live="polite">
      {!online && <span className="offline__badge">{t('offline.message')}</span>}
    </div>
  )
}
