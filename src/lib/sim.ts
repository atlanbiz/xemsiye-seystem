/**
 * Deterministic solar production simulator.
 * Every number in the UI is derived from site capacity + date + hour, so charts,
 * KPIs, reports and invoices all agree. When real telemetry exists (Supabase
 * `readings` table) it can replace these functions.
 */
import type { Device, Site } from './types'
import { addDays, dayKey, daysBetween, parseDay } from './utils'

export function hash(str: string) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

export function rand(seed: string) {
  let t = hash(seed) + 0x6d2b79f5
  t = Math.imul(t ^ (t >>> 15), t | 1)
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}

/** Normalised irradiance curve 0..1 for a fractional hour. */
export function solarCurve(h: number) {
  if (h <= 6 || h >= 20) return 0
  const x = Math.sin((Math.PI * (h - 6)) / 14)
  return Math.pow(Math.max(0, x), 1.6)
}

/** Seasonal factor (northern hemisphere). */
export function seasonFactor(d: Date) {
  const doy = Math.floor((d.getTime() - new Date(d.getFullYear(), 0, 0).getTime()) / 86400000)
  return 0.68 + 0.32 * Math.sin((2 * Math.PI * (doy - 80)) / 365)
}

/** Cloudiness factor for a day (shared by all sites — same weather region). */
export function weatherFactor(day: string) {
  const r = rand('wx' + day)
  return r < 0.12 ? 0.35 + r * 2 : 0.78 + r * 0.22
}

const PERF = 0.82 // system performance ratio

function statusFactor(site: Site, day: string) {
  if (day !== dayKey(new Date())) return 1
  switch (site.status) {
    case 'offline':
      return 0
    case 'maintenance':
      return 0
    case 'idle':
      return 0.15
    default:
      return 1
  }
}

/** Instantaneous kW output for a site at a given day + fractional hour. */
export function siteKw(site: Site, day: string, hour: number) {
  if (day < site.installDate) return 0
  const d = parseDay(day)
  const siteVar = 0.92 + rand(site.id + day) * 0.1
  return site.capacityKw * PERF * solarCurve(hour) * seasonFactor(d) * weatherFactor(day) * siteVar * statusFactor(site, day)
}

/** kWh produced by a site on a day. If `untilHour` given, only up to that hour (for today). */
export function siteDayKwh(site: Site, day: string, untilHour = 24) {
  let sum = 0
  const step = 0.25
  for (let h = 0; h < untilHour; h += step) sum += siteKw(site, day, h + step / 2) * Math.min(step, untilHour - h)
  return sum
}

const cache = new Map<string, number>()
/** Cached full-day production (past days only). */
export function siteDayKwhCached(site: Site, day: string) {
  const today = dayKey(new Date())
  if (day >= today) return siteDayKwh(site, day, day === today ? nowHour() : 24)
  const k = `${site.id}|${site.capacityKw}|${site.installDate}|${day}`
  let v = cache.get(k)
  if (v === undefined) {
    v = siteDayKwh(site, day)
    cache.set(k, v)
  }
  return v
}

export const nowHour = () => {
  const n = new Date()
  return n.getHours() + n.getMinutes() / 60 + n.getSeconds() / 3600
}

/** Small live jitter so the "current power" visibly breathes. */
export function liveKw(sites: Site[], t = Date.now()) {
  const day = dayKey(new Date(t))
  const h = nowHour()
  const jitter = 1 + Math.sin(t / 7000) * 0.015 + (rand(String(Math.floor(t / 5000))) - 0.5) * 0.02
  return sites.reduce((s, x) => s + siteKw(x, day, h), 0) * jitter
}

export function totalKwh(sites: Site[], from: Date, to: Date) {
  let s = 0
  for (const d of daysBetween(from, to)) {
    const k = dayKey(d)
    for (const site of sites) s += siteDayKwhCached(site, k)
  }
  return s
}

export function dailySeries(sites: Site[], from: Date, to: Date) {
  return daysBetween(from, to).map((d) => {
    const k = dayKey(d)
    return { key: k, date: d, kwh: sites.reduce((s, x) => s + siteDayKwhCached(x, k), 0) }
  })
}

