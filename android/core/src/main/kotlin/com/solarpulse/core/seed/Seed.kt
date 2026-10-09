package com.solarpulse.core.seed

import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.AlertSeverity
import com.solarpulse.core.model.AppNotification
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Device
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.DeviceType
import com.solarpulse.core.model.FinanceDefaults
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Settings
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.model.TicketPriority
import com.solarpulse.core.model.TicketStatus
import com.solarpulse.core.model.jsRound
import com.solarpulse.core.model.withFinanceDefaults
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.sim.SimMath.rand
import com.solarpulse.core.time.Days
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor

/** Demo data — an exact port of src/lib/seed.ts (16 sites, same names/coordinates). */
class Seed(private val sim: Sim) {

    private data class SiteDef(
        val name: String, val location: String, val type: SiteType,
        val cap: Double, val bat: Double, val lat: Double, val lng: Double,
    )

    private val siteDefs = listOf(
        SiteDef("تۇرپان قۇياش مەيدانى", "تۇرپان", SiteType.UTILITY, 820.0, 600.0, 42.95, 89.18),
        SiteDef("ئۈرۈمچى سودا مەركىزى", "ئۈرۈمچى", SiteType.COMMERCIAL, 183.0, 120.0, 43.82, 87.61),
        SiteDef("قەشقەر كونا شەھەر ئۆيلىرى", "قەشقەر", SiteType.RESIDENTIAL, 12.4, 15.0, 39.47, 75.99),
        SiteDef("خوتەن ئاشلىق زاۋۇتى", "خوتەن", SiteType.INDUSTRIAL, 410.0, 300.0, 37.11, 79.92),
        SiteDef("غۇلجا مەكتىپى", "غۇلجا", SiteType.COMMERCIAL, 96.0, 60.0, 43.92, 81.32),
        SiteDef("ئاقسۇ يېزا ئىگىلىك مەيدانى", "ئاقسۇ", SiteType.INDUSTRIAL, 265.0, 180.0, 41.17, 80.26),
        SiteDef("قۇمۇل شامال-قۇياش بازىسى", "قۇمۇل", SiteType.UTILITY, 640.0, 450.0, 42.82, 93.51),
        SiteDef("كورلا ئىشخانا بىناسى", "كورلا", SiteType.COMMERCIAL, 65.0, 40.0, 41.76, 86.15),
        SiteDef("ئاتۇش ئائىلە ئولتۇراق رايونى", "ئاتۇش", SiteType.RESIDENTIAL, 38.0, 30.0, 39.71, 76.17),
        SiteDef("چۆچەك دوختۇرخانىسى", "چۆچەك", SiteType.COMMERCIAL, 120.0, 150.0, 46.75, 82.98),
        SiteDef("ئالتاي تاغ ئارامگاھى", "ئالتاي", SiteType.RESIDENTIAL, 18.0, 20.0, 47.84, 88.14),
        SiteDef("يەكەن توقۇمىچىلىق زاۋۇتى", "يەكەن", SiteType.INDUSTRIAL, 350.0, 220.0, 38.42, 77.24),
        SiteDef("قاراماي ئامبار مەركىزى", "قاراماي", SiteType.INDUSTRIAL, 290.0, 160.0, 45.58, 84.89),
        SiteDef("بۆرتالا مېھمانخانىسى", "بۆرتالا", SiteType.COMMERCIAL, 74.0, 50.0, 44.9, 82.07),
        SiteDef("كۇچا ئۈزۈمزارلىقى", "كۇچا", SiteType.RESIDENTIAL, 24.0, 20.0, 41.72, 82.96),
        SiteDef("شىخەنزە لوگىستىكا پاركى", "شىخەنزە", SiteType.COMMERCIAL, 210.0, 140.0, 44.3, 86.04),
    )

