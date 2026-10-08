export const uid = (prefix = '') =>
  prefix + (crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36))

export const cn = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(' ')

export const pad = (n: number) => String(n).padStart(2, '0')

/** Local date key YYYY-MM-DD */
export const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
export const monthKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}`

export const parseDay = (s: string) => {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d || 1)
}

export const addDays = (d: Date, n: number) => {
  const r = new Date(d)
  r.setDate(r.getDate() + n)
  return r
}

export const addMonths = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth() + n, 1)

export const daysBetween = (from: Date, to: Date) => {
  const out: Date[] = []
  for (let d = new Date(from.getFullYear(), from.getMonth(), from.getDate()); d <= to; d = addDays(d, 1)) out.push(d)
  return out
}

export const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

export const pctChange = (cur: number, prev: number) => (prev === 0 ? 0 : ((cur - prev) / prev) * 100)

export function downloadFile(name: string, content: string, type = 'text/plain') {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function toCSV(rows: (string | number)[][]) {
  const esc = (v: string | number) => {
    const s = String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  // BOM so Excel opens UTF-8 (Uyghur) text correctly
  return '﻿' + rows.map((r) => r.map(esc).join(',')).join('\n')
}

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase()

/** Compact axis labels with explicit units. */
export const axisEnergy = (v: number) => (v >= 1e6 ? `${+(v / 1e6).toFixed(1)} GWh` : v >= 1000 ? `${+(v / 1000).toFixed(1)} MWh` : `${Math.round(v)} kWh`)
export const axisPower = (v: number) => (v >= 1000 ? `${+(v / 1000).toFixed(1)} MW` : `${Math.round(v)} kW`)
