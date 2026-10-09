// Generates web-reference.json from the web's TypeScript simulator (src/lib) with a frozen clock.
// Usage (from the repo root):
//   TZ=Asia/Shanghai node --experimental-strip-types android/tools/gen-web-reference.mjs src/lib android/core/src/test/resources/web-reference.json
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
const [libDir, out] = process.argv.slice(2)
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'spref-'))
for (const f of ['sim', 'utils', 'seed', 'types', 'finance', 'alerts']) {
  let src = fs.readFileSync(path.join(libDir, f + '.ts'), 'utf8')
  src = src.replace(/from '\.\/(\w+)'/g, "from './$1.ts'")
  fs.writeFileSync(path.join(tmp, f + '.ts'), src)
}
const FIXED = Date.parse('2026-06-21T15:30:00+08:00')
const Orig = Date
globalThis.Date = class extends Orig {
  constructor(...a) { if (a.length === 0) super(FIXED); else super(...a) }
  static now() { return FIXED }
}
const sim = await import(path.join(tmp, 'sim.ts'))
const seed = await import(path.join(tmp, 'seed.ts'))
const fin = await import(path.join(tmp, 'finance.ts'))
const alerts = await import(path.join(tmp, 'alerts.ts'))
const strs = ['', 'a', 'site-01', 'wx2026-06-21', 'inst0', 'ئۈرۈمچى', 'load2026-06-2155', 'site-01inverter0']
const site = { id: 'site-01', name: 'T', location: 'L', type: 'utility', status: 'active', capacityKw: 820, batteryKwh: 600, pricePerKwh: 0.08, customer: 'C', installDate: '2024-01-01', lat: 42.95, lng: 89.18, systemCost: 738000, annualOpex: 11070, degradationPct: 0.5, tariffEscalationPct: 2 }
const days = ['2026-01-01', '2026-03-20', '2026-06-20', '2026-06-21', '2026-09-23', '2026-12-31', '2025-02-28']
const db = seed.buildSeed()
const pf = fin.analyzePortfolio(db.sites, 6)
const ref = {
  now: FIXED,
  tz: process.env.TZ,
  hash: strs.map((s) => [s, sim.hash(s)]),
  rand: strs.map((s) => [s, sim.rand(s)]),
  solarCurve: [0, 6, 6.125, 9.5, 13, 16.75, 19.9, 20].map((h) => [h, sim.solarCurve(h)]),
  weatherFactor: days.map((d) => [d, sim.weatherFactor(d)]),
  seasonFactor: days.map((d) => [d, sim.seasonFactor(new Date(d + 'T00:00'))]),
  siteKw: days.flatMap((d) => [7.125, 12.5, 18].map((h) => [d, h, sim.siteKw(site, d, h)])),
  siteDayKwh: days.map((d) => [d, sim.siteDayKwh(site, d)]),
  siteDayKwhPartial: [['2026-06-20', 15.5, sim.siteDayKwh(site, '2026-06-20', 15.5)]],
  consumptionKw: [3, 9, 12.25, 19.5].map((h) => [h, sim.consumptionKw(db.sites, h, '2026-06-21')]),
  liveKw: sim.liveKw(db.sites, FIXED),
  energyFlow: sim.energyFlow(db.sites, FIXED),
  simWeather: sim.simWeather(new Date()),
  deviceStats: sim.deviceStats(db.devices),
  seed: db,
  finance: {
    site: { input: site, e1: fin.yearOneKwh(site), result: fin.analyze(fin.projectSite(site, fin.yearOneKwh(site)), 6, site.pricePerKwh) },
    portfolio: pf.portfolio,
    perSite: pf.perSite.map((x) => ({ id: x.site.id, result: x.result })),
    irrSimple: fin.irr([-1000, 300, 300, 300, 300, 300]),
    paybackSimple: fin.payback([-1000, 300, 300, 300, 300, 300]),
  },
  alerts: alerts.evaluate(db).map((f) => ({ ruleId: f.rule.id, matches: f.matches })),
}
fs.writeFileSync(out, JSON.stringify(ref, null, 1))
console.log('ok', out, fs.statSync(out).size)
