import { apiFetch } from './client'

// Shapes mirror docs/43-api-contract.openapi.yaml (User, TokenPair, *Request).

export type Locale = 'bn' | 'en'

export interface User {
  id: string
  displayName: string | null
  uiLocale: Locale
  roles: string[]
  /** True for an anonymous guest that hasn't claimed the account yet. */
  isGuest: boolean
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface RegisterInput {
  email: string
  password: string
  displayName?: string
  uiLocale?: Locale
}

export interface LoginInput {
  email: string
  password: string
}

/** Adds email+password to the current guest account (in-place upgrade — keeps all progress). */
export interface ClaimInput {
  email: string
  password: string
  displayName?: string
}

export const authApi = {
  register: (input: RegisterInput) =>
    apiFetch<TokenPair>('/auth/register', { method: 'POST', body: input }),

  login: (input: LoginInput) =>
    apiFetch<TokenPair>('/auth/login', { method: 'POST', body: input }),

  guest: (uiLocale?: Locale) =>
    apiFetch<TokenPair>('/auth/guest', { method: 'POST', body: { uiLocale } }),

  claim: (token: string, input: ClaimInput) =>
    apiFetch<TokenPair>('/auth/claim', { method: 'POST', token, body: input }),

  refresh: (refreshToken: string) =>
    apiFetch<TokenPair>('/auth/refresh', { method: 'POST', body: { refreshToken } }),

  logout: (token: string) =>
    apiFetch<void>('/auth/logout', { method: 'POST', token }),

  me: (token: string) => apiFetch<User>('/me', { token }),

  updateProfile: (token: string, patch: { uiLocale?: Locale; displayName?: string }) =>
    apiFetch<User>('/me', { method: 'PATCH', token, body: patch }),
}
