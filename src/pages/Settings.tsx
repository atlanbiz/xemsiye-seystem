import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { User, SlidersHorizontal, Bell, Leaf, Database, Download, Upload, RotateCcw, CheckCircle2, CloudOff, Plug } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n, LANGS } from '../context/i18n'
import { useToast } from '../context/toast'
import { Card, ConfirmDialog, Field, Input, PageHeader, Select, Toggle } from '../components/ui'
import Integrations from '../components/Integrations'
import { isSupabase } from '../lib/supabase'
import { normalizeDB } from '../lib/repo'
import { CITIES, cityLabel } from '../lib/weather'
import type { DB, Settings as S } from '../lib/types'
import { cn, downloadFile, initials } from '../lib/utils'
import type { DictKey } from '../i18n/en'

type Tab = 'profile' | 'preferences' | 'notifications' | 'environment' | 'integrations' | 'data'
const TABS: Tab[] = ['profile', 'preferences', 'notifications', 'environment', 'integrations', 'data']

export default function Settings() {
  const { db, updateSettings, replaceAll, resetDemo } = useData()
  const { t } = useI18n()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const tab: Tab = TABS.find((x) => x === params.get('tab')) ?? 'profile'
  const setTab = (id: Tab) => setParams(id === 'profile' ? {} : { tab: id }, { replace: true })
  const [f, setF] = useState<S>(db.settings)
  const [reset, setReset] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  useEffect(() => setF(db.settings), [db.settings])

  const set = <K extends keyof S>(k: K, v: S[K]) => setF((p) => ({ ...p, [k]: v }))
  const save = async (patch?: Partial<S>) => {
    await updateSettings(patch ?? f)
    toast(t('common.saved'))
  }
  // instant-apply preferences
  const apply = async <K extends keyof S>(k: K, v: S[K]) => {
    set(k, v)
    await updateSettings({ [k]: v } as Partial<S>)
  }

  const tabs: { id: Tab; icon: typeof User; key: DictKey }[] = [
    { id: 'profile', icon: User, key: 'set.profile' },
    { id: 'preferences', icon: SlidersHorizontal, key: 'set.preferences' },
    { id: 'notifications', icon: Bell, key: 'set.notifications' },
    { id: 'environment', icon: Leaf, key: 'set.environment' },
    { id: 'integrations', icon: Plug, key: 'set.integrations' },
    { id: 'data', icon: Database, key: 'set.data' },
  ]

  const exportJson = () => downloadFile(`solarpulse-backup-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(db, null, 2), 'application/json')
  const importJson = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fl = e.target.files?.[0]
    e.target.value = ''
    if (!fl) return
    try {
      const parsed = JSON.parse(await fl.text()) as DB
      if (!Array.isArray(parsed.sites) || !Array.isArray(parsed.devices) || !parsed.settings) throw new Error('shape')
      await replaceAll(normalizeDB(parsed))
      toast(t('set.importDone'))
    } catch {
      toast(t('set.importFail'), 'error')
    }
  }

  return (
    <div>
      <PageHeader title={t('set.title')} subtitle={t('set.subtitle')} />
      <div className="grid gap-4 lg:grid-cols-[220px_1fr]">
        <nav className="card flex gap-1 overflow-x-auto !p-2 lg:flex-col scrollbar-thin">
          {tabs.map(({ id, icon: Icon, key }) => (
            <button key={id} onClick={() => setTab(id)} className={cn('flex shrink-0 cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium transition', tab === id ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300' : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-700/50')}>
              <Icon className="h-4 w-4" />{t(key)}
            </button>
          ))}
        </nav>

        <div className="min-w-0">
          {tab === 'profile' && (
            <Card title={t('set.profile')}>
              <div className="mb-5 flex items-center gap-4">
                <div className="grid h-16 w-16 place-items-center rounded-full bg-gradient-to-br from-amber-300 to-orange-500 text-xl font-semibold text-white">{initials(f.userName) || 'U'}</div>
                <div><div className="font-semibold">{f.userName}</div><div className="text-sm text-slate-500">{f.email}</div></div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label={t('set.fullName')}><Input value={f.userName} onChange={(e) => set('userName', e.target.value)} /></Field>
                <Field label={t('set.email')}><Input type="email" value={f.email} onChange={(e) => set('email', e.target.value)} /></Field>
                <Field label={t('set.company')}><Input value={f.company} onChange={(e) => set('company', e.target.value)} /></Field>
                <Field label={t('set.role')}><Select value={f.role} onChange={(e) => set('role', e.target.value)}>{['admin', 'operator', 'viewer'].map((r) => <option key={r} value={r}>{t(`role.${r}` as DictKey)}</option>)}</Select></Field>
              </div>
              <div className="mt-4 flex justify-end"><button className="btn btn-primary" disabled={!f.userName.trim()} onClick={() => save({ userName: f.userName.trim(), email: f.email, company: f.company, role: f.role })}>{t('common.save')}</button></div>
            </Card>
          )}

          {tab === 'preferences' && (
            <Card title={t('set.preferences')}>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label={t('set.language')}><Select value={f.language} onChange={(e) => apply('language', e.target.value as S['language'])}>{LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</Select></Field>
                <Field label={t('set.theme')}><Select value={f.theme} onChange={(e) => apply('theme', e.target.value as S['theme'])}>{(['light', 'dark', 'system'] as const).map((x) => <option key={x} value={x}>{t(`set.theme.${x}`)}</option>)}</Select></Field>
                <Field label={t('set.currency')}><Select value={f.currency} onChange={(e) => apply('currency', e.target.value as S['currency'])}><option value="USD">USD ($)</option><option value="CNY">CNY (¥)</option><option value="EUR">EUR (€)</option></Select></Field>
                <Field label={t('set.discountRate')}>
                  <Input type="number" min={0} max={50} step="0.1" value={f.discountRatePct} onChange={(e) => set('discountRatePct', Number(e.target.value))} onBlur={() => f.discountRatePct >= 0 && f.discountRatePct < 100 && f.discountRatePct !== db.settings.discountRatePct && save({ discountRatePct: f.discountRatePct })} />
                </Field>
                <Field label={t('set.city')}>
                  <Select value={f.city} onChange={(e) => { const c = CITIES.find((x) => x.name === e.target.value); if (c) { setF((p) => ({ ...p, city: c.name, lat: c.lat, lng: c.lng })); updateSettings({ city: c.name, lat: c.lat, lng: c.lng }) } }}>
                    {!CITIES.some((c) => c.name === f.city) && <option value={f.city}>{f.city}</option>}
                    {CITIES.map((c) => <option key={c.name} value={c.name}>{cityLabel(c.name, f.language)}</option>)}
                  </Select>
                </Field>
              </div>
            </Card>
          )}

          {tab === 'notifications' && (
            <Card title={t('set.notifications')}>
              <div className="divide-y divide-slate-100 dark:divide-slate-700">
                <Toggle label={t('set.notifyPush')} checked={f.notifyPush} onChange={(v) => apply('notifyPush', v)} />
                <Toggle label={t('set.notifyEmail')} checked={f.notifyEmail} onChange={(v) => apply('notifyEmail', v)} />
                <Toggle label={t('set.notifyDevice')} checked={f.notifyDeviceAlerts} onChange={(v) => apply('notifyDeviceAlerts', v)} />
                <Toggle label={t('set.notifyBilling')} checked={f.notifyBilling} onChange={(v) => apply('notifyBilling', v)} />
                <Toggle label={t('set.notifyMaintenance')} checked={f.notifyMaintenance} onChange={(v) => apply('notifyMaintenance', v)} />
              </div>
            </Card>
          )}

          {tab === 'environment' && (
            <Card title={t('set.environment')}>
              <div className="grid gap-3 sm:grid-cols-3">
                <Field label={t('set.co2Factor')}><Input type="number" step="0.01" min={0} value={f.co2KgPerKwh} onChange={(e) => set('co2KgPerKwh', Number(e.target.value))} /></Field>
                <Field label={t('set.treeFactor')}><Input type="number" step="0.1" min={0.1} value={f.treeKgPerYear} onChange={(e) => set('treeKgPerYear', Number(e.target.value))} /></Field>
                <Field label={t('set.carFactor')}><Input type="number" step="0.1" min={0.1} value={f.carTonsPerYear} onChange={(e) => set('carTonsPerYear', Number(e.target.value))} /></Field>
              </div>
              <div className="mt-4 flex justify-end"><button className="btn btn-primary" disabled={!(f.treeKgPerYear > 0 && f.carTonsPerYear > 0 && f.co2KgPerKwh >= 0)} onClick={() => save({ co2KgPerKwh: f.co2KgPerKwh, treeKgPerYear: f.treeKgPerYear, carTonsPerYear: f.carTonsPerYear })}>{t('common.save')}</button></div>
            </Card>
          )}

          {tab === 'integrations' && <Integrations />}

          {tab === 'data' && (
            <div className="space-y-4">
              <Card title={t('set.storage')}>
                <div className={cn('flex items-center gap-3 rounded-xl p-3 text-sm', isSupabase ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' : 'bg-amber-50 text-amber-800 dark:bg-amber-500/10 dark:text-amber-300')}>
                  {isSupabase ? <CheckCircle2 className="h-5 w-5" /> : <CloudOff className="h-5 w-5" />}
                  {isSupabase ? t('set.storage.supabase') : t('set.storage.local')}
                </div>
                {!isSupabase && <p className="mt-3 text-xs leading-relaxed text-slate-500">{t('set.supabaseHint')}</p>}
              </Card>
              <Card title={t('set.data')}>
                <div className="flex flex-wrap gap-2">
                  <button className="btn btn-ghost" onClick={exportJson}><Download className="h-4 w-4" />{t('set.exportJson')}</button>
                  <button className="btn btn-ghost" onClick={() => file.current?.click()}><Upload className="h-4 w-4" />{t('set.importJson')}</button>
                  <input ref={file} type="file" accept="application/json" hidden onChange={importJson} />
                  <button className="btn btn-ghost !text-rose-600" onClick={() => setReset(true)}><RotateCcw className="h-4 w-4" />{t('set.reset')}</button>
                </div>
              </Card>
            </div>
          )}
        </div>
      </div>
      <ConfirmDialog open={reset} onClose={() => setReset(false)} danger={false} message={t('set.resetConfirm')} onConfirm={async () => { await resetDemo(); toast(t('set.resetDone')) }} />
    </div>
  )
}
