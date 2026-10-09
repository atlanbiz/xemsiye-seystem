package com.solarpulse.app.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.appViewModel
import com.solarpulse.app.ui.components.InfoBanner
import com.solarpulse.app.ui.components.KeyValueGrid
import com.solarpulse.app.ui.components.LineChart
import com.solarpulse.app.ui.components.LoadingSkeleton
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.Series
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.nav.SettingsRoute
import com.solarpulse.app.ui.screens.sites.Metric
import com.solarpulse.app.ui.screens.sites.paybackText
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.finance.Finance
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

class FinanceViewModel(c: AppContainer, initialSite: String?) : ViewModel() {
    val siteId = MutableStateFlow(initialSite)

    /** Portfolio analysis; recomputed only when sites or the discount rate change. */
    val portfolio: StateFlow<Finance.Portfolio?> = c.repository.db.filterNotNull()
        .map { it.sites to it.settings.discountRatePct }
        .distinctUntilChanged()
        .map { (sites, rate) -> Finance.analyzePortfolio(c.sim, sites, rate) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val discountRate: StateFlow<Double> = c.repository.db.filterNotNull().map { it.settings.discountRatePct }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 6.0)

    /** Selected scope: the portfolio (null) or one site. */
    val selected: StateFlow<Pair<String?, Finance.Result>?> = combine(portfolio, siteId) { p, id ->
        if (p == null) null else id?.let { sid -> p.perSite.firstOrNull { it.site.id == sid }?.let { sid to it.result } } ?: (null to p.portfolio)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun FinanceScreen(initialSite: String?) {
    val vm = appViewModel(key = "finance-$initialSite") { FinanceViewModel(it, initialSite) }
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val rate by vm.discountRate.collectAsStateWithLifecycle()
    val siteId by vm.siteId.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current

    ScreenScaffold(title = stringResource(R.string.fin_title), subtitle = stringResource(R.string.fin_subtitle), showBack = true) { padding ->
        val p = portfolio
        val sel = selected
        if (p == null || sel == null) {
            LoadingSkeleton(Modifier.padding(padding))
            return@ScreenScaffold
        }
        val r = sel.second
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SelectField(
                    stringResource(R.string.common_site),
                    listOf<Pair<String?, String>>(null to stringResource(R.string.fin_portfolio)) + p.perSite.map { it.site.id to it.site.name },
                    siteId,
                    { vm.siteId.value = it },
                )
            }
            item {
                val tiles = listOf<@Composable (Modifier) -> Unit>(
                    { m -> StatTile(Icons.Rounded.AccountBalance, stringResource(R.string.fin_investment), fmt.money(r.systemCost), Brand.Blue500, m) },
                    { m ->
                        StatTile(Icons.Rounded.Savings, stringResource(R.string.fin_npv), fmt.money(r.npv), if (r.npv >= 0) Brand.Green else Brand.Red, m,
                            sub = { Sub("${stringResource(R.string.fin_discountRate)} ${fmt.pct(rate)}") })
                    },
                    { m -> StatTile(Icons.Rounded.Percent, stringResource(R.string.fin_irr), r.irrPct?.let { fmt.pct(it) } ?: "—", Brand.Violet, m) },
                    { m ->
                        StatTile(Icons.Rounded.Schedule, stringResource(R.string.fin_payback), paybackText(r.paybackYears), Brand.Amber, m,
                            sub = {
                                r.paybackYears?.let { y ->
                                    Sub(stringResource(R.string.fin_breakEven, (kotlin.math.floor(y).toInt() + 1).toString()))
                                }
                            })
                    },
                    { m -> StatTile(Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.fin_roi), fmt.pct(r.roiPct, 0), Brand.Green, m) },
                    { m -> StatTile(Icons.Rounded.Speed, stringResource(R.string.fin_lcoe), "${fmt.money2(r.lcoe)}${stringResource(R.string.fin_perKwh)}", Brand.Sky, m) },
                    { m ->
                        StatTile(Icons.Rounded.Payments, stringResource(R.string.fin_savings), fmt.money(r.year1Savings), Brand.Green, m,
                            sub = { Sub(stringResource(R.string.fin_yr1)) })
                    },
                    { m -> StatTile(Icons.Rounded.Bolt, stringResource(R.string.fin_e1), fmt.energy(r.e1), Brand.Amber, m) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                    }
                }
            }
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.fin_cashFlow))
                    Spacer(Modifier.height(8.dp))
                    val years = r.series.map { it.year.toDouble() }
                    val positive = r.series.lastOrNull()?.cumulative?.let { it >= 0 } ?: true
                    LineChart(
                        series = listOf(Series(years, r.series.map { it.cumulative }, if (positive) Brand.Green else Brand.Red)),
                        xLabel = { v -> v.toInt().toString() },
                        yLabel = { v -> compactMoney(v, fmt.currency.symbol) },
                        markerText = fmt::money,
                        labelSpacing = 5,
                        height = 220,
                    )
                    Text(
                        r.paybackYears?.let { stringResource(R.string.fin_breakEven, (kotlin.math.floor(it).toInt() + 1).toString()) }
                            ?: stringResource(R.string.fin_never),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (r.paybackYears != null) SolarTheme.colors.success else SolarTheme.colors.danger,
                    )
                }
            }
            if (sel.first == null) {
                item { SectionHeader(stringResource(R.string.fin_perSite)) }
                items(p.perSite.sortedByDescending { it.result.irrPct ?: -1000.0 }, key = { it.site.id }) { a ->
                    SpCard(Modifier.clickable { vm.siteId.value = a.site.id }) {
                        Text(a.site.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Metric(stringResource(R.string.fin_payback), paybackText(a.result.paybackYears), Modifier.weight(1f))
                            Metric(stringResource(R.string.fin_irr), a.result.irrPct?.let { fmt.pct(it) } ?: "—", Modifier.weight(1f))
                            Metric(stringResource(R.string.fin_npv), fmt.money(a.result.npv), Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.fin_assumptions)) {
                        TextButton(onClick = { app.navigate(SettingsRoute) }) { Text(stringResource(R.string.fin_editRate)) }
                    }
                    KeyValueGrid(
                        listOf(
                            stringResource(R.string.fin_discountRate) to fmt.pct(rate),
                            stringResource(R.string.fin_horizon) to stringResource(R.string.fin_years, Finance.HORIZON.toString()),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(Icons.Rounded.Info, stringResource(R.string.fin_model), MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Sub(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** Axis money labels: $1.2M, $350k. */
private fun compactMoney(v: Double, symbol: String): String {
    val a = kotlin.math.abs(v)
    val sign = if (v < 0) "-" else ""
    return when {
        a >= 1e6 -> "$sign$symbol${String.format(java.util.Locale.ROOT, "%.1f", a / 1e6)}M"
        a >= 1e3 -> "$sign$symbol${Math.round(a / 1e3)}k"
        else -> "$sign$symbol${Math.round(a)}"
    }
}

