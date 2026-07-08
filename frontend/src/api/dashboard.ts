import { apiFetch } from './client'
import type { Stats } from './sessions'

// Mirrors DashboardResponse/WordMasteryEntry (contract, E13; read-only). One round-trip
// snapshot for the profile/dashboard surface: hot-path stats plus per-band word mastery,
// review load, and lifetime totals.

export interface WordMasteryEntry {
  cefrLevel: string
  /** Words answered correctly at least once. */
  mastered: number
  /** Words in this band's vocabulary. */
  total: number
}

export interface DashboardResponse {
  stats: Stats
  /** One entry per CEFR band A1-C1, ordered. */
  wordMastery: WordMasteryEntry[]
  reviewDueCount: number
  lessonsCompleted: number
  practiceSessionsCompleted: number
  /** Lifetime graded answers (lessons + practice). */
  totalAnswered: number
  totalCorrect: number
}

export function fetchDashboard(token: string): Promise<DashboardResponse> {
  return apiFetch<DashboardResponse>('/dashboard', { token })
}
