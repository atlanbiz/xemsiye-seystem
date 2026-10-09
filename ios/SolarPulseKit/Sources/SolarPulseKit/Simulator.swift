import Foundation

/// Deterministic solar production simulator — an exact port of `src/lib/sim.ts`.
/// Same inputs give the same kWh on web, iOS and Android (PLATFORM.md §2).
public struct Simulator: Sendable {
    public let days: DayMath
    /// Source of "now" (injectable for tests).
    public let clock: @Sendable () -> Date
    private let cache: SimCache

    /// System performance ratio.
    public static let performanceRatio = 0.82

    public init(timeZone: TimeZone = .current, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.days = DayMath(timeZone: timeZone)
        self.clock = clock
        self.cache = SimCache()
    }

    /// Shared live simulator in the device time zone.
    public static let live = Simulator()

    public var now: Date { clock() }
    public var todayKey: String { days.dayKey(clock()) }
    public func nowHour() -> Double { days.hourOfDay(clock()) }

    // MARK: - Primitives (bit-exact with the JS implementation)

    /// FNV-1a over UTF-16 code units with 32-bit wrapping multiply (`Math.imul`).
    public static func hash(_ s: String) -> UInt32 {
        var h: UInt32 = 2_166_136_261
        for unit in s.utf16 {
            h ^= UInt32(unit)
            h = h &* 16_777_619
        }
        return h
    }

