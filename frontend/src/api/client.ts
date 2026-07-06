// Thin fetch wrapper for the Shikhi JSON API. In dev, Vite proxies /v1 to Spring Boot;
// in prod it is same-origin. Errors surface as ApiError carrying the contract's Error code.
//
// Resilience (M5, D2/NFR-N*): every request has a timeout; transient failures (network
// errors, timeouts, 429/502/503/504) are retried with exponential backoff + jitter. Retry
// defaults to GET only; non-idempotent writes opt in via `retry: true` (safe for our writes
// that carry an idempotency key, e.g. progress sync).

export interface ApiErrorBody {
  code: string
  message: string
  correlationId?: string
  details?: Record<string, unknown>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details?: Record<string, unknown>

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.details = body.details
  }

  /** True when the failure was a network/timeout error rather than a server response. */
  get isNetwork(): boolean {
    return this.status === 0
  }
}

export interface RequestOptions {
  method?: string
  body?: unknown
  token?: string | null
  /** Retry transient failures (defaults to true for GET). */
  retry?: boolean
  timeoutMs?: number
}

// API origin. Empty by default → same-origin `/v1` (dev via Vite proxy, or a CDN/nginx that
// fronts both the SPA and the API). Set VITE_API_BASE_URL at build time (e.g. the Render
// backend URL) when the SPA and API live on different origins — then the backend must also
// allow this SPA's origin via SHIKHI_CORS_ORIGINS. No trailing slash.
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const DEFAULT_TIMEOUT_MS = 10_000
const MAX_RETRIES = 2
const BASE_BACKOFF_MS = 300

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const isTransientStatus = (s: number) => s === 429 || s === 502 || s === 503 || s === 504

function backoff(attempt: number): number {
  const base = BASE_BACKOFF_MS * 2 ** (attempt - 1)
  return base + Math.floor(Math.random() * 100)
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(url, { ...init, signal: controller.signal })
  } finally {
    clearTimeout(timer)
  }
}

export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET'
  const retryable = opts.retry ?? method === 'GET'
  const maxAttempts = retryable ? MAX_RETRIES + 1 : 1

  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`
  const init: RequestInit = {
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  }

  for (let attempt = 1; ; attempt++) {
    try {
      const res = await fetchWithTimeout(`${API_BASE}/v1${path}`, init, opts.timeoutMs ?? DEFAULT_TIMEOUT_MS)

      if (!res.ok) {
        if (retryable && isTransientStatus(res.status) && attempt < maxAttempts) {
          await delay(backoff(attempt))
          continue
        }
        const text = await res.text()
        const parsed = text ? (JSON.parse(text) as ApiErrorBody) : undefined
        throw new ApiError(res.status, parsed ?? { code: 'ERROR', message: res.statusText })
      }

      const text = await res.text()
      return (text ? JSON.parse(text) : undefined) as T
    } catch (err) {
      // A real API error (4xx, or an exhausted transient) is final — surface it.
      if (err instanceof ApiError) throw err
      // Network error / timeout / abort: retry if we can, else report as a network failure.
      if (retryable && attempt < maxAttempts) {
        await delay(backoff(attempt))
        continue
      }
      throw new ApiError(0, { code: 'NETWORK', message: 'Network error — please retry.' })
    }
  }
}
