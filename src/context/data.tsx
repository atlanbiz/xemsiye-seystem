import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { AppNotification, CollectionName, DB, Row, Settings } from '../lib/types'
import { repo } from '../lib/repo'
import { buildSeed } from '../lib/seed'
import { dayKey, uid } from '../lib/utils'

interface DataCtx {
  db: DB
  ready: boolean
  error: string | null
  now: number
  upsert: <C extends CollectionName>(c: C, row: Row<C>) => Promise<void>
  upsertMany: <C extends CollectionName>(c: C, rows: Row<C>[]) => Promise<void>
  remove: (c: CollectionName, id: string) => Promise<void>
  removeSite: (id: string) => Promise<void>
  updateSettings: (patch: Partial<Settings>) => Promise<void>
  notify: (n: Omit<AppNotification, 'id' | 'read' | 'createdAt'>) => Promise<void>
  replaceAll: (db: DB) => Promise<void>
  resetDemo: () => Promise<void>
}

const Ctx = createContext<DataCtx | null>(null)

export function DataProvider({ children }: { children: ReactNode }) {
  const [db, setDb] = useState<DB | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(Date.now())
  const dbRef = useRef<DB | null>(null)
  dbRef.current = db

  useEffect(() => {
    repo
      .load()
      .then((loaded) => setDb(refreshOverdue(loaded)))
      .catch((e) => {
        console.error(e)
        setError(String(e?.message ?? e))
        setDb(buildSeed())
      })
  }, [])

  // live clock: drives current power, energy flow, weather time
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 5000)
    return () => clearInterval(t)
  }, [])

  const run = useCallback((p: Promise<void>) => p.catch((e) => setError(String(e?.message ?? e))), [])

  const upsert = useCallback(
    async <C extends CollectionName>(c: C, row: Row<C>) => {
      setDb((prev) => {
        if (!prev) return prev
        const list = prev[c] as Row<C>[]
        const i = list.findIndex((r) => r.id === row.id)
        const next = i >= 0 ? list.map((r) => (r.id === row.id ? row : r)) : [row, ...list]
        return { ...prev, [c]: next }
      })
      await run(repo.upsert(c, row))
    },
    [run],
  )

  const upsertMany = useCallback(
    async <C extends CollectionName>(c: C, rows: Row<C>[]) => {
      for (const r of rows) await upsert(c, r)
    },
    [upsert],
  )

  const remove = useCallback(
    async (c: CollectionName, id: string) => {
      setDb((prev) => (prev ? { ...prev, [c]: (prev[c] as { id: string }[]).filter((r) => r.id !== id) } : prev))
      await run(repo.remove(c, id))
    },
    [run],
  )

  const removeSite = useCallback(
    async (id: string) => {
      const cur = dbRef.current
      if (!cur) return
      for (const c of ['devices', 'tickets', 'invoices', 'integrations', 'alertRules'] as const)
        for (const r of (cur[c] as { id: string; siteId: string | null }[]).filter((r) => r.siteId === id)) await remove(c, r.id)
      await remove('sites', id)
    },
    [remove],
  )

  const updateSettings = useCallback(
    async (patch: Partial<Settings>) => {
      const cur = dbRef.current
      if (!cur) return
      const s = { ...cur.settings, ...patch }
      setDb((prev) => (prev ? { ...prev, settings: s } : prev))
      await run(repo.saveSettings(s))
    },
    [run],
  )

  const notify = useCallback(
    async (n: Omit<AppNotification, 'id' | 'read' | 'createdAt'>) => {
      const s = dbRef.current?.settings
      if (s && !s.notifyPush) return
      await upsert('notifications', { ...n, id: uid('ntf-'), read: false, createdAt: new Date().toISOString() })
    },
    [upsert],
  )

  const replaceAll = useCallback(
    async (next: DB) => {
      setDb(next)
      await run(repo.replaceAll(next))
    },
    [run],
  )

  const resetDemo = useCallback(async () => {
    const seed = buildSeed()
    const s = dbRef.current?.settings
    if (s) seed.settings = { ...seed.settings, language: s.language, theme: s.theme, userName: s.userName, email: s.email }
    await replaceAll(seed)
  }, [replaceAll])

  const value = useMemo<DataCtx | null>(
    () =>
      db
        ? { db, ready: true, error, now, upsert, upsertMany, remove, removeSite, updateSettings, notify, replaceAll, resetDemo }
        : null,
    [db, error, now, upsert, upsertMany, remove, removeSite, updateSettings, notify, replaceAll, resetDemo],
  )

  if (!value)
    return (
      <div className="grid min-h-screen place-items-center">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
      </div>
    )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

/** Pending invoices whose due date passed become overdue. */
function refreshOverdue(db: DB): DB {
  const today = dayKey(new Date())
  let changed = false
  const invoices = db.invoices.map((i) => {
    if (i.status === 'pending' && i.dueAt < today) {
      changed = true
      const next = { ...i, status: 'overdue' as const }
      repo.upsert('invoices', next).catch(() => {})
      return next
    }
    return i
  })
  return changed ? { ...db, invoices } : db
}

export function useData() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useData outside provider')
  return c
}
