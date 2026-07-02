import { apiFetch } from './client'
import type { Bilingual } from './curriculum'

// Spaced-repetition review (contract ReviewItem, ReviewResultsRequest). Review is self-graded
// recall in M6 — the item carries only the prompt, never the answer.

export interface ReviewItem {
  exerciseId: string
  prompt: Bilingual
  boxLevel: number
  dueAt: string
}

export interface ReviewResult {
  exerciseId: string
  correct: boolean
}

export function fetchDue(token: string): Promise<ReviewItem[]> {
  return apiFetch<ReviewItem[]>('/review/due', { token })
}

export function postResults(token: string, results: ReviewResult[]): Promise<void> {
  return apiFetch<void>('/review/results', { method: 'POST', token, body: { results } })
}
