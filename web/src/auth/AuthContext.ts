import { createContext } from 'react'
import type { User } from '../lib/types'

export interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => void
  canManageVenues: boolean
}

export const AuthContext = createContext<AuthState | null>(null)
