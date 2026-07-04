import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { type Bilingual, type ExerciseView, fetchLesson, type LessonView } from '../api/curriculum'
import { enqueue } from '../api/outbox'
import {
  type AnswerPayload,
  completeSession,
  type LessonResult,
  type LessonSession,
  startSession,
  submitAnswer,
  type Verdict,
} from '../api/sessions'
import { useAuth } from '../auth/useAuth'

interface Props {
  lessonId: string
  onExit: () => void
}

type Phase = 'loading' | 'error' | 'playing' | 'finished'

const TEXT_TYPES = ['TYPE_TRANSLATION', 'FILL_BLANK']

export function LessonPlayer({ lessonId, onExit }: Props) {
  const { t, i18n } = useTranslation()
  const { getToken } = useAuth()

  const [phase, setPhase] = useState<Phase>('loading')
  const [lesson, setLesson] = useState<LessonView | null>(null)
  const [session, setSession] = useState<LessonSession | null>(null)
  const [index, setIndex] = useState(0)
  const [hearts, setHearts] = useState(0)
  const [answer, setAnswer] = useState('')
  // WORD_BANK: the ids of tokens the learner has placed, in tapped order.
  const [placed, setPlaced] = useState<string[]>([])
  const [verdict, setVerdict] = useState<Verdict | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<LessonResult | null>(null)
  const [correctCount, setCorrectCount] = useState(0)
  const [savedOffline, setSavedOffline] = useState(false)
  const startedRef = useRef(false)
  const promptRef = useRef<HTMLHeadingElement>(null)

  const label = (b: Bilingual) => (i18n.language === 'bn' ? b.bn : b.en)

  // Move focus to each new exercise prompt so screen-reader users hear it (a11y).
  useEffect(() => {
    if (phase === 'playing') promptRef.current?.focus()
  }, [index, phase])

  useEffect(() => {
    // Guard against React 19 StrictMode double-invoking the effect (would start two sessions).
    if (startedRef.current) return
    startedRef.current = true

    const token = getToken()
    if (!token) {
      setPhase('error')
      return
    }
    Promise.all([fetchLesson(token, lessonId), startSession(token, lessonId)])
      .then(([loaded, started]) => {
        setLesson(loaded)
        setSession(started)
        setHearts(started.heartsRemaining)
        setPhase('playing')
      })
      .catch(() => setPhase('error'))
  }, [lessonId, getToken])

  if (phase === 'loading') return <p className="lesson__status">{t('lesson.loading')}</p>
  if (phase === 'error' || !lesson || !session) {
    return (
      <section className="lesson">
        <p className="auth__error">{t('lesson.error')}</p>
        <button type="button" className="lesson__exit" onClick={onExit}>
          {t('lesson.backToCourse')}
        </button>
      </section>
    )
  }

  if (phase === 'finished' && result) {
    return (
      <section className="lesson" aria-label={t('lesson.resultsTitle')}>
        <h2>{t('lesson.resultsTitle')}</h2>
        <p className="lesson__score">
          {t('lesson.score', { score: result.score, total: lesson.exercises.length })}
        </p>
        <p className="lesson__xp">{t('lesson.xpEarned', { xp: result.xpEarned })}</p>
        <p className="lesson__hearts">{t('lesson.heartsLeft', { hearts: result.stats.hearts })}</p>
        {savedOffline && (
          <p className="lesson__offline" role="status">
            {t('lesson.savedOffline')}
          </p>
        )}
        <button type="button" className="lesson__exit" onClick={onExit}>
          {t('lesson.backToCourse')}
        </button>
      </section>
    )
  }

  const exercise: ExerciseView = lesson.exercises[index]
  const isText = TEXT_TYPES.includes(exercise.type)
  const isMcq = exercise.type === 'MCQ'
  const isWordBank = exercise.type === 'WORD_BANK'
  const tokens = exercise.config.tokens ?? []
  const isLast = index === lesson.exercises.length - 1
  const answered = verdict !== null
  const canCheck =
    !busy &&
    !answered &&
    (isText
      ? answer.trim().length > 0
      : isWordBank
        ? placed.length === tokens.length && tokens.length > 0
        : answer.length > 0)

  const buildPayload = (): AnswerPayload => {
    if (isText) return { text: answer }
    if (isWordBank) {
      // Grading joins the arranged words: send the token text in placed order.
      const byId = new Map(tokens.map((tk) => [tk.id, tk.text.en]))
      return { tokenOrder: placed.map((id) => byId.get(id) ?? '') }
    }
    return { selectedOptionId: answer }
  }

  const check = () => {
    const token = getToken()
    if (!token || !canCheck) return
    setBusy(true)
    submitAnswer(token, session.id, {
      idempotencyKey: crypto.randomUUID(),
      exerciseId: exercise.id,
      answer: buildPayload(),
    })
      .then((res) => {
        setVerdict(res.verdict)
        setHearts(res.stats.hearts)
        if (res.verdict.correct) setCorrectCount((c) => c + 1)
      })
      .catch(() => setVerdict({ correct: false, feedback: { en: '', bn: '' }, matchedPatternCode: null, source: 'RULE' }))
      .finally(() => setBusy(false))
  }

  const finishOffline = () => {
    // Buffer the completion so progress reconciles on reconnect (M5, D2); show results now.
    enqueue({
      idempotencyKey: crypto.randomUUID(),
      type: 'COMPLETE_LESSON',
      payload: { lessonId, score: correctCount },
    })
    setSavedOffline(true)
    setResult({
      score: correctCount,
      xpEarned: 0,
      newlyUnlocked: [],
      reviewItemsAdded: 0,
      stats: { hearts, xp: 0, rank: 0, currentStreak: 0, longestStreak: 0, dailyGoal: 0, cefrLevel: 'A1', accuracyByPattern: {} },
    })
    setPhase('finished')
  }

  const next = () => {
    const token = getToken()
    if (!token) return
    if (!isLast) {
      setIndex((i) => i + 1)
      setAnswer('')
      setPlaced([])
      setVerdict(null)
      return
    }
    setBusy(true)
    completeSession(token, session.id, crypto.randomUUID())
      .then((res) => {
        setResult(res)
        setPhase('finished')
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.isNetwork || err.status >= 500)) {
          finishOffline()
        } else {
          setPhase('error')
        }
      })
      .finally(() => setBusy(false))
  }

  return (
    <section className="lesson" aria-label={label(lesson.title)}>
      <div className="lesson__bar">
        <button type="button" className="lesson__exit" onClick={onExit}>
          ✕
        </button>
        <span className="lesson__progress">
          {index + 1} / {lesson.exercises.length}
        </span>
        <span className="lesson__hearts" aria-label={t('lesson.heartsLeft', { hearts })}>
          {'❤'.repeat(hearts) || '—'}
        </span>
      </div>

      <h2 className="lesson__prompt" ref={promptRef} tabIndex={-1}>
        {label(exercise.prompt)}
      </h2>

      {isMcq && (
        <ul className="lesson__options">
          {(exercise.config.options ?? []).map((opt) => (
            <li key={opt.id}>
              <button
                type="button"
                className={`lesson__option${answer === opt.id ? ' lesson__option--selected' : ''}`}
                aria-pressed={answer === opt.id}
                disabled={answered}
                onClick={() => setAnswer(opt.id)}
              >
                {label(opt.text)}
              </button>
            </li>
          ))}
        </ul>
      )}

      {isText && (
        <input
          className="lesson__input"
          type="text"
          aria-label={t('lesson.answerLabel')}
          placeholder={t('lesson.answerPlaceholder')}
          value={answer}
          disabled={answered}
          onChange={(e) => setAnswer(e.target.value)}
        />
      )}

      {isWordBank && (
        <div className="lesson__wordbank">
          <div
            className="lesson__sentence"
            aria-label={t('lesson.sentenceLabel')}
            aria-live="polite"
          >
            {placed.length === 0 ? (
              <span className="lesson__sentence-hint">{t('lesson.wordBankHint')}</span>
            ) : (
              placed.map((id) => {
                const tk = tokens.find((x) => x.id === id)
                return (
                  <button
                    key={id}
                    type="button"
                    className="lesson__token lesson__token--placed"
                    disabled={answered}
                    onClick={() => setPlaced((p) => p.filter((x) => x !== id))}
                  >
                    {tk ? tk.text.en : ''}
                  </button>
                )
              })
            )}
          </div>
          <ul className="lesson__bank">
            {tokens
              .filter((tk) => !placed.includes(tk.id))
              .map((tk) => (
                <li key={tk.id}>
                  <button
                    type="button"
                    className="lesson__token"
                    disabled={answered}
                    onClick={() => setPlaced((p) => [...p, tk.id])}
                  >
                    {tk.text.en}
                  </button>
                </li>
              ))}
          </ul>
        </div>
      )}

      {!isMcq && !isText && !isWordBank && (
        <p className="lesson__status">{t('lesson.unsupported')}</p>
      )}

      {answered && verdict && (
        <div
          className={`lesson__feedback lesson__feedback--${verdict.correct ? 'ok' : 'no'}`}
          role="status"
        >
          <strong>{verdict.correct ? t('lesson.correct') : t('lesson.incorrect')}</strong>
          {verdict.feedback && <p>{label(verdict.feedback)}</p>}
        </div>
      )}

      {!answered ? (
        <button type="button" className="lesson__check" disabled={!canCheck} onClick={check}>
          {t('lesson.check')}
        </button>
      ) : (
        <button type="button" className="lesson__check" disabled={busy} onClick={next}>
          {isLast ? t('lesson.finish') : t('lesson.next')}
        </button>
      )}
    </section>
  )
}
