import XCTest
@testable import SolarPulseKit

/// Seed parity with `buildSeed()` run in Node with `Date` frozen at 2026-10-08T12:00:00Z and `TZ=UTC`.
final class SeedTests: XCTestCase {
    let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
    var seed: SeedBuilder { SeedBuilder(sim: sim) }

    func testInstallOffsets() {
        let expected = [534, 735, 1206, 843, 867, 938, 717, 552, 854, 879, 1023, 1115, 760, 791, 872, 697]
        XCTAssertEqual((0..<16).map { SeedBuilder.installOffset(index: $0) }, expected)
    }

    func testSites() {
        let db = seed.build()
        XCTAssertEqual(db.sites.count, 16)
        XCTAssertEqual(db.sites[0].id, "site-01")
        XCTAssertEqual(db.sites[0].installDate, "2025-04-22")
        XCTAssertEqual(db.sites[2].installDate, "2023-06-20")
        XCTAssertEqual(db.sites[15].installDate, "2024-11-10")
        XCTAssertEqual(db.sites[10].status, .offline)
        XCTAssertEqual(db.sites[14].status, .maintenance)
        XCTAssertEqual(db.sites[2].pricePerKwh, 0.14)
        XCTAssertEqual(db.sites[0].pricePerKwh, 0.08)
    }

    func testDevices() {
        let db = seed.build()
        XCTAssertEqual(db.devices.count, 87)
        let expected: [(String, String, DeviceType, String, String, DeviceStatus, Double, Double, String)] = [
            ("dev-001", "INV-01-1", .inverter, "Sungrow SG110CX", "SN50518D620", .online, 96, 97.9, "v4.6.14"),
            ("dev-002", "INV-01-2", .inverter, "SMA Sunny Tripower 25", "SNDBA69F597", .online, 93, 97.3, "v3.4.8"),
            ("dev-003", "INV-01-3", .inverter, "SMA Sunny Tripower 25", "SNACB1FD2074", .online, 93, 97.1, "v3.3.7"),
            ("dev-004", "PV-01-4", .panel, "Trina Vertex S+ (Array)", "SNA56A216101", .online, 96, 95.6, "v4.6.14"),
            ("dev-005", "MTR-01-5", .meter, "Eastron SDM630", "SNED506B7672", .online, 96, 95.6, "v4.6.14"),
            ("dev-006", "BAT-01-6", .battery, "BYD Battery-Box HVM", "SNC2DD027653", .online, 90, 91.3, "v2.1.3"),
        ]
        for (i, e) in expected.enumerated() {
            let d = db.devices[i]
            XCTAssertEqual(d.id, e.0)
            XCTAssertEqual(d.name, e.1)
            XCTAssertEqual(d.type, e.2)
            XCTAssertEqual(d.model, e.3)
            XCTAssertEqual(d.serial, e.4)
            XCTAssertEqual(d.status, e.5)
            XCTAssertEqual(d.health, e.6)
            XCTAssertEqual(d.efficiency, e.7, accuracy: 1e-9)
            XCTAssertEqual(d.firmware, e.8)
        }
        let d57 = db.devices.first { $0.id == "dev-057" }
        XCTAssertEqual(d57?.serial, "SN28164E7997")
        XCTAssertEqual(d57?.status, .offline)
        XCTAssertEqual(d57?.health, 93)
        XCTAssertEqual(d57?.lastSeen.prefix(16), "2026-10-07T13:30")
        XCTAssertEqual(db.devices.filter { $0.siteId == "site-11" }.map(\.status), [.offline, .offline, .offline, .offline])
    }

    func testTickets() {
        let db = seed.build()
        XCTAssertEqual(db.tickets.count, 14)
        XCTAssertEqual(db.tickets[0].siteId, "site-11")
        XCTAssertEqual(db.tickets[0].deviceId, "dev-057")
        XCTAssertEqual(db.tickets[0].dueDate, "2026-10-09")
        XCTAssertEqual(db.tickets[0].createdAt, "2026-10-04T12:00:00.000Z")
        XCTAssertEqual(db.tickets[1].deviceId, "dev-078")
        XCTAssertEqual(db.tickets[1].createdAt, "2026-10-02T12:00:00.000Z")
        XCTAssertNil(db.tickets[2].deviceId)
        XCTAssertEqual(db.tickets[2].assignee, "دىلنۇر ئابلىز")
    }

