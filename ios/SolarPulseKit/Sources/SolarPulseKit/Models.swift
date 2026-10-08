import Foundation

// MARK: - Enumerations (raw values match the Supabase check constraints, see supabase/schema.sql)

public enum SiteStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case active, idle, offline, maintenance
    public var id: String { rawValue }
}

public enum SiteType: String, Codable, Sendable, CaseIterable, Identifiable {
    case residential, commercial, industrial, utility
    public var id: String { rawValue }
}

public enum DeviceType: String, Codable, Sendable, CaseIterable, Identifiable {
    case inverter, battery, panel, meter, sensor
    public var id: String { rawValue }
}

public enum DeviceStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case online, warning, offline
    public var id: String { rawValue }
}

public enum TicketPriority: String, Codable, Sendable, CaseIterable, Identifiable {
    case low, medium, high, critical
    public var id: String { rawValue }
    /// Sort order used by the web board (critical first).
    public var order: Int {
        switch self {
        case .critical: return 0
        case .high: return 1
        case .medium: return 2
        case .low: return 3
        }
    }
}

public enum TicketStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case open
    case inProgress = "in_progress"
    case resolved
    public var id: String { rawValue }
}

public enum InvoiceStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case paid, pending, overdue
    public var id: String { rawValue }
}

public enum NotificationKind: String, Codable, Sendable, CaseIterable, Identifiable {
    case info, success, warning, danger
    public var id: String { rawValue }
}

public enum ReportKind: String, Codable, Sendable, CaseIterable, Identifiable {
    case energy, financial, devices, maintenance, environment
    public var id: String { rawValue }
}

public enum AppLanguage: String, Codable, Sendable, CaseIterable, Identifiable {
    case ug, en, ar, tr
    public var id: String { rawValue }
    public var isRTL: Bool { self == .ug || self == .ar }
    /// Native name, as shown in the language picker.
    public var nativeName: String {
        switch self {
        case .ug: return "ئۇيغۇرچە"
        case .en: return "English"
        case .ar: return "العربية"
        case .tr: return "Türkçe"
        }
    }
    /// Locale used for dates and relative times. Arabic forces Latin digits.
    public var localeIdentifier: String {
        switch self {
        case .ug: return "ug_CN"
        case .en: return "en_US"
        case .ar: return "ar_EG@numbers=latn"
        case .tr: return "tr_TR"
        }
    }
}

public enum AppTheme: String, Codable, Sendable, CaseIterable, Identifiable {
    case light, dark, system
    public var id: String { rawValue }
}

public enum CurrencyCode: String, Codable, Sendable, CaseIterable, Identifiable {
    case USD, CNY, EUR
    public var id: String { rawValue }
}

public enum UserRole: String, Codable, Sendable, CaseIterable, Identifiable {
    case admin, operator_ = "operator", viewer
    public var id: String { rawValue }
}

public enum AlertMetric: String, Codable, Sendable, CaseIterable, Identifiable {
    case siteYieldBelow = "site_yield_below"
    case siteOffline = "site_offline"
    case deviceEfficiencyBelow = "device_efficiency_below"
    case deviceHealthBelow = "device_health_below"
    case deviceOfflineMinutes = "device_offline_minutes"
    case invoiceOverdueDays = "invoice_overdue_days"
    public var id: String { rawValue }

    /// Unit of the threshold (empty for `site_offline`, which has none).
    public var unit: String {
        switch self {
        case .siteYieldBelow: return "kWh/kWp"
        case .siteOffline: return ""
        case .deviceEfficiencyBelow, .deviceHealthBelow: return "%"
        case .deviceOfflineMinutes: return "min"
        case .invoiceOverdueDays: return "d"
        }
    }

    public var usesThreshold: Bool { self != .siteOffline }

    public var defaultThreshold: Double {
        switch self {
        case .siteYieldBelow: return 1.5
        case .siteOffline: return 0
        case .deviceEfficiencyBelow: return 95
        case .deviceHealthBelow: return 80
        case .deviceOfflineMinutes: return 60
        case .invoiceOverdueDays: return 7
        }
    }
}

