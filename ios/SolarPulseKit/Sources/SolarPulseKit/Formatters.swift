import Foundation

/// Number / date formatting that mirrors the web's `fmt` (src/context/i18n.tsx).
/// Numbers always use Latin digits and en-US grouping, whatever the UI language.
public struct Fmt: Sendable {
    public let lang: AppLanguage
    public let currency: CurrencyCode
    public let days: DayMath

    public init(lang: AppLanguage, currency: CurrencyCode, days: DayMath = DayMath()) {
        self.lang = lang
        self.currency = currency
        self.days = days
    }

    static let numberLocale = Locale(identifier: "en_US")

    public var locale: Locale { Locale(identifier: lang.localeIdentifier) }

    // MARK: numbers

    /// Up to `d` fraction digits (JS `maximumFractionDigits`), grouped.
    public func num(_ v: Double, _ d: Int = 0) -> String {
        guard v.isFinite else { return "—" }
        return v.formatted(.number.precision(.fractionLength(0...max(0, d))).rounded(rule: .toNearestOrAwayFromZero).locale(Fmt.numberLocale))
    }

    /// Exactly two fraction digits.
    public func dec2(_ v: Double) -> String {
        guard v.isFinite else { return "—" }
        return v.formatted(.number.precision(.fractionLength(2)).rounded(rule: .toNearestOrAwayFromZero).locale(Fmt.numberLocale))
    }

    public func money(_ v: Double) -> String {
        guard v.isFinite else { return "—" }
        return v.formatted(.currency(code: currency.rawValue).precision(.fractionLength(0)).locale(Fmt.numberLocale))
    }

    public func money2(_ v: Double) -> String {
        guard v.isFinite else { return "—" }
        return v.formatted(.currency(code: currency.rawValue).precision(.fractionLength(2)).locale(Fmt.numberLocale))
    }

    /// Auto-scales kW → MW.
    public func power(_ kw: Double) -> String {
        kw >= 1000 ? "\(dec2(kw / 1000)) MW" : "\(num(kw, 1)) kW"
    }

    /// Auto-scales kWh → MWh → GWh.
    public func energy(_ kwh: Double) -> String {
        if kwh >= 1e6 { return "\(dec2(kwh / 1e6)) GWh" }
        if kwh >= 1000 { return "\(dec2(kwh / 1000)) MWh" }
        return "\(num(kwh, 1)) kWh"
    }

    public func pct(_ v: Double, _ d: Int = 1) -> String { "\(num(v, d))%" }

    public func signedPct(_ v: Double) -> String { "\(v >= 0 ? "+" : "")\(num(v, 1))%" }

    /// Compact axis labels with explicit units (web `axisEnergy` / `axisPower`).
    public static func axisEnergy(_ v: Double) -> String {
        if v >= 1e6 { return "\(trimmed(v / 1e6)) GWh" }
        if v >= 1000 { return "\(trimmed(v / 1000)) MWh" }
        return "\(Int(jsRound(v))) kWh"
    }

    public static func axisPower(_ v: Double) -> String {
        v >= 1000 ? "\(trimmed(v / 1000)) MW" : "\(Int(jsRound(v))) kW"
    }

    /// `+(v).toFixed(1)` → "1.5" / "2".
    static func trimmed(_ v: Double) -> String {
        let r = (v * 10).rounded() / 10
        return r == r.rounded() ? String(Int(r)) : String(format: "%.1f", r)
    }

    // MARK: dates

    static let ugMonths = ["يانۋار", "فېۋرال", "مارت", "ئاپرېل", "ماي", "ئىيۇن", "ئىيۇل", "ئاۋغۇست", "سېنتەبىر", "ئۆكتەبىر", "نويابىر", "دېكابىر"]
    static let ugDays = ["يەكشەنبە", "دۈشەنبە", "سەيشەنبە", "چارشەنبە", "پەيشەنبە", "جۈمە", "شەنبە"]
    static let ugDaysShort = ["يە", "دۈ", "سە", "چا", "پە", "جۈ", "شە"]

