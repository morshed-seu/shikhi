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
