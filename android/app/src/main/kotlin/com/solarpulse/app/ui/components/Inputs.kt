package com.solarpulse.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solarpulse.app.R
import com.solarpulse.app.ui.LocalFmt
import com.solarpulse.core.time.Days
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Segmented pill selector (web `PillSelect` / `Segmented`). Scrolls horizontally when crowded. */
@Composable
fun <T> PillTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.surface else Color.Transparent, label = "pill")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "pillText")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(role = Role.Tab) {
                        if (!on) haptic()
                        onSelect(value)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}

/** Horizontal row of filter chips. */
@Composable
fun <T> ChipRow(
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, label) ->
            FilterChip(
                selected = isSelected(v),
                onClick = { onToggle(v) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = CircleShape,
            )
        }
    }
}

/** Read-only text field that opens a dropdown of [options]. */
@Composable
fun <T> SelectField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole field opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.DropdownList,
                ) { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(
                    text = { Text(l, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(v)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    ltr: Boolean = false,
    placeholder: String? = null,
    supporting: String? = null,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = if (ltr) MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr) else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Decimal input that keeps the user's text while typing and reports parsed values. */
@Composable
fun NumberField(
    value: Double,
    onValue: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var text by remember { mutableStateOf(trimNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val clean = t.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
            text = clean
            onValue(clean.toDoubleOrNull())
        },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

fun trimNumber(v: Double): String =
    if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else String.format(Locale.ROOT, "%s", v)

/** `YYYY-MM-DD` field with a Material date picker. */
@Composable
fun DateField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val fmt = LocalFmt.current
    val shown = runCatching { fmt.date(Days.parse(value)) }.getOrDefault(value)
    Box(modifier) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { open = true },
        )
    }
    if (open) {
        val initial = runCatching { Days.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onValue(Days.key(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    open = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = stringResource(R.string.common_search)) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clearSearch))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Confirmation dialog (web `ConfirmDialog`). */
@Composable
fun ConfirmDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
    detail: String? = null,
    confirmLabel: String = stringResource(if (danger) R.string.common_delete else R.string.common_confirm),
) {
    val haptic = rememberHaptic()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_confirm)) },
        text = {
            Column {
                Text(text)
                if (detail != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(detail, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic()
                    onConfirm()
                    onDismiss()
                },
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Bottom-sheet form with a title, scrollable fields and Cancel / Save — used for every
 * create/edit form (sites, devices, tickets, rules, integrations).
 */
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = stringResource(R.string.common_save),
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberHaptic()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (extraAction != null) extraAction()
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = {
                    haptic()
                    onSave()
                }) { Text(saveLabel) }
            }
        }
    }
}

/** Two fields side by side on wide screens, stacked on phones. */
@Composable
fun FieldRow(content: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        content(Modifier.weight(1f))
    }
}

/** Today as a `LocalDate` in the device zone. */
fun todayLocal(): LocalDate = LocalDate.now()
