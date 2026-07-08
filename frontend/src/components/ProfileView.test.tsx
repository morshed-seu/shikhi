import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => (body === undefined ? '' : JSON.stringify(body)) }
}

const STATS = {
  hearts: 5,
  xp: 120,
  rank: 0,
  currentStreak: 4,
  longestStreak: 7,
  dailyGoal: 20,
  cefrLevel: 'B1',
  accuracyByPattern: {},
}

const DASHBOARD = {
  stats: STATS,
  wordMastery: [
    { cefrLevel: 'A1', mastered: 80, total: 100 },
    { cefrLevel: 'A2', mastered: 20, total: 120 },
    { cefrLevel: 'B1', mastered: 5, total: 150 },
    { cefrLevel: 'B2', mastered: 0, total: 130 },
    { cefrLevel: 'C1', mastered: 0, total: 90 },
  ],
  reviewDueCount: 3,
  lessonsCompleted: 2,
  practiceSessionsCompleted: 6,
  totalAnswered: 50,
  totalCorrect: 40,
}

const IDENTITIES = [{ provider: 'EMAIL', verified: true, maskedRef: 'na***@example.com' }]

const REGISTERED_USER = {
  id: '1',
  displayName: 'Nadia',
  uiLocale: 'bn',
  roles: ['LEARNER'],
  isGuest: false,
  joinedAt: '2026-01-15T00:00:00Z',
}

async function signUpAndOpenProfile() {
  await screen.findByRole('tab', { name: 'নিবন্ধন' })
  fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
  fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
  fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
  fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))
  fireEvent.click(await screen.findByRole('button', { name: 'প্রোফাইল খুলুন' }))
}

