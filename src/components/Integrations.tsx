import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Copy, Sun, Cloud, Webhook, KeyRound, Info, AlertTriangle } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { Card, ConfirmDialog, EmptyState, Field, Input, Modal, Select, StatusBadge } from './ui'
import { repo } from '../lib/repo'
import { isSupabase } from '../lib/supabase'
import type { Integration, IntegrationVendor } from '../lib/types'
import { cn, uid } from '../lib/utils'

const VENDORS: { id: IntegrationVendor; icon: typeof Sun }[] = [
  { id: 'solaredge', icon: Sun },
  { id: 'fusionsolar', icon: Cloud },
  { id: 'webhook', icon: Webhook },
]
const STATUS_TONE = { pending: 'pending', ok: 'online', error: 'offline' } as const
const FS_DEFAULT_BASE = 'https://eu5.fusionsolar.huawei.com'

const supabaseUrl = (import.meta.env.VITE_SUPABASE_URL as string | undefined)?.replace(/\/$/, '')
const INGEST_URL = `${supabaseUrl ?? 'https://<project-ref>.supabase.co'}/functions/v1/ingest`
const newToken = () => uid().replace(/-/g, '')

function useCopy() {
  const { t } = useI18n()
  const toast = useToast()
  return (text: string) =>
    navigator.clipboard
      .writeText(text)
      .then(() => toast(t('common.copied')))
      .catch(() => toast(t('common.copyFailed'), 'error'))
}

function CopyRow({ label, value }: { label: string; value: string }) {
  const { t } = useI18n()
  const copy = useCopy()
  return (
    <div>
      <div className="mb-1 text-[11px] font-medium text-slate-500">{label}</div>
      <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 py-1 pe-1 ps-3 dark:border-slate-600 dark:bg-slate-900/60" dir="ltr">
        <code className="min-w-0 flex-1 truncate text-xs">{value}</code>
        <button type="button" className="icon-btn h-7 w-7 shrink-0" title={t('common.copy')} aria-label={t('common.copy')} onClick={() => copy(value)}><Copy className="h-3.5 w-3.5" /></button>
      </div>
    </div>
  )
}

