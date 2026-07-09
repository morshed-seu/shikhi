import { fireEvent, render, screen } from '@testing-library/react'
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

// Round 1: an MCQ (correct) and a typed word (wrong, to exercise the reveal + hearts).
const ROUND1 = {
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
      solution: { correctOptionId: 'opt-good', reveal: 'advice — পরামর্শ' },
    },
    {
      id: 'pex2',
      type: 'TYPE_WORD',
      ordinal: 2,
      prompt: { en: 'Type the English word for “বাতাস”', bn: '“বাতাস” — এর ইংরেজি শব্দটি লিখুন' },
      config: { partOfSpeech: 'n.' },
      solution: { accepted: ['air'], reveal: 'air — বাতাস' },
    },
  ],
}

// Round 2 (after "keep going"): a single MCQ, correct.
const ROUND2 = {
  sessionId: 'ps1',
  round: 2,
  cefrLevel: 'A1',
  levelUpEligible: false,
  exercises: [
    {
      id: 'pex3',
      type: 'WORD_MEANING',
      ordinal: 1,
      prompt: { en: 'What does “calm” mean?', bn: '“calm” শব্দের অর্থ কী?' },
      config: {
        options: [
          { id: 'opt-calm', text: { en: 'শান্ত', bn: 'শান্ত' } },
          { id: 'opt-hot', text: { en: 'গরম', bn: 'গরম' } },
        ],
      },
      solution: { correctOptionId: 'opt-calm', reveal: 'calm — শান্ত' },
    },
  ],
}

