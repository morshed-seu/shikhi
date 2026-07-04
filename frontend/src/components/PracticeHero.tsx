import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CEFR_LEVELS, type CefrLevel, setLevel } from '../api/practice'
import { fetchStats } from '../api/sessions'
import { useAuth } from '../auth/useAuth'

interface Props {
  /** Bumped by the parent after a session finishes, to re-pull the level/streak. */
  refreshKey: number
  onStart: () => void
}

/**
 * The signed-in home hero (E12, US-12.2): one clear "Start session" action, with the
 * learner's CEFR band worn as a badge. Tapping the badge opens self-placement — the same
 * PUT /stats/level used by onboarding and level-up.
 */
export function PracticeHero({ refreshKey, onStart }: Props) {
  const { t } = useTranslation()
  const { user, getToken } = useAuth()
  const [level, setLevelState] = useState<CefrLevel | null>(null)
  const [streak, setStreak] = useState(0)
  const [picking, setPicking] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!user) {
      setLevelState(null)
      return
    }
    const token = getToken()
    if (!token) return

    let cancelled = false
    fetchStats(token)
      .then((s) => {
        if (cancelled) return
        setLevelState((s.cefrLevel as CefrLevel) ?? 'A1')
        setStreak(s.currentStreak)
      })
      .catch(() => {
        if (!cancelled) setLevelState('A1')
      })
    return () => {
      cancelled = true
    }
  }, [user, getToken, refreshKey])

  if (!user || !level) return null

  const pickLevel = (next: CefrLevel) => {
    const token = getToken()
    if (!token || saving) return
    setSaving(true)
    setLevel(token, next)
      .then((s) => setLevelState((s.cefrLevel as CefrLevel) ?? next))
      .catch(() => undefined)
      .finally(() => {
        setSaving(false)
        setPicking(false)
      })
  }

  return (
    <section className="hero" aria-label={t('practice.heroTitle')}>
      <p className="hero__eyebrow">{t('practice.heroEyebrow')}</p>
      <div className="hero__head">
        <h2 className="hero__title">{t('practice.heroTitle')}</h2>
        <button
          type="button"
          className="hero__level"
          onClick={() => setPicking((p) => !p)}
          aria-expanded={picking}
          aria-label={t('practice.levelBadge', { level })}
        >
          {level}
          <span className="hero__level-caret" aria-hidden="true">
            ▾
          </span>
        </button>
      </div>
      <p className="hero__copy">{t('practice.heroCopy', { level })}</p>

      {picking && (
        <div className="hero__picker" role="group" aria-label={t('practice.pickLevel')}>
          {CEFR_LEVELS.map((band) => (
            <button
              key={band}
              type="button"
              className={`hero__band${band === level ? ' hero__band--active' : ''}`}
              disabled={saving}
              aria-pressed={band === level}
              onClick={() => pickLevel(band)}
            >
              <span className="hero__band-code">{band}</span>
              <span className="hero__band-name">{t(`practice.bands.${band}`)}</span>
            </button>
          ))}
        </div>
      )}

      <div className="hero__actions">
        <button type="button" className="hero__start" onClick={onStart}>
          {t('practice.start')}
        </button>
        {streak > 0 && (
          <span className="hero__streak" aria-label={t('stats.streak')}>
            🔥 {streak}
          </span>
        )}
      </div>
    </section>
  )
}
