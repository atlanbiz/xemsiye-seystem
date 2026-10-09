package com.solarpulse.app.ui.screens.maintenance

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.ChipRow
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.DateField
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.FormSheet
import com.solarpulse.app.ui.components.PillTabs
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SearchField
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.next
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.model.TicketPriority
import com.solarpulse.core.model.TicketStatus
import com.solarpulse.core.time.Days
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MaintenanceScreen(siteFilter: String?, openId: String?) {
    val c = LocalContext.current.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var tab by rememberSaveable { mutableStateOf(TicketStatus.OPEN) }
    var query by rememberSaveable { mutableStateOf("") }
    var prio by rememberSaveable { mutableStateOf<TicketPriority?>(null) }
    var site by rememberSaveable { mutableStateOf(siteFilter) }
    var editing by remember { mutableStateOf<Ticket?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Ticket?>(null) }
    var handledOpen by rememberSaveable { mutableStateOf(false) }

    val d = db ?: return
    LaunchedEffect(openId, d.tickets) {
        if (openId != null && !handledOpen) {
            d.tickets.firstOrNull { it.id == openId }?.let {
                editing = it
                tab = it.status
                handledOpen = true
            }
        }
    }
    val today = Days.key(LocalDate.now())
    val inWeek = Days.key(LocalDate.now().plusDays(7))
    val filtered = remember(d.tickets, query, prio, site) {
        val q = query.trim().lowercase()
        d.tickets
            .filter { (prio == null || it.priority == prio) && (site == null || it.siteId == site) }
            .filter { t -> q.isEmpty() || listOf(t.title, t.description, t.assignee, d.siteName(t.siteId)).any { it.lowercase().contains(q) } }
            .sortedWith(compareBy<Ticket> { it.priority.order }.thenBy { it.dueDate })
    }
    val rows = filtered.filter { it.status == tab }
    val overdue = d.tickets.count { it.status != TicketStatus.RESOLVED && it.dueDate < today }
    val upcoming = d.tickets.count { it.status != TicketStatus.RESOLVED && it.dueDate >= today && it.dueDate <= inWeek }
    val statusWords = TicketStatus.entries.associateWith { stringResource(it.label) }
    val saved = stringResource(R.string.common_saved)
    val created = stringResource(R.string.mt_created)
    val deletedMsg = stringResource(R.string.common_deleted)

    fun move(t: Ticket, to: TicketStatus) {
        if (t.status == to) return
        haptic()
        scope.launch {
            repo.upsertTicket(t.copy(status = to))
            snackbar.showSnackbar("${t.title} → ${statusWords.getValue(to)}")
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.mt_title),
        subtitle = stringResource(R.string.mt_subtitle),
        showBack = true,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.mt_add)) },
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
                        StatTile(Icons.Rounded.PendingActions, stringResource(R.string.mt_openCount), d.tickets.count { it.status == TicketStatus.OPEN }.toString(), Brand.Blue500, Modifier.weight(1f))
                        StatTile(Icons.Rounded.HourglassTop, stringResource(R.string.mt_inProgress), d.tickets.count { it.status == TicketStatus.IN_PROGRESS }.toString(), Brand.Violet, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.Rounded.Error, stringResource(R.string.mt_overdue), overdue.toString(), Brand.Red, Modifier.weight(1f))
                        StatTile(Icons.Rounded.Schedule, stringResource(R.string.mt_upcoming), upcoming.toString(), Brand.Green, Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(query, { query = it })
                    SelectField(
                        stringResource(R.string.common_site),
                        listOf<Pair<String?, String>>(null to stringResource(R.string.an_allSites)) + d.sites.map { it.id to it.name },
                        site,
                        { site = it },
                    )
                    ChipRow(
                        TicketPriority.entries.sortedBy { it.order }.map { it to stringResource(it.label) },
                        { it == prio },
                        { prio = if (prio == it) null else it },
                    )
                    PillTabs(
                        TicketStatus.entries.map { st -> st to "${statusWords.getValue(st)} ${filtered.count { it.status == st }}" },
                        tab,
                        { tab = it },
                    )
                    Text(stringResource(R.string.mt_swipeHint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (rows.isEmpty()) item { EmptyState(Icons.Rounded.Engineering, stringResource(R.string.common_noData)) }
            items(rows, key = { it.id }) { t ->
                SwipeableTicket(
                    t = t,
                    db = d,
                    today = today,
                    modifier = Modifier.animateItem(),
                    onClick = { editing = t },
                    onAdvance = { t.status.next?.let { move(t, it) } },
                    onDelete = { deleting = t },
                    fmtDate = { fmt.date(it) },
                )
            }
        }
    }

    if (creating || editing != null) {
        TicketFormSheet(
            initial = editing,
            db = d,
            onDismiss = {
                creating = false
                editing = null
            },
            onDelete = { t ->
                creating = false
                editing = null
                deleting = t
            },
        ) { t ->
            val isNew = d.tickets.none { it.id == t.id }
            creating = false
            editing = null
            scope.launch {
                repo.upsertTicket(t)
                if (isNew && d.settings.notifyMaintenance) {
                    repo.notify(created, "${t.title} · ${d.siteName(t.siteId)}", if (t.priority == TicketPriority.CRITICAL) NotificationKind.DANGER else NotificationKind.INFO, "/maintenance?open=${t.id}")
                }
                snackbar.showSnackbar(if (isNew) created else saved)
            }
        }
    }
    deleting?.let { t ->
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            onConfirm = {
                scope.launch {
                    repo.removeTickets(listOf(t.id))
                    snackbar.showSnackbar(deletedMsg)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Swipe start→end advances the status, end→start asks to delete. */
@Composable
private fun SwipeableTicket(
    t: Ticket,
    db: Database,
    today: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onAdvance: () -> Unit,
    onDelete: () -> Unit,
    fmtDate: (String) -> String,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> onAdvance()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // always snap back; the list updates from the data
        },
    )
    val next = t.status.next
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = next != null,
        backgroundContent = {
            val dir = state.dismissDirection
            val color = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> next?.color ?: Color.Transparent
                SwipeToDismissBoxValue.EndToStart -> SolarTheme.colors.danger
                else -> Color.Transparent
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(color.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> if (next != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.mt_moveTo, stringResource(next.label)), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = Color.White)
                    else -> Unit
                }
            }
        },
    ) {
        SpCard(Modifier.fillMaxWidth(), onClick = onClick) {
            Row(verticalAlignment = Alignment.Top) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                StatusPill(stringResource(t.priority.label), t.priority.color)
            }
            if (t.description.isNotBlank()) {
                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(db.siteName(t.siteId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val late = t.status != TicketStatus.RESOLVED && t.dueDate < today
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (late) SolarTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(fmtDate(t.dueDate), style = MaterialTheme.typography.labelSmall, color = if (late) SolarTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (t.assignee.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(t.assignee, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TicketFormSheet(initial: Ticket?, db: Database, onDismiss: () -> Unit, onDelete: (Ticket) -> Unit, onSave: (Ticket) -> Unit) {
    var f by remember {
        mutableStateOf(
            initial ?: Ticket(
                id = DataRepository.newId("tkt-"),
                siteId = db.sites.firstOrNull()?.id.orEmpty(),
                deviceId = null,
                title = "",
                description = "",
                priority = TicketPriority.MEDIUM,
                status = TicketStatus.OPEN,
                assignee = "",
                dueDate = Days.key(LocalDate.now().plusDays(3)),
                createdAt = Days.iso(System.currentTimeMillis()),
            ),
        )
    }
    var showErrors by remember { mutableStateOf(false) }
    val required = stringResource(R.string.common_required)
    fun err(bad: Boolean) = if (showErrors && bad) required else null
    val devices = db.devices.filter { it.siteId == f.siteId }
    FormSheet(
        title = stringResource(if (initial == null) R.string.mt_add else R.string.mt_edit),
        onDismiss = onDismiss,
        onSave = {
            showErrors = true
            if (f.title.isNotBlank() && f.siteId.isNotBlank() && f.dueDate.isNotBlank()) onSave(f.copy(title = f.title.trim()))
        },
        extraAction = if (initial != null) {
            {
                TextButton(onClick = { onDelete(initial) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            null
        },
    ) {
        AppTextField(f.title, { f = f.copy(title = it) }, stringResource(R.string.mt_ticketTitle), error = err(f.title.isBlank()))
        AppTextField(f.description, { f = f.copy(description = it) }, stringResource(R.string.common_description), singleLine = false, minLines = 3)
        SelectField(stringResource(R.string.common_site), db.sites.map { it.id to it.name }, f.siteId, { f = f.copy(siteId = it, deviceId = null) }, error = err(f.siteId.isBlank()))
        SelectField(
            stringResource(R.string.mt_device),
            listOf<Pair<String?, String>>(null to stringResource(R.string.common_none)) + devices.map { it.id to it.name },
            f.deviceId,
            { f = f.copy(deviceId = it) },
        )
        Row {
            SelectField(stringResource(R.string.mt_priority), TicketPriority.entries.map { it to stringResource(it.label) }, f.priority, { f = f.copy(priority = it) }, Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            SelectField(stringResource(R.string.common_status), TicketStatus.entries.map { it to stringResource(it.label) }, f.status, { f = f.copy(status = it) }, Modifier.weight(1f))
        }
        Row {
            AppTextField(f.assignee, { f = f.copy(assignee = it) }, stringResource(R.string.mt_assignee), Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            DateField(f.dueDate, { f = f.copy(dueDate = it) }, stringResource(R.string.mt_due), Modifier.weight(1f))
        }
    }
}
