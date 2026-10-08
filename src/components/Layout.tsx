import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate, Link } from 'react-router-dom'
import {
  LayoutGrid, MapPin, BarChart3, Cpu, FileText, Wrench, CreditCard, Settings as SettingsIcon, Search, Bell, CalendarDays,
  Menu, X, Zap, ChevronDown, LogOut, User, Moon, Sun, Languages, ChevronLeft, ChevronRight, Leaf, Map as MapIcon, BellRing, TrendingUp,
} from 'lucide-react'
import { useData } from '../context/data'
import { useI18n, LANGS } from '../context/i18n'
import { useAuth } from '../context/auth'
import { cn, dayKey, addDays, initials, monthKey } from '../lib/utils'
import { useClickOutside } from './ui'
import { co2Tons, totalKwh } from '../lib/sim'
import { useAlertEngine } from '../context/alerts'
import type { DictKey } from '../i18n/en'

export const NAV: { to: string; key: DictKey; icon: typeof LayoutGrid }[] = [
  { to: '/', key: 'nav.overview', icon: LayoutGrid },
  { to: '/sites', key: 'nav.sites', icon: MapPin },
  { to: '/map', key: 'nav.map', icon: MapIcon },
  { to: '/analytics', key: 'nav.analytics', icon: BarChart3 },
  { to: '/finance', key: 'nav.finance', icon: TrendingUp },
  { to: '/devices', key: 'nav.devices', icon: Cpu },
  { to: '/alerts', key: 'nav.alerts', icon: BellRing },
  { to: '/reports', key: 'nav.reports', icon: FileText },
  { to: '/maintenance', key: 'nav.maintenance', icon: Wrench },
  { to: '/billing', key: 'nav.billing', icon: CreditCard },
  { to: '/settings', key: 'nav.settings', icon: SettingsIcon },
]

export function Logo({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center gap-2', className)} dir="ltr">
      <div className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-md shadow-brand-600/30">
        <Zap className="h-4.5 w-4.5" fill="currentColor" />
      </div>
      <span className="text-lg font-bold tracking-tight text-slate-800 dark:text-white">SolarPulse</span>
    </div>
  )
}

