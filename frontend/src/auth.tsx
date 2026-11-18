import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshSession, setAccessToken } from './api'
import type { Me } from './types'

type AuthState = {
  ready: boolean
  me: Me | null
  login: (email: string, password: string) => Promise<void>
  signup: (username: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  reloadMe: () => Promise<Me | null>
  setMe: (m: Me) => void
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [me, setMe] = useState<Me | null>(null)

  const reloadMe = useCallback(async () => {
    try {
      const m = await api<Me>('/users/me')
      setMe(m)
      return m
    } catch {
      setMe(null)
      return null
    }
  }, [])

  useEffect(() => {
    ;(async () => {
      if (await refreshSession()) await reloadMe()
      setReady(true)
    })()
  }, [reloadMe])

  const value = useMemo<AuthState>(
    () => ({
      ready,
      me,
      setMe,
      reloadMe,
      login: async (email, password) => {
        const r = await api<{ accessToken: string }>('/auth/login', { method: 'POST', body: { email, password }, noAuth: true })
        setAccessToken(r.accessToken)
        await reloadMe()
      },
      signup: async (username, email, password) => {
        const r = await api<{ accessToken: string }>('/auth/signup', { method: 'POST', body: { username, email, password }, noAuth: true })
        setAccessToken(r.accessToken)
        await reloadMe()
      },
      logout: async () => {
        try { await api('/auth/logout', { method: 'POST' }) } catch { /* 이미 만료됐어도 로컬 상태는 지운다 */ }
        setAccessToken(null)
        setMe(null)
      },
    }),
    [ready, me, reloadMe],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth() {
  const v = useContext(Ctx)
  if (!v) throw new Error('AuthProvider 밖에서 호출했어요')
  return v
}
