import { apiFetch } from './client'

// Mirrors backend VocabularyEntry (GET /v1/vocabulary?level=A1). A flat, CEFR-tagged
// dictionary of the Oxford-3000 words, each with a Bengali gloss and a bilingual example.

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2'

export interface VocabularyEntry {
  id: string
  headword: string
  senseLabel: string | null
  partOfSpeech: string
  cefrLevel: CefrLevel
  bnGloss: string
  exampleEn: string | null
  exampleBn: string | null
}

export function fetchVocabulary(token: string, level: CefrLevel): Promise<VocabularyEntry[]> {
  return apiFetch<VocabularyEntry[]>(`/vocabulary?level=${level}`, { token })
}
