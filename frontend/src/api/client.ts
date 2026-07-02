// Thin fetch wrapper for the Shikhi JSON API. In dev, Vite proxies /v1 to Spring Boot;
// in prod it is same-origin. Errors surface as ApiError carrying the contract's Error code.

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
}

export interface RequestOptions {
  method?: string
  body?: unknown
  token?: string | null
}

export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`

  const res = await fetch(`/v1${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })

  const text = await res.text()
  const data: unknown = text ? JSON.parse(text) : undefined

  if (!res.ok) {
    const body = (data as ApiErrorBody | undefined) ?? {
      code: 'ERROR',
      message: res.statusText,
    }
    throw new ApiError(res.status, body)
  }
  return data as T
}
