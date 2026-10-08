package com.solarpulse.core

import com.solarpulse.core.finance.Finance
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.model.withFinanceDefaults
import com.solarpulse.core.seed.Seed
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FinanceTest {
    private fun site(
        cost: Double = 500.0, opex: Double = 0.0, degradation: Double = 0.0, escalation: Double = 0.0, price: Double = 0.1,
    ) = Site(
        id = "x", name = "x", location = "x", type = SiteType.COMMERCIAL, status = SiteStatus.ACTIVE,
        capacityKw = 1.0, customer = "x", installDate = "2020-01-01", pricePerKwh = price,
        systemCost = cost, annualOpex = opex, degradationPct = degradation, tariffEscalationPct = escalation,
    )

    @Test
    fun `hand computed flat case`() {
        // E1 = 1000 kWh, price 0.10, no degradation/escalation/opex, CAPEX 500 → CF_y = 100
        val p = Finance.projectSite(site(), 1000.0)
        val r = Finance.analyze(p, 0.0, 0.1)
        assertEquals(26, p.cashFlow.size)
        assertClose(-500.0, p.cashFlow[0])
        assertClose(100.0, p.cashFlow[25])
        assertClose(5.0, r.paybackYears!!, 1e-12, "payback")
        assertClose(400.0, r.roiPct, 1e-9, "roi") // (25·100 − 500) / 500
        assertClose(2000.0, r.npv, 1e-9, "npv @0%")
        assertClose(500.0 / 25000.0, r.lcoe, 1e-12, "lcoe") // (500 + 0) / (25 · 1000)
        assertClose(100.0, r.year1Savings, 1e-12)
        assertClose(2000.0, r.series.last().cumulative, 1e-9)
        // IRR of −500 then 25 × 100: annuity factor 5 → r ≈ 19.7805 % (solved independently)
        val irr = r.irrPct!!
        assertClose(0.0, Finance.npv(p.cashFlow, irr), 1e-4, "npv at irr")
        assertClose(19.7805, irr, 1e-3, "irr")
    }

    @Test
    fun `hand computed degradation, escalation, opex and discounting`() {
        val s = site(cost = 10_000.0, opex = 100.0, degradation = 1.0, escalation = 3.0, price = 0.2)
        val p = Finance.projectSite(s, 5000.0)
        // year 3: E = 5000·0.99², price = 0.2·1.03², CF = E·price − 100
        val e3 = 5000 * 0.99.pow(2)
        val p3 = 0.2 * 1.03.pow(2)
        assertClose(e3 * p3 - 100, p.cashFlow[3], 1e-9)
        val r = Finance.analyze(p, 6.0, 0.2)
        var npv = -10_000.0
        var dOpex = 0.0
        var dEnergy = 0.0
        for (y in 1..25) {
            val e = 5000 * 0.99.pow(y - 1)
            val cf = e * 0.2 * 1.03.pow(y - 1) - 100
            npv += cf / 1.06.pow(y)
            dOpex += 100 / 1.06.pow(y)
            dEnergy += e / 1.06.pow(y)
        }
        assertClose(npv, r.npv, 1e-6, "npv")
        assertClose((10_000 + dOpex) / dEnergy, r.lcoe, 1e-12, "lcoe")
        assertNotNull(r.paybackYears)
        // payback lies inside the year where the cumulative cash flow turns positive
        val y = r.series.first { it.cumulative >= 0 }.year
        assert(r.paybackYears!! > y - 1 && r.paybackYears!! <= y.toDouble())
    }

    @Test
    fun `irr and payback simple series`() {
        val cf = listOf(-1000.0, 300.0, 300.0, 300.0, 300.0, 300.0)
        val irr = Finance.irr(cf)!!
        assertClose(15.2382, irr, 1e-3, "irr")
        assertClose(WebReference["finance"].obj["irrSimple"]!!.num, irr, 1e-6, "irr vs web")
        assertClose(3 + 100.0 / 300.0, Finance.payback(cf)!!, 1e-12, "payback")
        assertClose(WebReference["finance"].obj["paybackSimple"]!!.num, Finance.payback(cf)!!, 1e-12)
    }

    @Test
    fun `never pays back`() {
        val cf = listOf(-1000.0) + List(25) { 10.0 }
        assertNull(Finance.payback(cf))
        val p = Finance.projectSite(site(cost = 1_000_000.0, opex = 1000.0), 10.0)
        val r = Finance.analyze(p, 6.0, 0.1)
        assertNull(r.paybackYears)
        assertNull(r.irrPct) // all cash flows negative → no sign change
    }

    @Test
    fun `contract defaults`() {
        val s = site(cost = 0.0, opex = 0.0).copy(capacityKw = 12.4).withFinanceDefaults()
        assertEquals(11160.0, s.systemCost) // round(12.4 × 900)
        assertEquals(167.0, s.annualOpex) // round(11160 × 1.5 %)
        val kept = site(cost = 900.0, opex = 0.0).withFinanceDefaults()
        assertEquals(0.0, kept.annualOpex) // opex 0 kept when a cost is set
    }

    @Test
    fun `site and portfolio match the web`() {
        val sim = WebReference.sim()
        val ref = WebReference["finance"].obj
        val input = ref["site"]!!.obj["input"]!!.obj
        val s = site(
            cost = input["systemCost"]!!.num, opex = input["annualOpex"]!!.num,
            degradation = input["degradationPct"]!!.num, escalation = input["tariffEscalationPct"]!!.num,
            price = input["pricePerKwh"]!!.num,
        ).copy(id = input["id"]!!.str, capacityKw = input["capacityKw"]!!.num, installDate = input["installDate"]!!.str)
        val e1 = Finance.yearOneKwh(sim, s)
        assertClose(ref["site"]!!.obj["e1"]!!.num, e1, 1e-4, "E1")
        val r = Finance.analyze(Finance.projectSite(s, e1), 6.0, s.pricePerKwh)
        assertResult(ref["site"]!!.obj["result"]!!.obj, r)

        val sites = Seed(sim).buildSites()
        val pf = Finance.analyzePortfolio(sim, sites, 6.0)
        assertResult(ref["portfolio"]!!.obj, pf.portfolio)
        ref["perSite"]!!.arr.forEachIndexed { i, x -> assertResult(x.obj["result"]!!.obj, pf.perSite[i].result) }
    }

    private fun assertResult(o: kotlinx.serialization.json.JsonObject, r: Finance.Result) {
        assertClose(o["systemCost"]!!.num, r.systemCost, 1e-6, "systemCost")
        assertClose(o["e1"]!!.num, r.e1, 1e-4, "e1")
        assertClose(o["year1Savings"]!!.num, r.year1Savings, 1e-5, "year1Savings")
        val pb = o["paybackYears"].numOrNull
        if (pb == null) assertNull(r.paybackYears) else assertClose(pb, r.paybackYears!!, 1e-6, "payback")
        assertClose(o["roiPct"]!!.num, r.roiPct, 1e-6, "roi")
        assertClose(o["npv"]!!.num, r.npv, 1e-3, "npv")
        val irr = o["irrPct"].numOrNull
        if (irr == null) assertNull(r.irrPct) else assertClose(irr, r.irrPct!!, 1e-5, "irr")
        assertClose(o["lcoe"]!!.num, r.lcoe, 1e-9, "lcoe")
    }
}
