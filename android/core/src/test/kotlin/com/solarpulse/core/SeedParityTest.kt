package com.solarpulse.core

import com.solarpulse.core.seed.Seed
import com.solarpulse.core.time.Days
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedParityTest {
    private val sim = WebReference.sim()
    private val db = Seed(sim).build()
    private val ref = WebReference["seed"].obj

    private fun instant(s: String?) = s?.let { Days.parseMillis(it) }

    @Test
    fun sites() {
        val r = ref["sites"]!!.arr
        assertEquals(16, db.sites.size)
        assertEquals(r.size, db.sites.size)
        db.sites.forEachIndexed { i, s ->
            val o = r[i].obj
            assertEquals(o["id"]!!.str, s.id)
            assertEquals(o["name"]!!.str, s.name)
            assertEquals(o["location"]!!.str, s.location)
            assertEquals(o["type"]!!.str, s.type.key)
            assertEquals(o["status"]!!.str, s.status.key)
            assertEquals(o["capacityKw"]!!.num, s.capacityKw)
            assertEquals(o["batteryKwh"]!!.num, s.batteryKwh)
            assertEquals(o["pricePerKwh"]!!.num, s.pricePerKwh)
            assertEquals(o["customer"]!!.str, s.customer)
            assertEquals(o["installDate"]!!.str, s.installDate, "installDate ${s.id}")
            assertEquals(o["lat"]!!.num, s.lat)
            assertEquals(o["lng"]!!.num, s.lng)
            assertEquals(o["systemCost"]!!.num, s.systemCost)
            assertEquals(o["annualOpex"]!!.num, s.annualOpex)
            assertEquals(o["degradationPct"]!!.num, s.degradationPct)
            assertEquals(o["tariffEscalationPct"]!!.num, s.tariffEscalationPct)
        }
    }

    @Test
    fun devices() {
        val r = ref["devices"]!!.arr
        assertEquals(r.size, db.devices.size)
        db.devices.forEachIndexed { i, d ->
            val o = r[i].obj
            assertEquals(o["id"]!!.str, d.id)
            assertEquals(o["siteId"]!!.str, d.siteId)
            assertEquals(o["name"]!!.str, d.name)
            assertEquals(o["type"]!!.str, d.type.key)
            assertEquals(o["model"]!!.str, d.model)
            assertEquals(o["serial"]!!.str, d.serial, "serial ${d.id}")
            assertEquals(o["status"]!!.str, d.status.key)
            assertEquals(o["health"]!!.num, d.health)
            assertEquals(o["efficiency"]!!.num, d.efficiency)
            assertEquals(o["firmware"]!!.str, d.firmware)
            assertEquals(o["installedAt"]!!.str, d.installedAt)
            assertEquals(instant(o["lastSeen"]!!.str), instant(d.lastSeen), "lastSeen ${d.id}")
        }
    }

    @Test
    fun tickets() {
        val r = ref["tickets"]!!.arr
        assertEquals(r.size, db.tickets.size)
        db.tickets.forEachIndexed { i, t ->
            val o = r[i].obj
            assertEquals(o["id"]!!.str, t.id)
            assertEquals(o["siteId"]!!.str, t.siteId)
            assertEquals(o["deviceId"].strOrNull, t.deviceId)
            assertEquals(o["title"]!!.str, t.title)
            assertEquals(o["priority"]!!.str, t.priority.key)
            assertEquals(o["status"]!!.str, t.status.key)
            assertEquals(o["assignee"]!!.str, t.assignee)
            assertEquals(o["dueDate"]!!.str, t.dueDate)
            assertEquals(o["createdAt"]!!.str, t.createdAt)
        }
    }

    @Test
    fun invoices() {
        val r = ref["invoices"]!!.arr
        assertEquals(r.size, db.invoices.size)
        db.invoices.forEachIndexed { i, v ->
            val o = r[i].obj
            assertEquals(o["id"]!!.str, v.id)
            assertEquals(o["number"]!!.str, v.number)
            assertEquals(o["period"]!!.str, v.period)
            assertEquals(o["energyKwh"]!!.num, v.energyKwh, "energy ${v.id}")
            assertEquals(o["rate"]!!.num, v.rate)
            assertEquals(o["amount"]!!.num, v.amount, "amount ${v.id}")
            assertEquals(o["status"]!!.str, v.status.key, "status ${v.id}")
            assertEquals(o["issuedAt"]!!.str, v.issuedAt)
            assertEquals(o["dueAt"]!!.str, v.dueAt)
            assertEquals(o["paidAt"].strOrNull, v.paidAt, "paidAt ${v.id}")
        }
    }

    @Test
    fun `notifications, alert rules and settings`() {
        val n = ref["notifications"]!!.arr
        assertEquals(n.size, db.notifications.size)
        db.notifications.forEachIndexed { i, x ->
            assertEquals(n[i].obj["id"]!!.str, x.id)
            assertEquals(n[i].obj["createdAt"]!!.str, x.createdAt)
            assertEquals(n[i].obj["kind"]!!.str, x.kind.key)
        }
        val r = ref["alertRules"]!!.arr
        assertEquals(r.size, db.alertRules.size)
        db.alertRules.forEachIndexed { i, x ->
            val o = r[i].obj
            assertEquals(o["id"]!!.str, x.id)
            assertEquals(o["name"]!!.str, x.name)
            assertEquals(o["metric"]!!.str, x.metric.key)
            assertEquals(o["threshold"]!!.num, x.threshold)
            assertEquals(o["severity"]!!.str, x.severity.key)
            assertEquals(o["createdAt"]!!.str, x.createdAt)
        }
        val s = ref["settings"]!!.obj
        assertEquals(s["language"]!!.str, db.settings.language.code)
        assertEquals(s["discountRatePct"]!!.num, db.settings.discountRatePct)
        assertEquals(s["co2KgPerKwh"]!!.num, db.settings.co2KgPerKwh)
    }
}
