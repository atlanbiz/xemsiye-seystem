package com.solarpulse.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.solarpulse.app.R
import com.solarpulse.app.data.WeatherKind
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertSeverity
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.DeviceType
import com.solarpulse.core.model.IntegrationStatus
import com.solarpulse.core.model.IntegrationVendor
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.ReportKind
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.model.ThemeMode
import com.solarpulse.core.model.TicketPriority
import com.solarpulse.core.model.TicketStatus

/* Enum → translation key / colour, mirroring the web's `t(`status.${x}`)` lookups. */

@get:StringRes val SiteStatus.label: Int
    get() = when (this) {
        SiteStatus.ACTIVE -> R.string.status_active
        SiteStatus.IDLE -> R.string.status_idle
        SiteStatus.OFFLINE -> R.string.status_offline
        SiteStatus.MAINTENANCE -> R.string.status_maintenance
    }

/** Map pin / badge colours (PLATFORM.md §3.1). */
val SiteStatus.color: Color
    get() = when (this) {
        SiteStatus.ACTIVE -> Brand.Green
        SiteStatus.IDLE -> Brand.Amber
        SiteStatus.OFFLINE -> Brand.Red
        SiteStatus.MAINTENANCE -> Brand.Violet
    }

@get:StringRes val SiteType.label: Int
    get() = when (this) {
        SiteType.RESIDENTIAL -> R.string.siteType_residential
        SiteType.COMMERCIAL -> R.string.siteType_commercial
        SiteType.INDUSTRIAL -> R.string.siteType_industrial
        SiteType.UTILITY -> R.string.siteType_utility
    }

@get:StringRes val DeviceType.label: Int
    get() = when (this) {
        DeviceType.INVERTER -> R.string.devType_inverter
        DeviceType.BATTERY -> R.string.devType_battery
        DeviceType.PANEL -> R.string.devType_panel
        DeviceType.METER -> R.string.devType_meter
        DeviceType.SENSOR -> R.string.devType_sensor
    }

val DeviceType.icon: ImageVector
    get() = when (this) {
        DeviceType.INVERTER -> Icons.Rounded.Memory
        DeviceType.BATTERY -> Icons.Rounded.BatteryChargingFull
        DeviceType.PANEL -> Icons.Rounded.GridView
        DeviceType.METER -> Icons.Rounded.Speed
        DeviceType.SENSOR -> Icons.Rounded.Sensors
    }

@get:StringRes val DeviceStatus.label: Int
    get() = when (this) {
        DeviceStatus.ONLINE -> R.string.status_online
        DeviceStatus.WARNING -> R.string.status_warning
        DeviceStatus.OFFLINE -> R.string.status_offline
    }

val DeviceStatus.color: Color
    get() = when (this) {
        DeviceStatus.ONLINE -> Brand.Green
        DeviceStatus.WARNING -> Brand.Amber
        DeviceStatus.OFFLINE -> Brand.Red
    }

@get:StringRes val TicketStatus.label: Int
    get() = when (this) {
        TicketStatus.OPEN -> R.string.status_open
        TicketStatus.IN_PROGRESS -> R.string.status_in_progress
        TicketStatus.RESOLVED -> R.string.status_resolved
    }

val TicketStatus.color: Color
    get() = when (this) {
        TicketStatus.OPEN -> Brand.Blue500
        TicketStatus.IN_PROGRESS -> Brand.Violet
        TicketStatus.RESOLVED -> Brand.Green
    }

val TicketStatus.next: TicketStatus?
    get() = when (this) {
        TicketStatus.OPEN -> TicketStatus.IN_PROGRESS
        TicketStatus.IN_PROGRESS -> TicketStatus.RESOLVED
        TicketStatus.RESOLVED -> null
    }

@get:StringRes val TicketPriority.label: Int
    get() = when (this) {
        TicketPriority.LOW -> R.string.prio_low
        TicketPriority.MEDIUM -> R.string.prio_medium
        TicketPriority.HIGH -> R.string.prio_high
        TicketPriority.CRITICAL -> R.string.prio_critical
    }

val TicketPriority.color: Color
    get() = when (this) {
        TicketPriority.CRITICAL -> Brand.Red
        TicketPriority.HIGH -> Brand.Amber
        TicketPriority.MEDIUM -> Brand.Blue500
        TicketPriority.LOW -> Brand.Slate500
    }

@get:StringRes val InvoiceStatus.label: Int
    get() = when (this) {
        InvoiceStatus.PAID -> R.string.status_paid
        InvoiceStatus.PENDING -> R.string.status_pending
        InvoiceStatus.OVERDUE -> R.string.status_overdue
    }