public enum AlertSeverity: String, Codable, Sendable, CaseIterable, Identifiable {
    case warning, danger
    public var id: String { rawValue }
}

public enum IntegrationVendor: String, Codable, Sendable, CaseIterable, Identifiable {
    case solaredge, fusionsolar, webhook
    public var id: String { rawValue }
}

public enum IntegrationStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case pending, ok, error
    public var id: String { rawValue }
}

// MARK: - Lenient decoding helpers

extension KeyedDecodingContainer {
    /// Decodes a value, falling back to `fallback` when the key is missing, null or malformed.
    func value<T: Decodable>(_ key: Key, _ fallback: T) -> T {
        (try? decodeIfPresent(T.self, forKey: key)) ?? fallback
    }

    /// Decodes a number that PostgREST may return as a JSON number or a numeric string.
    func number(_ key: Key, _ fallback: Double) -> Double {
        if let v = try? decodeIfPresent(Double.self, forKey: key) { return v }
        if let s = try? decodeIfPresent(String.self, forKey: key), let v = Double(s) { return v }
        return fallback
    }

    func optionalString(_ key: Key) -> String? {
        (try? decodeIfPresent(String.self, forKey: key)) ?? nil
    }
}

// MARK: - Rows

/// Every table row has a text id.
public protocol IdentifiedRow: Codable, Sendable, Identifiable, Equatable where ID == String {
    var id: String { get }
}

public struct Site: IdentifiedRow, Hashable {
    public var id: String
    public var name: String
    public var location: String
    public var type: SiteType
    public var status: SiteStatus
    public var capacityKw: Double
    public var batteryKwh: Double
    public var pricePerKwh: Double
    public var customer: String
    public var installDate: String
    public var lat: Double
    public var lng: Double
    /// CAPEX. 0 means "not set" → `capacityKw × 900` (see `effectiveSystemCost`).
    public var systemCost: Double
    /// O&M per year. 0 means "not set" → 1.5 % of system cost.
    public var annualOpex: Double
    public var degradationPct: Double
    public var tariffEscalationPct: Double

    public init(id: String, name: String, location: String, type: SiteType, status: SiteStatus,
                capacityKw: Double, batteryKwh: Double, pricePerKwh: Double, customer: String,
                installDate: String, lat: Double, lng: Double,
                systemCost: Double = 0, annualOpex: Double = 0,
                degradationPct: Double = 0.5, tariffEscalationPct: Double = 2) {
        self.id = id
        self.name = name
        self.location = location
        self.type = type
        self.status = status
        self.capacityKw = capacityKw
        self.batteryKwh = batteryKwh
        self.pricePerKwh = pricePerKwh
        self.customer = customer
        self.installDate = installDate
        self.lat = lat
        self.lng = lng
        self.systemCost = systemCost
        self.annualOpex = annualOpex
        self.degradationPct = degradationPct
        self.tariffEscalationPct = tariffEscalationPct
    }

    /// Contract defaults for the ROI fields (PLATFORM.md §1, web `siteFinanceDefaults`).
    public static func financeDefaults(capacityKw: Double) -> (systemCost: Double, annualOpex: Double) {
        let cost = jsRound(capacityKw * 900)
        return (cost, jsRound(cost * 0.015))
    }

    /// CAPEX with the contract default applied when it is still at the column default 0.
    public var effectiveSystemCost: Double { systemCost > 0 ? systemCost : Site.financeDefaults(capacityKw: capacityKw).systemCost }
    /// A stored O&M of 0 is only treated as missing when the system cost is missing too (web `withSiteDefaults`).
    public var effectiveAnnualOpex: Double { systemCost > 0 ? annualOpex : Site.financeDefaults(capacityKw: capacityKw).annualOpex }

    /// Fills ROI fields that are missing or still at the column default 0 (web `withSiteDefaults`).
    public func withDefaults() -> Site {
        var s = self
        if systemCost <= 0 {
            let d = Site.financeDefaults(capacityKw: capacityKw)
            s.systemCost = d.systemCost
            s.annualOpex = d.annualOpex
        }
        return s
    }

