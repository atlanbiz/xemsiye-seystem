import type { AppNotification, DB, Device, DeviceType, Invoice, Settings, Site, Ticket } from './types'
import { rand, siteDayKwhCached } from './sim'
import { addDays, addMonths, dayKey, daysBetween, monthKey, pad } from './utils'

export const defaultSettings: Settings = {
  id: 'settings',
  userName: 'ئالىم كېرىم',
  email: 'admin@solarpulse.app',
  role: 'admin',
  company: 'SolarPulse Energy',
  language: 'ug',
  theme: 'light',
  currency: 'USD',
  city: 'ئۈرۈمچى',
  lat: 43.825,
  lng: 87.617,
  co2KgPerKwh: 0.7,
  treeKgPerYear: 21.8,
  carTonsPerYear: 4.6,
  notifyEmail: true,
  notifyPush: true,
  notifyDeviceAlerts: true,
  notifyBilling: true,
  notifyMaintenance: true,
}

const SITE_DEFS: [string, string, Site['type'], number, number, number, number][] = [
  // name, location, type, capacity kW, battery kWh, lat, lng
  ['تۇرپان قۇياش مەيدانى', 'تۇرپان', 'utility', 820, 600, 42.95, 89.18],
  ['ئۈرۈمچى سودا مەركىزى', 'ئۈرۈمچى', 'commercial', 183, 120, 43.82, 87.61],
  ['قەشقەر كونا شەھەر ئۆيلىرى', 'قەشقەر', 'residential', 12.4, 15, 39.47, 75.99],
  ['خوتەن ئاشلىق زاۋۇتى', 'خوتەن', 'industrial', 410, 300, 37.11, 79.92],
  ['غۇلجا مەكتىپى', 'غۇلجا', 'commercial', 96, 60, 43.92, 81.32],
  ['ئاقسۇ يېزا ئىگىلىك مەيدانى', 'ئاقسۇ', 'industrial', 265, 180, 41.17, 80.26],
  ['قۇمۇل شامال-قۇياش بازىسى', 'قۇمۇل', 'utility', 640, 450, 42.82, 93.51],
  ['كورلا ئىشخانا بىناسى', 'كورلا', 'commercial', 65, 40, 41.76, 86.15],
  ['ئاتۇش ئائىلە ئولتۇراق رايونى', 'ئاتۇش', 'residential', 38, 30, 39.71, 76.17],
  ['چۆچەك دوختۇرخانىسى', 'چۆچەك', 'commercial', 120, 150, 46.75, 82.98],
  ['ئالتاي تاغ ئارامگاھى', 'ئالتاي', 'residential', 18, 20, 47.84, 88.14],
  ['يەكەن توقۇمىچىلىق زاۋۇتى', 'يەكەن', 'industrial', 350, 220, 38.42, 77.24],
  ['قاراماي ئامبار مەركىزى', 'قاراماي', 'industrial', 290, 160, 45.58, 84.89],
  ['بۆرتالا مېھمانخانىسى', 'بۆرتالا', 'commercial', 74, 50, 44.9, 82.07],
  ['كۇچا ئۈزۈمزارلىقى', 'كۇچا', 'residential', 24, 20, 41.72, 82.96],
  ['شىخەنزە لوگىستىكا پاركى', 'شىخەنزە', 'commercial', 210, 140, 44.3, 86.04],
]

