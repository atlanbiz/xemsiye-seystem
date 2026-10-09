package com.solarpulse.app.ui.screens.alerts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.alerts.AlertEngine
import com.solarpulse.app.container
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.FormSheet
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.InfoBanner
import com.solarpulse.app.ui.components.NumberField
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatTile
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.description
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.Links
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.tint
import com.solarpulse.core.alerts.Alerts
import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.AlertSeverity
import com.solarpulse.core.model.Database
import com.solarpulse.core.time.Days
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen() {
    val context = LocalContext.current
    val c = context.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var editing by remember { mutableStateOf<AlertRule?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AlertRule?>(null) }
    var checking by remember { mutableStateOf(false) }
    val d = db ?: return
    val minute = now / 60_000
    val matching = remember(d, minute) { d.alertRules.associate { it.id to Alerts.matchRule(it, d, c.sim, now).size } }
    val ruleNames = d.alertRules.map { it.name }.toSet()
    val recent = d.notifications.filter { it.title in ruleNames }.take(12)
    val firedToday = recent.count { (Days.parseMillis(it.createdAt) ?: 0L) > now - 24 * 3600_000L }
    val checkedTemplate = stringResource(R.string.alerts_checked, "%s")
    val saved = stringResource(R.string.common_saved)
    val deletedMsg = stringResource(R.string.common_deleted)

    ScreenScaffold(
        title = stringResource(R.string.alerts_title),
        subtitle = stringResource(R.string.alerts_subtitle),
        showBack = true,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.alerts_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.AutoMirrored.Rounded.Rule, stringResource(R.string.alerts_activeRules), d.alertRules.count { it.enabled }.toString(), Brand.Blue500, Modifier.weight(1f))
                    StatTile(Icons.Rounded.Warning, stringResource(R.string.alerts_firing), d.alertRules.count { it.enabled && (matching[it.id] ?: 0) > 0 }.toString(), Brand.Red, Modifier.weight(1f))
                    StatTile(Icons.Rounded.NotificationsActive, stringResource(R.string.alerts_firedToday), firedToday.toString(), Brand.Amber, Modifier.weight(1f))
                }
            }
            item {
                InfoBanner(
                    Icons.Rounded.Info,
                    stringResource(if (c.isDemo) R.string.alerts_demoHintAndroid else R.string.alerts_serverHint),
                    MaterialTheme.colorScheme.primary,
                    action = if (c.isDemo) {
                        {
                            TextButton(onClick = {
                                checking = true
                                haptic()
                                scope.launch {
                                    val n = runCatching { c.alerts.evaluate() }.getOrDefault(0)
                                    checking = false
                                    snackbar.showSnackbar(checkedTemplate.replace("%s", n.toString()))
                                }
                            }, enabled = !checking) {
                                if (checking) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.alerts_checkNow))
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item { SectionHeader(stringResource(R.string.alerts_rules)) }
            if (d.alertRules.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.Rule, stringResource(R.string.alerts_empty)) }
            items(d.alertRules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    db = d,
                    now = now,
                    matching = matching[rule.id] ?: 0,
                    modifier = Modifier.animateItem(),
                    onToggle = { on -> scope.launch { repo.upsertAlertRules(listOf(rule.copy(enabled = on))) } },
                    onClick = { editing = rule },
                )
            }
            item { SectionHeader(stringResource(R.string.alerts_recent)) }
            if (recent.isEmpty()) item { EmptyState(Icons.Rounded.NotificationsNone, stringResource(R.string.alerts_recentEmpty)) }
            items(recent, key = { "n-" + it.id }) { n ->
                SpCard(Modifier.fillMaxWidth(), onClick = { Links.route(n.link)?.let(app.navigate) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.NotificationsActive, n.kind.tint, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall)
                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(fmt.ago(n.createdAt, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        RuleFormSheet(
            initial = editing,
            db = d,
            onDismiss = {
                creating = false
                editing = null
            },
            onDelete = { r ->
                creating = false
                editing = null
                deleting = r
            },
        ) { r ->
            creating = false
            editing = null
            scope.launch {
                repo.upsertAlertRules(listOf(r))
                snackbar.showSnackbar(saved)
            }
        }
    }
    deleting?.let { r ->
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            onConfirm = {
                scope.launch {
                    repo.removeAlertRules(listOf(r.id))
                    snackbar.showSnackbar(deletedMsg)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun unitText(metric: AlertMetric): String? {
    val ctx = LocalContext.current
    return remember(metric) { AlertEngine.unitLabel(ctx, metric) }
}

@Composable
private fun RuleCard(rule: AlertRule, db: Database, now: Long, matching: Int, modifier: Modifier, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val fmt = LocalFmt.current
    val unit = unitText(rule.metric)
    SpCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val threshold = if (rule.metric.hasThreshold) {
                    " · ${fmt.num(rule.threshold, if (rule.metric == AlertMetric.SITE_YIELD_BELOW) 2 else 0)}${if (unit == "%") "" else " "}${unit.orEmpty()}"
                } else {
                    ""
                }
                Text(
                    stringResource(rule.metric.label) + threshold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(stringResource(rule.severity.label), rule.severity.color)
            Text(
                "${stringResource(R.string.alerts_scope)}: ${rule.siteId?.let { db.siteName(it) } ?: stringResource(R.string.alerts_allSites)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        val last = Days.parseMillis(rule.lastTriggeredAt)
        val lastText = rule.lastTriggeredAt?.let { fmt.ago(it, now) } ?: stringResource(R.string.alerts_never)
        Text(
            buildString {
                append("${stringResource(R.string.alerts_lastTriggered)}: $lastText")
                if (last != null && now - last < Alerts.COOLDOWN_MS) {
                    append(" · ")
                    append(stringResource(R.string.alerts_cooldown, fmt.time(last + Alerts.COOLDOWN_MS)))
                }
                if (rule.enabled && matching > 0) {
                    append(" · ")
                    append(stringResource(R.string.alerts_matching, matching.toString()))
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (rule.enabled && matching > 0) rule.severity.color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuleFormSheet(initial: AlertRule?, db: Database, onDismiss: () -> Unit, onDelete: (AlertRule) -> Unit, onSave: (AlertRule) -> Unit) {
    var f by remember {
        mutableStateOf(
            initial ?: AlertRule(
                id = DataRepository.newId("rule-"),
                name = "",
                metric = AlertMetric.SITE_OFFLINE,
                threshold = 0.0,
                siteId = null,
                severity = AlertSeverity.WARNING,
                enabled = true,
                lastTriggeredAt = null,
                createdAt = Days.iso(System.currentTimeMillis()),
            ),
        )
    }
    var showErrors by remember { mutableStateOf(false) }
    val required = stringResource(R.string.common_required)
    val unit = unitText(f.metric)
    FormSheet(
        title = stringResource(if (initial == null) R.string.alerts_add else R.string.alerts_edit),
        onDismiss = onDismiss,
        onSave = {
            showErrors = true
            if (f.name.isNotBlank()) onSave(f.copy(name = f.name.trim()))
        },
        extraAction = if (initial != null) {
            { TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } }
        } else {
            null
        },
    ) {
        AppTextField(f.name, { f = f.copy(name = it) }, stringResource(R.string.alerts_name), error = if (showErrors && f.name.isBlank()) required else null)
        SelectField(stringResource(R.string.alerts_metric), AlertMetric.entries.map { it to stringResource(it.label) }, f.metric, { f = f.copy(metric = it) })
        Text(stringResource(f.metric.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (f.metric.hasThreshold) {
            androidx.compose.runtime.key(f.metric) {
                NumberField(f.threshold, { v -> v?.let { f = f.copy(threshold = it) } }, "${stringResource(R.string.alerts_threshold)}${unit?.let { " ($it)" }.orEmpty()}")
            }
        } else {
            Text(stringResource(R.string.alerts_noThreshold), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SelectField(
            stringResource(R.string.alerts_scope),
            listOf<Pair<String?, String>>(null to stringResource(R.string.alerts_allSites)) + db.sites.map { it.id to it.name },
            f.siteId,
            { f = f.copy(siteId = it) },
        )
        SelectField(stringResource(R.string.alerts_severity), AlertSeverity.entries.map { it to stringResource(it.label) }, f.severity, { f = f.copy(severity = it) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alerts_enabled), modifier = Modifier.weight(1f))
            Switch(checked = f.enabled, onCheckedChange = { f = f.copy(enabled = it) })
        }
    }
}
