package com.solarpulse.core

import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.seed.Seed
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.sim.SimMath
import kotlin.test.Test
import kotlin.test.assertEquals

class SimParityTest {
    private val sim = WebReference.sim()

    private val site = Site(
        id = "site-01", name = "T", location = "L", type = SiteType.UTILITY, status = SiteStatus.ACTIVE,
        capacityKw = 820.0, batteryKwh = 600.0, pricePerKwh = 0.08, customer = "C", installDate = "2024-01-01",
        lat = 42.95, lng = 89.18, systemCost = 738000.0, annualOpex = 11070.0,
    )

    @Test
    fun `hash matches Math_imul based FNV-1a`() {
        for (p in WebReference["hash"].arr) {
            val s = p.arr[0].str
            assertEquals(p.arr[1].num.toLong(), SimMath.hash(s), "hash('$s')")
        }
    }

    @Test
    fun `rand is bit-identical`() {
        for (p in WebReference["rand"].arr) {
            val s = p.arr[0].str
            assertEquals(p.arr[1].num, SimMath.rand(s), "rand('$s')")
        }
    }

    @Test
    fun `curves and factors`() {
        for (p in WebReference["solarCurve"].arr) assertClose(p.arr[1].num, SimMath.solarCurve(p.arr[0].num), 1e-12, "solarCurve")
        for (p in WebReference["weatherFactor"].arr) assertEquals(p.arr[1].num, SimMath.weatherFactor(p.arr[0].str), "weatherFactor")
        for (p in WebReference["seasonFactor"].arr) assertClose(p.arr[1].num, sim.seasonFactor(p.arr[0].str), 1e-12, "seasonFactor ${p.arr[0].str}")
    }

    @Test
    fun `siteDayKwh for site-01 on 2026-06-21 matches the web`() {
        // node: siteDayKwh(site-01, '2026-06-21') with TZ=Asia/Shanghai
        assertClose(4545.852236393882, sim.siteDayKwh(site, "2026-06-21"), 1e-6, "siteDayKwh 2026-06-21")
    }

    @Test
    fun `siteKw and siteDayKwh across the year`() {
        for (p in WebReference["siteKw"].arr) {
            val (d, h, v) = Triple(p.arr[0].str, p.arr[1].num, p.arr[2].num)
            assertClose(v, sim.siteKw(site, d, h), 1e-6, "siteKw $d $h")
        }
        for (p in WebReference["siteDayKwh"].arr) assertClose(p.arr[1].num, sim.siteDayKwh(site, p.arr[0].str), 1e-6, "siteDayKwh ${p.arr[0].str}")
        for (p in WebReference["siteDayKwhPartial"].arr) {
            assertClose(p.arr[2].num, sim.siteDayKwh(site, p.arr[0].str, p.arr[1].num), 1e-6, "partial")
        }
        assertEquals(0.0, sim.siteDayKwh(site.copy(installDate = "2027-01-01"), "2026-06-21"))
    }

    @Test
    fun `fleet level live power, load and energy flow`() {
        val sites = Seed(sim).buildSites()
        for (p in WebReference["consumptionKw"].arr) {
            assertClose(p.arr[1].num, sim.consumptionKw(sites, p.arr[0].num, "2026-06-21"), 1e-6, "consumption ${p.arr[0].num}")
        }
        assertClose(WebReference["liveKw"].num, sim.liveKw(sites, WebReference.now), 1e-6, "liveKw")
        val f = sim.energyFlow(sites, WebReference.now)
        val ref = WebReference["energyFlow"].obj
        assertClose(ref["solar"]!!.num, f.solar, 1e-6, "solar")
        assertClose(ref["consumption"]!!.num, f.consumption, 1e-6, "consumption")
        assertClose(ref["battery"]!!.num, f.battery, 1e-6, "battery")
        assertClose(ref["grid"]!!.num, f.grid, 1e-6, "grid")
        assertClose(ref["batterySoc"]!!.num, f.batterySoc, 1e-9, "soc")
        assertClose(ref["batteryCapacity"]!!.num, f.batteryCapacity, 1e-9, "capacity")
    }

    @Test
    fun `simulated weather`() {
        val w = sim.simWeather(WebReference.now)
        val ref = WebReference["simWeather"].obj
        assertEquals(ref["temp"]!!.num.toInt(), w.temp)
        assertEquals(ref["code"]!!.num.toInt(), w.code)
        assertEquals(ref["irradiance"]!!.num.toInt(), w.irradiance)
        assertEquals(ref["wind"]!!.num.toInt(), w.wind)
        assertEquals(ref["humidity"]!!.num.toInt(), w.humidity)
    }

    @Test
    fun `device stats over the seed`() {
        val seed = Seed(sim)
        val devices = seed.buildDevices(seed.buildSites())
        val s = Sim.deviceStats(devices)
        val ref = WebReference["deviceStats"].obj
        assertClose(ref["availability"]!!.num, s.availability, 1e-9)
        assertClose(ref["inverterEfficiency"]!!.num, s.inverterEfficiency, 1e-9)
        assertClose(ref["batteryHealth"]!!.num, s.batteryHealth, 1e-9)
        assertEquals(ref["online"]!!.num.toInt(), s.online)
        assertEquals(ref["warning"]!!.num.toInt(), s.warning)
        assertEquals(ref["offline"]!!.num.toInt(), s.offline)
    }

    @Test
    fun `simulator is deterministic`() {
        val a = Sim(WebReference.zone) { WebReference.now }
        val b = Sim(WebReference.zone) { WebReference.now }
        val days = listOf("2025-01-15", "2025-07-04", "2026-03-01")
        for (d in days) assertEquals(a.siteDayKwh(site, d), b.siteDayKwh(site, d))
        // cached and uncached agree for past days
        for (d in days) assertEquals(a.siteDayKwh(site, d), a.siteDayKwhCached(site, d))
    }

    @Test
    fun `closed-form full day equals the 15-minute integration`() {
        for (d in listOf("2025-01-15", "2025-07-04", "2026-03-01", "2026-06-20")) {
            assertClose(sim.siteDayKwh(site, d), sim.siteFullDayKwh(site, d), 1e-6, "siteFullDayKwh $d")
        }
    }

    @Test
    fun `status factor only applies to today`() {
        val off = site.copy(status = SiteStatus.OFFLINE)
        assertEquals(0.0, sim.siteDayKwh(off, "2026-06-21"))
        assertClose(sim.siteDayKwh(site, "2026-06-20"), sim.siteDayKwh(off, "2026-06-20"), 0.0)
        val idle = site.copy(status = SiteStatus.IDLE)
        assertClose(sim.siteDayKwh(site, "2026-06-21") * 0.15, sim.siteDayKwh(idle, "2026-06-21"), 1e-6)
    }
}
