import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Zap, Sun, Cloud, CloudSun, CloudRain, CloudSnow, CloudFog, CloudLightning, Leaf, DollarSign, TreePine, Car, CircleDot, ChevronRight, ArrowUpRight, ArrowDownRight, BatteryCharging, UtilityPole, Plug } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { Card, ChartTooltip, PillSelect, Ring, Sparkline, StatusBadge } from '../components/ui'
import { co2Tons, dailySeries, deviceStats, energyFlow, hourlySeries, liveKw, siteDayKwh, siteDayKwhCached, siteKw, totalKwh } from '../lib/sim'
import { useWeather, weatherKind } from '../lib/weather'
import { addDays, axisEnergy, axisPower, cn, dayKey, pctChange } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const WX_ICON = { sunny: Sun, partly: CloudSun, cloudy: Cloud, fog: CloudFog, rain: CloudRain, snow: CloudSnow, storm: CloudLightning }

export default function Overview() {
  const { db, now } = useData()
  const { t, fmt } = useI18n()
  const { sites, devices, settings } = db

  const hour = new Date(now).getHours()
  const greet = hour < 12 ? t('greet.morning') : hour < 18 ? t('greet.afternoon') : t('greet.evening')
  const stats = useMemo(() => deviceStats(devices), [devices])
  const health = stats.availability >= 95 ? 'excellent' : stats.availability >= 85 ? 'good' : 'attention'

  const active = sites.filter((s) => s.status === 'active').length
  const idle = sites.filter((s) => s.status === 'idle').length
  const capacity = sites.reduce((s, x) => s + x.capacityKw, 0)
  const live = liveKw(sites, now)

  // month-to-date vs same span last month
  const kpis = useMemo(() => {
    const today = new Date()
    const mStart = new Date(today.getFullYear(), today.getMonth(), 1)
    const pStart = new Date(today.getFullYear(), today.getMonth() - 1, 1)
    const pEnd = new Date(today.getFullYear(), today.getMonth() - 1, Math.min(today.getDate(), new Date(today.getFullYear(), today.getMonth(), 0).getDate()))
    const mtd = totalKwh(sites, mStart, today)
    const prev = totalKwh(sites, pStart, pEnd)
    const revenue = (from: Date, to: Date) => sites.reduce((s, site) => s + totalKwh([site], from, to) * site.pricePerKwh, 0)
    const revM = revenue(mStart, today)
    const revP = revenue(pStart, pEnd)
    const todayKwh = totalKwh(sites, today, today)
    const ydKey = dayKey(addDays(today, -1))
    const hourNow = today.getHours() + today.getMinutes() / 60
    const ydSame = sites.reduce((s, x) => {
      let sum = 0
      for (let h = 0; h < hourNow; h += 0.25) sum += siteKw(x, ydKey, h + 0.125) * 0.25
      return s + sum
    }, 0)
    const last14 = dailySeries(sites, addDays(today, -13), today).map((d) => d.kwh)
    return {
      mtd, prev, revM, revP, todayKwh,
      todayChange: pctChange(todayKwh, ydSame),
      co2: co2Tons(mtd, settings.co2KgPerKwh),
      co2Change: pctChange(mtd, prev),
      spark: last14,
      revSpark: last14.map((v, i) => v * (0.95 + (i % 3) * 0.03)),
    }
    // recompute every minute, not every 5s tick
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sites, settings.co2KgPerKwh, Math.floor(now / 60000)])

  const liveSpark = useMemo(() => hourlySeries(sites, dayKey(new Date()), 60).filter((p) => p.kw !== null).map((p) => p.kw as number).slice(-10), [sites, Math.floor(now / 60000)]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="space-y-4">
      {/* HERO */}
      <section className="relative overflow-hidden rounded-3xl">
        <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1592833159155-c62df1b65634?auto=format&fit=crop&w=1800&q=70')] bg-cover bg-center" />
        <div className="absolute inset-0 bg-gradient-to-r from-white/95 via-white/70 to-white/0 rtl:bg-gradient-to-l dark:from-slate-900/95 dark:via-slate-900/70 dark:to-slate-900/10" />
        <div className="relative grid gap-4 p-5 lg:grid-cols-[1fr_auto] lg:p-6">
          <div className="flex flex-col justify-between gap-6">
            <div>
              <p className="text-sm text-slate-600 dark:text-slate-300">{greet}, {settings.userName.split(' ')[0]} 👋</p>
              <h1 className="mt-1 max-w-md text-2xl font-semibold leading-tight text-slate-900 sm:text-3xl dark:text-white">
                {t('hero.title1')} {t('hero.title2')}{' '}
                <span className={cn(health === 'attention' ? 'text-amber-500' : 'text-brand-600 dark:text-brand-400')}>{t(`hero.${health}` as DictKey)}</span>
              </h1>
            </div>
            <Link to="/sites" className="card flex w-full max-w-sm items-center gap-4 !p-4 transition hover:shadow-md">
              <div className="flex-1">
                <div className="text-xs text-slate-500">{t('ov.totalSites')}</div>
                <div className="text-3xl font-semibold">{sites.length}</div>
                <div className="mt-2 flex gap-4 text-xs">
                  <div><span className="me-1 inline-block h-2 w-2 rounded-full bg-emerald-500" />{t('ov.activeSites')} <b className="ms-1">{active}</b></div>
                  <div><span className="me-1 inline-block h-2 w-2 rounded-full bg-amber-400" />{t('ov.idleSites')} <b className="ms-1">{idle}</b></div>
                </div>
              </div>
              <Ring value={(live / capacity) * 100} size={88} stroke={7} color="#3b74f6">
                <div className="leading-tight">
                  <div className="text-[9px] text-slate-500">{t('ov.totalCapacity')}</div>
                  <div className="text-sm font-bold">{fmt.num(capacity / 1000, 2)}</div>
                  <div className="text-[9px] text-slate-500">MWp</div>
                </div>
              </Ring>
            </Link>
          </div>
          <WeatherCard />
        </div>
      </section>

      {/* KPI ROW */}
      <section className="card grid grid-cols-1 gap-4 !p-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={<Zap className="h-4 w-4" />} tone="blue" label={t('ov.currentPower')} value={fmt.power(live)} sub={<span className="flex items-center gap-1 text-slate-500"><span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" /><span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" /></span>{t('ov.liveOutput')}</span>} spark={liveSpark} color="#3b74f6" />
        <Kpi icon={<Sun className="h-4 w-4" />} tone="amber" label={t('ov.energyToday')} value={fmt.energy(kpis.todayKwh)} change={kpis.todayChange} changeLabel={t('common.vsYesterday')} spark={kpis.spark} color="#f59e0b" />
        <Kpi icon={<Leaf className="h-4 w-4" />} tone="sky" label={t('ov.co2Offset')} value={`${fmt.num(kpis.co2, 1)} ${t('common.tons')}`} change={kpis.co2Change} spark={kpis.spark} color="#0ea5e9" />
        <Kpi icon={<DollarSign className="h-4 w-4" />} tone="green" label={t('ov.totalRevenue')} value={fmt.money(kpis.revM)} change={pctChange(kpis.revM, kpis.revP)} spark={kpis.revSpark} color="#22c55e" />
      </section>

      {/* ROW 2 */}
      <section className="grid gap-4 xl:grid-cols-12">
        <EnergyGeneration className="xl:col-span-5" />
        <EnvironmentalImpact className="xl:col-span-3" />
        <PerformanceHealth className="xl:col-span-4" stats={stats} />
      </section>

      {/* ROW 3 */}
      <section className="grid gap-4 xl:grid-cols-12">
        <SitePerformance className="xl:col-span-5" />
        <EnergyFlowCard className="xl:col-span-7" />
      </section>
    </div>
  )
}

