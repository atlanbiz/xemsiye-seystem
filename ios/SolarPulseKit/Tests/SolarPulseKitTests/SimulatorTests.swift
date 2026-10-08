import XCTest
@testable import SolarPulseKit

/// Reference values were produced by running the web simulator (`src/lib/sim.ts`, `src/lib/seed.ts`)
/// with Node 22 under `TZ=UTC` — see ios/README.md ("Simulator parity").
final class SimulatorTests: XCTestCase {
    static let utc = TimeZone(identifier: "UTC")!
    /// 2026-10-08T12:00:00Z
    static let fixedNow = Date(timeIntervalSince1970: 1_791_460_800)

    let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })

    static let site01 = Site(id: "site-01", name: "x", location: "x", type: .utility, status: .active,
                             capacityKw: 820, batteryKwh: 600, pricePerKwh: 0.08, customer: "c",
                             installDate: "2024-01-01", lat: 0, lng: 0)

    func testFixedNowIsWhatWeThinkItIs() {
        XCTAssertEqual(ISODate.string(SimulatorTests.fixedNow), "2026-10-08T12:00:00.000Z")
        XCTAssertEqual(sim.todayKey, "2026-10-08")
    }

    func testHashMatchesWeb() {
        XCTAssertEqual(Simulator.hash("site-01"), 126_484_622)
        XCTAssertEqual(Simulator.hash(""), 2_166_136_261)
        // UTF-16 code units, like JS charCodeAt
        XCTAssertEqual(Simulator.hash("ئۈرۈمچى"), 4_089_484_232)
    }

    func testRandMatchesWeb() {
        XCTAssertEqual(Simulator.rand("wx2026-06-21"), 0.43811059230938554, accuracy: 1e-15)
        XCTAssertEqual(Simulator.rand("site-012026-06-21"), 0.9357610589358956, accuracy: 1e-15)
        XCTAssertEqual(Simulator.rand("inst0"), 0.12676901719532907, accuracy: 1e-15)
    }

    func testCurveFactors() {
        XCTAssertEqual(Simulator.solarCurve(12.5), 0.9899585292005758, accuracy: 1e-12)
        XCTAssertEqual(Simulator.solarCurve(7), 0.09032196941052942, accuracy: 1e-12)
        XCTAssertEqual(Simulator.solarCurve(5), 0)
        XCTAssertEqual(Simulator.solarCurve(20), 0)
        XCTAssertEqual(sim.seasonFactor(sim.days.parseDay("2026-06-21")), 0.9999733307695162, accuracy: 1e-12)
        XCTAssertEqual(sim.seasonFactor(sim.days.parseDay("2026-01-01")), 0.36708853071818986, accuracy: 1e-12)
        XCTAssertEqual(Simulator.weatherFactor("2026-06-21"), 0.8763843303080648, accuracy: 1e-12)
    }

    func testSiteOutputMatchesWeb() {
        let s = SimulatorTests.site01
        XCTAssertEqual(sim.siteKw(s, day: "2026-06-21", hour: 12.125), 579.0091048058575, accuracy: 1e-6)
        XCTAssertEqual(sim.siteDayKwh(s, day: "2026-06-21"), 4545.852236393882, accuracy: 1e-6)
        XCTAssertEqual(sim.siteDayKwh(s, day: "2026-06-21", untilHour: 13.4), 2511.231479877936, accuracy: 1e-6)
        XCTAssertEqual(sim.siteDayKwh(s, day: "2025-12-15"), 1692.0793148311313, accuracy: 1e-6)
        XCTAssertEqual(sim.siteDayKwhCached(s, day: "2026-06-21"), 4545.852236393882, accuracy: 1e-6)
        XCTAssertEqual(sim.consumptionKw([s], hour: 9.5, day: "2026-06-21"), 174.3318270933407, accuracy: 1e-6)
    }

    func testDeterministicAcrossInstances() {
        let other = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
        let s = SimulatorTests.site01
        for day in ["2026-03-01", "2026-07-15", "2026-09-30"] {
            XCTAssertEqual(sim.siteDayKwh(s, day: day), other.siteDayKwh(s, day: day))
        }
    }

    func testBeforeInstallProducesNothing() {
        var s = SimulatorTests.site01
        s.installDate = "2026-07-01"
        XCTAssertEqual(sim.siteDayKwh(s, day: "2026-06-21"), 0)
    }

    func testStatusFactorOnlyAppliesToday() {
        var s = SimulatorTests.site01
        s.status = .offline
        XCTAssertEqual(sim.siteKw(s, day: "2026-10-08", hour: 12), 0)
        XCTAssertGreaterThan(sim.siteKw(s, day: "2026-10-07", hour: 12), 0)
        s.status = .idle
        var active = s
        active.status = .active
        XCTAssertEqual(sim.siteKw(s, day: "2026-10-08", hour: 12), sim.siteKw(active, day: "2026-10-08", hour: 12) * 0.15, accuracy: 1e-9)
    }

    func testHourlySeriesLabelsAndNowCutoff() {
        let series = sim.hourlySeries([SimulatorTests.site01], day: "2026-10-08", stepMin: 30)
        XCTAssertEqual(series.count, 49)
        XCTAssertEqual(series.first?.label, "00:00")
        XCTAssertEqual(series[25].label, "12:30")
        XCTAssertEqual(series.last?.label, "00:00")
        XCTAssertNotNil(series[24].kw) // 12:00 == now
        XCTAssertNil(series[25].kw)
    }

    func testDailySeriesAndTotalsAgree() {
        let from = sim.days.parseDay("2026-06-01")
        let to = sim.days.parseDay("2026-06-30")
        let series = sim.dailySeries([SimulatorTests.site01], from: from, to: to)
        XCTAssertEqual(series.count, 30)
        XCTAssertEqual(series.reduce(0) { $0 + $1.kwh }, sim.totalKwh([SimulatorTests.site01], from: from, to: to), accuracy: 1e-6)
    }

    func testDeviceStats() {
        let d = { (status: DeviceStatus, type: DeviceType, eff: Double, health: Double) in
            Device(id: UUID().uuidString, siteId: "s", name: "n", type: type, model: "", serial: "", status: status,
                   health: health, efficiency: eff, firmware: "", installedAt: "2024-01-01", lastSeen: "2026-10-08T12:00:00.000Z")
        }
        let stats = Simulator.deviceStats([d(.online, .inverter, 98, 90), d(.warning, .inverter, 96, 80),
                                           d(.offline, .inverter, 97, 70), d(.online, .battery, 0, 88)])
        XCTAssertEqual(stats.availability, 62.5, accuracy: 1e-9)
        XCTAssertEqual(stats.inverterEfficiency, (98 + 96 + 0) / 3, accuracy: 1e-9)
        XCTAssertEqual(stats.batteryHealth, 88)
        XCTAssertEqual(stats.offline, 1)
    }

    func testWeatherKind() {
        XCTAssertEqual(WeatherKind(code: 0), .sunny)
        XCTAssertEqual(WeatherKind(code: 2), .partly)
        XCTAssertEqual(WeatherKind(code: 48), .fog)
        XCTAssertEqual(WeatherKind(code: 75), .snow)
        XCTAssertEqual(WeatherKind(code: 61), .rain)
        XCTAssertEqual(WeatherKind(code: 96), .storm)
    }

    func testJSRound() {
        XCTAssertEqual(jsRound(2.5), 3)
        XCTAssertEqual(jsRound(-2.5), -2)
        XCTAssertEqual(jsRound(-2.6), -3)
    }
}
