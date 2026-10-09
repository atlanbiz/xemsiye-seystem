package com.solarpulse.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solarpulse.app.AppContainer
import com.solarpulse.app.container
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Currency
import com.solarpulse.core.model.Lang

/** Number/date formatting for the current UI language and currency. */
val LocalFmt = staticCompositionLocalOf { Fmt(Lang.EN, Currency.USD) }

/** One snackbar host for the whole app (toasts in the web app). */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Navigation callbacks every screen may need (bell in the top bar, deep links). */
class AppActions(
    val openNotifications: () -> Unit = {},
    val navigate: (Any) -> Unit = {},
    val back: () -> Unit = {},
    val unreadCount: Int = 0,
    /** Whether the bottom navigation bar (not a rail) is showing: bottom insets are taken. */
    val bottomBarShown: Boolean = true,
)

val LocalAppActions = compositionLocalOf { AppActions() }

/** ViewModel built from the [AppContainer] (manual DI, no Hilt). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
