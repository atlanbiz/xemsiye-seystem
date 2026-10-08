import { useEffect, useRef, useState, type ReactNode, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { X, ChevronDown, Inbox } from 'lucide-react'
import { cn } from '../lib/utils'
import { useI18n } from '../context/i18n'

export function Card({ className, children, title, action, ...rest }: { className?: string; children: ReactNode; title?: ReactNode; action?: ReactNode; id?: string }) {
  return (
    <section className={cn('card', className)} {...rest}>
      {(title || action) && (
        <div className="mb-3 flex items-center justify-between gap-2">
          {title && <h3 className="text-[15px] font-semibold text-slate-800 dark:text-slate-100">{title}</h3>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

const TONES = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-500/15 dark:text-emerald-300 dark:ring-emerald-500/30',
  red: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-500/15 dark:text-rose-300 dark:ring-rose-500/30',
  amber: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-500/15 dark:text-amber-300 dark:ring-amber-500/30',
  blue: 'bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-500/15 dark:text-blue-300 dark:ring-blue-500/30',
  slate: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-700/60 dark:text-slate-300 dark:ring-slate-600',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200 dark:bg-violet-500/15 dark:text-violet-300 dark:ring-violet-500/30',
}
export type Tone = keyof typeof TONES

const STATUS_TONE: Record<string, Tone> = {
  active: 'green', online: 'green', paid: 'green', resolved: 'green',
  idle: 'amber', warning: 'amber', pending: 'amber', in_progress: 'blue', medium: 'blue',
  offline: 'red', overdue: 'red', critical: 'red', high: 'amber',
  maintenance: 'violet', open: 'slate', low: 'slate',
}

export function Badge({ tone = 'slate', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return <span className={cn('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset', TONES[tone], className)}>{children}</span>
}

export function StatusBadge({ status, label }: { status: string; label: string }) {
  return <Badge tone={STATUS_TONE[status] ?? 'slate'}>{label}</Badge>
}

export function Modal({ open, onClose, title, children, footer, size = 'md' }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode; size?: 'sm' | 'md' | 'lg' | 'xl' }) {
  useEffect(() => {
    if (!open) return
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [open, onClose])
  if (!open) return null
  const w = { sm: 'max-w-md', md: 'max-w-xl', lg: 'max-w-3xl', xl: 'max-w-5xl' }[size]
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-0 backdrop-blur-sm sm:items-center sm:p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div role="dialog" aria-modal className={cn('fade-in flex max-h-[92vh] w-full flex-col rounded-t-2xl bg-white shadow-2xl sm:rounded-2xl dark:bg-slate-800', w)}>
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4 dark:border-slate-700">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button className="icon-btn h-8 w-8" onClick={onClose} aria-label="close">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="scrollbar-thin overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-3 dark:border-slate-700">{footer}</div>}
      </div>
    </div>
  )
}

export function ConfirmDialog({ open, onClose, onConfirm, message, danger = true }: { open: boolean; onClose: () => void; onConfirm: () => void; message: ReactNode; danger?: boolean }) {
  const { t } = useI18n()
  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={t('common.confirm')}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button>
          <button className={cn('btn', danger ? 'btn-danger' : 'btn-primary')} onClick={() => { onConfirm(); onClose() }}>
            {danger ? t('common.delete') : t('common.confirm')}
          </button>
        </>
      }
    >
      <p className="text-sm text-slate-600 dark:text-slate-300">{message}</p>
    </Modal>
  )
}

export function Field({ label, error, children, className }: { label: string; error?: string; children: ReactNode; className?: string }) {
  return (
    <label className={cn('block', className)}>
      <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">{label}</span>
      {children}
      {error && <span className="mt-1 block text-xs text-rose-600">{error}</span>}
    </label>
  )
}

export const Input = (p: InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={cn('input', p.className)} />
export const Textarea = (p: TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea rows={3} {...p} className={cn('input', p.className)} />
export const Select = ({ className, ...p }: SelectHTMLAttributes<HTMLSelectElement>) => (
  <div className="relative">
    <select {...p} className={cn('input appearance-none pe-8', className)} />
    <ChevronDown className="pointer-events-none absolute end-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
  </div>
)

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 py-2">
      <span className="text-sm">{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={cn('relative h-6 w-11 shrink-0 rounded-full transition', checked ? 'bg-brand-600' : 'bg-slate-300 dark:bg-slate-600')}
      >
        <span className={cn('absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all', checked ? 'start-[22px]' : 'start-0.5')} />
      </button>
    </label>
  )
}

export function Segmented<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  return (
    <div className="inline-flex rounded-xl bg-slate-100 p-1 dark:bg-slate-700/60">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={cn('cursor-pointer rounded-lg px-3 py-1 text-xs font-medium transition', value === o.value ? 'bg-white text-brand-700 shadow-sm dark:bg-slate-800 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400')}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** Compact dropdown like the "Daily ⌄" pill in the design. */
export function PillSelect<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useClickOutside(ref, () => setOpen(false))
  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen((o) => !o)} className="inline-flex cursor-pointer items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-600 hover:border-brand-300 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-300">
        {options.find((o) => o.value === value)?.label}
        <ChevronDown className="h-3.5 w-3.5" />
      </button>
      {open && (
        <div className="fade-in absolute end-0 z-20 mt-1 min-w-32 rounded-xl border border-slate-100 bg-white p-1 shadow-lg dark:border-slate-700 dark:bg-slate-800">
          {options.map((o) => (
            <button key={o.value} onClick={() => { onChange(o.value); setOpen(false) }} className={cn('block w-full cursor-pointer rounded-lg px-3 py-1.5 text-start text-xs hover:bg-slate-50 dark:hover:bg-slate-700', o.value === value && 'font-semibold text-brand-600')}>
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export function useClickOutside(ref: React.RefObject<HTMLElement | null>, cb: () => void) {
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) cb()
    }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [ref, cb])
}

/** Circular progress ring (performance & health widgets, capacity donut). */
export function Ring({ value, size = 44, stroke = 4, color = '#22c55e', track = 'rgba(148,163,184,.25)', children }: { value: number; size?: number; stroke?: number; color?: string; track?: string; children?: ReactNode }) {
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const v = Math.max(0, Math.min(100, value))
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={track} strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c - (c * v) / 100} style={{ transition: 'stroke-dashoffset .6s' }} />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">{children}</div>
    </div>
  )
}

/** Tiny SVG sparkline, always LTR (time flows left→right). */
export function Sparkline({ data, color = '#3b74f6', width = 80, height = 26, fill = false }: { data: number[]; color?: string; width?: number; height?: number; fill?: boolean }) {
  if (data.length < 2) return <svg width={width} height={height} />
  const max = Math.max(...data)
  const min = Math.min(...data)
  const span = max - min || 1
  const pts = data.map((v, i) => [(i / (data.length - 1)) * width, height - 2 - ((v - min) / span) * (height - 4)])
  const d = pts.map((p, i) => {
    if (i === 0) return `M${p[0]},${p[1]}`
    const prev = pts[i - 1]
    const cx = (prev[0] + p[0]) / 2
    return `C${cx},${prev[1]} ${cx},${p[1]} ${p[0]},${p[1]}`
  }).join(' ')
  const id = 'sg' + color.replace('#', '')
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} style={{ direction: 'ltr' }} className="shrink-0 overflow-visible">
      {fill && (
        <>
          <defs>
            <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor={color} stopOpacity=".3" />
              <stop offset="1" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={`${d} L${width},${height} L0,${height} Z`} fill={`url(#${id})`} />
        </>
      )}
      <path d={d} fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" />
    </svg>
  )
}

export function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-10 text-slate-400">
      <Inbox className="h-8 w-8" />
      <p className="text-sm">{text}</p>
    </div>
  )
}

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="relative">
      <svg className="pointer-events-none absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="input w-56 ps-9" />
    </div>
  )
}

