import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import './i18n'

describe('language toggle (M5)', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('persists the chosen locale and sets <html lang> for accessibility', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 'UP', service: 'shikhi' }),
        text: async () => JSON.stringify({ status: 'UP', service: 'shikhi' }),
      }),
    )

    render(<App />)

    // Default is Bangla; the toggle offers English.
    const toggle = await screen.findByRole('button', { name: 'ভাষা পরিবর্তন করুন' })
    fireEvent.click(toggle)

    expect(localStorage.getItem('shikhi.locale')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    // Tagline is now English.
    expect(screen.getByText('Learn English — in Bangla.')).toBeInTheDocument()
  })
})
