import Foundation

/// Client-side alert rule evaluation for demo mode (PLATFORM.md §3.3), port of `src/lib/alerts.ts`.
/// With Supabase the `evaluate-alerts` Edge Function applies the same semantics on a cron.
public struct AlertEvaluator: Sendable {
    /// A rule fires at most once per 6 hours.
    public static let cooldown: TimeInterval = 6 * 3600
    /// `site_yield_below` is only checked after 14:00 local.
    public static let yieldCheckHour = 14

    public let sim: Simulator

    public init(sim: Simulator) {
        self.sim = sim
    }

    /// One entity that breaches a rule.
    public struct Match: Sendable, Equatable {
        public let entityId: String
        /// Site / device / invoice it names.
        public let label: String
        /// Measured value in the metric's unit (nil for `site_offline`).
        public let value: Double?
        public let link: String

        public init(entityId: String, label: String, value: Double?, link: String) {
            self.entityId = entityId
            self.label = label
            self.value = value
            self.link = link
        }
    }

    public struct Firing: Sendable, Equatable {
        public let rule: AlertRule
        public let matches: [Match]
        public var ruleId: String { rule.id }
    }

    public static func isCoolingDown(_ rule: AlertRule, now: Date) -> Bool {
        guard let last = rule.lastTriggeredAt, let d = ISODate.parse(last) else { return false }
        return now.timeIntervalSince(d) < cooldown
    }

    static func encodeQuery(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.!~*'()")
        return s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s
    }

    /// Entities currently breaching a rule (ignores `enabled` and the cooldown).
    public func matches(_ rule: AlertRule, sites: [Site], devices: [Device], invoices: [Invoice], now: Date) -> [Match] {
        let days = sim.days
        let inScope: (String) -> Bool = { siteId in rule.siteId == nil || rule.siteId == siteId }
        let siteName: (String) -> String = { id in sites.first { $0.id == id }?.name ?? id }
        let today = days.dayKey(now)
        switch rule.metric {
        case .siteYieldBelow:
            guard days.hour(now) >= AlertEvaluator.yieldCheckHour else { return [] }
            return sites.filter { inScope($0.id) && $0.capacityKw > 0 }.compactMap { (s: Site) -> Match? in
                let v = sim.siteDayKwhCached(s, day: today) / s.capacityKw
                guard v < rule.threshold else { return nil }
                return Match(entityId: s.id, label: s.name, value: v, link: "/sites/\(s.id)")
            }
        case .siteOffline:
            return sites.filter { inScope($0.id) && $0.status == .offline }
                .map { Match(entityId: $0.id, label: $0.name, value: nil, link: "/sites/\($0.id)") }
        case .deviceEfficiencyBelow, .deviceHealthBelow:
            let efficiency = rule.metric == .deviceEfficiencyBelow
            return devices.compactMap { (d: Device) -> Match? in
                let v = efficiency ? d.efficiency : d.health
                guard inScope(d.siteId), v < rule.threshold else { return nil }
                return Match(entityId: d.id, label: "\(d.name) · \(siteName(d.siteId))", value: v,
                             link: "/devices?q=\(AlertEvaluator.encodeQuery(d.name))")
            }
        case .deviceOfflineMinutes:
            return devices.compactMap { (d: Device) -> Match? in
                guard inScope(d.siteId), d.status == .offline, let seen = ISODate.parse(d.lastSeen) else { return nil }
                let v = now.timeIntervalSince(seen) / 60
                guard v > rule.threshold else { return nil }
                return Match(entityId: d.id, label: "\(d.name) · \(siteName(d.siteId))", value: v,
                             link: "/devices?q=\(AlertEvaluator.encodeQuery(d.name))")
            }
        case .invoiceOverdueDays:
            let t0 = days.parseDay(today)
            return invoices.compactMap { (inv: Invoice) -> Match? in
                guard inScope(inv.siteId), inv.status == .overdue else { return nil }
                let v = jsRound(t0.timeIntervalSince(days.parseDay(inv.dueAt)) / 86_400)
                guard v > rule.threshold else { return nil }
                return Match(entityId: inv.id, label: "\(inv.number) · \(inv.customer)", value: v, link: "/billing?open=\(inv.id)")
            }
        }
    }

    /// Enabled rules outside their 6-hour cooldown that have at least one match.
    public func evaluate(rules: [AlertRule], sites: [Site], devices: [Device], invoices: [Invoice], now: Date) -> [Firing] {
        rules.compactMap { (rule: AlertRule) -> Firing? in
            guard rule.enabled, !AlertEvaluator.isCoolingDown(rule, now: now) else { return nil }
            let m = matches(rule, sites: sites, devices: devices, invoices: invoices, now: now)
            return m.isEmpty ? nil : Firing(rule: rule, matches: m)
        }
    }

    /// Notification body: names up to three matches, then "+N" (web `describe`).
    public static func describe(_ matches: [Match], formatValue: (Match) -> String) -> String {
        let shown = matches.prefix(3).map { $0.value == nil ? $0.label : "\($0.label) (\(formatValue($0)))" }
        let more = matches.count - shown.count
        return shown.joined(separator: "; ") + (more > 0 ? " +\(more)" : "")
    }

    /// Default (non-localised) value text: `1.23 kWh/kWp`, `91.5%`, `75 min`, `12 d`.
    public static func defaultValueText(_ metric: AlertMetric, _ m: Match, fmt: Fmt) -> String {
        let unit = metric.unit
        let decimals = metric == .siteYieldBelow ? 2 : 0
        return "\(fmt.num(m.value ?? 0, decimals))\(unit == "%" ? "" : " ")\(unit)"
    }

    /// The notification a firing posts: kind = severity, title = rule name, link to the first match.
    public static func notification(for f: Firing, body: String, id: String, now: Date) -> AppNotification {
        AppNotification(id: id, title: f.rule.name, body: body, kind: f.rule.severity == .danger ? .danger : .warning,
                        link: f.matches.first?.link, read: false, createdAt: ISODate.string(now))
    }
}
