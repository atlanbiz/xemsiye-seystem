package com.solarpulse.app.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.ChipRow
import com.solarpulse.app.ui.components.ColumnChart
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.LegendDot
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SearchField
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.InvoiceRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.seed.Seed
import com.solarpulse.core.time.Days
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Marks an invoice paid (+ notification when billing updates are on) — shared with InvoiceScreen. */
suspend fun markInvoicePaid(repo: DataRepository, db: Database, inv: Invoice, title: String) {
    repo.upsertInvoices(listOf(inv.copy(status = InvoiceStatus.PAID, paidAt = Days.key(java.time.LocalDate.now()))))
    if (db.settings.notifyBilling) repo.notify(title, "${inv.number} · ${inv.customer}", NotificationKind.SUCCESS, "/billing?open=${inv.id}")
}

@Composable
fun BillingScreen(siteFilter: String?) {
    val c = LocalContext.current.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<InvoiceStatus?>(null) }
    var period by rememberSaveable { mutableStateOf<String?>(null) }
    var site by rememberSaveable { mutableStateOf(siteFilter) }
    var generating by rememberSaveable { mutableStateOf(false) }

    val d = db ?: return
    val periods = remember(d.invoices) { d.invoices.map { it.period }.distinct().sortedDescending() }
    val rows = remember(d.invoices, query, status, period, site) {
        val q = query.trim().lowercase()
        d.invoices
            .filter { (status == null || it.status == status) && (period == null || it.period == period) && (site == null || it.siteId == site) }
            .filter { i -> q.isEmpty() || listOf(i.number, i.customer).any { it.lowercase().contains(q) } }
            .sortedWith(compareByDescending<Invoice> { it.period }.thenBy { it.number })
    }
    fun sum(f: (Invoice) -> Boolean) = d.invoices.filter(f).sumOf { it.amount }
    val all = sum { true }
    val paid = sum { it.status == InvoiceStatus.PAID }
    val chartPeriods = periods.reversed()
    val paidSeries = chartPeriods.map { p -> d.invoices.filter { it.period == p && it.status == InvoiceStatus.PAID }.sumOf { it.amount } }
    val openSeries = chartPeriods.map { p -> d.invoices.filter { it.period == p && it.status != InvoiceStatus.PAID }.sumOf { it.amount } }
    val markedPaid = stringResource(R.string.bill_markedPaid)

    ScreenScaffold(
        title = stringResource(R.string.bill_title),
        subtitle = stringResource(R.string.bill_subtitle),
        showBack = true,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { generating = true },
                icon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.bill_generate)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.bill_totalBilled), fmt.money(all), Brand.Blue500, Modifier.weight(1f),
                            sub = { Text("${d.invoices.size} ${stringResource(R.string.bill_invoice)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                        StatTile(Icons.Rounded.CheckCircle, stringResource(R.string.bill_paid), fmt.money(paid), Brand.Green, Modifier.weight(1f),
                            sub = { Text(fmt.pct(paid / (if (all == 0.0) 1.0 else all) * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.Rounded.Schedule, stringResource(R.string.bill_pending), fmt.money(sum { it.status == InvoiceStatus.PENDING }), Brand.Amber, Modifier.weight(1f))
                        StatTile(Icons.Rounded.Error, stringResource(R.string.bill_overdue), fmt.money(sum { it.status == InvoiceStatus.OVERDUE }), Brand.Red, Modifier.weight(1f))
                    }
                }
            }
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.bill_revenueTrend))
                    Spacer(Modifier.height(8.dp))
                    ColumnChart(
                        series = listOf(paidSeries, openSeries),
                        colors = listOf(Brand.Green, Brand.Amber),
                        xLabel = { i -> chartPeriods.getOrNull(i)?.let { fmt.monthShort(YearMonth.parse(it).atDay(1)) } ?: "" },
                        yLabel = { v -> "${Math.round(v / 1000)}k" },
                        markerText = fmt::money,
                        stacked = true,
                        height = 200,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(Brand.Green, stringResource(R.string.status_paid))
                        LegendDot(Brand.Amber, stringResource(R.string.status_pending))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(query, { query = it })
                    Row {
                        SelectField(
                            stringResource(R.string.common_site),
                            listOf<Pair<String?, String>>(null to stringResource(R.string.an_allSites)) + d.sites.map { it.id to it.name },
                            site, { site = it }, Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        SelectField(
                            stringResource(R.string.bill_period),
                            listOf<Pair<String?, String>>(null to stringResource(R.string.common_all)) + periods.map { it to fmt.month(it) },
                            period, { period = it }, Modifier.weight(1f),
                        )
                    }
                    ChipRow(InvoiceStatus.entries.map { it to stringResource(it.label) }, { it == status }, { status = if (status == it) null else it })
                    Text(
                        "${stringResource(R.string.common_showing)} ${rows.size} ${stringResource(R.string.common_of)} ${d.invoices.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) item { EmptyState(Icons.Rounded.Payments, stringResource(R.string.common_noData)) }
            items(rows, key = { it.id }) { inv ->
                SpCard(Modifier.animateItem(), onClick = { app.navigate(InvoiceRoute(inv.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.number, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr))
                            Text(inv.customer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusPill(stringResource(inv.status.label), inv.status.color)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.money2(inv.amount), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
                            Text(
                                "${fmt.month(inv.period)} · ${fmt.energy(inv.energyKwh)} · ${stringResource(R.string.bill_due)} ${fmt.date(inv.dueAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (inv.status != InvoiceStatus.PAID) {
                            FilledTonalButton(onClick = {
                                haptic()
                                scope.launch {
                                    markInvoicePaid(repo, d, inv, markedPaid)
                                    snackbar.showSnackbar(markedPaid)
                                }
                            }) { Text(stringResource(R.string.bill_markPaid), maxLines = 1) }
                        }
                    }
                }
            }
        }
    }

    if (generating) {
        val months = remember { (1..12).map { YearMonth.now().minusMonths(it.toLong() - 1).toString() } }
        var month by remember { mutableStateOf(YearMonth.now().minusMonths(1).toString()) }
        val exists = stringResource(R.string.bill_alreadyExists)
        val generatedTemplate = stringResource(R.string.bill_generated, "%s")
        AlertDialog(
            onDismissRequest = { generating = false },
            title = { Text(stringResource(R.string.bill_generate)) },
            text = { SelectField(stringResource(R.string.bill_generateFor), months.map { it to fmt.month(it) }, month, { month = it }) },
            confirmButton = {
                Button(onClick = {
                    haptic()
                    val existing = d.invoices.filter { it.period == month }.map { it.siteId }.toSet()
                    val seed = Seed(c.sim)
                    val list = d.sites.filter { it.id !in existing }
                        .mapIndexed { i, s -> seed.buildInvoice(s, month, existing.size + i + 1) }
                        .filter { it.energyKwh > 0 }
                    generating = false
                    scope.launch {
                        if (list.isEmpty()) {
                            snackbar.showSnackbar(exists)
                        } else {
                            repo.upsertInvoices(list)
                            val msg = generatedTemplate.replace("%s", list.size.toString())
                            if (d.settings.notifyBilling) repo.notify(msg, month, NotificationKind.INFO, "/billing")
                            period = month
                            snackbar.showSnackbar(msg)
                        }
                    }
                }) { Text(stringResource(R.string.bill_generate)) }
            },
            dismissButton = { TextButton(onClick = { generating = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
