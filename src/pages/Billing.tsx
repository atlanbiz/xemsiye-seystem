import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Legend } from 'recharts'
import { Receipt, CheckCircle2, Clock, AlertCircle, Download, Printer, FilePlus2, Trash2 } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { Card, ChartTooltip, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, SearchInput, Pager, Select, Stat, StatusBadge, usePaged } from '../components/ui'
import { Logo } from '../components/Layout'
import { buildInvoice } from '../lib/seed'
import type { Invoice } from '../lib/types'
import { addMonths, dayKey, downloadFile, monthKey, toCSV } from '../lib/utils'
import type { DictKey } from '../i18n/en'

export default function Billing() {
  const { db, upsert, upsertMany, remove, notify } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('all')
  const [period, setPeriod] = useState('all')
  const site = params.get('site') ?? 'all'
  const [open, setOpen] = useState<Invoice | null>(null)
  const [gen, setGen] = useState(false)
  const [genMonth, setGenMonth] = useState(monthKey(addMonths(new Date(), -1)))
  const [del, setDel] = useState<Invoice | null>(null)

  useEffect(() => {
    const id = params.get('open')
    if (id) {
      const inv = db.invoices.find((x) => x.id === id)
      if (inv) setOpen(inv)
      const p = new URLSearchParams(params)
      p.delete('open')
      setParams(p, { replace: true })
    }
  }, [params, db.invoices, setParams])

  const periods = useMemo(() => [...new Set(db.invoices.map((i) => i.period))].sort().reverse(), [db.invoices])
  const rows = useMemo(() => {
    const s = q.toLowerCase()
    return db.invoices
      .filter((i) => (status === 'all' || i.status === status) && (period === 'all' || i.period === period) && (site === 'all' || i.siteId === site))
      .filter((i) => !s || [i.number, i.customer].some((v) => v.toLowerCase().includes(s)))
      .sort((a, b) => b.period.localeCompare(a.period) || a.number.localeCompare(b.number))
  }, [db.invoices, q, status, period, site])

  const sum = (f: (i: Invoice) => boolean) => db.invoices.filter(f).reduce((s, i) => s + i.amount, 0)
  const totals = { all: sum(() => true), paid: sum((i) => i.status === 'paid'), pending: sum((i) => i.status === 'pending'), overdue: sum((i) => i.status === 'overdue') }

  const chart = useMemo(() => {
    const m = new Map<string, { x: string; paid: number; open: number }>()
    for (const p of [...periods].reverse()) {
      const [y, mm] = p.split('-').map(Number)
      m.set(p, { x: fmt.monthShort(new Date(y, mm - 1, 1)), paid: 0, open: 0 })
    }
    for (const i of db.invoices) {
      const e = m.get(i.period)
      if (!e) continue
      if (i.status === 'paid') e.paid += i.amount
      else e.open += i.amount
    }
    return [...m.values()]
  }, [db.invoices, periods, fmt])

  const markPaid = async (inv: Invoice) => {
    const next = { ...inv, status: 'paid' as const, paidAt: dayKey(new Date()) }
    await upsert('invoices', next)
    if (db.settings.notifyBilling) await notify({ title: t('bill.markedPaid'), body: `${inv.number} · ${inv.customer}`, kind: 'success', link: `/billing?open=${inv.id}` })
    setOpen((o) => (o?.id === inv.id ? next : o))
    toast(t('bill.markedPaid'))
  }

  const generate = async () => {
    const existing = new Set(db.invoices.filter((i) => i.period === genMonth).map((i) => i.siteId))
    const list = db.sites
      .filter((s) => !existing.has(s.id))
      .map((s, i) => buildInvoice(s, genMonth, existing.size + i + 1))
      .filter((i) => i.energyKwh > 0)
    if (!list.length) return toast(t('bill.alreadyExists'), 'warning')
    await upsertMany('invoices', list)
    if (db.settings.notifyBilling) await notify({ title: t('bill.generated', { n: list.length }), body: genMonth, kind: 'info', link: '/billing' })
    toast(t('bill.generated', { n: list.length }))
    setGen(false)
    setPeriod(genMonth)
  }

  const exportCsv = () =>
    downloadFile('invoices.csv', toCSV([['number', 'customer', 'period', 'energy_kwh', 'rate', 'amount', 'status', 'issued', 'due', 'paid'], ...rows.map((i) => [i.number, i.customer, i.period, i.energyKwh, i.rate, i.amount, i.status, i.issuedAt, i.dueAt, i.paidAt ?? ''])]), 'text/csv')

  const periodLabel = (p: string) => { const [y, m] = p.split('-').map(Number); return fmt.month(new Date(y, m - 1, 1)) }

  const paged = usePaged(rows)
  return (
    <div className="space-y-4">
      <PageHeader title={t('bill.title')} subtitle={t('bill.subtitle')} actions={<>
        <button className="btn btn-ghost" onClick={exportCsv}><Download className="h-4 w-4" />{t('common.export')}</button>
        <button className="btn btn-primary" onClick={() => setGen(true)}><FilePlus2 className="h-4 w-4" />{t('bill.generate')}</button>
      </>} />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={<Receipt className="h-5 w-5" />} label={t('bill.totalBilled')} value={<span dir="ltr">{fmt.money(totals.all)}</span>} sub={`${db.invoices.length} ${t('bill.invoice')}`} />
        <Stat icon={<CheckCircle2 className="h-5 w-5" />} tone="green" label={t('bill.paid')} value={<span dir="ltr">{fmt.money(totals.paid)}</span>} sub={fmt.pct((totals.paid / (totals.all || 1)) * 100)} />
        <Stat icon={<Clock className="h-5 w-5" />} tone="amber" label={t('bill.pending')} value={<span dir="ltr">{fmt.money(totals.pending)}</span>} />
        <Stat icon={<AlertCircle className="h-5 w-5" />} tone="red" label={t('bill.overdue')} value={<span dir="ltr">{fmt.money(totals.overdue)}</span>} />
      </div>

      <Card title={t('bill.revenueTrend')}>
        <div className="h-60" dir="ltr">
          <ResponsiveContainer>
            <BarChart data={chart} margin={{ left: 0, right: 8, top: 8 }}>
              <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="rgba(148,163,184,.25)" />
              <XAxis dataKey="x" tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false} tickFormatter={(v) => `${Math.round(v / 1000)}k`} />
              <Tooltip content={<ChartTooltip format={fmt.money} />} cursor={{ fill: 'rgba(59,116,246,.06)' }} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="paid" stackId="a" name={t('status.paid')} fill="#22c55e" />
              <Bar dataKey="open" stackId="a" name={t('status.pending')} fill="#f59e0b" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <div className="card flex flex-wrap items-center gap-2 !p-3">
        <SearchInput value={q} onChange={setQ} placeholder={t('common.search')} />
        <div className="w-44"><Select value={site} onChange={(e) => { const p = new URLSearchParams(params); if (e.target.value === 'all') p.delete('site'); else p.set('site', e.target.value); setParams(p) }}><option value="all">{t('an.allSites')}</option>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></div>
        <div className="w-40"><Select value={period} onChange={(e) => setPeriod(e.target.value)}><option value="all">{t('bill.period')}: {t('common.all')}</option>{periods.map((p) => <option key={p} value={p}>{periodLabel(p)}</option>)}</Select></div>
        <div className="w-40"><Select value={status} onChange={(e) => setStatus(e.target.value)}><option value="all">{t('common.status')}: {t('common.all')}</option>{(['paid', 'pending', 'overdue'] as const).map((x) => <option key={x} value={x}>{t(`status.${x}`)}</option>)}</Select></div>
        <div className="flex-1" />
        <span className="text-xs text-slate-500">{t('common.showing')} {rows.length} {t('common.of')} {db.invoices.length}</span>
      </div>

      <div className="card overflow-x-auto !p-0 scrollbar-thin">
        {rows.length === 0 ? <EmptyState text={t('common.noData')} /> : (
          <table className="table-base">
            <thead><tr><th>{t('bill.invoice')}</th><th>{t('bill.customer')}</th><th>{t('bill.period')}</th><th>{t('bill.energy')}</th><th>{t('bill.amount')}</th><th>{t('bill.due')}</th><th>{t('common.status')}</th><th /></tr></thead>
            <tbody>
              {paged.items.map((i) => (
                <tr key={i.id} className="cursor-pointer" onClick={() => setOpen(i)}>
                  <td className="font-medium" dir="ltr" style={{ textAlign: 'start' }}>{i.number}</td>
                  <td className="max-w-48 truncate">{i.customer}</td>
                  <td>{periodLabel(i.period)}</td>
                  <td dir="ltr" className="text-start">{fmt.energy(i.energyKwh)}</td>
                  <td dir="ltr" className="text-start font-semibold">{fmt.money2(i.amount)}</td>
                  <td>{fmt.date(i.dueAt)}</td>
                  <td><StatusBadge status={i.status} label={t(`status.${i.status}` as DictKey)} /></td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <div className="flex gap-1">
                      {i.status !== 'paid' && <button className="btn btn-ghost !px-2 !py-1 text-xs" onClick={() => markPaid(i)}><CheckCircle2 className="h-3.5 w-3.5" />{t('bill.markPaid')}</button>}
                      <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => setDel(i)}><Trash2 className="h-3.5 w-3.5" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pager {...paged} />
      </div>

      <Modal open={!!open} onClose={() => setOpen(null)} size="lg" title={open ? <span dir="ltr">{open.number}</span> : ''}
        footer={open && <>
          <button className="btn btn-ghost" onClick={() => window.print()}><Printer className="h-4 w-4" />{t('common.print')}</button>
          {open.status !== 'paid' && <button className="btn btn-primary" onClick={() => markPaid(open)}><CheckCircle2 className="h-4 w-4" />{t('bill.markPaid')}</button>}
        </>}>
        {open && <InvoiceDoc inv={open} />}
      </Modal>

      <Modal open={gen} onClose={() => setGen(false)} size="sm" title={t('bill.generate')}
        footer={<><button className="btn btn-ghost" onClick={() => setGen(false)}>{t('common.cancel')}</button><button className="btn btn-primary" onClick={generate}>{t('bill.generate')}</button></>}>
        <Field label={t('bill.generateFor')}><Input type="month" value={genMonth} max={monthKey(new Date())} onChange={(e) => setGenMonth(e.target.value)} /></Field>
      </Modal>
      <ConfirmDialog open={!!del} onClose={() => setDel(null)} message={t('common.deleteConfirm')} onConfirm={async () => { if (del) { await remove('invoices', del.id); toast(t('common.deleted'), 'info') } }} />
    </div>
  )
}

function InvoiceDoc({ inv }: { inv: Invoice }) {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const site = db.sites.find((s) => s.id === inv.siteId)
  const [y, m] = inv.period.split('-').map(Number)
  return (
    <div className="print-area space-y-5 p-1 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Logo />
          <div className="mt-2 text-xs text-slate-500">{db.settings.company}<br />{db.settings.email}</div>
        </div>
        <div className="text-end">
          <div className="text-xl font-bold" dir="ltr">{inv.number}</div>
          <StatusBadge status={inv.status} label={t(`status.${inv.status}` as DictKey)} />
        </div>
      </div>
      <div className="grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-3 dark:bg-slate-900/50">
        <div><div className="text-xs text-slate-500">{t('bill.billTo')}</div><div className="font-semibold">{inv.customer}</div><div className="text-xs text-slate-500">{site?.name}</div></div>
        <div><div className="text-xs text-slate-500">{t('bill.issued')}</div><div className="font-medium">{fmt.date(inv.issuedAt)}</div><div className="mt-1 text-xs text-slate-500">{t('bill.due')}</div><div className="font-medium">{fmt.date(inv.dueAt)}</div></div>
        <div><div className="text-xs text-slate-500">{t('bill.period')}</div><div className="font-medium">{fmt.month(new Date(y, m - 1, 1))}</div>{inv.paidAt && <><div className="mt-1 text-xs text-slate-500">{t('bill.paidOn')}</div><div className="font-medium">{fmt.date(inv.paidAt)}</div></>}</div>
      </div>
      <table className="table-base">
        <thead><tr><th>{t('common.description')}</th><th>{t('bill.energy')}</th><th>{t('bill.rate')}</th><th>{t('bill.amount')}</th></tr></thead>
        <tbody>
          <tr><td>{t('rep.energy')} — {site?.name}</td><td dir="ltr" className="text-start">{fmt.num(inv.energyKwh)} kWh</td><td dir="ltr" className="text-start">{fmt.money2(inv.rate)}</td><td dir="ltr" className="text-start font-semibold">{fmt.money2(inv.amount)}</td></tr>
        </tbody>
      </table>
      <div className="flex justify-end">
        <div className="w-56 space-y-1 rounded-xl bg-brand-50 p-3 dark:bg-brand-500/10">
          <div className="flex justify-between text-base font-bold"><span>{t('common.total')}</span><span dir="ltr">{fmt.money2(inv.amount)}</span></div>
        </div>
      </div>
      <p className="text-center text-xs text-slate-500">{t('bill.thankYou')}</p>
    </div>
  )
}