    private val statuses = listOf(
        SiteStatus.ACTIVE, SiteStatus.ACTIVE, SiteStatus.ACTIVE, SiteStatus.ACTIVE, SiteStatus.IDLE,
        SiteStatus.ACTIVE, SiteStatus.ACTIVE, SiteStatus.ACTIVE, SiteStatus.IDLE, SiteStatus.ACTIVE,
        SiteStatus.OFFLINE, SiteStatus.ACTIVE, SiteStatus.IDLE, SiteStatus.ACTIVE, SiteStatus.MAINTENANCE,
        SiteStatus.ACTIVE,
    )
    private val customers = listOf(
        "تۇرپان ئېنېرگىيە شىركىتى", "تەڭرىتاغ سودا گۇرۇھى", "ئابدۇللا ئائىلىسى", "خوتەن ئاشلىق شىركىتى",
        "غۇلجا مائارىپ ئىدارىسى", "ئاقسۇ دېھقانچىلىق كوپراتىپى", "قۇمۇل توك تورى", "كورلا نېفىت شىركىتى",
        "ئاتۇش مۈلۈك باشقۇرۇش", "چۆچەك ساغلاملىق مەركىزى", "نۇرگۈل خانىم", "يەكەن توقۇمىچىلىق",
        "قاراماي ئامبار شىركىتى", "بۆرتالا ساياھەت", "كۇچا ئۈزۈمچىلىك", "شىخەنزە لوگىستىكا",
    )
    private val techs = listOf("ئەركىن تۇرسۇن", "مۇختەر ئەھمەت", "دىلنۇر ئابلىز", "پەرھات قادىر", "گۈلنار ھەسەن")

    private val models: Map<DeviceType, List<String>> = mapOf(
        DeviceType.INVERTER to listOf("Huawei SUN2000-100KTL", "SMA Sunny Tripower 25", "Sungrow SG110CX", "Fronius Symo 20"),
        DeviceType.BATTERY to listOf("BYD Battery-Box HVM", "Tesla Powerwall 2", "CATL EnerOne", "LG RESU 16H"),
        DeviceType.PANEL to listOf("LONGi Hi-MO 6 (Array)", "JinkoSolar Tiger Neo (Array)", "Trina Vertex S+ (Array)"),
        DeviceType.METER to listOf("Schneider PM5560", "Eastron SDM630"),
        DeviceType.SENSOR to listOf("Kipp & Zonen SMP10", "Davis Vantage Pro2"),
    )

    private fun pad2(n: Int) = n.toString().padStart(2, '0')
    private fun now() = sim.now()
    private fun today(): LocalDate = sim.today()

    /** `addDays(new Date(), n).toISOString()` — shifts the local date-time, keeps the time of day. */
    private fun isoDaysFromNow(n: Long): String =
        Days.iso(Instant.ofEpochMilli(now()).atZone(sim.zone).plusDays(n).toInstant().toEpochMilli())

    fun buildSites(): List<Site> = siteDefs.mapIndexed { i, d ->
        val cost = FinanceDefaults.systemCost(d.cap)
        Site(
            id = "site-${pad2(i + 1)}",
            name = d.name,
            location = d.location,
            type = d.type,
            status = statuses[i],
            capacityKw = d.cap,
            batteryKwh = d.bat,
            pricePerKwh = when (d.type) {
                SiteType.RESIDENTIAL -> 0.14
                SiteType.UTILITY -> 0.08
                SiteType.INDUSTRIAL -> 0.1
                SiteType.COMMERCIAL -> 0.12
            },
            customer = customers[i],
            installDate = Days.key(today().minusDays((420 + floor(rand("inst$i") * 900)).toLong())),
            lat = d.lat,
            lng = d.lng,
            systemCost = cost,
            annualOpex = FinanceDefaults.annualOpex(cost),
            degradationPct = FinanceDefaults.DEGRADATION_PCT,
            tariffEscalationPct = FinanceDefaults.TARIFF_ESCALATION_PCT,
        )
    }

