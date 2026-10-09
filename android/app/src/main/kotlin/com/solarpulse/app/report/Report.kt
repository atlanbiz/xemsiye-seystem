package com.solarpulse.app.report

import com.solarpulse.app.R
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.DeviceType
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.ReportKind
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.TicketPriority
import com.solarpulse.core.model.TicketStatus
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.sim.SimMath
import com.solarpulse.core.time.Days
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A generated report: preview table, summary tiles and an optional daily chart (web `Report`). */
data class Report(
    val kind: ReportKind,
    val from: String,
    val to: String,
    val title: String,
    val rangeLabel: String,
    val scopeLabel: String,
    val columns: List<String>,
    val display: List<List<String>>,
    val weights: List<Float>,
    val summary: List<Pair<String, String>>,
    /** (label, value) per day. */
    val chart: List<Pair<String, Double>> = emptyList(),
    val chartCaption: String? = null,
)

/** Builds the 5 report kinds exactly like src/pages/Reports.tsx `build()`. */
object ReportBuilder {
    fun build(
        s: (Int) -> String,
        kind: ReportKind,
        fromKey: String,
        toKey: String,
        siteIds: List<String>,
        db: Database,
        sim: Sim,
        fmt: Fmt,
    ): Report {
        val sites: List<Site> = if (siteIds.isEmpty()) db.sites else db.sites.filter { it.id in siteIds }
        val ids = sites.map { it.id }.toSet()
        val from = Days.parse(fromKey)
        val today = sim.today()
        val toRaw = Days.parse(toKey)
        val to: LocalDate = if (toRaw.isAfter(today)) today else toRaw
        val fromK = Days.key(from)
        val toK = Days.key(to)
        val title = s(kindLabel(kind))
        val range = "${fmt.date(fromKey)} — ${fmt.date(toKey)}"
        val scope = if (siteIds.isEmpty()) s(R.string.rep_allSites) else "${siteIds.size} ${s(R.string.rep_sites)}"
        fun base(columns: List<String>, display: List<List<String>>, weights: List<Float>, summary: List<Pair<String, String>>) =
            Report(kind, fromKey, toKey, title, range, scope, columns, display, weights, summary)

        return when (kind) {
            ReportKind.ENERGY -> {
                val days = maxOf(1L, ChronoUnit.DAYS.between(from, to) + 1)
                val data = sites.map { x ->
                    val kwh = sim.totalKwh(listOf(x), from, to)
                    Triple(x, kwh, kwh * x.pricePerKwh)
                }
                val total = data.sumOf { it.second }
                base(
                    listOf(s(R.string.common_site), s(R.string.sites_capacity), s(R.string.an_production_short), s(R.string.sites_yield), s(R.string.ov_totalRevenue)),
                    data.map { (x, kwh, rev) ->
                        listOf(x.name, fmt.power(x.capacityKw), fmt.energy(kwh), "${fmt.num(kwh / days / x.capacityKw, 2)} kWh/kWp", fmt.money(rev))
                    },
                    listOf(2.2f, 1f, 1.1f, 1.1f, 1f),
                    listOf(
                        s(R.string.an_production) to fmt.energy(total),
                        s(R.string.ov_totalRevenue) to fmt.money(data.sumOf { it.third }),
                        s(R.string.rep_sites) to sites.size.toString(),
                    ),
                ).copy(
                    chart = sim.dailySeries(sites, from, to).map { fmt.dateShort(it.date) to it.kwh },
                    chartCaption = s(R.string.an_production_short),
                )
            }
            ReportKind.FINANCIAL -> {
                val inv = db.invoices.filter { it.siteId in ids && it.issuedAt >= fromK && it.issuedAt <= toK }
                data class FinRow(val site: Site, val n: Int, val billed: Double, val paid: Double)
                val by = sites.map { x ->
                    val l = inv.filter { it.siteId == x.id }
                    FinRow(x, l.size, l.sumOf { it.amount }, l.filter { it.status == InvoiceStatus.PAID }.sumOf { it.amount })
                }.filter { it.n > 0 }
                val billed = by.sumOf { it.billed }
                val paid = by.sumOf { it.paid }
                base(
                    listOf(s(R.string.common_site), s(R.string.bill_customer), s(R.string.bill_invoice), s(R.string.bill_totalBilled), s(R.string.bill_paid), s(R.string.bill_pending)),
                    by.map { r -> listOf(r.site.name, r.site.customer, r.n.toString(), fmt.money2(r.billed), fmt.money2(r.paid), fmt.money2(r.billed - r.paid)) },
                    listOf(1.8f, 1.6f, 0.7f, 1f, 1f, 1f),
                    listOf(
                        s(R.string.bill_totalBilled) to fmt.money(billed),
                        s(R.string.bill_paid) to fmt.money(paid),
                        s(R.string.bill_pending) to fmt.money(billed - paid),
                    ),
                )
            }
            ReportKind.DEVICES -> {
                val devs = db.devices.filter { it.siteId in ids }
                val avg = if (devs.isEmpty()) 0.0 else devs.sumOf { it.health } / devs.size
                base(
                    listOf(s(R.string.common_name), s(R.string.common_site), s(R.string.common_type), s(R.string.dev_model), s(R.string.common_status), s(R.string.dev_health), s(R.string.dev_efficiency), s(R.string.dev_firmware)),
                    devs.map { d ->
                        listOf(d.name, db.siteName(d.siteId), s(deviceTypeLabel(d.type)), d.model, s(deviceStatusLabel(d.status)), "${d.health.toInt()}%", fmt.pct(d.efficiency), d.firmware)
                    },
                    listOf(1f, 1.6f, 1f, 1.6f, 0.9f, 0.7f, 0.8f, 0.8f),
                    listOf(
                        s(R.string.dev_title) to devs.size.toString(),
                        s(R.string.status_online) to devs.count { it.status == DeviceStatus.ONLINE }.toString(),
                        s(R.string.status_offline) to devs.count { it.status == DeviceStatus.OFFLINE }.toString(),
                        s(R.string.dev_health) to fmt.pct(avg),
                    ),
                )
            }
            ReportKind.MAINTENANCE -> {
                val tk = db.tickets.filter { x ->
                    val created = x.createdAt.take(10)
                    x.siteId in ids && ((created >= fromK && created <= toK) || (x.dueDate >= fromK && x.dueDate <= toK))
                }
                base(
                    listOf(s(R.string.mt_ticketTitle), s(R.string.common_site), s(R.string.mt_priority), s(R.string.common_status), s(R.string.mt_assignee), s(R.string.mt_due)),
                    tk.map { x ->
                        listOf(x.title, db.siteName(x.siteId), s(priorityLabel(x.priority)), s(ticketStatusLabel(x.status)), x.assignee.ifBlank { "—" }, fmt.date(x.dueDate))
                    },
                    listOf(2.2f, 1.6f, 0.8f, 0.9f, 1.1f, 1f),
                    listOf(
                        s(R.string.common_total) to tk.size.toString(),
                        s(R.string.status_resolved) to tk.count { it.status == TicketStatus.RESOLVED }.toString(),
                        s(R.string.status_open) to tk.count { it.status != TicketStatus.RESOLVED }.toString(),
                    ),
                )
            }
            ReportKind.ENVIRONMENT -> {
                val st = db.settings
                data class EnvRow(val site: Site, val kwh: Double, val co2: Double)
                val data = sites.map { x ->
                    val kwh = sim.totalKwh(listOf(x), from, to)
                    EnvRow(x, kwh, SimMath.co2Tons(kwh, st.co2KgPerKwh))
                }
                val co2 = data.sumOf { it.co2 }
                base(
                    listOf(s(R.string.common_site), s(R.string.an_production_short) + " (kWh)", "CO₂ (t)", s(R.string.ov_trees), s(R.string.ov_cars)),
                    data.map { r ->
                        listOf(r.site.name, fmt.energy(r.kwh), fmt.num(r.co2, 2), fmt.num(r.co2 * 1000 / st.treeKgPerYear), fmt.num(r.co2 / st.carTonsPerYear, 1))
                    },
                    listOf(2.2f, 1.2f, 0.9f, 1.1f, 1.1f),
                    listOf(
                        s(R.string.ov_co2Offset) to "${fmt.num(co2, 1)} ${s(R.string.common_tons)}",
                        s(R.string.ov_trees) to fmt.num(co2 * 1000 / st.treeKgPerYear),
                        s(R.string.ov_cars) to fmt.num(co2 / st.carTonsPerYear, 1),
                    ),
                ).copy(
                    chart = sim.dailySeries(sites, from, to).map { fmt.dateShort(it.date) to SimMath.co2Tons(it.kwh, st.co2KgPerKwh) },
                    chartCaption = "CO₂ (t)",
                )
            }
        }
    }