    private func style() -> Date.FormatStyle {
        Date.FormatStyle(date: nil, time: nil, locale: locale, calendar: days.calendar, timeZone: days.timeZone)
    }

    private func ug(_ d: Date, year: Bool, day: Bool, weekday: Bool) -> String {
        let c = days.components(d)
        let month = Fmt.ugMonths[(c.month - 1) % 12]
        if !day { return year ? "\(c.year)-يىلى \(month)" : month }
        let md = "\(c.day)-\(month)"
        let base = year ? "\(c.year)-يىلى \(md)" : md
        return weekday ? "\(base)، \(Fmt.ugDays[(c.weekday - 1) % 7])" : base
    }

    /// "Jun 21, 2026" — accepts a `Date`.
    public func date(_ d: Date) -> String {
        if lang == .ug { return ug(d, year: true, day: true, weekday: false) }
        return d.formatted(style().year().month(.abbreviated).day())
    }

    /// Accepts `YYYY-MM-DD` (local day) or an ISO timestamp.
    public func date(_ s: String) -> String {
        if s.count == 10 { return date(days.parseDay(s)) }
        guard let d = ISODate.parse(s) else { return s }
        return date(d)
    }

    public func dateLong(_ d: Date) -> String {
        if lang == .ug { return ug(d, year: true, day: true, weekday: true) }
        return d.formatted(style().weekday(.wide).year().month(.wide).day())
    }

    public func dateShort(_ d: Date) -> String {
        if lang == .ug { return ug(d, year: false, day: true, weekday: false) }
        return d.formatted(style().month(.abbreviated).day())
    }

    public func month(_ d: Date) -> String {
        if lang == .ug { return ug(d, year: true, day: false, weekday: false) }
        return d.formatted(style().year().month(.wide))
    }

    /// `YYYY-MM` → "May 2026".
    public func period(_ p: String) -> String {
        let (y, m) = DayMath.parseMonth(p)
        return month(days.makeDate(year: y, monthIndex0: m - 1, day: 1))
    }

    public func monthShort(_ d: Date) -> String {
        if lang == .ug { return ug(d, year: false, day: false, weekday: false) }
        return d.formatted(style().month(.abbreviated))
    }

    public func time(_ d: Date) -> String {
        let c = days.calendar.dateComponents([.hour, .minute], from: d)
        return "\(DayMath.pad2(c.hour ?? 0)):\(DayMath.pad2(c.minute ?? 0))"
    }

    public func weekday(_ d: Date) -> String {
        if lang == .ug { return Fmt.ugDaysShort[(days.components(d).weekday - 1) % 7] }
        return d.formatted(style().weekday(.abbreviated))
    }

    /// Relative time ("5 minutes ago").
    public func ago(_ iso: String, now: Date = Date()) -> String {
        guard let d = ISODate.parse(iso) else { return iso }
        return ago(d, now: now)
    }

    public func ago(_ d: Date, now: Date = Date()) -> String {
        let s = now.timeIntervalSince(d)
        if lang == .ug {
            if s < 60 { return "ھازىرلا" }
            if s < 3600 { return "\(Int(jsRound(s / 60))) مىنۇت ئىلگىرى" }
            if s < 86400 { return "\(Int(jsRound(s / 3600))) سائەت ئىلگىرى" }
            return "\(Int(jsRound(s / 86400))) كۈن ئىلگىرى"
        }
        let f = RelativeDateTimeFormatter()
        f.locale = locale
        f.calendar = days.calendar
        f.dateTimeStyle = .named
        f.unitsStyle = .full
        var comps = DateComponents()
        if s < 60 { comps.second = -Int(jsRound(s)) }
        else if s < 3600 { comps.minute = -Int(jsRound(s / 60)) }
        else if s < 86400 { comps.hour = -Int(jsRound(s / 3600)) }
        else { comps.day = -Int(jsRound(s / 86400)) }
        return f.localizedString(from: comps)
    }
}
