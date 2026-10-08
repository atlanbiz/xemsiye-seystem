package com.solarpulse.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.color
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.EmptyState
import com.solarpulse.app.ui.components.FormSheet
import com.solarpulse.app.ui.components.IconBadge
import com.solarpulse.app.ui.components.InfoBanner
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.StatusPill
import com.solarpulse.app.ui.copyToClipboard
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Integration
import com.solarpulse.core.model.IntegrationStatus
import com.solarpulse.core.model.IntegrationVendor
import com.solarpulse.core.time.Days
import kotlinx.coroutines.launch

/** FusionSolar northbound API default (PLATFORM.md §3.4). */
const val FS_DEFAULT_BASE = "https://eu5.fusionsolar.huawei.com"

private val IntegrationVendor.icon: ImageVector
    get() = when (this) {
        IntegrationVendor.SOLAREDGE -> Icons.Rounded.WbSunny
        IntegrationVendor.FUSIONSOLAR -> Icons.Rounded.Cloud
        IntegrationVendor.WEBHOOK -> Icons.Rounded.Webhook
    }

/** Secret key per vendor; secrets are written only via rpc('set_integration_secret'). */
private fun IntegrationVendor.secretKey(): String? = when (this) {
    IntegrationVendor.SOLAREDGE -> "api_key"
    IntegrationVendor.FUSIONSOLAR -> "system_code"
    IntegrationVendor.WEBHOOK -> null
}

@Composable
fun IntegrationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val now by c.ticker.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Integration?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Integration?>(null) }
    val d = db ?: return
    val savedMsg = stringResource(R.string.int_saved)
    val failedTemplate = stringResource(R.string.int_secretFailed, "%s")
    val deletedMsg = stringResource(R.string.common_deleted)
    val copied = stringResource(R.string.common_copied)
    val copyFailed = stringResource(R.string.common_copyFailed)
    val copy: (String) -> Unit = { text ->
        val ok = copyToClipboard(context, "SolarPulse", text)
        scope.launch { snackbar.showSnackbar(if (ok) copied else copyFailed) }
    }

    ScreenScaffold(
        title = stringResource(R.string.int_title),
        subtitle = stringResource(R.string.int_subtitle),
        showBack = true,
        floatingActionButton = {
            if (d.sites.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.int_add)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (c.isDemo) item { InfoBanner(Icons.Rounded.Info, stringResource(R.string.int_demoNote), SolarTheme.colors.warning) }
            if (d.integrations.isEmpty()) item { EmptyState(Icons.Rounded.Hub, stringResource(R.string.int_empty)) }
            items(d.integrations, key = { it.id }) { x ->
                SpCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(x.vendor.icon, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(x.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(6.dp))
                                StatusPill(stringResource(x.status.label), x.status.color)
                            }
                            Text(
                                listOfNotNull(stringResource(x.vendor.label), d.siteName(x.siteId), x.externalId.ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${stringResource(R.string.int_lastSync)}: ${x.lastSyncAt?.let { fmt.ago(it, now) } ?: stringResource(R.string.alerts_never)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = x }) { Icon(Icons.Rounded.Edit, stringResource(R.string.common_edit)) }
                        IconButton(onClick = { deleting = x }) { Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete)) }
                    }
                    x.lastError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(Icons.Rounded.Error, "${stringResource(R.string.int_lastError)}: $err", SolarTheme.colors.danger)
                    }
                    if (x.vendor == IntegrationVendor.WEBHOOK) {
                        Spacer(Modifier.height(8.dp))
                        CopyRow(stringResource(R.string.int_ingestUrl), c.ingestUrl, copy)
                        Spacer(Modifier.height(6.dp))
                        CopyRow(stringResource(R.string.int_token), x.ingestToken, copy)
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val initial = editing
        IntegrationFormSheet(
            initial = initial,
            db = d,
            isDemo = c.isDemo,
            ingestUrl = c.ingestUrl,
            copy = copy,
            onDismiss = {
                creating = false
                editing = null
            },
        ) { next, secret ->
            creating = false
            editing = null
            scope.launch {
                repo.upsertIntegration(next)
                val key = next.vendor.secretKey()
                if (key != null && secret.isNotBlank()) {
                    val err = runCatching { repo.setIntegrationSecret(next.id, mapOf(key to secret.trim())) }.exceptionOrNull()
                    if (err != null) {
                        snackbar.showSnackbar(failedTemplate.replace("%s", err.message ?: err.toString()))
                        return@launch
                    }
                }
                snackbar.showSnackbar(savedMsg)
            }
        }
    }
    deleting?.let { x ->
        ConfirmDialog(
            text = stringResource(R.string.common_deleteConfirm),
            onConfirm = {
                scope.launch {
                    repo.removeIntegration(listOf(x.id))
                    snackbar.showSnackbar(deletedMsg)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Monospace value with a copy button (ingest URL, token). */
@Composable
private fun CopyRow(label: String, value: String, copy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copy(value) }) { Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.common_copy)) }
        }
    }
}

