'use client'

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser]         = useState<AuthUser | null>(null)
  const [isLoading, setLoading] = useState(true)

  // Restore session from in-memory token on mount (e.g. after SPA navigation)
  useEffect(() => {
    const token = tokenStore.getAccess()
    if (token) {
      const claims = parseJwt(token)
      if (claims) {
        setUser({ userId: claims.sub, username: claims.username, role: claims.role, storeId: claims.storeId ?? null })
      }
    }
    setLoading(false)
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
