import { render, screen, waitFor } from '@testing-library/react'
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