export default function Integrations() {
  const { db, remove } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const [edit, setEdit] = useState<Integration | null>(null)
  const [del, setDel] = useState<Integration | null>(null)
  const siteName = (id: string) => db.sites.find((s) => s.id === id)?.name ?? '—'

  const blank = (): Integration => ({
    id: uid('int-'),
    vendor: 'solaredge',
    name: '',
    siteId: db.sites[0]?.id ?? '',
    externalId: '',
    config: {},
    ingestToken: newToken(),
    status: 'pending',
    lastSyncAt: null,
    lastError: null,
    createdAt: new Date().toISOString(),
  })

  return (
    <div className="space-y-4">
      <Card title={t('int.title')} action={<button className="btn btn-primary !py-1.5 text-xs" disabled={!db.sites.length} onClick={() => setEdit(blank())}><Plus className="h-3.5 w-3.5" />{t('int.add')}</button>}>
        <p className="-mt-1 mb-3 text-xs text-slate-500">{t('int.subtitle')}</p>
        {!isSupabase && (
          <div className="mb-3 flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-xs leading-relaxed text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
            <Info className="mt-0.5 h-4 w-4 shrink-0" />{t('int.demoNote')}
          </div>
        )}
        {db.integrations.length === 0 ? <EmptyState text={t('int.empty')} /> : (
          <ul className="space-y-3">
            {db.integrations.map((x) => {
              const V = VENDORS.find((v) => v.id === x.vendor)!.icon
              return (
                <li key={x.id} className="rounded-2xl border border-slate-100 bg-white/70 p-3 dark:border-slate-700 dark:bg-slate-900/30">
                  <div className="flex flex-wrap items-center gap-3">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300"><V className="h-5 w-5" /></span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="truncate text-sm font-semibold">{x.name}</span>
                        <StatusBadge status={STATUS_TONE[x.status]} label={t(`int.status.${x.status}`)} />
                      </div>
                      <div className="truncate text-xs text-slate-500">{t(`int.v.${x.vendor}`)} · {siteName(x.siteId)}{x.externalId && <> · <span dir="ltr">{x.externalId}</span></>}</div>
                      <div className="text-[11px] text-slate-400">{t('int.lastSync')}: {x.lastSyncAt ? fmt.ago(x.lastSyncAt) : t('alerts.never')}</div>
                    </div>
                    <div className="flex gap-1">
                      <button className="icon-btn h-8 w-8" aria-label={t('common.edit')} onClick={() => setEdit(x)}><Pencil className="h-3.5 w-3.5" /></button>
                      <button className="icon-btn h-8 w-8 hover:!text-rose-600" aria-label={t('common.delete')} onClick={() => setDel(x)}><Trash2 className="h-3.5 w-3.5" /></button>
                    </div>
                  </div>
                  {x.lastError && (
                    <div className="mt-2 flex items-start gap-2 rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:bg-rose-500/10 dark:text-rose-300">
                      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /><span><b>{t('int.lastError')}:</b> <span dir="ltr">{x.lastError}</span></span>
                    </div>
                  )}
                  {x.vendor === 'webhook' && (
                    <div className="mt-3 grid gap-2 sm:grid-cols-2">
                      <CopyRow label={t('int.ingestUrl')} value={INGEST_URL} />
                      <CopyRow label={t('int.token')} value={x.ingestToken} />
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </Card>
      <IntegrationForm item={edit} onClose={() => setEdit(null)} />
      <ConfirmDialog open={!!del} onClose={() => setDel(null)} message={t('common.deleteConfirm')} onConfirm={async () => { if (del) { await remove('integrations', del.id); toast(t('common.deleted'), 'info') } }} />
    </div>
  )
}

function IntegrationForm({ item, onClose }: { item: Integration | null; onClose: () => void }) {
  const { db, upsert } = useData()
  const { t } = useI18n()
  const toast = useToast()
  const [f, setF] = useState<Integration | null>(null)
  const [secret, setSecret] = useState('')
  const [err, setErr] = useState<Record<string, string>>({})
  const isNew = !!item && !db.integrations.some((x) => x.id === item.id)
  useEffect(() => {
    setF(item ? { ...item, config: { ...item.config } } : null)
    setSecret('')
    setErr({})
  }, [item])
  if (!f) return null

  const set = <K extends keyof Integration>(k: K, v: Integration[K]) => setF((p) => (p ? { ...p, [k]: v } : p))
  const setCfg = (k: string, v: string) => setF((p) => (p ? { ...p, config: { ...p.config, [k]: v } } : p))
  const secretKey = f.vendor === 'solaredge' ? 'api_key' : f.vendor === 'fusionsolar' ? 'system_code' : null

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const er: Record<string, string> = {}
    if (!f.name.trim()) er.name = t('common.required')
    if (!f.siteId) er.siteId = t('common.required')
    if (f.vendor !== 'webhook' && !f.externalId.trim()) er.externalId = t('common.required')
    if (f.vendor === 'fusionsolar' && !f.config.username?.trim()) er.username = t('common.required')
    if (isSupabase && isNew && secretKey && !secret.trim()) er.secret = t('common.required')
    setErr(er)
    if (Object.keys(er).length) return
    const config: Record<string, string> = f.vendor === 'fusionsolar' ? { base_url: (f.config.base_url || FS_DEFAULT_BASE).replace(/\/$/, ''), username: f.config.username.trim() } : {}
    const next: Integration = { ...f, name: f.name.trim(), externalId: f.vendor === 'webhook' ? '' : f.externalId.trim(), config, ...(secret.trim() ? { status: 'pending' as const, lastError: null } : {}) }
    await upsert('integrations', next)
    if (secretKey && secret.trim()) {
      try {
        await repo.setIntegrationSecret(next.id, { [secretKey]: secret.trim() })
      } catch (e2) {
        toast(t('int.secretFailed', { e: String((e2 as Error)?.message ?? e2) }), 'error')
        return onClose()
      }
    }
    toast(t('int.saved'))
    onClose()
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isNew ? t('int.add') : t('int.edit')}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button>
        <button className="btn btn-primary" form="int-form" type="submit">{t('common.save')}</button>
      </>}
    >
      <form id="int-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <div className="mb-1 text-xs font-medium text-slate-600 dark:text-slate-300">{t('int.vendor')}</div>
          <div className="grid grid-cols-3 gap-2">
            {VENDORS.map(({ id, icon: Icon }) => (
              <button key={id} type="button" disabled={!isNew} onClick={() => set('vendor', id)} className={cn('flex cursor-pointer flex-col items-center gap-1 rounded-xl border p-3 text-center text-xs font-medium transition disabled:cursor-default', f.vendor === id ? 'border-brand-400 bg-brand-50 text-brand-700 ring-4 ring-brand-100 dark:bg-brand-500/15 dark:text-brand-300 dark:ring-brand-500/20' : 'border-slate-200 bg-white/70 hover:border-brand-200 disabled:opacity-40 dark:border-slate-600 dark:bg-slate-800/60')}>
                <Icon className="h-5 w-5" />{t(`int.v.${id}`)}
              </button>
            ))}
          </div>
        </div>
        <Field label={t('int.name')} error={err.name}><Input value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label={t('common.site')} error={err.siteId}>
          <Select value={f.siteId} onChange={(e) => set('siteId', e.target.value)}>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select>
        </Field>

        {f.vendor === 'solaredge' && <>
          <Field label={t('int.seSiteId')} error={err.externalId}><Input dir="ltr" inputMode="numeric" value={f.externalId} onChange={(e) => set('externalId', e.target.value)} placeholder="1234567" /></Field>
          <Field label={t('int.apiKey')} error={err.secret}><Input dir="ltr" type="password" autoComplete="off" value={secret} onChange={(e) => setSecret(e.target.value)} placeholder={isNew ? '' : t('int.secretKeep')} /></Field>
        </>}
        {f.vendor === 'fusionsolar' && <>
          <Field label={t('int.fsBaseUrl')} className="sm:col-span-2"><Input dir="ltr" type="url" value={f.config.base_url ?? ''} onChange={(e) => setCfg('base_url', e.target.value)} placeholder={FS_DEFAULT_BASE} /></Field>
          <Field label={t('int.fsUser')} error={err.username}><Input dir="ltr" autoComplete="off" value={f.config.username ?? ''} onChange={(e) => setCfg('username', e.target.value)} /></Field>
          <Field label={t('int.fsSystemCode')} error={err.secret}><Input dir="ltr" type="password" autoComplete="off" value={secret} onChange={(e) => setSecret(e.target.value)} placeholder={isNew ? '' : t('int.secretKeep')} /></Field>
          <Field label={t('int.fsStation')} error={err.externalId} className="sm:col-span-2"><Input dir="ltr" value={f.externalId} onChange={(e) => set('externalId', e.target.value)} placeholder="NE=12345678" /></Field>
        </>}
        {f.vendor === 'webhook' && (
          <div className="space-y-2 sm:col-span-2">
            <p className="text-xs text-slate-500">{t('int.webhookHint')}</p>
            <CopyRow label={t('int.ingestUrl')} value={INGEST_URL} />
            <CopyRow label={t('int.token')} value={f.ingestToken} />
            <div>
              <div className="mb-1 text-[11px] font-medium text-slate-500">{t('int.example')}</div>
              <pre dir="ltr" className="scrollbar-thin overflow-x-auto rounded-xl bg-slate-900 p-3 text-[11px] leading-relaxed text-slate-100">{`curl -X POST '${INGEST_URL}' \\
  -H 'x-ingest-token: ${f.ingestToken}' \\
  -H 'content-type: application/json' \\
  -d '{"powerKw": 42.5, "energyKwh": 180.2,
       "devices": [{"serial": "SN123", "status": "online", "efficiency": 97.1}]}'`}</pre>
            </div>
          </div>
        )}
        {secretKey && (
          <p className="flex items-start gap-1.5 text-xs text-slate-500 sm:col-span-2">
            <KeyRound className="mt-0.5 h-3.5 w-3.5 shrink-0" />{isSupabase ? t('int.secretNote') : t('int.demoNote')}
          </p>
        )}
      </form>
    </Modal>
  )
}

