// POST /functions/v1/sync-vendors — pulls current power / today's energy from vendor clouds
// for every SolarEdge and FusionSolar integration and writes a `readings` row per site.
// Meant to run on a pg_cron schedule (see README). Credentials come from integration_secrets
// (service role only): SolarEdge { api_key }, FusionSolar { system_code }.
//
// NOTE: written against the vendors' published APIs but not tested against live accounts.
//   SolarEdge Monitoring API:   GET  https://monitoringapi.solaredge.com/site/{siteId}/overview?api_key=…
//   FusionSolar Northbound API: POST {base}/thirdData/login → xsrf-token,
//                               POST {base}/thirdData/getStationRealKpi (day_power, kWh)
//                               POST {base}/thirdData/getDevList + getDevRealKpi (inverter active_power, kW)
import { admin, cors, errorText, fetchWithTimeout, json, markIntegration, rejectUnlessCron, writeReading, type Db } from '../_shared/common.ts'

interface IntegrationRow {
  id: string
  vendor: 'solaredge' | 'fusionsolar'
  site_id: string
  external_id: string
  config: { base_url?: string; username?: string } | null
}
interface Point {
  powerKw: number
  energyKwh: number | null
}
type Result = { id: string; ok: boolean; error?: string }

const FS_DEFAULT_BASE = 'https://eu5.fusionsolar.huawei.com'
const INVERTER_DEV_TYPE = 1
/** Readings are stamped on the minute so retries within a minute upsert instead of duplicating. */
const stamp = () => new Date(Math.floor(Date.now() / 60_000) * 60_000).toISOString()

// ── SolarEdge ───────────────────────────────────────────────────────────────
async function solarEdge(siteId: string, apiKey: string): Promise<Point> {
  const url = `https://monitoringapi.solaredge.com/site/${encodeURIComponent(siteId)}/overview?api_key=${encodeURIComponent(apiKey)}`
  const res = await fetchWithTimeout(url, { headers: { accept: 'application/json' } })
  if (!res.ok) throw new Error(`SolarEdge HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`)
  const overview = (await res.json())?.overview
  if (!overview) throw new Error('SolarEdge: response has no overview')
  const powerW = Number(overview.currentPower?.power ?? 0) // W
  const dayWh = overview.lastDayData?.energy // Wh produced today
  return { powerKw: Number.isFinite(powerW) ? powerW / 1000 : 0, energyKwh: dayWh == null ? null : Number(dayWh) / 1000 }
}

// ── FusionSolar ─────────────────────────────────────────────────────────────
async function fsLogin(base: string, userName: string, systemCode: string) {
  const res = await fetchWithTimeout(`${base}/thirdData/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userName, systemCode }),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) throw new Error(`FusionSolar login failed (${body?.failCode ?? `HTTP ${res.status}`}${body?.message ? `: ${body.message}` : ''})`)
  const token = res.headers.get('xsrf-token') ?? /XSRF-TOKEN=([^;]+)/i.exec(res.headers.get('set-cookie') ?? '')?.[1]
  if (!token) throw new Error('FusionSolar login: no xsrf-token in response')
  return token
}

async function fsPost<T>(base: string, token: string, path: string, payload: unknown): Promise<T> {
  const res = await fetchWithTimeout(`${base}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'XSRF-TOKEN': token, cookie: `XSRF-TOKEN=${token}` },
    body: JSON.stringify(payload),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) throw new Error(`FusionSolar ${path} failed (${body?.failCode ?? `HTTP ${res.status}`}${body?.message ? `: ${body.message}` : ''})`)
  return body.data as T
}

/** Sum of inverter active_power (kW) per station; empty when the device endpoints are unavailable. */
async function fsStationPower(base: string, token: string, stationCodes: string[]) {
  const power = new Map<string, number>()
  try {
    const devs = await fsPost<{ id: number; stationCode: string; devTypeId: number }[]>(base, token, '/thirdData/getDevList', { stationCodes: stationCodes.join(',') })
    const inverters = (devs ?? []).filter((d) => Number(d.devTypeId) === INVERTER_DEV_TYPE)
    if (!inverters.length) return power
    const station = new Map(inverters.map((d) => [String(d.id), d.stationCode]))
    const kpis = await fsPost<{ devId: number; dataItemMap: { active_power?: number } }[]>(base, token, '/thirdData/getDevRealKpi', { devIds: inverters.map((d) => d.id).join(','), devTypeId: INVERTER_DEV_TYPE })
    for (const k of kpis ?? []) {
      const code = station.get(String(k.devId))
      const kw = Number(k.dataItemMap?.active_power)
      if (code && Number.isFinite(kw)) power.set(code, (power.get(code) ?? 0) + kw)
    }
  } catch (e) {
    console.warn('FusionSolar real-time power unavailable:', errorText(e))
  }
  return power
}

