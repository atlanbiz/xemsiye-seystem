import XCTest
@testable import SolarPulseKit

final class FinanceTests: XCTestCase {
    /// Hand-computed: 1000 kWh × 0.10 = 100/yr, no opex, no degradation/escalation, cost 500, r = 0.
    func testFlatCase() {
        let r = Finance.analyze(Finance.Inputs(e1: 1000, pricePerKwh: 0.1, systemCost: 500, annualOpex: 0,
                                               degradationPct: 0, tariffEscalationPct: 0), discountRatePct: 0)
        XCTAssertEqual(r.cashFlows.count, 26)
        XCTAssertEqual(r.cashFlows[0], -500)
        XCTAssertEqual(r.cashFlows[1], 100, accuracy: 1e-9)
        XCTAssertEqual(r.cashFlows[25], 100, accuracy: 1e-9)
        XCTAssertEqual(r.paybackYears ?? -1, 5, accuracy: 1e-9)         // −500 + 5×100 = 0
        XCTAssertEqual(r.roiPct, 400, accuracy: 1e-9)                     // (2500 − 500) / 500
        XCTAssertEqual(r.npv, 2000, accuracy: 1e-9)                       // r = 0 → plain sum
        XCTAssertEqual(r.lcoe ?? -1, 0.02, accuracy: 1e-12)               // 500 / 25 000 kWh
        XCTAssertEqual(r.irr ?? -1, 0.197805304914778, accuracy: 1e-7)   // annuity factor 5 over 25 years
        XCTAssertEqual(r.annualSavings, 100, accuracy: 1e-9)
        XCTAssertEqual(r.cumulative.last ?? 0, 2000, accuracy: 1e-9)
    }

    /// Typical commercial site with contract defaults (cost = 100 kW × 900, opex 1.5 %, 0.5 %/yr degradation, 2 % escalation, 6 % discount).
    func testRealisticCase() {
        let r = Finance.analyze(Finance.Inputs(e1: 150_000, pricePerKwh: 0.12, systemCost: 90_000, annualOpex: 1_350,
                                               degradationPct: 0.5, tariffEscalationPct: 2), discountRatePct: 6)
        XCTAssertEqual(r.cashFlows[1], 150_000 * 0.12 - 1_350, accuracy: 1e-9)
        XCTAssertEqual(r.cashFlows[25], 24_320.27760602899, accuracy: 1e-6)
        XCTAssertEqual(r.npv, 157_260.01894665288, accuracy: 1e-6)
        XCTAssertEqual(r.irr ?? -1, 0.19801773279691848, accuracy: 1e-7)
        XCTAssertEqual(r.paybackYears ?? -1, 5.2233716451673615, accuracy: 1e-9)
        XCTAssertEqual(r.roiPct, 463.004455060314, accuracy: 1e-6)
        XCTAssertEqual(r.lcoe ?? -1, 0.05850414842961244, accuracy: 1e-12)
    }

    func testIRRSimple() {
        XCTAssertEqual(Finance.irr([-100, 110]) ?? -1, 0.10, accuracy: 1e-8)
        XCTAssertEqual(Finance.irr([-100, 0, 121]) ?? -1, 0.10, accuracy: 1e-8)
        XCTAssertNil(Finance.irr([100, 100]))   // never negative → no root
    }

    func testNoPaybackWhenFlowsNeverRecoverCost() {
        let r = Finance.analyze(Finance.Inputs(e1: 10, pricePerKwh: 0.1, systemCost: 1_000, annualOpex: 5,
                                               degradationPct: 0, tariffEscalationPct: 0), discountRatePct: 6)
        XCTAssertNil(r.paybackYears)
        XCTAssertLessThan(r.roiPct, -90)
    }

