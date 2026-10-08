// POST /functions/v1/evaluate-alerts — server-side alert rules (docs/PLATFORM.md §3.3), on a pg_cron schedule.
// Same semantics as src/lib/alerts.ts, with one difference: the simulator is not available here,
// so site_yield_below uses today's readings.energy_kwh (max of the local day) and skips sites without readings.
// Local time ("after 14:00", "today") uses the IANA zone in ALERTS_TIMEZONE (default UTC).
import { admin, cors, errorText, json, rejectUnlessCron, type Db } from '../_shared/common.ts'

const COOLDOWN_MS = 6 * 3600_000
const YIELD_CHECK_HOUR = 14

interface Rule {
  id: string
  name: string
  metric: string
  threshold: number
  site_id: string | null
  severity: 'warning' | 'danger'
  enabled: boolean
  last_triggered_at: string | null
}
interface Match {
  label: string
  value: string | null
  link: string
}

function localParts(d: Date, timeZone: string) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23' })
      .formatToParts(d)
      .map((p) => [p.type, p.value]),
  )
  return { day: `${parts.year}-${parts.month}-${parts.day}`, hour: Number(parts.hour) }
}

const dayNumber = (ymd: string) => Date.UTC(+ymd.slice(0, 4), +ymd.slice(5, 7) - 1, +ymd.slice(8, 10)) / 86400_000

async function todayEnergy(db: Db, siteIds: string[], tz: string, today: string) {
  const out = new Map<string, number>()
  if (!siteIds.length) return out
  const { data, error } = await db
    .from('readings')
    .select('site_id, ts, energy_kwh')
    .in('site_id', siteIds)
    .gte('ts', new Date(Date.now() - 36 * 3600_000).toISOString())
    .not('energy_kwh', 'is', null)
  if (error) throw new Error(`readings: ${error.message}`)
  for (const r of data ?? []) {
    if (localParts(new Date(r.ts), tz).day !== today) continue
    out.set(r.site_id, Math.max(out.get(r.site_id) ?? 0, Number(r.energy_kwh)))
  }
  return out
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  const denied = rejectUnlessCron(req)
  if (denied) return denied
  try {
    const db = admin()
    const tz = Deno.env.get('ALERTS_TIMEZONE') || 'UTC'
    const now = new Date()
    const { day: today, hour } = localParts(now, tz)

    const { data: ruleRows, error: re } = await db.from('alert_rules').select('*').eq('enabled', true)
    if (re) throw new Error(re.message)
    const rules = ((ruleRows ?? []) as Rule[]).filter((r) => !r.last_triggered_at || now.getTime() - new Date(r.last_triggered_at).getTime() >= COOLDOWN_MS)
    if (!rules.length) return json({ evaluated: 0, fired: [] })

    const [sites, devices, invoices, settings] = await Promise.all([
      db.from('sites').select('id, name, status, capacity_kw'),
      db.from('devices').select('id, site_id, name, status, health, efficiency, last_seen'),
      db.from('invoices').select('id, site_id, number, customer, status, due_at').eq('status', 'overdue'),
      db.from('settings').select('notify_push').eq('id', 'settings').maybeSingle(),
    ])
    for (const r of [sites, devices, invoices, settings]) if (r.error) throw new Error(r.error.message)
    const siteName = (id: string) => sites.data!.find((s) => s.id === id)?.name ?? id
    const notifyPush = settings.data?.notify_push !== false
    const needsYield = rules.some((r) => r.metric === 'site_yield_below') && hour >= YIELD_CHECK_HOUR
    const energy = needsYield ? await todayEnergy(db, sites.data!.map((s) => s.id), tz, today) : new Map<string, number>()

    const fired: { rule: string; matches: number }[] = []
    for (const rule of rules) {
      const inScope = (siteId: string) => rule.site_id == null || rule.site_id === siteId
      const th = Number(rule.threshold)
      let matches: Match[] = []
      switch (rule.metric) {
        case 'site_yield_below':
          if (hour < YIELD_CHECK_HOUR) break
          matches = sites.data!
            .filter((s) => inScope(s.id) && Number(s.capacity_kw) > 0 && energy.has(s.id))
            .map((s) => ({ s, v: energy.get(s.id)! / Number(s.capacity_kw) }))
            .filter(({ v }) => v < th)
            .map(({ s, v }) => ({ label: s.name, value: `${v.toFixed(2)} kWh/kWp`, link: `/sites/${s.id}` }))
          break
        case 'site_offline':
          matches = sites.data!.filter((s) => inScope(s.id) && s.status === 'offline').map((s) => ({ label: s.name, value: null, link: `/sites/${s.id}` }))
          break
        case 'device_efficiency_below':
        case 'device_health_below': {
          const key = rule.metric === 'device_efficiency_below' ? 'efficiency' : 'health'
          matches = devices.data!
            .filter((d) => inScope(d.site_id) && Number(d[key]) < th)
            .map((d) => ({ label: `${d.name} · ${siteName(d.site_id)}`, value: `${Math.round(Number(d[key]))}%`, link: `/devices?q=${encodeURIComponent(d.name)}` }))
          break
        }
        case 'device_offline_minutes':
          matches = devices.data!
            .filter((d) => inScope(d.site_id) && d.status === 'offline')
            .map((d) => ({ d, v: (now.getTime() - new Date(d.last_seen).getTime()) / 60000 }))
            .filter(({ v }) => v > th)
            .map(({ d, v }) => ({ label: `${d.name} · ${siteName(d.site_id)}`, value: `${Math.round(v)} min`, link: `/devices?q=${encodeURIComponent(d.name)}` }))
          break
        case 'invoice_overdue_days':
          matches = invoices.data!
            .filter((i) => inScope(i.site_id))
            .map((i) => ({ i, v: dayNumber(today) - dayNumber(String(i.due_at)) }))
            .filter(({ v }) => v > th)
            .map(({ i, v }) => ({ label: `${i.number} · ${i.customer}`, value: `${v} days`, link: `/billing?open=${i.id}` }))
          break
      }
      if (!matches.length) continue

      const shown = matches.slice(0, 3).map((m) => (m.value ? `${m.label} (${m.value})` : m.label))
      const body = shown.join('; ') + (matches.length > shown.length ? ` +${matches.length - shown.length}` : '')
      if (notifyPush) {
        const { error } = await db.from('notifications').insert({ id: `ntf-${crypto.randomUUID()}`, title: rule.name, body, kind: rule.severity, link: matches[0].link, read: false, created_at: now.toISOString() })
        if (error) throw new Error(`notifications: ${error.message}`)
      }
      const { error } = await db.from('alert_rules').update({ last_triggered_at: now.toISOString() }).eq('id', rule.id)
      if (error) throw new Error(`alert_rules: ${error.message}`)
      fired.push({ rule: rule.id, matches: matches.length })
    }
    return json({ evaluated: rules.length, fired })
  } catch (e) {
    console.error(e)
    return json({ error: errorText(e) }, 500)
  }
})
