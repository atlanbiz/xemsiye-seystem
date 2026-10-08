package com.solarpulse.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Data model — docs/PLATFORM.md §1. JSON names are the Supabase snake_case column names, so the
 * same serializers are used for PostgREST rows and for the demo-mode JSON file on the device.
 * Enum `key`s are the wire values and also the suffix of the matching translation keys
 * (e.g. status_active, devType_inverter).
 */

@Serializable
enum class SiteStatus(val key: String) {
    @SerialName("active") ACTIVE("active"),
    @SerialName("idle") IDLE("idle"),
    @SerialName("offline") OFFLINE("offline"),
    @SerialName("maintenance") MAINTENANCE("maintenance"),
}

@Serializable
enum class SiteType(val key: String) {
    @SerialName("residential") RESIDENTIAL("residential"),
    @SerialName("commercial") COMMERCIAL("commercial"),
    @SerialName("industrial") INDUSTRIAL("industrial"),
    @SerialName("utility") UTILITY("utility"),
}

@Serializable
enum class DeviceType(val key: String) {
    @SerialName("inverter") INVERTER("inverter"),
    @SerialName("battery") BATTERY("battery"),
    @SerialName("panel") PANEL("panel"),
    @SerialName("meter") METER("meter"),
    @SerialName("sensor") SENSOR("sensor"),
}

@Serializable
enum class DeviceStatus(val key: String) {
    @SerialName("online") ONLINE("online"),
    @SerialName("warning") WARNING("warning"),
    @SerialName("offline") OFFLINE("offline"),
}

@Serializable
enum class TicketPriority(val key: String, val order: Int) {
    @SerialName("low") LOW("low", 3),
    @SerialName("medium") MEDIUM("medium", 2),
    @SerialName("high") HIGH("high", 1),
    @SerialName("critical") CRITICAL("critical", 0),
}

@Serializable
enum class TicketStatus(val key: String) {
    @SerialName("open") OPEN("open"),
    @SerialName("in_progress") IN_PROGRESS("in_progress"),
    @SerialName("resolved") RESOLVED("resolved"),
}

@Serializable
enum class InvoiceStatus(val key: String) {
    @SerialName("paid") PAID("paid"),
    @SerialName("pending") PENDING("pending"),
    @SerialName("overdue") OVERDUE("overdue"),
}

@Serializable
enum class NotificationKind(val key: String) {
    @SerialName("info") INFO("info"),
    @SerialName("success") SUCCESS("success"),
    @SerialName("warning") WARNING("warning"),
    @SerialName("danger") DANGER("danger"),
}

@Serializable
enum class ReportKind(val key: String) {
    @SerialName("energy") ENERGY("energy"),
    @SerialName("financial") FINANCIAL("financial"),
    @SerialName("devices") DEVICES("devices"),
    @SerialName("maintenance") MAINTENANCE("maintenance"),
    @SerialName("environment") ENVIRONMENT("environment"),
}

@Serializable
enum class Lang(val code: String, val rtl: Boolean, val nativeName: String) {
    @SerialName("ug") UG("ug", true, "ئۇيغۇرچە"),
    @SerialName("en") EN("en", false, "English"),
    @SerialName("ar") AR("ar", true, "العربية"),
    @SerialName("tr") TR("tr", false, "Türkçe");

