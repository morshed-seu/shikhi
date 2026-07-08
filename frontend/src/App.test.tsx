import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import './i18n'

describe('App (M0 walking skeleton)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders the Bangla title by default and shows backend status from /v1/health', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'UP', service: 'shikhi' }),
      }),
    )

    render(<App />)

    // Default UI locale is Bangla (D1): title renders as শিখি.
    expect(screen.getByRole('heading', { name: 'শিখি' })).toBeInTheDocument()

    // Health status resolves and is displayed.
    await waitFor(() => expect(screen.getByText(/UP/)).toBeInTheDocument())
    expect(fetch).toHaveBeenCalledWith('/v1/health')
  })

  it('shows an error state when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))

    render(<App />)

    await waitFor(() =>
      expect(screen.getByText('ব্যাকএন্ডে সংযোগ করা যায়নি')).toBeInTheDocument(),
    )
  })
})

describe('Profile entry points (E13)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  function jsonResponse(body: unknown, status = 200) {
    return { ok: status < 400, status, text: async () => (body === undefined ? '' : JSON.stringify(body)) }
  }

  const stats = {
    hearts: 5,
    xp: 40,
    rank: 0,
    currentStreak: 3,
    longestStreak: 3,
    dailyGoal: 20,
    cefrLevel: 'A1',
    accuracyByPattern: {},
  }

  const dashboard = {
    stats,
    wordMastery: [
      { cefrLevel: 'A1', mastered: 1, total: 10 },
      { cefrLevel: 'A2', mastered: 0, total: 10 },
      { cefrLevel: 'B1', mastered: 0, total: 10 },
      { cefrLevel: 'B2', mastered: 0, total: 10 },
      { cefrLevel: 'C1', mastered: 0, total: 10 },
    ],
    reviewDueCount: 0,
    lessonsCompleted: 0,
    practiceSessionsCompleted: 0,
    totalAnswered: 0,
    totalCorrect: 0,
  }

  function stubProfileFetch() {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET')
          return Promise.resolve(
            jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'], isGuest: false }),
          )
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(stats))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(dashboard))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse([]))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )
  }

  it('opens the profile view from the header button and returns home via the back control', async () => {
    stubProfileFetch()
    render(<App />)

    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    // Home surface is showing (the practice hero), and the header profile button appears.
    await screen.findByText('আজকের অনুশীলন')
    fireEvent.click(screen.getByRole('button', { name: 'প্রোফাইল খুলুন' }))

    await screen.findByRole('heading', { name: 'Nadia' })
    expect(screen.queryByText('আজকের অনুশীলন')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /হোমে ফিরে যান/ }))
    await screen.findByText('আজকের অনুশীলন')
  })

  it('opens the profile view by clicking the stats bar', async () => {
    stubProfileFetch()
    render(<App />)

    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    await screen.findByText('আজকের অনুশীলন')
    fireEvent.click(screen.getByRole('button', { name: 'আপনার অগ্রগতি — প্রোফাইল খুলুন' }))

    await screen.findByRole('heading', { name: 'Nadia' })
  })
})
