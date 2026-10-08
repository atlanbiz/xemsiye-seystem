import Foundation

/// Financial analysis (ROI) — PLATFORM.md §3.5, 25-year horizon.
public enum Finance {
    public static let horizonYears = 25

    public struct Inputs: Sendable, Equatable {
        /// Year-1 energy, kWh.
        public var e1: Double
        public var pricePerKwh: Double
        public var systemCost: Double
        public var annualOpex: Double
        public var degradationPct: Double
        public var tariffEscalationPct: Double

        public init(e1: Double, pricePerKwh: Double, systemCost: Double, annualOpex: Double,
                    degradationPct: Double, tariffEscalationPct: Double) {
            self.e1 = e1
            self.pricePerKwh = pricePerKwh
            self.systemCost = systemCost
            self.annualOpex = annualOpex
            self.degradationPct = degradationPct
            self.tariffEscalationPct = tariffEscalationPct
        }
    }

    public struct Result: Sendable, Equatable {
        /// CF_0 … CF_25 (CF_0 = −systemCost).
        public let cashFlows: [Double]
        /// Cumulative cash flow after each year, index 0 = year 0.
        public let cumulative: [Double]
        /// E_1 … E_25.
        public let energy: [Double]
        public let systemCost: Double
        /// Fractional years until cumulative CF ≥ 0, nil if never within the horizon.
        public let paybackYears: Double?
        public let roiPct: Double
        public let npv: Double
        public let irr: Double?
        public let lcoe: Double?
        /// Year-1 gross savings (E1 × price1).
        public let annualSavings: Double
        /// Σ CF_1…25.
        public let lifetimeNet: Double
        /// systemCost + Σ discounted opex (LCOE numerator).
        public let lcoeCost: Double
        /// Σ discounted energy (LCOE denominator).
        public let lcoeEnergy: Double
    }

    /// Analyse one site.
    public static func analyze(_ i: Inputs, discountRatePct: Double, years: Int = horizonYears) -> Result {
        let r = discountRatePct / 100
        var flows: [Double] = [-i.systemCost]
        var energy: [Double] = []
        var discOpex = 0.0
        var discEnergy = 0.0
        for y in 1...max(1, years) {
            let e = i.e1 * pow(1 - i.degradationPct / 100, Double(y - 1))
            let price = i.pricePerKwh * pow(1 + i.tariffEscalationPct / 100, Double(y - 1))
            flows.append(e * price - i.annualOpex)
            energy.append(e)
            let df = pow(1 + r, Double(y))
            discOpex += i.annualOpex / df
            discEnergy += e / df
        }
        return result(flows: flows, energy: energy, systemCost: i.systemCost, discountRatePct: discountRatePct,
                      annualSavings: i.e1 * i.pricePerKwh, lcoeCost: i.systemCost + discOpex, lcoeEnergy: discEnergy)
    }

    /// Portfolio = element-wise sum of the sites' cash flows; LCOE from summed numerators/denominators.
    public static func portfolio(_ results: [Result], discountRatePct: Double) -> Result? {
        guard let first = results.first else { return nil }
        var flows = Array(repeating: 0.0, count: first.cashFlows.count)
        var energy = Array(repeating: 0.0, count: first.energy.count)
        var cost = 0.0, savings = 0.0, lc = 0.0, le = 0.0
        for res in results {
            for (k, v) in res.cashFlows.enumerated() where k < flows.count { flows[k] += v }
            for (k, v) in res.energy.enumerated() where k < energy.count { energy[k] += v }
            cost += res.systemCost
            savings += res.annualSavings
            lc += res.lcoeCost
            le += res.lcoeEnergy
        }
        return result(flows: flows, energy: energy, systemCost: cost, discountRatePct: discountRatePct,
                      annualSavings: savings, lcoeCost: lc, lcoeEnergy: le)
    }

    static func result(flows: [Double], energy: [Double], systemCost: Double, discountRatePct: Double,
                       annualSavings: Double, lcoeCost: Double, lcoeEnergy: Double) -> Result {
        var cumulative: [Double] = []
        var acc = 0.0
        for f in flows {
            acc += f
            cumulative.append(acc)
        }
        let lifetimeNet = flows.dropFirst().reduce(0, +)
        return Result(
            cashFlows: flows,
            cumulative: cumulative,
            energy: energy,
            systemCost: systemCost,
            paybackYears: payback(flows),
            roiPct: systemCost > 0 ? (lifetimeNet - systemCost) / systemCost * 100 : 0,
            npv: npv(rate: discountRatePct / 100, flows: flows),
            irr: irr(flows),
            lcoe: lcoeEnergy > 0 ? lcoeCost / lcoeEnergy : nil,
            annualSavings: annualSavings,
            lifetimeNet: lifetimeNet,
            lcoeCost: lcoeCost,
            lcoeEnergy: lcoeEnergy
        )
    }

    /// Σ CF_y / (1+r)^y, y = 0…n.
    public static func npv(rate: Double, flows: [Double]) -> Double {
        var s = 0.0
        for (y, f) in flows.enumerated() { s += f / pow(1 + rate, Double(y)) }
        return s
    }

    /// Internal rate of return by bisection on NPV(rate) = 0. nil when NPV doesn't change sign in (−99 %, 1000 %).
    public static func irr(_ flows: [Double], tolerance: Double = 1e-9) -> Double? {
        var lo = -0.99
        var hi = 10.0
        var fLo = npv(rate: lo, flows: flows)
        let fHi = npv(rate: hi, flows: flows)
        guard fLo.isFinite, fHi.isFinite, fLo * fHi <= 0 else { return nil }
        if fLo == 0 { return lo }
        if fHi == 0 { return hi }
        for _ in 0..<300 {
            let mid = (lo + hi) / 2
            let fMid = npv(rate: mid, flows: flows)
            if abs(fMid) < tolerance || (hi - lo) / 2 < tolerance { return mid }
            if (fMid < 0) == (fLo < 0) {
                lo = mid
                fLo = fMid
            } else {
                hi = mid
            }
        }
        return (lo + hi) / 2
    }

    /// Fractional payback: first year y where cumulative ≥ 0, interpolated inside that year.
    public static func payback(_ flows: [Double]) -> Double? {
        guard !flows.isEmpty else { return nil }
        var cum = flows[0]
        if cum >= 0 { return 0 }
        for y in 1..<flows.count {
            let prev = cum
            cum += flows[y]
            if cum >= 0 {
                let cf = flows[y]
                return cf > 0 ? Double(y - 1) + (-prev / cf) : Double(y)
            }
        }
        return nil
    }

    /// Default inputs for a site (contract defaults applied by `Site.effective*`).
    public static func inputs(for site: Site, e1: Double) -> Inputs {
        Inputs(e1: e1, pricePerKwh: site.pricePerKwh, systemCost: site.effectiveSystemCost,
               annualOpex: site.effectiveAnnualOpex, degradationPct: site.degradationPct,
               tariffEscalationPct: site.tariffEscalationPct)
    }

    /// Year-1 energy E1 (web `yearOneKwh`): simulated kWh over the 365 full days ending yesterday.
    /// The install date is ignored so a site commissioned this year still gets a full-year estimate.
    public static func yearOneEnergy(_ site: Site, sim: Simulator) -> Double {
        var probe = site
        probe.installDate = "0000-01-01"
        let today = sim.now
        var sum = 0.0
        for i in 1...365 {
            sum += sim.siteDayKwhCached(probe, day: sim.days.dayKey(sim.days.addDays(today, -i)))
        }
        return sum
    }
}
