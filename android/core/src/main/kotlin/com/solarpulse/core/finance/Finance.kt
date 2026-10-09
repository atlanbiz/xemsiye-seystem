package com.solarpulse.core.finance

import com.solarpulse.core.model.Site
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days
import kotlin.math.pow

/**
 * Financial (ROI) model — docs/PLATFORM.md §3.5, same formulas as src/lib/finance.ts.
 * 25-year horizon, yearly cash flows; index 0 = installation year (CAPEX only).
 */
object Finance {
    const val HORIZON = 25

    data class Projection(
        val systemCost: Double,
        val energy: List<Double>,
        val revenue: List<Double>,
        val opex: List<Double>,
        val cashFlow: List<Double>,
    )

    data class YearPoint(val year: Int, val cashFlow: Double, val cumulative: Double)

    data class Result(
        val systemCost: Double,
        /** Year-1 kWh. */
        val e1: Double,
        /** E1 × pricePerKwh. */
        val year1Savings: Double,
        /** Fractional years until the cumulative cash flow reaches ≥ 0; null = not within the horizon. */
        val paybackYears: Double?,
        val roiPct: Double,
        val npv: Double,
        /** null = no sign change (never pays back). */
        val irrPct: Double?,
        /** Levelised cost of energy, per kWh. */
        val lcoe: Double,
        val series: List<YearPoint>,
    )

    data class SiteAnalysis(val site: Site, val projection: Projection, val result: Result)
    data class Portfolio(val perSite: List<SiteAnalysis>, val portfolio: Result)

    /**
     * Year-1 energy E1: simulated kWh over the 365 full days ending yesterday. The install date is
     * ignored so a site commissioned this year still gets a full-year estimate.
     */
    fun yearOneKwh(sim: Sim, site: Site): Double {
        val today = sim.today()
        var sum = 0.0
        for (i in 1..365) sum += sim.siteFullDayKwh(site, Days.key(today.minusDays(i.toLong())))
        return sum
    }

    fun projectSite(site: Site, e1: Double): Projection {
        val energy = arrayListOf(0.0)
        val revenue = arrayListOf(0.0)
        val opex = arrayListOf(0.0)
        val cashFlow = arrayListOf(-site.systemCost)
        for (y in 1..HORIZON) {
            val e = e1 * (1 - site.degradationPct / 100).pow(y - 1)
            val price = site.pricePerKwh * (1 + site.tariffEscalationPct / 100).pow(y - 1)
            energy.add(e)
            revenue.add(e * price)
            opex.add(site.annualOpex)
            cashFlow.add(e * price - site.annualOpex)
        }
        return Projection(site.systemCost, energy, revenue, opex, cashFlow)
    }

    /** Portfolio = element-wise sum of site projections. */
    fun combine(list: List<Projection>): Projection {
        fun sum(f: (Projection) -> List<Double>) = (0..HORIZON).map { y -> list.sumOf { f(it)[y] } }
        return Projection(
            systemCost = list.sumOf { it.systemCost },
            energy = sum { it.energy },
            revenue = sum { it.revenue },
            opex = sum { it.opex },
            cashFlow = sum { it.cashFlow },
        )
    }

    fun npv(cashFlow: List<Double>, ratePct: Double): Double {
        var s = 0.0
        cashFlow.forEachIndexed { y, cf -> s += cf / (1 + ratePct / 100).pow(y) }
        return s
    }

    /** IRR (in %) by bisection on NPV(r) = 0 over (−99 %, 1000 %]. */
    fun irr(cashFlow: List<Double>): Double? {
        var lo = -99.0
        var hi = 1000.0
        var fLo = npv(cashFlow, lo)
        if (fLo * npv(cashFlow, hi) > 0) return null
        var i = 0
        while (i < 200 && hi - lo > 1e-7) {
            val mid = (lo + hi) / 2
            val f = npv(cashFlow, mid)
            if (f == 0.0) return mid
            if (fLo * f < 0) {
                hi = mid
            } else {
                lo = mid
                fLo = f
            }
            i++
        }
        return (lo + hi) / 2
    }

    /** Fractional years until the cumulative cash flow first reaches ≥ 0. */
    fun payback(cashFlow: List<Double>): Double? {
        var cum = cashFlow[0]
        if (cum >= 0) return 0.0
        for (y in 1 until cashFlow.size) {
            val next = cum + cashFlow[y]
            if (next >= 0) return y - 1 + -cum / cashFlow[y]
            cum = next
        }
        return null
    }

    fun analyze(p: Projection, discountRatePct: Double, pricePerKwh: Double): Result {
        fun d(y: Int) = (1 + discountRatePct / 100).pow(y)
        val operating = p.cashFlow.drop(1).sum()
        var discOpex = 0.0
        var discEnergy = 0.0
        for (y in 1..HORIZON) {
            discOpex += p.opex[y] / d(y)
            discEnergy += p.energy[y] / d(y)
        }
        var cum = 0.0
        return Result(
            systemCost = p.systemCost,
            e1 = p.energy[1],
            year1Savings = p.energy[1] * pricePerKwh,
            paybackYears = payback(p.cashFlow),
            roiPct = if (p.systemCost > 0) ((operating - p.systemCost) / p.systemCost) * 100 else 0.0,
            npv = npv(p.cashFlow, discountRatePct),
            irrPct = irr(p.cashFlow),
            lcoe = if (discEnergy > 0) (p.systemCost + discOpex) / discEnergy else 0.0,
            series = p.cashFlow.mapIndexed { year, cf ->
                cum += cf
                YearPoint(year, cf, cum)
            },
        )
    }

    fun analyzeSite(sim: Sim, site: Site, discountRatePct: Double): SiteAnalysis {
        val projection = projectSite(site, yearOneKwh(sim, site))
        return SiteAnalysis(site, projection, analyze(projection, discountRatePct, site.pricePerKwh))
    }

    /** Per-site analysis plus the portfolio, sharing one E1 computation per site. */
    fun analyzePortfolio(sim: Sim, sites: List<Site>, discountRatePct: Double): Portfolio {
        val perSite = sites.map { analyzeSite(sim, it, discountRatePct) }
        val total = combine(perSite.map { it.projection })
        // portfolio year-1 savings = Σ E1 × price, expressed through an energy-weighted price
        val avgPrice = if (total.energy[1] > 0) {
            perSite.sumOf { it.projection.energy[1] * it.site.pricePerKwh } / total.energy[1]
        } else {
            0.0
        }
        return Portfolio(perSite, analyze(total, discountRatePct, avgPrice))
    }
}
