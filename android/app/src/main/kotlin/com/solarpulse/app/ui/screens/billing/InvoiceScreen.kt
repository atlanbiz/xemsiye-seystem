package com.solarpulse.app.ui.screens.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.pdf.PdfBuilder
import com.solarpulse.app.pdf.invoicePdf
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.KeyValueGrid
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.screens.login.SolarLogo
import com.solarpulse.core.model.InvoiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Invoice document preview with Mark as paid, PDF share and delete (web invoice modal). */
@Composable
fun InvoiceScreen(id: String) {
    val context = LocalContext.current
    val c = context.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var deleting by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    val d = db ?: return
    val inv = d.invoices.firstOrNull { it.id == id }
    val site = inv?.let { d.site(it.siteId) }
    val markedPaid = stringResource(R.string.bill_markedPaid)
    val pdfFailed = stringResource(R.string.common_pdfFailed)
    val deletedMsg = stringResource(R.string.common_deleted)

    ScreenScaffold(
        title = inv?.number ?: stringResource(R.string.bill_invoice),
        subtitle = inv?.customer,
        showBack = true,
        actions = {
            if (inv != null) IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete)) }
        },
    ) { padding ->
        if (inv == null) {
            Box(Modifier.padding(padding).fillMaxSize()) { EmptyState(Icons.Rounded.Payments, stringResource(R.string.common_noData)) }
            return@ScreenScaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpCard(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        SolarLogo()
                        Spacer(Modifier.height(6.dp))
                        Text(d.settings.company, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(d.settings.email, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(inv.number, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                        Spacer(Modifier.height(4.dp))
                        StatusPill(stringResource(inv.status.label), inv.status.color)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(14.dp),
                ) {
                    KeyValueGrid(
                        buildList {
                            add(stringResource(R.string.bill_billTo) to "${inv.customer}\n${site?.name.orEmpty()}")
                            add(stringResource(R.string.bill_period) to fmt.month(inv.period))
                            add(stringResource(R.string.bill_issued) to fmt.date(inv.issuedAt))
                            add(stringResource(R.string.bill_due) to fmt.date(inv.dueAt))
                            inv.paidAt?.let { add(stringResource(R.string.bill_paidOn) to fmt.date(it)) }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.common_description), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${stringResource(R.string.rep_energy)} — ${site?.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row {
                    Line(stringResource(R.string.bill_energy), "${fmt.num(inv.energyKwh)} kWh", Modifier.weight(1f))
                    Line(stringResource(R.string.bill_rate), fmt.money2(inv.rate), Modifier.weight(1f))
                    Line(stringResource(R.string.bill_amount), fmt.money2(inv.amount), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp),
                ) {
                    Text(stringResource(R.string.common_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(fmt.money2(inv.amount), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.bill_thankYou),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        exporting = true
                        scope.launch {
                            val file = runCatching { withContext(Dispatchers.IO) { invoicePdf(context, inv, d, fmt) } }.getOrNull()
                            exporting = false
                            if (file == null) snackbar.showSnackbar(pdfFailed) else PdfBuilder.share(context, file, inv.number)
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_sharePdf), maxLines = 1)
                }
                if (inv.status != InvoiceStatus.PAID) {
                    Button(
                        onClick = {
                            haptic()
                            scope.launch {
                                markInvoicePaid(repo, d, inv, markedPaid)
                                snackbar.showSnackbar(markedPaid)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bill_markPaid), maxLines = 1)
                    }
                }
            }
        }
    }

    if (deleting && inv != null) {
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            onConfirm = {
                scope.launch {
                    repo.removeInvoices(listOf(inv.id))
                    app.back()
                    snackbar.showSnackbar(deletedMsg)
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun Line(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr))
    }
}
