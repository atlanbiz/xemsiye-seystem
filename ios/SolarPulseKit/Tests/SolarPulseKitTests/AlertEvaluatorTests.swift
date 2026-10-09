import XCTest
@testable import SolarPulseKit

final class AlertEvaluatorTests: XCTestCase {
    /// 2026-10-08T15:00:00Z — after the 14:00 yield check hour.
    static let afternoon = Date(timeIntervalSince1970: 1_791_471_600)
    /// 2026-10-08T09:00:00Z
    static let morning = Date(timeIntervalSince1970: 1_791_450_000)

    func evaluator(_ now: Date) -> AlertEvaluator {
        AlertEvaluator(sim: Simulator(timeZone: SimulatorTests.utc, clock: { now }))
    }

    func site(_ id: String, status: SiteStatus, cap: Double = 100) -> Site {
        Site(id: id, name: "Site \(id)", location: "L", type: .commercial, status: status, capacityKw: cap,
             batteryKwh: 0, pricePerKwh: 0.1, customer: "C", installDate: "2024-01-01", lat: 0, lng: 0)
    }

    func device(_ id: String, site: String, status: DeviceStatus = .online, eff: Double = 97, health: Double = 95,
                lastSeen: Date = AlertEvaluatorTests.afternoon) -> Device {
        Device(id: id, siteId: site, name: "DEV-\(id)", type: .inverter, model: "M", serial: "S", status: status,
               health: health, efficiency: eff, firmware: "v1", installedAt: "2024-01-01", lastSeen: ISODate.string(lastSeen))
    }

    func rule(_ metric: AlertMetric, _ threshold: Double = 0, site: String? = nil, severity: AlertSeverity = .warning,
              enabled: Bool = true, last: Date? = nil, id: String? = nil) -> AlertRule {
        AlertRule(id: id ?? "rule-\(metric.rawValue)", name: "R \(metric.rawValue)", metric: metric, threshold: threshold, siteId: site,
                  severity: severity, enabled: enabled, lastTriggeredAt: last.map(ISODate.string), createdAt: "2026-01-01T00:00:00.000Z")
    }

    func testSiteOfflineRespectsScope() {
        let ev = evaluator(Self.afternoon)
        let sites = [site("a", status: .offline), site("b", status: .active), site("c", status: .offline)]
        XCTAssertEqual(ev.matches(rule(.siteOffline), sites: sites, devices: [], invoices: [], now: Self.afternoon).map(\.entityId), ["a", "c"])
        XCTAssertEqual(ev.matches(rule(.siteOffline, site: "c"), sites: sites, devices: [], invoices: [], now: Self.afternoon).map(\.entityId), ["c"])
        XCTAssertTrue(ev.matches(rule(.siteOffline, site: "b"), sites: sites, devices: [], invoices: [], now: Self.afternoon).isEmpty)
        XCTAssertNil(ev.matches(rule(.siteOffline), sites: sites, devices: [], invoices: [], now: Self.afternoon).first?.value)
    }

    func testYieldBelowOnlyAfter14h() {
        // An idle site produces 15 % today, so its yield is far below the active one's.
        let sites = [site("a", status: .idle), site("b", status: .active)]
        let r = rule(.siteYieldBelow, 1000)
        XCTAssertTrue(evaluator(Self.morning).matches(r, sites: sites, devices: [], invoices: [], now: Self.morning).isEmpty)
        XCTAssertEqual(evaluator(Self.afternoon).matches(r, sites: sites, devices: [], invoices: [], now: Self.afternoon).count, 2)
        let ev = evaluator(Self.afternoon)
        let day = ev.sim.days.dayKey(Self.afternoon)
        let h = ev.sim.days.hourOfDay(Self.afternoon)
        let yActive = ev.sim.siteDayKwh(sites[1], day: day, untilHour: h) / 100
        let yIdle = ev.sim.siteDayKwh(sites[0], day: day, untilHour: h) / 100
        XCTAssertLessThan(yIdle, yActive)
        let m = ev.matches(rule(.siteYieldBelow, (yIdle + yActive) / 2), sites: sites, devices: [], invoices: [], now: Self.afternoon)
        XCTAssertEqual(m.map(\.entityId), ["a"])
        XCTAssertEqual(m.first?.value ?? -1, yIdle, accuracy: 1e-9)
        XCTAssertEqual(m.first?.link, "/sites/a")
    }

