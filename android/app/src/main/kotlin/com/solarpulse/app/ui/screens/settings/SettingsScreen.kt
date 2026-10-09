package com.solarpulse.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solarpulse.app.BuildConfig
import com.solarpulse.app.R
import com.solarpulse.app.alerts.AlertEngine
import com.solarpulse.app.container
import com.solarpulse.app.ui.LocalAppActions
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.app.ui.LocalSnackbar
import com.solarpulse.app.ui.applyAppLanguage
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.ConfirmDialog
import com.solarpulse.app.ui.components.InfoBanner
import com.solarpulse.app.ui.components.NumberField
import com.solarpulse.app.ui.components.PillTabs
import com.solarpulse.app.ui.components.ScreenScaffold
import com.solarpulse.app.ui.components.SectionHeader
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.initials
import com.solarpulse.app.ui.label
import com.solarpulse.app.ui.nav.AlertsRoute
import com.solarpulse.app.ui.nav.IntegrationsRoute
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Currency
import com.solarpulse.core.model.Lang
import com.solarpulse.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Asks once for POST_NOTIFICATIONS on Android 13+ (alert rules post system notifications). */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val c = context.container
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !c.prefs.askedNotifications.first()) {
            c.prefs.setAskedNotifications()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val repo = c.repository
    val db by repo.db.collectAsStateWithLifecycle()
    val dynamic by c.prefs.dynamicColor.collectAsStateWithLifecycle(false)
    val fmt = LocalFmt.current
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val d = db ?: return
    val s = d.settings
    var name by rememberSaveable(s.userName) { mutableStateOf(s.userName) }
    var email by rememberSaveable(s.email) { mutableStateOf(s.email) }
    var company by rememberSaveable(s.company) { mutableStateOf(s.company) }
    var role by rememberSaveable(s.role) { mutableStateOf(s.role) }
    var co2 by remember(s.co2KgPerKwh) { mutableStateOf<Double?>(s.co2KgPerKwh) }
    var tree by remember(s.treeKgPerYear) { mutableStateOf<Double?>(s.treeKgPerYear) }
    var car by remember(s.carTonsPerYear) { mutableStateOf<Double?>(s.carTonsPerYear) }
    var rate by remember(s.discountRatePct) { mutableStateOf<Double?>(s.discountRatePct) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val saved = stringResource(R.string.common_saved)
    val resetDone = stringResource(R.string.set_resetDone)
    val lang = fmt.lang
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }

    fun save(msg: String = saved, block: (com.solarpulse.core.model.Settings) -> com.solarpulse.core.model.Settings) {
        haptic()
        scope.launch {
            repo.updateSettings(block)
            snackbar.showSnackbar(msg)
        }
    }

    ScreenScaffold(title = stringResource(R.string.set_title), subtitle = stringResource(R.string.set_subtitle), showBack = true) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── profile ──
            item {
                SpCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFCD34D), Color(0xFFF97316)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials(name).ifBlank { "U" }, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(email, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(stringResource(R.string.set_profile))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(name, { name = it }, stringResource(R.string.set_fullName))
                        AppTextField(email, { email = it }, stringResource(R.string.set_email), ltr = true)
                        AppTextField(company, { company = it }, stringResource(R.string.set_company))
                        SelectField(
                            stringResource(R.string.set_role),
                            listOf("admin" to stringResource(R.string.role_admin), "operator" to stringResource(R.string.role_operator), "viewer" to stringResource(R.string.role_viewer)),
                            role,
                            { role = it },
                        )
                        Button(
                            onClick = { save { it.copy(userName = name.trim(), email = email.trim(), company = company, role = role) } },
                            enabled = name.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.common_save)) }
                    }
                }
            }
            // ── preferences ──
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.set_preferences))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectField(
                            stringResource(R.string.set_language),
                            Lang.entries.map { it to it.nativeName },
                            lang,
                            { l ->
                                scope.launch {
                                    repo.updateSettings { it.copy(language = l) }
                                    applyAppLanguage(l)
                                }
                            },
                        )
                        Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.labelMedium)
                        PillTabs(
                            ThemeMode.entries.map { it to stringResource(it.label) },
                            s.theme,
                            { t ->
                                scope.launch {
                                    c.prefs.setTheme(t)
                                    repo.updateSettings { it.copy(theme = t) }
                                }
                            },
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ToggleRow(stringResource(R.string.set_dynamicColor), stringResource(R.string.set_dynamicColorDesc), dynamic) { on ->
                                scope.launch { c.prefs.setDynamicColor(on) }
                            }
                        }
                        SelectField(
                            stringResource(R.string.set_currency),
                            listOf(Currency.USD to "USD ($)", Currency.CNY to "CNY (¥)", Currency.EUR to "EUR (€)"),
                            s.currency,
                            { cur -> scope.launch { repo.updateSettings { it.copy(currency = cur) } } },
                        )
                        SelectField(
                            stringResource(R.string.set_city),
                            (if (Fmt.CITIES.none { it.name == s.city }) listOf(s.city to s.city) else emptyList()) + Fmt.CITIES.map { it.name to it.label(lang) },
                            s.city,
                            { name ->
                                Fmt.CITIES.firstOrNull { it.name == name }?.let { city ->
                                    scope.launch { repo.updateSettings { it.copy(city = city.name, lat = city.lat, lng = city.lng) } }
                                }
                            },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberField(s.discountRatePct, { rate = it }, stringResource(R.string.set_discountRate), Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { rate?.let { r -> save { it.copy(discountRatePct = r) } } },
                                enabled = rate != null && rate != s.discountRatePct,
                            ) { Text(stringResource(R.string.common_save)) }
                        }
                    }
                }
            }
            // ── notifications ──
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.set_notifications))
                    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(Icons.Rounded.NotificationsActive, stringResource(R.string.set_notifyPermission), SolarTheme.colors.warning, action = {
                            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text(stringResource(R.string.set_notifyAllow))
                            }
                        })
                    }
                    ToggleRow(stringResource(R.string.set_notifyPush), null, s.notifyPush) { v -> scope.launch { repo.updateSettings { it.copy(notifyPush = v) } } }
                    ToggleRow(stringResource(R.string.set_notifyEmail), null, s.notifyEmail) { v -> scope.launch { repo.updateSettings { it.copy(notifyEmail = v) } } }
                    ToggleRow(stringResource(R.string.set_notifyDevice), null, s.notifyDeviceAlerts) { v -> scope.launch { repo.updateSettings { it.copy(notifyDeviceAlerts = v) } } }
                    ToggleRow(stringResource(R.string.set_notifyBilling), null, s.notifyBilling) { v -> scope.launch { repo.updateSettings { it.copy(notifyBilling = v) } } }
                    ToggleRow(stringResource(R.string.set_notifyMaintenance), null, s.notifyMaintenance) { v -> scope.launch { repo.updateSettings { it.copy(notifyMaintenance = v) } } }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    NavRow(Icons.Rounded.NotificationsActive, stringResource(R.string.alerts_title)) { app.navigate(AlertsRoute) }
                }
            }
            // ── impact factors ──
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.set_environment))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(s.co2KgPerKwh, { co2 = it }, stringResource(R.string.set_co2Factor))
                        NumberField(s.treeKgPerYear, { tree = it }, stringResource(R.string.set_treeFactor))
                        NumberField(s.carTonsPerYear, { car = it }, stringResource(R.string.set_carFactor))
                        val ok = (co2 ?: -1.0) >= 0 && (tree ?: 0.0) > 0 && (car ?: 0.0) > 0
                        Button(
                            onClick = { save { it.copy(co2KgPerKwh = co2!!, treeKgPerYear = tree!!, carTonsPerYear = car!!) } },
                            enabled = ok,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.common_save)) }
                    }
                }
            }
            // ── integrations + data ──
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.set_data))
                    NavRow(Icons.Rounded.Hub, stringResource(R.string.set_integrations)) { app.navigate(IntegrationsRoute) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    InfoBanner(
                        if (c.isDemo) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                        stringResource(if (c.isDemo) R.string.set_storage_device else R.string.set_storage_supabase),
                        if (c.isDemo) SolarTheme.colors.warning else SolarTheme.colors.success,
                    )
                    if (c.isDemo) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.set_supabaseHintAndroid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { resetting = true }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.set_reset))
                    }
                }
            }
            item {
                SpCard {
                    SectionHeader(stringResource(R.string.set_about))
                    Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { signingOut = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.user_logout))
                    }
                }
            }
        }
    }

    if (resetting) {
        ConfirmDialog(
            text = stringResource(R.string.set_resetConfirm),
            danger = false,
            onConfirm = {
                scope.launch {
                    repo.resetDemo()
                    snackbar.showSnackbar(resetDone)
                }
            },
            onDismiss = { resetting = false },
        )
    }
    if (signingOut) {
        ConfirmDialog(
            text = stringResource(R.string.user_logout) + "?",
            danger = true,
            confirmLabel = stringResource(R.string.user_logout),
            onConfirm = { scope.launch { c.auth.signOut() } },
            onDismiss = { signingOut = false },
        )
    }
    // keep the notification channel name in the current language
    LaunchedEffect(lang) { c.notifier.ensureChannel(AlertEngine.localizedContext(context, lang)) }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = Brand.Blue500)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}
