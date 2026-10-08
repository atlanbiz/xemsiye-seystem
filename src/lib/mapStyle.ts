import type { SiteStatus } from './types'

/** Pin colours by site status (docs/PLATFORM.md §3.1). */
export const STATUS_COLOR: Record<SiteStatus, string> = {
  active: '#22c55e',
  idle: '#f59e0b',
  offline: '#ef4444',
  maintenance: '#8b5cf6',
}

/** Pin radius in px, growing with √capacity: ~7 px for a 12 kW roof, 26 px for 800 kW+. */
export const pinRadius = (capacityKw: number) => Math.max(6, Math.min(26, 4 + Math.sqrt(capacityKw) * 0.8))
