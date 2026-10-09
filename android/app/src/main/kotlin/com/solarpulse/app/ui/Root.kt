package com.solarpulse.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.solarpulse.app.MainViewModel
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.AuthState
import com.solarpulse.app.ui.components.AppBackground
import com.solarpulse.app.ui.components.LoadingSkeleton
import com.solarpulse.app.ui.nav.AlertsRoute
import com.solarpulse.app.ui.nav.AnalyticsRoute
import com.solarpulse.app.ui.nav.BillingRoute
import com.solarpulse.app.ui.nav.DevicesRoute
import com.solarpulse.app.ui.nav.FinanceRoute
import com.solarpulse.app.ui.nav.IntegrationsRoute
import com.solarpulse.app.ui.nav.InvoiceRoute
import com.solarpulse.app.ui.nav.Links
import com.solarpulse.app.ui.nav.MaintenanceRoute
import com.solarpulse.app.ui.nav.MapRoute
import com.solarpulse.app.ui.nav.MoreRoute
import com.solarpulse.app.ui.nav.NotificationsRoute
import com.solarpulse.app.ui.nav.OverviewRoute
import com.solarpulse.app.ui.nav.ReportsRoute
import com.solarpulse.app.ui.nav.SettingsRoute
import com.solarpulse.app.ui.nav.SiteDetailRoute
import com.solarpulse.app.ui.nav.SitesRoute
import com.solarpulse.app.ui.screens.alerts.AlertsScreen
import com.solarpulse.app.ui.screens.analytics.AnalyticsScreen
import com.solarpulse.app.ui.screens.billing.BillingScreen
import com.solarpulse.app.ui.screens.billing.InvoiceScreen
import com.solarpulse.app.ui.screens.devices.DevicesScreen
import com.solarpulse.app.ui.screens.finance.FinanceScreen
import com.solarpulse.app.ui.screens.login.LoginScreen
import com.solarpulse.app.ui.screens.maintenance.MaintenanceScreen
import com.solarpulse.app.ui.screens.map.MapScreen
import com.solarpulse.app.ui.screens.more.MoreScreen
import com.solarpulse.app.ui.screens.more.NotificationsScreen
import com.solarpulse.app.ui.screens.overview.OverviewScreen
import com.solarpulse.app.ui.screens.reports.ReportsScreen
import com.solarpulse.app.ui.screens.settings.IntegrationsScreen
import com.solarpulse.app.ui.screens.settings.RequestNotificationPermissionOnce
import com.solarpulse.app.ui.screens.settings.SettingsScreen
import com.solarpulse.app.ui.screens.sites.SiteDetailScreen
import com.solarpulse.app.ui.screens.sites.SitesScreen
import com.solarpulse.app.ui.theme.SolarPulseTheme
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Currency
import com.solarpulse.core.model.Lang
import com.solarpulse.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/** BCP-47 tag for a UI language; the -u-nu-latn extension keeps system formatters on Latin digits. */
fun localeTag(lang: Lang): String = when (lang) {
    Lang.AR -> "ar-u-nu-latn"
    Lang.UG -> "ug-u-nu-latn"
    else -> lang.code
}

/** Switches the app language in place (activity is recreated with the new resources). */
fun applyAppLanguage(lang: Lang) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag(lang)))
}

@Composable
fun SolarPulseRoot(vm: MainViewModel, pendingLink: MutableStateFlow<String?>, onDarkTheme: (Boolean) -> Unit) {
    val container = LocalContext.current.container
    val auth by vm.auth.collectAsStateWithLifecycle()
    val db by vm.db.collectAsStateWithLifecycle()
    val themeMode by vm.theme.collectAsStateWithLifecycle()
    val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()

    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) { onDarkTheme(dark) }

    // First run: adopt the shared language setting (default Uyghur, like the web) unless the user
    // already picked an app language (Settings or the system's per-app language screen).
    val settingsLang = db?.settings?.language
    LaunchedEffect(settingsLang) {
        if (settingsLang != null && AppCompatDelegate.getApplicationLocales().isEmpty) applyAppLanguage(settingsLang)
    }

    val lang = Lang.of(LocalConfiguration.current.locales[0].language)
    val currency = db?.settings?.currency ?: Currency.USD
    val fmt = remember(lang, currency) { Fmt(lang, currency, container.sim.zone) }
    val snackbar = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalFmt provides fmt,
        LocalSnackbar provides snackbar,
        LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        SolarPulseTheme(darkTheme = dark, dynamicColor = dynamic) {
            AppBackground {
                val screen = when {
                    auth == AuthState.Loading -> 0
                    auth == AuthState.SignedOut -> 1
                    db == null -> 2
                    else -> 3
                }
                AnimatedContent(targetState = screen, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "root") { s ->
                    when (s) {
                        0 -> Box(Modifier.fillMaxSize())
                        1 -> LoginScreen()
                        2 -> LoadingSkeleton(Modifier.statusBarsPadding())
                        else -> AppShell(pendingLink)
                    }
                }
            }
        }
    }
}