    func testPortfolioSumsFlows() {
        let a = Finance.analyze(Finance.Inputs(e1: 1000, pricePerKwh: 0.1, systemCost: 500, annualOpex: 0,
                                               degradationPct: 0, tariffEscalationPct: 0), discountRatePct: 0)
        let b = Finance.analyze(Finance.Inputs(e1: 2000, pricePerKwh: 0.1, systemCost: 500, annualOpex: 0,
                                               degradationPct: 0, tariffEscalationPct: 0), discountRatePct: 0)
        let p = Finance.portfolio([a, b], discountRatePct: 0)!
        XCTAssertEqual(p.systemCost, 1000)
        XCTAssertEqual(p.cashFlows[1], 300, accuracy: 1e-9)
        XCTAssertEqual(p.npv, a.npv + b.npv, accuracy: 1e-9)
        XCTAssertEqual(p.lcoe ?? -1, 1000.0 / 75_000.0, accuracy: 1e-12)
        XCTAssertEqual(p.paybackYears ?? -1, 1000.0 / 300.0, accuracy: 1e-9)
        XCTAssertNil(Finance.portfolio([], discountRatePct: 6))
    }

    func testSiteDefaults() {
        let s = Site(id: "s", name: "n", location: "l", type: .commercial, status: .active, capacityKw: 100, batteryKwh: 0,
                     pricePerKwh: 0.12, customer: "c", installDate: "2020-01-01", lat: 0, lng: 0)
        let i = Finance.inputs(for: s, e1: 150_000)
        XCTAssertEqual(i.systemCost, 90_000)
        XCTAssertEqual(i.annualOpex, 1_350)
        XCTAssertEqual(i.degradationPct, 0.5)
        XCTAssertEqual(i.tariffEscalationPct, 2)
    }

    func testYearOneEnergyUsesLast365Days() {
        let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
        let s = SimulatorTests.site01 // installed 2024-01-01
        let e1 = Finance.yearOneEnergy(s, sim: sim)
        let expected = sim.totalKwh([s], from: sim.days.parseDay("2025-10-08"), to: sim.days.parseDay("2026-10-07"))
        XCTAssertEqual(e1, expected, accuracy: 1e-6)
        XCTAssertGreaterThan(e1, 820 * 365 * 2) // plausible specific yield
    }

    /// End-to-end parity with the web's `analyzePortfolio(buildSeed().sites, 6)` (Node, TZ=UTC, Date frozen at 2026-10-08T12:00Z).
    func testSeedPortfolioMatchesWeb() {
        let sim = Simulator(timeZone: SimulatorTests.utc, clock: { SimulatorTests.fixedNow })
        let db = SeedBuilder(sim: sim).build()
        let perSite = db.sites.map { site in
            Finance.analyze(Finance.inputs(for: site, e1: Finance.yearOneEnergy(site, sim: sim)), discountRatePct: 6)
        }
        let s1 = perSite[0]
        XCTAssertEqual(db.sites[0].systemCost, 738_000)
        XCTAssertEqual(db.sites[0].annualOpex, 11_070)
        XCTAssertEqual(s1.energy[0], 1_038_133.5906247293, accuracy: 1e-5)
        XCTAssertEqual(s1.npv, 340_952.9305098869, accuracy: 1e-4)
        XCTAssertEqual((s1.irr ?? 0) * 100, 10.114845462754602, accuracy: 1e-4)
        XCTAssertEqual(s1.paybackYears ?? -1, 9.525522445453934, accuracy: 1e-8)
        XCTAssertEqual(s1.roiPct, 200.38826347854206, accuracy: 1e-8)
        XCTAssertEqual(s1.lcoe ?? -1, 0.06931680394342991, accuracy: 1e-12)

        let p = Finance.portfolio(perSite, discountRatePct: 6)!
        XCTAssertEqual(p.systemCost, 3_253_860)
        XCTAssertEqual(p.npv, 2_648_615.667786784, accuracy: 1e-3)
        XCTAssertEqual((p.irr ?? 0) * 100, 12.930094597424613, accuracy: 1e-4)
        XCTAssertEqual(p.paybackYears ?? -1, 7.775825667330362, accuracy: 1e-8)
        XCTAssertEqual(p.roiPct, 272.30703752216675, accuracy: 1e-8)
        XCTAssertEqual(p.lcoe ?? -1, 0.06936198156493842, accuracy: 1e-12)
        XCTAssertEqual(p.annualSavings, 444_112.43442913226, accuracy: 1e-5)
    }
}
