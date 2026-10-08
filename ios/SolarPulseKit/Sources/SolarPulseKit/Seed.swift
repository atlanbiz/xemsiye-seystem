import Foundation

/// Demo dataset — an exact port of `src/lib/seed.ts` (16 sites, same names, coordinates and RNG seeds).
public struct SeedBuilder: Sendable {
    public let sim: Simulator

    public init(sim: Simulator = .live) {
        self.sim = sim
    }

    private var days: DayMath { sim.days }

    // name, location, type, capacity kW, battery kWh, lat, lng
    static let siteDefs: [(String, String, SiteType, Double, Double, Double, Double)] = [
        ("تۇرپان قۇياش مەيدانى", "تۇرپان", .utility, 820, 600, 42.95, 89.18),
        ("ئۈرۈمچى سودا مەركىزى", "ئۈرۈمچى", .commercial, 183, 120, 43.82, 87.61),
        ("قەشقەر كونا شەھەر ئۆيلىرى", "قەشقەر", .residential, 12.4, 15, 39.47, 75.99),
        ("خوتەن ئاشلىق زاۋۇتى", "خوتەن", .industrial, 410, 300, 37.11, 79.92),
        ("غۇلجا مەكتىپى", "غۇلجا", .commercial, 96, 60, 43.92, 81.32),
        ("ئاقسۇ يېزا ئىگىلىك مەيدانى", "ئاقسۇ", .industrial, 265, 180, 41.17, 80.26),
        ("قۇمۇل شامال-قۇياش بازىسى", "قۇمۇل", .utility, 640, 450, 42.82, 93.51),
        ("كورلا ئىشخانا بىناسى", "كورلا", .commercial, 65, 40, 41.76, 86.15),
        ("ئاتۇش ئائىلە ئولتۇراق رايونى", "ئاتۇش", .residential, 38, 30, 39.71, 76.17),
        ("چۆچەك دوختۇرخانىسى", "چۆچەك", .commercial, 120, 150, 46.75, 82.98),
        ("ئالتاي تاغ ئارامگاھى", "ئالتاي", .residential, 18, 20, 47.84, 88.14),
        ("يەكەن توقۇمىچىلىق زاۋۇتى", "يەكەن", .industrial, 350, 220, 38.42, 77.24),
        ("قاراماي ئامبار مەركىزى", "قاراماي", .industrial, 290, 160, 45.58, 84.89),
        ("بۆرتالا مېھمانخانىسى", "بۆرتالا", .commercial, 74, 50, 44.9, 82.07),
        ("كۇچا ئۈزۈمزارلىقى", "كۇچا", .residential, 24, 20, 41.72, 82.96),
        ("شىخەنزە لوگىستىكا پاركى", "شىخەنزە", .commercial, 210, 140, 44.3, 86.04),
    ]

    static let statuses: [SiteStatus] = [.active, .active, .active, .active, .idle, .active, .active, .active,
                                          .idle, .active, .offline, .active, .idle, .active, .maintenance, .active]

    static let customers = ["تۇرپان ئېنېرگىيە شىركىتى", "تەڭرىتاغ سودا گۇرۇھى", "ئابدۇللا ئائىلىسى", "خوتەن ئاشلىق شىركىتى",
                            "غۇلجا مائارىپ ئىدارىسى", "ئاقسۇ دېھقانچىلىق كوپراتىپى", "قۇمۇل توك تورى", "كورلا نېفىت شىركىتى",
                            "ئاتۇش مۈلۈك باشقۇرۇش", "چۆچەك ساغلاملىق مەركىزى", "نۇرگۈل خانىم", "يەكەن توقۇمىچىلىق",
                            "قاراماي ئامبار شىركىتى", "بۆرتالا ساياھەت", "كۇچا ئۈزۈمچىلىك", "شىخەنزە لوگىستىكا"]

    static let techs = ["ئەركىن تۇرسۇن", "مۇختەر ئەھمەت", "دىلنۇر ئابلىز", "پەرھات قادىر", "گۈلنار ھەسەن"]

    static let models: [DeviceType: [String]] = [
        .inverter: ["Huawei SUN2000-100KTL", "SMA Sunny Tripower 25", "Sungrow SG110CX", "Fronius Symo 20"],
        .battery: ["BYD Battery-Box HVM", "Tesla Powerwall 2", "CATL EnerOne", "LG RESU 16H"],
        .panel: ["LONGi Hi-MO 6 (Array)", "JinkoSolar Tiger Neo (Array)", "Trina Vertex S+ (Array)"],
        .meter: ["Schneider PM5560", "Eastron SDM630"],
        .sensor: ["Kipp & Zonen SMP10", "Davis Vantage Pro2"],
    ]

