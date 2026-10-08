import { useMemo, useState } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { FileText, Download, Printer, Save, Trash2, Zap, DollarSign, Cpu, Wrench, Leaf, FolderOpen } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n, type TFn } from '../context/i18n'
import { useToast } from '../context/toast'
import { Card, ChartTooltip, EmptyState, Field, Input, PageHeader } from '../components/ui'
import { Logo } from '../components/Layout'
import { co2Tons, dailySeries, totalKwh } from '../lib/sim'
import type { DB, ReportKind, SavedReport, Site } from '../lib/types'
import { addDays, axisEnergy, cn, dayKey, downloadFile, parseDay, toCSV, uid } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const KINDS: { kind: ReportKind; icon: typeof Zap; key: DictKey }[] = [
  { kind: 'energy', icon: Zap, key: 'rep.energy' },
  { kind: 'financial', icon: DollarSign, key: 'rep.financial' },
  { kind: 'devices', icon: Cpu, key: 'rep.devices' },
  { kind: 'maintenance', icon: Wrench, key: 'rep.maintenance' },
  { kind: 'environment', icon: Leaf, key: 'rep.environment' },
]

type Fmt = ReturnType<typeof useI18n>['fmt']
interface Report {
  columns: string[]
  rows: (string | number)[][]
  display: string[][]
  summary: { label: string; value: string }[]
  chart?: { x: string; v: number }[]
}

