import { lazy, Suspense, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRight, Zap } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { PageHeader, SearchInput, Skeleton, StatusBadge } from '../components/ui'
import { STATUS_COLOR, pinRadius } from '../lib/mapStyle'
import { nowHour, siteKw } from '../lib/sim'
import type { Site, SiteStatus } from '../lib/types'
import { cn, dayKey } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const SiteMap = lazy(() => import('../components/SiteMap'))
const STATUSES: SiteStatus[] = ['active', 'idle', 'offline', 'maintenance']

/** Popup card shown when a pin is tapped. */
function SitePopup({ site }: { site: Site }) {
  const { now } = useData()
  const { t, fmt, dir } = useI18n()
  const kw = siteKw(site, dayKey(new Date(now)), nowHour())
  return (
    <div dir={dir} className="space-y-2 text-slate-800 dark:text-slate-100">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold">{site.name}</div>
          <div className="truncate text-xs text-slate-500 dark:text-slate-400">{site.location} · {t(`siteType.${site.type}` as DictKey)}</div>
        </div>
        <StatusBadge status={site.status} label={t(`status.${site.status}` as DictKey)} />
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-xl bg-slate-50 p-2 dark:bg-slate-900/50">
          <div className="text-[11px] text-slate-500">{t('map.capacity')}</div>
          <div className="text-sm font-semibold" dir="ltr" style={{ textAlign: 'start' }}>{fmt.power(site.capacityKw)}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-2 dark:bg-slate-900/50">
          <div className="text-[11px] text-slate-500">{t('sites.currentPower')}</div>
          <div className="flex items-center gap-1 text-sm font-semibold text-brand-600 dark:text-brand-300" dir="ltr" style={{ justifyContent: 'flex-start' }}><Zap className="h-3.5 w-3.5" />{fmt.power(kw)}</div>
        </div>
      </div>
      <Link to={`/sites/${site.id}`} className="btn btn-primary w-full !py-1.5 text-xs !text-white">
        {t('map.openSite')}<ArrowRight className="h-3.5 w-3.5 rtl:rotate-180" />
      </Link>
    </div>
  )
}

export default function MapPage() {
  const { db } = useData()
  const { t, fmt } = useI18n()
  const [hidden, setHidden] = useState<SiteStatus[]>([])
  const [q, setQ] = useState('')
  const [params] = useSearchParams()
  const [selected, setSelected] = useState<string | null>(params.get('site'))

  const counts = useMemo(() => Object.fromEntries(STATUSES.map((s) => [s, db.sites.filter((x) => x.status === s).length])) as Record<SiteStatus, number>, [db.sites])
  const visible = useMemo(() => db.sites.filter((s) => !hidden.includes(s.status) && Number.isFinite(s.lat) && Number.isFinite(s.lng)), [db.sites, hidden])
  const list = useMemo(() => {
    const s = q.trim().toLowerCase()
    return visible.filter((x) => !s || [x.name, x.location, x.customer].some((v) => v.toLowerCase().includes(s))).sort((a, b) => b.capacityKw - a.capacityKw)
  }, [visible, q])
  const toggle = (s: SiteStatus) => setHidden((h) => (h.includes(s) ? h.filter((x) => x !== s) : [...h, s]))

  return (
    <div className="space-y-4">
      <PageHeader title={t('map.title')} subtitle={t('map.subtitle')} />
      <div className="flex flex-wrap items-center gap-2">
        {STATUSES.map((s) => (
          <button key={s} onClick={() => toggle(s)} title={t('map.filterHint')} className={cn('inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium transition', hidden.includes(s) ? 'border-slate-200 bg-white/50 text-slate-400 line-through dark:border-slate-700 dark:bg-slate-800/40' : 'border-white/80 bg-white/80 text-slate-700 shadow-sm dark:border-white/10 dark:bg-slate-800/80 dark:text-slate-200')}>
            <span className="h-2.5 w-2.5 rounded-full ring-2 ring-white dark:ring-slate-800" style={{ background: STATUS_COLOR[s] }} />
            {t(`status.${s}` as DictKey)}
            <span className="text-slate-400">{counts[s]}</span>
          </button>
        ))}
        <div className="flex-1" />
        <div className="hidden items-center gap-2 text-xs text-slate-500 sm:flex">
          {[12, 120, 820].map((c) => <span key={c} className="rounded-full bg-slate-400/70" style={{ width: pinRadius(c), height: pinRadius(c) }} />)}
          {t('map.size')}
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
        <div className="card !p-1.5">
          <Suspense fallback={<Skeleton className="h-[62vh] min-h-[380px]" />}>
            <SiteMap sites={visible} className="h-[62vh] min-h-[380px]" selectedId={selected} onSelect={setSelected} popup={(s) => <SitePopup site={s} />} />
          </Suspense>
        </div>
        <div className="card flex max-h-[62vh] min-h-[300px] flex-col !p-3">
          <div className="mb-2 flex items-center justify-between gap-2 px-1">
            <h3 className="text-[15px] font-semibold">{t('nav.sites')} <span className="text-xs font-normal text-slate-400">{list.length}</span></h3>
          </div>
          <div className="mb-2 [&_input]:w-full [&>div]:w-full"><SearchInput value={q} onChange={setQ} placeholder={t('common.search')} /></div>
          <ul className="scrollbar-thin -mx-1 flex-1 space-y-1 overflow-y-auto px-1">
            {list.map((s) => (
              <li key={s.id}>
                <button onClick={() => setSelected(s.id)} className={cn('flex w-full cursor-pointer items-center gap-3 rounded-xl px-2.5 py-2 text-start transition', selected === s.id ? 'bg-brand-50 ring-1 ring-brand-200 dark:bg-brand-500/15 dark:ring-brand-500/30' : 'hover:bg-slate-50 dark:hover:bg-slate-700/40')}>
                  <span className="h-3 w-3 shrink-0 rounded-full" style={{ background: STATUS_COLOR[s.status] }} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{s.name}</span>
                    <span className="block truncate text-xs text-slate-500">{s.location}</span>
                  </span>
                  <span className="shrink-0 text-xs font-medium text-slate-500" dir="ltr">{fmt.power(s.capacityKw)}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}