    static func label(_ t: DeviceType) -> String {
        switch t {
        case .inverter: return "INV"
        case .battery: return "BAT"
        case .panel: return "PV"
        case .meter: return "MTR"
        case .sensor: return "SNS"
        }
    }

    public static func defaultPrice(for type: SiteType) -> Double {
        switch type {
        case .residential: return 0.14
        case .utility: return 0.08
        case .industrial: return 0.1
        case .commercial: return 0.12
        }
    }

    /// Days before "now" each seeded site was installed: 420 + ⌊rand('inst'+i)·900⌋.
    public static func installOffset(index i: Int) -> Int {
        420 + Int((Simulator.rand("inst\(i)") * 900).rounded(.down))
    }

    // MARK: - Builders

    public func buildSites(now: Date) -> [Site] {
        SeedBuilder.siteDefs.enumerated().map { i, def in
            let (name, location, type, cap, bat, lat, lng) = def
            return Site(
                id: "site-\(DayMath.pad2(i + 1))",
                name: name,
                location: location,
                type: type,
                status: SeedBuilder.statuses[i],
                capacityKw: cap,
                batteryKwh: bat,
                pricePerKwh: SeedBuilder.defaultPrice(for: type),
                customer: SeedBuilder.customers[i],
                installDate: days.dayKey(days.addDays(now, -SeedBuilder.installOffset(index: i))),
                lat: lat,
                lng: lng
            ).withDefaults()
        }
    }

    static func hash6(_ s: String) -> String {
        let a = Int((Simulator.rand(s) * Double(0xFFFFFF)).rounded(.down))
        var hex = String(a, radix: 16, uppercase: true)
        while hex.count < 6 { hex = "0" + hex }
        let b = Int((Simulator.rand(s + "x") * 9999).rounded(.down))
        return hex + String(b)
    }

    public func buildDevices(_ sites: [Site], now: Date) -> [Device] {
        var out: [Device] = []
        var n = 1
        for s in sites {
            let inverters = s.capacityKw > 300 ? 3 : s.capacityKw > 80 ? 2 : 1
            var types: [DeviceType] = Array(repeating: DeviceType.inverter, count: inverters)
            types.append(contentsOf: [DeviceType.panel, DeviceType.meter])
            if s.batteryKwh > 0 { types.append(.battery) }
            if s.capacityKw > 100 { types.append(.sensor) }
            for (idx, type) in types.enumerated() {
                let r = Simulator.rand(s.id + type.rawValue + String(idx))
                var status: DeviceStatus = r < 0.08 ? .warning : .online
                if s.status == .offline { status = .offline }
                if s.status == .maintenance && type == .inverter { status = .warning }
                let models = SeedBuilder.models[type] ?? [""]
                let model = models[min(models.count - 1, Int((r * Double(models.count)).rounded(.down)))]
                let lastSeenMs = status == .offline ? 3_600_000 * (6 + r * 40) : r * 120_000
                out.append(Device(
                    id: "dev-\(DayMath.pad(n, width: 3))",
                    siteId: s.id,
                    name: "\(SeedBuilder.label(type))-\(s.id.suffix(2))-\(idx + 1)",
                    type: type,
                    model: model,
                    serial: "SN" + SeedBuilder.hash6(s.id + String(idx) + type.rawValue),
                    status: status,
                    health: jsRound(status == .warning ? 70 + r * 12 : 88 + r * 12),
                    efficiency: jsRound((type == .inverter ? 96.2 + r * 2.4 : 90 + r * 8) * 10) / 10,
                    firmware: "v\(2 + Int((r * 3).rounded(.down))).\(Int((r * 9).rounded(.down))).\(Int((r * 20).rounded(.down)))",
                    installedAt: s.installDate,
                    lastSeen: ISODate.string(now.addingTimeInterval(-lastSeenMs / 1000))
                ))
                n += 1
            }
        }
        return out
    }

