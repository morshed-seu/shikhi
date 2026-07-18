import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
}

const stats = (over: Partial<Record<string, unknown>> = {}) => ({
  hearts: 5,
  xp: 0,
  rank: 0,
  currentStreak: 1,
  longestStreak: 1,
  dailyGoal: 20,
  cefrLevel: 'A1',
  accuracyByPattern: {},
  ...over,
})

const ROUND = {
  sessionId: 'ps1',
  round: 1,
  cefrLevel: 'A1',
  levelUpEligible: true,
  exercises: [
    {
      id: 'pex1',
      type: 'WORD_MEANING',
      ordinal: 1,
      prompt: { en: 'What does “advice” mean?', bn: '“advice” শব্দের অর্থ কী?' },
      config: {
        options: [
          { id: 'opt-good', text: { en: 'পরামর্শ', bn: 'পরামর্শ' } },
          { id: 'opt-bad', text: { en: 'টেবিল', bn: 'টেবিল' } },
        ],
      },
    },
    {
      id: 'pex2',
      type: 'TYPE_WORD',
      ordinal: 2,
      prompt: { en: 'Type the English word for “বাতাস”', bn: '“বাতাস” — এর ইংরেজি শব্দটি লিখুন' },
      config: { partOfSpeech: 'n.' },
    },
  ],
}

const ROUND_PRONOUNCE = {
  sessionId: 'ps2',
  round: 1,
  cefrLevel: 'A1',
  levelUpEligible: false,
  exercises: [
    {
      id: 'pex3',
      type: 'MEANING_WORD',
      ordinal: 1,
      prompt: { en: 'Which English word means “বাতাস”?', bn: '“বাতাস” — এর ইংরেজি শব্দ কোনটি?' },
      config: {
        options: [
          { id: 'opt-air', text: { en: 'air', bn: 'air' } },
          { id: 'opt-water', text: { en: 'water', bn: 'water' } },
        ],
      },
    },
    {
      id: 'pex4',
      type: 'SENTENCE_BUILD',
      ordinal: 2,
      prompt: { en: 'Build the sentence: “আমি ভালো আছি।”', bn: 'শব্দ সাজিয়ে বাক্যটি তৈরি করুন: “আমি ভালো আছি।”' },
      config: {
        tokens: [
          { id: 'tk-i', text: { en: 'I', bn: 'I' } },
          { id: 'tk-am', text: { en: 'am', bn: 'am' } },
          { id: 'tk-fine', text: { en: 'fine', bn: 'fine' } },
        ],
        targetBn: 'আমি ভালো আছি।',
      },
    },
  ],
}