function Kpi({ icon, label, value, change, changeLabel, sub, spark, color, tone }: { icon: React.ReactNode; label: string; value: string; change?: number; changeLabel?: string; sub?: React.ReactNode; spark: number[]; color: string; tone: 'blue' | 'amber' | 'sky' | 'green' }) {
  const { t, fmt } = useI18n()
  const bg = { blue: 'bg-blue-100 text-blue-600', amber: 'bg-amber-100 text-amber-600', sky: 'bg-sky-100 text-sky-600', green: 'bg-emerald-100 text-emerald-600' }[tone]
  return (
    <div className="flex items-center gap-3 rounded-xl p-2 sm:border-e sm:border-slate-100 last:border-0 dark:sm:border-slate-700">
      <div className={cn('grid h-9 w-9 shrink-0 place-items-center self-start rounded-xl dark:bg-white/10', bg)}>{icon}</div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-xs text-slate-500">{label}</div>
        <div className="truncate text-xl font-semibold" dir="ltr" style={{ textAlign: 'start' }}>{value}</div>
        <div className="mt-0.5 truncate text-[11px]">
          {change !== undefined ? (
            <span><span className={change >= 0 ? 'text-emerald-600' : 'text-rose-600'} dir="ltr">{fmt.signedPct(change)}</span> <span className="text-slate-500">{changeLabel ?? t('common.vsLastMonth')}</span></span>
          ) : sub}
        </div>
      </div>
      <Sparkline data={spark} color={color} width={72} height={30} />
    </div>
  )
}