function build(kind: ReportKind, from: Date, to: Date, sites: Site[], db: DB, t: TFn, fmt: Fmt): Report {
  const ids = new Set(sites.map((s) => s.id))
  const siteName = (id: string) => db.sites.find((s) => s.id === id)?.name ?? '—'
  const fromK = dayKey(from)
  const toK = dayKey(to)
  switch (kind) {
    case 'energy': {
      const data = sites.map((s) => {
        const kwh = totalKwh([s], from, to)
        const days = Math.max(1, Math.round((to.getTime() - from.getTime()) / 86400000) + 1)
        return { s, kwh, yld: kwh / days / s.capacityKw, rev: kwh * s.pricePerKwh }
      })
      const total = data.reduce((a, d) => a + d.kwh, 0)
      return {
        columns: [t('common.site'), t('sites.capacity'), t('an.production.short'), t('sites.yield'), t('ov.totalRevenue')],
        rows: data.map((d) => [d.s.name, d.s.capacityKw, Math.round(d.kwh), +d.yld.toFixed(2), +d.rev.toFixed(2)]),
        display: data.map((d) => [d.s.name, fmt.power(d.s.capacityKw), fmt.energy(d.kwh), `${fmt.num(d.yld, 2)} kWh/kWp`, fmt.money(d.rev)]),
        summary: [
          { label: t('an.production'), value: fmt.energy(total) },
          { label: t('ov.totalRevenue'), value: fmt.money(data.reduce((a, d) => a + d.rev, 0)) },
          { label: t('rep.sites'), value: String(sites.length) },
        ],
        chart: dailySeries(sites, from, to).map((p) => ({ x: fmt.dateShort(p.date), v: p.kwh })),
      }
    }
    case 'financial': {
      const inv = db.invoices.filter((i) => ids.has(i.siteId) && i.issuedAt >= fromK && i.issuedAt <= toK)
      const by = sites.map((s) => {
        const l = inv.filter((i) => i.siteId === s.id)
        const billed = l.reduce((a, i) => a + i.amount, 0)
        const paid = l.filter((i) => i.status === 'paid').reduce((a, i) => a + i.amount, 0)
        return { s, n: l.length, billed, paid, open: billed - paid }
      }).filter((r) => r.n > 0)
      const billed = by.reduce((a, r) => a + r.billed, 0)
      const paid = by.reduce((a, r) => a + r.paid, 0)
      return {
        columns: [t('common.site'), t('bill.customer'), t('bill.invoice'), t('bill.totalBilled'), t('bill.paid'), t('bill.pending')],
        rows: by.map((r) => [r.s.name, r.s.customer, r.n, +r.billed.toFixed(2), +r.paid.toFixed(2), +r.open.toFixed(2)]),
        display: by.map((r) => [r.s.name, r.s.customer, String(r.n), fmt.money2(r.billed), fmt.money2(r.paid), fmt.money2(r.open)]),
        summary: [
          { label: t('bill.totalBilled'), value: fmt.money(billed) },
          { label: t('bill.paid'), value: fmt.money(paid) },
          { label: t('bill.pending'), value: fmt.money(billed - paid) },
        ],
      }
    }
    case 'devices': {
      const devs = db.devices.filter((d) => ids.has(d.siteId))
      const avg = devs.length ? devs.reduce((a, d) => a + d.health, 0) / devs.length : 0
      return {
        columns: [t('common.name'), t('common.site'), t('common.type'), t('dev.model'), t('common.status'), t('dev.health'), t('dev.efficiency'), t('dev.firmware')],
        rows: devs.map((d) => [d.name, siteName(d.siteId), d.type, d.model, d.status, d.health, d.efficiency, d.firmware]),
        display: devs.map((d) => [d.name, siteName(d.siteId), t(`devType.${d.type}` as DictKey), d.model, t(`status.${d.status}` as DictKey), `${d.health}%`, fmt.pct(d.efficiency), d.firmware]),
        summary: [
          { label: t('dev.title'), value: String(devs.length) },
          { label: t('status.online'), value: String(devs.filter((d) => d.status === 'online').length) },
          { label: t('status.offline'), value: String(devs.filter((d) => d.status === 'offline').length) },
          { label: t('dev.health'), value: fmt.pct(avg) },
        ],
      }
    }
    case 'maintenance': {
      const tk = db.tickets.filter((x) => ids.has(x.siteId) && ((x.createdAt.slice(0, 10) >= fromK && x.createdAt.slice(0, 10) <= toK) || (x.dueDate >= fromK && x.dueDate <= toK)))
      return {
        columns: [t('mt.ticketTitle'), t('common.site'), t('mt.priority'), t('common.status'), t('mt.assignee'), t('mt.due')],
        rows: tk.map((x) => [x.title, siteName(x.siteId), x.priority, x.status, x.assignee, x.dueDate]),
        display: tk.map((x) => [x.title, siteName(x.siteId), t(`prio.${x.priority}` as DictKey), t(`status.${x.status}` as DictKey), x.assignee || '—', fmt.date(x.dueDate)]),
        summary: [
          { label: t('common.total'), value: String(tk.length) },
          { label: t('status.resolved'), value: String(tk.filter((x) => x.status === 'resolved').length) },
          { label: t('status.open'), value: String(tk.filter((x) => x.status !== 'resolved').length) },
        ],
      }
    }
    case 'environment': {
      const s = db.settings
      const data = sites.map((x) => {
        const kwh = totalKwh([x], from, to)
        const co2 = co2Tons(kwh, s.co2KgPerKwh)
        return { x, kwh, co2, trees: (co2 * 1000) / s.treeKgPerYear, cars: co2 / s.carTonsPerYear }
      })
      const co2 = data.reduce((a, d) => a + d.co2, 0)
      return {
        columns: [t('common.site'), t('an.production.short') + ' (kWh)', 'CO₂ (t)', t('ov.trees'), t('ov.cars')],
        rows: data.map((d) => [d.x.name, Math.round(d.kwh), +d.co2.toFixed(2), Math.round(d.trees), +d.cars.toFixed(1)]),
        display: data.map((d) => [d.x.name, fmt.energy(d.kwh), fmt.num(d.co2, 2), fmt.num(d.trees), fmt.num(d.cars, 1)]),
        summary: [
          { label: t('ov.co2Offset'), value: `${fmt.num(co2, 1)} ${t('common.tons')}` },
          { label: t('ov.trees'), value: fmt.num((co2 * 1000) / s.treeKgPerYear) },
          { label: t('ov.cars'), value: fmt.num(co2 / s.carTonsPerYear, 1) },
        ],
        chart: dailySeries(sites, from, to).map((p) => ({ x: fmt.dateShort(p.date), v: co2Tons(p.kwh, s.co2KgPerKwh) })),
      }
    }
  }
}

