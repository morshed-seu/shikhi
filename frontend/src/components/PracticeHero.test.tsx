import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
}

const stats = (cefrLevel: string) => ({
  hearts: 5,
  xp: 40,
  rank: 0,
  currentStreak: 3,
  longestStreak: 3,
  dailyGoal: 20,
  cefrLevel,
  accuracyByPattern: {},
})

describe('PracticeHero (E12)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows the level badge and start action; self-placement updates the level', async () => {
    let level = 'A1'
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
        if (url === '/v1/stats' && method === 'GET') return Promise.resolve(jsonResponse(stats(level)))
        if (url === '/v1/stats/level' && method === 'PUT') {
          level = (JSON.parse(opts?.body ?? '{}') as { cefrLevel: string }).cefrLevel
          return Promise.resolve(jsonResponse(stats(level)))
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

    // The hero leads the signed-in home: title, level badge, one start action.
    await screen.findByText('আজকের অনুশীলন')
    expect(screen.getByRole('button', { name: 'সেশন শুরু করুন' })).toBeInTheDocument()
    const badge = await screen.findByRole('button', { name: 'আপনার স্তর: A1' })

    // Dismiss onboarding so its level buttons don't shadow the hero picker.
    fireEvent.click(screen.getByRole('button', { name: 'এড়িয়ে যান — A1 থেকে শুরু' }))

    // Open the picker from the badge and self-place at B1.
    fireEvent.click(badge)
    fireEvent.click(screen.getByRole('button', { name: /B1/ }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'আপনার স্তর: B1' })).toBeInTheDocument(),
    )
  })
})
