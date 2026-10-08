package com.solarpulse.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solarpulse.app.data.AuthState
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: auth, loaded data, theme. Drives the splash screen and the root UI. */
class MainViewModel(private val c: AppContainer) : ViewModel() {
    val auth: StateFlow<AuthState> = c.auth.state.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)
    val db: StateFlow<Database?> = c.repository.db

    /** The shared setting wins once loaded; before that the device-local mirror. */
    val theme: StateFlow<ThemeMode?> = combine(c.prefs.theme, c.repository.db) { pref, db -> db?.settings?.theme ?: pref }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dynamicColor: StateFlow<Boolean> = c.prefs.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Splash stays until we know whether to show login or data. */
    val ready: StateFlow<Boolean> = combine(auth, c.repository.db) { a, d ->
        when (a) {
            AuthState.Loading -> false
            AuthState.SignedOut -> true
            is AuthState.SignedIn -> d != null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            auth.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> if (c.repository.db.value == null) {
                        c.repository.load()
                        if (c.isDemo) runCatching { c.alerts.evaluate() }
                    }
                    AuthState.SignedOut -> if (c.repository.isRemote) c.repository.clear()
                    AuthState.Loading -> Unit
                }
            }
        }
    }

    /** On resume: refresh live telemetry and (demo mode) evaluate alert rules. */
    fun onResume() {
        if (auth.value !is AuthState.SignedIn || c.repository.db.value == null) return
        viewModelScope.launch {
            c.repository.refreshReadings()
            if (c.isDemo) runCatching { c.alerts.evaluate() }
        }
    }
}
