package com.solarpulse.app.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.ChipRow
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.DateField
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.FormSheet
import com.solarpulse.app.ui.components.HealthBar
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.KeyValueGrid
import com.solarpulse.app.ui.components.NumberField
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SearchField
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.icon
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.MaintenanceRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.core.model.Device
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.DeviceType
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.model.TicketPriority
import com.solarpulse.core.model.TicketStatus
import com.solarpulse.core.time.Days
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DevicesScreen(siteFilter: String?, initialQuery: String?) {
    val c = LocalContext.current.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var site by rememberSaveable { mutableStateOf(siteFilter) }
    var type by rememberSaveable { mutableStateOf<DeviceType?>(null) }
    var status by rememberSaveable { mutableStateOf<DeviceStatus?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Device?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Device?>(null) }

    val d = db ?: return
    val all = d.devices
    val rows = remember(all, query, site, type, status) {
        val q = query.trim().lowercase()
        all.filter { x ->
            (type == null || x.type == type) && (status == null || x.status == status) && (site == null || x.siteId == site) &&
                (q.isEmpty() || listOf(x.name, x.model, x.serial).any { it.lowercase().contains(q) })
        }
    }
    val restarted = stringResource(R.string.dev_restarted)
    val saved = stringResource(R.string.common_saved)
    val deletedMsg = stringResource(R.string.common_deleted)
    val ticketCreated = stringResource(R.string.mt_created)
    val offlineWord = stringResource(R.string.status_offline)
    val statusWords = DeviceStatus.entries.associateWith { stringResource(it.label) }
    val fwTemplate = stringResource(R.string.dev_fwUpdated, "%s")

    ScreenScaffold(
        title = stringResource(R.string.dev_title),
        subtitle = stringResource(R.string.dev_subtitle),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.dev_add)) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Rounded.CheckCircle, stringResource(R.string.status_online), all.count { it.status == DeviceStatus.ONLINE }.toString(), Brand.Green, Modifier.weight(1f))
                    StatTile(Icons.Rounded.Warning, stringResource(R.string.status_warning), all.count { it.status == DeviceStatus.WARNING }.toString(), Brand.Amber, Modifier.weight(1f))
                    StatTile(Icons.Rounded.Error, stringResource(R.string.status_offline), all.count { it.status == DeviceStatus.OFFLINE }.toString(), Brand.Red, Modifier.weight(1f))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(query, { query = it })
                    SelectField(
                        stringResource(R.string.common_site),
                        listOf<Pair<String?, String>>(null to stringResource(R.string.an_allSites)) + d.sites.map { it.id to it.name },
                        site,
                        { site = it },
                    )
                    ChipRow(DeviceType.entries.map { it to stringResource(it.label) }, { it == type }, { type = if (type == it) null else it })
                    ChipRow(DeviceStatus.entries.map { it to statusWords.getValue(it) }, { it == status }, { status = if (status == it) null else it })
                    Text(
                        "${stringResource(R.string.common_showing)} ${rows.size} ${stringResource(R.string.common_of)} ${all.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Memory, stringResource(R.string.common_noData)) }
            }
            items(rows, key = { it.id }) { dev ->
                SpCard(Modifier.animateItem(), onClick = { detailId = dev.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(dev.type.icon, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dev.name, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                            Text(
                                "${stringResource(dev.type.label)} · ${d.siteName(dev.siteId)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        StatusPill(statusWords.getValue(dev.status), dev.status.color)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.dev_health), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HealthBar(dev.health)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            "${stringResource(R.string.dev_efficiency)} ${fmt.pct(dev.efficiency)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(fmt.ago(dev.lastSeen, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ── detail sheet with actions (restart / firmware / ticket) ─────────────
    val detail = all.firstOrNull { it.id == detailId }
    if (detail != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { detailId = null }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(detail.type.icon, MaterialTheme.colorScheme.primary, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(detail.name, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
                    IconButton(onClick = { editing = detail; detailId = null }) { Icon(Icons.Rounded.Edit, stringResource(R.string.common_edit)) }
                    IconButton(onClick = { deleting = detail; detailId = null }) { Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete)) }
                }
                StatusPill(statusWords.getValue(detail.status), detail.status.color)
                KeyValueGrid(
                    listOf(
                        stringResource(R.string.common_type) to stringResource(detail.type.label),
                        stringResource(R.string.common_site) to d.siteName(detail.siteId),
                        stringResource(R.string.dev_model) to detail.model,
                        stringResource(R.string.dev_serial) to detail.serial,
                        stringResource(R.string.dev_firmware) to detail.firmware,
                        stringResource(R.string.dev_efficiency) to fmt.pct(detail.efficiency),
                        stringResource(R.string.sites_installDate) to fmt.date(detail.installedAt),
                        stringResource(R.string.dev_lastSeen) to fmt.ago(detail.lastSeen, now),
                    ),
                )
                Text(stringResource(R.string.dev_health), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HealthBar(detail.health)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        haptic()
                        val parts = detail.firmware.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
                        val v = "v${parts.getOrElse(0) { 0 }}.${parts.getOrElse(1) { 0 } + 1}.0"
                        scope.launch {
                            repo.upsertDevice(detail.copy(firmware = v, lastSeen = Days.iso(c.sim.now())))
                            snackbar.showSnackbar(fwTemplate.replace("%s", v))
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dev_updateFw), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = {
                        haptic()
                        scope.launch {
                            repo.upsertDevice(detail.copy(status = DeviceStatus.ONLINE, lastSeen = Days.iso(c.sim.now()), health = maxOf(detail.health, 85.0)))
                            snackbar.showSnackbar(restarted)
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dev_restart), maxLines = 1)
                    }
                }
                FilledTonalButton(onClick = {
                    val id = DataRepository.newId("tkt-")
                    val t = Ticket(
                        id = id,
                        siteId = detail.siteId,
                        deviceId = detail.id,
                        title = "${detail.name} — ${statusWords.getValue(detail.status)}",
                        description = "${detail.model} (${detail.serial})",
                        priority = if (detail.status == DeviceStatus.OFFLINE) TicketPriority.HIGH else TicketPriority.MEDIUM,
                        status = TicketStatus.OPEN,
                        assignee = "",
                        dueDate = Days.key(LocalDate.now().plusDays(3)),
                        createdAt = Days.iso(c.sim.now()),
                    )
                    detailId = null
                    scope.launch {
                        repo.upsertTicket(t)
                        if (d.settings.notifyMaintenance) repo.notify(ticketCreated, detail.name, NotificationKind.INFO, "/maintenance?open=$id")
                        app.navigate(MaintenanceRoute(openId = id))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Engineering, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dev_createTicket))
                }
            }
        }
    }

    if (creating || editing != null) {
        DeviceFormSheet(
            initial = editing,
            sites = d.sites.map { it.id to it.name },
            onDismiss = {
                creating = false
                editing = null
            },
        ) { dev ->
            val prev = all.firstOrNull { it.id == dev.id }
            creating = false
            editing = null
            scope.launch {
                repo.upsertDevice(dev)
                if (dev.status == DeviceStatus.OFFLINE && prev?.status != DeviceStatus.OFFLINE && d.settings.notifyDeviceAlerts) {
                    repo.notify("${dev.name} $offlineWord", d.siteName(dev.siteId), NotificationKind.DANGER, "/devices")
                }
                snackbar.showSnackbar(saved)
            }
        }
    }
    deleting?.let { dev ->
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            onConfirm = {
                scope.launch {
                    repo.removeDevices(listOf(dev.id))
                    snackbar.showSnackbar(deletedMsg)
                }
            },
            onDismiss = { deleting = null },
        )
    }
    LaunchedEffect(siteFilter) { if (siteFilter != null) site = siteFilter }
}

@Composable
private fun DeviceFormSheet(initial: Device?, sites: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (Device) -> Unit) {
    var f by remember {
        mutableStateOf(
            initial ?: Device(
                id = DataRepository.newId("dev-"),
                siteId = sites.firstOrNull()?.first.orEmpty(),
                name = "",
                type = DeviceType.INVERTER,
                model = "",
                serial = "",
                status = DeviceStatus.ONLINE,
                health = 100.0,
                efficiency = 97.0,
                firmware = "v1.0.0",
                installedAt = Days.key(LocalDate.now()),
                lastSeen = Days.iso(System.currentTimeMillis()),
            ),
        )
    }
    var showErrors by remember { mutableStateOf(false) }
    val required = stringResource(R.string.common_required)
    fun err(bad: Boolean) = if (showErrors && bad) required else null
    FormSheet(
        title = stringResource(if (initial == null) R.string.dev_add else R.string.dev_edit),
        onDismiss = onDismiss,
        onSave = {
            showErrors = true
            if (f.name.isNotBlank() && f.siteId.isNotBlank() && f.model.isNotBlank()) onSave(f.copy(name = f.name.trim(), model = f.model.trim()))
        },
    ) {
        AppTextField(f.name, { f = f.copy(name = it) }, stringResource(R.string.common_name), error = err(f.name.isBlank()), ltr = true)
        SelectField(stringResource(R.string.common_site), sites, f.siteId, { f = f.copy(siteId = it) }, error = err(f.siteId.isBlank()))
        Row {
            SelectField(stringResource(R.string.common_type), DeviceType.entries.map { it to stringResource(it.label) }, f.type, { f = f.copy(type = it) }, Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            SelectField(stringResource(R.string.common_status), DeviceStatus.entries.map { it to stringResource(it.label) }, f.status, { f = f.copy(status = it) }, Modifier.weight(1f))
        }
        AppTextField(f.model, { f = f.copy(model = it) }, stringResource(R.string.dev_model), error = err(f.model.isBlank()), ltr = true)
        AppTextField(f.serial, { f = f.copy(serial = it) }, stringResource(R.string.dev_serial), ltr = true)
        Row {
            NumberField(f.health, { v -> v?.let { f = f.copy(health = it.coerceIn(0.0, 100.0)) } }, "${stringResource(R.string.dev_health)} (%)", Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            NumberField(f.efficiency, { v -> v?.let { f = f.copy(efficiency = it.coerceIn(0.0, 100.0)) } }, "${stringResource(R.string.dev_efficiency)} (%)", Modifier.weight(1f))
        }
        Row {
            AppTextField(f.firmware, { f = f.copy(firmware = it) }, stringResource(R.string.dev_firmware), Modifier.weight(1f), ltr = true)
            Spacer(Modifier.weight(0.04f))
            DateField(f.installedAt, { f = f.copy(installedAt = it) }, stringResource(R.string.sites_installDate), Modifier.weight(1f))
        }
    }
}
