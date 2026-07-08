import { useTranslation } from 'react-i18next'
import type { DashboardResponse } from '../api/dashboard'

interface Props {
  dashboard: DashboardResponse
}

/** US-13.3: the dashboard snapshot — one tile per lifetime/session metric. */
export function StatsGrid({ dashboard }: Props) {
  const { t } = useTranslation()
  const { stats, reviewDueCount, lessonsCompleted, practiceSessionsCompleted, totalAnswered, totalCorrect } =
    dashboard
  const accuracy = totalAnswered > 0 ? Math.round((totalCorrect / totalAnswered) * 100) : null

  const tiles: Array<{ key: string; label: string; value: string }> = [
    { key: 'xp', label: t('stats.xp'), value: String(stats.xp) },
    { key: 'currentStreak', label: t('stats.streak'), value: String(stats.currentStreak) },
    { key: 'longestStreak', label: t('profile.stats.longestStreak'), value: String(stats.longestStreak) },
    { key: 'hearts', label: t('stats.hearts'), value: String(stats.hearts) },
    { key: 'dailyGoal', label: t('profile.stats.dailyGoal'), value: String(stats.dailyGoal) },
    { key: 'reviewDue', label: t('profile.stats.reviewDue'), value: String(reviewDueCount) },
    { key: 'lessonsCompleted', label: t('profile.stats.lessonsCompleted'), value: String(lessonsCompleted) },
    {
      key: 'practiceSessions',
      label: t('profile.stats.practiceSessions'),
      value: String(practiceSessionsCompleted),
    },
    {
      key: 'accuracy',
      label: t('profile.stats.accuracy'),
      value: accuracy === null ? '—' : `${accuracy}%`,
    },
  ]

  return (
    <section className="stats-grid" aria-label={t('stats.title')}>
      {tiles.map((tile) => (
        <div key={tile.key} className="stats-grid__tile">
          <span className="stats-grid__value">{tile.value}</span>
          <span className="stats-grid__label">{tile.label}</span>
        </div>
      ))}
    </section>
  )
}
