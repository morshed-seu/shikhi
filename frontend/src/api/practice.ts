import { apiFetch } from './client'
import type { Bilingual } from './curriculum'
import type { Stats } from './sessions'

// Adaptive vocabulary practice (contract /practice/sessions/*, E12). Exercises are
// generated server-side from the learner's CEFR band. Each exercise ships its `solution`
// (ADR-0013) so the web client grades instantly and locally; the server still re-grades
// authoritatively when the batch is submitted, so client verdicts are display-only.

export type PracticeExerciseType =
  | 'WORD_MEANING'
  | 'MEANING_WORD'
  | 'SENTENCE_GAP'
  | 'SENTENCE_BUILD'
  | 'TYPE_WORD'

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1'

export const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1']

/** The band after `level`, or null at the top. */
export function nextLevel(level: string): CefrLevel | null {
  const i = CEFR_LEVELS.indexOf(level as CefrLevel)
  return i >= 0 && i + 1 < CEFR_LEVELS.length ? CEFR_LEVELS[i + 1] : null
}

export interface PracticeOption {
  id: string
  text: Bilingual
}

/**
 * The answer key for one exercise, now shipped alongside it so the client can grade
 * instantly offline (E12 batch grading). `correctOptionId` is set for the three MCQ types
 * (WORD_MEANING/MEANING_WORD/SENTENCE_GAP); `accepted` is set for SENTENCE_BUILD/TYPE_WORD.
 * `reveal` is the correct-answer text shown in wrong-answer feedback, for every type.
 */
export interface PracticeSolution {
  correctOptionId?: string
  accepted?: string[]
  reveal: string
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
  solution: PracticeSolution
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

export interface PracticeBatchAnswerInput {
  idempotencyKey: string
  exerciseId: string
  answer: PracticeAnswerPayload
}

export interface PracticeBatchResult {
  stats: Stats
  verdicts: { exerciseId: string; correct: boolean }[]
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

/**
 * Batch submit of buffered answers (E12 batch grading). Routed through the offline outbox
 * for durability — see src/api/outbox.ts — rather than called directly from the UI.
 */
export function submitPracticeAnswersBatch(
  token: string,
  sessionId: string,
  answers: PracticeBatchAnswerInput[],
): Promise<PracticeBatchResult> {
  return apiFetch<PracticeBatchResult>(`/practice/sessions/${sessionId}/answers/batch`, {
    method: 'POST',
    token,
    body: { answers },
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

// --- Local grading (E12 batch grading) ---------------------------------------------------
//
// The client now grades instantly for display, buffers the answer, and submits a batch later;
// the server always re-grades authoritatively from the same rules, so a client verdict is
// display-only. `normalizeAnswer` mirrors backend/.../grading/AnswerNormalizer.java EXACTLY:
// trim -> collapse internal whitespace to a single space -> lowercase -> strip one trailing
// run of sentence punctuation (. ! ? or the Bengali danda ।).
export function normalizeAnswer(raw: string): string {
  if (raw == null) return ''
  const collapsed = raw.trim().replace(/\s+/g, ' ').toLowerCase()
  return collapsed.replace(/[.!?।]+$/, '').trim()
}

/** Local, offline equivalent of the server verdict for one exercise/answer pair. */
export function gradeLocally(
  exercise: PracticeExercise,
  answer: PracticeAnswerPayload,
): PracticeVerdict {
  const { solution } = exercise
  let correct: boolean

  if (exercise.type === 'SENTENCE_BUILD') {
    const tokenOrder = Array.isArray(answer.tokenOrder) ? (answer.tokenOrder as unknown[]) : []
    const joined = normalizeAnswer(tokenOrder.map((t) => String(t)).join(' '))
    correct = (solution.accepted ?? []).some((a) => normalizeAnswer(a) === joined)
  } else if (exercise.type === 'TYPE_WORD') {
    const text = typeof answer.text === 'string' ? answer.text : ''
    const normalized = normalizeAnswer(text)
    correct = (solution.accepted ?? []).some((a) => normalizeAnswer(a) === normalized)
  } else {
    // WORD_MEANING / MEANING_WORD / SENTENCE_GAP (MCQ types).
    correct = answer.selectedOptionId === solution.correctOptionId
  }

  return correct
    ? { correct: true, feedback: { en: 'Correct!', bn: 'সঠিক!' } }
    : {
        correct: false,
        feedback: {
          en: `Correct answer: ${solution.reveal}`,
          bn: `সঠিক উত্তর: ${solution.reveal}`,
        },
      }
}
