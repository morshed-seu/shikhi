import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import {
  authApi,
  type LoginInput,
  type RegisterInput,
  type TokenPair,
  type User,
} from '../api/auth'
import { AuthContext } from './auth-context'

const REFRESH_KEY = 'shikhi.refreshToken'

// Token strategy (M1): the short-lived access token lives only in memory (a ref, never
// persisted); the rotating refresh token is kept in localStorage so a page reload can
// silently re-establish the session via /auth/refresh. localStorage is XSS-reachable —
// moving the refresh token to an HttpOnly cookie is a documented later hardening choice.
export function AuthProvider({ children }: { children: ReactNode }) {
  const accessToken = useRef<string | null>(null)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const clearSession = useCallback(() => {
    accessToken.current = null
    localStorage.removeItem(REFRESH_KEY)
    setUser(null)
  }, [])

  const establishSession = useCallback(async (tokens: TokenPair) => {
    accessToken.current = tokens.accessToken
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    setUser(await authApi.me(tokens.accessToken))
  }, [])

  // On first load, try to resume a session from a stored refresh token.
  useEffect(() => {
    const stored = localStorage.getItem(REFRESH_KEY)
    if (!stored) {
      setLoading(false)
      return
    }
    authApi
      .refresh(stored)
      .then(establishSession)
      .catch(clearSession)
      .finally(() => setLoading(false))
  }, [establishSession, clearSession])

  const login = useCallback(
    async (input: LoginInput) => {
      await establishSession(await authApi.login(input))
    },
    [establishSession],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      await establishSession(await authApi.register(input))
    },
    [establishSession],
  )

  const logout = useCallback(async () => {
    const token = accessToken.current
    try {
      if (token) await authApi.logout(token)
    } finally {
      clearSession()
    }
  }, [clearSession])

  const getToken = useCallback(() => accessToken.current, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, getToken }}>
      {children}
    </AuthContext.Provider>
  )
}
