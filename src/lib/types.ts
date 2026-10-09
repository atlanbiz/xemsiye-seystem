export type SiteStatus = 'active' | 'idle' | 'offline' | 'maintenance'
export type SiteType = 'residential' | 'commercial' | 'industrial' | 'utility'

export interface Site {
  id: string
  name: string
  location: string
  type: SiteType
  status: SiteStatus
  capacityKw: number
  batteryKwh: number
  pricePerKwh: number
  customer: string
  installDate: string // YYYY-MM-DD
  lat: number
  lng: number
  // financial model (ROI); defaults in seed.ts → siteFinanceDefaults
  systemCost: number // CAPEX, in settings.currency
  annualOpex: number // O&M per year
  degradationPct: number // output loss per year, %
  tariffEscalationPct: number // tariff growth per year, %
}

export type DeviceType = 'inverter' | 'battery' | 'panel' | 'meter' | 'sensor'
export type DeviceStatus = 'online' | 'warning' | 'offline'

export interface Device {
  id: string
  siteId: string
  name: string
  type: DeviceType
  model: string
  serial: string
  status: DeviceStatus
  health: number // 0-100
  efficiency: number // 0-100
  firmware: string
  installedAt: string
  lastSeen: string // ISO
}

export type TicketPriority = 'low' | 'medium' | 'high' | 'critical'
export type TicketStatus = 'open' | 'in_progress' | 'resolved'

export interface Ticket {
  id: string
  siteId: string
  deviceId: string | null
  title: string
  description: string
  priority: TicketPriority
  status: TicketStatus
  assignee: string
  dueDate: string
  createdAt: string
}

export type InvoiceStatus = 'paid' | 'pending' | 'overdue'

export interface Invoice {
  id: string
  number: string
  siteId: string
  customer: string
  period: string // YYYY-MM
  energyKwh: number
  rate: number
  amount: number
  status: InvoiceStatus
  issuedAt: string
  dueAt: string
  paidAt: string | null
}

export type NotificationKind = 'info' | 'success' | 'warning' | 'danger'

export interface AppNotification {
  id: string
  title: string
  body: string
  kind: NotificationKind
  link: string | null
  read: boolean
  createdAt: string
}

export interface SavedReport {
  id: string
  kind: ReportKind
  title: string
  from: string
  to: string
  siteIds: string[]
  createdAt: string
}

export type ReportKind = 'energy' | 'financial' | 'devices' | 'maintenance' | 'environment'

export type Lang = 'ug' | 'en' | 'ar' | 'tr'
export type Theme = 'light' | 'dark' | 'system'

export interface Settings {
  id: string
  userName: string
  email: string
  role: string
  company: string
  language: Lang
  theme: Theme
  currency: 'USD' | 'CNY' | 'EUR'
  city: string
  lat: number
  lng: number
  co2KgPerKwh: number
  treeKgPerYear: number
  carTonsPerYear: number
  notifyEmail: boolean
  notifyPush: boolean
  notifyDeviceAlerts: boolean
  notifyBilling: boolean
  notifyMaintenance: boolean
  discountRatePct: number
}

export type AlertMetric =
  | 'site_yield_below'
  | 'site_offline'
  | 'device_efficiency_below'
  | 'device_health_below'
  | 'device_offline_minutes'
  | 'invoice_overdue_days'
export type AlertSeverity = 'warning' | 'danger'

export interface AlertRule {
  id: string
  name: string
  metric: AlertMetric
  threshold: number
  siteId: string | null // null = all sites
  severity: AlertSeverity
  enabled: boolean
  lastTriggeredAt: string | null
  createdAt: string
}

export type IntegrationVendor = 'solaredge' | 'fusionsolar' | 'webhook'
export type IntegrationStatus = 'pending' | 'ok' | 'error'

export interface Integration {
  id: string
  vendor: IntegrationVendor
  name: string
  siteId: string
  externalId: string // SolarEdge site id / FusionSolar station code
  config: Record<string, string> // non-secret, e.g. { base_url, username }
  ingestToken: string
  status: IntegrationStatus
  lastSyncAt: string | null
  lastError: string | null
  createdAt: string
}

export interface DB {
  sites: Site[]
  devices: Device[]
  tickets: Ticket[]
  invoices: Invoice[]
  notifications: AppNotification[]
  reports: SavedReport[]
  alertRules: AlertRule[]
  integrations: Integration[]
  settings: Settings
}

export type CollectionName = Exclude<keyof DB, 'settings'>
export type Row<C extends CollectionName> = DB[C][number]
