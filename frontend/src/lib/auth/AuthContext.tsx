'use client'

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import axios from 'axios'
import { login as apiLogin, logout as apiLogout } from '@/lib/api/auth'
import { tokenStore } from '@/lib/api/client'
import type { AuthUser, LoginRequest, Role } from '@/types'

interface AuthContextType {
  user:        AuthUser | null
  isLoading:   boolean
  isAuthed:    boolean
  login:       (req: LoginRequest) => Promise<void>
  logout:      () => Promise<void>
  isAdmin:     boolean
  isManager:   boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

function parseJwt(token: string): { sub: string; username: string; role: Role; storeId?: string } | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload
  } catch {
    return null
  }
}

function userFromToken(token: string): AuthUser | null {
  const claims = parseJwt(token)
  if (!claims) return null
  return { userId: claims.sub, username: claims.username, role: claims.role, storeId: claims.storeId ?? null }
}

const BUILD_ID      = process.env.NEXT_PUBLIC_BUILD_ID ?? ''
const BUILD_ID_KEY  = 'sl_build'

function clearStaleSession() {
  // If the stored build ID doesn't match this build, a new frontend was deployed.
  // Wipe all auth sessionStorage so the user gets a clean login rather than a
  // broken refresh attempt against a JWT the backend may no longer recognise.
  const stored = typeof window !== 'undefined' ? sessionStorage.getItem(BUILD_ID_KEY) : null
  if (BUILD_ID && stored && stored !== BUILD_ID) {
    sessionStorage.clear()
  }
  if (typeof window !== 'undefined' && BUILD_ID) {
    sessionStorage.setItem(BUILD_ID_KEY, BUILD_ID)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser]         = useState<AuthUser | null>(null)
  const [isLoading, setLoading] = useState(true)

  useEffect(() => {
    // Detect new deploy and wipe stale tokens before attempting any refresh
    clearStaleSession()

    const accessToken = tokenStore.getAccess()

    if (accessToken) {
      // In-memory token present (same-session SPA navigation)
      setUser(userFromToken(accessToken))
      setLoading(false)
      return
    }

    // No in-memory token — try a silent refresh using the sessionStorage refresh token
    const refreshToken = tokenStore.getRefresh()
    if (!refreshToken) {
      setLoading(false)
      return
    }

    axios
      .post<{ data: { accessToken: string; refreshToken: string } }>(
        '/api/auth/refresh',
        { refreshToken }
      )
      .then(({ data }) => {
        tokenStore.set(data.data.accessToken, data.data.refreshToken)
        setUser(userFromToken(data.data.accessToken))
      })
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (req: LoginRequest) => {
    const resp = await apiLogin(req)
    setUser({ userId: resp.userId, username: resp.username, role: resp.role, storeId: resp.storeId })
  }, [])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
  }, [])

  const isAuthed  = !!user
  const isAdmin   = user?.role === 'ADMIN'
  const isManager = user?.role === 'ADMIN' || user?.role === 'STORE_MANAGER'

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthed, login, logout, isAdmin, isManager }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
