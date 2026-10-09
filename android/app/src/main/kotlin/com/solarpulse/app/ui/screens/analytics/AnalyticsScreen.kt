package com.solarpulse.app.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.solarpulse.app.AppContainer
import com.solarpulse.app.R
import com.solarpulse.app.container
import androidx.compose.ui.platform.LocalContext
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.appViewModel
import com.solarpulse.app.ui.components.ColumnChart
import com.solarpulse.app.ui.components.Donut
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.LegendDot
import com.solarpulse.app.ui.components.LineChart
import com.solarpulse.app.ui.components.LoadingSkeleton
import com.solarpulse.app.ui.components.PillTabs
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.Series
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.pctChange
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.sim.DayPoint
import com.solarpulse.core.sim.RangeKind
import com.solarpulse.core.sim.SimMath
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
import java.time.LocalDate
import java.time.YearMonth

data class AnalyticsState(
    val range: RangeKind,
    val total: Double,
    val change: Double,
    val avg: Double,
    val best: DayPoint?,
    val capacity: Double,
    /** x = index; labels are dates (days) or month starts (12M). */
    val trend: List<Double>,
    val trendLabels: List<LocalDate>,
    val monthly: Boolean,
    val bySite: List<Pair<String, Double>>,
    val byType: List<Pair<SiteType, Double>>,
    val prodHours: List<Double>,
    val prod: List<Double>,
    val cons: List<Double>,
    val heat: List<Pair<LocalDate, List<Double>>>,
    val heatMax: Double,
    val co2: Double,
    val trees: Double,
    val cars: Double,
    val monthlyCo2: List<Pair<LocalDate, Double>>,
)

class AnalyticsViewModel(private val c: AppContainer) : ViewModel() {
    private val sim = c.sim
    val range = MutableStateFlow(RangeKind.D30)
    val siteId = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)
    private val minute = c.ticker.map { it / 60_000 }.distinctUntilChanged()

    val state: StateFlow<AnalyticsState?> = combine(c.repository.db.filterNotNull(), range, siteId, minute) { db, r, id, _ -> compute(db, r, id) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            c.repository.load()
            refreshing.value = false
        }
    }

    private fun compute(db: Database, r: RangeKind, id: String?): AnalyticsState {
        val sites = if (id == null) db.sites else db.sites.filter { it.id == id }
        val (from, to) = sim.rangeOf(r)
        val series = sim.dailySeries(sites, from, to)
        val total = series.sumOf { it.kwh }
        val days = series.size
        val prevTotal = sim.totalKwh(sites, from.minusDays(days.toLong()), from.minusDays(1))
        val monthly = r == RangeKind.M12
        val trend: List<Double>
        val labels: List<LocalDate>
        if (monthly) {
            val groups = series.groupBy { YearMonth.from(it.date) }
            labels = groups.keys.map { it.atDay(1) }
            trend = groups.values.map { g -> g.sumOf { it.kwh } }
        } else {
            labels = series.map { it.date }
            trend = series.map { it.kwh }
        }
        val bySite = sites.map { it.name to sim.totalKwh(listOf(it), from, to) }.sortedByDescending { it.second }
        val byType = sites.groupBy { it.type }.map { (t, l) -> t to l.sumOf { sim.totalKwh(listOf(it), from, to) } }
        val day = sim.todayKey()
        val hourly = sim.hourlySeries(sites, day, 60).filter { it.kw != null }
        val heat = (0 until 14).map { i ->
            val d = sim.today().minusDays(13L - i)
            val k = Days.key(d)
            d to (0 until 24).map { h -> sites.sumOf { sim.siteKw(it, k, h + 0.5) } }
        }
        val heatMax = maxOf(1.0, heat.maxOfOrNull { row -> row.second.maxOrNull() ?: 0.0 } ?: 1.0)
        val s = db.settings
        val co2 = SimMath.co2Tons(total, s.co2KgPerKwh)
        val today = sim.today()
        val monthlyCo2 = (11 downTo 0).map { k ->
            val start = YearMonth.from(today).minusMonths(k.toLong()).atDay(1)
            val end = YearMonth.from(start).atEndOfMonth().let { if (it.isAfter(today)) today else it }
            start to SimMath.co2Tons(sim.totalKwh(sites, start, end), s.co2KgPerKwh)
        }
        return AnalyticsState(
            range = r,
            total = total,
            change = pctChange(total, prevTotal),
            avg = if (days > 0) total / days else 0.0,
            best = series.maxByOrNull { it.kwh },
            capacity = sites.sumOf { it.capacityKw },
            trend = trend,
            trendLabels = labels,
            monthly = monthly,
            bySite = bySite,
            byType = byType,
            prodHours = hourly.map { it.hour },
            prod = hourly.map { it.kw!! },
            cons = hourly.map { sim.consumptionKw(sites, it.hour, day) },
            heat = heat,
            heatMax = heatMax,
            co2 = co2,
            trees = co2 * 1000 / s.treeKgPerYear,
            cars = co2 / s.carTonsPerYear,
            monthlyCo2 = monthlyCo2,
        )
    }
}

