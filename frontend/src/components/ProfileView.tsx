import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi, type Identity } from '../api/auth'
import { fetchDashboard, type DashboardResponse } from '../api/dashboard'
import { useAuth } from '../auth/useAuth'
import { AccountActions } from './AccountActions'
import { MasteryBars } from './MasteryBars'
import { ProfileCard } from './ProfileCard'
import { StatsGrid } from './StatsGrid'

interface Props {
  onBack: () => void
}

type LoadState = 'loading' | 'error' | 'ready'

/**
 * E13 profile & dashboard (Flow F): the container for the whole surface — fetches the
 * dashboard snapshot + linked identities, then renders the card, stats grid, mastery bars,
 * and account actions. Replaces the home stack, like PracticePlayer does.
 */
export function ProfileView({ onBack }: Props) {
  const { t } = useTranslation()
  const { user, getToken } = useAuth()
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [identities, setIdentities] = useState<Identity[]>([])
  const [state, setState] = useState<LoadState>('loading')

  useEffect(() => {
    const token = getToken()
    if (!user || !token) {
      setState('error')
      return
    }
    let cancelled = false
    setState('loading')
    // Fetched independently: the dashboard is the surface's backbone, so only its failure
    // hard-errors the view. Identities merely feed the masked-email line — on failure we
    // default to [] and ProfileCard simply omits the email rather than blanking the profile.
    const identitiesPromise = authApi.fetchIdentities(token).catch(() => [] as Identity[])
    Promise.all([fetchDashboard(token), identitiesPromise])
      .then(([dashboardData, identityList]) => {
        if (cancelled) return
        setDashboard(dashboardData)
        setIdentities(identityList)
        setState('ready')
      })
      .catch(() => {
        if (!cancelled) setState('error')
      })
    return () => {
      cancelled = true
    }
  }, [user, getToken])

  return (
    <section className="profile" aria-label={t('profile.title')}>
      <button type="button" className="lesson__exit profile__back" onClick={onBack}>
        ← {t('practice.backHome')}
      </button>

      {state === 'loading' && <p className="lesson__status">{t('profile.loading')}</p>}
      {state === 'error' && <p className="auth__error">{t('profile.error')}</p>}

      {state === 'ready' && user && dashboard && (
        <>
          <ProfileCard user={user} identities={identities} stats={dashboard.stats} />
          <StatsGrid dashboard={dashboard} />
          <MasteryBars wordMastery={dashboard.wordMastery} />
          <AccountActions />
        </>
      )}
    </section>
  )
}