/** Fallback when no real-time power: average kW since the previous reading of the same day. */
async function powerFromEnergyDelta(db: Db, siteId: string, energyKwh: number): Promise<number | null> {
  if (energyKwh === 0) return 0
  const { data } = await db.from('readings').select('ts, energy_kwh').eq('site_id', siteId).order('ts', { ascending: false }).limit(1).maybeSingle()
  if (!data || data.energy_kwh == null) return null
  const hours = (Date.now() - new Date(data.ts).getTime()) / 3600_000
  const delta = energyKwh - Number(data.energy_kwh)
  return hours > 0 && hours <= 2 && delta >= 0 ? delta / hours : null
}

async function syncFusionSolar(db: Db, list: IntegrationRow[], secrets: Map<string, Record<string, string>>): Promise<Result[]> {
  // one login per account (base URL + user + system code); FusionSolar rate-limits logins
  const groups = new Map<string, IntegrationRow[]>()
  const results: Result[] = []
  for (const i of list) {
    const code = secrets.get(i.id)?.system_code
    if (!i.config?.username || !code || !i.external_id) {
      const error = !code ? 'system code not set' : !i.config?.username ? 'username not set' : 'station code not set'
      await markIntegration(db, i.id, error)
      results.push({ id: i.id, ok: false, error })
      continue
    }
    const base = (i.config.base_url || FS_DEFAULT_BASE).replace(/\/$/, '')
    const key = JSON.stringify([base, i.config.username, code])
    groups.set(key, [...(groups.get(key) ?? []), i])
  }

  for (const [key, items] of groups) {
    const [base, user, code] = JSON.parse(key) as string[]
    try {
      const token = await fsLogin(base, user, code)
      const codes = [...new Set(items.map((i) => i.external_id))]
      const kpis = await fsPost<{ stationCode: string; dataItemMap: { day_power?: number } }[]>(base, token, '/thirdData/getStationRealKpi', { stationCodes: codes.join(',') })
      const dayKwh = new Map((kpis ?? []).map((k) => [k.stationCode, Number(k.dataItemMap?.day_power)]))
      const power = await fsStationPower(base, token, codes)
      for (const i of items) {
        try {
          const energy = dayKwh.get(i.external_id)
          if (energy === undefined || !Number.isFinite(energy)) throw new Error(`station ${i.external_id} not found in getStationRealKpi`)
          const kw = power.get(i.external_id) ?? (await powerFromEnergyDelta(db, i.site_id, energy))
          if (kw == null) throw new Error('real-time power unavailable (getDevRealKpi failed and no earlier reading today)')
          await writeReading(db, i.site_id, stamp(), kw, energy)
          await markIntegration(db, i.id, null)
          results.push({ id: i.id, ok: true })
        } catch (e) {
          await markIntegration(db, i.id, errorText(e))
          results.push({ id: i.id, ok: false, error: errorText(e) })
        }
      }
    } catch (e) {
      for (const i of items) {
        await markIntegration(db, i.id, errorText(e))
        results.push({ id: i.id, ok: false, error: errorText(e) })
      }
    }
  }
  return results
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  const denied = rejectUnlessCron(req)
  if (denied) return denied
  try {
    const db = admin()
    const { data, error } = await db.from('integrations').select('id, vendor, site_id, external_id, config').in('vendor', ['solaredge', 'fusionsolar'])
    if (error) throw new Error(error.message)
    const list = (data ?? []) as IntegrationRow[]
    if (!list.length) return json({ synced: 0, results: [] })

    const { data: rows, error: se } = await db.from('integration_secrets').select('integration_id, secret').in('integration_id', list.map((i) => i.id))
    if (se) throw new Error(se.message)
    const secrets = new Map((rows ?? []).map((r) => [r.integration_id as string, (r.secret ?? {}) as Record<string, string>]))

    const results: Result[] = []
    for (const i of list.filter((x) => x.vendor === 'solaredge')) {
      try {
        const key = secrets.get(i.id)?.api_key
        if (!key) throw new Error('API key not set')
        if (!i.external_id) throw new Error('SolarEdge site id not set')
        const p = await solarEdge(i.external_id, key)
        await writeReading(db, i.site_id, stamp(), p.powerKw, p.energyKwh)
        await markIntegration(db, i.id, null)
        results.push({ id: i.id, ok: true })
      } catch (e) {
        await markIntegration(db, i.id, errorText(e))
        results.push({ id: i.id, ok: false, error: errorText(e) })
      }
    }
    results.push(...(await syncFusionSolar(db, list.filter((x) => x.vendor === 'fusionsolar'), secrets)))
    return json({ synced: results.filter((r) => r.ok).length, results })
  } catch (e) {
    console.error(e)
    return json({ error: errorText(e) }, 500)
  }
})
