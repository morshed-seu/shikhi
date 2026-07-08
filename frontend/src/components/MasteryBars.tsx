import { useTranslation } from 'react-i18next'
import type { WordMasteryEntry } from '../api/dashboard'

interface Props {
  wordMastery: WordMasteryEntry[]
}

// A ratio between 0 and this floor still renders as a visible sliver, so "I've started but
// barely touched this band" doesn't look identical to "I haven't touched it at all".
const MIN_VISIBLE_PCT = 1.5

/** US-13.3: five horizontal bars (A1-C1), pure CSS — no chart library. */
export function MasteryBars({ wordMastery }: Props) {
  const { t } = useTranslation()

  return (
    <section className="mastery" aria-label={t('profile.mastery.title')}>
      <h3>{t('profile.mastery.title')}</h3>
      <ul className="mastery__list">
        {wordMastery.map((entry) => {
          const ratio = entry.total > 0 ? entry.mastered / entry.total : 0
          const pct = ratio <= 0 ? 0 : Math.max(ratio * 100, MIN_VISIBLE_PCT)
          return (
            <li key={entry.cefrLevel} className="mastery__row">
              <div className="mastery__head">
                <span className="mastery__level">{entry.cefrLevel}</span>
                <span className="mastery__count">
                  {t('profile.mastery.count', { mastered: entry.mastered, total: entry.total })}
                </span>
              </div>
              <div
                className="mastery__track"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={entry.total}
                aria-valuenow={entry.mastered}
                aria-label={t('profile.mastery.level', { level: entry.cefrLevel })}
              >
                <div className="mastery__fill" style={{ width: `${pct}%` }} />
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
