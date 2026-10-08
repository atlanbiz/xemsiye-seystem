package com.solarpulse.app.ui.screens.overview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.Weather
import com.solarpulse.app.data.WeatherKind
import com.solarpulse.app.data.weatherKind
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.appViewModel
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.ChangeLine
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.LineChart
import com.solarpulse.app.ui.components.LiveBadge
import com.solarpulse.app.ui.components.LoadingSkeleton
import com.solarpulse.app.ui.components.PillTabs
import com.solarpulse.app.ui.components.Ring
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.Series
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.Sparkline
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.DevicesRoute
import com.solarpulse.app.ui.nav.SiteDetailRoute
import com.solarpulse.app.ui.nav.SitesRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.time.Days
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OverviewScreen() {
    val vm = appViewModel { OverviewViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val impactPeriod by vm.impactPeriod.collectAsStateWithLifecycle()
    val c = LocalContext.current.container
    val db by c.repository.db.collectAsStateWithLifecycle()
    val settings = db?.settings
    LaunchedEffect(settings?.lat, settings?.lng) { settings?.let { vm.loadWeather(it.lat, it.lng) } }

    ScreenScaffold(title = stringResource(R.string.nav_overview)) { padding ->
        val s = state
        if (s == null) {
            LoadingSkeleton(Modifier.padding(padding))
            return@ScreenScaffold
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > 760.dp
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "hero") {
                        TwoUp(wide, { m: Modifier -> Hero(s, m) }, { m: Modifier -> WeatherCard(weather, settings?.city.orEmpty(), s.now, m) })
                    }
                    item(key = "kpis") { KpiGrid(s, wide) }
                    item(key = "gen") {
                        TwoUp(
                            wide,
                            { m: Modifier -> GenerationCard(s.generation, period, { vm.period.value = it }, m) },
                            { m: Modifier -> EnergyFlowCard(s, m) },
                        )
                    }
                    item(key = "impact") {
                        TwoUp(
                            wide,
                            { m: Modifier -> ImpactCard(s.impact, impactPeriod, { vm.impactPeriod.value = it }, m) },
                            { m: Modifier -> HealthCard(s, m) },
                        )
                    }
                    item(key = "sites") { SitePerformance(s) }
                }
            }
        }
    }
}