    enum CodingKeys: String, CodingKey {
        case id, name, location, type, status
        case capacityKw = "capacity_kw"
        case batteryKwh = "battery_kwh"
        case pricePerKwh = "price_per_kwh"
        case customer
        case installDate = "install_date"
        case lat, lng
        case systemCost = "system_cost"
        case annualOpex = "annual_opex"
        case degradationPct = "degradation_pct"
        case tariffEscalationPct = "tariff_escalation_pct"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = c.value(.name, "")
        location = c.value(.location, "")
        type = c.value(.type, SiteType.commercial)
        status = c.value(.status, SiteStatus.active)
        capacityKw = c.number(.capacityKw, 0)
        batteryKwh = c.number(.batteryKwh, 0)
        pricePerKwh = c.number(.pricePerKwh, 0.1)
        customer = c.value(.customer, "")
        installDate = String(c.value(.installDate, "2020-01-01").prefix(10))
        lat = c.number(.lat, 0)
        lng = c.number(.lng, 0)
        systemCost = c.number(.systemCost, 0)
        annualOpex = c.number(.annualOpex, 0)
        degradationPct = c.number(.degradationPct, 0.5)
        tariffEscalationPct = c.number(.tariffEscalationPct, 2)
    }
}

public struct Device: IdentifiedRow, Hashable {
    public var id: String
    public var siteId: String
    public var name: String
    public var type: DeviceType
    public var model: String
    public var serial: String
    public var status: DeviceStatus
    public var health: Double
    public var efficiency: Double
    public var firmware: String
    public var installedAt: String
    public var lastSeen: String

    public init(id: String, siteId: String, name: String, type: DeviceType, model: String, serial: String,
                status: DeviceStatus, health: Double, efficiency: Double, firmware: String,
                installedAt: String, lastSeen: String) {
        self.id = id
        self.siteId = siteId
        self.name = name
        self.type = type
        self.model = model
        self.serial = serial
        self.status = status
        self.health = health
        self.efficiency = efficiency
        self.firmware = firmware
        self.installedAt = installedAt
        self.lastSeen = lastSeen
    }

    enum CodingKeys: String, CodingKey {
        case id
        case siteId = "site_id"
        case name, type, model, serial, status, health, efficiency, firmware
        case installedAt = "installed_at"
        case lastSeen = "last_seen"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        siteId = c.value(.siteId, "")
        name = c.value(.name, "")
        type = c.value(.type, DeviceType.inverter)
        model = c.value(.model, "")
        serial = c.value(.serial, "")
        status = c.value(.status, DeviceStatus.online)
        health = c.number(.health, 100)
        efficiency = c.number(.efficiency, 100)
        firmware = c.value(.firmware, "")
        installedAt = String(c.value(.installedAt, "2020-01-01").prefix(10))
        lastSeen = c.value(.lastSeen, ISODate.string(Date()))
    }
}

public struct Ticket: IdentifiedRow, Hashable {
    public var id: String
    public var siteId: String
    public var deviceId: String?
    public var title: String
    public var description: String
    public var priority: TicketPriority
    public var status: TicketStatus
    public var assignee: String
    public var dueDate: String
    public var createdAt: String

    public init(id: String, siteId: String, deviceId: String?, title: String, description: String,
                priority: TicketPriority, status: TicketStatus, assignee: String, dueDate: String, createdAt: String) {
        self.id = id
        self.siteId = siteId
        self.deviceId = deviceId
        self.title = title
        self.description = description
        self.priority = priority
        self.status = status
        self.assignee = assignee
        self.dueDate = dueDate
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case siteId = "site_id"
        case deviceId = "device_id"
        case title, description, priority, status, assignee
        case dueDate = "due_date"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        siteId = c.value(.siteId, "")
        deviceId = c.optionalString(.deviceId)
        title = c.value(.title, "")
        description = c.value(.description, "")
        priority = c.value(.priority, TicketPriority.medium)
        status = c.value(.status, TicketStatus.open)
        assignee = c.value(.assignee, "")
        dueDate = String(c.value(.dueDate, "2020-01-01").prefix(10))
        createdAt = c.value(.createdAt, ISODate.string(Date()))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(siteId, forKey: .siteId)
        try c.encode(deviceId, forKey: .deviceId) // explicit null clears the column on upsert
        try c.encode(title, forKey: .title)
        try c.encode(description, forKey: .description)
        try c.encode(priority, forKey: .priority)
        try c.encode(status, forKey: .status)
        try c.encode(assignee, forKey: .assignee)
        try c.encode(dueDate, forKey: .dueDate)
        try c.encode(createdAt, forKey: .createdAt)
    }
}

