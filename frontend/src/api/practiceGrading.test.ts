import { describe, expect, it } from 'vitest'
import { gradeLocally, normalizeAnswer, type PracticeExercise } from './practice'

// Local, offline equivalent of the server verdict (E12 batch grading). normalizeAnswer must
// match backend/src/main/java/com/shikhi/learning/grading/AnswerNormalizer.java exactly —
// trim -> collapse internal whitespace -> lowercase -> strip one trailing run of . ! ? or ।.

describe('normalizeAnswer', () => {
  it('trims, collapses internal whitespace, and lowercases', () => {
    expect(normalizeAnswer('  Hello   World  ')).toBe('hello world')
  })

  it('strips a single trailing run of sentence punctuation', () => {
    expect(normalizeAnswer('Good morning.')).toBe('good morning')
    expect(normalizeAnswer('Really?!')).toBe('really')
    expect(normalizeAnswer('সে ভালো আছে।')).toBe('সে ভালো আছে')
  })

  it('does not touch internal punctuation', () => {
    expect(normalizeAnswer("it's fine.")).toBe("it's fine")
  })
})

function exercise(over: Partial<PracticeExercise>): PracticeExercise {
  return {
    id: 'ex1',
    type: 'WORD_MEANING',
    ordinal: 1,
    prompt: { en: 'p', bn: 'p' },
    config: {},
    solution: { reveal: 'reveal-text' },
    ...over,
  }
}

describe('gradeLocally', () => {
  it('grades WORD_MEANING/MEANING_WORD/SENTENCE_GAP (MCQ) correct', () => {
    const ex = exercise({ type: 'WORD_MEANING', solution: { correctOptionId: 'opt-a', reveal: 'advice' } })
    const v = gradeLocally(ex, { selectedOptionId: 'opt-a' })
    expect(v).toEqual({ correct: true, feedback: { en: 'Correct!', bn: 'সঠিক!' } })
  })

  it('grades MCQ wrong, revealing the correct answer', () => {
    const ex = exercise({ type: 'MEANING_WORD', solution: { correctOptionId: 'opt-a', reveal: 'advice' } })
    const v = gradeLocally(ex, { selectedOptionId: 'opt-b' })
    expect(v.correct).toBe(false)
    expect(v.feedback).toEqual({ en: 'Correct answer: advice', bn: 'সঠিক উত্তর: advice' })
  })

  it('grades SENTENCE_GAP as MCQ too', () => {
    const ex = exercise({ type: 'SENTENCE_GAP', solution: { correctOptionId: 'opt-x', reveal: 'x' } })
    expect(gradeLocally(ex, { selectedOptionId: 'opt-x' }).correct).toBe(true)
    expect(gradeLocally(ex, { selectedOptionId: 'opt-y' }).correct).toBe(false)
  })

  it('grades TYPE_WORD with normalization (whitespace/case/trailing punctuation)', () => {
    const ex = exercise({ type: 'TYPE_WORD', solution: { accepted: ['air'], reveal: 'air — বাতাস' } })
    expect(gradeLocally(ex, { text: '  Air.  ' }).correct).toBe(true)
    expect(gradeLocally(ex, { text: 'AIR' }).correct).toBe(true)
    const wrong = gradeLocally(ex, { text: 'water' })
    expect(wrong.correct).toBe(false)
    expect(wrong.feedback).toEqual({
      en: 'Correct answer: air — বাতাস',
      bn: 'সঠিক উত্তর: air — বাতাস',
    })
  })

  it('grades SENTENCE_BUILD by joining token order and normalizing, correct order', () => {
    const ex = exercise({
      type: 'SENTENCE_BUILD',
      solution: { accepted: ['I am fine'], reveal: 'I am fine' },
    })
    expect(gradeLocally(ex, { tokenOrder: ['I', 'am', 'fine'] }).correct).toBe(true)
    // Trailing punctuation / case variance in a token still normalizes to an accepted match.
    expect(gradeLocally(ex, { tokenOrder: ['I', 'AM', 'fine.'] }).correct).toBe(true)
  })

  it('grades SENTENCE_BUILD wrong order as incorrect, with reveal', () => {
    const ex = exercise({
      type: 'SENTENCE_BUILD',
      solution: { accepted: ['I am fine'], reveal: 'I am fine' },
    })
    const v = gradeLocally(ex, { tokenOrder: ['fine', 'am', 'I'] })
    expect(v.correct).toBe(false)
    expect(v.feedback).toEqual({ en: 'Correct answer: I am fine', bn: 'সঠিক উত্তর: I am fine' })
  })
})
