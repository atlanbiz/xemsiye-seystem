/**
 * PDF export: renders a printable DOM node to an image (the browser lays out and shapes the
 * text, so Uyghur/Arabic RTL comes out exactly as on screen) and slices it into A4 pages.
 * html-to-image and jsPDF are loaded on demand to keep them out of the main bundle.
 */

const PAGE_W_MM = 210
const PAGE_H_MM = 297
const MARGIN_MM = 12
const CAPTURE_W_PX = 820 // CSS px width the document is laid out at
const SCALE = 2 // device pixels per CSS px

export async function exportPdf(source: HTMLElement, filename: string) {
  const [{ toCanvas }, { jsPDF }] = await Promise.all([import('html-to-image'), import('jspdf')])

  // Lay a light-themed copy out at a fixed width, off screen (`.pdf-light` switches off dark: styles).
  const host = document.createElement('div')
  host.className = 'pdf-light'
  host.dir = document.documentElement.dir
  Object.assign(host.style, { position: 'fixed', top: '0', left: '-10000px', width: `${CAPTURE_W_PX}px`, padding: '4px', background: '#ffffff', color: '#0f172a' })
  const clone = source.cloneNode(true) as HTMLElement
  fitCharts(source, clone)
  host.appendChild(clone)
  document.body.appendChild(host)

  try {
    const breaks = breakPoints(host)
    const canvas = await toCanvas(host, {
      pixelRatio: SCALE,
      backgroundColor: '#ffffff',
      // the copy is parked off screen; render its root in place
      style: { position: 'static', left: '0', top: '0' },
      ...(await fontOptions()),
    })
    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait', compress: true })
    const contentW = PAGE_W_MM - MARGIN_MM * 2
    const pxPerMm = canvas.width / contentW
    const pageH = Math.floor((PAGE_H_MM - MARGIN_MM * 2) * pxPerMm)

    let y = 0
    let first = true
    while (y < canvas.height - 1) {
      // cut at the last row/block boundary that fits, so table rows are never split
      let end = Math.min(y + pageH, canvas.height)
      if (end < canvas.height) {
        const fit = breaks.map((b) => b * SCALE).filter((b) => b > y + pageH * 0.5 && b <= end)
        if (fit.length) end = Math.max(...fit)
      }
      const slice = document.createElement('canvas')
      slice.width = canvas.width
      slice.height = end - y
      const ctx = slice.getContext('2d')!
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, slice.width, slice.height)
      ctx.drawImage(canvas, 0, y, canvas.width, slice.height, 0, 0, canvas.width, slice.height)
      if (!first) pdf.addPage()
      pdf.addImage(slice.toDataURL('image/jpeg', 0.92), 'JPEG', MARGIN_MM, MARGIN_MM, contentW, slice.height / pxPerMm)
      first = false
      y = end
    }
    pdf.setProperties({ title: filename.replace(/\.pdf$/, ''), creator: 'SolarPulse' })
    pdf.save(filename)
  } finally {
    host.remove()
  }
}

/** Recharts renders fixed-pixel SVGs; make the copies scale to the capture width. */
function fitCharts(source: HTMLElement, clone: HTMLElement) {
  const orig = source.querySelectorAll<SVGSVGElement>('svg.recharts-surface')
  clone.querySelectorAll<SVGSVGElement>('svg.recharts-surface').forEach((svg, i) => {
    const o = orig[i]
    if (!o) return
    const w = o.width.baseVal.value
    const h = o.height.baseVal.value
    if (!svg.getAttribute('viewBox')) svg.setAttribute('viewBox', `0 0 ${w} ${h}`)
    svg.setAttribute('width', '100%')
    svg.removeAttribute('height')
    svg.style.height = 'auto'
    for (let el = svg.parentElement; el && el !== clone; el = el.parentElement) {
      el.style.width = '100%'
      el.style.height = 'auto'
    }
  })
}

/** Candidate page-break offsets (CSS px from the top): bottoms of table rows and top-level blocks. */
function breakPoints(host: HTMLElement) {
  const top = host.getBoundingClientRect().top
  const els = [...host.querySelectorAll<HTMLElement>('tr'), ...host.querySelectorAll<HTMLElement>(':scope > * > *')]
  return els.map((el) => Math.round(el.getBoundingClientRect().bottom - top))
}

/**
 * Embeds only the web-font files the page actually loaded (a handful of woff2 subsets) instead
 * of every @font-face. Falls back to system fonts when the font CSS can't be fetched.
 */
async function fontOptions(): Promise<{ fontEmbedCSS: string } | { skipFonts: true }> {
  try {
    const loaded = [...document.fonts].filter((f) => f.status === 'loaded')
    const norm = (s: string) => s.replace(/\s/g, '').toUpperCase()
    const links = [...document.querySelectorAll<HTMLLinkElement>('link[rel="stylesheet"][href*="fonts.googleapis.com"]')]
    let css = ''
    for (const link of links) {
      const text = await (await fetch(link.href)).text()
      for (const block of text.match(/@font-face\s*{[^}]*}/g) ?? []) {
        const family = /font-family:\s*['"]?([^;'"]+)/.exec(block)?.[1]
        const weight = /font-weight:\s*(\d+)/.exec(block)?.[1]
        const range = /unicode-range:\s*([^;]+)/.exec(block)?.[1]
        const url = /url\(([^)]+)\)/.exec(block)?.[1]
        if (!url || !loaded.some((f) => f.family.replace(/['"]/g, '') === family && String(f.weight) === weight && (!range || norm(f.unicodeRange) === norm(range)))) continue
        css += block.replace(url, await toDataUrl(url.replace(/['"]/g, ''))) + '\n'
      }
    }
    return css ? { fontEmbedCSS: css } : { skipFonts: true }
  } catch {
    return { skipFonts: true }
  }
}

async function toDataUrl(url: string) {
  const blob = await (await fetch(url)).blob()
  return new Promise<string>((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result as string)
    r.onerror = () => reject(r.error)
    r.readAsDataURL(blob)
  })
}
