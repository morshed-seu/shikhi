import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enqueue, flushOutbox, pendingCount } from './outbox'

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
})
