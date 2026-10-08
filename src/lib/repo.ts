/**
 * Persistence layer. Uses Supabase when VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY
 * are set (see supabase/schema.sql), otherwise browser localStorage with demo seed data.
 */
import type { CollectionName, DB, Settings } from './types'
import { buildAlertRules, buildSeed, defaultSettings, withSiteDefaults } from './seed'
import { supabase } from './supabase'

/** Latest real telemetry point for a site (Supabase `readings`). */
export interface Reading {
  ts: string
  powerKw: number
}

export interface Repo {
  load(): Promise<DB>
  upsert(table: CollectionName, row: { id: string }): Promise<void>
  remove(table: CollectionName, id: string): Promise<void>
  saveSettings(s: Settings): Promise<void>
  replaceAll(db: DB): Promise<void>
  /** Stores vendor credentials write-only. Demo mode never stores secrets. */
  setIntegrationSecret(integrationId: string, secret: Record<string, string>): Promise<void>
  /** Newest reading for a site at or after `sinceIso`, or null. */
  latestReading(siteId: string, sinceIso: string): Promise<Reading | null>
}

const KEY = 'solarpulse-db-v1'

const snake = (s: string) => s.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase())
const camel = (s: string) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
const mapKeys = (o: Record<string, unknown>, f: (s: string) => string) =>
  Object.fromEntries(Object.entries(o).map(([k, v]) => [f(k), v]))

const TABLES: Record<CollectionName, string> = {
  sites: 'sites',
  devices: 'devices',
  tickets: 'tickets',
  invoices: 'invoices',
  notifications: 'notifications',
  reports: 'reports',
  alertRules: 'alert_rules',
  integrations: 'integrations',
}

/** Parents before children: replaceAll inserts in this order and deletes in reverse. */
const ORDER: CollectionName[] = ['sites', 'devices', 'tickets', 'invoices', 'notifications', 'reports', 'alertRules', 'integrations']

const withoutNulls = (o: Record<string, unknown>) => Object.fromEntries(Object.entries(o).filter(([, v]) => v != null))

class LocalRepo implements Repo {
  private db: DB | null = null
  async load() {
    try {
      const raw = localStorage.getItem(KEY)
      if (raw) {
        this.db = normalizeDB(JSON.parse(raw) as Partial<DB>)
        this.persist()
        return this.db
      }
    } catch {
      /* corrupted or unavailable storage: fall back to seed */
    }
    this.db = buildSeed()
    this.persist()
    return this.db
  }
  private persist() {
    try {
      localStorage.setItem(KEY, JSON.stringify(this.db))
    } catch {
      /* storage full / blocked */
    }
  }
  async upsert(table: CollectionName, row: { id: string }) {
    if (!this.db) return
    const list = this.db[table] as { id: string }[]
    const i = list.findIndex((r) => r.id === row.id)
    if (i >= 0) list[i] = row
    else list.unshift(row)
    this.persist()
  }
  async remove(table: CollectionName, id: string) {
    if (!this.db) return
    ;(this.db[table] as { id: string }[]) = (this.db[table] as { id: string }[]).filter((r) => r.id !== id)
    this.persist()
  }
  async saveSettings(s: Settings) {
    if (!this.db) return
    this.db.settings = s
    this.persist()
  }
  async replaceAll(db: DB) {
    this.db = db
    this.persist()
  }
  async setIntegrationSecret() {
    /* demo mode: secrets are intentionally not stored in the browser */
  }
  async latestReading() {
    return null
  }
}

/** Upgrades data saved by older versions: new collections, ROI fields, new settings. */
export function normalizeDB(parsed: Partial<DB>): DB {
  return {
    ...buildEmpty(),
    ...parsed,
    sites: (parsed.sites ?? []).map(withSiteDefaults),
    alertRules: parsed.alertRules ?? buildAlertRules(),
    integrations: parsed.integrations ?? [],
    settings: { ...defaultSettings, ...withoutNulls((parsed.settings ?? {}) as unknown as Record<string, unknown>) },
  } as DB
}

class SupabaseRepo implements Repo {
  async load(): Promise<DB> {
    const sb = supabase!
    const db = buildEmpty()
    for (const [name, table] of Object.entries(TABLES) as [CollectionName, string][]) {
      const { data, error } = await sb.from(table).select('*')
      if (error) throw error
      ;(db[name] as unknown[]) = (data ?? []).map((r) => mapKeys(r, camel))
    }
    const { data: s } = await sb.from('settings').select('*').eq('id', 'settings').maybeSingle()
    db.settings = { ...defaultSettings, ...(s ? (withoutNulls(mapKeys(s, camel)) as Partial<Settings>) : {}) }
    db.sites = db.sites.map(withSiteDefaults)
    // First run on an empty project: seed it so the dashboard isn't blank.
    if (db.sites.length === 0) {
      const seed = buildSeed()
      await this.replaceAll(seed)
      return seed
    }
    sortByDate(db)
    return db
  }
  async upsert(table: CollectionName, row: { id: string }) {
    const { error } = await supabase!.from(TABLES[table]).upsert(mapKeys(row as Record<string, unknown>, snake))
    if (error) throw error
  }
  async remove(table: CollectionName, id: string) {
    const { error } = await supabase!.from(TABLES[table]).delete().eq('id', id)
    if (error) throw error
  }
  async saveSettings(s: Settings) {
    const { error } = await supabase!.from('settings').upsert(mapKeys(s as unknown as Record<string, unknown>, snake))
    if (error) throw error
  }
  async replaceAll(db: DB) {
    const sb = supabase!
    // delete children first, insert parents first
    for (const name of [...ORDER].reverse()) await sb.from(TABLES[name]).delete().neq('id', '')
    for (const name of ORDER) {
      const rows = (db[name] as unknown as Record<string, unknown>[]).map((r) => mapKeys(r, snake))
      if (rows.length) {
        const { error } = await sb.from(TABLES[name]).insert(rows)
        if (error) throw error
      }
    }
    await this.saveSettings(db.settings)
  }
  async setIntegrationSecret(integrationId: string, secret: Record<string, string>) {
    const { error } = await supabase!.rpc('set_integration_secret', { p_integration_id: integrationId, p_secret: secret })
    if (error) throw error
  }
  async latestReading(siteId: string, sinceIso: string) {
    const { data, error } = await supabase!
      .from('readings')
      .select('ts, power_kw')
      .eq('site_id', siteId)
      .gte('ts', sinceIso)
      .order('ts', { ascending: false })
      .limit(1)
      .maybeSingle()
    if (error) throw error
    return data ? { ts: data.ts as string, powerKw: Number(data.power_kw) } : null
  }
}

function sortByDate(db: DB) {
  db.notifications.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  db.tickets.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  db.invoices.sort((a, b) => b.period.localeCompare(a.period) || a.number.localeCompare(b.number))
  db.reports.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  db.sites.sort((a, b) => a.id.localeCompare(b.id))
  db.devices.sort((a, b) => a.id.localeCompare(b.id))
  db.alertRules.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  db.integrations.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

function buildEmpty(): DB {
  return { sites: [], devices: [], tickets: [], invoices: [], notifications: [], reports: [], alertRules: [], integrations: [], settings: { ...defaultSettings } }
}

export const repo: Repo = supabase ? new SupabaseRepo() : new LocalRepo()
export const STORAGE_KEY = KEY