function WeatherCard() {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const w = useWeather(db.settings.lat, db.settings.lng)
  const kind = weatherKind(w.code)
  const Icon = WX_ICON[kind]
  return (
    <div className="card w-full self-start !p-4 lg:w-64">
      <div className="text-xs font-medium text-slate-500">{fmt.dateLong(new Date())}</div>
      <div className="mt-1 text-xs text-slate-400">{db.settings.city}</div>
      <div className="mt-2 flex items-center gap-3">
        <Icon className={cn('h-10 w-10', kind === 'sunny' ? 'text-amber-400' : 'text-sky-500')} />
        <div>
          <div className="text-2xl font-semibold" dir="ltr">{w.temp}°C</div>
          <div className="text-xs text-slate-500">{t(`wx.${kind}` as DictKey)}</div>
        </div>
      </div>
      <div className="mt-3 space-y-1.5 border-t border-slate-100 pt-3 text-xs dark:border-slate-700">
        <Row label={t('wx.irradiance')} value={`${w.irradiance} W/m²`} />
        <Row label={t('wx.wind')} value={`${w.wind} km/h`} />
        <Row label={t('wx.humidity')} value={`${w.humidity}%`} />
      </div>
    </div>
  )
}

const Row = ({ label, value }: { label: string; value: string }) => (
  <div className="flex justify-between gap-2">
    <span className="text-slate-500">{label}</span>
    <span className="font-semibold" dir="ltr">{value}</span>
  </div>
)