    func testDeviceEfficiencyAndHealth() {
        let ev = evaluator(Self.afternoon)
        let devs = [device("1", site: "a", eff: 94.5), device("2", site: "a", eff: 97, health: 70), device("3", site: "b", eff: 90)]
        let sites = [site("a", status: .active), site("b", status: .active)]
        let eff = ev.matches(rule(.deviceEfficiencyBelow, 95), sites: sites, devices: devs, invoices: [], now: Self.afternoon)
        XCTAssertEqual(eff.map(\.entityId), ["1", "3"])
        XCTAssertEqual(eff.first?.label, "DEV-1 · Site a")
        XCTAssertEqual(eff.first?.link, "/devices?q=DEV-1")
        XCTAssertEqual(ev.matches(rule(.deviceEfficiencyBelow, 95, site: "a"), sites: sites, devices: devs, invoices: [], now: Self.afternoon).map(\.entityId), ["1"])
        XCTAssertEqual(ev.matches(rule(.deviceHealthBelow, 80), sites: sites, devices: devs, invoices: [], now: Self.afternoon).map(\.entityId), ["2"])
        // strictly below: equal does not fire
        XCTAssertTrue(ev.matches(rule(.deviceEfficiencyBelow, 90), sites: sites, devices: [devs[2]], invoices: [], now: Self.afternoon).isEmpty)
    }

    func testDeviceOfflineMinutes() {
        let ev = evaluator(Self.afternoon)
        let devs = [
            device("1", site: "a", status: .offline, lastSeen: Self.afternoon.addingTimeInterval(-2 * 3600)),
            device("2", site: "a", status: .online, lastSeen: Self.afternoon.addingTimeInterval(-5 * 3600)),
        ]
        let m = ev.matches(rule(.deviceOfflineMinutes, 60), sites: [], devices: devs, invoices: [], now: Self.afternoon)
        XCTAssertEqual(m.map(\.entityId), ["1"])
        XCTAssertEqual(m.first?.value ?? -1, 120, accuracy: 1e-6)
        XCTAssertTrue(ev.matches(rule(.deviceOfflineMinutes, 180), sites: [], devices: devs, invoices: [], now: Self.afternoon).isEmpty)
    }

    func testInvoiceOverdueDays() {
        let ev = evaluator(Self.afternoon)
        func inv(_ id: String, _ status: InvoiceStatus, due: String) -> Invoice {
            Invoice(id: id, number: "INV-\(id)", siteId: "a", customer: "C", period: "2026-08", energyKwh: 1, rate: 1,
                    amount: 1, status: status, issuedAt: "2026-09-01", dueAt: due, paidAt: nil)
        }
        let invoices = [inv("1", .overdue, due: "2026-09-28"), inv("2", .overdue, due: "2026-10-05"), inv("3", .pending, due: "2026-09-01")]
        // 2026-10-08 − 2026-09-28 = 10 days > 7; 3 days is not; pending never fires.
        let m = ev.matches(rule(.invoiceOverdueDays, 7), sites: [], devices: [], invoices: invoices, now: Self.afternoon)
        XCTAssertEqual(m.map(\.entityId), ["1"])
        XCTAssertEqual(m.first?.value, 10)
        XCTAssertEqual(m.first?.label, "INV-1 · C")
        XCTAssertEqual(m.first?.link, "/billing?open=1")
    }