const STATUSES: Site['status'][] = ['active', 'active', 'active', 'active', 'idle', 'active', 'active', 'active', 'idle', 'active', 'offline', 'active', 'idle', 'active', 'maintenance', 'active']
const CUSTOMERS = ['تۇرپان ئېنېرگىيە شىركىتى', 'تەڭرىتاغ سودا گۇرۇھى', 'ئابدۇللا ئائىلىسى', 'خوتەن ئاشلىق شىركىتى', 'غۇلجا مائارىپ ئىدارىسى', 'ئاقسۇ دېھقانچىلىق كوپراتىپى', 'قۇمۇل توك تورى', 'كورلا نېفىت شىركىتى', 'ئاتۇش مۈلۈك باشقۇرۇش', 'چۆچەك ساغلاملىق مەركىزى', 'نۇرگۈل خانىم', 'يەكەن توقۇمىچىلىق', 'قاراماي ئامبار شىركىتى', 'بۆرتالا ساياھەت', 'كۇچا ئۈزۈمچىلىك', 'شىخەنزە لوگىستىكا']
const TECHS = ['ئەركىن تۇرسۇن', 'مۇختەر ئەھمەت', 'دىلنۇر ئابلىز', 'پەرھات قادىر', 'گۈلنار ھەسەن']

function buildSites(): Site[] {
  return SITE_DEFS.map(([name, location, type, cap, bat, lat, lng], i) => ({
    id: `site-${pad(i + 1)}`,
    name,
    location,
    type,
    status: STATUSES[i],
    capacityKw: cap,
    batteryKwh: bat,
    pricePerKwh: type === 'residential' ? 0.14 : type === 'utility' ? 0.08 : type === 'industrial' ? 0.1 : 0.12,
    customer: CUSTOMERS[i],
    installDate: dayKey(addDays(new Date(), -(420 + Math.floor(rand('inst' + i) * 900)))),
    lat,
    lng,
  }))
}

const MODELS: Record<DeviceType, string[]> = {
  inverter: ['Huawei SUN2000-100KTL', 'SMA Sunny Tripower 25', 'Sungrow SG110CX', 'Fronius Symo 20'],
  battery: ['BYD Battery-Box HVM', 'Tesla Powerwall 2', 'CATL EnerOne', 'LG RESU 16H'],
  panel: ['LONGi Hi-MO 6 (Array)', 'JinkoSolar Tiger Neo (Array)', 'Trina Vertex S+ (Array)'],
  meter: ['Schneider PM5560', 'Eastron SDM630'],
  sensor: ['Kipp & Zonen SMP10', 'Davis Vantage Pro2'],
}

function buildDevices(sites: Site[]): Device[] {
  const out: Device[] = []
  let n = 1
  for (const s of sites) {
    const inverters = s.capacityKw > 300 ? 3 : s.capacityKw > 80 ? 2 : 1
    const types: DeviceType[] = [...Array(inverters).fill('inverter'), 'panel', 'meter']
    if (s.batteryKwh > 0) types.push('battery')
    if (s.capacityKw > 100) types.push('sensor')
    types.forEach((type, idx) => {
      const r = rand(s.id + type + idx)
      let status: Device['status'] = r < 0.08 ? 'warning' : 'online'
      if (s.status === 'offline') status = 'offline'
      if (s.status === 'maintenance' && type === 'inverter') status = 'warning'
      const models = MODELS[type]
      const label = { inverter: 'INV', battery: 'BAT', panel: 'PV', meter: 'MTR', sensor: 'SNS' }[type]
      out.push({
        id: `dev-${String(n).padStart(3, '0')}`,
        siteId: s.id,
        name: `${label}-${s.id.slice(-2)}-${idx + 1}`,
        type,
        model: models[Math.floor(r * models.length)],
        serial: `SN${(hash6(s.id + idx + type))}`,
        status,
        health: Math.round(status === 'warning' ? 70 + r * 12 : 88 + r * 12),
        efficiency: Math.round((type === 'inverter' ? 96.2 + r * 2.4 : 90 + r * 8) * 10) / 10,
        firmware: `v${2 + Math.floor(r * 3)}.${Math.floor(r * 9)}.${Math.floor(r * 20)}`,
        installedAt: s.installDate,
        lastSeen: new Date(Date.now() - (status === 'offline' ? 3600_000 * (6 + r * 40) : r * 120_000)).toISOString(),
      })
      n++
    })
  }
  return out
}

function hash6(s: string) {
  return Math.floor(rand(s) * 0xffffff).toString(16).toUpperCase().padStart(6, '0') + Math.floor(rand(s + 'x') * 9999)
}

