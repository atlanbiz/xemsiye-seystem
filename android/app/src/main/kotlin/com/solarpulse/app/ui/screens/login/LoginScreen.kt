package com.solarpulse.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.solarpulse.app.R
import com.solarpulse.app.container
import com.solarpulse.app.data.AuthRepository
import com.solarpulse.app.ui.applyAppLanguage
import com.solarpulse.app.ui.components.InfoBanner
import com.solarpulse.app.ui.components.SelectField
import com.solarpulse.app.ui.components.SpCard
import com.solarpulse.app.ui.components.rememberHaptic
import com.solarpulse.app.ui.theme.Brand
import com.solarpulse.app.ui.theme.SolarTheme
import com.solarpulse.core.model.Lang
import kotlinx.coroutines.launch

@Composable
fun SolarLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Brand.Blue400, Brand.Blue700))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.size(10.dp))
        Text("SolarPulse", style = MaterialTheme.typography.titleLarge)
    }
}

/** Sign in with Supabase, or any e-mail + password in demo mode (web `Login`). */
@Composable
fun LoginScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var signUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf(if (c.isDemo) "admin@solarpulse.app" else "") }
    var password by rememberSaveable { mutableStateOf(if (c.isDemo) "demo1234" else "") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.auth_error)
    val checkEmail = stringResource(R.string.auth_checkEmail)
    val lang = Lang.of(LocalConfiguration.current.locales[0].language)

    fun submit() {
        error = null
        info = null
        busy = true
        haptic()
        scope.launch {
            val res = if (signUp) c.auth.signUp(email, password) else c.auth.signIn(email, password)
            busy = false
            when {
                res == AuthRepository.INVALID -> error = invalid
                res != null -> error = res
                signUp && !c.isDemo -> info = checkEmail
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        SpCard(
            Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SolarLogo(Modifier.weight(1f))
                    SelectField(
                        label = stringResource(R.string.set_language),
                        options = Lang.entries.map { it to it.nativeName },
                        selected = lang,
                        onSelect = { applyAppLanguage(it) },
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.auth_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email)) },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = stringResource(R.string.cd_showPassword))
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { InfoBanner(Icons.Rounded.Info, it, SolarTheme.colors.danger) }
                info?.let { InfoBanner(Icons.Rounded.Info, it, SolarTheme.colors.success) }
                Button(
                    onClick = { submit() },
                    enabled = !busy && email.isNotBlank() && password.length >= 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(if (signUp) R.string.auth_signUp else R.string.auth_signIn))
                }
                if (c.isDemo) {
                    InfoBanner(Icons.Rounded.Info, stringResource(R.string.auth_demo), MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = { signUp = !signUp }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (signUp) R.string.auth_haveAccount else R.string.auth_noAccount))
                    }
                }
            }
        }
    }
}