describe('PracticePlayer (E12)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('runs a round: answer, feedback with reveal, summary with level-up, finish', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me')
          return Promise.resolve(jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }))
        if (url === '/v1/curriculum') return Promise.resolve(jsonResponse({ contentVersion: 'v1', levels: [] }))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/stats' && method === 'GET') return Promise.resolve(jsonResponse(stats()))
        if (url === '/v1/practice/sessions' && method === 'POST')
          return Promise.resolve(jsonResponse(ROUND, 201))
        if (url === '/v1/practice/sessions/ps1/answers' && method === 'POST') {
          const body = JSON.parse(opts?.body ?? '{}') as { exerciseId: string }
          if (body.exerciseId === 'pex1')
            return Promise.resolve(
              jsonResponse({
                verdict: { correct: true, feedback: { en: 'Correct!', bn: 'সঠিক!' } },
                stats: stats({ xp: 10 }),
              }),
            )
          return Promise.resolve(
            jsonResponse({
              verdict: {
                correct: false,
                feedback: { en: 'Correct answer: air — বাতাস', bn: 'সঠিক উত্তর: air — বাতাস' },
              },
              stats: stats({ xp: 10, hearts: 4 }),
            }),
          )
        }
        if (url === '/v1/stats/level' && method === 'PUT')
          return Promise.resolve(jsonResponse(stats({ cefrLevel: 'A2' })))
        if (url === '/v1/practice/sessions/ps1/complete' && method === 'POST')
          return Promise.resolve(
            jsonResponse({
              correctCount: 1,
              totalCount: 2,
              roundsPlayed: 1,
              xpEarned: 10,
              levelUpEligible: true,
              stats: stats({ xp: 10, hearts: 4 }),
            }),
          )
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    // One tap starts the session (US-12.2).
    const start = await screen.findByRole('button', { name: 'সেশন শুরু করুন' })
    fireEvent.click(start)

    // Exercise 1 — MCQ: pick the right gloss, get positive feedback.
    await screen.findByText('“advice” শব্দের অর্থ কী?')
    fireEvent.click(screen.getByRole('button', { name: 'পরামর্শ' }))
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await screen.findByText('সঠিক!', { selector: 'strong' })

    // Exercise 2 — typed word: a wrong answer reveals the correct one.
    fireEvent.click(screen.getByRole('button', { name: 'পরবর্তী' }))
    await screen.findByText('“বাতাস” — এর ইংরেজি শব্দটি লিখুন')
    fireEvent.change(screen.getByLabelText('আপনার উত্তর'), { target: { value: 'water' } })
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await screen.findByText('সঠিক উত্তর: air — বাতাস')

    // Round summary: score, XP, and the level-up suggestion (US-12.7).
    fireEvent.click(screen.getByRole('button', { name: 'রাউন্ডের ফলাফল দেখুন' }))
    await screen.findByText('রাউন্ড শেষ!')
    expect(screen.getByText('2টির মধ্যে 1টি সঠিক')).toBeInTheDocument()
    expect(screen.getByText('+10 XP')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'A2 এ উন্নীত হোন' }))
    await screen.findByText('আপনার স্তর এখন A2। নতুন সেশনে এটি ব্যবহৃত হবে।')

    // Finish → session summary.
    fireEvent.click(screen.getByRole('button', { name: 'আজ এ পর্যন্তই' }))
    await waitFor(() => expect(screen.getByText('সেশন সম্পন্ন!')).toBeInTheDocument())
    expect(screen.getByText('সেশন: 2টির মধ্যে 1টি সঠিক')).toBeInTheDocument()
  })

  it('lets learners hear pronunciation for MCQ options and a built sentence', async () => {
    const speak = vi.fn()
    vi.stubGlobal('speechSynthesis', { speak, cancel: vi.fn() })
    vi.stubGlobal(
      'SpeechSynthesisUtterance',
      class {
        text: string
        lang = ''
        constructor(text: string) {
          this.text = text
        }
      },
    )
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me')
          return Promise.resolve(jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }))
        if (url === '/v1/curriculum') return Promise.resolve(jsonResponse({ contentVersion: 'v1', levels: [] }))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/stats' && method === 'GET') return Promise.resolve(jsonResponse(stats()))
        if (url === '/v1/practice/sessions' && method === 'POST')
          return Promise.resolve(jsonResponse(ROUND_PRONOUNCE, 201))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    const start = await screen.findByRole('button', { name: 'সেশন শুরু করুন' })
    fireEvent.click(start)

    // MEANING_WORD: each English option carries its own pronunciation button.
    await screen.findByText('“বাতাস” — এর ইংরেজি শব্দ কোনটি?')
    fireEvent.click(screen.getByRole('button', { name: '"air" শুনুন' }))
    expect(speak).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: '"water" শুনুন' }))
    expect(speak).toHaveBeenCalledTimes(2)

    // Answer it (any option) to move to the SENTENCE_BUILD exercise.
    fireEvent.click(screen.getByRole('button', { name: 'air' }))
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'পরবর্তী' })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'পরবর্তী' }))

    // SENTENCE_BUILD: each word-bank token has its own pronunciation button.
    await screen.findByText('শব্দ সাজিয়ে বাক্যটি তৈরি করুন: “আমি ভালো আছি।”')
    fireEvent.click(screen.getByRole('button', { name: '"I" শুনুন' }))
    expect(speak).toHaveBeenCalledTimes(3)

    // Once every token is placed, a "listen to your sentence" control appears.
    expect(screen.queryByRole('button', { name: 'আপনার বাক্যটি শুনুন' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'I' }))
    fireEvent.click(screen.getByRole('button', { name: 'am' }))
    fireEvent.click(screen.getByRole('button', { name: 'fine' }))
    fireEvent.click(screen.getByRole('button', { name: 'আপনার বাক্যটি শুনুন' }))
    expect(speak).toHaveBeenCalledTimes(4)
  })
})
