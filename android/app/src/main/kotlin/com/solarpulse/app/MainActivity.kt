package com.solarpulse.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solarpulse.app.ui.SolarPulseRoot
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single activity. AppCompatActivity so per-app languages (AppCompatDelegate.setApplicationLocales)
 * also work below Android 13.
 */
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(applicationContext.container) } }
    }

    /** Deep link from a notification (web-style path, e.g. /sites/site-11). */
    private val pendingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ready.value }
        if (savedInstanceState == null) pendingLink.value = intent?.getStringExtra(EXTRA_LINK)
        enableEdgeToEdge()
        setContent {
            SolarPulseRoot(
                vm = vm,
                pendingLink = pendingLink,
                onDarkTheme = { dark -> applySystemBars(dark) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_LINK)?.let { pendingLink.value = it }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Status/navigation bar icons follow the in-app theme, not only the system one. */
    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
    }

    companion object {
        const val EXTRA_LINK = "com.solarpulse.app.LINK"
        private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