    func testInvoices() {
        let db = seed.build()
        XCTAssertEqual(db.invoices.count, 96)
        let site01 = db.invoices.filter { $0.siteId == "site-01" }
        let expected: [(String, String, Double, Double, InvoiceStatus, String?, String)] = [
            ("inv-site-01-2026-04", "INV-202604-001", 96529, 7722.32, .paid, "2026-05-04", "2026-05-21"),
            ("inv-site-01-2026-05", "INV-202605-001", 122328, 9786.24, .paid, "2026-06-09", "2026-06-21"),
            ("inv-site-01-2026-06", "INV-202606-001", 125739, 10059.12, .paid, "2026-07-01", "2026-07-21"),
            ("inv-site-01-2026-07", "INV-202607-001", 125062, 10004.96, .paid, "2026-08-18", "2026-08-21"),
            ("inv-site-01-2026-08", "INV-202608-001", 107737, 8618.96, .paid, "2026-09-11", "2026-09-21"),
            ("inv-site-01-2026-09", "INV-202609-001", 92159, 7372.72, .paid, "2026-10-02", "2026-10-21"),
        ]
        XCTAssertEqual(site01.count, expected.count)
        for (inv, e) in zip(site01, expected) {
            XCTAssertEqual(inv.id, e.0)
            XCTAssertEqual(inv.number, e.1)
            XCTAssertEqual(inv.energyKwh, e.2)
            XCTAssertEqual(inv.amount, e.3, accuracy: 1e-9)
            XCTAssertEqual(inv.status, e.4)
            XCTAssertEqual(inv.paidAt, e.5)
            XCTAssertEqual(inv.dueAt, e.6)
        }
        let statuses = db.invoices.map { String($0.status.rawValue.prefix(2)) }.joined()
        let expectedStatuses = "papapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapapaovpapapapapapapapaovpapapapapapapapapapapapapapapapapapapapapapapapepapepepepapapepapapepepepe"
        XCTAssertEqual(statuses, expectedStatuses)
    }

    func testBuildInvoiceForFixedSite() {
        let inv = seed.buildInvoice(SimulatorTests.site01, month: "2026-05", seq: 1)
        XCTAssertEqual(inv.energyKwh, 122328)
        XCTAssertEqual(inv.amount, 9786.24, accuracy: 1e-9)
        XCTAssertEqual(inv.issuedAt, "2026-06-01")
        XCTAssertEqual(inv.dueAt, "2026-06-21")
        XCTAssertEqual(inv.number, "INV-202605-001")
    }

    func testRefreshOverdue() {
        var db = seed.build()
        let changed = db.refreshOverdue(todayKey: "2026-12-31")
        XCTAssertFalse(changed.isEmpty)
        XCTAssertFalse(db.invoices.contains { $0.status == .pending })
    }

    func testDBRoundTripsThroughJSON() throws {
        let db = seed.build()
        let data = try JSONEncoder().encode(db)
        let back = try JSONDecoder().decode(DB.self, from: data)
        XCTAssertEqual(back, db)
        // snake_case columns, like supabase/schema.sql
        let json = String(decoding: data, as: UTF8.self)
        XCTAssertTrue(json.contains("\"capacity_kw\""))
        XCTAssertTrue(json.contains("\"site_id\""))
        XCTAssertTrue(json.contains("\"alert_rules\""))
    }

    func testSiteDecodesWithMissingFinanceColumns() throws {
        let json = #"{"id":"s1","name":"A","location":"L","type":"commercial","status":"active","capacity_kw":100,"battery_kwh":0,"price_per_kwh":0.12,"customer":"C","install_date":"2024-01-01","lat":1,"lng":2}"#
        let s = try JSONDecoder().decode(Site.self, from: Data(json.utf8))
        XCTAssertEqual(s.effectiveSystemCost, 90_000)
        XCTAssertEqual(s.effectiveAnnualOpex, 1_350)
        XCTAssertEqual(s.degradationPct, 0.5)
        XCTAssertEqual(s.tariffEscalationPct, 2)
    }

    func testSettingsDecodeNullsToDefaults() throws {
        let json = #"{"id":"settings","user_name":null,"language":"ar","discount_rate_pct":null,"co2_kg_per_kwh":"0.55"}"#
        let s = try JSONDecoder().decode(AppSettings.self, from: Data(json.utf8))
        XCTAssertEqual(s.userName, AppSettings.defaults.userName)
        XCTAssertEqual(s.language, .ar)
        XCTAssertEqual(s.discountRatePct, 6)
        XCTAssertEqual(s.co2KgPerKwh, 0.55)
    }
}
