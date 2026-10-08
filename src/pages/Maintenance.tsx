import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Plus, Pencil, Trash2, CalendarClock, AlertOctagon, CircleDot, Loader, CheckCircle2, User } from 'lucide-react'
import { useData } from '../context/data'
import { useI18n } from '../context/i18n'
import { useToast } from '../context/toast'
import { Badge, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, SearchInput, Segmented, Select, Stat, StatusBadge, Textarea } from '../components/ui'
import type { Ticket, TicketStatus } from '../lib/types'
import { cn, dayKey, addDays, uid } from '../lib/utils'
import type { DictKey } from '../i18n/en'

const COLS: TicketStatus[] = ['open', 'in_progress', 'resolved']
const PRIO_ORDER = { critical: 0, high: 1, medium: 2, low: 3 }

export default function Maintenance() {
  const { db, upsert, remove, notify } = useData()
  const { t, fmt } = useI18n()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [view, setView] = useState<'board' | 'list'>('board')
  const [q, setQ] = useState('')
  const [prio, setPrio] = useState('all')
  const site = params.get('site') ?? 'all'
  const [form, setForm] = useState<{ open: boolean; tk: Ticket | null }>({ open: false, tk: null })
  const [del, setDel] = useState<Ticket | null>(null)
  const [dragOver, setDragOver] = useState<TicketStatus | null>(null)
  const today = dayKey(new Date())

  // deep link ?open=<id>
  useEffect(() => {
    const id = params.get('open')
    if (id) {
      const tk = db.tickets.find((x) => x.id === id)
      if (tk) setForm({ open: true, tk })
      const p = new URLSearchParams(params)
      p.delete('open')
      setParams(p, { replace: true })
    }
  }, [params, db.tickets, setParams])

  const siteName = (id: string) => db.sites.find((s) => s.id === id)?.name ?? '—'
  const rows = useMemo(() => {
    const s = q.toLowerCase()
    return db.tickets
      .filter((x) => (prio === 'all' || x.priority === prio) && (site === 'all' || x.siteId === site))
      .filter((x) => !s || [x.title, x.description, x.assignee, siteName(x.siteId)].some((v) => v.toLowerCase().includes(s)))
      .sort((a, b) => PRIO_ORDER[a.priority] - PRIO_ORDER[b.priority] || a.dueDate.localeCompare(b.dueDate))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [db.tickets, q, prio, site])

  const overdue = db.tickets.filter((x) => x.status !== 'resolved' && x.dueDate < today)
  const upcoming = db.tickets.filter((x) => x.status !== 'resolved' && x.dueDate >= today && x.dueDate <= dayKey(addDays(new Date(), 7)))

  const move = async (id: string, status: TicketStatus) => {
    const tk = db.tickets.find((x) => x.id === id)
    if (!tk || tk.status === status) return
    await upsert('tickets', { ...tk, status })
    toast(`${tk.title} → ${t(`status.${status}` as DictKey)}`, 'info')
  }

  const prioTone = { critical: 'red', high: 'amber', medium: 'blue', low: 'slate' } as const

  const TicketCard = ({ x }: { x: Ticket }) => (
    <div
      draggable
      onDragStart={(e) => e.dataTransfer.setData('text/plain', x.id)}
      onClick={() => setForm({ open: true, tk: x })}
      className="cursor-grab rounded-xl border border-slate-100 bg-white p-3 shadow-sm transition hover:shadow-md active:cursor-grabbing dark:border-slate-700 dark:bg-slate-800"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="text-sm font-medium">{x.title}</div>
        <Badge tone={prioTone[x.priority]}>{t(`prio.${x.priority}` as DictKey)}</Badge>
      </div>
      <div className="mt-1 line-clamp-2 text-xs text-slate-500">{x.description}</div>
      <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-slate-500">
        <span className="truncate">{siteName(x.siteId)}</span>
        <span className={cn('flex shrink-0 items-center gap-1', x.status !== 'resolved' && x.dueDate < today && 'font-semibold text-rose-600')}><CalendarClock className="h-3 w-3" />{fmt.date(x.dueDate)}</span>
      </div>
      {x.assignee && <div className="mt-1 flex items-center gap-1 text-[11px] text-slate-500"><User className="h-3 w-3" />{x.assignee}</div>}
    </div>
  )

  return (
    <div>
      <PageHeader title={t('mt.title')} subtitle={t('mt.subtitle')} actions={<button className="btn btn-primary" onClick={() => setForm({ open: true, tk: null })}><Plus className="h-4 w-4" />{t('mt.add')}</button>} />
      <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat icon={<CircleDot className="h-5 w-5" />} label={t('mt.openCount')} value={db.tickets.filter((x) => x.status === 'open').length} />
        <Stat icon={<Loader className="h-5 w-5" />} tone="violet" label={t('mt.inProgress')} value={db.tickets.filter((x) => x.status === 'in_progress').length} />
        <Stat icon={<AlertOctagon className="h-5 w-5" />} tone="red" label={t('mt.overdue')} value={overdue.length} />
        <Stat icon={<CheckCircle2 className="h-5 w-5" />} tone="green" label={t('mt.upcoming')} value={upcoming.length} />
      </div>
      <div className="card mb-4 flex flex-wrap items-center gap-2 !p-3">
        <SearchInput value={q} onChange={setQ} placeholder={t('common.search')} />
        <div className="w-44"><Select value={site} onChange={(e) => { const p = new URLSearchParams(params); if (e.target.value === 'all') p.delete('site'); else p.set('site', e.target.value); setParams(p) }}><option value="all">{t('an.allSites')}</option>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></div>
        <div className="w-40"><Select value={prio} onChange={(e) => setPrio(e.target.value)}><option value="all">{t('mt.priority')}: {t('common.all')}</option>{(['critical', 'high', 'medium', 'low'] as const).map((x) => <option key={x} value={x}>{t(`prio.${x}`)}</option>)}</Select></div>
        <div className="flex-1" />
        {view === 'board' && <span className="hidden text-xs text-slate-400 md:inline">{t('mt.dragHint')}</span>}
        <Segmented value={view} onChange={setView} options={[{ value: 'board', label: t('common.board') }, { value: 'list', label: t('common.list') }]} />
      </div>

      {view === 'board' ? (
        <div className="grid gap-4 lg:grid-cols-3">
          {COLS.map((col) => {
            const items = rows.filter((x) => x.status === col)
            return (
              <div
                key={col}
                onDragOver={(e) => { e.preventDefault(); setDragOver(col) }}
                onDragLeave={() => setDragOver(null)}
                onDrop={(e) => { e.preventDefault(); setDragOver(null); move(e.dataTransfer.getData('text/plain'), col) }}
                className={cn('rounded-2xl border-2 border-dashed border-transparent bg-slate-100/60 p-3 transition dark:bg-slate-800/40', dragOver === col && 'border-brand-400 bg-brand-50/60')}
              >
                <div className="mb-3 flex items-center justify-between px-1">
                  <StatusBadge status={col} label={t(`status.${col}` as DictKey)} />
                  <span className="text-xs text-slate-500">{items.length}</span>
                </div>
                <div className="min-h-24 space-y-2">
                  {items.map((x) => <TicketCard key={x.id} x={x} />)}
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="card overflow-x-auto !p-0 scrollbar-thin">
          {rows.length === 0 ? <EmptyState text={t('common.noData')} /> : (
            <table className="table-base">
              <thead><tr><th>{t('mt.ticketTitle')}</th><th>{t('common.site')}</th><th>{t('mt.priority')}</th><th>{t('mt.assignee')}</th><th>{t('mt.due')}</th><th>{t('common.status')}</th><th /></tr></thead>
              <tbody>
                {rows.map((x) => (
                  <tr key={x.id} className="cursor-pointer" onClick={() => setForm({ open: true, tk: x })}>
                    <td className="max-w-64 truncate font-medium">{x.title}</td>
                    <td className="max-w-40 truncate">{siteName(x.siteId)}</td>
                    <td><Badge tone={prioTone[x.priority]}>{t(`prio.${x.priority}` as DictKey)}</Badge></td>
                    <td>{x.assignee || '—'}</td>
                    <td className={cn(x.status !== 'resolved' && x.dueDate < today && 'font-semibold text-rose-600')}>{fmt.date(x.dueDate)}</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <select value={x.status} onChange={(e) => move(x.id, e.target.value as TicketStatus)} className="input !w-auto !py-1 text-xs">
                        {COLS.map((c) => <option key={c} value={c}>{t(`status.${c}` as DictKey)}</option>)}
                      </select>
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <div className="flex gap-1">
                        <button className="icon-btn h-7 w-7" onClick={() => setForm({ open: true, tk: x })}><Pencil className="h-3.5 w-3.5" /></button>
                        <button className="icon-btn h-7 w-7 hover:!text-rose-600" onClick={() => setDel(x)}><Trash2 className="h-3.5 w-3.5" /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <TicketForm
        open={form.open}
        initial={form.tk}
        onClose={() => setForm({ open: false, tk: null })}
        onDelete={(tk) => { setForm({ open: false, tk: null }); setDel(tk) }}
        onSave={async (tk) => {
          const isNew = !db.tickets.some((x) => x.id === tk.id)
          await upsert('tickets', tk)
          if (isNew && db.settings.notifyMaintenance) await notify({ title: t('mt.created'), body: `${tk.title} · ${siteName(tk.siteId)}`, kind: tk.priority === 'critical' ? 'danger' : 'info', link: `/maintenance?open=${tk.id}` })
          toast(isNew ? t('mt.created') : t('common.saved'))
          setForm({ open: false, tk: null })
        }}
      />
      <ConfirmDialog open={!!del} onClose={() => setDel(null)} message={t('common.deleteConfirm')} onConfirm={async () => { if (del) { await remove('tickets', del.id); toast(t('common.deleted'), 'info') } }} />
    </div>
  )
}

function TicketForm({ open, initial, onClose, onSave, onDelete }: { open: boolean; initial: Ticket | null; onClose: () => void; onSave: (t: Ticket) => void; onDelete: (t: Ticket) => void }) {
  const { db } = useData()
  const { t } = useI18n()
  const blank = (): Ticket => ({ id: uid('tkt-'), siteId: db.sites[0]?.id ?? '', deviceId: null, title: '', description: '', priority: 'medium', status: 'open', assignee: '', dueDate: dayKey(addDays(new Date(), 3)), createdAt: new Date().toISOString() })
  const [f, setF] = useState<Ticket>(blank)
  const [err, setErr] = useState<Record<string, string>>({})
  useEffect(() => { if (open) { setF(initial ? { ...initial } : blank()); setErr({}) } }, [open, initial]) // eslint-disable-line react-hooks/exhaustive-deps
  const set = <K extends keyof Ticket>(k: K, v: Ticket[K]) => setF((p) => ({ ...p, [k]: v }))
  const devices = db.devices.filter((d) => d.siteId === f.siteId)
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const er: Record<string, string> = {}
    if (!f.title.trim()) er.title = t('common.required')
    if (!f.siteId) er.siteId = t('common.required')
    if (!f.dueDate) er.dueDate = t('common.required')
    setErr(er)
    if (!Object.keys(er).length) onSave({ ...f, title: f.title.trim() })
  }
  return (
    <Modal open={open} onClose={onClose} title={initial ? t('mt.edit') : t('mt.add')}
      footer={<>
        {initial && <button className="btn btn-ghost me-auto !text-rose-600" onClick={() => onDelete(initial)}><Trash2 className="h-4 w-4" />{t('common.delete')}</button>}
        <button className="btn btn-ghost" onClick={onClose}>{t('common.cancel')}</button>
        <button className="btn btn-primary" type="submit" form="tk-form">{t('common.save')}</button>
      </>}>
      <form id="tk-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label={t('mt.ticketTitle')} error={err.title} className="sm:col-span-2"><Input value={f.title} onChange={(e) => set('title', e.target.value)} autoFocus /></Field>
        <Field label={t('common.description')} className="sm:col-span-2"><Textarea value={f.description} onChange={(e) => set('description', e.target.value)} /></Field>
        <Field label={t('common.site')} error={err.siteId}><Select value={f.siteId} onChange={(e) => setF((p) => ({ ...p, siteId: e.target.value, deviceId: null }))}>{db.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></Field>
        <Field label={t('mt.device')}><Select value={f.deviceId ?? ''} onChange={(e) => set('deviceId', e.target.value || null)}><option value="">{t('common.none')}</option>{devices.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</Select></Field>
        <Field label={t('mt.priority')}><Select value={f.priority} onChange={(e) => set('priority', e.target.value as Ticket['priority'])}>{(['low', 'medium', 'high', 'critical'] as const).map((x) => <option key={x} value={x}>{t(`prio.${x}`)}</option>)}</Select></Field>
        <Field label={t('common.status')}><Select value={f.status} onChange={(e) => set('status', e.target.value as TicketStatus)}>{COLS.map((x) => <option key={x} value={x}>{t(`status.${x}` as DictKey)}</option>)}</Select></Field>
        <Field label={t('mt.assignee')}><Input value={f.assignee} onChange={(e) => set('assignee', e.target.value)} /></Field>
        <Field label={t('mt.due')} error={err.dueDate}><Input type="date" value={f.dueDate} onChange={(e) => set('dueDate', e.target.value)} /></Field>
      </form>
    </Modal>
  )
}