public struct Invoice: IdentifiedRow, Hashable {
    public var id: String
    public var number: String
    public var siteId: String
    public var customer: String
    public var period: String
    public var energyKwh: Double
    public var rate: Double
    public var amount: Double
    public var status: InvoiceStatus
    public var issuedAt: String
    public var dueAt: String
    public var paidAt: String?

    public init(id: String, number: String, siteId: String, customer: String, period: String, energyKwh: Double,
                rate: Double, amount: Double, status: InvoiceStatus, issuedAt: String, dueAt: String, paidAt: String?) {
        self.id = id
        self.number = number
        self.siteId = siteId
        self.customer = customer
        self.period = period
        self.energyKwh = energyKwh
        self.rate = rate
        self.amount = amount
        self.status = status
        self.issuedAt = issuedAt
        self.dueAt = dueAt
        self.paidAt = paidAt
    }

    enum CodingKeys: String, CodingKey {
        case id, number
        case siteId = "site_id"
        case customer, period
        case energyKwh = "energy_kwh"
        case rate, amount, status
        case issuedAt = "issued_at"
        case dueAt = "due_at"
        case paidAt = "paid_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        number = c.value(.number, "")
        siteId = c.value(.siteId, "")
        customer = c.value(.customer, "")
        period = c.value(.period, "")
        energyKwh = c.number(.energyKwh, 0)
        rate = c.number(.rate, 0)
        amount = c.number(.amount, 0)
        status = c.value(.status, InvoiceStatus.pending)
        issuedAt = String(c.value(.issuedAt, "2020-01-01").prefix(10))
        dueAt = String(c.value(.dueAt, "2020-01-01").prefix(10))
        paidAt = c.optionalString(.paidAt).map { String($0.prefix(10)) }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(number, forKey: .number)
        try c.encode(siteId, forKey: .siteId)
        try c.encode(customer, forKey: .customer)
        try c.encode(period, forKey: .period)
        try c.encode(energyKwh, forKey: .energyKwh)
        try c.encode(rate, forKey: .rate)
        try c.encode(amount, forKey: .amount)
        try c.encode(status, forKey: .status)
        try c.encode(issuedAt, forKey: .issuedAt)
        try c.encode(dueAt, forKey: .dueAt)
        try c.encode(paidAt, forKey: .paidAt)
    }
}

public struct AppNotification: IdentifiedRow, Hashable {
    public var id: String
    public var title: String
    public var body: String
    public var kind: NotificationKind
    public var link: String?
    public var read: Bool
    public var createdAt: String

    public init(id: String, title: String, body: String, kind: NotificationKind, link: String?, read: Bool, createdAt: String) {
        self.id = id
        self.title = title
        self.body = body
        self.kind = kind
        self.link = link
        self.read = read
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, title, body, kind, link, read
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.value(.title, "")
        body = c.value(.body, "")
        kind = c.value(.kind, NotificationKind.info)
        link = c.optionalString(.link)
        read = c.value(.read, false)
        createdAt = c.value(.createdAt, ISODate.string(Date()))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(body, forKey: .body)
        try c.encode(kind, forKey: .kind)
        try c.encode(link, forKey: .link)
        try c.encode(read, forKey: .read)
        try c.encode(createdAt, forKey: .createdAt)
    }
}

public struct SavedReport: IdentifiedRow, Hashable {
    public var id: String
    public var kind: ReportKind
    public var title: String
    public var from: String
    public var to: String
    public var siteIds: [String]
    public var createdAt: String