    // siteIndex, title, description, priority, status, due (days from now)
    static let ticketDefs: [(Int, String, String, TicketPriority, TicketStatus, Int)] = [
        (10, "ئالتاي ئىستانسىسى تورسىز", "ئالاقە ئۈسكۈنىسى جاۋاب قايتۇرمايۋاتىدۇ، نەق مەيداندا تەكشۈرۈش كېرەك.", .critical, .open, 1),
        (14, "كۇچا ئىنۋېرتورىنى ئالماشتۇرۇش", "ئىنۋېرتور قىزىپ كېتىش خاتالىقى بەردى، يېڭىسىغا ئالماشتۇرۇلىدۇ.", .high, .inProgress, 2),
        (0, "تاختايلارنى تازىلاش", "قۇم-توپا سەۋەبىدىن ئۈنۈم 6% تۆۋەنلىدى.", .medium, .open, 4),
        (3, "باتارېيە BMS يۇمشاق دېتالىنى يېڭىلاش", "BMS v3.2 گە يېڭىلاش.", .low, .open, 9),
        (6, "پەسىللىك تەكشۈرۈش", "ئېلېكتر ئۇلىنىشى، يەرلەشتۈرۈش ۋە كابېللارنى تەكشۈرۈش.", .medium, .inProgress, 3),
        (11, "توك ئۆلچىگۈچ سانلىق مەلۇماتى كەم", "سائەت 14:00-16:00 ئارىلىقىدا ئۆلچەش كەم.", .medium, .open, 6),
        (1, "ئوت ئۆچۈرگۈچ ۋە بىخەتەرلىك تەكشۈرۈشى", "يىللىق بىخەتەرلىك تەكشۈرۈشى.", .low, .resolved, -5),
        (12, "ئىنۋېرتور ئالاقە ئۈزۈلۈشى", "RS485 ئالاقىسى ۋاقتى-ۋاقتى بىلەن ئۈزۈلىدۇ.", .high, .open, 2),
        (8, "سايە چۈشۈش مەسىلىسى", "يېڭى بىنا سايىسى سەۋەبىدىن ئەتىگەنلىك ھاسىلات تۆۋەن.", .low, .resolved, -12),
        (4, "تاختاي يېرىلىشى", "مۆلدۈردىن كېيىن 3 تاختايدا يېرىق بايقالدى.", .high, .inProgress, 5),
        (9, "زاپاس توك سىستېمىسىنى سىناش", "دوختۇرخانا ئۈچۈن ئايلىق زاپاس توك سىنىقى.", .critical, .open, 0),
        (2, "تاختايلارنى تازىلاش", "ئايلىق تازىلاش.", .low, .resolved, -20),
        (6, "ھاۋا رايى سېنزورىنى تەڭشەش", "نۇرلىنىش سېنزورى %8 يۇقىرى كۆرسىتىۋاتىدۇ.", .medium, .open, 12),
        (13, "يۇمشاق دېتال يېڭىلاش", "ئىنۋېرتور يۇمشاق دېتالىنى ئەڭ يېڭى نەشرىگە يېڭىلاش.", .low, .open, 15),
    ]

    public func buildTickets(_ sites: [Site], _ devices: [Device], now: Date) -> [Ticket] {
        SeedBuilder.ticketDefs.enumerated().map { i, def in
            let (si, title, description, priority, status, due) = def
            let site = sites[si]
            let dev = devices.first { $0.siteId == site.id && $0.type == .inverter }
            return Ticket(
                id: "tkt-\(DayMath.pad2(i + 1))",
                siteId: site.id,
                deviceId: i % 3 == 2 ? nil : dev?.id,
                title: title,
                description: description,
                priority: priority,
                status: status,
                assignee: SeedBuilder.techs[i % SeedBuilder.techs.count],
                dueDate: days.dayKey(days.addDays(now, due)),
                createdAt: ISODate.string(days.addDays(now, -abs(due) - 3 - (i % 4)))
            )
        }
    }

    /// kWh a site produced in a `YYYY-MM` month (up to today for the running month).
    public func monthEnergy(_ site: Site, month: String) -> Double {
        let (y, m) = DayMath.parseMonth(month)
        let from = days.makeDate(year: y, monthIndex0: m - 1, day: 1)
        let last = days.makeDate(year: y, monthIndex0: m, day: 0)
        let today = sim.now
        let to = last > today ? today : last
        return days.daysBetween(from, to).reduce(0.0) { $0 + sim.siteDayKwhCached(site, day: days.dayKey($1)) }
    }

    public func buildInvoice(_ site: Site, month: String, seq: Int) -> Invoice {
        let (y, m) = DayMath.parseMonth(month)
        let issued = days.makeDate(year: y, monthIndex0: m, day: 1)
        let energy = jsRound(monthEnergy(site, month: month))
        return Invoice(
            id: "inv-\(site.id)-\(month)",
            number: "INV-\(month.replacingOccurrences(of: "-", with: ""))-\(DayMath.pad(seq, width: 3))",
            siteId: site.id,
            customer: site.customer,
            period: month,
            energyKwh: energy,
            rate: site.pricePerKwh,
            amount: jsRound(energy * site.pricePerKwh * 100) / 100,
            status: .pending,
            issuedAt: days.dayKey(issued),
            dueAt: days.dayKey(days.addDays(issued, 20)),
            paidAt: nil
        )
    }

