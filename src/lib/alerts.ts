/**
 * Alert-rule evaluator — docs/PLATFORM.md §3.3. Demo mode runs it in the browser;
 * with Supabase the `evaluate-alerts` Edge Function applies the same semantics on a cron.
 */
import type { AlertMetric, AlertRule, DB } from './types'
import { siteDayKwhCached } from './sim'
import { dayKey, parseDay } from './utils'

export const COOLDOWN_MS = 6 * 3600_000
export const YIELD_CHECK_HOUR = 14

export const METRICS: AlertMetric[] = ['site_yield_below', 'site_offline', 'device_efficiency_below', 'device_health_below', 'device_offline_minutes', 'invoice_overdue_days']

/** Threshold unit shown next to the input; null = the metric has no threshold. */
export const METRIC_UNIT: Record<AlertMetric, string | null> = {
  site_yield_below: 'kWh/kWp',
  site_offline: null,
  device_efficiency_below: '%',
  device_health_below: '%',
  device_offline_minutes: 'min',
  invoice_overdue_days: 'd',
}

export interface AlertMatch {
  label: string // site / device / invoice it names
  value: number | null // measured value in the metric's unit
  link: string
}

export interface Firing {
  rule: AlertRule
  matches: AlertMatch[]
}

const inCooldown = (rule: AlertRule, now: number) => !!rule.lastTriggeredAt && now - new Date(rule.lastTriggeredAt).getTime() < COOLDOWN_MS

/** Entities currently breaching a rule (ignores enabled/cooldown). */
export function matchRule(rule: AlertRule, db: DB, now = new Date()): AlertMatch[] {
  const inScope = (siteId: string) => rule.siteId == null || rule.siteId === siteId
  const siteName = (id: string) => db.sites.find((s) => s.id === id)?.name ?? id
  const today = dayKey(now)
  switch (rule.metric) {
    case 'site_yield_below': {
      if (now.getHours() < YIELD_CHECK_HOUR) return []
      return db.sites
        .filter((s) => inScope(s.id) && s.capacityKw > 0)
        .map((s) => ({ s, v: siteDayKwhCached(s, today) / s.capacityKw }))
        .filter(({ v }) => v < rule.threshold)
        .map(({ s, v }) => ({ label: s.name, value: v, link: `/sites/${s.id}` }))
    }
    case 'site_offline':
      return db.sites.filter((s) => inScope(s.id) && s.status === 'offline').map((s) => ({ label: s.name, value: null, link: `/sites/${s.id}` }))
    case 'device_efficiency_below':
    case 'device_health_below': {
      const key = rule.metric === 'device_efficiency_below' ? 'efficiency' : 'health'
      return db.devices
        .filter((d) => inScope(d.siteId) && d[key] < rule.threshold)
        .map((d) => ({ label: `${d.name} · ${siteName(d.siteId)}`, value: d[key], link: `/devices?q=${encodeURIComponent(d.name)}` }))
    }
    case 'device_offline_minutes':
      return db.devices
        .filter((d) => inScope(d.siteId) && d.status === 'offline')
        .map((d) => ({ d, v: (now.getTime() - new Date(d.lastSeen).getTime()) / 60000 }))
        .filter(({ v }) => v > rule.threshold)
        .map(({ d, v }) => ({ label: `${d.name} · ${siteName(d.siteId)}`, value: v, link: `/devices?q=${encodeURIComponent(d.name)}` }))
    case 'invoice_overdue_days': {
      const t0 = parseDay(today).getTime()
      return db.invoices
        .filter((i) => inScope(i.siteId) && i.status === 'overdue')
        .map((i) => ({ i, v: Math.round((t0 - parseDay(i.dueAt).getTime()) / 86400000) }))
        .filter(({ v }) => v > rule.threshold)
        .map(({ i, v }) => ({ label: `${i.number} · ${i.customer}`, value: v, link: `/billing?open=${i.id}` }))
    }
  }
}

/** Enabled rules outside their 6-hour cooldown that have at least one match. */
export function evaluate(db: DB, now = new Date()): Firing[] {
  return db.alertRules
    .filter((r) => r.enabled && !inCooldown(r, now.getTime()))
    .map((rule) => ({ rule, matches: matchRule(rule, db, now) }))
    .filter((f) => f.matches.length > 0)
}

/** Notification body: names up to three matches, then "+N". */
export function describe(f: Firing, fmtValue: (m: AlertMatch) => string) {
  const shown = f.matches.slice(0, 3).map((m) => (m.value == null ? m.label : `${m.label} (${fmtValue(m)})`))
  const more = f.matches.length - shown.length
  return shown.join('; ') + (more > 0 ? ` +${more}` : '')
}
