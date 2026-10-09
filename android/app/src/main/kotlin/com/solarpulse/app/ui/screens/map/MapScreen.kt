package com.solarpulse.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.Live
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.LiveBadge
import com.solarpulse.app.ui.components.OsmMap
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.components.pinRadiusDp
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.SiteDetailRoute
import com.solarpulse.app.ui.screens.sites.Metric
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus

/** Every site as a status-coloured pin sized by capacity; tap → bottom sheet → site detail. */
@Composable
fun MapScreen(focus: String?) {
    val c = LocalContext.current.container
    val db by c.repository.db.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val haptic = rememberHaptic()
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val sites = db?.sites.orEmpty()
    val visible = sites.filter { it.status.key !in hidden }

    ScreenScaffold(title = stringResource(R.string.map_title), subtitle = stringResource(R.string.map_subtitle)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                OsmMap(
                    sites = visible,
                    modifier = Modifier.fillMaxSize(),
                    selectedId = selectedId,
                    focusId = focus,
                    dark = SolarTheme.colors.isDark,
                    onSiteClick = {
                        haptic()
                        selectedId = it.id
                    },
                )
            }
            // Status filter + legend overlay
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SiteStatus.entries.forEach { st ->
                        val on = st.key !in hidden
                        val count = sites.count { it.status == st }
                        FilterChip(
                            selected = on,
                            onClick = { hidden = if (on) hidden + st.key else hidden - st.key },
                            label = { Text("${stringResource(st.label)} $count") },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(st.color)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.map_size),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    val selected = sites.firstOrNull { it.id == selectedId }
    if (selected != null) {
        SiteSheet(selected, onDismiss = { selectedId = null }) {
            selectedId = null
            app.navigate(SiteDetailRoute(selected.id))
        }
    }
}

/** Bottom sheet for a tapped pin; only this part follows the live 5-second clock. */
@Composable
private fun SiteSheet(selected: Site, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val readings by c.repository.readings.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kw = Live.siteKw(c.sim, selected, readings, now)
    val live = Live.reading(selected, readings, now) != null
    val kwh = remember(selected, now / 60_000) { c.sim.siteDayKwhCached(selected, c.sim.todayKey()) }
    run {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size((pinRadiusDp(selected.capacityKw) * 1.4f).dp).clip(CircleShape).background(selected.status.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${selected.location} · ${stringResource(selected.type.label)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (live) LiveBadge() else StatusPill(stringResource(selected.status.label), selected.status.color)
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Metric(stringResource(R.string.map_capacity), fmt.power(selected.capacityKw), Modifier.weight(1f))
                    Metric(stringResource(R.string.sites_currentPower), fmt.power(kw), Modifier.weight(1f))
                    Metric(stringResource(R.string.sites_energyToday), fmt.energy(kwh), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(R.string.map_openSite)) }
            }
        }
    }
}