@Composable
private fun IntegrationFormSheet(
    initial: Integration?,
    db: Database,
    isDemo: Boolean,
    ingestUrl: String,
    copy: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Integration, String) -> Unit,
) {
    val isNew = initial == null
    var f by remember {
        mutableStateOf(
            initial ?: Integration(
                id = DataRepository.newId("int-"),
                vendor = IntegrationVendor.SOLAREDGE,
                name = "",
                siteId = db.sites.firstOrNull()?.id.orEmpty(),
                externalId = "",
                config = emptyMap(),
                ingestToken = DataRepository.newToken(),
                status = IntegrationStatus.PENDING,
                createdAt = Days.iso(System.currentTimeMillis()),
            ),
        )
    }
    var secret by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val required = stringResource(R.string.common_required)
    fun err(bad: Boolean) = if (showErrors && bad) required else null
    val secretKey = f.vendor.secretKey()
    val baseUrl = f.config["base_url"].orEmpty()
    val username = f.config["username"].orEmpty()

    FormSheet(
        title = stringResource(if (isNew) R.string.int_add else R.string.int_edit),
        onDismiss = onDismiss,
        onSave = {
            showErrors = true
            val ok = f.name.isNotBlank() && f.siteId.isNotBlank() &&
                (f.vendor == IntegrationVendor.WEBHOOK || f.externalId.isNotBlank()) &&
                (f.vendor != IntegrationVendor.FUSIONSOLAR || username.isNotBlank()) &&
                !(!isDemo && isNew && secretKey != null && secret.isBlank())
            if (ok) {
                val config = if (f.vendor == IntegrationVendor.FUSIONSOLAR) {
                    mapOf("base_url" to baseUrl.ifBlank { FS_DEFAULT_BASE }.trimEnd('/'), "username" to username.trim())
                } else {
                    emptyMap()
                }
                var next = f.copy(
                    name = f.name.trim(),
                    externalId = if (f.vendor == IntegrationVendor.WEBHOOK) "" else f.externalId.trim(),
                    config = config,
                )
                if (secret.isNotBlank()) next = next.copy(status = IntegrationStatus.PENDING, lastError = null)
                onSave(next, secret)
            }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegrationVendor.entries.forEach { v ->
                val on = v == f.vendor
                androidx.compose.material3.FilterChip(
                    selected = on,
                    onClick = { f = f.copy(vendor = v) },
                    label = { Text(stringResource(v.label)) },
                    leadingIcon = { Icon(v.icon, contentDescription = null) },
                )
            }
        }
        AppTextField(f.name, { f = f.copy(name = it) }, stringResource(R.string.int_name), error = err(f.name.isBlank()))
        SelectField(stringResource(R.string.common_site), db.sites.map { it.id to it.name }, f.siteId, { f = f.copy(siteId = it) }, error = err(f.siteId.isBlank()))
        when (f.vendor) {
            IntegrationVendor.SOLAREDGE -> {
                AppTextField(f.externalId, { f = f.copy(externalId = it) }, stringResource(R.string.int_seSiteId), error = err(f.externalId.isBlank()), ltr = true)
            }
            IntegrationVendor.FUSIONSOLAR -> {
                AppTextField(baseUrl, { f = f.copy(config = f.config + ("base_url" to it)) }, stringResource(R.string.int_fsBaseUrl), ltr = true, placeholder = FS_DEFAULT_BASE)
                AppTextField(username, { f = f.copy(config = f.config + ("username" to it)) }, stringResource(R.string.int_fsUser), error = err(username.isBlank()), ltr = true)
                AppTextField(f.externalId, { f = f.copy(externalId = it) }, stringResource(R.string.int_fsStation), error = err(f.externalId.isBlank()), ltr = true)
            }
            IntegrationVendor.WEBHOOK -> {
                Text(stringResource(R.string.int_webhookHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CopyRow(stringResource(R.string.int_ingestUrl), ingestUrl, copy)
                CopyRow(stringResource(R.string.int_token), f.ingestToken, copy)
                Text(stringResource(R.string.int_example), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "curl -X POST '$ingestUrl' \\\n  -H 'x-ingest-token: ${f.ingestToken}' \\\n  -H 'content-type: application/json' \\\n" +
                        "  -d '{\"powerKw\": 42.5, \"energyKwh\": 180.2,\n       \"devices\": [{\"serial\": \"SN123\", \"status\": \"online\", \"efficiency\": 97.1}]}'",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, textDirection = TextDirection.Ltr),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brand.Slate900)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
        if (secretKey != null) {
            AppTextField(
                secret,
                { secret = it },
                stringResource(if (f.vendor == IntegrationVendor.SOLAREDGE) R.string.int_apiKey else R.string.int_fsSystemCode),
                error = err(!isDemo && isNew && secret.isBlank()),
                ltr = true,
                placeholder = if (!isNew) stringResource(R.string.int_secretKeep) else null,
                password = true,
            )
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.padding(top = 2.dp).width(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (isDemo) R.string.int_demoNote else R.string.int_secretNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