    fun kindLabel(kind: ReportKind): Int = when (kind) {
        ReportKind.ENERGY -> R.string.rep_energy
        ReportKind.FINANCIAL -> R.string.rep_financial
        ReportKind.DEVICES -> R.string.rep_devices
        ReportKind.MAINTENANCE -> R.string.rep_maintenance
        ReportKind.ENVIRONMENT -> R.string.rep_environment
    }

    private fun deviceTypeLabel(t: DeviceType) = when (t) {
        DeviceType.INVERTER -> R.string.devType_inverter
        DeviceType.BATTERY -> R.string.devType_battery
        DeviceType.PANEL -> R.string.devType_panel
        DeviceType.METER -> R.string.devType_meter
        DeviceType.SENSOR -> R.string.devType_sensor
    }

    private fun deviceStatusLabel(t: DeviceStatus) = when (t) {
        DeviceStatus.ONLINE -> R.string.status_online
        DeviceStatus.WARNING -> R.string.status_warning
        DeviceStatus.OFFLINE -> R.string.status_offline
    }

    private fun priorityLabel(p: TicketPriority) = when (p) {
        TicketPriority.LOW -> R.string.prio_low
        TicketPriority.MEDIUM -> R.string.prio_medium
        TicketPriority.HIGH -> R.string.prio_high
        TicketPriority.CRITICAL -> R.string.prio_critical
    }

    private fun ticketStatusLabel(s: TicketStatus) = when (s) {
        TicketStatus.OPEN -> R.string.status_open
        TicketStatus.IN_PROGRESS -> R.string.status_in_progress
        TicketStatus.RESOLVED -> R.string.status_resolved
    }
}
