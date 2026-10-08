import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { supabase } from '../lib/supabase'

interface AuthCtx {
  user: { email: string } | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<string | null>
  signUp: (email: string, password: string) => Promise<string | null>
  signOut: () => Promise<void>
}

const Ctx = createContext<AuthCtx | null>(null)
const KEY = 'solarpulse-session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthCtx['user']>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (supabase) {
      supabase.auth.getSession().then(({ data }) => {
        setUser(data.session?.user?.email ? { email: data.session.user.email } : null)
        setLoading(false)
      })
      const { data } = supabase.auth.onAuthStateChange((_e, session) =>
        setUser(session?.user?.email ? { email: session.user.email } : null),
      )
      return () => data.subscription.unsubscribe()
    }
    try {
      const raw = localStorage.getItem(KEY)
      if (raw) setUser(JSON.parse(raw))
    } catch {
      /* ignore */
    }
    setLoading(false)
  }, [])

  const signIn = async (email: string, password: string) => {
    if (supabase) {
      const { error } = await supabase.auth.signInWithPassword({ email, password })
      return error ? error.message : null
    }
    if (!email || !password) return 'invalid'
    const u = { email }
    try {
      localStorage.setItem(KEY, JSON.stringify(u))
    } catch {
      /* ignore */
    }
    setUser(u)
    return null
  }

  const signUp = async (email: string, password: string) => {
    if (supabase) {
      const { error } = await supabase.auth.signUp({ email, password })
      return error ? error.message : null
    }
    return signIn(email, password)
  }

  const signOut = async () => {
    if (supabase) await supabase.auth.signOut()
    try {
      localStorage.removeItem(KEY)
    } catch {
      /* ignore */
    }
    setUser(null)
  }

  return <Ctx.Provider value={{ user, loading, signIn, signUp, signOut }}>{children}</Ctx.Provider>
}

export function useAuth() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useAuth outside provider')
  return c
}
