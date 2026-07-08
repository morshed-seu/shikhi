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
            { id: 'les1', title: { en: 'Hello', bn: 'হ্যালো' }, ordinal: 1, status: 'NOT_STARTED', locked: false },
            { id: 'les2', title: { en: 'Introductions', bn: 'পরিচয়' }, ordinal: 2, status: 'NOT_STARTED', locked: true },
          ],
        },
      ],
    },
  ],
}

// Skipped: the curriculum map is hidden from the web home surface (matching the
// Android client) — this App-level flow is unreachable until the map is restored.
describe.skip('CurriculumMap (M2)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows the published curriculum once the learner is signed in', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') {
          return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        }
        if (url === '/v1/auth/register' && method === 'POST') {
          return Promise.resolve(
            jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201),
          )
        }
        if (url === '/v1/me') {
          return Promise.resolve(
            jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }),
          )
        }
        if (url === '/v1/curriculum') {
          return Promise.resolve(jsonResponse(CURRICULUM))
        }
        if (url === '/v1/stats') {
          return Promise.resolve(
            jsonResponse({ xp: 30, rank: 0, currentStreak: 2, longestStreak: 2, hearts: 4, dailyGoal: 20, accuracyByPattern: {} }),
          )
        }
        if (url === '/v1/review/due') {
          return Promise.resolve(jsonResponse([]))
        }
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })

    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    // Default locale is Bangla → the seeded lesson title renders in Bangla.
    await waitFor(() => expect(screen.getByText('হ্যালো')).toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'প্রাথমিক' })).toBeInTheDocument()

    // Progress stats bar renders once signed in.
    expect(await screen.findByLabelText('আপনার অগ্রগতি')).toBeInTheDocument()

    // A locked lesson is shown but not clickable.
    const locked = screen.getByRole('button', { name: /পরিচয়/ })
    expect(locked).toBeDisabled()
  })
})
