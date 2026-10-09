import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Bar, CartesianGrid, Cell, ComposedChart, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Wallet, TrendingUp, Percent, Hourglass, PiggyBank, Gauge, ArrowRight } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { Card, ChartTooltip, PageHeader, Select, Stat } from '../components/ui'
import { analyzePortfolio, HORIZON } from '../lib/finance'
import { cn } from '../lib/utils'

const compactMoney = (v: number) => {
  const a = Math.abs(v)
  const s = a >= 1e6 ? `${+(a / 1e6).toFixed(1)}M` : a >= 1e3 ? `${Math.round(a / 1e3)}k` : String(Math.round(a))
  return v < 0 ? `−${s}` : s
}

export default function Finance() {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const [params, setParams] = useSearchParams()
  const rate = db.settings.discountRatePct
  const { perSite, portfolio } = useMemo(() => analyzePortfolio(db.sites, rate), [db.sites, rate])
  const siteId = params.get('site')
  const current = perSite.find((x) => x.site.id === siteId)
  const r = current?.result ?? portfolio
  const select = (id: string) => {
    const p = new URLSearchParams(params)
    if (id === 'all') p.delete('site')
    else p.set('site', id)
    setParams(p, { replace: true })
  }

  const years = (v: number | null) => (v == null ? t('fin.never') : t('fin.years', { n: fmt.num(v, 1) }))
  const chart = r.series.map((p) => ({ x: p.year, cf: p.cashFlow, cum: p.cumulative }))
  const breakEven = r.paybackYears == null ? null : Math.ceil(r.paybackYears)
  const rows = [...perSite].sort((a, b) => (b.result.irrPct ?? -1e9) - (a.result.irrPct ?? -1e9))
  const sites = current ? [current.site] : db.sites
  const avg = (f: (s: (typeof sites)[number]) => number) => sites.reduce((s, x) => s + f(x), 0) / (sites.length || 1)

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('fin.title')}
        subtitle={t('fin.subtitle')}
        actions={
          <div className="w-64">
            <Select value={current ? current.site.id : 'all'} onChange={(e) => select(e.target.value)}>
              <option value="all">{t('fin.portfolio')}</option>
              {db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        <Stat icon={<Wallet className="h-5 w-5" />} label={t('fin.investment')} value={<span dir="ltr">{fmt.money(r.systemCost)}</span>} sub={current ? current.site.name : `${db.sites.length} ${t('rep.sites')}`} />
        <Stat icon={<TrendingUp className="h-5 w-5" />} tone={r.npv >= 0 ? 'green' : 'red'} label={t('fin.npv')} value={<span dir="ltr">{fmt.money(r.npv)}</span>} sub={`${t('fin.discountRate')} ${fmt.pct(rate)}`} />
        <Stat icon={<Percent className="h-5 w-5" />} tone="violet" label={t('fin.irr')} value={<span dir="ltr">{r.irrPct == null ? '—' : fmt.pct(r.irrPct)}</span>} />
        <Stat icon={<Hourglass className="h-5 w-5" />} tone="amber" label={t('fin.payback')} value={years(r.paybackYears)} sub={breakEven != null ? t('fin.breakEven', { n: breakEven }) : undefined} />
        <Stat icon={<PiggyBank className="h-5 w-5" />} tone="green" label={t('fin.roi')} value={<span dir="ltr">{fmt.pct(r.roiPct, 0)}</span>} sub={<span dir="ltr">{fmt.money(r.year1Savings)} / {t('fin.yr1')}</span>} />
        <Stat icon={<Gauge className="h-5 w-5" />} tone="blue" label={t('fin.lcoe')} value={<span dir="ltr">{fmt.money2(r.lcoe)}</span>} sub={<span dir="ltr">{t('fin.perKwh')}</span>} />
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2" title={t('fin.cashFlow')}>
          <div className="h-72" dir="ltr">
            <ResponsiveContainer>
              <ComposedChart data={chart} margin={{ left: 0, right: 8, top: 8 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
                <XAxis dataKey="x" tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} interval={4} />
                <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={compactMoney} width={56} />
                <Tooltip content={<ChartTooltip format={fmt.money} labelFormat={(l) => t('fin.year', { n: l })} />} cursor={{ fill: 'rgba(59,116,246,.06)' }} />
                <ReferenceLine y={0} stroke="#94a3b8" />
                {breakEven != null && <ReferenceLine x={breakEven} stroke="#22c55e" strokeDasharray="4 4" />}
                <Bar dataKey="cf" name={t('fin.annualCf')} radius={[3, 3, 0, 0]} maxBarSize={18}>
                  {chart.map((p) => <Cell key={p.x} fill={p.cf >= 0 ? '#93b8fd' : '#fda4af'} />)}
                </Bar>
                <Line dataKey="cum" name={t('fin.cumulative')} type="monotone" stroke="#2557eb" strokeWidth={2.5} dot={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-slate-500">
            <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-[#93b8fd]" />{t('fin.annualCf')}</span>
            <span className="flex items-center gap-1.5"><span className="h-0.5 w-4 rounded bg-brand-600" />{t('fin.cumulative')}</span>
            {breakEven != null && <span className="flex items-center gap-1.5"><span className="h-3 w-0 border-s-2 border-dashed border-emerald-500" />{t('fin.breakEven', { n: breakEven })}</span>}
          </div>
        </Card>

        <Card title={t('fin.assumptions')} action={<Link to="/settings?tab=preferences" className="inline-flex items-center gap-1 text-xs text-brand-600 hover:underline">{t('fin.editRate')}<ArrowRight className="h-3 w-3 rtl:rotate-180" /></Link>}>
          <dl className="divide-y divide-slate-100 text-sm dark:divide-slate-700">
            {[
              [t('fin.horizon'), t('fin.years', { n: HORIZON })],
              [t('fin.discountRate'), fmt.pct(rate)],
              [t('fin.e1'), fmt.energy(r.e1)],
              [t('fin.savings'), fmt.money(r.year1Savings)],
              [t('sites.degradation'), fmt.pct(avg((s) => s.degradationPct), 2)],
              [t('sites.escalation'), fmt.pct(avg((s) => s.tariffEscalationPct), 2)],
              [t('sites.annualOpex'), fmt.money(sites.reduce((s, x) => s + x.annualOpex, 0))],
            ].map(([k, v]) => (
              <div key={k} className="flex items-center justify-between gap-3 py-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="font-medium" dir="ltr">{v}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-3 text-xs leading-relaxed text-slate-500">{t('fin.model')}</p>
        </Card>
      </div>

      <Card title={t('fin.perSite')}>
        <div className="-mx-4 -mb-4 overflow-x-auto scrollbar-thin">
          <table className="table-base">
            <thead>
              <tr>
                <th>{t('common.site')}</th><th>{t('map.capacity')}</th><th>{t('fin.investment')}</th><th>{t('fin.e1')}</th><th>{t('fin.savings')}</th>
                <th>{t('fin.payback')}</th><th>{t('fin.roi')}</th><th>{t('fin.npv')}</th><th>{t('fin.irr')}</th><th>{t('fin.lcoe')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ site, result: x }) => (
                <tr key={site.id} className={cn('cursor-pointer', site.id === siteId && '!bg-brand-50 dark:!bg-brand-500/15')} onClick={() => select(site.id === siteId ? 'all' : site.id)}>
                  <td className="max-w-56 truncate font-medium">{site.name}</td>
                  <td dir="ltr" className="text-start">{fmt.power(site.capacityKw)}</td>
                  <td dir="ltr" className="text-start">{fmt.money(x.systemCost)}</td>
                  <td dir="ltr" className="text-start">{fmt.energy(x.e1)}</td>
                  <td dir="ltr" className="text-start">{fmt.money(x.year1Savings)}</td>
                  <td>{years(x.paybackYears)}</td>
                  <td dir="ltr" className={cn('text-start font-medium', x.roiPct >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600')}>{fmt.pct(x.roiPct, 0)}</td>
                  <td dir="ltr" className={cn('text-start', x.npv < 0 && 'text-rose-600')}>{fmt.money(x.npv)}</td>
                  <td dir="ltr" className="text-start">{x.irrPct == null ? '—' : fmt.pct(x.irrPct)}</td>
                  <td dir="ltr" className="text-start">{fmt.money2(x.lcoe)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
