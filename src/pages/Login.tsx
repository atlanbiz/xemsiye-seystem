import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from '../context/auth'
import { useI18n } from '../context/i18n'
import { Field, Input } from '../components/ui'
import { Logo, LanguageMenu } from '../components/Layout'
import { isSupabase } from '../lib/supabase'
import { useEffect } from 'react'

export default function Login() {
  const { user, signIn, signUp } = useAuth()
  const { t, dir, lang } = useI18n()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'in' | 'up'>('in')
  const [email, setEmail] = useState(isSupabase ? '' : 'admin@solarpulse.app')
  const [password, setPassword] = useState(isSupabase ? '' : 'demo1234')
  const [err, setErr] = useState('')
  const [info, setInfo] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    document.documentElement.dir = dir
    document.documentElement.lang = lang
  }, [dir, lang])

  if (user) return <Navigate to="/" replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr('')
    setInfo('')
    setBusy(true)
    const res = mode === 'in' ? await signIn(email.trim(), password) : await signUp(email.trim(), password)
    setBusy(false)
    if (res) return setErr(res === 'invalid' ? t('auth.error') : res)
    if (mode === 'up' && isSupabase) return setInfo(t('auth.checkEmail'))
    navigate('/')
  }

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <div className="app-bg" />
      <div className="glass fade-in w-full max-w-md rounded-3xl p-8">
        <div className="flex items-center justify-between">
          <Logo />
          <LanguageMenu />
        </div>
        <h1 className="mt-8 text-2xl font-semibold">{t('auth.welcome')}</h1>
        <p className="mt-1 text-sm text-slate-500">{t('auth.subtitle')}</p>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <Field label={t('auth.email')}><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} dir="ltr" /></Field>
          <Field label={t('auth.password')}><Input type="password" required minLength={6} value={password} onChange={(e) => setPassword(e.target.value)} dir="ltr" /></Field>
          {err && <div className="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700">{err}</div>}
          {info && <div className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{info}</div>}
          <button className="btn btn-primary w-full !py-2.5" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />}{mode === 'in' ? t('auth.signIn') : t('auth.signUp')}</button>
        </form>
        {isSupabase ? (
          <button className="mt-4 w-full cursor-pointer text-center text-sm text-brand-600 hover:underline" onClick={() => setMode(mode === 'in' ? 'up' : 'in')}>{mode === 'in' ? t('auth.noAccount') : t('auth.haveAccount')}</button>
        ) : (
          <p className="mt-4 rounded-xl bg-brand-50 px-3 py-2 text-center text-xs text-brand-700 dark:bg-brand-500/10 dark:text-brand-300">{t('auth.demo')}</p>
        )}
      </div>
    </div>
  )
}
