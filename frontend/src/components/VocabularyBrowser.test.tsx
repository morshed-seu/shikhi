import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
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

// 90 words at A1 — enough to span three pages at the browser's 40-per-page size.
const words = Array.from({ length: 90 }, (_, i) => ({
  id: `w${i}`,
  headword: `word${i}`,
  senseLabel: null,
  partOfSpeech: 'noun',
  cefrLevel: 'A1',
  bnGloss: `অর্থ${i}`,
  exampleEn: null,
  exampleBn: null,
}))

describe('VocabularyBrowser pagination', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('paginates the word list instead of silently truncating it', async () => {
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
        if (url === '/v1/stats' && method === 'GET') return Promise.resolve(jsonResponse(stats))
        if (url === '/v1/vocabulary?level=A1') return Promise.resolve(jsonResponse(words))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    await screen.findByText('শব্দ দেখুন')
    fireEvent.click(screen.getByRole('button', { name: 'শব্দ দেখুন' }))

    await screen.findByText('word0')
    expect(screen.queryByText('word39')).toBeInTheDocument()
    // The old fixed-window render capped at 120 with no way to see the rest;
    // page one must stop at 40 and hide the remainder until paging forward.
    expect(screen.queryByText('word40')).not.toBeInTheDocument()
    expect(screen.getByText('3টির মধ্যে 1 নম্বর পৃষ্ঠা')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'পরবর্তী' }))
    await waitFor(() => expect(screen.getByText('word40')).toBeInTheDocument())
    expect(screen.queryByText('word0')).not.toBeInTheDocument()
    expect(screen.getByText('3টির মধ্যে 2 নম্বর পৃষ্ঠা')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'পরবর্তী' }))
    await waitFor(() => expect(screen.getByText('word80')).toBeInTheDocument())
    expect(screen.getByText('word89')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'পরবর্তী' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'পূর্ববর্তী' }))
    await waitFor(() => expect(screen.getByText('word40')).toBeInTheDocument())
  })
})
