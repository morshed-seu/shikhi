import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchStats, type Stats } from '../api/sessions'
import { useAuth } from '../auth/useAuth'

interface Props {
  /** Bumped by the parent after a lesson finishes, to re-pull the latest stats. */
  refreshKey: number
}

export function StatsBar({ refreshKey }: Props) {
  const { t } = useTranslation()
  const { user, getToken } = useAuth()
  const [stats, setStats] = useState<Stats | null>(null)

  useEffect(() => {
    if (!user) {
      setStats(null)
      return
    }
    const token = getToken()
    if (!token) return

    let cancelled = false
    fetchStats(token)
      .then((s) => {
        if (!cancelled) setStats(s)
      })
      .catch(() => {
        if (!cancelled) setStats(null)
      })
    return () => {
      cancelled = true
    }
  }, [user, getToken, refreshKey])

  if (!user || !stats) return null

  return (
    <section className="stats" aria-label={t('stats.title')}>
      <span className="stats__item">⭐ {stats.xp} {t('stats.xp')}</span>
      <span className="stats__item">🔥 {stats.currentStreak} {t('stats.streak')}</span>
      <span className="stats__item" aria-label={t('stats.hearts')}>❤ {stats.hearts}</span>
    </section>
  )
}