    fun buildDevices(sites: List<Site>): List<Device> {
        val out = ArrayList<Device>()
        var n = 1
        for (s in sites) {
            val inverters = if (s.capacityKw > 300) 3 else if (s.capacityKw > 80) 2 else 1
            val types = ArrayList<DeviceType>()
            repeat(inverters) { types.add(DeviceType.INVERTER) }
            types.add(DeviceType.PANEL)
            types.add(DeviceType.METER)
            if (s.batteryKwh > 0) types.add(DeviceType.BATTERY)
            if (s.capacityKw > 100) types.add(DeviceType.SENSOR)
            types.forEachIndexed { idx, type ->
                val r = rand(s.id + type.key + idx)
                var status = if (r < 0.08) DeviceStatus.WARNING else DeviceStatus.ONLINE
                if (s.status == SiteStatus.OFFLINE) status = DeviceStatus.OFFLINE
                if (s.status == SiteStatus.MAINTENANCE && type == DeviceType.INVERTER) status = DeviceStatus.WARNING
                val list = models.getValue(type)
                val label = when (type) {
                    DeviceType.INVERTER -> "INV"
                    DeviceType.BATTERY -> "BAT"
                    DeviceType.PANEL -> "PV"
                    DeviceType.METER -> "MTR"
                    DeviceType.SENSOR -> "SNS"
                }
                val lastSeenOffset = if (status == DeviceStatus.OFFLINE) 3600_000 * (6 + r * 40) else r * 120_000
                out.add(
                    Device(
                        id = "dev-${n.toString().padStart(3, '0')}",
                        siteId = s.id,
                        name = "$label-${s.id.takeLast(2)}-${idx + 1}",
                        type = type,
                        model = list[floor(r * list.size).toInt()],
                        serial = "SN${hash6(s.id + idx + type.key)}",
                        status = status,
                        health = jsRound(if (status == DeviceStatus.WARNING) 70 + r * 12 else 88 + r * 12),
                        efficiency = jsRound((if (type == DeviceType.INVERTER) 96.2 + r * 2.4 else 90 + r * 8) * 10) / 10,
                        firmware = "v${2 + floor(r * 3).toInt()}.${floor(r * 9).toInt()}.${floor(r * 20).toInt()}",
                        installedAt = s.installDate,
                        // new Date(Date.now() - x) truncates fractional milliseconds
                        lastSeen = Days.iso((now() - lastSeenOffset).toLong()),
                    ),
                )
                n++
            }
        }
        return out
    }

    private fun hash6(s: String): String =
        floor(rand(s) * 0xffffff).toLong().toString(16).uppercase().padStart(6, '0') +
            floor(rand(s + "x") * 9999).toLong()

    private data class TicketDef(
        val site: Int, val title: String, val description: String,
        val priority: TicketPriority, val status: TicketStatus, val due: Long,
    )

    fun buildTickets(sites: List<Site>, devices: List<Device>): List<Ticket> {
        val defs = listOf(
            TicketDef(10, "ئالتاي ئىستانسىسى تورسىز", "ئالاقە ئۈسكۈنىسى جاۋاب قايتۇرمايۋاتىدۇ، نەق مەيداندا تەكشۈرۈش كېرەك.", TicketPriority.CRITICAL, TicketStatus.OPEN, 1),
            TicketDef(14, "كۇچا ئىنۋېرتورىنى ئالماشتۇرۇش", "ئىنۋېرتور قىزىپ كېتىش خاتالىقى بەردى، يېڭىسىغا ئالماشتۇرۇلىدۇ.", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 2),
            TicketDef(0, "تاختايلارنى تازىلاش", "قۇم-توپا سەۋەبىدىن ئۈنۈم 6% تۆۋەنلىدى.", TicketPriority.MEDIUM, TicketStatus.OPEN, 4),
            TicketDef(3, "باتارېيە BMS يۇمشاق دېتالىنى يېڭىلاش", "BMS v3.2 گە يېڭىلاش.", TicketPriority.LOW, TicketStatus.OPEN, 9),
            TicketDef(6, "پەسىللىك تەكشۈرۈش", "ئېلېكتر ئۇلىنىشى، يەرلەشتۈرۈش ۋە كابېللارنى تەكشۈرۈش.", TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 3),
            TicketDef(11, "توك ئۆلچىگۈچ سانلىق مەلۇماتى كەم", "سائەت 14:00-16:00 ئارىلىقىدا ئۆلچەش كەم.", TicketPriority.MEDIUM, TicketStatus.OPEN, 6),
            TicketDef(1, "ئوت ئۆچۈرگۈچ ۋە بىخەتەرلىك تەكشۈرۈشى", "يىللىق بىخەتەرلىك تەكشۈرۈشى.", TicketPriority.LOW, TicketStatus.RESOLVED, -5),
            TicketDef(12, "ئىنۋېرتور ئالاقە ئۈزۈلۈشى", "RS485 ئالاقىسى ۋاقتى-ۋاقتى بىلەن ئۈزۈلىدۇ.", TicketPriority.HIGH, TicketStatus.OPEN, 2),
            TicketDef(8, "سايە چۈشۈش مەسىلىسى", "يېڭى بىنا سايىسى سەۋەبىدىن ئەتىگەنلىك ھاسىلات تۆۋەن.", TicketPriority.LOW, TicketStatus.RESOLVED, -12),
            TicketDef(4, "تاختاي يېرىلىشى", "مۆلدۈردىن كېيىن 3 تاختايدا يېرىق بايقالدى.", TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 5),
            TicketDef(9, "زاپاس توك سىستېمىسىنى سىناش", "دوختۇرخانا ئۈچۈن ئايلىق زاپاس توك سىنىقى.", TicketPriority.CRITICAL, TicketStatus.OPEN, 0),
            TicketDef(2, "تاختايلارنى تازىلاش", "ئايلىق تازىلاش.", TicketPriority.LOW, TicketStatus.RESOLVED, -20),
            TicketDef(6, "ھاۋا رايى سېنزورىنى تەڭشەش", "نۇرلىنىش سېنزورى %8 يۇقىرى كۆرسىتىۋاتىدۇ.", TicketPriority.MEDIUM, TicketStatus.OPEN, 12),
            TicketDef(13, "يۇمشاق دېتال يېڭىلاش", "ئىنۋېرتور يۇمشاق دېتالىنى ئەڭ يېڭى نەشرىگە يېڭىلاش.", TicketPriority.LOW, TicketStatus.OPEN, 15),
        )
        return defs.mapIndexed { i, d ->
            val site = sites[d.site]
            val dev = devices.firstOrNull { it.siteId == site.id && it.type == DeviceType.INVERTER }
            Ticket(
                id = "tkt-${pad2(i + 1)}",
                siteId = site.id,
                deviceId = if (i % 3 == 2) null else dev?.id,
                title = d.title,
                description = d.description,
                priority = d.priority,
                status = d.status,
                assignee = techs[i % techs.size],
                dueDate = Days.key(today().plusDays(d.due)),
                createdAt = isoDaysFromNow(-abs(d.due) - 3 - (i % 4)),
            )
        }
    }