function EnergyGeneration({ className }: { className?: string }) {
  const { db, now } = useData()
  const { t, fmt } = useI18n()
  const [period, setPeriod] = useState<'daily' | 'weekly' | 'monthly'>('daily')
  const minute = Math.floor(now / 60000)

  const { data, total, prevTotal, peak, isHourly } = useMemo(() => {
    const today = new Date()
    if (period === 'daily') {
      const d = hourlySeries(db.sites, dayKey(today), 30).map((p) => ({ x: p.label, v: p.kw }))
      const total = totalKwh(db.sites, today, today)
      const yd = dayKey(addDays(today, -1))
      const h = today.getHours() + today.getMinutes() / 60
      const prevTotal = db.sites.reduce((s, x) => s + siteDayKwh(x, yd, h), 0)
      const peak = d.reduce((m, p) => ((p.v ?? 0) > (m.v ?? 0) ? p : m), d[0])
      return { data: d, total, prevTotal, peak, isHourly: true }
    }
    const days = period === 'weekly' ? 7 : 30
    const series = dailySeries(db.sites, addDays(today, -(days - 1)), today)
    const prev = dailySeries(db.sites, addDays(today, -(2 * days - 1)), addDays(today, -days))
    const d = series.map((p) => ({ x: fmt.dateShort(p.date), v: p.kwh }))
    const peak = d.reduce((m, p) => (p.v > m.v ? p : m), d[0])
    return { data: d, total: series.reduce((s, p) => s + p.kwh, 0), prevTotal: prev.reduce((s, p) => s + p.kwh, 0), peak, isHourly: false }
  }, [db.sites, period, minute, fmt])

  const change = pctChange(total, prevTotal)
  const yFmt = (v: number) => (isHourly ? fmt.power(v) : fmt.energy(v))

  return (
    <Card
      className={className}
      title={t('ov.energyGeneration')}
      action={<PillSelect value={period} onChange={setPeriod} options={[{ value: 'daily', label: t('common.daily') }, { value: 'weekly', label: t('common.weekly') }, { value: 'monthly', label: t('common.monthly') }]} />}
    >
      <div className="text-3xl font-semibold" dir="ltr" style={{ textAlign: 'start' }}>{fmt.energy(total)}</div>
      <div className="mt-1 text-xs text-slate-500">{t('ov.totalEnergy')}</div>
      <div className="text-xs"><span className={change >= 0 ? 'text-emerald-600' : 'text-rose-600'} dir="ltr">{fmt.signedPct(change)}</span> <span className="text-slate-500">{period === 'daily' ? t('common.vsYesterday') : t('common.vsPrevPeriod')}</span></div>
      <div className="mt-2 h-48" dir="ltr">
        <ResponsiveContainer>
          <AreaChart data={data} margin={{ top: 24, right: 18, left: -8, bottom: 0 }}>
            <defs>
              <linearGradient id="eg" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="#3b74f6" stopOpacity={0.45} />
                <stop offset="1" stopColor="#3b74f6" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
            <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} interval={isHourly ? 11 : 'preserveStartEnd'} minTickGap={20} />
            <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} width={64} tickFormatter={isHourly ? axisPower : axisEnergy} />
            <Tooltip content={<ChartTooltip format={yFmt} />} />
            <Area
              type="monotone"
              dataKey="v"
              name={t('an.production.short')}
              stroke="#3b74f6"
              strokeWidth={2}
              fill="url(#eg)"
              connectNulls={false}
              isAnimationActive={false}
              dot={(p: { cx?: number; cy?: number; index: number; payload: { x: string } }) =>
                p.payload.x === peak?.x && p.cx != null && p.cy != null ? (
                  <g key="peak">
                    <line x1={p.cx} x2={p.cx} y1={p.cy} y2={170} stroke="#3b74f6" strokeDasharray="3 3" opacity={0.5} />
                    <rect x={p.cx - 34} y={p.cy - 34} width={68} height={22} rx={6} fill="#fff" stroke="#e2e8f0" />
                    <text x={p.cx} y={p.cy - 19} textAnchor="middle" fontSize={10} fontWeight={600} fill="#1e293b">{yFmt(peak.v ?? 0)}</text>
                    <circle cx={p.cx} cy={p.cy} r={5} fill="#fff" stroke="#3b74f6" strokeWidth={2.5} />
                  </g>
                ) : (
                  <g key={p.index} />
                )
              }
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Card>
  )
}

