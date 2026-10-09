package com.solarpulse.app.ui.screens.sites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.solarpulse.app.AppContainer
import com.solarpulse.app.R
import com.solarpulse.app.data.Live
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.appViewModel
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.ColumnChart
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.LineChart
import com.solarpulse.app.ui.components.LiveBadge
import com.solarpulse.app.ui.components.LoadingSkeleton
import com.solarpulse.app.ui.components.OsmMap
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.Series
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.BillingRoute
import com.solarpulse.app.ui.nav.DevicesRoute
import com.solarpulse.app.ui.nav.FinanceRoute
import com.solarpulse.app.ui.nav.InvoiceRoute
import com.solarpulse.app.ui.nav.MaintenanceRoute
import com.solarpulse.app.ui.nav.MapRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.finance.Finance
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Device
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.sim.DayPoint
import com.solarpulse.core.sim.HourPoint
import com.solarpulse.core.time.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SiteDetailState(
    val site: Site?,
    val kw: Double = 0.0,
    val live: Boolean = false,
    val todayKwh: Double = 0.0,
    val monthKwh: Double = 0.0,
    val last30: Double = 0.0,
    val hourly: List<HourPoint> = emptyList(),
    val daily: List<DayPoint> = emptyList(),
    val devices: List<Device> = emptyList(),
    val tickets: List<Ticket> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
)

class SiteDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val sim = c.sim
    private val minute = c.ticker.map { it / 60_000 }.distinctUntilChanged()

    val state: StateFlow<SiteDetailState?> = combine(c.repository.db.filterNotNull(), c.repository.readings, minute) { db, readings, _ ->
        build(db, readings)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** ROI for this site (heavier: 365 simulated days), recomputed when the site or rate changes. */
    val finance: StateFlow<Finance.Result?> = c.repository.db.filterNotNull()
        .map { db -> db.site(id)?.let { it to db.settings.discountRatePct } }
        .distinctUntilChanged()
        .map { p -> p?.let { (site, rate) -> Finance.analyzeSite(sim, site, rate).result } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(db: Database, readings: Map<String, com.solarpulse.core.model.Reading>): SiteDetailState {
        val site = db.site(id) ?: return SiteDetailState(null)
        val now = sim.now()
        val today = Days.localDate(now, sim.zone)
        val day = Days.key(today)
        return SiteDetailState(
            site = site,
            kw = Live.siteKw(sim, site, readings, now),
            live = Live.reading(site, readings, now) != null,
            todayKwh = sim.totalKwh(listOf(site), today, today),
            monthKwh = sim.totalKwh(listOf(site), today.withDayOfMonth(1), today),
            last30 = sim.totalKwh(listOf(site), today.minusDays(29), today),
            hourly = sim.hourlySeries(listOf(site), day, 30).filter { it.kw != null },
            daily = sim.dailySeries(listOf(site), today.minusDays(29), today),
            devices = db.devices.filter { it.siteId == id },
            tickets = db.tickets.filter { it.siteId == id },
            invoices = db.invoices.filter { it.siteId == id }.sortedByDescending { it.period },
        )
    }

    fun setStatus(status: SiteStatus, offlineTitle: String) {
        val site = c.repository.db.value?.site(id) ?: return
        viewModelScope.launch {
            c.repository.upsertSite(site.copy(status = status))
            if (status == SiteStatus.OFFLINE) c.repository.notify(offlineTitle, site.name, NotificationKind.DANGER, "/sites/${site.id}")
        }
    }

    fun save(site: Site) {
        viewModelScope.launch { c.repository.upsertSite(site) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            c.repository.removeSite(id)
            onDone()
        }
    }
}

@Composable
fun SiteDetailScreen(id: String) {
    val vm = appViewModel(key = "site-$id") { SiteDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val finance by vm.finance.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val saved = stringResource(R.string.common_saved)
    val deleted = stringResource(R.string.common_deleted)
    val offlineTitle = stringResource(R.string.status_offline)
    val s = state
    val site = s?.site

    ScreenScaffold(
        title = site?.name ?: stringResource(R.string.sites_title),
        subtitle = site?.let { "${it.location} · ${stringResource(it.type.label)}" },
        showBack = true,
        actions = {
            if (site != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.common_edit)) }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete)) }
            }
        },
    ) { padding ->
        when {
            s == null -> LoadingSkeleton(Modifier.padding(padding))
            site == null -> Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(Icons.Rounded.SolarPower, stringResource(R.string.sites_notFound)) {
                    TextButton(onClick = app.back) { Text(stringResource(R.string.common_back)) }
                }
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SpCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(site.customer, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${site.location} · ${fmt.date(site.installDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (s.live) LiveBadge()
                        }
                        Spacer(Modifier.height(10.dp))
                        SelectField(
                            stringResource(R.string.common_status),
                            SiteStatus.entries.map { it to stringResource(it.label) },
                            site.status,
                            { st ->
                                vm.setStatus(st, offlineTitle)
                                scope.launch { snackbar.showSnackbar(saved) }
                            },
                        )
                    }
                }
                item {
                    val tiles = listOf<@Composable (Modifier) -> Unit>(
                        { m ->
                            StatTile(
                                Icons.Rounded.Bolt, stringResource(R.string.sites_currentPower), fmt.power(s.kw), Brand.Blue500, m,
                                sub = { SmallLine("${fmt.pct(if (site.capacityKw > 0) s.kw / site.capacityKw * 100 else 0.0)} / ${fmt.power(site.capacityKw)}") },
                            )
                        },
                        { m ->
                            StatTile(
                                Icons.Rounded.WbSunny, stringResource(R.string.sites_energyToday), fmt.energy(s.todayKwh), Brand.Amber, m,
                                sub = { SmallLine(fmt.money2(s.todayKwh * site.pricePerKwh)) },
                            )
                        },
                        { m ->
                            StatTile(
                                Icons.Rounded.CalendarMonth, stringResource(R.string.sites_thisMonth), fmt.energy(s.monthKwh), Brand.Green, m,
                                sub = { SmallLine(fmt.money(s.monthKwh * site.pricePerKwh)) },
                            )
                        },
                        { m ->
                            StatTile(
                                Icons.Rounded.Speed, stringResource(R.string.sites_yield),
                                "${fmt.num(if (site.capacityKw > 0) s.last30 / 30 / site.capacityKw else 0.0, 2)} kWh/kWp", Brand.Violet, m,
                                sub = { SmallLine(stringResource(R.string.an_avgDaily)) },
                            )
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                        }
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.sites_todayCurve))
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(Series(s.hourly.map { it.hour }, s.hourly.map { it.kw ?: 0.0 }, Brand.Blue500)),
                            xLabel = { v -> "%02d:00".format(java.util.Locale.ROOT, v.toInt()) },
                            yLabel = fmt::axisPower,
                            markerText = fmt::power,
                            minX = 0.0,
                            maxX = 24.0,
                            minY = 0.0,
                            labelSpacing = 12,
                            height = 190,
                        )
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.sites_last30))
                        Spacer(Modifier.height(8.dp))
                        ColumnChart(
                            series = listOf(s.daily.map { it.kwh }),
                            colors = listOf(Brand.Blue500),
                            xLabel = { i -> s.daily.getOrNull(i)?.let { fmt.dateShort(it.date) } ?: "" },
                            yLabel = fmt::axisEnergy,
                            markerText = fmt::energy,
                            labelSpacing = 7,
                            height = 190,
                        )
                    }
                }
                item { FinanceCard(finance) { app.navigate(FinanceRoute(site.id)) } }
                item {
                    SpCard(contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))) {
                            OsmMap(listOf(site), Modifier.fillMaxSize(), selectedId = site.id, focusId = site.id, interactive = false, dark = SolarTheme.colors.isDark)
                        }
                        TextButton(onClick = { app.navigate(MapRoute(site.id)) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.map_viewOnMap))
                        }
                    }
                }
                item {
                    ListCard("${stringResource(R.string.sites_devices)} (${s.devices.size})", { app.navigate(DevicesRoute(siteId = site.id)) }) {
                        s.devices.forEach { d ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr))
                                    Text("${stringResource(d.type.label)} · ${d.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                StatusPill(stringResource(d.status.label), d.status.color)
                            }
                        }
                    }
                }
                item {
                    ListCard("${stringResource(R.string.sites_tickets)} (${s.tickets.size})", { app.navigate(MaintenanceRoute(siteId = site.id)) }) {
                        s.tickets.forEach { t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { app.navigate(MaintenanceRoute(openId = t.id)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${fmt.date(t.dueDate)} · ${t.assignee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(stringResource(t.status.label), t.status.color)
                            }
                        }
                    }
                }
                item {
                    ListCard("${stringResource(R.string.sites_invoices)} (${s.invoices.size})", { app.navigate(BillingRoute(site.id)) }) {
                        s.invoices.take(6).forEach { inv ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { app.navigate(InvoiceRoute(inv.id)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(inv.number, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr))
                                    Text("${fmt.energy(inv.energyKwh)} · ${fmt.money2(inv.amount)}", style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(stringResource(inv.status.label), inv.status.color)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing && site != null) {
        SiteFormSheet(initial = site, onDismiss = { editing = false }) {
            vm.save(it)
            editing = false
            scope.launch { snackbar.showSnackbar(saved) }
        }
    }
    if (deleting) {
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            detail = stringResource(R.string.sites_deleteWarn),
            onConfirm = {
                vm.delete {
                    app.back()
                    scope.launch { snackbar.showSnackbar(deleted) }
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun SmallLine(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun ListCard(title: String, onViewAll: () -> Unit, content: @Composable () -> Unit) {
    SpCard(Modifier.fillMaxWidth()) {
        SectionHeader(title) { TextButton(onClick = onViewAll) { Text(stringResource(R.string.common_viewAll)) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Site ROI summary (payback, ROI, NPV, IRR) with a link to the full analysis. */
@Composable
fun FinanceCard(r: Finance.Result?, onDetails: () -> Unit) {
    val fmt = LocalFmt.current
    SpCard(Modifier.fillMaxWidth(), onClick = onDetails) {
        SectionHeader(stringResource(R.string.fin_card)) {
            Icon(Icons.Rounded.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        if (r == null) {
            Box(Modifier.fillMaxWidth().height(60.dp)) { com.solarpulse.app.ui.components.SkeletonBlock(Modifier.fillMaxSize()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.fin_payback), paybackText(r.paybackYears), Modifier.weight(1f))
                Metric(stringResource(R.string.fin_roi), fmt.pct(r.roiPct, 0), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.fin_npv), fmt.money(r.npv), Modifier.weight(1f))
                Metric(stringResource(R.string.fin_irr), r.irrPct?.let { fmt.pct(it) } ?: "—", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.fin_details), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** "8.4 yrs" or "> 25 yrs". */
@Composable
fun paybackText(years: Double?): String {
    val fmt = LocalFmt.current
    return if (years == null) stringResource(R.string.fin_never) else stringResource(R.string.fin_years, fmt.num(years, 1))
}
