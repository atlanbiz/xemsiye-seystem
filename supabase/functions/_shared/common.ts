// Shared helpers for the SolarPulse Edge Functions (Deno runtime).
import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2.45.4'

export type Db = SupabaseClient

/** Service-role client: bypasses RLS, can read integration_secrets. Never expose it to clients. */
export function admin(): Db {
  const url = Deno.env.get('SUPABASE_URL')
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !key) throw new Error('SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are not set')
  return createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } })
}

export const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-ingest-token, x-cron-secret',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, 'content-type': 'application/json' } })

export const errorText = (e: unknown) => (e instanceof Error ? e.message : String(e)).slice(0, 500)

/** Optional shared secret for the scheduled functions: set CRON_SECRET and send it as x-cron-secret. */
export function rejectUnlessCron(req: Request): Response | null {
  const secret = Deno.env.get('CRON_SECRET')
  if (secret && req.headers.get('x-cron-secret') !== secret) return json({ error: 'forbidden' }, 403)
  return null
}

/** Records the outcome of a sync/ingest on the integration row. */
export async function markIntegration(db: Db, id: string, error: string | null) {
  const patch = error ? { status: 'error', last_error: error } : { status: 'ok', last_error: null, last_sync_at: new Date().toISOString() }
  const { error: e } = await db.from('integrations').update(patch).eq('id', id)
  if (e) console.error('integration status update failed', id, e.message)
}

/** Upserts one telemetry point (primary key site_id + ts). */
export async function writeReading(db: Db, siteId: string, ts: string, powerKw: number, energyKwh: number | null) {
  const { error } = await db.from('readings').upsert({ site_id: siteId, ts, power_kw: powerKw, energy_kwh: energyKwh })
  if (error) throw new Error(`readings: ${error.message}`)
}

export async function fetchWithTimeout(url: string, init: RequestInit = {}, ms = 15000) {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), ms)
  try {
    return await fetch(url, { ...init, signal: ctrl.signal })
  } finally {
    clearTimeout(timer)
  }
}
