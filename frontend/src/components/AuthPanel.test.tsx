import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

/** Builds a minimal Response-like object matching how api/client.ts reads responses. */
function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status < 400,
    status,
    text: async () => JSON.stringify(body),
  }
}

describe('AuthPanel (M1 identity)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('registers a new account and shows the signed-in profile', async () => {
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
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    // Wait until the login form is shown (initial session check finished, no stored token).
    await screen.findByRole('tab', { name: 'নিবন্ধন' })

    fireEvent.click(screen.getByRole('tab', { name: 'নিবন্ধন' }))
    fireEvent.change(screen.getByLabelText('ইমেইল'), {
      target: { value: 'nadia@example.com' },
    })
    fireEvent.change(screen.getByLabelText('নাম'), { target: { value: 'Nadia' } })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), {
      target: { value: 's3cretpassword' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'নিবন্ধন' }))

    await waitFor(() =>
      expect(screen.getByText('স্বাগতম, Nadia!')).toBeInTheDocument(),
    )
    // The refresh token is persisted so a reload can resume the session.
    expect(localStorage.getItem('shikhi.refreshToken')).toBe('r')
  })

  it('toggles the password field between hidden and visible', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'লগ ইন' })

    const password = screen.getByLabelText('পাসওয়ার্ড') as HTMLInputElement
    expect(password.type).toBe('password')

    fireEvent.click(screen.getByRole('button', { name: 'পাসওয়ার্ড দেখান' }))
    expect(password.type).toBe('text')

    fireEvent.click(screen.getByRole('button', { name: 'পাসওয়ার্ড লুকান' }))
    expect(password.type).toBe('password')
  })

  it('remembers the email on login and prefills it next time', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') {
          return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        }
        if (url === '/v1/auth/login' && method === 'POST') {
          return Promise.resolve(
            jsonResponse({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }),
          )
        }
        if (url === '/v1/me') {
          return Promise.resolve(
            jsonResponse({ id: '1', displayName: 'Nadia', uiLocale: 'bn', roles: ['LEARNER'] }),
          )
        }
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    const { unmount } = render(<App />)
    await screen.findByRole('tab', { name: 'লগ ইন' })

    fireEvent.change(screen.getByLabelText('ইমেইল'), {
      target: { value: 'nadia@example.com' },
    })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), {
      target: { value: 's3cretpassword' },
    })
    fireEvent.click(screen.getByLabelText('আমার ইমেইল মনে রাখুন'))
    fireEvent.click(screen.getByRole('button', { name: 'লগ ইন' }))

    await waitFor(() =>
      expect(localStorage.getItem('shikhi.rememberedEmail')).toBe('nadia@example.com'),
    )

    // A fresh mount (as on a later visit) should prefill the remembered email.
    unmount()
    localStorage.removeItem('shikhi.refreshToken')
    render(<App />)
    const email = (await screen.findByLabelText('ইমেইল')) as HTMLInputElement
    expect(email.value).toBe('nadia@example.com')
    expect(screen.getByLabelText('আমার ইমেইল মনে রাখুন')).toBeChecked()
  })

  it('shows the server error message when login fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, opts?: { method?: string }) => {
        const method = opts?.method ?? 'GET'
        if (url === '/v1/health') {
          return Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))
        }
        if (url === '/v1/auth/login' && method === 'POST') {
          return Promise.resolve(
            jsonResponse(
              { code: 'UNAUTHORIZED', message: 'Invalid email or password', correlationId: 'c1' },
              401,
            ),
          )
        }
        return Promise.resolve(jsonResponse({ code: 'ERROR', message: 'unexpected' }, 500))
      }),
    )

    render(<App />)
    await screen.findByRole('tab', { name: 'লগ ইন' })

    fireEvent.change(screen.getByLabelText('ইমেইল'), {
      target: { value: 'nobody@example.com' },
    })
    fireEvent.change(screen.getByLabelText('পাসওয়ার্ড'), {
      target: { value: 'wrongpass' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'লগ ইন' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password'),
    )
  })
})