export function Stat({ label, value, sub, icon, tone = 'blue' }: { label: string; value: ReactNode; sub?: ReactNode; icon?: ReactNode; tone?: 'blue' | 'green' | 'amber' | 'red' | 'violet' }) {
  const bg = { blue: 'bg-blue-50 text-blue-600 dark:bg-blue-500/15', green: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15', amber: 'bg-amber-50 text-amber-600 dark:bg-amber-500/15', red: 'bg-rose-50 text-rose-600 dark:bg-rose-500/15', violet: 'bg-violet-50 text-violet-600 dark:bg-violet-500/15' }[tone]
  return (
    <div className="card flex items-start gap-3">
      {icon && <div className={cn('grid h-10 w-10 shrink-0 place-items-center rounded-xl', bg)}>{icon}</div>}
      <div className="min-w-0">
        <div className="text-xs text-slate-500 dark:text-slate-400">{label}</div>
        <div className="mt-0.5 truncate text-xl font-semibold">{value}</div>
        {sub && <div className="mt-0.5 text-xs text-slate-500">{sub}</div>}
      </div>
    </div>
  )
}

export function ChartTooltip({ active, payload, label, format, labelFormat }: { active?: boolean; payload?: { value: number; name: string; color: string }[]; label?: string | number; format: (v: number) => string; labelFormat?: (l: string | number) => string }) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-xl border border-slate-100 bg-white/95 px-3 py-2 text-xs shadow-lg dark:border-slate-700 dark:bg-slate-800/95">
      <div className="mb-1 font-medium text-slate-500">{labelFormat ? labelFormat(label ?? '') : label}</div>
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full" style={{ background: p.color }} />
          <span className="text-slate-500">{p.name}</span>
          <span className="font-semibold">{format(p.value)}</span>
        </div>
      ))}
    </div>
  )
}

export function usePaged<T>(rows: T[], size = 15) {
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(rows.length / size))
  useEffect(() => {
    if (page >= pages) setPage(0)
  }, [page, pages])
  return { items: rows.slice(page * size, page * size + size), page, pages, setPage }
}

export function Pager({ page, pages, setPage }: { page: number; pages: number; setPage: (p: number) => void }) {
  if (pages <= 1) return null
  return (
    <div className="flex items-center justify-center gap-1 border-t border-slate-100 p-3 dark:border-slate-700" dir="ltr">
      <button className="btn btn-ghost !px-2.5 !py-1 text-xs" disabled={page === 0} onClick={() => setPage(page - 1)}>‹</button>
      {Array.from({ length: pages }, (_, i) => i)
        .filter((i) => i === 0 || i === pages - 1 || Math.abs(i - page) <= 1)
        .map((i, idx, arr) => (
          <span key={i} className="flex items-center gap-1">
            {idx > 0 && i - arr[idx - 1] > 1 && <span className="px-1 text-xs text-slate-400">…</span>}
            <button onClick={() => setPage(i)} className={cn('h-7 min-w-7 cursor-pointer rounded-lg px-2 text-xs', i === page ? 'bg-brand-600 text-white' : 'hover:bg-slate-100 dark:hover:bg-slate-700')}>{i + 1}</button>
          </span>
        ))}
      <button className="btn btn-ghost !px-2.5 !py-1 text-xs" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>›</button>
    </div>
  )
}
