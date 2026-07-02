import { createContext } from 'react'
import type { LoginInput, RegisterInput, User } from '../api/auth'

export interface AuthState {
  user: User | null
  /** True while the initial "am I still logged in?" check is in flight. */
  loading: boolean
  login: (input: LoginInput) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)
