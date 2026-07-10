import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => (body === undefined ? '' : JSON.stringify(body)) }
}

describe('DeleteAccountPage (Play Store compliance, /delete-account)', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/')
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows sign-in guidance and the sign-in form when no session is active', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))))
    window.history.pushState({}, '', '/delete-account')

    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'আপনার অ্যাকাউন্ট মুছে ফেলুন' })).toBeInTheDocument()
    await screen.findByText(/প্রথমে নিচে সাইন ইন করুন/)
    // The existing sign-in form (AuthPanel) is reused.
    await screen.findByRole('tab', { name: 'লগ ইন' })
  })

  it('lets a signed-in learner delete their account, with an explicit confirm step', async () => {
    let deleteCalled = false
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
        if (url === '/v1/me' && method === 'DELETE') {
          deleteCalled = true
          return Promise.resolve(jsonResponse(undefined, 204))
        }
        if (url === '/v1/auth/logout' && method === 'POST') return Promise.resolve(jsonResponse(undefined, 204))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )
    window.history.pushState({}, '', '/delete-account')

    render(<App />)

    // Sign up right on the delete-account page (reused AuthPanel form).
    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    fireEvent.click(await screen.findByRole('button', { name: 'আমার অ্যাকাউন্ট মুছে ফেলুন' }))
    expect(deleteCalled).toBe(false)

    fireEvent.click(await screen.findByRole('button', { name: 'হ্যাঁ, মুছে ফেলুন' }))

    await waitFor(() => expect(deleteCalled).toBe(true))
    await screen.findByText('আপনার অ্যাকাউন্ট মুছে ফেলা হয়েছে। আপনাকে সাইন আউট করা হয়েছে।')
  })

  it('shows the delete-error message (not the success state) when the delete call fails', async () => {
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
        if (url === '/v1/me' && method === 'DELETE')
          return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'boom' }, 500))
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )
    window.history.pushState({}, '', '/delete-account')

    render(<App />)

    await screen.findByRole('tab', { name: 'নিবন্ধন' })
    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), { target: { value: 'nadia@example.com' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), { target: { value: 's3cretpassword' } })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    fireEvent.click(await screen.findByRole('button', { name: 'আমার অ্যাকাউন্ট মুছে ফেলুন' }))
    fireEvent.click(await screen.findByRole('button', { name: 'হ্যাঁ, মুছে ফেলুন' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'আপনার অ্যাকাউন্ট মুছে ফেলা যায়নি। আবার চেষ্টা করুন।',
    )
    // No success state, and the learner can retry (delete flow still on screen).
    expect(
      screen.queryByText('আপনার অ্যাকাউন্ট মুছে ফেলা হয়েছে। আপনাকে সাইন আউট করা হয়েছে।'),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'হ্যাঁ, মুছে ফেলুন' })).toBeEnabled()
  })
})