    public func buildInvoices(_ sites: [Site], now: Date) -> [Invoice] {
        var out: [Invoice] = []
        for k in stride(from: 6, through: 1, by: -1) {
            let month = days.monthKey(days.addMonths(now, -k))
            for (i, s) in sites.enumerated() {
                var inv = buildInvoice(s, month: month, seq: i + 1)
                if inv.energyKwh == 0 { continue }
                let due = DayMath.utcMidnight(inv.dueAt)
                let r = Simulator.rand(inv.id)
                if k >= 2 || r < 0.5 {
                    inv.status = r < 0.06 && k <= 3 ? .overdue : .paid
                    if inv.status == .paid {
                        let issuedUTC = DayMath.utcMidnight(inv.issuedAt)
                        inv.paidAt = days.dayKey(days.addDays(issuedUTC, Int((r * 18).rounded(.down))))
                    }
                } else {
                    inv.status = due < now ? .overdue : .pending
                }
                out.append(inv)
            }
        }
        return out
    }

    public func buildNotifications(now: Date) -> [AppNotification] {
        func t(_ mins: Double) -> String { ISODate.string(now.addingTimeInterval(-mins * 60)) }
        return [
            AppNotification(id: "ntf-1", title: "ئالتاي ئىستانسىسى تورسىز", body: "ئالتاي تاغ ئارامگاھى 6 سائەتتىن بۇيان سانلىق مەلۇمات ئەۋەتمىدى.", kind: .danger, link: "/sites/site-11", read: false, createdAt: t(25)),
            AppNotification(id: "ntf-2", title: "ئىنۋېرتور ئاگاھلاندۇرۇشى", body: "كۇچا ئۈزۈمزارلىقىدىكى ئىنۋېرتور تېمپېراتۇرىسى يۇقىرى.", kind: .warning, link: "/devices", read: false, createdAt: t(90)),
            AppNotification(id: "ntf-3", title: "ھېسابات تۆلەندى", body: "تەڭرىتاغ سودا گۇرۇھى ئالدىنقى ئاينىڭ ھېساباتىنى تۆلىدى.", kind: .success, link: "/billing", read: false, createdAt: t(240)),
            AppNotification(id: "ntf-4", title: "يېڭى ئاسراش ۋەزىپىسى", body: "چۆچەك دوختۇرخانىسى زاپاس توك سىنىقى بۈگۈن.", kind: .info, link: "/maintenance", read: true, createdAt: t(600)),
            AppNotification(id: "ntf-5", title: "ئايلىق دوكلات تەييار", body: "ئالدىنقى ئاينىڭ ئېنېرگىيە دوكلاتىنى چۈشۈرەلەيسىز.", kind: .info, link: "/reports", read: true, createdAt: t(1440)),
        ]
    }

    /// Starter alert rules (web `buildAlertRules`).
    public func buildAlertRules(now: Date) -> [AlertRule] {
        let created = ISODate.string(days.addDays(now, -30))
        let defs: [(String, AlertMetric, Double, AlertSeverity)] = [
            ("ئىستانسا تورسىز", .siteOffline, 0, .danger),
            ("ھاسىلات تۆۋەن", .siteYieldBelow, 1.5, .warning),
            ("ئىنۋېرتور ئۈنۈمى تۆۋەن", .deviceEfficiencyBelow, 92, .warning),
            ("ئۈسكۈنە سالامەتلىكى ناچار", .deviceHealthBelow, 72, .warning),
            ("ئۈسكۈنە 1 سائەتتىن ئارتۇق تورسىز", .deviceOfflineMinutes, 60, .danger),
            ("تالون 10 كۈندىن ئارتۇق كېچىكتى", .invoiceOverdueDays, 10, .warning),
        ]
        return defs.enumerated().map { i, d in
            AlertRule(id: "rule-\(DayMath.pad2(i + 1))", name: d.0, metric: d.1, threshold: d.2, siteId: nil,
                      severity: d.3, enabled: true, lastTriggeredAt: nil, createdAt: created)
        }
    }

    /// Full demo database, like `buildSeed()`.
    public func build() -> DB {
        let now = sim.now
        let sites = buildSites(now: now)
        let devices = buildDevices(sites, now: now)
        return DB(
            sites: sites,
            devices: devices,
            tickets: buildTickets(sites, devices, now: now),
            invoices: buildInvoices(sites, now: now),
            notifications: buildNotifications(now: now),
            reports: [],
            settings: .defaults,
            alertRules: buildAlertRules(now: now),
            integrations: []
        )
    }
}

extension DB {
    /// Pending invoices whose due date passed become overdue (web `refreshOverdue`).
    /// Returns the invoices that changed.
    @discardableResult
    public mutating func refreshOverdue(todayKey: String) -> [Invoice] {
        var changed: [Invoice] = []
        for i in invoices.indices where invoices[i].status == .pending && invoices[i].dueAt < todayKey {
            invoices[i].status = .overdue
            changed.append(invoices[i])
        }
        return changed
    }
}