function buildTickets(sites: Site[], devices: Device[]): Ticket[] {
  const defs: [number, string, string, Ticket['priority'], Ticket['status'], number][] = [
    [10, 'ئالتاي ئىستانسىسى تورسىز', 'ئالاقە ئۈسكۈنىسى جاۋاب قايتۇرمايۋاتىدۇ، نەق مەيداندا تەكشۈرۈش كېرەك.', 'critical', 'open', 1],
    [14, 'كۇچا ئىنۋېرتورىنى ئالماشتۇرۇش', 'ئىنۋېرتور قىزىپ كېتىش خاتالىقى بەردى، يېڭىسىغا ئالماشتۇرۇلىدۇ.', 'high', 'in_progress', 2],
    [0, 'تاختايلارنى تازىلاش', 'قۇم-توپا سەۋەبىدىن ئۈنۈم 6% تۆۋەنلىدى.', 'medium', 'open', 4],
    [3, 'باتارېيە BMS يۇمشاق دېتالىنى يېڭىلاش', 'BMS v3.2 گە يېڭىلاش.', 'low', 'open', 9],
    [6, 'پەسىللىك تەكشۈرۈش', 'ئېلېكتر ئۇلىنىشى، يەرلەشتۈرۈش ۋە كابېللارنى تەكشۈرۈش.', 'medium', 'in_progress', 3],
    [11, 'توك ئۆلچىگۈچ سانلىق مەلۇماتى كەم', 'سائەت 14:00-16:00 ئارىلىقىدا ئۆلچەش كەم.', 'medium', 'open', 6],
    [1, 'ئوت ئۆچۈرگۈچ ۋە بىخەتەرلىك تەكشۈرۈشى', 'يىللىق بىخەتەرلىك تەكشۈرۈشى.', 'low', 'resolved', -5],
    [12, 'ئىنۋېرتور ئالاقە ئۈزۈلۈشى', 'RS485 ئالاقىسى ۋاقتى-ۋاقتى بىلەن ئۈزۈلىدۇ.', 'high', 'open', 2],
    [8, 'سايە چۈشۈش مەسىلىسى', 'يېڭى بىنا سايىسى سەۋەبىدىن ئەتىگەنلىك ھاسىلات تۆۋەن.', 'low', 'resolved', -12],
    [4, 'تاختاي يېرىلىشى', 'مۆلدۈردىن كېيىن 3 تاختايدا يېرىق بايقالدى.', 'high', 'in_progress', 5],
    [9, 'زاپاس توك سىستېمىسىنى سىناش', 'دوختۇرخانا ئۈچۈن ئايلىق زاپاس توك سىنىقى.', 'critical', 'open', 0],
    [2, 'تاختايلارنى تازىلاش', 'ئايلىق تازىلاش.', 'low', 'resolved', -20],
    [6, 'ھاۋا رايى سېنزورىنى تەڭشەش', 'نۇرلىنىش سېنزورى %8 يۇقىرى كۆرسىتىۋاتىدۇ.', 'medium', 'open', 12],
    [13, 'يۇمشاق دېتال يېڭىلاش', 'ئىنۋېرتور يۇمشاق دېتالىنى ئەڭ يېڭى نەشرىگە يېڭىلاش.', 'low', 'open', 15],
  ]
  return defs.map(([si, title, description, priority, status, due], i) => {
    const site = sites[si]
    const dev = devices.find((d) => d.siteId === site.id && d.type === 'inverter') ?? null
    return {
      id: `tkt-${pad(i + 1)}`,
      siteId: site.id,
      deviceId: i % 3 === 2 ? null : dev?.id ?? null,
      title,
      description,
      priority,
      status,
      assignee: TECHS[i % TECHS.length],
      dueDate: dayKey(addDays(new Date(), due)),
      createdAt: addDays(new Date(), -Math.abs(due) - 3 - (i % 4)).toISOString(),
    }
  })
}

