import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
}

const CURRICULUM = {
  contentVersion: 'pilot-v1',
  levels: [
    {
      id: 'l1',
      code: 'A1',
      title: { en: 'Beginner', bn: 'প্রাথমিক' },
      ordinal: 1,
      units: [
        {
          id: 'u1',
          code: 'A1-U1',
          title: { en: 'Greetings', bn: 'শুভেচ্ছা' },
          ordinal: 1,
          locked: false,
          lessons: [
            { id: 'les1', title: { en: 'Goodbye', bn: 'বিদায়' }, ordinal: 1, status: 'NOT_STARTED', locked: false },
          ],
        },
      ],
    },
  ],
}

// A single WORD_BANK exercise: tokens are shuffled; the accepted order is server-side only.
const LESSON = {
  id: 'les1',
  contentVersion: 'pilot-v1',
  title: { en: 'Goodbye', bn: 'বিদায়' },
  exercises: [
    {
      id: 'ex1',
      type: 'WORD_BANK',
      ordinal: 1,
      prompt: { en: 'Arrange: See you tomorrow', bn: 'সাজান: See you tomorrow' },
      mediaRef: null,
      patternTags: [],
      config: {
        tokens: [
          { id: 'tk-tomorrow', text: { en: 'tomorrow', bn: 'tomorrow' } },
          { id: 'tk-see', text: { en: 'See', bn: 'See' } },
          { id: 'tk-you', text: { en: 'you', bn: 'you' } },
        ],
      },
    },
  ],
}

const stats = { hearts: 5, xp: 0, rank: 0, currentStreak: 0, longestStreak: 0, dailyGoal: 0, accuracyByPattern: {} }

// Skipped: the curriculum map (the only entry into the lesson player) is hidden
// from the web home surface, matching the Android client.
describe.skip('LessonPlayer WORD_BANK (M7)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('arranges word-bank tokens and submits them in tapped order', async () => {
    let submitted: unknown = null
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me')
          return Promise.resolve(jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }))
        if (url === '/v1/curriculum') return Promise.resolve(jsonResponse(CURRICULUM))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(stats))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/lessons/les1') return Promise.resolve(jsonResponse(LESSON))
        if (url === '/v1/sessions' && method === 'POST')
          return Promise.resolve(
            jsonResponse({ id: 'sess1', lessonId: 'les1', contentVersion: 'pilot-v1', heartsRemaining: 5, status: 'IN_PROGRESS' }, 201),
          )
        if (url === '/v1/sessions/sess1/answers' && method === 'POST') {
          submitted = JSON.parse(opts?.body ?? '{}')
          return Promise.resolve(
            jsonResponse({ verdict: { correct: true, feedback: { en: 'Nice!', bn: 'দারুণ!' }, matchedPatternCode: null, source: 'RULE' }, stats }),
          )
        }
        if (url === '/v1/sessions/sess1/complete' && method === 'POST')
          return Promise.resolve(jsonResponse({ score: 1, xpEarned: 10, newlyUnlocked: [], reviewItemsAdded: 0, stats }))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    const lessonButton = await screen.findByRole('button', { name: 'বিদায়' })
    fireEvent.click(lessonButton)

    await screen.findByText('সাজান: See you tomorrow')

    // Check is disabled until every token is placed.
    const checkBtn = screen.getByRole('button', { name: 'যাচাই করুন' })
    expect(checkBtn).toBeDisabled()

    // Tap the words in the correct order.
    fireEvent.click(screen.getByRole('button', { name: 'See' }))
    fireEvent.click(screen.getByRole('button', { name: 'you' }))
    fireEvent.click(screen.getByRole('button', { name: 'tomorrow' }))

    expect(checkBtn).not.toBeDisabled()
    fireEvent.click(checkBtn)

    await screen.findByText('দারুণ!')
    expect(submitted).toEqual({
      idempotencyKey: expect.any(String),
      exerciseId: 'ex1',
      answer: { tokenOrder: ['See', 'you', 'tomorrow'] },
    })
  })
})