    public init(id: String, kind: ReportKind, title: String, from: String, to: String, siteIds: [String], createdAt: String) {
        self.id = id
        self.kind = kind
        self.title = title
        self.from = from
        self.to = to
        self.siteIds = siteIds
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, kind, title, from, to
        case siteIds = "site_ids"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kind = c.value(.kind, ReportKind.energy)
        title = c.value(.title, "")
        from = String(c.value(.from, "2020-01-01").prefix(10))
        to = String(c.value(.to, "2020-01-01").prefix(10))
        siteIds = c.value(.siteIds, [String]())
        createdAt = c.value(.createdAt, ISODate.string(Date()))
    }
}

public struct AlertRule: IdentifiedRow, Hashable {
    public var id: String
    public var name: String
    public var metric: AlertMetric
    public var threshold: Double
    /// nil = all sites
    public var siteId: String?
    public var severity: AlertSeverity
    public var enabled: Bool
    public var lastTriggeredAt: String?
    public var createdAt: String

    public init(id: String, name: String, metric: AlertMetric, threshold: Double, siteId: String?,
                severity: AlertSeverity, enabled: Bool, lastTriggeredAt: String?, createdAt: String) {
        self.id = id
        self.name = name
        self.metric = metric
        self.threshold = threshold
        self.siteId = siteId
        self.severity = severity
        self.enabled = enabled
        self.lastTriggeredAt = lastTriggeredAt
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, name, metric, threshold
        case siteId = "site_id"
        case severity, enabled
        case lastTriggeredAt = "last_triggered_at"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = c.value(.name, "")
        metric = c.value(.metric, AlertMetric.siteOffline)
        threshold = c.number(.threshold, 0)
        siteId = c.optionalString(.siteId)
        severity = c.value(.severity, AlertSeverity.warning)
        enabled = c.value(.enabled, true)
        lastTriggeredAt = c.optionalString(.lastTriggeredAt)
        createdAt = c.value(.createdAt, ISODate.string(Date()))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(metric, forKey: .metric)
        try c.encode(threshold, forKey: .threshold)
        try c.encode(siteId, forKey: .siteId)
        try c.encode(severity, forKey: .severity)
        try c.encode(enabled, forKey: .enabled)
        try c.encode(lastTriggeredAt, forKey: .lastTriggeredAt)
        try c.encode(createdAt, forKey: .createdAt)
    }
}

public struct Integration: IdentifiedRow, Hashable {
    public var id: String
    public var vendor: IntegrationVendor
    public var name: String
    public var siteId: String
    public var externalId: String
    /// Non-secret settings (base_url, username, …). Secrets never live here.
    public var config: [String: String]
    /// Generated by the database; empty until the row has been inserted.
    public var ingestToken: String
    public var status: IntegrationStatus
    public var lastSyncAt: String?
    public var lastError: String?
    public var createdAt: String

    public init(id: String, vendor: IntegrationVendor, name: String, siteId: String, externalId: String,
                config: [String: String], ingestToken: String, status: IntegrationStatus,
                lastSyncAt: String?, lastError: String?, createdAt: String) {
        self.id = id
        self.vendor = vendor
        self.name = name
        self.siteId = siteId
        self.externalId = externalId
        self.config = config
        self.ingestToken = ingestToken
        self.status = status
        self.lastSyncAt = lastSyncAt
        self.lastError = lastError
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, vendor, name
        case siteId = "site_id"
        case externalId = "external_id"
        case config
        case ingestToken = "ingest_token"
        case status
        case lastSyncAt = "last_sync_at"
        case lastError = "last_error"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        vendor = c.value(.vendor, IntegrationVendor.webhook)
        name = c.value(.name, "")
        siteId = c.value(.siteId, "")
        externalId = c.value(.externalId, "")
        config = c.value(.config, [String: String]())
        ingestToken = c.value(.ingestToken, "")
        status = c.value(.status, IntegrationStatus.pending)
        lastSyncAt = c.optionalString(.lastSyncAt)
        lastError = c.optionalString(.lastError)
        createdAt = c.value(.createdAt, ISODate.string(Date()))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(vendor, forKey: .vendor)
        try c.encode(name, forKey: .name)
        try c.encode(siteId, forKey: .siteId)
        try c.encode(externalId, forKey: .externalId)
        try c.encode(config, forKey: .config)
        // Let the database generate the token on insert.
        if !ingestToken.isEmpty { try c.encode(ingestToken, forKey: .ingestToken) }
        try c.encode(status, forKey: .status)
        try c.encode(lastSyncAt, forKey: .lastSyncAt)
        try c.encode(lastError, forKey: .lastError)
        try c.encode(createdAt, forKey: .createdAt)
    }
}

