/**
 * Leaflet + OpenStreetMap site map (thin React wrapper around plain Leaflet).
 * Pins are coloured by status and sized by capacity (docs/PLATFORM.md §3.1); the popup body is
 * React content rendered through a portal so it can use router links, i18n and live values.
 * Loaded lazily (React.lazy) so Leaflet stays out of the main bundle.
 */
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Site } from '../lib/types'
import { STATUS_COLOR, pinRadius } from '../lib/mapStyle'
import { cn } from '../lib/utils'

interface Props {
  sites: Site[]
  className?: string
  /** Site whose popup should be open (e.g. picked from a list). */
  selectedId?: string | null
  onSelect?: (id: string | null) => void
  popup?: (site: Site) => ReactNode
  /** Single-site mini map: fixed zoom, no wheel zoom. */
  compact?: boolean
}

export default function SiteMap({ sites, className, selectedId, onSelect, popup, compact }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const pins = useRef(new Map<string, L.CircleMarker>())
  const leafletPopup = useRef<L.Popup | null>(null)
  const fitted = useRef(false)
  const switching = useRef(false)
  const popupEl = useMemo(() => document.createElement('div'), [])
  const [openId, setOpenId] = useState<string | null>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect

  useEffect(() => {
    const m = L.map(el.current!, { scrollWheelZoom: !compact, zoomControl: true, attributionControl: true, worldCopyJump: true })
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a>',
    }).addTo(m)
    layer.current = L.layerGroup().addTo(m)
    leafletPopup.current = L.popup({ autoPanPadding: [24, 24], maxWidth: 320, offset: [0, -4] }).setContent(popupEl)
    m.on('popupclose', () => {
      if (switching.current) return
      setOpenId(null)
      onSelectRef.current?.(null)
    })
    map.current = m
    const ro = new ResizeObserver(() => m.invalidateSize())
    ro.observe(el.current!)
    return () => {
      ro.disconnect()
      m.remove()
      map.current = null
      fitted.current = false
    }
  }, [compact, popupEl])

  const open = (site: Site) => {
    const m = map.current
    if (!m || !popup) return
    switching.current = true
    leafletPopup.current!.setLatLng([site.lat, site.lng]).openOn(m)
    switching.current = false
    setOpenId(site.id)
  }

  // (re)draw pins; big pins first so small ones stay clickable on top
  useEffect(() => {
    const m = map.current
    const g = layer.current
    if (!m || !g) return
    g.clearLayers()
    pins.current.clear()
    for (const s of [...sites].sort((a, b) => b.capacityKw - a.capacityKw)) {
      const c = STATUS_COLOR[s.status]
      const pin = L.circleMarker([s.lat, s.lng], { radius: pinRadius(s.capacityKw), color: '#ffffff', weight: 2, fillColor: c, fillOpacity: 0.88, className: 'site-pin' })
      pin.bindTooltip(s.name, { direction: 'top', offset: [0, -pinRadius(s.capacityKw)] })
      if (popup)
        pin.on('click', () => {
          pin.closeTooltip()
          open(s)
          onSelectRef.current?.(s.id)
        })
      pin.addTo(g)
      pins.current.set(s.id, pin)
    }
    if (!fitted.current && sites.length) {
      fitted.current = true
      if (sites.length === 1 || compact) m.setView([sites[0].lat, sites[0].lng], compact ? 9 : 11)
      else m.fitBounds(L.latLngBounds(sites.map((s) => [s.lat, s.lng] as [number, number])), { padding: [40, 40] })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sites, compact])

  // external selection → fly there and open its card
  useEffect(() => {
    const m = map.current
    const s = sites.find((x) => x.id === selectedId)
    if (!m || !s || openId === s.id) return
    m.flyTo([s.lat, s.lng], Math.max(m.getZoom(), 8), { duration: 0.6 })
    open(s)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const openSite = sites.find((s) => s.id === openId)
  // portal content changed size → let Leaflet re-measure and re-position the popup
  useEffect(() => {
    if (openSite) leafletPopup.current?.update()
  })

  return (
    <div className={cn('relative isolate overflow-hidden rounded-2xl', className)} dir="ltr">
      <div ref={el} className="absolute inset-0" />
      {openSite && popup && createPortal(popup(openSite), popupEl)}
    </div>
  )
}