function EnvironmentalImpact({ className }: { className?: string }) {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const [period, setPeriod] = useState<'monthly' | 'yearly' | 'all'>('monthly')
  const s = db.settings
  const v = useMemo(() => {
    const today = new Date()
    const from = period === 'monthly' ? new Date(today.getFullYear(), today.getMonth(), 1) : period === 'yearly' ? new Date(today.getFullYear(), 0, 1) : new Date(Math.min(...db.sites.map((x) => new Date(x.installDate).getTime())))
    const kwh = totalKwh(db.sites, from, today)
    const co2 = co2Tons(kwh, s.co2KgPerKwh)
    return { co2, trees: (co2 * 1000) / s.treeKgPerYear, cars: co2 / s.carTonsPerYear }
  }, [db.sites, period, s.co2KgPerKwh, s.treeKgPerYear, s.carTonsPerYear])
  const items = [
    { icon: TreePine, bg: 'bg-emerald-100 text-emerald-600', label: t('ov.trees'), value: fmt.num(v.trees), unit: t('ov.trees.unit') },
    { icon: Leaf, bg: 'bg-sky-100 text-sky-600', label: t('ov.co2Offset'), value: fmt.num(v.co2, 1), unit: t('common.tons') },
    { icon: Car, bg: 'bg-amber-100 text-amber-600', label: t('ov.cars'), value: fmt.num(v.cars, 1), unit: t('ov.cars.unit') },
  ]
  return (
    <Card className={className} title={t('ov.envImpact')} action={<PillSelect value={period} onChange={setPeriod} options={[{ value: 'monthly', label: t('common.monthly') }, { value: 'yearly', label: t('common.yearly') }, { value: 'all', label: t('common.all') }]} />}>
      <div className="space-y-4">
        {items.map((it) => (
          <div key={it.label} className="flex items-center gap-3">
            <div className={cn('grid h-10 w-10 shrink-0 place-items-center rounded-xl dark:bg-white/10', it.bg)}><it.icon className="h-5 w-5" /></div>
            <div className="min-w-0">
              <div className="truncate text-xs text-slate-500">{it.label}</div>
              <div className="text-lg font-semibold">{it.value} <span className="text-xs font-normal text-slate-500">{it.unit}</span></div>
            </div>
          </div>
        ))}
      </div>
    </Card>
  )
}

