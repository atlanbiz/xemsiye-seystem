package com.solarpulse.app.ui.screens.sites

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.solarpulse.app.ui.components.ChipRow
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.LiveBadge
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SearchField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.Sparkline
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.SiteDetailRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.time.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SiteCardData(val site: Site, val kw: Double, val kwh: Double, val spark: List<Double>, val devices: Int, val live: Boolean)

class SitesViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val status = MutableStateFlow<SiteStatus?>(null)
    val type = MutableStateFlow<SiteType?>(null)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val minute = c.ticker.map { it / 60_000 }.distinctUntilChanged()

    val rows: StateFlow<List<SiteCardData>?> = combine(
        c.repository.db.filterNotNull(), c.repository.readings, minute, query, combine(status, type) { s, t -> s to t },
    ) { db, readings, _, q, filters ->
        val now = c.sim.now()
        val day = Days.key(Days.localDate(now, c.sim.zone))
        val s = q.trim().lowercase()
        db.sites
            .filter { (filters.first == null || it.status == filters.first) && (filters.second == null || it.type == filters.second) }
            .filter { x -> s.isEmpty() || listOf(x.name, x.location, x.customer).any { it.lowercase().contains(s) } }
            .map { x ->
                SiteCardData(
                    site = x,
                    kw = Live.siteKw(c.sim, x, readings, now),
                    kwh = c.sim.siteDayKwhCached(x, day),
                    spark = c.sim.hourlySeries(listOf(x), day, 60).filter { it.kw != null && it.hour >= 6 }.map { it.kw!! },
                    devices = db.devices.count { it.siteId == x.id },
                    live = Live.reading(x, readings, now) != null,
                )
            }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val total: StateFlow<Int> = c.repository.db.map { it?.sites?.size ?: 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            c.repository.load()
            _refreshing.value = false
        }
    }

    fun save(site: Site, addedTitle: String) {
        viewModelScope.launch {
            val isNew = c.repository.db.value?.sites?.none { it.id == site.id } ?: true
            c.repository.upsertSite(site)
            if (isNew) c.repository.notify(addedTitle, site.name, NotificationKind.SUCCESS, "/sites/${site.id}")
        }
    }
}

@Composable
fun SitesScreen() {
    val vm = appViewModel { SitesViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var formOpen by rememberSaveable { mutableStateOf(false) }
    val saved = stringResource(R.string.common_saved)
    val addTitle = stringResource(R.string.sites_add)

    ScreenScaffold(
        title = stringResource(R.string.sites_title),
        subtitle = stringResource(R.string.sites_subtitle),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { formOpen = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(addTitle) },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchField(query, { vm.query.value = it })
                        ChipRow(
                            options = SiteStatus.entries.map { it to stringResource(it.label) },
                            isSelected = { it == status },
                            onToggle = { vm.status.value = if (status == it) null else it },
                        )
                        ChipRow(
                            options = SiteType.entries.map { it to stringResource(it.label) },
                            isSelected = { it == type },
                            onToggle = { vm.type.value = if (type == it) null else it },
                        )
                        Text(
                            "${stringResource(R.string.common_showing)} ${rows?.size ?: 0} ${stringResource(R.string.common_of)} $total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val list = rows.orEmpty()
                if (rows != null && list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(Icons.Rounded.SolarPower, stringResource(R.string.common_noData))
                    }
                }
                items(list, key = { it.site.id }) { r ->
                    SiteCard(r, Modifier.animateItem()) { app.navigate(SiteDetailRoute(r.site.id)) }
                }
            }
        }
    }

    if (formOpen) {
        SiteFormSheet(initial = null, onDismiss = { formOpen = false }) { site ->
            vm.save(site, addTitle)
            formOpen = false
            scope.launch { snackbar.showSnackbar(saved) }
        }
    }
}

@Composable
private fun SiteCard(r: SiteCardData, modifier: Modifier, onClick: () -> Unit) {
    val fmt = LocalFmt.current
    val s = r.site
    SpCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${s.location} · ${stringResource(s.type.label)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (r.live) LiveBadge() else StatusPill(stringResource(s.status.label), s.status.color)
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(r.spark, if (s.status == SiteStatus.ACTIVE) Brand.Blue500 else Brand.Slate400, Modifier.fillMaxWidth().height(46.dp))
        Spacer(Modifier.height(10.dp))
        Row {
            Metric(stringResource(R.string.sites_currentPower), fmt.power(r.kw), Modifier.weight(1f))
            Metric(stringResource(R.string.sites_energyToday), fmt.energy(r.kwh), Modifier.weight(1f))
            Metric(stringResource(R.string.sites_capacity), fmt.power(s.capacityKw), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.sites_devices), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(r.devices.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.batteryKwh > 0) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.BatteryFull, contentDescription = stringResource(R.string.sites_battery), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${fmt.num(s.batteryKwh)} kWh",
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr), maxLines = 1)
    }
}
