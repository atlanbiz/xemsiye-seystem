package com.solarpulse.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val email: String) : AuthState
}

/** Supabase e-mail/password auth, or the demo sign-in (any e-mail + password) — web `AuthProvider`. */
class AuthRepository(private val supabase: SupabaseClient?, private val prefs: Prefs) {
    val isDemo: Boolean get() = supabase == null

    val state: Flow<AuthState> =
        if (supabase != null) {
            supabase.auth.sessionStatus.map { s ->
                when (s) {
                    is SessionStatus.Authenticated -> AuthState.SignedIn(s.session.user?.email.orEmpty())
                    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                    SessionStatus.Initializing -> AuthState.Loading
                    // Token refresh failed (often offline): keep the user in; requests surface the error.
                    is SessionStatus.RefreshFailure -> AuthState.SignedIn("")
                }
            }
        } else {
            prefs.demoUser.map { if (it.isNullOrBlank()) AuthState.SignedOut else AuthState.SignedIn(it) }
        }

    /** Returns an error message, or null on success. */
    suspend fun signIn(email: String, password: String): String? {
        if (email.isBlank() || password.isBlank()) return INVALID
        val client = supabase ?: run {
            prefs.setDemoUser(email.trim())
            return null
        }
        return runCatching {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }

    suspend fun signUp(email: String, password: String): String? {
        val client = supabase ?: return signIn(email, password)
        if (email.isBlank() || password.length < 6) return INVALID
        return runCatching {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }

    suspend fun signOut() {
        supabase?.let { runCatching { it.auth.signOut() } }
        prefs.setDemoUser(null)
    }

    companion object {
        const val INVALID = "invalid"
    }
}