describe('ProfileView (E13 web Phase 1)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the dashboard snapshot: stats tiles and five mastery bars with counts', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse(IDENTITIES))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()

    await screen.findByRole('heading', { name: 'Nadia' })
    expect(screen.getByText('na***@example.com')).toBeInTheDocument()

    const statsGrid = screen.getByLabelText('আপনার অগ্রগতি')
    expect(within(statsGrid).getByText('120')).toBeInTheDocument() // XP
    expect(within(statsGrid).getByText('4')).toBeInTheDocument() // current streak
    expect(within(statsGrid).getByText('7')).toBeInTheDocument() // longest streak
    expect(within(statsGrid).getByText('3')).toBeInTheDocument() // review due
    expect(within(statsGrid).getByText('2')).toBeInTheDocument() // lessons completed
    expect(within(statsGrid).getByText('6')).toBeInTheDocument() // practice sessions
    expect(within(statsGrid).getByText('80%')).toBeInTheDocument() // 40/50 lifetime accuracy

    const mastery = screen.getByLabelText('আয়ত্ত করা শব্দ')
    expect(within(mastery).getAllByRole('progressbar')).toHaveLength(5)
    expect(within(mastery).getByText('100টির মধ্যে 80টি')).toBeInTheDocument()
    expect(within(mastery).getByText('120টির মধ্যে 20টি')).toBeInTheDocument()
    expect(within(mastery).getByText('150টির মধ্যে 5টি')).toBeInTheDocument()
    expect(within(mastery).getByText('130টির মধ্যে 0টি')).toBeInTheDocument()
    expect(within(mastery).getByText('90টির মধ্যে 0টি')).toBeInTheDocument()
  })

  it('saves a new display name via inline edit, calling updateProfile and refreshing the cached user', async () => {
    let patchBody: unknown = null
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/me' && method === 'PATCH') {
          patchBody = JSON.parse(opts?.body ?? '{}')
          return Promise.resolve(jsonResponse({ ...REGISTERED_USER, displayName: 'Karim' }))
        }
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse(IDENTITIES))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()
    await screen.findByRole('heading', { name: 'Nadia' })

    fireEvent.click(screen.getByRole('button', { name: 'নাম সম্পাদনা করুন' }))
    fireEvent.change(screen.getByLabelText('নাম'), { target: { value: 'Karim' } })
    fireEvent.click(screen.getByRole('button', { name: 'সংরক্ষণ করুন' }))

    await screen.findByRole('heading', { name: 'Karim' })
    expect(patchBody).toEqual({ displayName: 'Karim' })
  })

  it('requires an explicit confirm step before deleting the account, then logs out', async () => {
    let deleteCalled = false
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/me' && method === 'DELETE') {
          deleteCalled = true
          return Promise.resolve(jsonResponse(undefined, 204))
        }
        if (url === '/v1/auth/logout' && method === 'POST') return Promise.resolve(jsonResponse(undefined, 204))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse(IDENTITIES))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()
    await screen.findByRole('heading', { name: 'Nadia' })

    fireEvent.click(screen.getByRole('button', { name: 'অ্যাকাউন্ট মুছে ফেলুন' }))
    expect(deleteCalled).toBe(false)

    fireEvent.click(await screen.findByRole('button', { name: 'হ্যাঁ, মুছে ফেলুন' }))

    await waitFor(() => expect(deleteCalled).toBe(true))
    await screen.findByRole('tab', { name: 'লগ ইন' })
  })

  it('shows the guest claim CTA and hides delete/export for a guest learner', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/guest' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }))
        if (url === '/v1/me' && method === 'GET')
          return Promise.resolve(
            jsonResponse({
              id: 'g1',
              displayName: null,
              uiLocale: 'bn',
              roles: ['LEARNER'],
              isGuest: true,
              joinedAt: '2026-01-15T00:00:00Z',
            }),
          )
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse([]))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('button', { name: 'অ্যাকাউন্ট ছাড়াই চেষ্টা করুন →' }))
    fireEvent.click(await screen.findByRole('button', { name: 'প্রোফাইল খুলুন' }))

    // The claim CTA (GuestBanner) is visible while the profile is open.
    await screen.findByRole('button', { name: 'আমার অগ্রগতি সংরক্ষণ করুন' })
    // The profile card also shows a guest badge instead of a masked email (US-13.1); the
    // persistent header AuthPanel shows its own copy of the same wording, so scope the query.
    const profileCard = screen.getByLabelText('আপনার বিবরণ')
    expect(within(profileCard).getByText('আপনি অতিথি হিসেবে দেখছেন।')).toBeInTheDocument()

    // No delete/export account actions for a guest.
    expect(screen.queryByRole('button', { name: 'অ্যাকাউন্ট মুছে ফেলুন' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'আমার তথ্য ডাউনলোড করুন' })).not.toBeInTheDocument()
  })

  it('still renders the dashboard when only the identities fetch fails (email line omitted)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities')
          return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'identities down' }, 500))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()

    // The profile card, stats grid, mastery bars, and account actions all still render.
    await screen.findByRole('heading', { name: 'Nadia' })
    const statsGrid = screen.getByLabelText('আপনার অগ্রগতি')
    expect(within(statsGrid).getByText('120')).toBeInTheDocument()
    expect(within(screen.getByLabelText('আয়ত্ত করা শব্দ')).getAllByRole('progressbar')).toHaveLength(5)
    expect(screen.getByRole('button', { name: 'অ্যাকাউন্ট মুছে ফেলুন' })).toBeInTheDocument()

    // No error state — the identities failure only costs the masked-email line.
    expect(screen.queryByText('আপনার প্রোফাইল লোড করা যায়নি।')).not.toBeInTheDocument()
    expect(screen.queryByText('na***@example.com')).not.toBeInTheDocument()
  })

  it('a name save whose response carries a stale uiLocale does not flip the UI language', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/me' && method === 'PATCH')
          // A stale/racing response: the new name but the OLD locale ('en' while the cached
          // user and the UI are 'bn'). Adopting it wholesale would flip the UI language back.
          return Promise.resolve(jsonResponse({ ...REGISTERED_USER, displayName: 'Karim', uiLocale: 'en' }))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse(DASHBOARD))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse(IDENTITIES))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()
    await screen.findByRole('heading', { name: 'Nadia' })
    expect(document.documentElement.lang).toBe('bn')

    fireEvent.click(screen.getByRole('button', { name: 'নাম সম্পাদনা করুন' }))
    fireEvent.change(screen.getByLabelText('নাম'), { target: { value: 'Karim' } })
    fireEvent.click(screen.getByRole('button', { name: 'সংরক্ষণ করুন' }))

    // The name updates, but the cached user keeps the current locale, so App's
    // adopt-saved-language effect never calls changeLocale with the stale value.
    await screen.findByRole('heading', { name: 'Karim' })
    expect(document.documentElement.lang).toBe('bn')
    expect(screen.getByRole('button', { name: 'নাম সম্পাদনা করুন' })).toBeInTheDocument()
  })

  it('shows an error state when the dashboard fails to load', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string; body?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        if (url === '/v1/auth/register' && method === 'POST')
          return Promise.resolve(jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }, 201))
        if (url === '/v1/me' && method === 'GET') return Promise.resolve(jsonResponse(REGISTERED_USER))
        if (url === '/v1/stats') return Promise.resolve(jsonResponse(STATS))
        if (url === '/v1/review/due') return Promise.resolve(jsonResponse([]))
        if (url === '/v1/dashboard') return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'boom' }, 500))
        if (url === '/v1/me/identities') return Promise.resolve(jsonResponse(IDENTITIES))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await signUpAndOpenProfile()

    await screen.findByText('আপনার প্রোফাইল লোড করা যায়নি।')
  })
})
