package com.solarpulse.app.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe Navigation Compose routes. */

@Serializable data object OverviewRoute
@Serializable data object SitesRoute
@Serializable data class SiteDetailRoute(val id: String)
@Serializable data class MapRoute(val focus: String? = null)
@Serializable data class DevicesRoute(val siteId: String? = null, val query: String? = null)
@Serializable data class MaintenanceRoute(val siteId: String? = null, val openId: String? = null)
@Serializable data class BillingRoute(val siteId: String? = null)
@Serializable data class InvoiceRoute(val id: String)
@Serializable data object AnalyticsRoute
@Serializable data object ReportsRoute
@Serializable data class FinanceRoute(val siteId: String? = null)
@Serializable data object AlertsRoute
@Serializable data object SettingsRoute
@Serializable data object IntegrationsRoute
@Serializable data object NotificationsRoute
@Serializable data object MoreRoute

/**
 * Maps the web-style links stored in notifications (`/sites/site-11`, `/billing?open=…`,
 * `/devices?q=…`, `/maintenance?open=…`) to routes.
 */
object Links {
    fun route(link: String?): Any? {
        if (link.isNullOrBlank()) return null
        val path = link.substringBefore('?').trimEnd('/')
        val query = link.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        return when {
            path.startsWith("/sites/") -> SiteDetailRoute(path.removePrefix("/sites/"))
            path == "/sites" -> SitesRoute
            path == "/devices" -> DevicesRoute(siteId = query["site"], query = query["q"])
            path == "/maintenance" -> MaintenanceRoute(siteId = query["site"], openId = query["open"])
            path == "/billing" -> query["open"]?.let { InvoiceRoute(it) } ?: BillingRoute(query["site"])
            path == "/analytics" -> AnalyticsRoute
            path == "/reports" -> ReportsRoute
            path == "/finance" -> FinanceRoute(query["site"])
            path == "/alerts" -> AlertsRoute
            path == "/map" -> MapRoute(query["site"])
            path == "/settings" -> SettingsRoute
            path == "" || path == "/" -> OverviewRoute
            else -> null
        }
    }
}
