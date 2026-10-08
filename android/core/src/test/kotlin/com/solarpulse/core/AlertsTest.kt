package com.solarpulse.core

import com.solarpulse.core.alerts.Alerts
import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.seed.Seed
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertsTest {
    private val sim = WebReference.sim()
    private val db = Seed(sim).build()
    private val now = WebReference.now

    private fun rule(metric: AlertMetric, threshold: Double = 0.0, siteId: String? = null) =
        AlertRule("r", "r", metric, threshold, siteId, createdAt = Days.iso(now))

    @Test
    fun `seeded rules fire exactly like the web`() {
        val ref = WebReference["alerts"].arr
        val firings = Alerts.evaluate(db, sim, now)
        assertEquals(ref.map { it.obj["ruleId"]!!.str }, firings.map { it.rule.id })
        firings.forEachIndexed { i, f ->
            val m = ref[i].obj["matches"]!!.arr
            assertEquals(m.size, f.matches.size, "matches of ${f.rule.id}")
            f.matches.forEachIndexed { j, x ->
                val o = m[j].obj
                assertEquals(o["label"]!!.str, x.label)
                assertEquals(o["link"]!!.str, x.link)
                val v = o["value"].numOrNull
                if (v == null) assertEquals(null, x.value) else assertClose(v, x.value!!, 1e-6, "value ${f.rule.id}")
            }
        }
    }

    @Test
    fun `cooldown of 6 hours and disabled rules`() {
        val r = rule(AlertMetric.SITE_OFFLINE)
        val recent = r.copy(lastTriggeredAt = Days.iso(now - 5 * 3600_000L))
        val old = r.copy(lastTriggeredAt = Days.iso(now - 6 * 3600_000L - 1))
        assertTrue(Alerts.evaluate(db.copy(alertRules = listOf(recent)), sim, now).isEmpty())
        assertEquals(1, Alerts.evaluate(db.copy(alertRules = listOf(old)), sim, now).size)
        assertTrue(Alerts.evaluate(db.copy(alertRules = listOf(r.copy(enabled = false))), sim, now).isEmpty())
    }

    @Test
    fun `site scope`() {
        val all = Alerts.matchRule(rule(AlertMetric.DEVICE_HEALTH_BELOW, 101.0), db, sim, now)
        assertEquals(db.devices.size, all.size)
        val one = Alerts.matchRule(rule(AlertMetric.DEVICE_HEALTH_BELOW, 101.0, "site-01"), db, sim, now)
        assertEquals(db.devices.count { it.siteId == "site-01" }, one.size)
    }

    @Test
    fun `yield is only checked after 14 00 local time`() {
        val morning = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), sim.zone).withHour(13).withMinute(59).toInstant().toEpochMilli()
        val morningSim = Sim(sim.zone) { morning }
        assertTrue(Alerts.matchRule(rule(AlertMetric.SITE_YIELD_BELOW, 100.0), db, morningSim, morning).isEmpty())
        assertEquals(db.sites.size, Alerts.matchRule(rule(AlertMetric.SITE_YIELD_BELOW, 100.0), db, sim, now).size)
    }

    @Test
    fun `offline minutes and overdue days thresholds`() {
        val offline = db.devices.filter { it.status.key == "offline" }
        assertTrue(offline.isNotEmpty())
        val minutes = offline.map { (now - Days.parseMillis(it.lastSeen)!!) / 60000.0 }
        val cut = minutes.minOrNull()!!
        assertEquals(minutes.count { it > cut }, Alerts.matchRule(rule(AlertMetric.DEVICE_OFFLINE_MINUTES, cut), db, sim, now).size)

        val inv = db.invoices.first().copy(id = "inv-x", status = InvoiceStatus.OVERDUE, dueAt = Days.key(sim.today().minusDays(11)))
        val d = db.copy(invoices = listOf(inv))
        assertEquals(1, Alerts.matchRule(rule(AlertMetric.INVOICE_OVERDUE_DAYS, 10.0), d, sim, now).size)
        assertEquals(0, Alerts.matchRule(rule(AlertMetric.INVOICE_OVERDUE_DAYS, 11.0), d, sim, now).size)
        assertEquals(11.0, Alerts.matchRule(rule(AlertMetric.INVOICE_OVERDUE_DAYS, 10.0), d, sim, now).single().value)
    }

    @Test
    fun `describe names three matches then +N`() {
        val f = Alerts.Firing(rule(AlertMetric.SITE_OFFLINE), (1..5).map { Alerts.Match("S$it", if (it == 2) 1.5 else null, "/x") })
        assertEquals("S1; S2 (1.5); S3 +2", Alerts.describe(f) { it.value.toString() })
        assertEquals("a%20b%C3%BC", Alerts.encodeURIComponent("a bü"))
    }
}