@Composable
fun AnalyticsScreen() {
    val vm = appViewModel { AnalyticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val siteId by vm.siteId.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val db by LocalContext.current.container.repository.db.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current

    ScreenScaffold(title = stringResource(R.string.an_title), subtitle = stringResource(R.string.an_subtitle), showBack = true) { padding ->
        PullToRefreshBox(refreshing, vm::refresh, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField(
                            stringResource(R.string.common_site),
                            listOf<Pair<String?, String>>(null to stringResource(R.string.an_allSites)) + db?.sites.orEmpty().map { it.id to it.name },
                            siteId,
                            { vm.siteId.value = it },
                        )
                        PillTabs(
                            listOf(RangeKind.D7 to "7D", RangeKind.D30 to "30D", RangeKind.D90 to "90D", RangeKind.M12 to "12M"),
                            range,
                            { vm.range.value = it },
                        )
                    }
                }
                val s = state
                if (s == null) {
                    item { LoadingSkeleton() }
                    return@LazyColumn
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(Icons.Rounded.WbSunny, stringResource(R.string.an_production), fmt.energy(s.total), Brand.Blue500, Modifier.weight(1f),
                                sub = {
                                    Text(
                                        fmt.signedPct(s.change),
                                        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                        color = if (s.change >= 0) SolarTheme.colors.success else SolarTheme.colors.danger,
                                    )
                                })
                            StatTile(Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.an_avgDaily), fmt.energy(s.avg), Brand.Green, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(Icons.Rounded.Leaderboard, stringResource(R.string.an_peakDay), fmt.energy(s.best?.kwh ?: 0.0), Brand.Amber, Modifier.weight(1f),
                                sub = { s.best?.let { Text(fmt.date(it.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } })
                            StatTile(Icons.Rounded.Speed, stringResource(R.string.an_perfRatio), "${fmt.num(if (s.capacity > 0) s.avg / s.capacity else 0.0, 2)} kWh/kWp", Brand.Violet, Modifier.weight(1f),
                                sub = { Text(stringResource(R.string.an_avgDaily), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                        }
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.an_trend))
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(Series(s.trend.indices.map { it.toDouble() }, s.trend, Brand.Blue500)),
                            xLabel = { v ->
                                s.trendLabels.getOrNull(v.toInt())?.let { if (s.monthly) fmt.monthShort(it) else fmt.dateShort(it) } ?: ""
                            },
                            yLabel = fmt::axisEnergy,
                            markerText = fmt::energy,
                            minY = 0.0,
                            labelSpacing = when {
                                s.monthly -> 2
                                s.trend.size > 60 -> 15
                                s.trend.size > 14 -> 6
                                else -> 1
                            },
                            height = 220,
                        )
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.an_bySite))
                        Spacer(Modifier.height(8.dp))
                        val max = s.bySite.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.bySite.forEach { (name, v) ->
                                Column {
                                    Row {
                                        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(fmt.energy(v), style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
                                    }
                                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Brand.Blue500))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.an_byType))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Donut(s.byType.map { it.second }, Brand.Chart, Modifier.size(140.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val sum = s.byType.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
                                s.byType.forEachIndexed { i, (t, v) ->
                                    LegendDot(Brand.Chart[i % Brand.Chart.size], "${stringResource(t.label)} · ${fmt.pct(v / sum * 100, 0)}")
                                }
                            }
                        }
                    }
                }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.an_prodVsCons))
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            series = listOf(
                                Series(s.prodHours, s.prod, Brand.Blue500),
                                Series(s.prodHours, s.cons, Brand.Amber, dashed = true),
                            ),
                            xLabel = { v -> "%02d:00".format(java.util.Locale.ROOT, v.toInt()) },
                            yLabel = fmt::axisPower,
                            markerText = fmt::power,
                            minX = 0.0,
                            maxX = 24.0,
                            minY = 0.0,
                            labelSpacing = 6,
                            area = false,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(Brand.Blue500, stringResource(R.string.an_production_short))
                            LegendDot(Brand.Amber, stringResource(R.string.an_consumption_short))
                        }
                    }
                }
                item { Heatmap(s) }
                item {
                    SpCard {
                        SectionHeader(stringResource(R.string.ov_envImpact))
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ImpactMini(Icons.Rounded.Eco, Brand.Sky, stringResource(R.string.ov_co2Offset), "${fmt.num(s.co2, 1)} ${stringResource(R.string.common_tons)}", Modifier.weight(1f))
                            ImpactMini(Icons.Rounded.Park, Brand.Green, stringResource(R.string.ov_trees), fmt.num(s.trees), Modifier.weight(1f))
                            ImpactMini(Icons.Rounded.DirectionsCar, Brand.Amber, stringResource(R.string.ov_cars), fmt.num(s.cars, 1), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        ColumnChart(
                            series = listOf(s.monthlyCo2.map { it.second }),
                            colors = listOf(Brand.Green),
                            xLabel = { i -> s.monthlyCo2.getOrNull(i)?.let { fmt.monthShort(it.first) } ?: "" },
                            yLabel = { v -> fmt.num(v, 1) },
                            markerText = { v -> "${fmt.num(v, 1)} t CO₂" },
                            labelSpacing = 2,
                            height = 180,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactMini(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        IconBadge(icon, tint, size = 34.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Hourly output heatmap for the last 14 days (left-to-right hours, like the web). */
@Composable
private fun Heatmap(s: AnalyticsState) {
    val fmt = LocalFmt.current
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val title = stringResource(R.string.an_heatmap)
    SpCard {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = title }) {
                Row {
                    Spacer(Modifier.width(56.dp))
                    (0 until 24).forEach { h ->
                        Text(if (h % 3 == 0) h.toString() else "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                s.heat.forEach { (date, cells) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.dateShort(date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Canvas(Modifier.size(16.dp * 24, 16.dp)) {
                            val cw = size.width / 24
                            cells.forEachIndexed { h, v ->
                                val color = if (v < 0.01) empty else Brand.Blue500.copy(alpha = (0.12 + 0.88 * (v / s.heatMax)).toFloat().coerceIn(0f, 1f))
                                drawRoundRect(color, Offset(h * cw + 1f, 1f), Size(cw - 2f, size.height - 2f), CornerRadius(4f, 4f))
                            }
                        }
                    }
                }
            }
        }
    }
}
