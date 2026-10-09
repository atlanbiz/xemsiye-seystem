import Foundation

/// Calendar helpers that mirror `src/lib/utils.ts` (local-time JS `Date` arithmetic).
/// Always Gregorian, whatever calendar the device uses, so `YYYY-MM-DD` keys agree with the web.
public struct DayMath: Sendable {
    public let calendar: Calendar

    public init(timeZone: TimeZone = .current) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.locale = Locale(identifier: "en_US_POSIX")
        cal.firstWeekday = 2
        self.calendar = cal
    }

    public var timeZone: TimeZone { calendar.timeZone }

    // MARK: keys

    public static func pad2(_ n: Int) -> String { n < 10 && n >= 0 ? "0\(n)" : "\(n)" }

    public static func pad(_ n: Int, width: Int) -> String {
        let s = String(n)
        return s.count >= width ? s : String(repeating: "0", count: width - s.count) + s
    }

    /// Local date key `YYYY-MM-DD`.
    public func dayKey(_ d: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: d)
        return "\(DayMath.pad(c.year ?? 1970, width: 4))-\(DayMath.pad2(c.month ?? 1))-\(DayMath.pad2(c.day ?? 1))"
    }

    /// Local month key `YYYY-MM`.
    public func monthKey(_ d: Date) -> String {
        let c = calendar.dateComponents([.year, .month], from: d)
        return "\(DayMath.pad(c.year ?? 1970, width: 4))-\(DayMath.pad2(c.month ?? 1))"
    }

    // MARK: construction

    /// Equivalent of JS `new Date(year, monthIndex0, day)`, including overflow/underflow
    /// (e.g. day 0 = last day of the previous month).
    public func makeDate(year: Int, monthIndex0: Int, day: Int) -> Date {
        let jan1 = calendar.date(from: DateComponents(year: year, month: 1, day: 1)) ?? Date(timeIntervalSince1970: 0)
        let month = calendar.date(byAdding: .month, value: monthIndex0, to: jan1) ?? jan1
        return calendar.date(byAdding: .day, value: day - 1, to: month) ?? month
    }

    /// `parseDay("2026-06-21")` → local midnight of that day (JS `parseDay`).
    public func parseDay(_ s: String) -> Date {
        let parts = s.prefix(10).split(separator: "-").map { Int($0) ?? 0 }
        let y = parts.count > 0 ? parts[0] : 1970
        let m = parts.count > 1 ? parts[1] : 1
        let d = parts.count > 2 && parts[2] != 0 ? parts[2] : 1
        return makeDate(year: y, monthIndex0: m - 1, day: d)
    }

    /// `parseMonth("2026-05")` → (year, month1).
    public static func parseMonth(_ s: String) -> (year: Int, month: Int) {
        let parts = s.split(separator: "-").map { Int($0) ?? 0 }
        return (parts.count > 0 ? parts[0] : 1970, parts.count > 1 ? parts[1] : 1)
    }

    /// JS `new Date("YYYY-MM-DD")` — note: **UTC** midnight, as the web seed relies on it.
    public static func utcMidnight(_ s: String) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        let parts = s.prefix(10).split(separator: "-").map { Int($0) ?? 0 }
        let comps = DateComponents(year: parts.count > 0 ? parts[0] : 1970,
                                   month: parts.count > 1 ? parts[1] : 1,
                                   day: parts.count > 2 ? parts[2] : 1)
        return cal.date(from: comps) ?? Date(timeIntervalSince1970: 0)
    }

    public func startOfDay(_ d: Date) -> Date { calendar.startOfDay(for: d) }

    /// JS `addDays` (keeps the wall-clock time).
    public func addDays(_ d: Date, _ n: Int) -> Date {
        calendar.date(byAdding: .day, value: n, to: d) ?? d.addingTimeInterval(Double(n) * 86_400)
    }

    /// JS `addMonths` → first day of the month `n` months away.
    public func addMonths(_ d: Date, _ n: Int) -> Date {
        let c = calendar.dateComponents([.year, .month], from: d)
        return makeDate(year: c.year ?? 1970, monthIndex0: (c.month ?? 1) - 1 + n, day: 1)
    }

    public func startOfMonth(_ d: Date) -> Date { addMonths(d, 0) }

    /// Last day of the month containing `d` (local midnight).
    public func endOfMonth(_ d: Date) -> Date {
        let c = calendar.dateComponents([.year, .month], from: d)
        return makeDate(year: c.year ?? 1970, monthIndex0: c.month ?? 1, day: 0)
    }

    /// Every local midnight from `from`'s day to `to` inclusive (JS `daysBetween`).
    public func daysBetween(_ from: Date, _ to: Date) -> [Date] {
        var out: [Date] = []
        var d = startOfDay(from)
        while d <= to {
            out.append(d)
            d = addDays(d, 1)
        }
        return out
    }

    /// Fractional local hour (h + m/60 + s/3600).
    public func hourOfDay(_ d: Date) -> Double {
        let c = calendar.dateComponents([.hour, .minute, .second], from: d)
        return Double(c.hour ?? 0) + Double(c.minute ?? 0) / 60 + Double(c.second ?? 0) / 3600
    }

    public func hour(_ d: Date) -> Int { calendar.component(.hour, from: d) }

    /// Whole calendar days from `a` to `b` (local days).
    public func daysFrom(_ a: Date, to b: Date) -> Int {
        calendar.dateComponents([.day], from: startOfDay(a), to: startOfDay(b)).day ?? 0
    }

    public func components(_ d: Date) -> (year: Int, month: Int, day: Int, weekday: Int) {
        let c = calendar.dateComponents([.year, .month, .day, .weekday], from: d)
        return (c.year ?? 1970, c.month ?? 1, c.day ?? 1, c.weekday ?? 1)
    }
}

/// ISO-8601 timestamps as the web writes them (`Date.toISOString()` → `2026-10-08T09:41:00.000Z`).
public enum ISODate {
    public static func string(_ d: Date) -> String {
        Date.ISO8601FormatStyle(includingFractionalSeconds: true).format(d)
    }

    /// Parses `…Z`, `…+00:00`, with or without (any number of) fractional digits, or a bare `YYYY-MM-DD`.
    public static func parse(_ s: String) -> Date? {
        if s.count == 10 { return DayMath.utcMidnight(s) }
        var text = s
        // Normalise fractional seconds (Postgres gives 6 digits) away: precision below 1 s is irrelevant here.
        if let dot = text.firstIndex(of: ".") {
            var end = text.index(after: dot)
            while end < text.endIndex, text[end].isNumber { end = text.index(after: end) }
            text.removeSubrange(dot..<end)
        }
        // "+00" → "+00:00" (Postgres short offset)
        if text.count >= 3 {
            let tail = text.suffix(3)
            if let sign = tail.first, sign == "+" || sign == "-", tail.dropFirst().allSatisfy(\.isNumber) {
                text += ":00"
            }
        }
        if text.contains(" ") && !text.contains("T") { text = text.replacingOccurrences(of: " ", with: "T") }
        if let t = text.firstIndex(of: "T") {
            let time = text[t...]
            if !time.contains("Z") && !time.contains("+") && !time.contains("-") { text += "Z" }
        }
        if let d = try? Date.ISO8601FormatStyle().parse(text) { return d }
        if let d = try? Date.ISO8601FormatStyle(timeZoneSeparator: .colon).parse(text) { return d }
        return nil
    }
}

/// JS `Math.round` (half rounds up, towards +∞).
@inline(__always) public func jsRound(_ x: Double) -> Double { (x + 0.5).rounded(.down) }