export default function Reports() {
  const { db, upsert, remove } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const [kind, setKind] = useState<ReportKind>('energy')
  const [from, setFrom] = useState(dayKey(new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1)))
  const [to, setTo] = useState(dayKey(new Date(new Date().getFullYear(), new Date().getMonth(), 0)))
  const [siteIds, setSiteIds] = useState<string[]>([])
  const [generated, setGenerated] = useState<{ kind: ReportKind; from: string; to: string; siteIds: string[] } | null>(null)

  const report = useMemo(() => {
    if (!generated) return null
    const sites = generated.siteIds.length ? db.sites.filter((s) => generated.siteIds.includes(s.id)) : db.sites
    const today = new Date()
    const toD = parseDay(generated.to) > today ? today : parseDay(generated.to)
    return build(generated.kind, parseDay(generated.from), toD, sites, db, t, fmt)
  }, [generated, db, t, fmt])

  const title = (k: ReportKind) => t(KINDS.find((x) => x.kind === k)!.key)

  const save = async () => {
    if (!generated) return
    await upsert('reports', { id: uid('rep-'), ...generated, title: `${title(generated.kind)} · ${generated.from} → ${generated.to}`, createdAt: new Date().toISOString() })
    toast(t('rep.saved'))
  }
  const exportCsv = () => {
    if (!report || !generated) return
    downloadFile(`${generated.kind}-report-${generated.from}_${generated.to}.csv`, toCSV([report.columns, ...report.rows]), 'text/csv')
  }
  const openSaved = (r: SavedReport) => {
    setKind(r.kind); setFrom(r.from); setTo(r.to); setSiteIds(r.siteIds)
    setGenerated({ kind: r.kind, from: r.from, to: r.to, siteIds: r.siteIds })
  }
  const quick = (days: number) => { setFrom(dayKey(addDays(new Date(), -days + 1))); setTo(dayKey(new Date())) }

  return (
    <div className="space-y-4">
      <PageHeader title={t('rep.title')} subtitle={t('rep.subtitle')} />
      <Card className="no-print">
        <div className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
          {KINDS.map(({ kind: k, icon: Icon, key }) => (
            <button key={k} onClick={() => setKind(k)} className={cn('flex cursor-pointer items-center gap-2 rounded-xl border p-3 text-start text-sm font-medium transition', kind === k ? 'border-brand-400 bg-brand-50 text-brand-700 ring-4 ring-brand-100 dark:bg-brand-500/15 dark:text-brand-300 dark:ring-brand-500/20' : 'border-slate-200 bg-white/70 hover:border-brand-200 dark:border-slate-600 dark:bg-slate-800/60')}>
              <Icon className="h-4 w-4 shrink-0" />{t(key)}
            </button>
          ))}
        </div>
        <div className="grid gap-3 md:grid-cols-[auto_auto_1fr]">
          <Field label={t('common.from')}><Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field label={t('common.to')}><Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></Field>
          <div className="flex flex-wrap items-end gap-2">
            {[[7, t('common.last7')], [30, t('common.last30')], [90, t('common.last90')], [365, t('common.last12m')]].map(([d, l]) => (
              <button key={d} className="btn btn-ghost !py-1.5 text-xs" onClick={() => quick(d as number)}>{l}</button>
            ))}
          </div>
        </div>
        <div className="mt-3">
          <div className="mb-1 text-xs font-medium text-slate-600 dark:text-slate-300">{t('rep.sites')}</div>
          <div className="flex flex-wrap gap-1.5">
            <button onClick={() => setSiteIds([])} className={cn('cursor-pointer rounded-full border px-3 py-1 text-xs', siteIds.length === 0 ? 'border-brand-500 bg-brand-600 text-white' : 'border-slate-200 bg-white dark:border-slate-600 dark:bg-slate-800')}>{t('rep.allSites')}</button>
            {db.sites.map((s) => {
              const on = siteIds.includes(s.id)
              return <button key={s.id} onClick={() => setSiteIds(on ? siteIds.filter((x) => x !== s.id) : [...siteIds, s.id])} className={cn('cursor-pointer rounded-full border px-3 py-1 text-xs', on ? 'border-brand-500 bg-brand-600 text-white' : 'border-slate-200 bg-white dark:border-slate-600 dark:bg-slate-800')}>{s.name}</button>
            })}
          </div>
        </div>
        <div className="mt-4 flex justify-end">
          <button className="btn btn-primary" disabled={!from || !to || from > to} onClick={() => setGenerated({ kind, from, to, siteIds })}><FileText className="h-4 w-4" />{t('rep.generate')}</button>
        </div>
      </Card>

      {report && generated && (
        <Card title={t('rep.preview')} action={<div className="no-print flex gap-2">
          <button className="btn btn-ghost !py-1.5 text-xs" onClick={save}><Save className="h-3.5 w-3.5" />{t('common.save')}</button>
          <button className="btn btn-ghost !py-1.5 text-xs" onClick={exportCsv}><Download className="h-3.5 w-3.5" />{t('common.export')}</button>
          <button className="btn btn-ghost !py-1.5 text-xs" onClick={() => window.print()}><Printer className="h-3.5 w-3.5" />{t('common.print')}</button>
        </div>}>
          <div className="print-area space-y-4 p-1">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3 dark:border-slate-700">
              <div>
                <div className="text-lg font-semibold">{title(generated.kind)}</div>
                <div className="text-xs text-slate-500">{fmt.date(generated.from)} — {fmt.date(generated.to)} · {generated.siteIds.length ? `${generated.siteIds.length} ${t('rep.sites')}` : t('rep.allSites')}</div>
              </div>
              <Logo />
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {report.summary.map((s) => (
                <div key={s.label} className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900/40">
                  <div className="text-xs text-slate-500">{s.label}</div>
                  <div className="text-lg font-semibold" dir="ltr" style={{ textAlign: 'start' }}>{s.value}</div>
                </div>
              ))}
            </div>
            {report.chart && report.chart.length > 1 && (
              <div className="h-52" dir="ltr">
                <ResponsiveContainer>
                  <AreaChart data={report.chart} margin={{ left: 0, right: 8, top: 8 }}>
                    <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                    <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} minTickGap={18} />
                    <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} width={64} tickFormatter={(v) => (generated.kind === 'environment' ? `${+v.toFixed(1)} t` : axisEnergy(v))} />
                    <Tooltip content={<ChartTooltip format={(v) => (generated.kind === 'environment' ? `${fmt.num(v, 2)} t` : fmt.energy(v))} />} />
                    <Area dataKey="v" name={title(generated.kind)} stroke="#3b74f6" fill="#3b74f6" fillOpacity={0.15} strokeWidth={2} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
            <div className="overflow-x-auto scrollbar-thin">
              {report.display.length === 0 ? <EmptyState text={t('common.noData')} /> : (
                <table className="table-base">
                  <thead><tr>{report.columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
                  <tbody>{report.display.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody>
                </table>
              )}
            </div>
          </div>
        </Card>
      )}

      <Card title={t('rep.history')} className="no-print">
        {db.reports.length === 0 ? <EmptyState text={t('rep.empty')} /> : (
          <ul className="divide-y divide-slate-100 dark:divide-slate-700">
            {db.reports.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-2 py-2.5">
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">{title(r.kind)} <span className="text-xs text-slate-500" dir="ltr">· {r.from} → {r.to}</span></div>
                  <div className="text-xs text-slate-500">{fmt.ago(r.createdAt)} · {r.siteIds.length ? `${r.siteIds.length} ${t('rep.sites')}` : t('rep.allSites')}</div>
                </div>
                <div className="flex gap-1">
                  <button className="btn btn-ghost !py-1 text-xs" onClick={() => openSaved(r)}><FolderOpen className="h-3.5 w-3.5" />{t('rep.open')}</button>
                  <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => remove('reports', r.id)}><Trash2 className="h-3.5 w-3.5" /></button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