private enum class TopLevel(val route: Any, val label: Int, val icon: ImageVector) {
    OVERVIEW(OverviewRoute, R.string.nav_overview, Icons.Rounded.Dashboard),
    SITES(SitesRoute, R.string.nav_sites, Icons.Rounded.SolarPower),
    MAP(MapRoute(), R.string.nav_map, Icons.Rounded.Map),
    DEVICES(DevicesRoute(), R.string.nav_devices, Icons.Rounded.Memory),
    MORE(MoreRoute, R.string.nav_more, Icons.Rounded.MoreHoriz),
}

private fun sectionOf(dest: NavDestination?): TopLevel {
    val h = dest?.hierarchy?.toList().orEmpty()
    return when {
        dest == null || h.has<OverviewRoute>() -> TopLevel.OVERVIEW
        h.has<SitesRoute>() || h.has<SiteDetailRoute>() -> TopLevel.SITES
        h.has<MapRoute>() -> TopLevel.MAP
        h.has<DevicesRoute>() -> TopLevel.DEVICES
        else -> TopLevel.MORE
    }
}

private inline fun <reified T : Any> List<NavDestination>.has(): Boolean = any { it.hasRoute(T::class) }

private fun NavController.navigateTop(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Adaptive shell: bottom bar on phones, navigation rail on tablets / foldables. */
@Composable
private fun AppShell(pendingLink: MutableStateFlow<String?>) {
    val container = LocalContext.current.container
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val section = sectionOf(entry?.destination)
    val db by container.repository.db.collectAsStateWithLifecycle()
    val unread = db?.notifications?.count { !it.read } ?: 0
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    val link by pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        val route = Links.route(link)
        if (route != null) nav.navigate(route)
        if (link != null) pendingLink.value = null
    }

    val actions = AppActions(
        openNotifications = { nav.navigate(NotificationsRoute) { launchSingleTop = true } },
        navigate = { r -> nav.navigate(r) },
        back = { nav.popBackStack() },
        unreadCount = unread,
        bottomBarShown = layoutType == NavigationSuiteType.NavigationBar,
    )
    val colors = MaterialTheme.colorScheme
    RequestNotificationPermissionOnce()
    CompositionLocalProvider(LocalAppActions provides actions) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevel.entries.forEach { t ->
                    item(
                        selected = section == t,
                        onClick = { if (section != t || t == TopLevel.MORE) nav.navigateTop(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                    )
                }
            },
            layoutType = layoutType,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface.copy(alpha = 0.92f),
            ),
            containerColor = Color.Transparent,
        ) {
            NavHost(
                navController = nav,
                startDestination = OverviewRoute,
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 12 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(160)) + androidx.compose.animation.slideOutHorizontally(tween(200)) { it / 12 } },
            ) {
                composable<OverviewRoute> { OverviewScreen() }
                composable<SitesRoute> { SitesScreen() }
                composable<SiteDetailRoute> { SiteDetailScreen(it.toRoute<SiteDetailRoute>().id) }
                composable<MapRoute> { MapScreen(it.toRoute<MapRoute>().focus) }
                composable<DevicesRoute> {
                    val r = it.toRoute<DevicesRoute>()
                    DevicesScreen(r.siteId, r.query)
                }
                composable<MaintenanceRoute> {
                    val r = it.toRoute<MaintenanceRoute>()
                    MaintenanceScreen(r.siteId, r.openId)
                }
                composable<BillingRoute> { BillingScreen(it.toRoute<BillingRoute>().siteId) }
                composable<InvoiceRoute> { InvoiceScreen(it.toRoute<InvoiceRoute>().id) }
                composable<AnalyticsRoute> { AnalyticsScreen() }
                composable<ReportsRoute> { ReportsScreen() }
                composable<FinanceRoute> { FinanceScreen(it.toRoute<FinanceRoute>().siteId) }
                composable<AlertsRoute> { AlertsScreen() }
                composable<SettingsRoute> { SettingsScreen() }
                composable<IntegrationsRoute> { IntegrationsScreen() }
                composable<NotificationsRoute> { NotificationsScreen() }
                composable<MoreRoute> { MoreScreen() }
            }
        }
    }
}
