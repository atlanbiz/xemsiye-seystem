// POST /functions/v1/ingest — telemetry from a gateway, logger or MQTT bridge.
// Header: x-ingest-token: <integrations.ingest_token>   (deploy with --no-verify-jwt)
// Body:   { ts?, powerKw, energyKwh?, devices?: [{ serial, status, efficiency?, health? }] }
//   ts        ISO-8601 timestamp of the measurement (default: now)
//   powerKw   current AC output of the whole site, kW
//   energyKwh energy produced so far today (local day), kWh
//   devices   status updates matched to devices of the integration's site by serial
import { admin, cors, errorText, json, markIntegration, writeReading } from '../_shared/common.ts'

const DEVICE_STATUS = ['online', 'warning', 'offline']
const MAX_DEVICES = 500

interface DeviceUpdate {
  serial: string
  status: string
  efficiency?: number
  health?: number
}
interface Payload {
  ts: string
  powerKw: number
  energyKwh: number | null
  devices: DeviceUpdate[]
}

const num = (v: unknown) => typeof v === 'number' && Number.isFinite(v)
const pct = (v: unknown) => v === undefined || (num(v) && (v as number) >= 0 && (v as number) <= 100)

function parse(body: unknown): Payload | string {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return 'body must be a JSON object'
  const b = body as Record<string, unknown>
  if (!num(b.powerKw) || (b.powerKw as number) < 0) return 'powerKw must be a non-negative number'
  if (b.energyKwh != null && (!num(b.energyKwh) || (b.energyKwh as number) < 0)) return 'energyKwh must be a non-negative number'
  let ts = new Date()
  if (b.ts != null) {
    ts = new Date(String(b.ts))
    if (Number.isNaN(ts.getTime())) return 'ts must be an ISO-8601 timestamp'
    if (ts.getTime() > Date.now() + 5 * 60_000) return 'ts is in the future'
  }
  const devices: DeviceUpdate[] = []
  if (b.devices != null) {
    if (!Array.isArray(b.devices) || b.devices.length > MAX_DEVICES) return `devices must be an array of at most ${MAX_DEVICES}`
    for (const [i, d] of b.devices.entries()) {
      const x = d as Record<string, unknown>
      if (!x || typeof x.serial !== 'string' || !x.serial.trim()) return `devices[${i}].serial is required`
      if (!DEVICE_STATUS.includes(String(x.status))) return `devices[${i}].status must be one of ${DEVICE_STATUS.join(', ')}`
      if (!pct(x.efficiency) || !pct(x.health)) return `devices[${i}].efficiency/health must be 0-100`
      devices.push({ serial: x.serial.trim(), status: String(x.status), efficiency: x.efficiency as number | undefined, health: x.health as number | undefined })
    }
  }
  return { ts: ts.toISOString(), powerKw: b.powerKw as number, energyKwh: b.energyKwh == null ? null : (b.energyKwh as number), devices }
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json({ error: 'method not allowed' }, 405)
  const token = req.headers.get('x-ingest-token')?.trim()
  if (!token) return json({ error: 'missing x-ingest-token header' }, 401)

  try {
    const db = admin()
    const { data: integration, error } = await db.from('integrations').select('id, site_id').eq('ingest_token', token).maybeSingle()
    if (error) throw new Error(error.message)
    if (!integration) return json({ error: 'unknown ingest token' }, 401)

    let body: unknown
    try {
      body = await req.json()
    } catch {
      await markIntegration(db, integration.id, 'ingest: body is not valid JSON')
      return json({ error: 'body is not valid JSON' }, 400)
    }
    const p = parse(body)
    if (typeof p === 'string') {
      await markIntegration(db, integration.id, `ingest: ${p}`)
      return json({ error: p }, 400)
    }

    await writeReading(db, integration.site_id, p.ts, p.powerKw, p.energyKwh)

    const unknownSerials: string[] = []
    for (const d of p.devices) {
      const patch: Record<string, unknown> = { status: d.status }
      if (d.status !== 'offline') patch.last_seen = p.ts
      if (d.efficiency !== undefined) patch.efficiency = d.efficiency
      if (d.health !== undefined) patch.health = d.health
      const { data, error: e } = await db.from('devices').update(patch).eq('site_id', integration.site_id).eq('serial', d.serial).select('id')
      if (e) throw new Error(`devices: ${e.message}`)
      if (!data?.length) unknownSerials.push(d.serial)
    }

    await markIntegration(db, integration.id, null)
    return json({ ok: true, siteId: integration.site_id, ts: p.ts, devicesUpdated: p.devices.length - unknownSerials.length, unknownSerials })
  } catch (e) {
    console.error(e)
    return json({ error: errorText(e) }, 500)
  }
})