    companion object {
        fun of(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

@Serializable
enum class ThemeMode(val key: String) {
    @SerialName("light") LIGHT("light"),
    @SerialName("dark") DARK("dark"),
    @SerialName("system") SYSTEM("system"),
}

@Serializable
enum class Currency(val code: String, val symbol: String) {
    @SerialName("USD") USD("USD", "$"),
    @SerialName("CNY") CNY("CNY", "CN¥"),
    @SerialName("EUR") EUR("EUR", "€"),
}

@Serializable
enum class AlertMetric(val key: String, val hasThreshold: Boolean) {
    @SerialName("site_yield_below") SITE_YIELD_BELOW("site_yield_below", true),
    @SerialName("site_offline") SITE_OFFLINE("site_offline", false),
    @SerialName("device_efficiency_below") DEVICE_EFFICIENCY_BELOW("device_efficiency_below", true),
    @SerialName("device_health_below") DEVICE_HEALTH_BELOW("device_health_below", true),
    @SerialName("device_offline_minutes") DEVICE_OFFLINE_MINUTES("device_offline_minutes", true),
    @SerialName("invoice_overdue_days") INVOICE_OVERDUE_DAYS("invoice_overdue_days", true),
}

@Serializable
enum class AlertSeverity(val key: String) {
    @SerialName("warning") WARNING("warning"),
    @SerialName("danger") DANGER("danger"),
}

@Serializable
enum class IntegrationVendor(val key: String) {
    @SerialName("solaredge") SOLAREDGE("solaredge"),
    @SerialName("fusionsolar") FUSIONSOLAR("fusionsolar"),
    @SerialName("webhook") WEBHOOK("webhook"),
}

@Serializable
enum class IntegrationStatus(val key: String) {
    @SerialName("pending") PENDING("pending"),
    @SerialName("ok") OK("ok"),
    @SerialName("error") ERROR("error"),
}

@Serializable
data class Site(
    val id: String,
    val name: String,
    val location: String,
    val type: SiteType,
    val status: SiteStatus,
    @SerialName("capacity_kw") val capacityKw: Double,
    @SerialName("battery_kwh") val batteryKwh: Double = 0.0,
    @SerialName("price_per_kwh") val pricePerKwh: Double = 0.1,
    val customer: String,
    @SerialName("install_date") val installDate: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** CAPEX in settings.currency. 0 / absent = contract default (see [withFinanceDefaults]). */
    @SerialName("system_cost") val systemCost: Double = 0.0,
    @SerialName("annual_opex") val annualOpex: Double = 0.0,
    @SerialName("degradation_pct") val degradationPct: Double = 0.5,
    @SerialName("tariff_escalation_pct") val tariffEscalationPct: Double = 2.0,
)

@Serializable
data class Device(
    val id: String,
    @SerialName("site_id") val siteId: String,
    val name: String,
    val type: DeviceType,
    val model: String,
    val serial: String = "",
    val status: DeviceStatus,
    val health: Double = 100.0,
    val efficiency: Double = 100.0,
    val firmware: String = "",
    @SerialName("installed_at") val installedAt: String,
    @SerialName("last_seen") val lastSeen: String,
)

@Serializable
data class Ticket(
    val id: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("device_id") val deviceId: String? = null,
    val title: String,
    val description: String = "",
    val priority: TicketPriority,
    val status: TicketStatus,
    val assignee: String = "",
    @SerialName("due_date") val dueDate: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Invoice(
    val id: String,
    val number: String,
    @SerialName("site_id") val siteId: String,
    val customer: String,
    val period: String,
    @SerialName("energy_kwh") val energyKwh: Double,
    val rate: Double,
    val amount: Double,
    val status: InvoiceStatus,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("due_at") val dueAt: String,
    @SerialName("paid_at") val paidAt: String? = null,
)

@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val kind: NotificationKind,
    val link: String? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SavedReport(
    val id: String,
    val kind: ReportKind,
    val title: String,
    val from: String,
    val to: String,
    @SerialName("site_ids") val siteIds: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Settings(
    val id: String = "settings",
    @SerialName("user_name") val userName: String = "ئالىم كېرىم",
    val email: String = "admin@solarpulse.app",
    val role: String = "admin",
    val company: String = "SolarPulse Energy",
    val language: Lang = Lang.UG,
    val theme: ThemeMode = ThemeMode.LIGHT,
    val currency: Currency = Currency.USD,
    val city: String = "ئۈرۈمچى",
    val lat: Double = 43.825,
    val lng: Double = 87.617,
    @SerialName("co2_kg_per_kwh") val co2KgPerKwh: Double = 0.7,
    @SerialName("tree_kg_per_year") val treeKgPerYear: Double = 21.8,
    @SerialName("car_tons_per_year") val carTonsPerYear: Double = 4.6,
    @SerialName("notify_email") val notifyEmail: Boolean = true,
    @SerialName("notify_push") val notifyPush: Boolean = true,
    @SerialName("notify_device_alerts") val notifyDeviceAlerts: Boolean = true,
    @SerialName("notify_billing") val notifyBilling: Boolean = true,
    @SerialName("notify_maintenance") val notifyMaintenance: Boolean = true,
    @SerialName("discount_rate_pct") val discountRatePct: Double = 6.0,
)

@Serializable
data class AlertRule(
    val id: String,
    val name: String,
    val metric: AlertMetric,
    val threshold: Double = 0.0,
    @SerialName("site_id") val siteId: String? = null,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val enabled: Boolean = true,
    @SerialName("last_triggered_at") val lastTriggeredAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Integration(
    val id: String,
    val vendor: IntegrationVendor,
    val name: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("external_id") val externalId: String = "",
    val config: Map<String, String> = emptyMap(),
    @SerialName("ingest_token") val ingestToken: String = "",
    val status: IntegrationStatus = IntegrationStatus.PENDING,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Real telemetry point (Supabase `readings`). */
@Serializable
data class Reading(
    @SerialName("site_id") val siteId: String,
    val ts: String,
    @SerialName("power_kw") val powerKw: Double,
    @SerialName("energy_kwh") val energyKwh: Double? = null,
)

/** Everything the app shows; mirrors the web's `DB`. */
@Serializable
data class Database(
    val sites: List<Site> = emptyList(),
    val devices: List<Device> = emptyList(),
    val tickets: List<Ticket> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val reports: List<SavedReport> = emptyList(),
    val settings: Settings = Settings(),
    @SerialName("alert_rules") val alertRules: List<AlertRule> = emptyList(),
    val integrations: List<Integration> = emptyList(),
) {
    fun site(id: String?): Site? = sites.firstOrNull { it.id == id }
    fun siteName(id: String?): String = site(id)?.name ?: "—"
}

/** Contract defaults for the ROI fields (PLATFORM.md §1, web `siteFinanceDefaults`). */
object FinanceDefaults {
    fun systemCost(capacityKw: Double): Double = jsRound(capacityKw * 900)
    fun annualOpex(systemCost: Double): Double = jsRound(systemCost * 0.015)
    const val DEGRADATION_PCT = 0.5
    const val TARIFF_ESCALATION_PCT = 2.0
    const val DISCOUNT_RATE_PCT = 6.0
}

/**
 * Fills ROI fields still at the column default 0 (web `withSiteDefaults`): a stored annualOpex
 * of 0 is only treated as missing when systemCost is missing too.
 */
fun Site.withFinanceDefaults(): Site {
    if (systemCost > 0) return this
    val cost = FinanceDefaults.systemCost(capacityKw)
    return copy(systemCost = cost, annualOpex = FinanceDefaults.annualOpex(cost))
}

/** JavaScript `Math.round` (half rounds towards +∞). */
fun jsRound(v: Double): Double = kotlin.math.floor(v + 0.5)
