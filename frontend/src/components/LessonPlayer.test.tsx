import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
            { id: 'les1', title: { en: 'Hello lesson', bn: 'হ্যালো পাঠ' }, ordinal: 1, status: 'NOT_STARTED', locked: false },
          ],
        },
      ],
    },
  ],
}

const LESSON = {
  id: 'les1',
  contentVersion: 'pilot-v1',
  title: { en: 'Hello lesson', bn: 'হ্যালো পাঠ' },
  exercises: [
    {
      id: 'ex1',
      type: 'MCQ',
      ordinal: 1,
      prompt: { en: 'Which is a greeting?', bn: 'কোনটি শুভেচ্ছা?' },
      mediaRef: null,
      patternTags: [],
      config: {
        options: [
          { id: 'opt-a', text: { en: 'Hello', bn: 'হ্যালো' } },
          { id: 'opt-b', text: { en: 'Table', bn: 'টেবিল' } },
        ],
      },
    },
  ],
}

const stats = { hearts: 5, xp: 0, rank: 0, currentStreak: 0, longestStreak: 0, dailyGoal: 0, accuracyByPattern: {} }

describe('LessonPlayer (M3)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('plays a lesson: answer an exercise, see feedback, finish with results', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me')
          return Promise.resolve(jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }))
        if (url === '/v1/curriculum') return Promise.resolve(jsonResponse(CURRICULUM))
        if (url === '/v1/lessons/les1') return Promise.resolve(jsonResponse(LESSON))
        if (url === '/v1/sessions' && method === 'POST')
          return Promise.resolve(
            jsonResponse({ id: 'sess1', lessonId: 'les1', contentVersion: 'pilot-v1', heartsRemaining: 5, status: 'IN_PROGRESS' }, 201),
          )
        if (url === '/v1/sessions/sess1/answers' && method === 'POST')
          return Promise.resolve(
            jsonResponse({ verdict: { correct: true, feedback: { en: 'Nice work!', bn: 'দারুণ!' }, matchedPatternCode: null, source: 'RULE' }, stats }),
          )
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

    // Open the lesson from the curriculum map.
    const lessonButton = await screen.findByRole('button', { name: 'হ্যালো পাঠ' })
    fireEvent.click(lessonButton)

    // The player shows the exercise prompt and options.
    await screen.findByText('কোনটি শুভেচ্ছা?')
    fireEvent.click(screen.getByRole('button', { name: 'হ্যালো' }))
    fireEvent.click(screen.getByRole('button', { name: 'যাচাই করুন' }))

    // Verdict feedback appears.
    await screen.findByText('দারুণ!')

    // Finish the (single-exercise) lesson → results.
    fireEvent.click(screen.getByRole('button', { name: 'শেষ করুন' }))
    await waitFor(() => expect(screen.getByText('পাঠ সম্পন্ন!')).toBeInTheDocument())
    expect(screen.getByText('স্কোর: 1 / 1')).toBeInTheDocument()
    expect(screen.getByText('অর্জিত XP: 10')).toBeInTheDocument()
  })
})
