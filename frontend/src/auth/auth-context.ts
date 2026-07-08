import { createContext } from 'react'
import type { ClaimInput, Locale, LoginInput, RegisterInput, User } from '../api/auth'

export interface AuthState {
  user: User | null
  /** True while the initial "am I still logged in?" check is in flight. */
  loading: boolean
  login: (input: LoginInput) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  /** Start an anonymous guest session (learn without signing up). */
  startGuest: (uiLocale?: Locale) => Promise<void>
  /** Upgrade the current guest into a full account, keeping all progress. */
  claim: (input: ClaimInput) => Promise<void>
  logout: () => Promise<void>
  /** Current access token for authenticated API calls, or null when signed out. */
  getToken: () => string | null
  /** Update the cached user's UI locale (kept in sync with a language toggle). */
  setUiLocale: (locale: Locale) => void
  /**
   * Replace the cached user (e.g. after PATCH /me returns the updated profile).
   * Caution: full replace — callers must carry over the current cached `uiLocale`
   * (it's owned by the optimistic locale-toggle flow; a response racing that flow may
   * still hold the old locale, and adopting it would flip the UI language back).
   */
  refreshUser: (user: User) => void
}

export const AuthContext = createContext<AuthState | null>(null)
