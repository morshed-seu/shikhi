import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from './client'

const ok = (body: unknown) => ({ ok: true, status: 200, text: async () => JSON.stringify(body) })
const fail = (status: number, body: unknown) => ({
  ok: false,
  status,
  text: async () => JSON.stringify(body),
})

describe('apiFetch resilience (M5)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('retries a GET after a transient network error, then succeeds', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(ok({ status: 'UP' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/health')).resolves.toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not retry a 4xx and surfaces the ApiError code', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fail(409, { code: 'EMAIL_ALREADY_REGISTERED', message: 'dup' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/auth/register', { method: 'POST', body: {} })).rejects.toMatchObject({
      code: 'EMAIL_ALREADY_REGISTERED',
      status: 409,
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not retry a non-idempotent POST by default', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('network'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      code: 'NETWORK',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
