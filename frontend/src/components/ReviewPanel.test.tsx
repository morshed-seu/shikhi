import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
}
function noContent() {
  return { ok: true, status: 204, text: async () => '' }
}

const CURRICULUM = { contentVersion: 'pilot-v1', levels: [] }
const stats = { xp: 0, rank: 0, currentStreak: 0, longestStreak: 0, hearts: 5, dailyGoal: 20, accuracyByPattern: {} }
const DUE = [
  { exerciseId: 'ex1', prompt: { en: 'Which is a greeting?', bn: 'কোনটি শুভেচ্ছা?' }, boxLevel: 1, dueAt: '2026-07-03T00:00:00Z' },
]

describe('ReviewPanel (M6)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows due review items and clears one when marked known', async () => {
    const results = vi.fn(() => Promise.resolve(noContent()))
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me') return Promise.resolve(jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }))
        if (url === '/v1/curriculum') return Promise.resolve(jsonResponse(CURRICULUM))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(stats))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse(DUE))
        if (url === '/v1/review/results' && method === 'POST') return results()
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    // The due item surfaces in the review panel.
    await screen.findByText('কোনটি শুভেচ্ছা?')

    // Mark it known → it is recorded and removed from view.
    fireEvent.click(screen.getByRole('button', { name: 'আমি জানতাম' }))
    await waitFor(() => expect(screen.queryByText('কোনটি শুভেচ্ছা?')).not.toBeInTheDocument())
    expect(results).toHaveBeenCalledOnce()
  })
})