export function hourlySeries(sites: Site[], day: string, stepMin = 30) {
  const isToday = day === dayKey(new Date())
  const limit = isToday ? nowHour() : 24
  const out: { hour: number; label: string; kw: number | null }[] = []
  for (let m = 0; m <= 24 * 60; m += stepMin) {
    const h = m / 60
    const label = `${String(Math.floor(h) % 24).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
    out.push({ hour: h, label, kw: h <= limit ? sites.reduce((s, x) => s + siteKw(x, day, h), 0) : null })
  }
  return out
}

/** Building load model, kW. */
export function consumptionKw(sites: Site[], h: number, day: string) {
  const cap = sites.reduce((s, x) => s + x.capacityKw, 0)
  const base = 0.09 + 0.07 * Math.exp(-Math.pow(h - 9, 2) / 6) + 0.12 * Math.exp(-Math.pow(h - 19.5, 2) / 5) + 0.08 * solarCurve(h)
  return cap * base * (0.95 + rand('load' + day + Math.floor(h * 4)) * 0.1)
}

export interface Flow {
  solar: number
  consumption: number
  battery: number // + charging, - discharging
  grid: number // + import, - export
  batterySoc: number
  batteryCapacity: number
}

export function energyFlow(sites: Site[], t = Date.now()): Flow {
  const day = dayKey(new Date(t))
  const h = nowHour()
  const solar = liveKw(sites, t)
  const consumption = consumptionKw(sites, h, day)
  const batteryCapacity = sites.reduce((s, x) => s + x.batteryKwh, 0)
  // SOC follows a simple daily shape: charges through the day, discharges at night
  const soc = Math.max(0.15, Math.min(0.98, 0.35 + 0.6 * Math.sin((Math.PI * Math.max(0, Math.min(h, 22) - 8)) / 18)))
  const maxRate = batteryCapacity * 0.25
  let battery = 0
  let grid = 0
  const net = solar - consumption
  if (net >= 0) {
    battery = soc < 0.97 ? Math.min(net, maxRate) : 0
    grid = -(net - battery)
  } else {
    const need = -net
    const discharge = soc > 0.2 ? Math.min(need, maxRate) : 0
    battery = -discharge
    grid = need - discharge
  }
  return { solar, consumption, battery, grid, batterySoc: soc * 100, batteryCapacity }
}

export const co2Tons = (kwh: number, factor: number) => (kwh * factor) / 1000

export function deviceStats(devices: Device[]) {
  const total = devices.length || 1
  const online = devices.filter((d) => d.status === 'online').length
  const warning = devices.filter((d) => d.status === 'warning').length
  const inv = devices.filter((d) => d.type === 'inverter')
  const bat = devices.filter((d) => d.type === 'battery')
  const avg = (a: Device[], f: (d: Device) => number) => (a.length ? a.reduce((s, d) => s + f(d), 0) / a.length : 0)
  return {
    availability: ((online + warning * 0.5) / total) * 100,
    inverterEfficiency: avg(inv, (d) => (d.status === 'offline' ? 0 : d.efficiency)),
    batteryHealth: avg(bat, (d) => d.health),
    online,
    warning,
    offline: devices.length - online - warning,
  }
}

/** Range helpers relative to today */
export function rangeOf(kind: '7d' | '30d' | '90d' | '12m') {
  const to = new Date()
  const days = { '7d': 6, '30d': 29, '90d': 89, '12m': 364 }[kind]
  return { from: addDays(to, -days), to }
}

/** Simulated weather for when the network weather API is unavailable. */
export function simWeather(t = new Date()) {
  const day = dayKey(t)
  const wf = weatherFactor(day)
  const h = t.getHours() + t.getMinutes() / 60
  const seasonal = seasonFactor(t)
  const temp = Math.round(-5 + seasonal * 32 + 6 * Math.sin((Math.PI * (h - 8)) / 12) + (rand('t' + day) - 0.5) * 4)
  return {
    temp,
    code: wf < 0.6 ? 3 : wf < 0.85 ? 2 : 0,
    irradiance: Math.round(1000 * solarCurve(h) * seasonal * wf),
    wind: Math.round(4 + rand('w' + day) * 18),
    humidity: Math.round(25 + rand('h' + day) * 40),
  }
}
