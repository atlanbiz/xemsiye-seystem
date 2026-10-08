package com.solarpulse.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.solarpulse.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "solarpulse")

/**
 * Device-local preferences (DataStore). Shared data such as the language, currency or impact
 * factors lives in the `settings` row; the theme is mirrored here so the first frame is right
 * before the data has loaded.
 */
class Prefs(private val context: Context) {
    private object Keys {
        val demoUser = stringPreferencesKey("demo_user")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val askedNotifications = booleanPreferencesKey("asked_notifications")
    }

    /** Signed-in e-mail in demo mode (any e-mail + password works, like the web). */
    val demoUser: Flow<String?> = context.dataStore.data.map { it[Keys.demoUser] }.distinctUntilChanged()

    val theme: Flow<ThemeMode?> = context.dataStore.data
        .map { p -> p[Keys.theme]?.let { k -> ThemeMode.entries.firstOrNull { it.key == k } } }
        .distinctUntilChanged()

    /** Material You colours; off by default to keep the SolarPulse brand palette. */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.dynamicColor] ?: false }.distinctUntilChanged()

    val askedNotifications: Flow<Boolean> = context.dataStore.data.map { it[Keys.askedNotifications] ?: false }

    suspend fun setDemoUser(email: String?) {
        context.dataStore.edit { if (email == null) it.remove(Keys.demoUser) else it[Keys.demoUser] = email }
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = theme.key }
    }

    suspend fun setDynamicColor(on: Boolean) {
        context.dataStore.edit { it[Keys.dynamicColor] = on }
    }

    suspend fun setAskedNotifications() {
        context.dataStore.edit { it[Keys.askedNotifications] = true }
    }
}