describe('PracticePlayer (E12 batch grading)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('grades locally with instant feedback, buffers answers, and batches exactly once per exit path', async () => {
    const fetchMock = vi.fn((url: string, opts?: { method?: string; body?: string }) => {
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
        return Promise.resolve(jsonResponse(ROUND1, 201))
      if (url === '/v1/practice/sessions/ps1/rounds' && method === 'POST')
        return Promise.resolve(jsonResponse(ROUND2, 201))
      if (url === '/v1/practice/sessions/ps1/answers/batch' && method === 'POST') {
        const body = JSON.parse(opts?.body ?? '{}') as { answers: { exerciseId: string }[] }
        const ids = body.answers.map((a) => a.exerciseId)
        if (ids.includes('pex3'))
          return Promise.resolve(
            jsonResponse({ stats: stats({ hearts: 3 }), verdicts: [{ exerciseId: 'pex3', correct: true }] }),
          )
        // Round 1's batch: server disagrees slightly with the (correct) local grade on
        // hearts just to prove the client reconciles from the response, not just local math.
        return Promise.resolve(
          jsonResponse({
            stats: stats({ hearts: 3 }),
            verdicts: [
              { exerciseId: 'pex1', correct: true },
              { exerciseId: 'pex2', correct: false },
            ],
          }),
        )
      }
      if (url === '/v1/stats/level' && method === 'PUT')
        return Promise.resolve(jsonResponse(stats({ cefrLevel: 'A2' })))
      if (url === '/v1/practice/sessions/ps1/complete' && method === 'POST')
        return Promise.resolve(
          jsonResponse({
            correctCount: 2,
            totalCount: 3,
            roundsPlayed: 2,
            xpEarned: 20,
            levelUpEligible: true,
            stats: stats({ hearts: 3 }),
          }),
        )
      return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    const start = await screen.findByRole('button', { name: 'সেশন শুরু করুন' })
    fireEvent.click(start)

    // Exercise 1 — MCQ correct: instant feedback, no per-answer request.
    await screen.findByText('“advice” শব্দের অর্থ কী?')
    fireEvent.click(screen.getByRole('button', { name: 'পরামর্শ' }))
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await screen.findByText('সঠিক!', { selector: 'strong' })

    // Exercise 2 — typed word, wrong: instant reveal + a local heart decrement, still no request.
    fireEvent.click(screen.getByRole('button', { name: 'পরবর্তী' }))
    await screen.findByText('“বাতাস” — এর ইংরেজি শব্দটি লিখুন')
    fireEvent.change(screen.getByLabelText('আপনার উত্তর'), { target: { value: 'water' } })
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await screen.findByText('সঠিক উত্তর: air — বাতাস')
    expect(screen.getByLabelText('হৃদয়: 4')).toBeInTheDocument()

    expect(fetchMock.mock.calls.some(([u]) => u === '/v1/practice/sessions/ps1/answers/batch')).toBe(false)

    // Round summary: score, XP, level-up offer.
    fireEvent.click(screen.getByRole('button', { name: 'রাউন্ডের ফলাফল দেখুন' }))
    await screen.findByText('রাউন্ড শেষ!')
    expect(screen.getByText('2টির মধ্যে 1টি সঠিক')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'A2 এ উন্নীত হোন' }))
    await screen.findByText('আপনার স্তর এখন A2। নতুন সেশনে এটি ব্যবহৃত হবে।')

    // "Keep going" flushes round 1's buffer in ONE batch request before starting round 2.
    fireEvent.click(screen.getByRole('button', { name: 'চালিয়ে যান' }))
    await screen.findByText('“calm” শব্দের অর্থ কী?')

    const batchCallsAfterRound1 = fetchMock.mock.calls.filter(
      ([u]) => u === '/v1/practice/sessions/ps1/answers/batch',
    )
    expect(batchCallsAfterRound1).toHaveLength(1)
    const round1Body = JSON.parse(batchCallsAfterRound1[0][1]?.body as string) as {
      answers: { exerciseId: string; answer: unknown; idempotencyKey: string }[]
    }
    expect(round1Body.answers).toHaveLength(2)
    expect(round1Body.answers[0]).toMatchObject({ exerciseId: 'pex1', answer: { selectedOptionId: 'opt-good' } })
    expect(round1Body.answers[1]).toMatchObject({ exerciseId: 'pex2', answer: { text: 'water' } })
    expect(typeof round1Body.answers[0].idempotencyKey).toBe('string')
    // Hearts reconcile from the batch response (server said 3, not the locally-graded 4).
    expect(screen.getByLabelText('হৃদয়: 3')).toBeInTheDocument()

    // Round 2 — MCQ correct, instant feedback again, still no per-answer request.
    fireEvent.click(screen.getByRole('button', { name: 'শান্ত' }))
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))
    await screen.findByText('সঠিক!', { selector: 'strong' })
    expect(fetchMock.mock.calls.some(([u]) => u === '/v1/practice/sessions/ps1/answers')).toBe(false)

    fireEvent.click(screen.getByRole('button', { name: 'রাউন্ডের ফলাফল দেখুন' }))
    await screen.findByText('রাউন্ড শেষ!')
    expect(screen.getByText('1টির মধ্যে 1টি সঠিক')).toBeInTheDocument()

    // Finish flushes round 2's buffer in a SECOND, separate batch request, then completes.
    fireEvent.click(screen.getByRole('button', { name: 'আজ এ পর্যন্তই' }))
    await screen.findByText('সেশন সম্পন্ন!')
    expect(screen.getByText('সেশন: 3টির মধ্যে 2টি সঠিক')).toBeInTheDocument()

    const batchCallsFinal = fetchMock.mock.calls.filter(([u]) => u === '/v1/practice/sessions/ps1/answers/batch')
    expect(batchCallsFinal).toHaveLength(2)
    const round2Body = JSON.parse(batchCallsFinal[1][1]?.body as string) as {
      answers: { exerciseId: string; answer: unknown }[]
    }
    expect(round2Body.answers).toHaveLength(1)
    expect(round2Body.answers[0]).toMatchObject({ exerciseId: 'pex3', answer: { selectedOptionId: 'opt-calm' } })

    // The old per-answer endpoint must never be called for any exercise, at any point.
    expect(fetchMock.mock.calls.some(([u]) => u === '/v1/practice/sessions/ps1/answers')).toBe(false)
  })
})
