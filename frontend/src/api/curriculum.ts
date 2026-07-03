import { apiFetch } from './client'

// Mirrors docs/43-api-contract.openapi.yaml (CurriculumTree, Level, Unit, LessonSummary).

export interface Bilingual {
  en: string
  bn: string
}

export interface LessonNode {
  id: string
  title: Bilingual
  ordinal: number
  status: string
  locked: boolean
}

export interface UnitNode {
  id: string
  code: string
  title: Bilingual
  ordinal: number
  locked: boolean
  lessons: LessonNode[]
}

export interface LevelNode {
  id: string
  code: string
  title: Bilingual
  ordinal: number
  units: UnitNode[]
}

export interface CurriculumTree {
  contentVersion: string | null
  levels: LevelNode[]
}

export function fetchCurriculum(token: string): Promise<CurriculumTree> {
  return apiFetch<CurriculumTree>('/curriculum', { token })
}

// A playable lesson (contract Lesson). config carries only render data — never correctness.

export interface McqOption {
  id: string
  text: Bilingual
}

// A word-bank token: a word to arrange. Shape mirrors McqOption (id + bilingual text).
export type WordToken = McqOption

export interface ExerciseView {
  id: string
  type: string
  ordinal: number
  prompt: Bilingual
  mediaRef: string | null
  patternTags: string[]
  config: { options?: McqOption[]; tokens?: WordToken[] }
}

export interface LessonView {
  id: string
  contentVersion: string
  title: Bilingual
  exercises: ExerciseView[]
}

export function fetchLesson(token: string, lessonId: string): Promise<LessonView> {
  return apiFetch<LessonView>(`/lessons/${lessonId}`, { token })
}
