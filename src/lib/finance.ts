/**
 * Financial (ROI) model — docs/PLATFORM.md §3.5. 25-year horizon, yearly cash flows.
 * Every client implements the same formulas so a site shows the same payback everywhere.
 */
import type { Site } from './types'
import { siteFullDayKwh } from './sim'
import { addDays, dayKey } from './utils'

export const HORIZON = 25

/** Yearly series, index 0 = installation year (CAPEX only), 1..25 = operating years. */
export interface Projection {
  systemCost: number
  energy: number[] // kWh E_y
  revenue: number[] // E_y × price_y
  opex: number[]
  cashFlow: number[] // CF_y
}

export interface FinanceResult {
  systemCost: number
  e1: number // year-1 kWh
  year1Savings: number // E1 × pricePerKwh
  paybackYears: number | null // null = not within the horizon
  roiPct: number
  npv: number
  irrPct: number | null // null = no sign change (never pays back)
  lcoe: number // per kWh
  series: { year: number; cashFlow: number; cumulative: number }[]
}

/**
 * Year-1 energy E1: simulated kWh over the 365 full days ending yesterday.
 * The install date is ignored so a site commissioned this year still gets a full-year estimate.
 */
export function yearOneKwh(site: Site, today = new Date()) {
  let sum = 0
  for (let i = 1; i <= 365; i++) sum += siteFullDayKwh(site, dayKey(addDays(today, -i)))
  return sum
}

export function projectSite(site: Site, e1: number): Projection {
  const energy = [0]
  const revenue = [0]
  const opex = [0]
  const cashFlow = [-site.systemCost]
  for (let y = 1; y <= HORIZON; y++) {
    const e = e1 * Math.pow(1 - site.degradationPct / 100, y - 1)
    const price = site.pricePerKwh * Math.pow(1 + site.tariffEscalationPct / 100, y - 1)
    energy.push(e)
    revenue.push(e * price)
    opex.push(site.annualOpex)
    cashFlow.push(e * price - site.annualOpex)
  }
  return { systemCost: site.systemCost, energy, revenue, opex, cashFlow }
}

/** Portfolio = element-wise sum of site projections. */
export function combine(list: Projection[]): Projection {
  const sum = (k: 'energy' | 'revenue' | 'opex' | 'cashFlow') => Array.from({ length: HORIZON + 1 }, (_, y) => list.reduce((s, p) => s + p[k][y], 0))
  return { systemCost: list.reduce((s, p) => s + p.systemCost, 0), energy: sum('energy'), revenue: sum('revenue'), opex: sum('opex'), cashFlow: sum('cashFlow') }
}

export const npv = (cashFlow: number[], ratePct: number) => cashFlow.reduce((s, cf, y) => s + cf / Math.pow(1 + ratePct / 100, y), 0)

/** IRR by bisection on NPV(r) = 0 over (-99 %, 1000 %]. */
export function irr(cashFlow: number[]): number | null {
  let lo = -99
  let hi = 1000
  let fLo = npv(cashFlow, lo)
  if (fLo * npv(cashFlow, hi) > 0) return null
  for (let i = 0; i < 200 && hi - lo > 1e-7; i++) {
    const mid = (lo + hi) / 2
    const f = npv(cashFlow, mid)
    if (f === 0) return mid
    if (fLo * f < 0) hi = mid
    else {
      lo = mid
      fLo = f
    }
  }
  return (lo + hi) / 2
}

/** Fractional years until cumulative cash flow first reaches ≥ 0. */
export function payback(cashFlow: number[]): number | null {
  let cum = cashFlow[0]
  if (cum >= 0) return 0
  for (let y = 1; y < cashFlow.length; y++) {
    const next = cum + cashFlow[y]
    if (next >= 0) return y - 1 + -cum / cashFlow[y]
    cum = next
  }
  return null
}

export function analyze(p: Projection, discountRatePct: number, pricePerKwh: number): FinanceResult {
  const d = (y: number) => Math.pow(1 + discountRatePct / 100, y)
  const operating = p.cashFlow.slice(1).reduce((s, v) => s + v, 0)
  let discOpex = 0
  let discEnergy = 0
  for (let y = 1; y <= HORIZON; y++) {
    discOpex += p.opex[y] / d(y)
    discEnergy += p.energy[y] / d(y)
  }
  let cum = 0
  return {
    systemCost: p.systemCost,
    e1: p.energy[1],
    year1Savings: p.energy[1] * pricePerKwh,
    paybackYears: payback(p.cashFlow),
    roiPct: p.systemCost > 0 ? ((operating - p.systemCost) / p.systemCost) * 100 : 0,
    npv: npv(p.cashFlow, discountRatePct),
    irrPct: irr(p.cashFlow),
    lcoe: discEnergy > 0 ? (p.systemCost + discOpex) / discEnergy : 0,
    series: p.cashFlow.map((cf, year) => ({ year, cashFlow: cf, cumulative: (cum += cf) })),
  }
}

/** Per-site analysis plus the portfolio, sharing one E1 computation per site. */
export function analyzePortfolio(sites: Site[], discountRatePct: number) {
  const perSite = sites.map((site) => {
    const projection = projectSite(site, yearOneKwh(site))
    return { site, projection, result: analyze(projection, discountRatePct, site.pricePerKwh) }
  })
  const total = combine(perSite.map((x) => x.projection))
  // portfolio year-1 savings = Σ E1 × price, expressed through an energy-weighted price
  const avgPrice = total.energy[1] > 0 ? perSite.reduce((s, x) => s + x.projection.energy[1] * x.site.pricePerKwh, 0) / total.energy[1] : 0
  return { perSite, portfolio: analyze(total, discountRatePct, avgPrice) }
}
