import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Bilingual } from '../api/curriculum'
import { fetchDue, postResults, type ReviewItem } from '../api/review'
import { useAuth } from '../auth/useAuth'

interface Props {
  /** Bumped after a lesson so newly-missed items surface here. */
  refreshKey: number
}

/**
 * Spaced-repetition review (M6): resurfaces previously-missed exercises as self-graded recall
 * cards. Marking an item updates its Leitner schedule server-side and removes it from view.
 */
export function ReviewPanel({ refreshKey }: Props) {
  const { t, i18n } = useTranslation()
  const { user, getToken } = useAuth()
  const [items, setItems] = useState<ReviewItem[]>([])

  const label = (b: Bilingual) => (i18n.language === 'bn' ? b.bn : b.en)

  const load = useCallback(() => {
    if (!user) {
      setItems([])
      return
    }
    const token = getToken()
    if (!token) return
    fetchDue(token)
      .then(setItems)
      .catch(() => setItems([]))
  }, [user, getToken])

  useEffect(() => {
    load()
  }, [load, refreshKey])

  if (!user || items.length === 0) return null

  const mark = (exerciseId: string, correct: boolean) => {
    const token = getToken()
    if (!token) return
    // Optimistically remove the card; record the outcome (reschedules server-side).
    setItems((current) => current.filter((it) => it.exerciseId !== exerciseId))
    void postResults(token, [{ exerciseId, correct }]).catch(() => load())
  }

  return (
    <section className="review" aria-label={t('review.title')}>
      <h2>{t('review.title')}</h2>
      <p className="review__count">{t('review.due', { count: items.length })}</p>
      <ul className="review__list">
        {items.map((item) => (
          <li key={item.exerciseId} className="review__card">
            <span className="review__prompt">{label(item.prompt)}</span>
            <div className="review__actions">
              <button
                type="button"
                className="review__knew"
                onClick={() => mark(item.exerciseId, true)}
              >
                {t('review.knewIt')}
              </button>
              <button
                type="button"
                className="review__forgot"
                onClick={() => mark(item.exerciseId, false)}
              >
                {t('review.stillLearning')}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
