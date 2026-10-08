import { useEffect, useState } from 'react'
import type { Site } from '../lib/types'
import { useI18n } from '../context/i18n'
import { Field, Input, Modal, Select } from './ui'
import { dayKey, uid } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const empty = (): Site => ({
  id: uid('site-'),
  name: '',
  location: '',
  type: 'commercial',
  status: 'active',
  capacityKw: 50,
  batteryKwh: 0,
  pricePerKwh: 0.12,
  customer: '',
  installDate: dayKey(new Date()),
  lat: 43.82,
  lng: 87.61,
})

export default function SiteForm({ open, onClose, onSave, initial }: { open: boolean; onClose: () => void; onSave: (s: Site) => void; initial?: Site | null }) {
  const { t } = useI18n()
  const [f, setF] = useState<Site>(empty())
  const [err, setErr] = useState<Record<string, string>>({})
  useEffect(() => {
    if (open) {
      setF(initial ? { ...initial } : empty())
      setErr({})
    }
  }, [open, initial])

  const set = <K extends keyof Site>(k: K, v: Site[K]) => setF((p) => ({ ...p, [k]: v }))
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const er: Record<string, string> = {}
    if (!f.name.trim()) er.name = t('common.required')
    if (!f.location.trim()) er.location = t('common.required')
    if (!f.customer.trim()) er.customer = t('common.required')
    if (!(f.capacityKw > 0)) er.capacityKw = t('common.required')
    setErr(er)
    if (Object.keys(er).length) return
    onSave({ ...f, name: f.name.trim(), location: f.location.trim(), customer: f.customer.trim() })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initial ? t('sites.edit') : t('sites.add')}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-primary" form="site-form" type="submit">{t('common.save')}</button>
        </>
      }
    >
      <form id="site-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label={t('common.name')} error={err.name} className="sm:col-span-2"><Input value={f.name} onChange={(e) => set('name', e.target.value)} autoFocus /></Field>
        <Field label={t('common.location')} error={err.location}><Input value={f.location} onChange={(e) => set('location', e.target.value)} /></Field>
        <Field label={t('sites.customer')} error={err.customer}><Input value={f.customer} onChange={(e) => set('customer', e.target.value)} /></Field>
        <Field label={t('common.type')}>
          <Select value={f.type} onChange={(e) => set('type', e.target.value as Site['type'])}>
            {(['residential', 'commercial', 'industrial', 'utility'] as const).map((x) => <option key={x} value={x}>{t(`siteType.${x}` as DictKey)}</option>)}
          </Select>
        </Field>
        <Field label={t('common.status')}>
          <Select value={f.status} onChange={(e) => set('status', e.target.value as Site['status'])}>
            {(['active', 'idle', 'offline', 'maintenance'] as const).map((x) => <option key={x} value={x}>{t(`status.${x}` as DictKey)}</option>)}
          </Select>
        </Field>
        <Field label={t('sites.capacity')} error={err.capacityKw}><Input type="number" min={0.1} step="0.1" value={f.capacityKw} onChange={(e) => set('capacityKw', Number(e.target.value))} /></Field>
        <Field label={t('sites.battery')}><Input type="number" min={0} step="1" value={f.batteryKwh} onChange={(e) => set('batteryKwh', Number(e.target.value))} /></Field>
        <Field label={t('sites.price')}><Input type="number" min={0} step="0.01" value={f.pricePerKwh} onChange={(e) => set('pricePerKwh', Number(e.target.value))} /></Field>
        <Field label={t('sites.installDate')}><Input type="date" value={f.installDate} onChange={(e) => set('installDate', e.target.value)} /></Field>
        <Field label={t('sites.lat')}><Input type="number" step="0.0001" value={f.lat} onChange={(e) => set('lat', Number(e.target.value))} /></Field>
        <Field label={t('sites.lng')}><Input type="number" step="0.0001" value={f.lng} onChange={(e) => set('lng', Number(e.target.value))} /></Field>
      </form>
    </Modal>
  )
}
