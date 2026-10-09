import { useCallback, useEffect, useRef } from 'react'
import { useData } from './data'
import { useI18n, type TFn } from './i18n'
import { describe, evaluate, METRIC_UNIT, type AlertMatch } from '../lib/alerts'
import { isSupabase } from '../lib/supabase'
import type { AlertMetric } from '../lib/types'

const INTERVAL_MS = 3 * 60_000

// Shared by every runner instance: one evaluation at a time, and firings are remembered
// until the new lastTriggeredAt has reached React state (avoids double notifications).
let busy = false
const fired = new Map<string, string>()

/** Localized unit for a metric's threshold / measured value. */
export function metricUnit(metric: AlertMetric, t: TFn) {
  const u = METRIC_UNIT[metric]
  return u === 'min' ? t('alerts.unit.min') : u === 'd' ? t('alerts.unit.days') : u
}

/** Evaluates all rules once and inserts a notification per firing rule. Returns how many fired. */
export function useAlertRunner() {
  const { db, upsert, notify } = useData()
  const { t, fmt } = useI18n()
  const dbRef = useRef(db)
  dbRef.current = db

  return useCallback(async () => {
    if (busy) return 0
    busy = true
    try {
      const now = new Date()
      const cur = dbRef.current
      const alertRules = cur.alertRules.map((r) => {
        const at = fired.get(r.id)
        return at && (!r.lastTriggeredAt || r.lastTriggeredAt < at) ? { ...r, lastTriggeredAt: at } : r
      })
      const firings = evaluate({ ...cur, alertRules }, now)
      for (const f of firings) {
        fired.set(f.rule.id, now.toISOString())
        const unit = metricUnit(f.rule.metric, t)
        // LRI…PDI keeps "70%" / "12 min" in order inside Uyghur/Arabic text
        const value = (m: AlertMatch) => `\u2066${fmt.num(m.value ?? 0, f.rule.metric === 'site_yield_below' ? 2 : 0)}${unit === '%' ? '' : ' '}${unit ?? ''}\u2069`
        await upsert('alertRules', { ...f.rule, lastTriggeredAt: now.toISOString() })
        await notify({ title: f.rule.name, body: describe(f, value), kind: f.rule.severity, link: f.matches[0].link })
      }
      return firings.length
    } finally {
      busy = false
    }
  }, [upsert, notify, t, fmt])
}

/** Demo mode: evaluate on load and every few minutes. With Supabase the Edge Function does it. */
export function useAlertEngine() {
  const run = useAlertRunner()
  const runRef = useRef(run)
  runRef.current = run
  useEffect(() => {
    if (isSupabase) return
    const first = setTimeout(() => runRef.current(), 1500)
    const timer = setInterval(() => runRef.current(), INTERVAL_MS)
    return () => {
      clearTimeout(first)
      clearInterval(timer)
    }
  }, [])
}