export default function Layout() {
  const { db } = useData()
  const { dir, lang } = useI18n()
  const [mobileOpen, setMobileOpen] = useState(false)
  const loc = useLocation()
  useAlertEngine()

  useEffect(() => setMobileOpen(false), [loc.pathname])

  useEffect(() => {
    document.documentElement.dir = dir
    document.documentElement.lang = lang
  }, [dir, lang])

  useEffect(() => {
    const apply = () => {
      const dark = db.settings.theme === 'dark' || (db.settings.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
      document.documentElement.classList.toggle('dark', dark)
    }
    apply()
    const mq = matchMedia('(prefers-color-scheme: dark)')
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [db.settings.theme])

  return (
    <div className="min-h-screen p-0 lg:p-5">
      <div className="app-bg" />
      <div className="glass mx-auto flex min-h-screen max-w-[1600px] overflow-hidden lg:min-h-[calc(100vh-2.5rem)] lg:rounded-[28px]">
        <Sidebar open={mobileOpen} onClose={() => setMobileOpen(false)} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar onMenu={() => setMobileOpen(true)} />
          <main className="scrollbar-thin min-w-0 flex-1 overflow-x-hidden px-4 pb-8 pt-2 lg:px-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, fmt } = useI18n()
  const { db } = useData()
  const { signOut, user } = useAuth()
  const navigate = useNavigate()
  const [menu, setMenu] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useClickOutside(ref, () => setMenu(false))

  const yearCo2 = useMemo(() => {
    const now = new Date()
    return co2Tons(totalKwh(db.sites, new Date(now.getFullYear(), 0, 1), now), db.settings.co2KgPerKwh)
  }, [db.sites, db.settings.co2KgPerKwh])

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-sm lg:hidden" onClick={onClose} />}
      <aside
        className={cn(
          'no-print scrollbar-thin fixed inset-y-0 start-0 z-50 flex w-64 shrink-0 flex-col gap-4 overflow-y-auto border-e border-white/60 bg-white/90 p-4 backdrop-blur-xl transition-transform lg:static lg:z-auto lg:w-60 lg:bg-white/40 dark:border-white/10 dark:bg-slate-900/90 lg:dark:bg-slate-900/30',
          !open && 'max-lg:ltr:-translate-x-full max-lg:rtl:translate-x-full',
        )}
      >
        <div className="flex items-center justify-between px-2 pt-2">
          <Logo />
          <button className="icon-btn lg:hidden" onClick={onClose}>
            <X className="h-4 w-4" />
          </button>
        </div>
        <nav className="mt-2 flex flex-col gap-0.5">
          {NAV.map(({ to, key, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
                  isActive ? 'bg-brand-50 text-brand-700 shadow-sm ring-1 ring-brand-100 dark:bg-brand-500/15 dark:text-brand-300 dark:ring-brand-500/20' : 'text-slate-600 hover:bg-white/70 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800',
                )
              }
            >
              <Icon className="h-[18px] w-[18px]" />
              {t(key)}
            </NavLink>
          ))}
        </nav>

        <div className="flex-1" />
        <div className="overflow-hidden rounded-2xl bg-white/80 shadow-sm ring-1 ring-white [@media(max-height:900px)]:hidden dark:bg-slate-800/80 dark:ring-white/10">
          <div className="relative h-24 bg-[url('https://images.unsplash.com/photo-1508514177221-188b1cf16e9d?auto=format&fit=crop&w=600&q=60')] bg-cover bg-center">
            <div className="absolute inset-0 bg-gradient-to-t from-black/30 to-transparent" />
            <Leaf className="absolute bottom-2 start-2 h-5 w-5 text-white" />
          </div>
          <div className="p-3">
            <div className="text-sm font-semibold">{t('ov.promoTitle')}</div>
            <p className="mt-1 text-xs leading-relaxed text-slate-500 dark:text-slate-400">{t('ov.promoBody', { n: fmt.num(yearCo2) })}</p>
            <Link to="/analytics#impact" className="btn btn-ghost mt-2 w-full py-1.5 text-xs">
              {t('ov.viewImpact')} <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" />
            </Link>
          </div>
        </div>

        <div ref={ref} className="relative">
          <button onClick={() => setMenu((m) => !m)} className="flex w-full cursor-pointer items-center gap-3 rounded-xl p-2 text-start hover:bg-white/70 dark:hover:bg-slate-800">
            <div className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-br from-amber-300 to-orange-500 text-sm font-semibold text-white">{initials(db.settings.userName) || 'U'}</div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-semibold">{db.settings.userName}</div>
              <div className="truncate text-xs text-slate-500">{t(`role.${db.settings.role}` as DictKey)}</div>
            </div>
            <ChevronDown className={cn('h-4 w-4 text-slate-400 transition', menu && 'rotate-180')} />
          </button>
          {menu && (
            <div className="fade-in absolute bottom-full start-0 mb-2 w-full rounded-xl border border-slate-100 bg-white p-1 shadow-lg dark:border-slate-700 dark:bg-slate-800">
              <div className="truncate px-3 py-2 text-xs text-slate-500">{user?.email}</div>
              <button onClick={() => navigate('/settings')} className="flex w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-700">
                <User className="h-4 w-4" /> {t('user.profile')}
              </button>
              <button onClick={() => signOut().then(() => navigate('/login'))} className="flex w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-500/10">
                <LogOut className="h-4 w-4" /> {t('user.logout')}
              </button>
            </div>
          )}
        </div>
      </aside>
    </>
  )
}

function Topbar({ onMenu }: { onMenu: () => void }) {
  const { updateSettings } = useData()
  const { t } = useI18n()
  const dark = document.documentElement.classList.contains('dark')
  return (
    <header className="no-print sticky top-0 z-30 flex items-center gap-1.5 px-4 sm:gap-2 pb-2 pt-4 lg:px-6">
      <button className="icon-btn lg:hidden" onClick={onMenu} aria-label="menu">
        <Menu className="h-4 w-4" />
      </button>
      <Logo className="hidden sm:flex lg:hidden" />
      <div className="hidden flex-1 sm:block" />
      <GlobalSearch />
      <LanguageMenu />
      <button className="icon-btn hidden sm:inline-grid" title={t('set.theme')} onClick={() => updateSettings({ theme: dark ? 'light' : 'dark' })}>
        {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      </button>
      <Notifications />
      <CalendarPopover />
    </header>
  )
}

export function LanguageMenu() {
  const { db, updateSettings } = useData()
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useClickOutside(ref, () => setOpen(false))
  return (
    <div ref={ref} className="relative">
      <button className="icon-btn" title={t('set.language')} aria-label={t('set.language')} onClick={() => setOpen((o) => !o)}>
        <Languages className="h-4 w-4" />
      </button>
      {open && (
        <div className="fade-in absolute end-0 z-40 mt-2 w-40 rounded-xl border border-slate-100 bg-white p-1 shadow-xl dark:border-slate-700 dark:bg-slate-800">
          {LANGS.map((l) => (
            <button
              key={l.code}
              dir={l.dir}
              onClick={() => { updateSettings({ language: l.code }); setOpen(false) }}
              className={cn('block w-full cursor-pointer rounded-lg px-3 py-1.5 text-start text-sm hover:bg-slate-50 dark:hover:bg-slate-700', l.code === db.settings.language && 'font-semibold text-brand-600')}
            >
              {l.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function GlobalSearch() {
  const { db } = useData()
  const { t } = useI18n()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  useClickOutside(ref, () => setOpen(false))

  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        inputRef.current?.focus()
        setOpen(true)
      }
    }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [])

  const results = useMemo(() => {
    const s = q.trim().toLowerCase()
    if (!s) return []
    const m = (...f: string[]) => f.some((x) => x.toLowerCase().includes(s))
    const siteName = (id: string) => db.sites.find((x) => x.id === id)?.name ?? ''
    const out: { group: string; label: string; sub: string; to: string }[] = []
    NAV.forEach((n) => m(t(n.key), n.to) && out.push({ group: t('search.pages'), label: t(n.key), sub: n.to, to: n.to }))
    db.sites.filter((x) => m(x.name, x.location, x.customer, x.id)).slice(0, 6).forEach((x) => out.push({ group: t('nav.sites'), label: x.name, sub: x.location, to: `/sites/${x.id}` }))
    db.devices.filter((x) => m(x.name, x.model, x.serial)).slice(0, 6).forEach((x) => out.push({ group: t('nav.devices'), label: x.name, sub: `${x.model} · ${siteName(x.siteId)}`, to: `/devices?q=${encodeURIComponent(x.name)}` }))
    db.tickets.filter((x) => m(x.title, x.assignee)).slice(0, 5).forEach((x) => out.push({ group: t('nav.maintenance'), label: x.title, sub: siteName(x.siteId), to: `/maintenance?open=${x.id}` }))
    db.invoices.filter((x) => m(x.number, x.customer)).slice(0, 5).forEach((x) => out.push({ group: t('nav.billing'), label: x.number, sub: x.customer, to: `/billing?open=${x.id}` }))
    return out
  }, [q, db, t])

  const go = (to: string) => {
    navigate(to)
    setOpen(false)
    setQ('')
  }

  return (
    <div ref={ref} className="relative min-w-0 flex-1 sm:flex-none">
      <div className="relative">
        <Search className="pointer-events-none absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input
          ref={inputRef}
          value={q}
          onChange={(e) => { setQ(e.target.value); setOpen(true); setActive(0) }}
          onFocus={() => setOpen(true)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)) }
            if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
            if (e.key === 'Enter' && results[active]) go(results[active].to)
            if (e.key === 'Escape') setOpen(false)
          }}
          placeholder={t('common.search')}
          className="h-9 w-full min-w-0 rounded-xl border border-white/70 bg-white/80 ps-9 pe-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-brand-300 focus:ring-4 focus:ring-brand-100 sm:w-64 sm:focus:w-80 dark:border-white/10 dark:bg-slate-800/80 dark:focus:ring-brand-500/20"
        />
      </div>
      {open && (
        <div className="fade-in absolute end-0 z-40 mt-2 max-h-96 w-80 overflow-y-auto rounded-2xl border border-slate-100 bg-white p-2 shadow-xl scrollbar-thin sm:w-96 dark:border-slate-700 dark:bg-slate-800">
          {!q && <div className="px-3 py-4 text-center text-xs text-slate-400">{t('search.hint')}</div>}
          {q && results.length === 0 && <div className="px-3 py-4 text-center text-sm text-slate-400">{t('search.noResults')}</div>}
          {results.map((r, i) => (
            <div key={r.to + i}>
              {(i === 0 || results[i - 1].group !== r.group) && <div className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-slate-400">{r.group}</div>}
              <button onMouseEnter={() => setActive(i)} onClick={() => go(r.to)} className={cn('block w-full cursor-pointer rounded-lg px-3 py-2 text-start', i === active && 'bg-brand-50 dark:bg-slate-700')}>
                <div className="truncate text-sm font-medium">{r.label}</div>
                <div className="truncate text-xs text-slate-500">{r.sub}</div>
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function Notifications() {
  const { db, upsert, remove } = useData()
  const { t, fmt } = useI18n()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useClickOutside(ref, () => setOpen(false))
  const unread = db.notifications.filter((n) => !n.read).length
  const dot = { info: 'bg-blue-500', success: 'bg-emerald-500', warning: 'bg-amber-500', danger: 'bg-rose-500' }
  const list = [...db.notifications].sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  return (
    <div ref={ref} className="relative">
      <button className="icon-btn relative" onClick={() => setOpen((o) => !o)} aria-label={t('ntf.title')}>
        <Bell className="h-4 w-4" />
        {unread > 0 && <span className="absolute -end-1 -top-1 grid h-4 min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">{unread}</span>}
      </button>
      {open && (
        <div className="fade-in absolute end-0 z-40 mt-2 w-80 rounded-2xl border border-slate-100 bg-white shadow-xl sm:w-96 dark:border-slate-700 dark:bg-slate-800">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-700">
            <span className="font-semibold">{t('ntf.title')}</span>
            <div className="flex gap-3 text-xs">
              <button className="cursor-pointer text-brand-600 hover:underline" onClick={() => list.filter((n) => !n.read).forEach((n) => upsert('notifications', { ...n, read: true }))}>{t('ntf.markAll')}</button>
              <button className="cursor-pointer text-slate-400 hover:text-rose-600" onClick={() => list.forEach((n) => remove('notifications', n.id))}>{t('ntf.clear')}</button>
            </div>
          </div>
          <div className="max-h-96 overflow-y-auto scrollbar-thin">
            {list.length === 0 && <div className="py-8 text-center text-sm text-slate-400">{t('ntf.empty')}</div>}
            {list.map((n) => (
              <button
                key={n.id}
                onClick={() => {
                  upsert('notifications', { ...n, read: true })
                  if (n.link) navigate(n.link)
                  setOpen(false)
                }}
                className={cn('flex w-full cursor-pointer gap-3 border-b border-slate-50 px-4 py-3 text-start last:border-0 hover:bg-slate-50 dark:border-slate-700/50 dark:hover:bg-slate-700/40', !n.read && 'bg-brand-50/40 dark:bg-brand-500/5')}
              >
                <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', dot[n.kind])} />
                <div className="min-w-0 flex-1">
                  <div className={cn('text-sm', !n.read && 'font-semibold')}>{n.title}</div>
                  <div className="line-clamp-2 text-xs text-slate-500">{n.body}</div>
                  <div className="mt-1 text-[11px] text-slate-400">{fmt.ago(n.createdAt)}</div>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function CalendarPopover() {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [sel, setSel] = useState(dayKey(new Date()))
  const ref = useRef<HTMLDivElement>(null)
  useClickOutside(ref, () => setOpen(false))

  const events = useMemo(() => {
    const map = new Map<string, { label: string; to: string; color: string }[]>()
    const add = (k: string, e: { label: string; to: string; color: string }) => map.set(k, [...(map.get(k) ?? []), e])
    db.tickets.filter((x) => x.status !== 'resolved').forEach((x) => add(x.dueDate, { label: x.title, to: `/maintenance?open=${x.id}`, color: 'bg-amber-500' }))
    db.invoices.filter((x) => x.status !== 'paid').forEach((x) => add(x.dueAt, { label: `${x.number} · ${x.customer}`, to: `/billing?open=${x.id}`, color: 'bg-rose-500' }))
    return map
  }, [db.tickets, db.invoices])

  const start = addDays(month, -((month.getDay() + 6) % 7)) // Monday-first grid
  const cells = Array.from({ length: 42 }, (_, i) => addDays(start, i))
  const today = dayKey(new Date())
  const selEvents = events.get(sel) ?? []

  return (
    <div ref={ref} className="relative">
      <button className="icon-btn" onClick={() => setOpen((o) => !o)} aria-label={t('cal.title')}>
        <CalendarDays className="h-4 w-4" />
      </button>
      {open && (
        <div className="fade-in absolute end-0 z-40 mt-2 w-80 rounded-2xl border border-slate-100 bg-white p-4 shadow-xl dark:border-slate-700 dark:bg-slate-800">
          <div className="mb-3 flex items-center justify-between" dir="ltr">
            <button className="icon-btn h-7 w-7" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><ChevronLeft className="h-4 w-4" /></button>
            <span className="text-sm font-semibold">{fmt.month(month)}</span>
            <button className="icon-btn h-7 w-7" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><ChevronRight className="h-4 w-4" /></button>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center" dir="ltr">
            {cells.slice(0, 7).map((d) => <div key={'h' + d} className="truncate text-[10px] font-medium text-slate-400">{fmt.weekday(d)}</div>)}
            {cells.map((d) => {
              const k = dayKey(d)
              const ev = events.get(k)
              return (
                <button
                  key={k}
                  onClick={() => setSel(k)}
                  className={cn(
                    'relative h-9 cursor-pointer rounded-lg text-xs transition',
                    monthKey(d) !== monthKey(month) && 'text-slate-300 dark:text-slate-600',
                    k === today && 'font-bold text-brand-600',
                    k === sel ? 'bg-brand-600 text-white' : 'hover:bg-slate-100 dark:hover:bg-slate-700',
                  )}
                >
                  {d.getDate()}
                  {ev && <span className={cn('absolute bottom-1 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full', k === sel ? 'bg-white' : ev[0].color)} />}
                </button>
              )
            })}
          </div>
          <div className="mt-3 border-t border-slate-100 pt-3 dark:border-slate-700">
            <div className="mb-2 text-xs font-semibold text-slate-500">{fmt.date(sel)} · {t('cal.events')}</div>
            {selEvents.length === 0 && <div className="text-xs text-slate-400">{t('cal.noEvents')}</div>}
            <div className="max-h-32 space-y-1 overflow-y-auto scrollbar-thin">
              {selEvents.map((e, i) => (
                <button key={i} onClick={() => { navigate(e.to); setOpen(false) }} className="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-start text-xs hover:bg-slate-50 dark:hover:bg-slate-700">
                  <span className={cn('h-2 w-2 shrink-0 rounded-full', e.color)} />
                  <span className="truncate">{e.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