    func testEvaluateRespectsEnabledAndSixHourCooldown() {
        let ev = evaluator(Self.afternoon)
        let sites = [site("a", status: .offline), site("b", status: .offline)]
        let rules = [
            rule(.siteOffline, severity: .danger),
            rule(.siteOffline, enabled: false, id: "r-disabled"),
            rule(.siteOffline, last: Self.afternoon.addingTimeInterval(-3600), id: "r-cooling"),
            rule(.siteOffline, last: Self.afternoon.addingTimeInterval(-7 * 3600), id: "r-expired"),
        ]
        let firings = ev.evaluate(rules: rules, sites: sites, devices: [], invoices: [], now: Self.afternoon)
        XCTAssertEqual(firings.map(\.ruleId), ["rule-site_offline", "r-expired"])

        let n = AlertEvaluator.notification(for: firings[0], body: "b", id: "ntf-x", now: Self.afternoon)
        XCTAssertEqual(n.title, "R site_offline")
        XCTAssertEqual(n.kind, .danger)
        XCTAssertEqual(n.link, "/sites/a")
        XCTAssertEqual(n.createdAt, "2026-10-08T15:00:00.000Z")
        XCTAssertFalse(n.read)
        XCTAssertEqual(AlertEvaluator.notification(for: firings[1], body: "b", id: "y", now: Self.afternoon).kind, .warning)

        // Exactly at the 6 h boundary the cooldown is over.
        var cooled = rules[0]
        cooled.lastTriggeredAt = ISODate.string(Self.afternoon.addingTimeInterval(-6 * 3600))
        XCTAssertFalse(AlertEvaluator.isCoolingDown(cooled, now: Self.afternoon))
        cooled.lastTriggeredAt = ISODate.string(Self.afternoon.addingTimeInterval(-6 * 3600 + 1))
        XCTAssertTrue(AlertEvaluator.isCoolingDown(cooled, now: Self.afternoon))
    }

    func testDescribe() {
        let m = (1...5).map { AlertEvaluator.Match(entityId: "\($0)", label: "N\($0)", value: Double($0), link: "") }
        XCTAssertEqual(AlertEvaluator.describe(m) { "\(Int($0.value ?? 0))%" }, "N1 (1%); N2 (2%); N3 (3%) +2")
        let offline = [AlertEvaluator.Match(entityId: "a", label: "Site a", value: nil, link: "")]
        XCTAssertEqual(AlertEvaluator.describe(offline) { _ in "x" }, "Site a")
        let fmt = Fmt(lang: .en, currency: .USD)
        XCTAssertEqual(AlertEvaluator.defaultValueText(.siteYieldBelow, AlertEvaluator.Match(entityId: "", label: "", value: 1.234, link: ""), fmt: fmt), "1.23 kWh/kWp")
        XCTAssertEqual(AlertEvaluator.defaultValueText(.deviceHealthBelow, AlertEvaluator.Match(entityId: "", label: "", value: 70, link: ""), fmt: fmt), "70%")
    }

    func testSeedRulesMatchWeb() {
        let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
        let rules = SeedBuilder(sim: sim).buildAlertRules(now: SimulatorTests.fixedNow)
        XCTAssertEqual(rules.map(\.id), ["rule-01", "rule-02", "rule-03", "rule-04", "rule-05", "rule-06"])
        XCTAssertEqual(rules.map(\.metric), [.siteOffline, .siteYieldBelow, .deviceEfficiencyBelow, .deviceHealthBelow, .deviceOfflineMinutes, .invoiceOverdueDays])
        XCTAssertEqual(rules.map(\.threshold), [0, 1.5, 92, 72, 60, 10])
        XCTAssertEqual(rules[0].createdAt, "2026-09-08T12:00:00.000Z")
    }

    func testISODateParsesPostgresFormats() {
        let expected = Date(timeIntervalSince1970: 1_791_460_800)
        XCTAssertEqual(ISODate.parse("2026-10-08T12:00:00.000Z"), expected)
        XCTAssertEqual(ISODate.parse("2026-10-08T12:00:00+00:00"), expected)
        XCTAssertEqual(ISODate.parse("2026-10-08T12:00:00.123456+00:00"), expected)
        XCTAssertEqual(ISODate.parse("2026-10-08T14:00:00+02:00"), expected)
        XCTAssertEqual(ISODate.parse("2026-10-08T12:00:00"), expected)
    }

    /// Same firings as the web's `evaluate(buildSeed())` at 2026-10-08T12:00Z (yield rule not yet checked before 14:00).
    func testSeedFiringsMatchWeb() {
        let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
        let db = SeedBuilder(sim: sim).build()
        let firings = AlertEvaluator(sim: sim).evaluate(rules: db.alertRules, sites: db.sites, devices: db.devices,
                                                        invoices: db.invoices, now: SimulatorTests.fixedNow)
        XCTAssertEqual(firings.map(\.ruleId), ["rule-01", "rule-03", "rule-04", "rule-05", "rule-06"])
        XCTAssertEqual(firings.map(\.matches.count), [1, 12, 4, 4, 2])
    }
}