public struct AppSettings: Codable, Sendable, Equatable {
    public var id: String
    public var userName: String
    public var email: String
    public var role: String
    public var company: String
    public var language: AppLanguage
    public var theme: AppTheme
    public var currency: CurrencyCode
    public var city: String
    public var lat: Double
    public var lng: Double
    public var co2KgPerKwh: Double
    public var treeKgPerYear: Double
    public var carTonsPerYear: Double
    public var notifyEmail: Bool
    public var notifyPush: Bool
    public var notifyDeviceAlerts: Bool
    public var notifyBilling: Bool
    public var notifyMaintenance: Bool
    public var discountRatePct: Double

    public static let defaults = AppSettings(
        id: "settings", userName: "ئالىم كېرىم", email: "admin@solarpulse.app", role: "admin",
        company: "SolarPulse Energy", language: .ug, theme: .light, currency: .USD, city: "ئۈرۈمچى",
        lat: 43.825, lng: 87.617, co2KgPerKwh: 0.7, treeKgPerYear: 21.8, carTonsPerYear: 4.6,
        notifyEmail: true, notifyPush: true, notifyDeviceAlerts: true, notifyBilling: true,
        notifyMaintenance: true, discountRatePct: 6
    )

    public init(id: String, userName: String, email: String, role: String, company: String,
                language: AppLanguage, theme: AppTheme, currency: CurrencyCode, city: String,
                lat: Double, lng: Double, co2KgPerKwh: Double, treeKgPerYear: Double, carTonsPerYear: Double,
                notifyEmail: Bool, notifyPush: Bool, notifyDeviceAlerts: Bool, notifyBilling: Bool,
                notifyMaintenance: Bool, discountRatePct: Double) {
        self.id = id
        self.userName = userName
        self.email = email
        self.role = role
        self.company = company
        self.language = language
        self.theme = theme
        self.currency = currency
        self.city = city
        self.lat = lat
        self.lng = lng
        self.co2KgPerKwh = co2KgPerKwh
        self.treeKgPerYear = treeKgPerYear
        self.carTonsPerYear = carTonsPerYear
        self.notifyEmail = notifyEmail
        self.notifyPush = notifyPush
        self.notifyDeviceAlerts = notifyDeviceAlerts
        self.notifyBilling = notifyBilling
        self.notifyMaintenance = notifyMaintenance
        self.discountRatePct = discountRatePct
    }

    enum CodingKeys: String, CodingKey {
        case id
        case userName = "user_name"
        case email, role, company, language, theme, currency, city, lat, lng
        case co2KgPerKwh = "co2_kg_per_kwh"
        case treeKgPerYear = "tree_kg_per_year"
        case carTonsPerYear = "car_tons_per_year"
        case notifyEmail = "notify_email"
        case notifyPush = "notify_push"
        case notifyDeviceAlerts = "notify_device_alerts"
        case notifyBilling = "notify_billing"
        case notifyMaintenance = "notify_maintenance"
        case discountRatePct = "discount_rate_pct"
    }