    /** kWh of a site in a month, up to today. */
    fun monthEnergy(site: Site, month: String): Double {
        val ym = Days.parseMonth(month)
        val from = ym.atDay(1)
        val last = ym.atEndOfMonth()
        val to = if (last.isAfter(today())) today() else last
        return Days.between(from, to).sumOf { sim.siteDayKwhCached(site, Days.key(it)) }
    }

    fun buildInvoice(site: Site, month: String, seq: Int): Invoice {
        val issued = Days.parseMonth(month).plusMonths(1).atDay(1)
        val energy = jsRound(monthEnergy(site, month))
        return Invoice(
            id = "inv-${site.id}-$month",
            number = "INV-${month.replace("-", "")}-${seq.toString().padStart(3, '0')}",
            siteId = site.id,
            customer = site.customer,
            period = month,
            energyKwh = energy,
            rate = site.pricePerKwh,
            amount = jsRound(energy * site.pricePerKwh * 100) / 100,
            status = InvoiceStatus.PENDING,
            issuedAt = Days.key(issued),
            dueAt = Days.key(issued.plusDays(20)),
            paidAt = null,
        )
    }

    fun buildInvoices(sites: List<Site>): List<Invoice> {
        val out = ArrayList<Invoice>()
        val now = now()
        val thisMonth = YearMonth.from(today())
        for (k in 6 downTo 1) {
            val month = thisMonth.minusMonths(k.toLong()).toString()
            sites.forEachIndexed { i, s ->
                var inv = buildInvoice(s, month, i + 1)
                if (inv.energyKwh == 0.0) return@forEachIndexed
                val due = Days.utcMidnightMillis(inv.dueAt) // JS new Date('YYYY-MM-DD') = UTC midnight
                val r = rand(inv.id)
                inv = if (k >= 2 || r < 0.5) {
                    if (r < 0.06 && k <= 3) {
                        inv.copy(status = InvoiceStatus.OVERDUE)
                    } else {
                        val paid = Instant.ofEpochMilli(Days.utcMidnightMillis(inv.issuedAt)).atZone(sim.zone)
                            .plusDays(floor(r * 18).toLong()).toLocalDate()
                        inv.copy(status = InvoiceStatus.PAID, paidAt = Days.key(paid))
                    }
                } else {
                    inv.copy(status = if (due < now) InvoiceStatus.OVERDUE else InvoiceStatus.PENDING)
                }
                out.add(inv)
            }
        }
        return out
    }

