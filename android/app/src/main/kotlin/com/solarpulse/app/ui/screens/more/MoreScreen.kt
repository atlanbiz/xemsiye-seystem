package com.solarpulse.app.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.nav.AlertsRoute
import com.solarpulse.app.ui.nav.AnalyticsRoute
import com.solarpulse.app.ui.nav.BillingRoute
import com.solarpulse.app.ui.nav.FinanceRoute
import com.solarpulse.app.ui.nav.Links
import com.solarpulse.app.ui.nav.MaintenanceRoute
import com.solarpulse.app.ui.nav.ReportsRoute
import com.solarpulse.app.ui.nav.SettingsRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.tint
import com.solarpulse.core.model.NotificationKind
import kotlinx.coroutines.launch

private data class Tile(val route: Any, val title: Int, val subtitle: Int, val icon: ImageVector, val tint: Color)

/** Hub for the sections that don't fit in the bottom bar. */
@Composable
fun MoreScreen() {
    val app = LocalAppActions.current
    val tiles = listOf(
        Tile(MaintenanceRoute(), R.string.nav_maintenance, R.string.mt_subtitle, Icons.Rounded.Engineering, Brand.Violet),
        Tile(BillingRoute(), R.string.nav_billing, R.string.bill_subtitle, Icons.AutoMirrored.Rounded.ReceiptLong, Brand.Green),
        Tile(AnalyticsRoute, R.string.nav_analytics, R.string.an_subtitle, Icons.Rounded.Insights, Brand.Blue500),
        Tile(ReportsRoute, R.string.nav_reports, R.string.rep_subtitle, Icons.Rounded.Description, Brand.Sky),
        Tile(FinanceRoute(), R.string.nav_finance, R.string.fin_subtitle, Icons.Rounded.Savings, Brand.Amber),
        Tile(AlertsRoute, R.string.nav_alerts, R.string.alerts_subtitle, Icons.Rounded.NotificationsActive, Brand.Red),
        Tile(SettingsRoute, R.string.nav_settings, R.string.set_subtitle, Icons.Rounded.Settings, Brand.Slate500),
    )
    ScreenScaffold(title = stringResource(R.string.nav_more)) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles) { t ->
                SpCard(onClick = { app.navigate(t.route) }) {
                    IconBadge(t.icon, t.tint, size = 44.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(t.subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** In-app notifications (web bell menu): tap opens the linked screen. */
@Composable
fun NotificationsScreen() {
    val c = LocalContext.current.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val scope = rememberCoroutineScope()
    val list = db?.notifications.orEmpty()
    ScreenScaffold(
        title = stringResource(R.string.ntf_title),
        showBack = true,
        actions = {
            IconButton(onClick = { scope.launch { repo.markAllRead() } }) {
                Icon(Icons.Rounded.DoneAll, contentDescription = stringResource(R.string.ntf_markAll))
            }
            IconButton(onClick = { scope.launch { repo.removeNotifications(list.map { it.id }) } }) {
                Icon(Icons.Rounded.ClearAll, contentDescription = stringResource(R.string.ntf_clear))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (list.isEmpty()) item { EmptyState(Icons.Rounded.NotificationsNone, stringResource(R.string.ntf_empty)) }
            items(list, key = { it.id }) { n ->
                val icon = when (n.kind) {
                    NotificationKind.DANGER -> Icons.Rounded.Error
                    NotificationKind.WARNING -> Icons.Rounded.Warning
                    NotificationKind.SUCCESS -> Icons.Rounded.CheckCircle
                    NotificationKind.INFO -> Icons.Rounded.Info
                }
                SpCard(
                    Modifier.animateItem(),
                    onClick = {
                        scope.launch { if (!n.read) repo.upsertNotifications(listOf(n.copy(read = true))) }
                        Links.route(n.link)?.let(app.navigate)
                    },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(icon, n.kind.tint, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt.ago(n.createdAt, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!n.read) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}

