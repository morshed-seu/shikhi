import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enqueue, enqueuePracticeAnswers, flushOutbox, flushPracticeAnswers, pendingCount } from './outbox'

describe('offline outbox (M5)', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.restoreAllMocks())

  it('buffers events and reports the pending count', () => {
    expect(pendingCount()).toBe(0)
    enqueue({ idempotencyKey: 'k1', type: 'COMPLETE_LESSON', payload: { lessonId: 'l1', score: 2 } })
    enqueue({ idempotencyKey: 'k2', type: 'ANSWER', payload: { correct: true } })
    expect(pendingCount()).toBe(2)
  })

  it('flushes to /progress/sync and clears on success', async () => {
    enqueue({ idempotencyKey: 'k1', type: 'COMPLETE_LESSON', payload: {} })
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ xp: 20 }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(flushOutbox('token')).resolves.toBe(true)
    expect(pendingCount()).toBe(0)
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('keeps events buffered when the flush fails', async () => {
    enqueue({ idempotencyKey: 'k1', type: 'COMPLETE_LESSON', payload: {} })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    await expect(flushOutbox('token')).resolves.toBe(false)
    expect(pendingCount()).toBe(1)
  })

  describe('PRACTICE_ANSWERS (E12 batch grading)', () => {
    const items = [
      { idempotencyKey: 'a1', exerciseId: 'pex1', answer: { selectedOptionId: 'opt-a' } },
      { idempotencyKey: 'a2', exerciseId: 'pex2', answer: { text: 'air' } },
    ]

    it('enqueuePracticeAnswers replaces the prior snapshot for the same session', () => {
      enqueuePracticeAnswers('ps1', [items[0]])
      expect(pendingCount()).toBe(1)
      enqueuePracticeAnswers('ps1', items)
      // Still one event — the second call replaced, not appended to, the first.
      expect(pendingCount()).toBe(1)
    })

    it('flushOutbox routes a PRACTICE_ANSWERS event to the batch endpoint and clears it on success', async () => {
      enqueuePracticeAnswers('ps1', items)
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ stats: { hearts: 4 }, verdicts: [] }),
      })
      vi.stubGlobal('fetch', fetchMock)

      await expect(flushOutbox('token')).resolves.toBe(true)
      expect(pendingCount()).toBe(0)
      expect(fetchMock).toHaveBeenCalledOnce()
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('/v1/practice/sessions/ps1/answers/batch')
      expect(JSON.parse(init.body as string)).toEqual({ answers: items })
    })

    it('flushOutbox keeps a PRACTICE_ANSWERS event buffered when the POST fails, and does not touch unrelated events', async () => {
      enqueue({ idempotencyKey: 'k1', type: 'COMPLETE_LESSON', payload: { lessonId: 'l1' } })
      enqueuePracticeAnswers('ps1', items)
      vi.stubGlobal(
        'fetch',
        vi.fn((url: string) => {
          if (url === '/v1/progress/sync')
            return Promise.resolve({ ok: true, status: 200, text: async () => JSON.stringify({}) })
          return Promise.reject(new Error('offline'))
        }),
      )

      await expect(flushOutbox('token')).resolves.toBe(false)
      // The unrelated progress event synced fine; only the failed practice batch remains.
      expect(pendingCount()).toBe(1)
    })

    it('flushPracticeAnswers submits just the one session and returns the batch result', async () => {
      enqueuePracticeAnswers('ps1', items)
      const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ stats: { hearts: 4 }, verdicts: [{ exerciseId: 'pex1', correct: true }] }),
      })
      vi.stubGlobal('fetch', fetchMock)

      const result = await flushPracticeAnswers('token', 'ps1')
      expect(result).toEqual({ stats: { hearts: 4 }, verdicts: [{ exerciseId: 'pex1', correct: true }] })
      expect(pendingCount()).toBe(0)
    })

    it('flushPracticeAnswers returns null and keeps the event buffered on failure', async () => {
      enqueuePracticeAnswers('ps1', items)
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

      await expect(flushPracticeAnswers('token', 'ps1')).resolves.toBeNull()
      expect(pendingCount()).toBe(1)
    })

    it('flushPracticeAnswers is a no-op when nothing is buffered for that session', async () => {
      const fetchMock = vi.fn()
      vi.stubGlobal('fetch', fetchMock)

      await expect(flushPracticeAnswers('token', 'unknown-session')).resolves.toBeNull()
      expect(fetchMock).not.toHaveBeenCalled()
    })
  })
})
