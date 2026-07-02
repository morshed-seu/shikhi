import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type Bilingual, type CurriculumTree, fetchCurriculum } from '../api/curriculum'
import { useAuth } from '../auth/useAuth'

type LoadState = 'idle' | 'loading' | 'error'

interface Props {
  onSelectLesson: (lessonId: string) => void
}

export function CurriculumMap({ onSelectLesson }: Props) {
  const { t, i18n } = useTranslation()
  const { user, getToken } = useAuth()
  const [tree, setTree] = useState<CurriculumTree | null>(null)
  const [state, setState] = useState<LoadState>('idle')

  useEffect(() => {
    if (!user) {
      setTree(null)
      return
    }
    const token = getToken()
    if (!token) return

    let cancelled = false
    setState('loading')
    fetchCurriculum(token)
      .then((data) => {
        if (!cancelled) {
          setTree(data)
          setState('idle')
        }
      })
      .catch(() => {
        if (!cancelled) setState('error')
      })
    return () => {
      cancelled = true
    }
  }, [user, getToken])

  if (!user) return null

  const label = (b: Bilingual) => (i18n.language === 'bn' ? b.bn : b.en)

  return (
    <section className="curriculum" aria-label={t('curriculum.title')}>
      <h2>{t('curriculum.title')}</h2>
      {state === 'loading' && <p>{t('curriculum.loading')}</p>}
      {state === 'error' && <p className="auth__error">{t('curriculum.error')}</p>}
      {tree && tree.levels.length === 0 && <p>{t('curriculum.empty')}</p>}
      {tree?.levels.map((level) => (
        <div key={level.id} className="curriculum__level">
          <h3>{label(level.title)}</h3>
          {level.units.map((unit) => (
            <div key={unit.id} className="curriculum__unit">
              <h4>{label(unit.title)}</h4>
              <ul>
                {unit.lessons.map((lesson) => (
                  <li key={lesson.id}>
                    <button
                      type="button"
                      className="curriculum__lesson"
                      onClick={() => onSelectLesson(lesson.id)}
                    >
                      {label(lesson.title)}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      ))}
    </section>
  )
}
