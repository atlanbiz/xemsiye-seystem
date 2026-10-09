package com.solarpulse.core.alerts

import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days
import java.time.temporal.ChronoUnit

/**
 * Alert-rule evaluator — docs/PLATFORM.md §3.3, same semantics as src/lib/alerts.ts.
 * Demo mode runs it on the device (WorkManager + on resume); with Supabase the
 * `evaluate-alerts` Edge Function does it on a cron.
 */
object Alerts {
    const val COOLDOWN_MS = 6 * 3600_000L
    const val YIELD_CHECK_HOUR = 14

    /** Threshold unit per metric; null = the metric has no threshold. */
    fun unit(metric: AlertMetric): String? = when (metric) {
        AlertMetric.SITE_YIELD_BELOW -> "kWh/kWp"
        AlertMetric.SITE_OFFLINE -> null
        AlertMetric.DEVICE_EFFICIENCY_BELOW, AlertMetric.DEVICE_HEALTH_BELOW -> "%"
        AlertMetric.DEVICE_OFFLINE_MINUTES -> "min"
        AlertMetric.INVOICE_OVERDUE_DAYS -> "d"
    }

    data class Match(
        /** The site / device / invoice it names. */
        val label: String,
        /** Measured value in the metric's unit (null for site_offline). */
        val value: Double?,
        /** Web-style path: /sites/{id}, /devices?q={name}, /billing?open={id}. */
        val link: String,
    )

    data class Firing(val rule: AlertRule, val matches: List<Match>)

    fun inCooldown(rule: AlertRule, now: Long): Boolean {
        val last = Days.parseMillis(rule.lastTriggeredAt) ?: return false
        return now - last < COOLDOWN_MS
    }

    /** Entities currently breaching a rule (ignores enabled/cooldown). */
    fun matchRule(rule: AlertRule, db: Database, sim: Sim, now: Long = sim.now()): List<Match> {
        fun inScope(siteId: String) = rule.siteId == null || rule.siteId == siteId
        fun siteName(id: String) = db.sites.firstOrNull { it.id == id }?.name ?: id
        val todayDate = Days.localDate(now, sim.zone)
        val today = Days.key(todayDate)
        return when (rule.metric) {
            AlertMetric.SITE_YIELD_BELOW -> {
                if (Days.localHour(now, sim.zone).toInt() < YIELD_CHECK_HOUR) return emptyList()
                db.sites.filter { inScope(it.id) && it.capacityKw > 0 }
                    .map { it to sim.siteDayKwhCached(it, today) / it.capacityKw }
                    .filter { (_, v) -> v < rule.threshold }
                    .map { (s, v) -> Match(s.name, v, "/sites/${s.id}") }
            }
            AlertMetric.SITE_OFFLINE ->
                db.sites.filter { inScope(it.id) && it.status == SiteStatus.OFFLINE }
                    .map { Match(it.name, null, "/sites/${it.id}") }
            AlertMetric.DEVICE_EFFICIENCY_BELOW, AlertMetric.DEVICE_HEALTH_BELOW -> {
                val eff = rule.metric == AlertMetric.DEVICE_EFFICIENCY_BELOW
                db.devices.filter { inScope(it.siteId) && (if (eff) it.efficiency else it.health) < rule.threshold }
                    .map { d ->
                        Match("${d.name} · ${siteName(d.siteId)}", if (eff) d.efficiency else d.health, "/devices?q=${encodeURIComponent(d.name)}")
                    }
            }
            AlertMetric.DEVICE_OFFLINE_MINUTES ->
                db.devices.filter { inScope(it.siteId) && it.status == DeviceStatus.OFFLINE }
                    .map { d -> d to (now - (Days.parseMillis(d.lastSeen) ?: now)) / 60000.0 }
                    .filter { (_, v) -> v > rule.threshold }
                    .map { (d, v) -> Match("${d.name} · ${siteName(d.siteId)}", v, "/devices?q=${encodeURIComponent(d.name)}") }
            AlertMetric.INVOICE_OVERDUE_DAYS ->
                db.invoices.filter { inScope(it.siteId) && it.status == InvoiceStatus.OVERDUE }
                    .map { i -> i to ChronoUnit.DAYS.between(Days.parse(i.dueAt), todayDate).toDouble() }
                    .filter { (_, v) -> v > rule.threshold }
                    .map { (i, v) -> Match("${i.number} · ${i.customer}", v, "/billing?open=${i.id}") }
        }
    }

    /** Enabled rules outside their 6-hour cooldown that have at least one match. */
    fun evaluate(db: Database, sim: Sim, now: Long = sim.now()): List<Firing> =
        db.alertRules
            .filter { it.enabled && !inCooldown(it, now) }
            .map { Firing(it, matchRule(it, db, sim, now)) }
            .filter { it.matches.isNotEmpty() }

    /** Notification body: names up to three matches, then "+N". */
    fun describe(f: Firing, fmtValue: (Match) -> String): String {
        val shown = f.matches.take(3).map { m -> if (m.value == null) m.label else "${m.label} (${fmtValue(m)})" }
        val more = f.matches.size - shown.size
        return shown.joinToString("; ") + if (more > 0) " +$more" else ""
    }

    /** JavaScript `encodeURIComponent`. */
    fun encodeURIComponent(s: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xff).toChar()
            if (b >= 0 && unreserved.indexOf(c) >= 0) sb.append(c) else sb.append('%').append("%02X".format(b.toInt() and 0xff))
        }
        return sb.toString()
    }
}
