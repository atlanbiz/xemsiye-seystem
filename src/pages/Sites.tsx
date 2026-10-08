import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, MapPin, Pencil, Trash2, Zap, Battery } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { ConfirmDialog, EmptyState, PageHeader, SearchInput, Segmented, Select, Sparkline, StatusBadge } from '../components/ui'
import SiteForm from '../components/SiteForm'
import { hourlySeries, nowHour, siteDayKwhCached, siteKw } from '../lib/sim'
import { cn, dayKey } from '../lib/utils'
import type { Site } from '../lib/types'
import type { DictKey } from '../i18n/en'

export default function Sites() {
  const { db, now, upsert, removeSite, notify } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('all')
  const [type, setType] = useState('all')
  const [view, setView] = useState<'grid' | 'list'>('grid')
  const [editing, setEditing] = useState<Site | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleting, setDeleting] = useState<Site | null>(null)
  const day = dayKey(new Date(now))

  const rows = useMemo(() => {
    const s = q.toLowerCase()
    return db.sites
      .filter((x) => (status === 'all' || x.status === status) && (type === 'all' || x.type === type))
      .filter((x) => !s || [x.name, x.location, x.customer].some((v) => v.toLowerCase().includes(s)))
      .map((x) => ({ x, kw: siteKw(x, day, nowHour()), kwh: siteDayKwhCached(x, day), spark: hourlySeries([x], day, 60).filter((p) => p.kw !== null && p.hour >= 6).map((p) => p.kw as number), devices: db.devices.filter((d) => d.siteId === x.id).length }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [db.sites, db.devices, q, status, type, day, Math.floor(now / 60000)])

  const save = async (s: Site) => {
    const isNew = !db.sites.some((x) => x.id === s.id)
    await upsert('sites', s)
    if (isNew) await notify({ title: t('sites.add'), body: s.name, kind: 'success', link: `/sites/${s.id}` })
    toast(t('common.saved'))
    setFormOpen(false)
  }

  return (
    <div>
      <PageHeader
        title={t('sites.title')}
        subtitle={t('sites.subtitle')}
        actions={<button className="btn btn-primary" onClick={() => { setEditing(null); setFormOpen(true) }}><Plus className="h-4 w-4" />{t('sites.add')}</button>}
      />
      <div className="card mb-4 flex flex-wrap items-center gap-2 !p-3">
        <SearchInput value={q} onChange={setQ} placeholder={t('common.search')} />
        <div className="w-40"><Select value={status} onChange={(e) => setStatus(e.target.value)}><option value="all">{t('common.status')}: {t('common.all')}</option>{(['active', 'idle', 'offline', 'maintenance'] as const).map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}</Select></div>
        <div className="w-44"><Select value={type} onChange={(e) => setType(e.target.value)}><option value="all">{t('common.type')}: {t('common.all')}</option>{(['residential', 'commercial', 'industrial', 'utility'] as const).map((s) => <option key={s} value={s}>{t(`siteType.${s}`)}</option>)}</Select></div>
        <div className="flex-1" />
        <span className="text-xs text-slate-500">{t('common.showing')} {rows.length} {t('common.of')} {db.sites.length}</span>
        <Segmented value={view} onChange={setView} options={[{ value: 'grid', label: '▦ ' + t('common.grid') }, { value: 'list', label: '☰ ' + t('common.list') }]} />
      </div>

      {rows.length === 0 && <div className="card"><EmptyState text={t('common.noData')} /></div>}

      {view === 'grid' ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {rows.map(({ x, kw, kwh, spark, devices }) => (
            <div key={x.id} onClick={() => navigate(`/sites/${x.id}`)} className="card group cursor-pointer transition hover:-translate-y-0.5 hover:shadow-lg">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="truncate font-semibold">{x.name}</div>
                  <div className="mt-0.5 flex items-center gap-1 text-xs text-slate-500"><MapPin className="h-3 w-3" />{x.location} · {t(`siteType.${x.type}` as DictKey)}</div>
                </div>
                <StatusBadge status={x.status} label={t(`status.${x.status}` as DictKey)} />
              </div>
              <div className="my-3"><Sparkline data={spark} width={260} height={44} fill color={x.status === 'active' ? '#3b74f6' : '#94a3b8'} /></div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div><div className="text-slate-500">{t('sites.currentPower')}</div><div className="font-semibold" dir="ltr">{fmt.power(kw)}</div></div>
                <div><div className="text-slate-500">{t('sites.energyToday')}</div><div className="font-semibold" dir="ltr">{fmt.energy(kwh)}</div></div>
                <div><div className="text-slate-500">{t('sites.capacity')}</div><div className="font-semibold" dir="ltr">{fmt.power(x.capacityKw)}</div></div>
              </div>
              <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3 text-xs text-slate-500 dark:border-slate-700">
                <span className="flex items-center gap-3"><span className="flex items-center gap-1"><Zap className="h-3 w-3" />{devices}</span>{x.batteryKwh > 0 && <span className="flex items-center gap-1" dir="ltr"><Battery className="h-3 w-3" />{x.batteryKwh} kWh</span>}</span>
                <span className="flex gap-1 opacity-0 transition group-hover:opacity-100" onClick={(e) => e.stopPropagation()}>
                  <button className="icon-btn h-7 w-7" onClick={() => { setEditing(x); setFormOpen(true) }}><Pencil className="h-3.5 w-3.5" /></button>
                  <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => setDeleting(x)}><Trash2 className="h-3.5 w-3.5" /></button>
                </span>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="card overflow-x-auto !p-0 scrollbar-thin">
          <table className="table-base">
            <thead><tr><th>{t('common.name')}</th><th>{t('common.location')}</th><th>{t('common.type')}</th><th>{t('sites.capacity')}</th><th>{t('sites.currentPower')}</th><th>{t('sites.energyToday')}</th><th>{t('sites.customer')}</th><th>{t('common.status')}</th><th>{t('common.actions')}</th></tr></thead>
            <tbody>
              {rows.map(({ x, kw, kwh }) => (
                <tr key={x.id} className="cursor-pointer" onClick={() => navigate(`/sites/${x.id}`)}>
                  <td className="font-medium">{x.name}</td>
                  <td>{x.location}</td>
                  <td>{t(`siteType.${x.type}` as DictKey)}</td>
                  <td dir="ltr" className="text-start">{fmt.power(x.capacityKw)}</td>
                  <td dir="ltr" className="text-start">{fmt.power(kw)}</td>
                  <td dir="ltr" className="text-start">{fmt.energy(kwh)}</td>
                  <td className="max-w-40 truncate">{x.customer}</td>
                  <td><StatusBadge status={x.status} label={t(`status.${x.status}` as DictKey)} /></td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <div className="flex gap-1">
                      <button className="icon-btn h-7 w-7" onClick={() => { setEditing(x); setFormOpen(true) }}><Pencil className="h-3.5 w-3.5" /></button>
                      <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => setDeleting(x)}><Trash2 className="h-3.5 w-3.5" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <SiteForm open={formOpen} onClose={() => setFormOpen(false)} onSave={save} initial={editing} />
      <ConfirmDialog
        open={!!deleting}
        onClose={() => setDeleting(null)}
        onConfirm={async () => { if (deleting) { await removeSite(deleting.id); toast(t('common.deleted'), 'info') } }}
        message={<>{t('common.deleteConfirm')}<br /><span className={cn('mt-2 block text-rose-600')}>{t('sites.deleteWarn')}</span></>}
      />
    </div>
  )
}
