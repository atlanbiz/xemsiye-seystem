import { useCallback, useMemo } from 'react'
import { en, type DictKey } from '../i18n/en'
import { ug } from '../i18n/ug'
import { useData } from './data'

const dicts = { en, ug }

export function useI18n() {
  const { db } = useData()
  const lang = db.settings.language
  const currency = db.settings.currency
  const locale = lang === 'ug' ? 'ug-CN' : 'en-US'

  const t = useCallback(
    (key: DictKey, vars?: Record<string, string | number>) => {
      let s: string = dicts[lang][key] ?? en[key] ?? key
      if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v))
      return s
    },
    [lang],
  )

  const fmt = useMemo(() => {
    const nf = (max = 0, min = 0) => new Intl.NumberFormat('en-US', { maximumFractionDigits: max, minimumFractionDigits: min })
    const n0 = nf(0)
    const n1 = nf(1)
    const n2 = nf(2, 2)
    const money = new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 0 })
    const money2 = new Intl.NumberFormat('en-US', { style: 'currency', currency, minimumFractionDigits: 2 })
    const safeDate = (opts: Intl.DateTimeFormatOptions): { format: (d: Date) => string } => {
      // Many browsers ship without Uyghur ICU data, so format Uyghur dates ourselves.
      if (lang === 'ug') return { format: (d: Date) => ugDate(d, opts) }
      return new Intl.DateTimeFormat(locale, opts)
    }
    const dShort = safeDate({ month: 'short', day: 'numeric' })
    const dLong = safeDate({ year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
    const dDate = safeDate({ year: 'numeric', month: 'short', day: 'numeric' })
    const dMonth = safeDate({ year: 'numeric', month: 'long' })
    const dMonthShort = safeDate({ month: 'short' })
    const dTime = safeDate({ hour: '2-digit', minute: '2-digit', hour12: false })
    const dWeekday = safeDate({ weekday: 'short' })
    return {
      num: (v: number, d = 0) => (d === 0 ? n0 : d === 1 ? n1 : nf(d)).format(v),
      dec2: (v: number) => n2.format(v),
      money: (v: number) => money.format(v),
      money2: (v: number) => money2.format(v),
      /** auto-scales kW → MW */
      power: (kw: number) => (kw >= 1000 ? `${n2.format(kw / 1000)} MW` : `${n1.format(kw)} kW`),
      /** auto-scales kWh → MWh → GWh */
      energy: (kwh: number) =>
        kwh >= 1e6 ? `${n2.format(kwh / 1e6)} GWh` : kwh >= 1000 ? `${n2.format(kwh / 1000)} MWh` : `${n1.format(kwh)} kWh`,
      pct: (v: number, d = 1) => `${nf(d).format(v)}%`,
      signedPct: (v: number) => `${v >= 0 ? '+' : ''}${n1.format(v)}%`,
      date: (d: Date | string) => dDate.format(typeof d === 'string' ? new Date(d.length === 10 ? d + 'T00:00' : d) : d),
      dateLong: (d: Date) => dLong.format(d),
      dateShort: (d: Date) => dShort.format(d),
      month: (d: Date) => dMonth.format(d),
      monthShort: (d: Date) => dMonthShort.format(d),
      time: (d: Date | string) => dTime.format(typeof d === 'string' ? new Date(d) : d),
      weekday: (d: Date) => dWeekday.format(d),
      ago: (iso: string) => {
        const s = (Date.now() - new Date(iso).getTime()) / 1000
        if (lang === 'ug') {
          if (s < 60) return 'ھازىرلا'
          if (s < 3600) return `${Math.round(s / 60)} مىنۇت ئىلگىرى`
          if (s < 86400) return `${Math.round(s / 3600)} سائەت ئىلگىرى`
          return `${Math.round(s / 86400)} كۈن ئىلگىرى`
        }
        const rtf = (() => {
          try {
            return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
          } catch {
            return new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
          }
        })()
        if (s < 60) return rtf.format(-Math.round(s), 'second')
        if (s < 3600) return rtf.format(-Math.round(s / 60), 'minute')
        if (s < 86400) return rtf.format(-Math.round(s / 3600), 'hour')
        return rtf.format(-Math.round(s / 86400), 'day')
      },
    }
  }, [currency, locale, lang])

  return { t, fmt, lang, locale, dir: lang === 'ug' ? ('rtl' as const) : ('ltr' as const) }
}

export type TFn = ReturnType<typeof useI18n>['t']

const UG_MONTHS = ['يانۋار', 'فېۋرال', 'مارت', 'ئاپرېل', 'ماي', 'ئىيۇن', 'ئىيۇل', 'ئاۋغۇست', 'سېنتەبىر', 'ئۆكتەبىر', 'نويابىر', 'دېكابىر']
const UG_DAYS = ['يەكشەنبە', 'دۈشەنبە', 'سەيشەنبە', 'چارشەنبە', 'پەيشەنبە', 'جۈمە', 'شەنبە']
const UG_DAYS_SHORT = ['يە', 'دۈ', 'سە', 'چا', 'پە', 'جۈ', 'شە']

function ugDate(d: Date, o: Intl.DateTimeFormatOptions) {
  const p2 = (n: number) => String(n).padStart(2, '0')
  if (o.hour) return `${p2(d.getHours())}:${p2(d.getMinutes())}`
  if (o.weekday && !o.month) return o.weekday === 'short' ? UG_DAYS_SHORT[d.getDay()] : UG_DAYS[d.getDay()]
  const month = UG_MONTHS[d.getMonth()]
  if (!o.day) return o.year ? `${d.getFullYear()}-يىلى ${month}` : month
  const md = `${d.getDate()}-${month}`
  const base = o.year ? `${d.getFullYear()}-يىلى ${md}` : md
  return o.weekday ? `${base}، ${UG_DAYS[d.getDay()]}` : base
}
