import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ArrowLeft, Pencil, Trash2, MapPin, Zap, Sun, CalendarDays, Gauge } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { Card, ChartTooltip, ConfirmDialog, EmptyState, Select, Stat, StatusBadge } from '../components/ui'
import SiteForm from '../components/SiteForm'
import { dailySeries, hourlySeries, nowHour, siteKw, totalKwh } from '../lib/sim'
import { addDays, axisEnergy, axisPower, dayKey } from '../lib/utils'
import type { DictKey } from '../i18n/en'
import type { Site } from '../lib/types'

export default function SiteDetail() {
  const { id } = useParams()
  const { db, now, upsert, removeSite, notify } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const [edit, setEdit] = useState(false)
  const [del, setDel] = useState(false)
  const site = db.sites.find((s) => s.id === id)
  const minute = Math.floor(now / 60000)

  const data = useMemo(() => {
    if (!site) return null
    const today = new Date()
    const day = dayKey(today)
    return {
      kw: siteKw(site, day, nowHour()),
      todayKwh: totalKwh([site], today, today),
      monthKwh: totalKwh([site], new Date(today.getFullYear(), today.getMonth(), 1), today),
      hourly: hourlySeries([site], day, 30).map((p) => ({ x: p.label, v: p.kw })),
      daily: dailySeries([site], addDays(today, -29), today).map((p) => ({ x: fmt.dateShort(p.date), v: p.kwh })),
      last30: totalKwh([site], addDays(today, -29), today),
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [site, minute, fmt])

  if (!site || !data)
    return (
      <div className="card">
        <EmptyState text={t('sites.notFound')} />
        <div className="text-center"><Link to="/sites" className="btn btn-ghost">{t('common.back')}</Link></div>
      </div>
    )

  const devices = db.devices.filter((d) => d.siteId === site.id)
  const tickets = db.tickets.filter((d) => d.siteId === site.id)
  const invoices = db.invoices.filter((d) => d.siteId === site.id).sort((a, b) => b.period.localeCompare(a.period))

  const setStatus = async (status: Site['status']) => {
    await upsert('sites', { ...site, status })
    if (status === 'offline') await notify({ title: t('status.offline'), body: site.name, kind: 'danger', link: `/sites/${site.id}` })
    toast(t('common.saved'))
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <button className="icon-btn" onClick={() => navigate('/sites')}><ArrowLeft className="h-4 w-4 rtl:rotate-180" /></button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-2xl font-semibold">{site.name}</h1>
          <div className="mt-0.5 flex flex-wrap items-center gap-2 text-sm text-slate-500"><MapPin className="h-3.5 w-3.5" />{site.location} · {t(`siteType.${site.type}` as DictKey)} · {site.customer}</div>
        </div>
        <div className="w-40"><Select value={site.status} onChange={(e) => setStatus(e.target.value as Site['status'])}>{(['active', 'idle', 'offline', 'maintenance'] as const).map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}</Select></div>
        <button className="btn btn-ghost" onClick={() => setEdit(true)}><Pencil className="h-4 w-4" />{t('common.edit')}</button>
        <button className="btn btn-ghost !text-rose-600" onClick={() => setDel(true)}><Trash2 className="h-4 w-4" />{t('common.delete')}</button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={<Zap className="h-5 w-5" />} label={t('sites.currentPower')} value={<span dir="ltr">{fmt.power(data.kw)}</span>} sub={<span dir="ltr">{fmt.pct((data.kw / site.capacityKw) * 100)} / {fmt.power(site.capacityKw)}</span>} />
        <Stat icon={<Sun className="h-5 w-5" />} tone="amber" label={t('sites.energyToday')} value={<span dir="ltr">{fmt.energy(data.todayKwh)}</span>} sub={<span dir="ltr">{fmt.money2(data.todayKwh * site.pricePerKwh)}</span>} />
        <Stat icon={<CalendarDays className="h-5 w-5" />} tone="green" label={t('sites.thisMonth')} value={<span dir="ltr">{fmt.energy(data.monthKwh)}</span>} sub={<span dir="ltr">{fmt.money(data.monthKwh * site.pricePerKwh)}</span>} />
        <Stat icon={<Gauge className="h-5 w-5" />} tone="violet" label={t('sites.yield')} value={<span dir="ltr">{fmt.num(data.last30 / 30 / site.capacityKw, 2)} kWh/kWp</span>} sub={t('an.avgDaily')} />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title={t('sites.todayCurve')}>
          <div className="h-56" dir="ltr">
            <ResponsiveContainer>
              <AreaChart data={data.hourly} margin={{ left: -10, right: 8, top: 8 }}>
                <defs><linearGradient id="sd" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#3b74f6" stopOpacity={0.4} /><stop offset="1" stopColor="#3b74f6" stopOpacity={0} /></linearGradient></defs>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} interval={7} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={axisPower} width={60} />
                <Tooltip content={<ChartTooltip format={fmt.power} />} />
                <Area dataKey="v" name={t('ov.currentPower')} stroke="#3b74f6" fill="url(#sd)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title={t('sites.last30')}>
          <div className="h-56" dir="ltr">
            <ResponsiveContainer>
              <BarChart data={data.daily} margin={{ left: -10, right: 8, top: 8 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis dataKey="x" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} minTickGap={16} />
                <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={axisEnergy} width={64} />
                <Tooltip content={<ChartTooltip format={fmt.energy} />} cursor={{ fill: 'rgba(59,116,246,.06)' }} />
                <Bar dataKey="v" name={t('an.production.short')} fill="#3b74f6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <Card title={`${t('sites.devices')} (${devices.length})`} action={<Link to={`/devices?site=${site.id}`} className="text-xs text-brand-600 hover:underline">{t('common.viewAll')}</Link>}>
          {devices.length === 0 ? <EmptyState text={t('common.noData')} /> : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-700">
              {devices.map((d) => (
                <li key={d.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <div className="min-w-0"><div className="font-medium">{d.name}</div><div className="truncate text-xs text-slate-500">{t(`devType.${d.type}` as DictKey)} · {d.model}</div></div>
                  <StatusBadge status={d.status} label={t(`status.${d.status}` as DictKey)} />
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title={`${t('sites.tickets')} (${tickets.length})`} action={<Link to={`/maintenance?site=${site.id}`} className="text-xs text-brand-600 hover:underline">{t('common.viewAll')}</Link>}>
          {tickets.length === 0 ? <EmptyState text={t('common.noData')} /> : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-700">
              {tickets.map((x) => (
                <li key={x.id}>
                  <Link to={`/maintenance?open=${x.id}`} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <div className="min-w-0"><div className="truncate font-medium">{x.title}</div><div className="text-xs text-slate-500">{fmt.date(x.dueDate)} · {x.assignee}</div></div>
                    <StatusBadge status={x.status} label={t(`status.${x.status}` as DictKey)} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title={`${t('sites.invoices')} (${invoices.length})`} action={<Link to={`/billing?site=${site.id}`} className="text-xs text-brand-600 hover:underline">{t('common.viewAll')}</Link>}>
          {invoices.length === 0 ? <EmptyState text={t('common.noData')} /> : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-700">
              {invoices.slice(0, 6).map((x) => (
                <li key={x.id}>
                  <Link to={`/billing?open=${x.id}`} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <div><div className="font-medium" dir="ltr">{x.number}</div><div className="text-xs text-slate-500" dir="ltr">{fmt.energy(x.energyKwh)} · {fmt.money2(x.amount)}</div></div>
                    <StatusBadge status={x.status} label={t(`status.${x.status}` as DictKey)} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <SiteForm open={edit} onClose={() => setEdit(false)} initial={site} onSave={async (s) => { await upsert('sites', s); toast(t('common.saved')); setEdit(false) }} />
      <ConfirmDialog open={del} onClose={() => setDel(false)} message={<>{t('common.deleteConfirm')}<br /><span className="mt-2 block text-rose-600">{t('sites.deleteWarn')}</span></>} onConfirm={async () => { await removeSite(site.id); toast(t('common.deleted'), 'info'); navigate('/sites') }} />
    </div>
  )
}
