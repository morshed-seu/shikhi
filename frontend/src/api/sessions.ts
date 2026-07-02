import { apiFetch } from './client'
import type { Bilingual } from './curriculum'

// Lesson session play-through (contract LessonSession, SubmitAnswerRequest, AnswerResult,
// LessonResult). Correct answers are never sent to the client — grading is server-side.

export interface LessonSession {
  id: string
  lessonId: string
  contentVersion: string
  heartsRemaining: number
  status: string
}

export interface Verdict {
  correct: boolean
  feedback: Bilingual
  matchedPatternCode: string | null
  source: string
}

export interface Stats {
  hearts: number
  xp: number
  currentStreak: number
  longestStreak: number
  rank: number
  dailyGoal: number
  accuracyByPattern: Record<string, number>
}

export interface AnswerResult {
  verdict: Verdict
  stats: Stats
}

export interface LessonResult {
  score: number
  xpEarned: number
  newlyUnlocked: string[]
  reviewItemsAdded: number
  stats: Stats
}

/** A type-specific answer payload, e.g. {selectedOptionId} (MCQ) or {text} (translation). */
export type AnswerPayload = Record<string, unknown>

export function fetchStats(token: string): Promise<Stats> {
  return apiFetch<Stats>('/stats', { token })
}

export function startSession(token: string, lessonId: string): Promise<LessonSession> {
  return apiFetch<LessonSession>('/sessions', { method: 'POST', token, body: { lessonId } })
}

export function submitAnswer(
  token: string,
  sessionId: string,
  input: { idempotencyKey: string; exerciseId: string; answer: AnswerPayload },
): Promise<AnswerResult> {
  return apiFetch<AnswerResult>(`/sessions/${sessionId}/answers`, {
    method: 'POST',
    token,
    body: input,
  })
}

export function completeSession(
  token: string,
  sessionId: string,
  idempotencyKey: string,
): Promise<LessonResult> {
  return apiFetch<LessonResult>(`/sessions/${sessionId}/complete`, {
    method: 'POST',
    token,
    body: { idempotencyKey },
  })
}