export function monthEnergy(site: Site, month: string) {
  const [y, m] = month.split('-').map(Number)
  const from = new Date(y, m - 1, 1)
  const last = new Date(y, m, 0)
  const today = new Date()
  const to = last > today ? today : last
  return daysBetween(from, to).reduce((s, d) => s + siteDayKwhCached(site, dayKey(d)), 0)
}

export function buildInvoice(site: Site, month: string, seq: number): Invoice {
  const [y, m] = month.split('-').map(Number)
  const issued = new Date(y, m, 1)
  const energy = Math.round(monthEnergy(site, month))
  return {
    id: `inv-${site.id}-${month}`,
    number: `INV-${month.replace('-', '')}-${String(seq).padStart(3, '0')}`,
    siteId: site.id,
    customer: site.customer,
    period: month,
    energyKwh: energy,
    rate: site.pricePerKwh,
    amount: Math.round(energy * site.pricePerKwh * 100) / 100,
    status: 'pending',
    issuedAt: dayKey(issued),
    dueAt: dayKey(addDays(issued, 20)),
    paidAt: null,
  }
}

function buildInvoices(sites: Site[]): Invoice[] {
  const out: Invoice[] = []
  const now = new Date()
  for (let k = 6; k >= 1; k--) {
    const month = monthKey(addMonths(now, -k))
    sites.forEach((s, i) => {
      const inv = buildInvoice(s, month, i + 1)
      if (inv.energyKwh === 0) return
      const due = new Date(inv.dueAt)
      const r = rand(inv.id)
      if (k >= 2 || r < 0.5) {
        inv.status = r < 0.06 && k <= 3 ? 'overdue' : 'paid'
        if (inv.status === 'paid') inv.paidAt = dayKey(addDays(new Date(inv.issuedAt), Math.floor(r * 18)))
      } else inv.status = due < now ? 'overdue' : 'pending'
      out.push(inv)
    })
  }
  return out
}

function buildNotifications(): AppNotification[] {
  const t = (mins: number) => new Date(Date.now() - mins * 60000).toISOString()
  return [
    { id: 'ntf-1', title: 'ئالتاي ئىستانسىسى تورسىز', body: 'ئالتاي تاغ ئارامگاھى 6 سائەتتىن بۇيان سانلىق مەلۇمات ئەۋەتمىدى.', kind: 'danger', link: '/sites/site-11', read: false, createdAt: t(25) },
    { id: 'ntf-2', title: 'ئىنۋېرتور ئاگاھلاندۇرۇشى', body: 'كۇچا ئۈزۈمزارلىقىدىكى ئىنۋېرتور تېمپېراتۇرىسى يۇقىرى.', kind: 'warning', link: '/devices', read: false, createdAt: t(90) },
    { id: 'ntf-3', title: 'ھېسابات تۆلەندى', body: 'تەڭرىتاغ سودا گۇرۇھى ئالدىنقى ئاينىڭ ھېساباتىنى تۆلىدى.', kind: 'success', link: '/billing', read: false, createdAt: t(240) },
    { id: 'ntf-4', title: 'يېڭى ئاسراش ۋەزىپىسى', body: 'چۆچەك دوختۇرخانىسى زاپاس توك سىنىقى بۈگۈن.', kind: 'info', link: '/maintenance', read: true, createdAt: t(600) },
    { id: 'ntf-5', title: 'ئايلىق دوكلات تەييار', body: 'ئالدىنقى ئاينىڭ ئېنېرگىيە دوكلاتىنى چۈشۈرەلەيسىز.', kind: 'info', link: '/reports', read: true, createdAt: t(1440) },
  ]
}

export function buildSeed(): DB {
  const sites = buildSites()
  const devices = buildDevices(sites)
  return {
    sites,
    devices,
    tickets: buildTickets(sites, devices),
    invoices: buildInvoices(sites),
    notifications: buildNotifications(),
    reports: [],
    settings: { ...defaultSettings },
  }
}
