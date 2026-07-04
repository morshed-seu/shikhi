import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Bilingual } from '../api/curriculum'
import {
  completePractice,
  nextLevel,
  nextPracticeRound,
  type PracticeAnswerPayload,
  type PracticeExercise,
  type PracticeResult,
  type PracticeRound,
  setLevel,
  startPractice,
  submitPracticeAnswer,
  type PracticeVerdict,
} from '../api/practice'
import { fetchStats } from '../api/sessions'
import { useAuth } from '../auth/useAuth'

interface Props {
  onExit: () => void
}

type Phase = 'loading' | 'error' | 'playing' | 'roundDone' | 'finished'

const XP_PER_CORRECT = 10

/**
 * The continuous practice flow (E12, US-12.3): exercises keep coming in rounds; each round
 * ends on a summary with one-tap "Keep going". The segmented bar across the top is the
 * round's matra — every answer draws one stroke of it (ink for right, clay for wrong).
 */
export function PracticePlayer({ onExit }: Props) {
  const { t, i18n } = useTranslation()
  const { getToken } = useAuth()

  const [phase, setPhase] = useState<Phase>('loading')
  const [round, setRound] = useState<PracticeRound | null>(null)
  const [index, setIndex] = useState(0)
  const [strokes, setStrokes] = useState<boolean[]>([]) // per-exercise verdicts this round
  const [hearts, setHearts] = useState<number | null>(null)
  const [answer, setAnswer] = useState('')
  const [placed, setPlaced] = useState<string[]>([])
  const [verdict, setVerdict] = useState<PracticeVerdict | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<PracticeResult | null>(null)
  const [leveledUpTo, setLeveledUpTo] = useState<string | null>(null)
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
    startPractice(token)
      .then((r) => {
        setRound(r)
        setPhase('playing')
      })
      .catch(() => setPhase('error'))
    // Hearts are shown from the first exercise on, not only after the first answer.
    fetchStats(token)
      .then((s) => setHearts((h) => (h === null ? s.hearts : h)))
      .catch(() => undefined)
  }, [getToken])

  const resetExerciseState = () => {
    setAnswer('')
    setPlaced([])
    setVerdict(null)
  }

  const finish = () => {
    const token = getToken()
    if (!token || !round) {
      onExit()
      return
    }
    setBusy(true)
    completePractice(token, round.sessionId, crypto.randomUUID())
      .then((r) => {
        setResult(r)
        setPhase('finished')
      })
      .catch(onExit)
      .finally(() => setBusy(false))
  }

  /** Leaving mid-session still finalizes it (idempotent server-side). */
  const exit = () => {
    const token = getToken()
    if (token && round && phase !== 'finished') {
      void completePractice(token, round.sessionId, crypto.randomUUID()).catch(() => undefined)
    }
    onExit()
  }

  if (phase === 'loading') return <p className="lesson__status">{t('practice.loading')}</p>
  if (phase === 'error' || !round) {
    return (
      <section className="lesson">
        <p className="auth__error">{t('practice.error')}</p>
        <button type="button" className="lesson__exit" onClick={onExit}>
          {t('practice.backHome')}
        </button>
      </section>
    )
  }

  const roundCorrect = strokes.filter(Boolean).length

  if (phase === 'finished' && result) {
    return (
      <section className="practice" aria-label={t('practice.resultsTitle')}>
        <h2 className="practice__cheer">{t('practice.resultsTitle')}</h2>
        <p className="practice__score">
          {t('practice.sessionScore', { correct: result.correctCount, total: result.totalCount })}
        </p>
        <p className="practice__xp">{t('practice.xpEarned', { xp: result.xpEarned })}</p>
        <button type="button" className="practice__primary" onClick={onExit}>
          {t('practice.backHome')}
        </button>
      </section>
    )
  }

  if (phase === 'roundDone') {
    const upTo = nextLevel(round.cefrLevel)
    const acceptLevelUp = () => {
      const token = getToken()
      if (!token || !upTo) return
      setLevel(token, upTo)
        .then((s) => setLeveledUpTo(s.cefrLevel))
        .catch(() => undefined)
    }
    const keepGoing = () => {
      const token = getToken()
      if (!token) return
      setBusy(true)
      nextPracticeRound(token, round.sessionId)
        .then((r) => {
          setRound(r)
          setIndex(0)
          setStrokes([])
          resetExerciseState()
          setPhase('playing')
        })
        .catch(() => setPhase('error'))
        .finally(() => setBusy(false))
    }
    return (
      <section className="practice" aria-label={t('practice.roundTitle')}>
        <h2 className="practice__cheer">{t('practice.roundTitle')}</h2>
        <p className="practice__score">
          {t('practice.roundScore', { correct: roundCorrect, total: strokes.length })}
        </p>
        <p className="practice__xp">
          {t('practice.xpEarned', { xp: roundCorrect * XP_PER_CORRECT })}
        </p>
        {round.levelUpEligible && upTo && !leveledUpTo && (
          <div className="practice__levelup" role="status">
            <p>{t('practice.levelUpOffer', { level: upTo })}</p>
            <button type="button" className="practice__levelup-btn" onClick={acceptLevelUp}>
              {t('practice.levelUpAccept', { level: upTo })}
            </button>
          </div>
        )}
        {leveledUpTo && (
          <p className="practice__levelup-done" role="status">
            {t('practice.levelUpDone', { level: leveledUpTo })}
          </p>
        )}
        <div className="practice__summary-actions">
          <button type="button" className="practice__primary" disabled={busy} onClick={keepGoing}>
            {t('practice.keepGoing')}
          </button>
          <button type="button" className="practice__ghost" disabled={busy} onClick={finish}>
            {t('practice.finish')}
          </button>
        </div>
      </section>
    )
  }

  const exercise: PracticeExercise = round.exercises[index]
  const isMcq =
    exercise.type === 'WORD_MEANING' ||
    exercise.type === 'MEANING_WORD' ||
    exercise.type === 'SENTENCE_GAP'
  const isBuild = exercise.type === 'SENTENCE_BUILD'
  const isType = exercise.type === 'TYPE_WORD'
  const tokens = exercise.config.tokens ?? []
  const answered = verdict !== null
  const canCheck =
    !busy &&
    !answered &&
    (isType
      ? answer.trim().length > 0
      : isBuild
        ? placed.length === tokens.length && tokens.length > 0
        : answer.length > 0)

  const buildPayload = (): PracticeAnswerPayload => {
    if (isType) return { text: answer }
    if (isBuild) {
      const byId = new Map(tokens.map((tk) => [tk.id, tk.text.en]))
      return { tokenOrder: placed.map((id) => byId.get(id) ?? '') }
    }
    return { selectedOptionId: answer }
  }

  const check = () => {
    const token = getToken()
    if (!token || !canCheck) return
    setBusy(true)
    submitPracticeAnswer(token, round.sessionId, {
      idempotencyKey: crypto.randomUUID(),
      exerciseId: exercise.id,
      answer: buildPayload(),
    })
      .then((res) => {
        setVerdict(res.verdict)
        setHearts(res.stats.hearts)
        setStrokes((s) => [...s, res.verdict.correct])
      })
      .catch(() => setVerdict({ correct: false, feedback: { en: '', bn: '' } }))
      .finally(() => setBusy(false))
  }

  const next = () => {
    if (index + 1 < round.exercises.length) {
      setIndex((i) => i + 1)
      resetExerciseState()
    } else {
      setPhase('roundDone')
    }
  }

  return (
    <section className="practice" aria-label={t('practice.heroTitle')}>
      <div className="practice__bar">
        <button
          type="button"
          className="lesson__exit"
          onClick={exit}
          aria-label={t('practice.exit')}
        >
          ✕
        </button>
        {/* The round's matra: one stroke per exercise, drawn as you answer. */}
        <div
          className="practice__matra"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={round.exercises.length}
          aria-valuenow={strokes.length}
          aria-label={t('practice.progress', {
            done: strokes.length,
            total: round.exercises.length,
          })}
        >
          {round.exercises.map((e, i) => {
            const state =
              i < strokes.length
                ? strokes[i]
                  ? 'practice__stroke--ok'
                  : 'practice__stroke--no'
                : i === index
                  ? 'practice__stroke--current'
                  : ''
            return <span key={e.id} className={`practice__stroke ${state}`} />
          })}
        </div>
        <span className="lesson__hearts" aria-label={t('lesson.heartsLeft', { hearts: hearts ?? '' })}>
          {hearts === null ? '' : '❤'.repeat(hearts) || '—'}
        </span>
      </div>

      <h2 className="lesson__prompt" ref={promptRef} tabIndex={-1}>
        {label(exercise.prompt)}
      </h2>

      {exercise.type === 'SENTENCE_GAP' && exercise.config.contextBn && (
        <p className="practice__context">{exercise.config.contextBn}</p>
      )}
      {isType && exercise.config.partOfSpeech && (
        <p className="practice__hint">{exercise.config.partOfSpeech}</p>
      )}

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

      {isType && (
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

      {isBuild && (
        <div className="lesson__wordbank">
          {exercise.config.targetBn && (
            <p className="practice__target">{exercise.config.targetBn}</p>
          )}
          <div className="lesson__sentence" aria-label={t('lesson.sentenceLabel')} aria-live="polite">
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

      {answered && verdict && (
        <div
          className={`lesson__feedback lesson__feedback--${verdict.correct ? 'ok' : 'no'}`}
          role="status"
        >
          <strong>{verdict.correct ? t('lesson.correct') : t('lesson.incorrect')}</strong>
          {/* The body carries the reveal on wrong answers; on right ones it would just
              repeat the label. */}
          {!verdict.correct && (verdict.feedback.en || verdict.feedback.bn) && (
            <p>{label(verdict.feedback)}</p>
          )}
        </div>
      )}

      {!answered ? (
        <button type="button" className="lesson__check" disabled={!canCheck} onClick={check}>
          {t('lesson.check')}
        </button>
      ) : (
        <button type="button" className="lesson__check" disabled={busy} onClick={next}>
          {index + 1 < round.exercises.length ? t('lesson.next') : t('practice.toSummary')}
        </button>
      )}
    </section>
  )
}
