import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BellRing, Plus, Pencil, Trash2, RefreshCw, ShieldAlert, Activity, Info, Clock } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { metricUnit, useAlertRunner } from '../context/alerts'
import { Badge, Card, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, Segmented, Select, Stat, Toggle } from '../components/ui'
import { COOLDOWN_MS, matchRule, METRICS, METRIC_UNIT } from '../lib/alerts'
import { isSupabase } from '../lib/supabase'
import type { AlertMetric, AlertRule, AlertSeverity } from '../lib/types'
import { cn, uid } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const DEFAULT_THRESHOLD: Record<AlertMetric, number> = {
  site_yield_below: 1.5,
  site_offline: 0,
  device_efficiency_below: 92,
  device_health_below: 75,
  device_offline_minutes: 60,
  invoice_overdue_days: 7,
}

const emptyRule = (): AlertRule => ({
  id: uid('rule-'),
  name: '',
  metric: 'device_efficiency_below',
  threshold: DEFAULT_THRESHOLD.device_efficiency_below,
  siteId: null,
  severity: 'warning',
  enabled: true,
  lastTriggeredAt: null,
  createdAt: new Date().toISOString(),
})

export default function Alerts() {
  const { db, now, upsert, remove } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const navigate = useNavigate()
  const runAlerts = useAlertRunner()
  const [edit, setEdit] = useState<AlertRule | null>(null)
  const [del, setDel] = useState<AlertRule | null>(null)
  const [checking, setChecking] = useState(false)
  const minute = Math.floor(now / 60000)

  const matches = useMemo(() => Object.fromEntries(db.alertRules.map((r) => [r.id, matchRule(r, db).length])), [db, minute]) // eslint-disable-line react-hooks/exhaustive-deps
  const ruleNames = useMemo(() => new Set(db.alertRules.map((r) => r.name)), [db.alertRules])
  const recent = useMemo(() => db.notifications.filter((n) => ruleNames.has(n.title)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)), [db.notifications, ruleNames])
  const dayAgo = new Date(now - 86400_000).toISOString()

  const siteName = (id: string | null) => (id ? db.sites.find((s) => s.id === id)?.name ?? '—' : t('alerts.allSites'))
  const condition = (r: AlertRule) => {
    const unit = metricUnit(r.metric, t)
    const label = t(`alerts.m.${r.metric}` as DictKey)
    return unit == null ? label : <>{label} <bdi dir="ltr">{fmt.num(r.threshold, 2)}{unit === '%' ? '' : ' '}{unit}</bdi></>
  }

  const checkNow = async () => {
    setChecking(true)
    const n = await runAlerts()
    setChecking(false)
    toast(t('alerts.checked', { n }), n ? 'warning' : 'success')
  }

  const save = async (r: AlertRule) => {
    await upsert('alertRules', r)
    toast(t('common.saved'))
    setEdit(null)
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('alerts.title')}
        subtitle={t('alerts.subtitle')}
        actions={<>
          {!isSupabase && <button className="btn btn-ghost" disabled={checking} onClick={checkNow}><RefreshCw className={cn('h-4 w-4', checking && 'animate-spin')} />{t('alerts.checkNow')}</button>}
          <button className="btn btn-primary" onClick={() => setEdit(emptyRule())}><Plus className="h-4 w-4" />{t('alerts.add')}</button>
        </>}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat icon={<BellRing className="h-5 w-5" />} label={t('alerts.activeRules')} value={`${db.alertRules.filter((r) => r.enabled).length} / ${db.alertRules.length}`} />
        <Stat icon={<ShieldAlert className="h-5 w-5" />} tone="red" label={t('alerts.firing')} value={db.alertRules.filter((r) => r.enabled && matches[r.id] > 0).length} />
        <Stat icon={<Activity className="h-5 w-5" />} tone="amber" label={t('alerts.firedToday')} value={recent.filter((n) => n.createdAt >= dayAgo).length} />
      </div>

      <div className="flex items-start gap-2 rounded-2xl bg-brand-50/80 p-3 text-xs leading-relaxed text-brand-800 ring-1 ring-brand-100 dark:bg-brand-500/10 dark:text-brand-200 dark:ring-brand-500/20">
        <Info className="mt-0.5 h-4 w-4 shrink-0" />
        {isSupabase ? t('alerts.serverHint') : t('alerts.demoHint')}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_380px]">
        <Card title={t('alerts.rules')}>
          {db.alertRules.length === 0 ? <EmptyState text={t('alerts.empty')} /> : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-700">
              {db.alertRules.map((r) => {
                const cooling = r.lastTriggeredAt && now - new Date(r.lastTriggeredAt).getTime() < COOLDOWN_MS
                return (
                  <li key={r.id} className={cn('flex flex-wrap items-center gap-3 py-3', !r.enabled && 'opacity-60')}>
                    <span className={cn('grid h-9 w-9 shrink-0 place-items-center rounded-xl', r.severity === 'danger' ? 'bg-rose-50 text-rose-600 dark:bg-rose-500/15' : 'bg-amber-50 text-amber-600 dark:bg-amber-500/15')}>
                      <BellRing className="h-4 w-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="truncate text-sm font-semibold">{r.name}</span>
                        <Badge tone={r.severity === 'danger' ? 'red' : 'amber'}>{t(`alerts.sev.${r.severity}`)}</Badge>
                        {r.enabled && matches[r.id] > 0 && <Badge tone="violet">{t('alerts.matching', { n: matches[r.id] })}</Badge>}
                      </div>
                      <div className="mt-0.5 truncate text-xs text-slate-500">{condition(r)} · {siteName(r.siteId)}</div>
                      <div className="mt-0.5 flex items-center gap-1 text-[11px] text-slate-400">
                        <Clock className="h-3 w-3" />
                        {t('alerts.lastTriggered')}: {r.lastTriggeredAt ? fmt.ago(r.lastTriggeredAt) : t('alerts.never')}
                        {cooling && <span>· {t('alerts.cooldown', { t: fmt.time(new Date(new Date(r.lastTriggeredAt!).getTime() + COOLDOWN_MS)) })}</span>}
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      <div className="w-12"><Toggle label="" checked={r.enabled} onChange={(v) => upsert('alertRules', { ...r, enabled: v })} /></div>
                      <button className="icon-btn h-8 w-8" aria-label={t('common.edit')} onClick={() => setEdit(r)}><Pencil className="h-3.5 w-3.5" /></button>
                      <button className="icon-btn h-8 w-8 hover:!text-rose-600" aria-label={t('common.delete')} onClick={() => setDel(r)}><Trash2 className="h-3.5 w-3.5" /></button>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        <Card title={t('alerts.recent')}>
          {recent.length === 0 ? <EmptyState text={t('alerts.recentEmpty')} /> : (
            <ul className="scrollbar-thin max-h-[520px] space-y-1 overflow-y-auto">
              {recent.slice(0, 30).map((n) => (
                <li key={n.id}>
                  <button onClick={() => { if (!n.read) upsert('notifications', { ...n, read: true }); if (n.link) navigate(n.link) }} className="flex w-full cursor-pointer gap-3 rounded-xl px-2 py-2 text-start hover:bg-slate-50 dark:hover:bg-slate-700/40">
                    <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', n.kind === 'danger' ? 'bg-rose-500' : 'bg-amber-500')} />
                    <span className="min-w-0 flex-1">
                      <span className={cn('block truncate text-sm', !n.read && 'font-semibold')}>{n.title}</span>
                      <span className="line-clamp-2 block text-xs text-slate-500">{n.body}</span>
                      <span className="mt-0.5 block text-[11px] text-slate-400">{fmt.ago(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <RuleForm rule={edit} onClose={() => setEdit(null)} onSave={save} />
      <ConfirmDialog open={!!del} onClose={() => setDel(null)} message={t('common.deleteConfirm')} onConfirm={async () => { if (del) { await remove('alertRules', del.id); toast(t('common.deleted'), 'info') } }} />
    </div>
  )
}

function RuleForm({ rule, onClose, onSave }: { rule: AlertRule | null; onClose: () => void; onSave: (r: AlertRule) => void }) {
  const { db } = useData()
  const { t } = useI18n()
  const [f, setF] = useState<AlertRule>(emptyRule())
  const [err, setErr] = useState<Record<string, string>>({})
  useEffect(() => {
    if (rule) {
      setF({ ...rule })
      setErr({})
    }
  }, [rule])

  const set = <K extends keyof AlertRule>(k: K, v: AlertRule[K]) => setF((p) => ({ ...p, [k]: v }))
  const unit = metricUnit(f.metric, t)
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const er: Record<string, string> = {}
    if (!f.name.trim()) er.name = t('common.required')
    if (unit != null && !(Number.isFinite(f.threshold) && f.threshold >= 0)) er.threshold = t('common.required')
    setErr(er)
    if (Object.keys(er).length) return
    onSave({ ...f, name: f.name.trim(), threshold: unit == null ? 0 : f.threshold })
  }

  return (
    <Modal
      open={!!rule}
      onClose={onClose}
      title={rule && db.alertRules.some((r) => r.id === rule.id) ? t('alerts.edit') : t('alerts.add')}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button>
        <button className="btn btn-primary" form="rule-form" type="submit">{t('common.save')}</button>
      </>}
    >
      <form id="rule-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label={t('alerts.name')} error={err.name} className="sm:col-span-2"><Input value={f.name} onChange={(e) => set('name', e.target.value)} autoFocus /></Field>
        <Field label={t('alerts.metric')} className="sm:col-span-2">
          <Select value={f.metric} onChange={(e) => { const m = e.target.value as AlertMetric; setF((p) => ({ ...p, metric: m, threshold: DEFAULT_THRESHOLD[m] })) }}>
            {METRICS.map((m) => <option key={m} value={m}>{t(`alerts.m.${m}` as DictKey)}</option>)}
          </Select>
          <span className="mt-1 block text-xs text-slate-500">{t(`alerts.d.${f.metric}` as DictKey)}</span>
        </Field>
        <Field label={t('alerts.threshold')} error={err.threshold}>
          {unit == null ? <div className="input !bg-slate-50 text-slate-400 dark:!bg-slate-900/40">{t('alerts.noThreshold')}</div> : (
            <div className="relative">
              <Input type="number" min={0} step={METRIC_UNIT[f.metric] === 'kWh/kWp' ? 0.1 : 1} value={f.threshold} onChange={(e) => set('threshold', Number(e.target.value))} className="pe-20" />
              <span className="pointer-events-none absolute end-3 top-1/2 -translate-y-1/2 text-xs text-slate-400" dir="ltr">{unit}</span>
            </div>
          )}
        </Field>
        <Field label={t('alerts.scope')}>
          <Select value={f.siteId ?? ''} onChange={(e) => set('siteId', e.target.value || null)}>
            <option value="">{t('alerts.allSites')}</option>
            {db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </Field>
        <Field label={t('alerts.severity')}>
          <div><Segmented<AlertSeverity> value={f.severity} onChange={(v) => set('severity', v)} options={(['warning', 'danger'] as const).map((v) => ({ value: v, label: t(`alerts.sev.${v}`) }))} /></div>
        </Field>
        <div className="flex items-end"><div className="w-full"><Toggle label={t('alerts.enabled')} checked={f.enabled} onChange={(v) => set('enabled', v)} /></div></div>
      </form>
    </Modal>
  )
}