    fun buildNotifications(): List<AppNotification> {
        fun t(mins: Int) = Days.iso(now() - mins * 60000L)
        return listOf(
            AppNotification("ntf-1", "ئالتاي ئىستانسىسى تورسىز", "ئالتاي تاغ ئارامگاھى 6 سائەتتىن بۇيان سانلىق مەلۇمات ئەۋەتمىدى.", NotificationKind.DANGER, "/sites/site-11", false, t(25)),
            AppNotification("ntf-2", "ئىنۋېرتور ئاگاھلاندۇرۇشى", "كۇچا ئۈزۈمزارلىقىدىكى ئىنۋېرتور تېمپېراتۇرىسى يۇقىرى.", NotificationKind.WARNING, "/devices", false, t(90)),
            AppNotification("ntf-3", "ھېسابات تۆلەندى", "تەڭرىتاغ سودا گۇرۇھى ئالدىنقى ئاينىڭ ھېساباتىنى تۆلىدى.", NotificationKind.SUCCESS, "/billing", false, t(240)),
            AppNotification("ntf-4", "يېڭى ئاسراش ۋەزىپىسى", "چۆچەك دوختۇرخانىسى زاپاس توك سىنىقى بۈگۈن.", NotificationKind.INFO, "/maintenance", true, t(600)),
            AppNotification("ntf-5", "ئايلىق دوكلات تەييار", "ئالدىنقى ئاينىڭ ئېنېرگىيە دوكلاتىنى چۈشۈرەلەيسىز.", NotificationKind.INFO, "/reports", true, t(1440)),
        )
    }

    fun buildAlertRules(): List<AlertRule> {
        val createdAt = isoDaysFromNow(-30)
        data class D(val name: String, val metric: AlertMetric, val threshold: Double, val severity: AlertSeverity)
        val defs = listOf(
            D("ئىستانسا تورسىز", AlertMetric.SITE_OFFLINE, 0.0, AlertSeverity.DANGER),
            D("ھاسىلات تۆۋەن", AlertMetric.SITE_YIELD_BELOW, 1.5, AlertSeverity.WARNING),
            D("ئىنۋېرتور ئۈنۈمى تۆۋەن", AlertMetric.DEVICE_EFFICIENCY_BELOW, 92.0, AlertSeverity.WARNING),
            D("ئۈسكۈنە سالامەتلىكى ناچار", AlertMetric.DEVICE_HEALTH_BELOW, 72.0, AlertSeverity.WARNING),
            D("ئۈسكۈنە 1 سائەتتىن ئارتۇق تورسىز", AlertMetric.DEVICE_OFFLINE_MINUTES, 60.0, AlertSeverity.DANGER),
            D("تالون 10 كۈندىن ئارتۇق كېچىكتى", AlertMetric.INVOICE_OVERDUE_DAYS, 10.0, AlertSeverity.WARNING),
        )
        return defs.mapIndexed { i, d ->
            AlertRule(
                id = "rule-${pad2(i + 1)}",
                name = d.name,
                metric = d.metric,
                threshold = d.threshold,
                siteId = null,
                severity = d.severity,
                enabled = true,
                lastTriggeredAt = null,
                createdAt = createdAt,
            )
        }
    }

    fun build(): Database {
        val sites = buildSites()
        val devices = buildDevices(sites)
        return Database(
            sites = sites,
            devices = devices,
            tickets = buildTickets(sites, devices),
            invoices = buildInvoices(sites),
            notifications = buildNotifications(),
            reports = emptyList(),
            settings = Settings(),
            alertRules = buildAlertRules(),
            integrations = emptyList(),
        )
    }
}

/**
 * Upgrades data saved by older versions (web `normalizeDB`): ROI defaults on every site and the
 * default alert rules when the saved data predates them ([hasAlertRules] = key present in JSON).
 */
fun Database.normalized(hasAlertRules: Boolean, defaultRules: () -> List<AlertRule>): Database = copy(
    sites = sites.map { it.withFinanceDefaults() },
    alertRules = if (hasAlertRules) alertRules else defaultRules(),
)
