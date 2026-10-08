import { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Sun, TrendingUp, Trophy, Gauge, TreePine, Leaf, Car, Download } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { Card, ChartTooltip, PageHeader, Segmented, Select, Stat } from '../components/ui'
import { co2Tons, consumptionKw, dailySeries, rangeOf, siteKw, totalKwh, hourlySeries } from '../lib/sim'
import { addDays, axisEnergy, axisPower, dayKey, downloadFile, monthKey, toCSV } from '../lib/utils'
import type { DictKey } from '../i18n/en'

type Range = '7d' | '30d' | '90d' | '12m'
const COLORS = ['#3b74f6', '#22c55e', '#f59e0b', '#8b5cf6', '#0ea5e9', '#ef4444']

export default function Analytics() {
  const { db, now } = useData()
  const { t, fmt } = useI18n()
  const loc = useLocation()
  const [range, setRange] = useState<Range>('30d')
  const [siteId, setSiteId] = useState('all')
  const minute = Math.floor(now / 60000)

  useEffect(() => {
    if (loc.hash === '#impact') setTimeout(() => document.getElementById('impact')?.scrollIntoView({ behavior: 'smooth' }), 100)
  }, [loc.hash])

  const sites = useMemo(() => (siteId === 'all' ? db.sites : db.sites.filter((s) => s.id === siteId)), [db.sites, siteId])
  const capacity = sites.reduce((s, x) => s + x.capacityKw, 0)

  const a = useMemo(() => {
    const { from, to } = rangeOf(range)
    const series = dailySeries(sites, from, to)
    const total = series.reduce((s, p) => s + p.kwh, 0)
    const days = series.length
    const prevFrom = addDays(from, -days)
    const prevTotal = totalKwh(sites, prevFrom, addDays(from, -1))
    const best = series.reduce((m, p) => (p.kwh > m.kwh ? p : m), series[0])
    // aggregate monthly for 12m
    let trend: { x: string; v: number }[]
    if (range === '12m') {
      const m = new Map<string, { x: string; v: number }>()
      for (const p of series) {
        const k = monthKey(p.date)
        const e = m.get(k) ?? { x: fmt.monthShort(p.date), v: 0 }
        e.v += p.kwh
        m.set(k, e)
      }
      trend = [...m.values()]
    } else trend = series.map((p) => ({ x: fmt.dateShort(p.date), v: p.kwh }))

    const bySite = sites
      .map((s) => ({ name: s.name, v: totalKwh([s], from, to), y: totalKwh([s], from, to) / days / s.capacityKw }))
      .sort((x, y) => y.v - x.v)
    const typeMap = new Map<string, number>()
    for (const s of sites) typeMap.set(s.type, (typeMap.get(s.type) ?? 0) + totalKwh([s], from, to))
    const byType = [...typeMap.entries()].map(([k, v]) => ({ name: t(`siteType.${k}` as DictKey), value: v }))

    const day = dayKey(new Date())
    const pvc = hourlySeries(sites, day, 60).map((p) => ({ x: p.label, prod: p.kw, cons: p.kw === null ? null : consumptionKw(sites, p.hour, day) }))

    // heatmap: 14 days × 24 hours
    const heat = Array.from({ length: 14 }, (_, i) => {
      const d = addDays(new Date(), -13 + i)
      const k = dayKey(d)
      return { label: fmt.dateShort(d), cells: Array.from({ length: 24 }, (_, h) => sites.reduce((s, x) => s + siteKw(x, k, h + 0.5), 0)) }
    })
    const heatMax = Math.max(1, ...heat.flatMap((r) => r.cells))

    const co2 = co2Tons(total, db.settings.co2KgPerKwh)
    const monthlyImpact = (() => {
      const out: { x: string; co2: number }[] = []
      for (let k = 11; k >= 0; k--) {
        const d = new Date(new Date().getFullYear(), new Date().getMonth() - k, 1)
        const end = new Date(d.getFullYear(), d.getMonth() + 1, 0)
        out.push({ x: fmt.monthShort(d), co2: co2Tons(totalKwh(sites, d, end > new Date() ? new Date() : end), db.settings.co2KgPerKwh) })
      }
      return out
    })()

    return { series, total, prevTotal, avg: total / days, best, trend, bySite, byType, pvc, heat, heatMax, co2, days, monthlyImpact }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sites, range, minute, fmt, t, db.settings.co2KgPerKwh])

  const exportCsv = () => {
    const rows: (string | number)[][] = [['date', 'kwh']]
    a.series.forEach((p) => rows.push([dayKey(p.date), Math.round(p.kwh * 10) / 10]))
    downloadFile(`analytics-${range}.csv`, toCSV(rows), 'text/csv')
  }

  const s = db.settings
  const change = ((a.total - a.prevTotal) / (a.prevTotal || 1)) * 100

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('an.title')}
        subtitle={t('an.subtitle')}
        actions={
          <>
            <div className="w-52"><Select value={siteId} onChange={(e) => setSiteId(e.target.value)}><option value="all">{t('an.allSites')}</option>{db.sites.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></div>
            <Segmented value={range} onChange={setRange} options={[{ value: '7d', label: '7D' }, { value: '30d', label: '30D' }, { value: '90d', label: '90D' }, { value: '12m', label: '12M' }]} />
            <button className="btn btn-ghost" onClick={exportCsv}><Download className="h-4 w-4" />{t('common.export')}</button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={<Sun className="h-5 w-5" />} label={t('an.production')} value={<span dir="ltr">{fmt.energy(a.total)}</span>} sub={<span className={change >= 0 ? 'text-emerald-600' : 'text-rose-600'} dir="ltr">{fmt.signedPct(change)}</span>} />
        <Stat icon={<TrendingUp className="h-5 w-5" />} tone="green" label={t('an.avgDaily')} value={<span dir="ltr">{fmt.energy(a.avg)}</span>} />
        <Stat icon={<Trophy className="h-5 w-5" />} tone="amber" label={t('an.peakDay')} value={<span dir="ltr">{fmt.energy(a.best?.kwh ?? 0)}</span>} sub={a.best ? fmt.date(a.best.date) : ''} />
        <Stat icon={<Gauge className="h-5 w-5" />} tone="violet" label={t('an.perfRatio')} value={<span dir="ltr">{fmt.num(capacity ? a.avg / capacity : 0, 2)} kWh/kWp</span>} sub={t('an.avgDaily')} />
      </div>

      <Card title={t('an.trend')}>
        <div className="h-72" dir="ltr">
          <ResponsiveContainer>
            <AreaChart data={a.trend} margin={{ left: 0, right: 8, top: 8 }}>
              <defs><linearGradient id="an" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#3b74f6" stopOpacity={0.4} /><stop offset="1" stopColor="#3b74f6" stopOpacity={0} /></linearGradient></defs>
              <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
              <XAxis dataKey="x" tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} minTickGap={18} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={axisEnergy} width={64} />
              <Tooltip content={<ChartTooltip format={fmt.energy} />} />
              <Area dataKey="v" name={t('an.production.short')} stroke="#3b74f6" strokeWidth={2} fill="url(#an)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <div className="grid gap-4 xl:grid-cols-3">
        <Card title={t('an.bySite')} className="xl:col-span-2">
          <div style={{ height: Math.max(220, a.bySite.length * 30) }} dir="ltr">
            <ResponsiveContainer>
              <BarChart data={a.bySite} layout="vertical" margin={{ left: 8, right: 16 }}>
                <CartesianGrid horizontal={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis type="number" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={axisEnergy} width={64} />
                <YAxis type="category" dataKey="name" width={170} tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} />
                <Tooltip content={<ChartTooltip format={fmt.energy} />} cursor={{ fill: 'rgba(59,116,246,.06)' }} />
                <Bar dataKey="v" name={t('an.production.short')} fill="#3b74f6" radius={[0, 6, 6, 0]} barSize={16} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title={t('an.byType')}>
          <div className="h-64" dir="ltr">
            <ResponsiveContainer>
              <PieChart>
                <Pie data={a.byType} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={3}>
                  {a.byType.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip content={<ChartTooltip format={fmt.energy} />} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title={t('an.prodVsCons')}>
          <div className="h-64" dir="ltr">
            <ResponsiveContainer>
              <LineChart data={a.pvc} margin={{ left: 0, right: 8, top: 8 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} interval={3} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={axisPower} width={60} />
                <Tooltip content={<ChartTooltip format={fmt.power} />} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Line dataKey="prod" name={t('an.production.short')} stroke="#3b74f6" strokeWidth={2} dot={false} />
                <Line dataKey="cons" name={t('an.consumption.short')} stroke="#f59e0b" strokeWidth={2} dot={false} strokeDasharray="4 3" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title={t('an.heatmap')}>
          <div className="overflow-x-auto scrollbar-thin" dir="ltr">
            <div className="min-w-[520px]">
              <div className="ms-14 grid grid-cols-24 gap-[2px] text-[9px] text-slate-400" style={{ gridTemplateColumns: 'repeat(24, minmax(0, 1fr))' }}>
                {Array.from({ length: 24 }, (_, h) => <div key={h} className="text-center">{h % 3 === 0 ? h : ''}</div>)}
              </div>
              {a.heat.map((r) => (
                <div key={r.label} className="mt-[2px] flex items-center gap-1">
                  <div className="w-13 shrink-0 truncate text-[10px] text-slate-500">{r.label}</div>
                  <div className="grid flex-1 gap-[2px]" style={{ gridTemplateColumns: 'repeat(24, minmax(0, 1fr))' }}>
                    {r.cells.map((v, h) => (
                      <div key={h} title={`${r.label} ${h}:00 — ${fmt.power(v)}`} className="h-3.5 rounded-[3px]" style={{ background: v < 0.01 ? 'rgba(148,163,184,.12)' : `rgba(59,116,246,${0.12 + 0.88 * (v / a.heatMax)})` }} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Card>
      </div>

      <Card title={t('ov.envImpact')} id="impact">
        <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
          <div className="space-y-4">
            <Impact icon={<Leaf className="h-5 w-5" />} cls="bg-sky-100 text-sky-600" label={t('ov.co2Offset')} value={`${fmt.num(a.co2, 1)} ${t('common.tons')}`} />
            <Impact icon={<TreePine className="h-5 w-5" />} cls="bg-emerald-100 text-emerald-600" label={t('ov.trees')} value={`${fmt.num((a.co2 * 1000) / s.treeKgPerYear)} ${t('ov.trees.unit')}`} />
            <Impact icon={<Car className="h-5 w-5" />} cls="bg-amber-100 text-amber-600" label={t('ov.cars')} value={`${fmt.num(a.co2 / s.carTonsPerYear, 1)} ${t('ov.cars.unit')}`} />
          </div>
          <div className="h-60" dir="ltr">
            <ResponsiveContainer>
              <BarChart data={a.monthlyImpact} margin={{ left: 0, right: 8, top: 8 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} />
                <Tooltip content={<ChartTooltip format={(v) => `${fmt.num(v, 1)} t CO₂`} />} cursor={{ fill: 'rgba(34,197,94,.06)' }} />
                <Bar dataKey="co2" name="CO₂" fill="#22c55e" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </Card>
    </div>
  )
}

const Impact = ({ icon, cls, label, value }: { icon: React.ReactNode; cls: string; label: string; value: string }) => (
  <div className="flex items-center gap-3">
    <div className={`grid h-11 w-11 place-items-center rounded-xl ${cls}`}>{icon}</div>
    <div><div className="text-xs text-slate-500">{label}</div><div className="text-lg font-semibold">{value}</div></div>
  </div>
)