/** Two cards side by side on wide screens, stacked on phones. */
@Composable
private fun TwoUp(wide: Boolean, a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            a(Modifier.weight(1f))
            b(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            a(Modifier.fillMaxWidth())
            b(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Hero(s: OverviewState, modifier: Modifier) {
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val greet = stringResource(
        when {
            s.hour < 12 -> R.string.greet_morning
            s.hour < 18 -> R.string.greet_afternoon
            else -> R.string.greet_evening
        },
    )
    val healthWord = stringResource(
        when (s.health) {
            Health.EXCELLENT -> R.string.hero_excellent
            Health.GOOD -> R.string.hero_good
            Health.ATTENTION -> R.string.hero_attention
        },
    )
    val accent = if (s.health == Health.ATTENTION) Color(0xFFFDE68A) else Color(0xFFBFF0D0)
    // hero.title2 is empty in languages whose word order folds it into title1
    val lead = listOf(stringResource(R.string.hero_title1), stringResource(R.string.hero_title2)).filter { it.isNotBlank() }.joinToString(" ")
    val title = buildAnnotatedString {
        append(lead)
        append(" ")
        withStyle(SpanStyle(color = accent)) { append(healthWord) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Brand.Blue700, Brand.Blue600, Brand.Blue400)))
            .clickable { app.navigate(SitesRoute) },
    ) {
        // decorative sun glow
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension * 0.55f
            drawCircle(
                Brush.radialGradient(listOf(Color(0x55FDE68A), Color.Transparent), center = Offset(size.width * 0.92f, size.height * 0.05f), radius = r),
                radius = r,
                center = Offset(size.width * 0.92f, size.height * 0.05f),
            )
        }
        Column(Modifier.padding(20.dp)) {
            Text("$greet, ${s.userFirstName}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(
                    value = if (s.capacityKw > 0) s.liveKw / s.capacityKw * 100 else 0.0,
                    color = Color.White,
                    size = 86.dp,
                    stroke = 7.dp,
                    contentDescription = stringResource(R.string.ov_totalCapacity),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.num(s.capacityKw / 1000, 2), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("MWp", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ov_totalSites), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text(s.totalSites.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroDot(Brand.Green, stringResource(R.string.ov_activeSites), s.active)
                        HeroDot(Brand.Amber, stringResource(R.string.ov_idleSites), s.idle)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDot(color: Color, label: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text("$label $n", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

private fun WeatherKind.icon(): ImageVector = when (this) {
    WeatherKind.SUNNY -> Icons.Rounded.WbSunny
    WeatherKind.PARTLY -> Icons.Rounded.WbCloudy
    WeatherKind.CLOUDY, WeatherKind.FOG -> Icons.Rounded.Cloud
    WeatherKind.RAIN -> Icons.Rounded.Umbrella
    WeatherKind.SNOW -> Icons.Rounded.AcUnit
    WeatherKind.STORM -> Icons.Rounded.Thunderstorm
}

@Composable
private fun WeatherCard(w: Weather, city: String, now: Long, modifier: Modifier) {
    val fmt = LocalFmt.current
    val kind = weatherKind(w.code)
    SpCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt.dateLong(Days.localDate(now, fmt.zone)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.cityLabel(city, fmt.lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.live) LiveBadge()
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                kind.icon(),
                contentDescription = stringResource(kind.label),
                tint = if (kind == WeatherKind.SUNNY) Brand.Amber else Brand.Sky,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${w.temp}°C", style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr))
                Text(stringResource(kind.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherStat(Icons.Rounded.LightMode, stringResource(R.string.wx_irradiance), "${w.irradiance} W/m²", Modifier.weight(1f))
            WeatherStat(Icons.Rounded.Air, stringResource(R.string.wx_wind), "${w.wind} km/h", Modifier.weight(1f))
            WeatherStat(Icons.Rounded.WaterDrop, stringResource(R.string.wx_humidity), "${w.humidity}%", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeatherStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr), maxLines = 1)
    }
}

@Composable
private fun KpiGrid(s: OverviewState, wide: Boolean) {
    val fmt = LocalFmt.current
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { m ->
            StatTile(
                Icons.Rounded.Bolt, stringResource(R.string.ov_currentPower), fmt.power(s.liveKw), Brand.Blue500, m,
                sub = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot()
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ov_liveOutput), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.anyLive) {
                            Spacer(Modifier.width(6.dp))
                            LiveBadge()
                        }
                    }
                },
                trailing = { Sparkline(s.liveSpark, Brand.Blue500, Modifier.size(64.dp, 30.dp)) },
            )
        },
        { m ->
            StatTile(
                Icons.Rounded.WbSunny, stringResource(R.string.ov_energyToday), fmt.energy(s.todayKwh), Brand.Amber, m,
                sub = { ChangeLine(s.todayChange, stringResource(R.string.common_vsYesterday), fmt.signedPct(s.todayChange)) },
                trailing = { Sparkline(s.spark14, Brand.Amber, Modifier.size(64.dp, 30.dp)) },
            )
        },
        { m ->
            StatTile(
                Icons.Rounded.Eco, stringResource(R.string.ov_co2Offset), "${fmt.num(s.co2Mtd, 1)} ${stringResource(R.string.common_tons)}", Brand.Sky, m,
                sub = { ChangeLine(s.co2Change, stringResource(R.string.common_vsLastMonth), fmt.signedPct(s.co2Change)) },
                trailing = { Sparkline(s.spark14, Brand.Sky, Modifier.size(64.dp, 30.dp)) },
            )
        },
        { m ->
            StatTile(
                Icons.Rounded.AttachMoney, stringResource(R.string.ov_totalRevenue), fmt.money(s.revenueMtd), Brand.Green, m,
                sub = { ChangeLine(s.revenueChange, stringResource(R.string.common_vsLastMonth), fmt.signedPct(s.revenueChange)) },
                trailing = { Sparkline(s.revSpark, Brand.Green, Modifier.size(64.dp, 30.dp)) },
            )
        },
    )
    val perRow = if (wide) 4 else 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PulseDot() {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseA")
    Box(Modifier.size(8.dp).clip(CircleShape).background(SolarTheme.colors.success.copy(alpha = a)))
}

@Composable
private fun GenerationCard(g: Generation, period: GenPeriod, onPeriod: (GenPeriod) -> Unit, modifier: Modifier) {
    val fmt = LocalFmt.current
    SpCard(modifier) {
        SectionHeader(stringResource(R.string.ov_energyGeneration))
        Spacer(Modifier.height(8.dp))
        PillTabs(
            options = listOf(
                GenPeriod.DAILY to stringResource(R.string.common_daily),
                GenPeriod.WEEKLY to stringResource(R.string.common_weekly),
                GenPeriod.MONTHLY to stringResource(R.string.common_monthly),
            ),
            selected = period,
            onSelect = onPeriod,
        )
        Spacer(Modifier.height(12.dp))
        Text(fmt.energy(g.total), style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.ContentOrLtr))
        Text(stringResource(R.string.ov_totalEnergy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChangeLine(g.change, stringResource(if (g.hourly) R.string.common_vsYesterday else R.string.common_vsPrevPeriod), fmt.signedPct(g.change))
        Spacer(Modifier.height(8.dp))
        if (g.hourly) {
            LineChart(
                series = listOf(Series(g.xs, g.ys, Brand.Blue500)),
                xLabel = { v -> "%02d:00".format(java.util.Locale.ROOT, v.toInt()) },
                yLabel = fmt::axisPower,
                markerText = fmt::power,
                minX = 0.0,
                maxX = 24.0,
                minY = 0.0,
                labelSpacing = 12,
            )
        } else {
            LineChart(
                series = listOf(Series(g.xs, g.ys, Brand.Blue500)),
                xLabel = { v -> g.labels.getOrNull(v.toInt())?.let { fmt.dateShort(it) } ?: "" },
                yLabel = fmt::axisEnergy,
                markerText = fmt::energy,
                minY = 0.0,
                labelSpacing = if (g.labels.size > 10) 6 else 1,
            )
        }
        Text(
            "▲ " + if (g.hourly) fmt.power(g.peak) else fmt.energy(g.peak),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnergyFlowCard(s: OverviewState, modifier: Modifier) {
    val fmt = LocalFmt.current
    val f = s.flow
    val gridIn = f.grid >= 0
    val charging = f.battery >= 0
    val t = rememberInfiniteTransition(label = "flow")
    val phase by t.animateFloat(0f, 40f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    val solarC = Brand.Blue500
    val gridC = Brand.Violet
    val batC = Brand.Green
    val loadC = Brand.Amber
    val muted = MaterialTheme.colorScheme.outlineVariant
    val desc = stringResource(R.string.ov_energyFlow)
    SpCard(modifier) {
        SectionHeader(desc)
        Spacer(Modifier.height(8.dp))
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxWidth().height(250.dp).semantics { contentDescription = desc }) {
                Canvas(Modifier.matchParentSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    fun flowLine(from: Offset, active: Boolean, color: Color, reverse: Boolean) {
                        val p = Path().apply {
                            moveTo(from.x, from.y)
                            cubicTo((from.x + cx) / 2, from.y, (from.x + cx) / 2, cy, cx, cy)
                        }
                        drawPath(
                            p,
                            if (active) color else muted,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = if (active) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), if (reverse) phase else -phase) else null,
                            ),
                        )
                    }
                    flowLine(Offset(size.width * 0.18f, size.height * 0.2f), f.solar > 0.5, solarC, reverse = false)
                    flowLine(Offset(size.width * 0.82f, size.height * 0.2f), abs(f.grid) > 0.5, gridC, reverse = !gridIn)
                    flowLine(Offset(size.width * 0.18f, size.height * 0.8f), abs(f.battery) > 0.5, batC, reverse = charging)
                    flowLine(Offset(size.width * 0.82f, size.height * 0.8f), true, loadC, reverse = true)
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Brand.Blue400, Brand.Blue700))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                FlowNode(Icons.Rounded.WbSunny, stringResource(R.string.ov_solarGen), fmt.power(f.solar), solarC, Modifier.align(Alignment.TopStart))
                FlowNode(
                    Icons.Rounded.ElectricalServices,
                    stringResource(if (gridIn) R.string.ov_gridImport else R.string.ov_gridExport),
                    fmt.power(abs(f.grid)), gridC, Modifier.align(Alignment.TopEnd),
                )
                FlowNode(
                    Icons.Rounded.BatteryChargingFull,
                    stringResource(R.string.ov_batteryStorage),
                    "${if (charging) "+" else "−"}${fmt.power(abs(f.battery))} · ${f.batterySoc.roundToInt()}%",
                    batC, Modifier.align(Alignment.BottomStart),
                )
                FlowNode(Icons.Rounded.Power, stringResource(R.string.ov_consumption), fmt.power(f.consumption), loadC, Modifier.align(Alignment.BottomEnd))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = if (charging) SolarTheme.colors.success else SolarTheme.colors.warning,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(if (charging) R.string.ov_charging else R.string.ov_discharging) + " · " +
                    "${fmt.energy(f.batteryCapacity * f.batterySoc / 100)} / ${fmt.energy(f.batteryCapacity)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowNode(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 30.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr), maxLines = 1)
        }
    }
}

@Composable
private fun ImpactCard(i: Impact, period: ImpactPeriod, onPeriod: (ImpactPeriod) -> Unit, modifier: Modifier) {
    val fmt = LocalFmt.current
    SpCard(modifier) {
        SectionHeader(stringResource(R.string.ov_envImpact))
        Spacer(Modifier.height(8.dp))
        PillTabs(
            options = listOf(
                ImpactPeriod.MONTHLY to stringResource(R.string.common_monthly),
                ImpactPeriod.YEARLY to stringResource(R.string.common_yearly),
                ImpactPeriod.ALL to stringResource(R.string.common_all),
            ),
            selected = period,
            onSelect = onPeriod,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ImpactRow(Icons.Rounded.Park, Brand.Green, stringResource(R.string.ov_trees), fmt.num(i.trees), stringResource(R.string.ov_trees_unit))
            ImpactRow(Icons.Rounded.Eco, Brand.Sky, stringResource(R.string.ov_co2Offset), fmt.num(i.co2, 1), stringResource(R.string.common_tons))
            ImpactRow(Icons.Rounded.DirectionsCar, Brand.Amber, stringResource(R.string.ov_cars), fmt.num(i.cars, 1), stringResource(R.string.ov_cars_unit))
        }
    }
}

@Composable
private fun ImpactRow(icon: ImageVector, tint: Color, label: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value $unit", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HealthCard(s: OverviewState, modifier: Modifier) {
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    fun color(v: Double) = when {
        v >= 90 -> Brand.Green
        v >= 75 -> Brand.Amber
        else -> Brand.Red
    }
    @Composable
    fun word(v: Double) = stringResource(
        when {
            v >= 97 -> R.string.ov_excellent
            v >= 90 -> R.string.ov_good
            v >= 75 -> R.string.ov_fair
            else -> R.string.ov_poor
        },
    )
    SpCard(modifier) {
        SectionHeader(stringResource(R.string.ov_perfHealth)) {
            TextButton(onClick = { app.navigate(DevicesRoute()) }) {
                Text(stringResource(R.string.common_viewAll))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                stringResource(R.string.ov_availability) to s.stats.availability,
                stringResource(R.string.ov_inverterEff) to s.stats.inverterEfficiency,
                stringResource(R.string.ov_batteryHealth) to s.stats.batteryHealth,
            ).forEach { (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(v, color(v), size = 52.dp, stroke = 5.dp, contentDescription = "$label ${fmt.pct(v)}") {
                        Text(fmt.pct(v), style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(word(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("${stringResource(R.string.status_online)} ${s.stats.online}", Brand.Green)
            StatusPill("${stringResource(R.string.status_warning)} ${s.stats.warning}", Brand.Amber)
            StatusPill("${stringResource(R.string.status_offline)} ${s.stats.offline}", Brand.Red)
        }
    }
}

@Composable
private fun SitePerformance(s: OverviewState) {
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    SpCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.ov_sitePerformance)) {
            TextButton(onClick = { app.navigate(SitesRoute) }) { Text(stringResource(R.string.common_viewAll)) }
        }
        s.topSites.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { app.navigate(SiteDetailRoute(r.site.id)) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.site.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${fmt.power(r.kw)} · ${fmt.energy(r.kwh)}",
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Sparkline(r.spark, if (r.site.status.key == "active") Brand.Blue500 else Brand.Slate400, Modifier.size(64.dp, 26.dp), fill = false)
                Spacer(Modifier.width(10.dp))
                if (r.live) LiveBadge() else StatusPill(stringResource(r.site.status.label), r.site.status.color)
            }
        }
    }
}
