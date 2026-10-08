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
}

export interface DB {
  sites: Site[]
  devices: Device[]
  tickets: Ticket[]
  invoices: Invoice[]
  notifications: AppNotification[]
  reports: SavedReport[]
  settings: Settings
}

export type CollectionName = Exclude<keyof DB, 'settings'>
export type Row<C extends CollectionName> = DB[C][number]
