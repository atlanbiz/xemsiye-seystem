package com.solarpulse.app.ui.screens.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.pdf.PdfBuilder
import com.solarpulse.app.pdf.reportPdf
import com.solarpulse.app.report.Report
import com.solarpulse.app.report.ReportBuilder
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.components.DateField
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.LineChart
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.Series
import com.solarpulse.app.ui.components.SkeletonBlock
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.core.model.ReportKind
import com.solarpulse.core.model.SavedReport
import com.solarpulse.core.time.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private val ReportKind.icon: ImageVector
    get() = when (this) {
        ReportKind.ENERGY -> Icons.Rounded.Bolt
        ReportKind.FINANCIAL -> Icons.Rounded.MonetizationOn
        ReportKind.DEVICES -> Icons.Rounded.Memory
        ReportKind.MAINTENANCE -> Icons.Rounded.Engineering
        ReportKind.ENVIRONMENT -> Icons.Rounded.Eco
    }

private data class Request(val kind: ReportKind, val from: String, val to: String, val siteIds: List<String>)

@Composable
fun ReportsScreen() {
    val context = LocalContext.current
    val c = context.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val prevMonth = YearMonth.now().minusMonths(1)
    var kind by rememberSaveable { mutableStateOf(ReportKind.ENERGY) }
    var from by rememberSaveable { mutableStateOf(Days.key(prevMonth.atDay(1))) }
    var to by rememberSaveable { mutableStateOf(Days.key(prevMonth.atEndOfMonth())) }
    var siteIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var request by remember { mutableStateOf<Request?>(null) }
    var report by remember { mutableStateOf<Report?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val d = db ?: return
    val strings: (Int) -> String = { id -> context.getString(id) }
    val savedMsg = stringResource(R.string.rep_saved)
    val pdfFailed = stringResource(R.string.common_pdfFailed)

    LaunchedEffect(request, d) {
        val r = request ?: return@LaunchedEffect
        report = null
        report = withContext(Dispatchers.Default) { ReportBuilder.build(strings, r.kind, r.from, r.to, r.siteIds, d, c.sim, fmt) }
    }

    ScreenScaffold(title = stringResource(R.string.rep_title), subtitle = stringResource(R.string.rep_subtitle), showBack = true) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.rep_kind))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportKind.entries.forEach { k ->
                            val on = k == kind
                            OutlinedCard(
                                onClick = { kind = k },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(k.icon, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(k.label), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        DateField(from, { from = it }, stringResource(R.string.common_from), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        DateField(to, { to = it }, stringResource(R.string.common_to), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 to R.string.common_last7, 30 to R.string.common_last30, 90 to R.string.common_last90, 365 to R.string.common_last12m).forEach { (n, label) ->
                            AssistChip(onClick = {
                                from = Days.key(LocalDate.now().minusDays(n - 1L))
                                to = Days.key(LocalDate.now())
                            }, label = { Text(stringResource(label)) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.rep_sites), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = siteIds.isEmpty(), onClick = { siteIds = emptyList() }, label = { Text(stringResource(R.string.rep_allSites)) })
                        d.sites.forEach { s ->
                            val on = s.id in siteIds
                            FilterChip(
                                selected = on,
                                onClick = { siteIds = if (on) siteIds - s.id else siteIds + s.id },
                                label = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptic()
                            request = Request(kind, from, to, siteIds)
                        },
                        enabled = from <= to,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.rep_generate))
                    }
                }
            }
            val r = request
            if (r != null) {
                item {
                    val rep = report
                    SpCard {
                        SectionHeader(stringResource(R.string.rep_preview))
                        Spacer(Modifier.height(6.dp))
                        if (rep == null) {
                            SkeletonBlock(Modifier.fillMaxWidth().height(200.dp))
                        } else {
                            ReportPreview(rep)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    haptic()
                                    scope.launch {
                                        repo.upsertReport(
                                            SavedReport(
                                                id = DataRepository.newId("rep-"),
                                                kind = r.kind,
                                                title = "${rep.title} · ${r.from} → ${r.to}",
                                                from = r.from,
                                                to = r.to,
                                                siteIds = r.siteIds,
                                                createdAt = Days.iso(c.sim.now()),
                                            ),
                                        )
                                        snackbar.showSnackbar(savedMsg)
                                    }
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Save, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.common_save))
                                }
                                Button(
                                    onClick = {
                                        exporting = true
                                        scope.launch {
                                            val file = runCatching {
                                                withContext(Dispatchers.IO) { reportPdf(context, rep, d, fmt, fmt.dateTime(Days.iso(c.sim.now()))) }
                                            }.getOrNull()
                                            exporting = false
                                            if (file == null) snackbar.showSnackbar(pdfFailed) else PdfBuilder.share(context, file, rep.title)
                                        }
                                    },
                                    enabled = !exporting,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.common_sharePdf), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.rep_history)) }
            if (d.reports.isEmpty()) item { EmptyState(Icons.Rounded.Description, stringResource(R.string.rep_empty)) }
            items(d.reports, key = { it.id }) { saved ->
                SpCard(Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(saved.kind.label), style = MaterialTheme.typography.titleSmall)
                            Text("${saved.from} → ${saved.to}", style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${fmt.ago(saved.createdAt, now)} · ${if (saved.siteIds.isEmpty()) stringResource(R.string.rep_allSites) else "${saved.siteIds.size} ${stringResource(R.string.rep_sites)}"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            kind = saved.kind
                            from = saved.from
                            to = saved.to
                            siteIds = saved.siteIds
                            request = Request(saved.kind, saved.from, saved.to, saved.siteIds)
                        }) { Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.rep_open)) }
                        IconButton(onClick = { scope.launch { repo.removeReport(saved.id) } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportPreview(rep: Report) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rep.title, style = MaterialTheme.typography.titleLarge)
        Text("${rep.rangeLabel} · ${rep.scopeLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rep.summary.forEach { (label, value) ->
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
                }
            }
        }
        if (rep.chart.size > 1) {
            LineChart(
                series = listOf(Series(rep.chart.indices.map { it.toDouble() }, rep.chart.map { it.second }, Brand.Blue500)),
                xLabel = { v -> rep.chart.getOrNull(v.toInt())?.first ?: "" },
                yLabel = { v -> String.format(java.util.Locale.ROOT, "%.1f", v) },
                minY = 0.0,
                labelSpacing = maxOf(1, rep.chart.size / 5),
                height = 180,
            )
        }
        if (rep.display.isEmpty()) {
            EmptyState(Icons.Rounded.Description, stringResource(R.string.common_noData))
        } else {
            val widths = rep.weights.map { (it * 92).dp }
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(vertical = 8.dp)) {
                    rep.columns.forEachIndexed { i, col ->
                        Text(col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(widths[i]).padding(horizontal = 6.dp), maxLines = 2)
                    }
                }
                rep.display.forEach { row ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        row.forEachIndexed { i, cell ->
                            Text(cell, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr), modifier = Modifier.width(widths[i]).padding(horizontal = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