val InvoiceStatus.color: Color
    get() = when (this) {
        InvoiceStatus.PAID -> Brand.Green
        InvoiceStatus.PENDING -> Brand.Amber
        InvoiceStatus.OVERDUE -> Brand.Red
    }

@get:StringRes val ReportKind.label: Int
    get() = when (this) {
        ReportKind.ENERGY -> R.string.rep_energy
        ReportKind.FINANCIAL -> R.string.rep_financial
        ReportKind.DEVICES -> R.string.rep_devices
        ReportKind.MAINTENANCE -> R.string.rep_maintenance
        ReportKind.ENVIRONMENT -> R.string.rep_environment
    }

@get:StringRes val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.LIGHT -> R.string.set_theme_light
        ThemeMode.DARK -> R.string.set_theme_dark
        ThemeMode.SYSTEM -> R.string.set_theme_system
    }

@get:StringRes val AlertMetric.label: Int
    get() = when (this) {
        AlertMetric.SITE_YIELD_BELOW -> R.string.alerts_m_site_yield_below
        AlertMetric.SITE_OFFLINE -> R.string.alerts_m_site_offline
        AlertMetric.DEVICE_EFFICIENCY_BELOW -> R.string.alerts_m_device_efficiency_below
        AlertMetric.DEVICE_HEALTH_BELOW -> R.string.alerts_m_device_health_below
        AlertMetric.DEVICE_OFFLINE_MINUTES -> R.string.alerts_m_device_offline_minutes
        AlertMetric.INVOICE_OVERDUE_DAYS -> R.string.alerts_m_invoice_overdue_days
    }

@get:StringRes val AlertMetric.description: Int
    get() = when (this) {
        AlertMetric.SITE_YIELD_BELOW -> R.string.alerts_d_site_yield_below
        AlertMetric.SITE_OFFLINE -> R.string.alerts_d_site_offline
        AlertMetric.DEVICE_EFFICIENCY_BELOW -> R.string.alerts_d_device_efficiency_below
        AlertMetric.DEVICE_HEALTH_BELOW -> R.string.alerts_d_device_health_below
        AlertMetric.DEVICE_OFFLINE_MINUTES -> R.string.alerts_d_device_offline_minutes
        AlertMetric.INVOICE_OVERDUE_DAYS -> R.string.alerts_d_invoice_overdue_days
    }

@get:StringRes val AlertSeverity.label: Int
    get() = when (this) {
        AlertSeverity.WARNING -> R.string.alerts_sev_warning
        AlertSeverity.DANGER -> R.string.alerts_sev_danger
    }

val AlertSeverity.color: Color
    get() = when (this) {
        AlertSeverity.WARNING -> Brand.Amber
        AlertSeverity.DANGER -> Brand.Red
    }

@get:StringRes val IntegrationVendor.label: Int
    get() = when (this) {
        IntegrationVendor.SOLAREDGE -> R.string.int_v_solaredge
        IntegrationVendor.FUSIONSOLAR -> R.string.int_v_fusionsolar
        IntegrationVendor.WEBHOOK -> R.string.int_v_webhook
    }

@get:StringRes val IntegrationStatus.label: Int
    get() = when (this) {
        IntegrationStatus.PENDING -> R.string.int_status_pending
        IntegrationStatus.OK -> R.string.int_status_ok
        IntegrationStatus.ERROR -> R.string.int_status_error
    }

val IntegrationStatus.color: Color
    get() = when (this) {
        IntegrationStatus.PENDING -> Brand.Amber
        IntegrationStatus.OK -> Brand.Green
        IntegrationStatus.ERROR -> Brand.Red
    }

@get:StringRes val WeatherKind.label: Int
    get() = when (this) {
        WeatherKind.SUNNY -> R.string.wx_sunny
        WeatherKind.PARTLY -> R.string.wx_partly
        WeatherKind.CLOUDY -> R.string.wx_cloudy
        WeatherKind.FOG -> R.string.wx_fog
        WeatherKind.RAIN -> R.string.wx_rain
        WeatherKind.SNOW -> R.string.wx_snow
        WeatherKind.STORM -> R.string.wx_storm
    }

/** Notification accent. */
val NotificationKind.tint: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        NotificationKind.INFO -> Brand.Blue500
        NotificationKind.SUCCESS -> SolarTheme.colors.success
        NotificationKind.WARNING -> SolarTheme.colors.warning
        NotificationKind.DANGER -> SolarTheme.colors.danger
    }

/** Health / efficiency colour thresholds (web HealthBar). */
fun healthColor(v: Double): Color = when {
    v >= 90 -> Brand.Green
    v >= 75 -> Brand.Amber
    else -> Brand.Red
}
