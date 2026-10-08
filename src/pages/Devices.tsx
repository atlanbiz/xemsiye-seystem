import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Plus, Pencil, Trash2, RotateCw, Download, Wrench, Cpu, BatteryFull, PanelTop, Gauge, Radio, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, SearchInput, Pager, Select, Stat, StatusBadge, usePaged } from '../components/ui'
import type { Device, DeviceType } from '../lib/types'
import { cn, dayKey, downloadFile, toCSV, uid } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const TYPE_ICON: Record<DeviceType, typeof Cpu> = { inverter: Cpu, battery: BatteryFull, panel: PanelTop, meter: Gauge, sensor: Radio }
const TYPES: DeviceType[] = ['inverter', 'battery', 'panel', 'meter', 'sensor']

export default function Devices() {
  const { db, upsert, remove, notify } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const [type, setType] = useState('all')
  const [status, setStatus] = useState('all')
  const site = params.get('site') ?? 'all'
  const [detail, setDetail] = useState<Device | null>(null)
  const [form, setForm] = useState<{ open: boolean; dev: Device | null }>({ open: false, dev: null })
  const [del, setDel] = useState<Device | null>(null)

  useEffect(() => setQ(params.get('q') ?? ''), [params])

  const siteName = (id: string) => db.sites.find((s) => s.id === id)?.name ?? '—'
  const rows = useMemo(() => {
    const s = q.toLowerCase()
    return db.devices.filter(
      (d) =>
        (type === 'all' || d.type === type) &&
        (status === 'all' || d.status === status) &&
        (site === 'all' || d.siteId === site) &&
        (!s || [d.name, d.model, d.serial].some((v) => v.toLowerCase().includes(s))),
    )
  }, [db.devices, q, type, status, site])

  const counts = { online: db.devices.filter((d) => d.status === 'online').length, warning: db.devices.filter((d) => d.status === 'warning').length, offline: db.devices.filter((d) => d.status === 'offline').length }

  const restart = async (d: Device) => {
    const next = { ...d, status: 'online' as const, lastSeen: new Date().toISOString(), health: Math.max(d.health, 85) }
    await upsert('devices', next)
    setDetail(next)
    toast(t('dev.restarted'))
  }
  const updateFw = async (d: Device) => {
    const parts = d.firmware.replace('v', '').split('.').map(Number)
    const v = `v${parts[0]}.${(parts[1] ?? 0) + 1}.0`
    const next = { ...d, firmware: v, lastSeen: new Date().toISOString() }
    await upsert('devices', next)
    setDetail(next)
    toast(t('dev.fwUpdated', { v }))
  }
  const createTicket = async (d: Device) => {
    const id = uid('tkt-')
    await upsert('tickets', { id, siteId: d.siteId, deviceId: d.id, title: `${d.name} — ${t(`status.${d.status}` as DictKey)}`, description: `${d.model} (${d.serial})`, priority: d.status === 'offline' ? 'high' : 'medium', status: 'open', assignee: '', dueDate: dayKey(new Date(Date.now() + 3 * 86400000)), createdAt: new Date().toISOString() })
    if (db.settings.notifyMaintenance) await notify({ title: t('mt.created'), body: d.name, kind: 'info', link: `/maintenance?open=${id}` })
    navigate(`/maintenance?open=${id}`)
  }

  const exportCsv = () => {
    downloadFile('devices.csv', toCSV([['id', 'name', 'type', 'site', 'model', 'serial', 'status', 'health', 'efficiency', 'firmware', 'lastSeen'], ...rows.map((d) => [d.id, d.name, d.type, siteName(d.siteId), d.model, d.serial, d.status, d.health, d.efficiency, d.firmware, d.lastSeen])]), 'text/csv')
  }

  const paged = usePaged(rows)
  return (
    <div>
      <PageHeader
        title={t('dev.title')}
        subtitle={t('dev.subtitle')}
        actions={
          <>
            <button className="btn btn-ghost" onClick={exportCsv}><Download className="h-4 w-4" />{t('common.export')}</button>
            <button className="btn btn-primary" onClick={() => setForm({ open: true, dev: null })}><Plus className="h-4 w-4" />{t('dev.add')}</button>
          </>
        }
      />
      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Stat icon={<CheckCircle2 className="h-5 w-5" />} tone="green" label={t('status.online')} value={counts.online} />
        <Stat icon={<AlertTriangle className="h-5 w-5" />} tone="amber" label={t('status.warning')} value={counts.warning} />
        <Stat icon={<XCircle className="h-5 w-5" />} tone="red" label={t('status.offline')} value={counts.offline} />
      </div>
      <div className="card mb-4 flex flex-wrap items-center gap-2 !p-3">
        <SearchInput value={q} onChange={setQ} placeholder={t('common.search')} />
        <div className="w-44"><Select value={site} onChange={(e) => { const p = new URLSearchParams(params); if (e.target.value === 'all') p.delete('site'); else p.set('site', e.target.value); setParams(p) }}><option value="all">{t('an.allSites')}</option>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></div>
        <div className="w-40"><Select value={type} onChange={(e) => setType(e.target.value)}><option value="all">{t('common.type')}: {t('common.all')}</option>{TYPES.map((x) => <option key={x} value={x}>{t(`devType.${x}`)}</option>)}</Select></div>
        <div className="w-40"><Select value={status} onChange={(e) => setStatus(e.target.value)}><option value="all">{t('common.status')}: {t('common.all')}</option>{(['online', 'warning', 'offline'] as const).map((x) => <option key={x} value={x}>{t(`status.${x}`)}</option>)}</Select></div>
        <div className="flex-1" />
        <span className="text-xs text-slate-500">{t('common.showing')} {rows.length} {t('common.of')} {db.devices.length}</span>
      </div>

      <div className="card overflow-x-auto !p-0 scrollbar-thin">
        {rows.length === 0 ? <EmptyState text={t('common.noData')} /> : (
          <table className="table-base">
            <thead><tr><th>{t('common.name')}</th><th>{t('common.type')}</th><th>{t('common.site')}</th><th>{t('dev.model')}</th><th>{t('dev.health')}</th><th>{t('dev.efficiency')}</th><th>{t('dev.firmware')}</th><th>{t('dev.lastSeen')}</th><th>{t('common.status')}</th><th /></tr></thead>
            <tbody>
              {paged.items.map((d) => {
                const Icon = TYPE_ICON[d.type]
                return (
                  <tr key={d.id} className="cursor-pointer" onClick={() => setDetail(d)}>
                    <td><div className="flex items-center gap-2 font-medium"><span className="grid h-7 w-7 place-items-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/15"><Icon className="h-3.5 w-3.5" /></span><span dir="ltr">{d.name}</span></div></td>
                    <td>{t(`devType.${d.type}` as DictKey)}</td>
                    <td className="max-w-40 truncate">{siteName(d.siteId)}</td>
                    <td className="text-xs text-slate-500" dir="ltr">{d.model}</td>
                    <td><HealthBar v={d.health} /></td>
                    <td dir="ltr" className="text-start">{fmt.pct(d.efficiency)}</td>
                    <td dir="ltr" className="text-start text-xs">{d.firmware}</td>
                    <td className="text-xs text-slate-500">{fmt.ago(d.lastSeen)}</td>
                    <td><StatusBadge status={d.status} label={t(`status.${d.status}` as DictKey)} /></td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <div className="flex gap-1">
                        <button className="icon-btn h-7 w-7" onClick={() => setForm({ open: true, dev: d })}><Pencil className="h-3.5 w-3.5" /></button>
                        <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => setDel(d)}><Trash2 className="h-3.5 w-3.5" /></button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        <Pager {...paged} />
      </div>

      <Modal open={!!detail} onClose={() => setDetail(null)} title={detail ? <span dir="ltr">{detail.name}</span> : ''} size="md"
        footer={detail && (
          <>
            <button className="btn btn-ghost" onClick={() => createTicket(detail)}><Wrench className="h-4 w-4" />{t('dev.createTicket')}</button>
            <button className="btn btn-ghost" onClick={() => updateFw(detail)}><Download className="h-4 w-4" />{t('dev.updateFw')}</button>
            <button className="btn btn-primary" onClick={() => restart(detail)}><RotateCw className="h-4 w-4" />{t('dev.restart')}</button>
          </>
        )}>
        {detail && (
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            {[
              [t('common.type'), t(`devType.${detail.type}` as DictKey)],
              [t('common.site'), siteName(detail.siteId)],
              [t('dev.model'), detail.model],
              [t('dev.serial'), detail.serial],
              [t('dev.firmware'), detail.firmware],
              [t('dev.efficiency'), fmt.pct(detail.efficiency)],
              [t('sites.installDate'), fmt.date(detail.installedAt)],
              [t('dev.lastSeen'), fmt.ago(detail.lastSeen)],
            ].map(([k, v]) => (
              <div key={k}><dt className="text-xs text-slate-500">{k}</dt><dd className="font-medium">{v}</dd></div>
            ))}
            <div className="col-span-2"><dt className="mb-1 text-xs text-slate-500">{t('dev.health')}</dt><HealthBar v={detail.health} wide /></div>
            <div className="col-span-2"><StatusBadge status={detail.status} label={t(`status.${detail.status}` as DictKey)} /></div>
          </dl>
        )}
      </Modal>

      <DeviceForm open={form.open} initial={form.dev} onClose={() => setForm({ open: false, dev: null })} onSave={async (d) => {
        const prev = db.devices.find((x) => x.id === d.id)
        await upsert('devices', d)
        if (d.status === 'offline' && prev?.status !== 'offline' && db.settings.notifyDeviceAlerts) await notify({ title: `${d.name} ${t('status.offline')}`, body: siteName(d.siteId), kind: 'danger', link: '/devices' })
        toast(t('common.saved'))
        setForm({ open: false, dev: null })
      }} />
      <ConfirmDialog open={!!del} onClose={() => setDel(null)} message={t('common.deleteConfirm')} onConfirm={async () => { if (del) { await remove('devices', del.id); toast(t('common.deleted'), 'info') } }} />
    </div>
  )
}

function HealthBar({ v, wide }: { v: number; wide?: boolean }) {
  const c = v >= 90 ? 'bg-emerald-500' : v >= 75 ? 'bg-amber-500' : 'bg-rose-500'
  return (
    <div className="flex items-center gap-2">
      <div className={cn('h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700', wide ? 'flex-1' : 'w-16')}><div className={cn('h-full rounded-full', c)} style={{ width: `${v}%` }} /></div>
      <span className="text-xs" dir="ltr">{v}%</span>
    </div>
  )
}

function DeviceForm({ open, initial, onClose, onSave }: { open: boolean; initial: Device | null; onClose: () => void; onSave: (d: Device) => void }) {
  const { db } = useData()
  const { t } = useI18n()
  const blank = (): Device => ({ id: uid('dev-'), siteId: db.sites[0]?.id ?? '', name: '', type: 'inverter', model: '', serial: '', status: 'online', health: 100, efficiency: 97, firmware: 'v1.0.0', installedAt: dayKey(new Date()), lastSeen: new Date().toISOString() })
  const [f, setF] = useState<Device>(blank)
  const [err, setErr] = useState<Record<string, string>>({})
  useEffect(() => { if (open) { setF(initial ? { ...initial } : blank()); setErr({}) } }, [open, initial]) // eslint-disable-line react-hooks/exhaustive-deps
  const set = <K extends keyof Device>(k: K, v: Device[K]) => setF((p) => ({ ...p, [k]: v }))
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const er: Record<string, string> = {}
    if (!f.name.trim()) er.name = t('common.required')
    if (!f.siteId) er.siteId = t('common.required')
    if (!f.model.trim()) er.model = t('common.required')
    setErr(er)
    if (!Object.keys(er).length) onSave(f)
  }
  return (
    <Modal open={open} onClose={onClose} title={initial ? t('dev.edit') : t('dev.add')} footer={<><button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button><button className="btn btn-primary" type="submit" form="dev-form">{t('common.save')}</button></>}>
      <form id="dev-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label={t('common.name')} error={err.name}><Input value={f.name} onChange={(e) => set('name', e.target.value)} autoFocus /></Field>
        <Field label={t('common.site')} error={err.siteId}><Select value={f.siteId} onChange={(e) => set('siteId', e.target.value)}>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></Field>
        <Field label={t('common.type')}><Select value={f.type} onChange={(e) => set('type', e.target.value as DeviceType)}>{TYPES.map((x) => <option key={x} value={x}>{t(`devType.${x}`)}</option>)}</Select></Field>
        <Field label={t('common.status')}><Select value={f.status} onChange={(e) => set('status', e.target.value as Device['status'])}>{(['online', 'warning', 'offline'] as const).map((x) => <option key={x} value={x}>{t(`status.${x}`)}</option>)}</Select></Field>
        <Field label={t('dev.model')} error={err.model}><Input value={f.model} onChange={(e) => set('model', e.target.value)} /></Field>
        <Field label={t('dev.serial')}><Input value={f.serial} onChange={(e) => set('serial', e.target.value)} /></Field>
        <Field label={`${t('dev.health')} (%)`}><Input type="number" min={0} max={100} value={f.health} onChange={(e) => set('health', Number(e.target.value))} /></Field>
        <Field label={`${t('dev.efficiency')} (%)`}><Input type="number" min={0} max={100} step="0.1" value={f.efficiency} onChange={(e) => set('efficiency', Number(e.target.value))} /></Field>
        <Field label={t('dev.firmware')}><Input value={f.firmware} onChange={(e) => set('firmware', e.target.value)} /></Field>
        <Field label={t('sites.installDate')}><Input type="date" value={f.installedAt} onChange={(e) => set('installedAt', e.target.value)} /></Field>
      </form>
    </Modal>
  )
}
