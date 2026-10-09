package com.solarpulse.app.ui.screens.sites

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solarpulse.app.R
import com.solarpulse.app.data.DataRepository
import com.solarpulse.app.ui.components.AppTextField
import com.solarpulse.app.ui.components.DateField
import com.solarpulse.app.ui.components.FormSheet
import com.solarpulse.app.ui.components.NumberField
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.label
import com.solarpulse.core.model.FinanceDefaults
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.SiteType
import com.solarpulse.core.time.Days
import java.time.LocalDate

/** New site defaults (web `SiteForm` empty()). */
fun blankSite(): Site {
    val cost = FinanceDefaults.systemCost(50.0)
    return Site(
        id = DataRepository.newId("site-"),
        name = "",
        location = "",
        type = SiteType.COMMERCIAL,
        status = SiteStatus.ACTIVE,
        capacityKw = 50.0,
        batteryKwh = 0.0,
        pricePerKwh = 0.12,
        customer = "",
        installDate = Days.key(LocalDate.now()),
        lat = 43.82,
        lng = 87.61,
        systemCost = cost,
        annualOpex = FinanceDefaults.annualOpex(cost),
    )
}

/** Create / edit a site, including the ROI fields (PLATFORM.md §1). */
@Composable
fun SiteFormSheet(initial: Site?, onDismiss: () -> Unit, onSave: (Site) -> Unit) {
    var f by remember { mutableStateOf(initial ?: blankSite()) }
    var showErrors by remember { mutableStateOf(false) }
    // bump to re-create number fields after "Use defaults"
    var defaultsVersion by remember { mutableStateOf(0) }
    val required = stringResource(R.string.common_required)
    fun err(bad: Boolean) = if (showErrors && bad) required else null

    FormSheet(
        title = stringResource(if (initial == null) R.string.sites_add else R.string.sites_edit),
        onDismiss = onDismiss,
        onSave = {
            showErrors = true
            val ok = f.name.isNotBlank() && f.location.isNotBlank() && f.customer.isNotBlank() && f.capacityKw > 0
            if (ok) onSave(f.copy(name = f.name.trim(), location = f.location.trim(), customer = f.customer.trim()))
        },
    ) {
        AppTextField(f.name, { f = f.copy(name = it) }, stringResource(R.string.common_name), error = err(f.name.isBlank()))
        Row {
            AppTextField(f.location, { f = f.copy(location = it) }, stringResource(R.string.common_location), Modifier.weight(1f), error = err(f.location.isBlank()))
            Spacer(Modifier.height(0.dp).weight(0.04f))
            AppTextField(f.customer, { f = f.copy(customer = it) }, stringResource(R.string.sites_customer), Modifier.weight(1f), error = err(f.customer.isBlank()))
        }
        Row {
            SelectField(
                stringResource(R.string.common_type),
                SiteType.entries.map { it to stringResource(it.label) },
                f.type,
                { f = f.copy(type = it) },
                Modifier.weight(1f),
            )
            Spacer(Modifier.weight(0.04f))
            SelectField(
                stringResource(R.string.common_status),
                SiteStatus.entries.map { it to stringResource(it.label) },
                f.status,
                { f = f.copy(status = it) },
                Modifier.weight(1f),
            )
        }
        Row {
            NumberField(f.capacityKw, { v -> v?.let { f = f.copy(capacityKw = it) } }, stringResource(R.string.sites_capacity), Modifier.weight(1f), error = err(!(f.capacityKw > 0)))
            Spacer(Modifier.weight(0.04f))
            NumberField(f.batteryKwh, { v -> v?.let { f = f.copy(batteryKwh = it) } }, stringResource(R.string.sites_battery), Modifier.weight(1f))
        }
        Row {
            NumberField(f.pricePerKwh, { v -> v?.let { f = f.copy(pricePerKwh = it) } }, stringResource(R.string.sites_price), Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            DateField(f.installDate, { f = f.copy(installDate = it) }, stringResource(R.string.sites_installDate), Modifier.weight(1f))
        }
        Row {
            NumberField(f.lat, { v -> v?.let { f = f.copy(lat = it) } }, stringResource(R.string.sites_lat), Modifier.weight(1f))
            Spacer(Modifier.weight(0.04f))
            NumberField(f.lng, { v -> v?.let { f = f.copy(lng = it) } }, stringResource(R.string.sites_lng), Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sites_financials), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val cost = FinanceDefaults.systemCost(f.capacityKw)
                f = f.copy(
                    systemCost = cost,
                    annualOpex = FinanceDefaults.annualOpex(cost),
                    degradationPct = FinanceDefaults.DEGRADATION_PCT,
                    tariffEscalationPct = FinanceDefaults.TARIFF_ESCALATION_PCT,
                )
                defaultsVersion++
            }) { Text(stringResource(R.string.sites_useDefaults)) }
        }
        androidx.compose.runtime.key(defaultsVersion) {
            Row {
                NumberField(f.systemCost, { v -> v?.let { f = f.copy(systemCost = it) } }, stringResource(R.string.sites_systemCost), Modifier.weight(1f))
                Spacer(Modifier.weight(0.04f))
                NumberField(f.annualOpex, { v -> v?.let { f = f.copy(annualOpex = it) } }, stringResource(R.string.sites_annualOpex), Modifier.weight(1f))
            }
            Row {
                NumberField(f.degradationPct, { v -> v?.let { f = f.copy(degradationPct = it) } }, stringResource(R.string.sites_degradation), Modifier.weight(1f))
                Spacer(Modifier.weight(0.04f))
                NumberField(f.tariffEscalationPct, { v -> v?.let { f = f.copy(tariffEscalationPct = it) } }, stringResource(R.string.sites_escalation), Modifier.weight(1f))
            }
        }
    }
}