    /// Missing or null columns fall back to `AppSettings.defaults` (same as the web's `{...defaultSettings, ...row}`).
    public init(from decoder: Decoder) throws {
        let d = AppSettings.defaults
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.value(.id, d.id)
        userName = c.value(.userName, d.userName)
        email = c.value(.email, d.email)
        role = c.value(.role, d.role)
        company = c.value(.company, d.company)
        language = c.value(.language, d.language)
        theme = c.value(.theme, d.theme)
        currency = c.value(.currency, d.currency)
        city = c.value(.city, d.city)
        lat = c.number(.lat, d.lat)
        lng = c.number(.lng, d.lng)
        co2KgPerKwh = c.number(.co2KgPerKwh, d.co2KgPerKwh)
        treeKgPerYear = c.number(.treeKgPerYear, d.treeKgPerYear)
        carTonsPerYear = c.number(.carTonsPerYear, d.carTonsPerYear)
        notifyEmail = c.value(.notifyEmail, d.notifyEmail)
        notifyPush = c.value(.notifyPush, d.notifyPush)
        notifyDeviceAlerts = c.value(.notifyDeviceAlerts, d.notifyDeviceAlerts)
        notifyBilling = c.value(.notifyBilling, d.notifyBilling)
        notifyMaintenance = c.value(.notifyMaintenance, d.notifyMaintenance)
        discountRatePct = c.number(.discountRatePct, d.discountRatePct)
    }
}

/// A live reading from the `readings` table (real telemetry written by integrations).
public struct Reading: Codable, Sendable, Equatable {
    public var siteId: String
    public var ts: String
    public var powerKw: Double
    public var energyKwh: Double?

    public init(siteId: String, ts: String, powerKw: Double, energyKwh: Double?) {
        self.siteId = siteId
        self.ts = ts
        self.powerKw = powerKw
        self.energyKwh = energyKwh
    }

    enum CodingKeys: String, CodingKey {
        case siteId = "site_id"
        case ts
        case powerKw = "power_kw"
        case energyKwh = "energy_kwh"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        siteId = c.value(.siteId, "")
        ts = c.value(.ts, "")
        powerKw = c.number(.powerKw, 0)
        energyKwh = (try? c.decodeIfPresent(Double.self, forKey: .energyKwh)) ?? nil
    }
}

/// The whole dataset, as persisted by demo mode.
public struct DB: Codable, Sendable, Equatable {
    public var sites: [Site]
    public var devices: [Device]
    public var tickets: [Ticket]
    public var invoices: [Invoice]
    public var notifications: [AppNotification]
    public var reports: [SavedReport]
    public var settings: AppSettings
    public var alertRules: [AlertRule]
    public var integrations: [Integration]

    public init(sites: [Site] = [], devices: [Device] = [], tickets: [Ticket] = [], invoices: [Invoice] = [],
                notifications: [AppNotification] = [], reports: [SavedReport] = [],
                settings: AppSettings = .defaults, alertRules: [AlertRule] = [], integrations: [Integration] = []) {
        self.sites = sites
        self.devices = devices
        self.tickets = tickets
        self.invoices = invoices
        self.notifications = notifications
        self.reports = reports
        self.settings = settings
        self.alertRules = alertRules
        self.integrations = integrations
    }

    enum CodingKeys: String, CodingKey {
        case sites, devices, tickets, invoices, notifications, reports, settings
        case alertRules = "alert_rules"
        case integrations
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sites = try c.decode([Site].self, forKey: .sites)
        devices = c.value(.devices, [Device]())
        tickets = c.value(.tickets, [Ticket]())
        invoices = c.value(.invoices, [Invoice]())
        notifications = c.value(.notifications, [AppNotification]())
        reports = c.value(.reports, [SavedReport]())
        settings = c.value(.settings, AppSettings.defaults)
        alertRules = c.value(.alertRules, [AlertRule]())
        integrations = c.value(.integrations, [Integration]())
    }

    /// Same ordering the web applies after loading from Supabase.
    public mutating func sortForDisplay() {
        notifications.sort { $0.createdAt > $1.createdAt }
        tickets.sort { $0.createdAt > $1.createdAt }
        invoices.sort { $0.period != $1.period ? $0.period > $1.period : $0.number < $1.number }
        reports.sort { $0.createdAt > $1.createdAt }
        sites.sort { $0.id < $1.id }
        devices.sort { $0.id < $1.id }
        alertRules.sort { $0.createdAt < $1.createdAt }
        integrations.sort { $0.createdAt > $1.createdAt }
    }
}

/// Short unique id with a prefix, like the web's `uid()`.
public func makeId(_ prefix: String) -> String {
    prefix + UUID().uuidString.lowercased()
}
