import { apiFetch } from './client'
import type { Bilingual } from './curriculum'
import type { Stats } from './sessions'

// Adaptive vocabulary practice (contract /practice/sessions/*, E12). Exercises are
// generated server-side from the learner's CEFR band; correct answers never reach the
// client — grading happens against a server-only answer key.

export type PracticeExerciseType =
  | 'WORD_MEANING'
  | 'MEANING_WORD'
  | 'SENTENCE_GAP'
  | 'SENTENCE_BUILD'
  | 'TYPE_WORD'

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2'

export const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2']

/** The band after `level`, or null at the top. */
export function nextLevel(level: string): CefrLevel | null {
  const i = CEFR_LEVELS.indexOf(level as CefrLevel)
  return i >= 0 && i + 1 < CEFR_LEVELS.length ? CEFR_LEVELS[i + 1] : null
}

export interface PracticeOption {
  id: string
  text: Bilingual
}

export interface PracticeExercise {
  id: string
  type: PracticeExerciseType
  ordinal: number
  prompt: Bilingual
  config: {
    options?: PracticeOption[]
    tokens?: PracticeOption[]
    contextBn?: string
    targetBn?: string
    partOfSpeech?: string
  }
}

export interface PracticeRound {
  sessionId: string
  round: number
  cefrLevel: CefrLevel
  levelUpEligible: boolean
  exercises: PracticeExercise[]
}

export interface PracticeVerdict {
  correct: boolean
  feedback: Bilingual
}

export interface PracticeAnswerResult {
  verdict: PracticeVerdict
  stats: Stats
}

export interface PracticeResult {
  correctCount: number
  totalCount: number
  roundsPlayed: number
  xpEarned: number
  levelUpEligible: boolean
  stats: Stats
}

/** {selectedOptionId} (MCQ formats), {tokenOrder} (SENTENCE_BUILD) or {text} (TYPE_WORD). */
export type PracticeAnswerPayload = Record<string, unknown>

export function startPractice(token: string): Promise<PracticeRound> {
  return apiFetch<PracticeRound>('/practice/sessions', { method: 'POST', token })
}

export function submitPracticeAnswer(
  token: string,
  sessionId: string,
  input: { idempotencyKey: string; exerciseId: string; answer: PracticeAnswerPayload },
): Promise<PracticeAnswerResult> {
  return apiFetch<PracticeAnswerResult>(`/practice/sessions/${sessionId}/answers`, {
    method: 'POST',
    token,
    body: input,
  })
}

export function nextPracticeRound(token: string, sessionId: string): Promise<PracticeRound> {
  return apiFetch<PracticeRound>(`/practice/sessions/${sessionId}/rounds`, {
    method: 'POST',
    token,
  })
}

export function completePractice(
  token: string,
  sessionId: string,
  idempotencyKey: string,
): Promise<PracticeResult> {
  return apiFetch<PracticeResult>(`/practice/sessions/${sessionId}/complete`, {
    method: 'POST',
    token,
    body: { idempotencyKey },
  })
}

/** Self-placement or an accepted level-up (PUT /stats/level). */
export function setLevel(token: string, cefrLevel: CefrLevel): Promise<Stats> {
  return apiFetch<Stats>('/stats/level', { method: 'PUT', token, body: { cefrLevel } })
}
