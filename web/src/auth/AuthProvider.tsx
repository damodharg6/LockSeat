import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../lib/api'
import { tokenStore } from '../lib/token'
import type { TokenResponse, User } from '../lib/types'
import { AuthContext } from './AuthContext'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  const loadMe = useCallback(async () => {
    try {
      setUser(await api<User>('/api/v1/users/me'))
    } catch {
      tokenStore.clear()
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (tokenStore.get()) void loadMe()
    const onSignedOut = () => setUser(null)
    window.addEventListener('seatlock:signed-out', onSignedOut)
    return () => window.removeEventListener('seatlock:signed-out', onSignedOut)
  }, [loadMe])

  const login = useCallback(async (email: string, password: string) => {
    const token = await api<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    tokenStore.set(token.accessToken)
    await loadMe()
  }, [loadMe])

  const signup = useCallback(async (email: string, password: string, fullName: string) => {
    await api<User>('/api/v1/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ email, password, fullName }),
    })
    await login(email, password)
  }, [login])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo(() => ({
    user,
    loading,
    login,
    signup,
    logout,
    canManageVenues: user?.role === 'ORGANISER' || user?.role === 'ADMIN',
  }), [user, loading, login, signup, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
