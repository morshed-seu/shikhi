import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import '../i18n'

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) }
}

describe('PrivacyPolicyPage (Play Store compliance, /privacy)', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/')
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the privacy policy at /privacy with its key sections, in Bangla by default', () => {
    // No network calls are expected — the page is fully static/local.
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))))
    window.history.pushState({}, '', '/privacy')

    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'গোপনীয়তা নীতি' })).toBeInTheDocument()
    expect(screen.getByText(/rajuseu5@gmail.com/)).toBeInTheDocument()
    expect(screen.getByText('আমরা যে তথ্য সংগ্রহ করি')).toBeInTheDocument()
    expect(screen.getByText('আপনার তথ্য মুছে ফেলা')).toBeInTheDocument()

    // Links back to the app and to the other compliance page (nav link + the inline one
    // in the deletion section both point at /delete-account).
    expect(screen.getByRole('link', { name: '← শিখিতে ফিরে যান' })).toHaveAttribute('href', '/')
    const deleteLinks = screen.getAllByRole('link', { name: 'অ্যাকাউন্ট মুছে ফেলুন' })
    expect(deleteLinks.length).toBeGreaterThan(0)
    deleteLinks.forEach((link) => expect(link).toHaveAttribute('href', '/delete-account'))
  })

  it('also matches the path with a trailing slash (/privacy/)', () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))))
    window.history.pushState({}, '', '/privacy/')

    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'গোপনীয়তা নীতি' })).toBeInTheDocument()
  })

  it('switches to English via the language toggle, same as the main app', () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ status: 'UP', service: 'shikhi' }))))
    window.history.pushState({}, '', '/privacy')

    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'ভাষা পরিবর্তন করুন' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Privacy Policy' })).toBeInTheDocument()
  })
})