function PerformanceHealth({ className, stats }: { className?: string; stats: ReturnType<typeof deviceStats> }) {
  const { t, fmt } = useI18n()
  const label = (v: number) => (v >= 97 ? t('ov.excellent') : v >= 90 ? t('ov.good') : v >= 75 ? t('ov.fair') : t('ov.poor'))
  const color = (v: number) => (v >= 90 ? '#22c55e' : v >= 75 ? '#f59e0b' : '#ef4444')
  const items = [
    { label: t('ov.availability'), v: stats.availability },
    { label: t('ov.inverterEff'), v: stats.inverterEfficiency },
    { label: t('ov.batteryHealth'), v: stats.batteryHealth },
  ]
  return (
    <Card className={className} title={t('ov.perfHealth')} action={<Link to="/devices" className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-slate-600 hover:border-brand-300 dark:border-slate-600 dark:text-slate-300">{t('common.viewAll')} <ChevronRight className="h-3 w-3 rtl:rotate-180" /></Link>}>
      <div className="space-y-4">
        {items.map((it) => (
          <div key={it.label} className="flex items-center gap-3">
            <Ring value={it.v} size={46} stroke={4} color={color(it.v)}>
              <span className="text-[10px] font-semibold" dir="ltr">{fmt.pct(it.v)}</span>
            </Ring>
            <div>
              <div className="text-sm font-medium">{it.label}</div>
              <div className="text-xs text-slate-500">{label(it.v)}</div>
            </div>
          </div>
        ))}
        <div className="flex gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500 dark:border-slate-700">
          <span><CircleDot className="me-1 inline h-3 w-3 text-emerald-500" />{t('status.online')} {stats.online}</span>
          <span><CircleDot className="me-1 inline h-3 w-3 text-amber-500" />{t('status.warning')} {stats.warning}</span>
          <span><CircleDot className="me-1 inline h-3 w-3 text-rose-500" />{t('status.offline')} {stats.offline}</span>
        </div>
      </div>
    </Card>
  )
}

function SitePerformance({ className }: { className?: string }) {
  const { db, now } = useData()
  const { t, fmt } = useI18n()
  const navigate = useNavigate()
  const day = dayKey(new Date(now))
  const rows = useMemo(
    () =>
      [...db.sites]
        .map((s) => ({
          s,
          kw: siteKw(s, day, new Date(now).getHours() + new Date(now).getMinutes() / 60),
          kwh: siteDayKwhCached(s, day),
          spark: hourlySeries([s], day, 60).filter((p) => p.kw !== null && p.hour >= 6).map((p) => p.kw as number),
        }))
        .sort((a, b) => b.kwh - a.kwh)
        .slice(0, 6),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [db.sites, day, Math.floor(now / 60000)],
  )
  return (
    <Card className={className} title={t('ov.sitePerformance')} action={<Link to="/sites" className="text-xs text-brand-600 hover:underline">{t('common.viewAll')}</Link>}>
      <div className="-mx-4 overflow-x-auto scrollbar-thin">
        <table className="table-base">
          <thead>
            <tr><th>{t('ov.siteName')}</th><th>{t('ov.currentPower')}</th><th /><th>{t('ov.energyToday')}</th><th>{t('common.status')}</th></tr>
          </thead>
          <tbody>
            {rows.map(({ s, kw, kwh, spark }) => (
              <tr key={s.id} className="cursor-pointer" onClick={() => navigate(`/sites/${s.id}`)}>
                <td className="max-w-40 truncate font-medium">{s.name}</td>
                <td dir="ltr" className="text-start">{fmt.power(kw)}</td>
                <td><Sparkline data={spark} width={56} height={20} color={s.status === 'active' ? '#3b74f6' : '#94a3b8'} /></td>
                <td dir="ltr" className="text-start">{fmt.energy(kwh)}</td>
                <td><StatusBadge status={s.status} label={t(`status.${s.status}` as DictKey)} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

function EnergyFlowCard({ className }: { className?: string }) {
  const { db, now } = useData()
  const { t, fmt } = useI18n()
  const f = energyFlow(db.sites, now)
  const gridIn = f.grid >= 0
  const batCharging = f.battery >= 0
  const node = (pos: string, icon: React.ReactNode, label: string, value: string, bg: string) => (
    <div className={cn('absolute z-10 flex items-center gap-2 rounded-xl bg-white/95 px-2.5 py-2 shadow-md ring-1 ring-slate-100 dark:bg-slate-800/95 dark:ring-slate-700', pos)}>
      <div className={cn('grid h-8 w-8 place-items-center rounded-lg', bg)}>{icon}</div>
      <div className="leading-tight">
        <div className="text-[10px] text-slate-500">{label}</div>
        <div className="text-sm font-semibold" dir="ltr">{value}</div>
      </div>
    </div>
  )
  return (
    <Card className={className} title={t('ov.energyFlow')}>
      <div className="relative mx-auto h-72 w-full max-w-2xl" dir="ltr">
        <svg viewBox="0 0 600 280" className="absolute inset-0 h-full w-full" preserveAspectRatio="none">
          <g fill="none" strokeWidth="2.5" strokeLinecap="round">
            {/* solar → house */}
            <path d="M120 55 C 220 55, 230 120, 285 130" stroke="#3b74f6" className={cn(f.solar > 0.5 && 'flow-line')} opacity={f.solar > 0.5 ? 1 : 0.25} />
            {/* grid ↔ house */}
            <path d="M480 55 C 380 55, 370 120, 315 130" stroke="#8b5cf6" className={cn(Math.abs(f.grid) > 0.5 && 'flow-line', !gridIn && 'reverse')} opacity={Math.abs(f.grid) > 0.5 ? 1 : 0.25} />
            {/* battery ↔ house */}
            <path d="M120 230 C 220 230, 230 170, 285 160" stroke="#22c55e" className={cn(Math.abs(f.battery) > 0.5 && 'flow-line', batCharging && 'reverse')} opacity={Math.abs(f.battery) > 0.5 ? 1 : 0.25} />
            {/* house → consumption */}
            <path d="M315 160 C 370 170, 380 230, 480 230" stroke="#f59e0b" className="flow-line" />
          </g>
        </svg>
        <House />
        {node('left-0 top-6', <Sun className="h-4 w-4" />, t('ov.solarGen'), fmt.power(f.solar), 'bg-blue-100 text-blue-600')}
        {node('right-0 top-6', <UtilityPole className="h-4 w-4" />, gridIn ? t('ov.gridImport') : t('ov.gridExport'), fmt.power(Math.abs(f.grid)), 'bg-violet-100 text-violet-600')}
        {node('left-0 bottom-6', <BatteryCharging className="h-4 w-4" />, t('ov.batteryStorage'), `${batCharging ? '+' : '−'}${fmt.power(Math.abs(f.battery))} · ${Math.round(f.batterySoc)}%`, 'bg-emerald-100 text-emerald-600')}
        {node('right-0 bottom-6', <Plug className="h-4 w-4" />, t('ov.consumption'), fmt.power(f.consumption), 'bg-amber-100 text-amber-600')}
      </div>
      <div className="mt-2 flex flex-wrap justify-center gap-4 text-xs text-slate-500">
        <span className="flex items-center gap-1">{batCharging ? <ArrowUpRight className="h-3.5 w-3.5 text-emerald-500" /> : <ArrowDownRight className="h-3.5 w-3.5 text-amber-500" />}{batCharging ? t('ov.charging') : t('ov.discharging')}</span>
        <span>{t('ov.batteryStorage')}: <b dir="ltr">{fmt.energy((f.batteryCapacity * f.batterySoc) / 100)}</b> / <span dir="ltr">{fmt.energy(f.batteryCapacity)}</span></span>
      </div>
    </Card>
  )
}

/** Stylised isometric house with a rooftop solar array. */
function House() {
  // roof rhombus: top T, right R, bottom B, left L
  const T = [100, 40], R = [182, 82], L = [18, 82]
  const u = [(R[0] - T[0]) / 5, (R[1] - T[1]) / 5]
  const v = [(L[0] - T[0]) / 4, (L[1] - T[1]) / 4]
  const pt = (i: number, j: number) => [T[0] + u[0] * i + v[0] * j, T[1] + u[1] * i + v[1] * j]
  const panels: string[] = []
  for (let i = 0; i < 5; i++)
    for (let j = 0; j < 4; j++) {
      const g = 0.08
      const a = pt(i + g, j + g), b = pt(i + 1 - g, j + g), c = pt(i + 1 - g, j + 1 - g), d = pt(i + g, j + 1 - g)
      panels.push(`${a} ${b} ${c} ${d}`)
    }
  return (
    <svg viewBox="0 0 200 175" className="absolute left-1/2 top-1/2 h-48 w-56 -translate-x-1/2 -translate-y-1/2 drop-shadow-xl">
      <ellipse cx="100" cy="152" rx="92" ry="18" fill="rgba(59,116,246,.12)" />
      {/* walls */}
      <polygon points="18,82 100,124 100,158 18,116" fill="#e2e8f0" />
      <polygon points="100,124 182,82 182,116 100,158" fill="#f8fafc" />
      {/* glass facades */}
      <polygon points="108,124 174,90 174,110 108,144" fill="#93c5fd" opacity=".9" />
      <polygon points="26,88 92,121 92,141 26,108" fill="#bfdbfe" opacity=".8" />
      <line x1="141" y1="107" x2="141" y2="127" stroke="#f8fafc" strokeWidth="1.5" />
      <line x1="59" y1="104" x2="59" y2="124" stroke="#e2e8f0" strokeWidth="1.5" />
      {/* roof slab */}
      <polygon points="100,36 186,80 100,124 14,80" fill="#cbd5e1" />
      <polygon points={`${T} ${R} 100,124 ${L}`} fill="#f1f5f9" />
      {/* panels */}
      <defs>
        <linearGradient id="pv" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#1e40af" />
          <stop offset="1" stopColor="#1e3a8a" />
        </linearGradient>
      </defs>
      {panels.map((p, i) => <polygon key={i} points={p} fill="url(#pv)" stroke="#93c5fd" strokeWidth=".5" />)}
    </svg>
  )
}
