import { createContext } from 'react'
import type { Locale, LoginInput, RegisterInput, User } from '../api/auth'

export interface AuthState {
  user: User | null
  /** True while the initial "am I still logged in?" check is in flight. */
  loading: boolean
  login: (input: LoginInput) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  /** Current access token for authenticated API calls, or null when signed out. */
  getToken: () => string | null
  /** Update the cached user's UI locale (kept in sync with a language toggle). */
  setUiLocale: (locale: Locale) => void
}

export const AuthContext = createContext<AuthState | null>(null)