    /// Mulberry32-style generator seeded by `hash`, returns [0, 1).
    public static func rand(_ seed: String) -> Double {
        var t: UInt32 = hash(seed) &+ 0x6D2B_79F5
        t = (t ^ (t >> 15)) &* (t | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }

    /// Normalised irradiance curve 0…1 for a fractional hour.
    public static func solarCurve(_ h: Double) -> Double {
        if h <= 6 || h >= 20 { return 0 }
        let x = sin((Double.pi * (h - 6)) / 14)
        return pow(max(0, x), 1.6)
    }

    /// Seasonal factor (northern hemisphere), day-of-year computed like JS (DST-aware ms difference).
    public func seasonFactor(_ d: Date) -> Double {
        let year = days.calendar.component(.year, from: d)
        let dec31 = days.makeDate(year: year, monthIndex0: 0, day: 0)
        let doy = Int(((d.timeIntervalSince1970 - dec31.timeIntervalSince1970) / 86_400).rounded(.down))
        return 0.68 + 0.32 * sin((2 * Double.pi * Double(doy - 80)) / 365)
    }

    /// Cloudiness factor for a day (shared by all sites — same weather region).
    public static func weatherFactor(_ day: String) -> Double {
        let r = rand("wx" + day)
        return r < 0.12 ? 0.35 + r * 2 : 0.78 + r * 0.22
    }

    func statusFactor(_ site: Site, _ day: String) -> Double {
        if day != todayKey { return 1 }
        switch site.status {
        case .offline, .maintenance: return 0
        case .idle: return 0.15
        case .active: return 1
        }
    }

    private struct DayFactors {
        let season: Double
        let weather: Double
        let siteVar: Double
        let status: Double
    }

    private func dayFactors(_ site: Site, _ day: String) -> DayFactors? {
        if day < site.installDate { return nil }
        let d = days.parseDay(day)
        return DayFactors(
            season: seasonFactor(d),
            weather: Simulator.weatherFactor(day),
            siteVar: 0.92 + Simulator.rand(site.id + day) * 0.1,
            status: statusFactor(site, day)
        )
    }

    @inline(__always)
    private static func kw(_ site: Site, _ f: DayFactors, _ hour: Double) -> Double {
        // identical multiplication order to sim.ts → bit-identical results
        site.capacityKw * performanceRatio * solarCurve(hour) * f.season * f.weather * f.siteVar * f.status
    }

    // MARK: - Site output

    /// Instantaneous kW output for a site at a given day + fractional hour.
    public func siteKw(_ site: Site, day: String, hour: Double) -> Double {
        guard let f = dayFactors(site, day) else { return 0 }
        return Simulator.kw(site, f, hour)
    }

    /// kWh produced by a site on a day. If `untilHour` is given, only up to that hour.
    public func siteDayKwh(_ site: Site, day: String, untilHour: Double = 24) -> Double {
        guard let f = dayFactors(site, day) else { return 0 }
        var sum = 0.0
        let step = 0.25
        var h = 0.0
        while h < untilHour {
            sum += Simulator.kw(site, f, h + step / 2) * min(step, untilHour - h)
            h += step
        }
        return sum
    }

    /// Full-day production cached for past days; today is integrated up to now.
    public func siteDayKwhCached(_ site: Site, day: String) -> Double {
        let today = todayKey
        if day >= today { return siteDayKwh(site, day: day, untilHour: day == today ? nowHour() : 24) }
        let key = "\(site.id)|\(site.capacityKw)|\(site.installDate)|\(day)"
        if let v = cache.get(key) { return v }
        let v = siteDayKwh(site, day: day)
        cache.set(key, v)
        return v
    }

    /// Fleet output now with a small live jitter so the "current power" visibly breathes.
    public func liveKw(_ sites: [Site], at t: Date? = nil) -> Double {
        let time = t ?? clock()
        let day = days.dayKey(time)
        let h = days.hourOfDay(time)
        let ms = time.timeIntervalSince1970 * 1000
        let bucket = Int64((ms / 5000).rounded(.down))
        let jitter = 1 + sin(ms / 7000) * 0.015 + (Simulator.rand(String(bucket)) - 0.5) * 0.02
        return sites.reduce(0.0) { $0 + siteKw($1, day: day, hour: h) } * jitter
    }

    public func totalKwh(_ sites: [Site], from: Date, to: Date) -> Double {
        var s = 0.0
        for d in days.daysBetween(from, to) {
            let k = days.dayKey(d)
            for site in sites { s += siteDayKwhCached(site, day: k) }
        }
        return s
    }

    public struct DailyPoint: Sendable, Identifiable, Equatable {
        public let key: String
        public let date: Date
        public let kwh: Double
        public var id: String { key }
    }

    public func dailySeries(_ sites: [Site], from: Date, to: Date) -> [DailyPoint] {
        days.daysBetween(from, to).map { d in
            let k = days.dayKey(d)
            return DailyPoint(key: k, date: d, kwh: sites.reduce(0.0) { $0 + siteDayKwhCached($1, day: k) })
        }
    }

    public struct HourlyPoint: Sendable, Identifiable, Equatable {
        public let hour: Double
        public let label: String
        public let kw: Double?
        public var id: Double { hour }
    }

    public func hourlySeries(_ sites: [Site], day: String, stepMin: Int = 30) -> [HourlyPoint] {
        let isToday = day == todayKey
        let limit = isToday ? nowHour() : 24
        var out: [HourlyPoint] = []
        var m = 0
        while m <= 24 * 60 {
            let h = Double(m) / 60
            let label = "\(DayMath.pad2(Int(h.rounded(.down)) % 24)):\(DayMath.pad2(m % 60))"
            let kw: Double? = h <= limit ? sites.reduce(0.0) { $0 + siteKw($1, day: day, hour: h) } : nil
            out.append(HourlyPoint(hour: h, label: label, kw: kw))
            m += stepMin
        }
        return out
    }

    /// Building load model, kW.
    public func consumptionKw(_ sites: [Site], hour h: Double, day: String) -> Double {
        let cap = sites.reduce(0.0) { $0 + $1.capacityKw }
        let base = 0.09 + 0.07 * exp(-pow(h - 9, 2) / 6) + 0.12 * exp(-pow(h - 19.5, 2) / 5) + 0.08 * Simulator.solarCurve(h)
        let slot = Int((h * 4).rounded(.down))
        return cap * base * (0.95 + Simulator.rand("load" + day + String(slot)) * 0.1)
    }

    public struct Flow: Sendable, Equatable {
        public let solar: Double
        public let consumption: Double
        /// + charging, − discharging
        public let battery: Double
        /// + import, − export
        public let grid: Double
        public let batterySoc: Double
        public let batteryCapacity: Double
    }

    public func energyFlow(_ sites: [Site], at t: Date? = nil) -> Flow {
        let time = t ?? clock()
        let day = days.dayKey(time)
        let h = days.hourOfDay(time)
        let solar = liveKw(sites, at: time)
        let consumption = consumptionKw(sites, hour: h, day: day)
        let batteryCapacity = sites.reduce(0.0) { $0 + $1.batteryKwh }
        let soc = max(0.15, min(0.98, 0.35 + 0.6 * sin((Double.pi * (max(0, min(h, 22) - 8))) / 18)))
        let maxRate = batteryCapacity * 0.25
        var battery = 0.0
        var grid = 0.0
        let net = solar - consumption
        if net >= 0 {
            battery = soc < 0.97 ? min(net, maxRate) : 0
            grid = -(net - battery)
        } else {
            let need = -net
            let discharge = soc > 0.2 ? min(need, maxRate) : 0
            battery = -discharge
            grid = need - discharge
        }
        return Flow(solar: solar, consumption: consumption, battery: battery, grid: grid,
                    batterySoc: soc * 100, batteryCapacity: batteryCapacity)
    }

    public static func co2Tons(_ kwh: Double, factor: Double) -> Double { (kwh * factor) / 1000 }

    public struct DeviceStats: Sendable, Equatable {
        public let availability: Double
        public let inverterEfficiency: Double
        public let batteryHealth: Double
        public let online: Int
        public let warning: Int
        public let offline: Int
    }

    public static func deviceStats(_ devices: [Device]) -> DeviceStats {
        let total = Double(max(devices.count, 1))
        let online = devices.filter { $0.status == .online }.count
        let warning = devices.filter { $0.status == .warning }.count
        let inv = devices.filter { $0.type == .inverter }
        let bat = devices.filter { $0.type == .battery }
        func avg(_ a: [Device], _ f: (Device) -> Double) -> Double {
            a.isEmpty ? 0 : a.reduce(0.0) { $0 + f($1) } / Double(a.count)
        }
        return DeviceStats(
            availability: ((Double(online) + Double(warning) * 0.5) / total) * 100,
            inverterEfficiency: avg(inv) { $0.status == .offline ? 0 : $0.efficiency },
            batteryHealth: avg(bat) { $0.health },
            online: online,
            warning: warning,
            offline: devices.count - online - warning
        )
    }

    public enum AnalyticsRange: String, Sendable, CaseIterable, Identifiable {
        case d7 = "7d", d30 = "30d", d90 = "90d", m12 = "12m"
        public var id: String { rawValue }
        public var daysBack: Int {
            switch self {
            case .d7: return 6
            case .d30: return 29
            case .d90: return 89
            case .m12: return 364
            }
        }
    }

    /// Range helpers relative to today.
    public func rangeOf(_ r: AnalyticsRange) -> (from: Date, to: Date) {
        let to = clock()
        return (days.addDays(to, -r.daysBack), to)
    }

    public struct SimWeather: Sendable, Equatable {
        public let temp: Int
        public let code: Int
        public let irradiance: Int
        public let wind: Int
        public let humidity: Int
    }

    /// Simulated weather for when the network weather API is unavailable.
    public func simWeather(at t: Date? = nil) -> SimWeather {
        let time = t ?? clock()
        let day = days.dayKey(time)
        let wf = Simulator.weatherFactor(day)
        let c = days.calendar.dateComponents([.hour, .minute], from: time)
        let h = Double(c.hour ?? 0) + Double(c.minute ?? 0) / 60
        let seasonal = seasonFactor(time)
        let temp = jsRound(-5 + seasonal * 32 + 6 * sin((Double.pi * (h - 8)) / 12) + (Simulator.rand("t" + day) - 0.5) * 4)
        return SimWeather(
            temp: Int(temp),
            code: wf < 0.6 ? 3 : wf < 0.85 ? 2 : 0,
            irradiance: Int(jsRound(1000 * Simulator.solarCurve(h) * seasonal * wf)),
            wind: Int(jsRound(4 + Simulator.rand("w" + day) * 18)),
            humidity: Int(jsRound(25 + Simulator.rand("h" + day) * 40))
        )
    }

    /// Drops all cached past-day results (e.g. after a site's capacity changed — keys include it anyway).
    public func clearCache() { cache.clear() }
}

/// Thread-safe memo for past-day production.
final class SimCache: @unchecked Sendable {
    private var map: [String: Double] = [:]
    private let lock = NSLock()

    func get(_ k: String) -> Double? {
        lock.lock()
        defer { lock.unlock() }
        return map[k]
    }

    func set(_ k: String, _ v: Double) {
        lock.lock()
        defer { lock.unlock() }
        if map.count > 200_000 { map.removeAll(keepingCapacity: true) }
        map[k] = v
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        map.removeAll()
    }
}

/// WMO weather code → kind (mirrors `weatherKind` in src/lib/weather.ts).
public enum WeatherKind: String, Sendable, CaseIterable {
    case sunny, partly, cloudy, fog, rain, snow, storm

    public init(code: Int) {
        switch code {
        case 0, 1: self = .sunny
        case 2: self = .partly
        case 3: self = .cloudy
        case 45, 48: self = .fog
        case 71...77, 85, 86: self = .snow
        case let c where c >= 95: self = .storm
        default: self = .rain
        }
    }
}
