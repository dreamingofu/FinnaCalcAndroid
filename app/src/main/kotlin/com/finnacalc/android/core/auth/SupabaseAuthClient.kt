//
// SupabaseAuthClient.kt
//
// Port of iOS Core/Auth/SupabaseAuthClient.swift — the supabase-kt-backed
// AuthClient. A faithful port of the auth calls in the web `lib/auth.tsx`:
//   signIn  → auth.signInWith(Email)
//   signUp  → auth.signUpWith(Email) { data = { name } } → needsConfirmation
//   Google  → auth.signInWith(Google) (browser Custom Tab + deep link redirect;
//             unlike iOS's ASWebAuthenticationSession this does NOT suspend
//             until the callback — the session lands via handleDeeplink and
//             flows out of authStateChanges)
//   state   → auth.sessionStatus → authStateChanges
//

package com.finnacalc.android.core.auth

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class SupabaseAuthClient : AuthClient {
    private val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.ANON_KEY,
    ) {
        install(Auth) {
            // The OAuth redirect the web flow returns to: finnacalc://auth-callback
            scheme = "finnacalc"
            host = "auth-callback"
        }
    }

    override val isConfigured = true

    override fun currentUser(): AuthUser? =
        client.auth.currentUserOrNull()?.let(::mapUser)

    override suspend fun signIn(email: String, password: String) {
        try {
            client.auth.signInWith(Email) {
                this.email = email.normalizedEmail()
                this.password = password
            }
        } catch (e: Exception) {
            throw AuthException.Message(e.message ?: "Sign-in failed.")
        }
    }

    override suspend fun signUp(email: String, password: String, name: String): SignUpResult {
        try {
            client.auth.signUpWith(Email) {
                this.email = email.normalizedEmail()
                this.password = password
                data = buildJsonObject { put("name", name.trim()) }
            }
            // Email-confirmation projects return a user but no session.
            return SignUpResult(needsConfirmation = client.auth.currentSessionOrNull() == null)
        } catch (e: Exception) {
            throw AuthException.Message(e.message ?: "Sign-up failed.")
        }
    }

    override suspend fun signInWithGoogle() {
        try {
            // Launches the browser; the redirect comes back through
            // handleDeeplink and the new session emits from authStateChanges.
            client.auth.signInWith(Google)
        } catch (e: Exception) {
            throw AuthException.Message("Couldn't start Google sign-in: ${e.message}")
        }
    }

    override suspend fun resetPassword(email: String) {
        try {
            client.auth.resetPasswordForEmail(email.normalizedEmail())
        } catch (e: Exception) {
            throw AuthException.Message(e.message ?: "Couldn't send the reset email.")
        }
    }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
    }

    /**
     * Refreshes an expired (or about-to-expire) JWT before returning it.
     * Brokerage/trading routes verify this token server-side, so a stale token
     * would 401 a signed-in user until the SDK's background refresh fired.
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun accessToken(): String? {
        val session = client.auth.currentSessionOrNull() ?: return null
        if (session.expiresAt < Clock.System.now() + 60.seconds) {
            runCatching { client.auth.refreshCurrentSession() }
        }
        return client.auth.currentAccessTokenOrNull()
    }

    override fun authStateChanges(): Flow<AuthUser?> =
        client.auth.sessionStatus
            // Initializing is "session restore still in flight" — the manager
            // keeps showing the splash until the first resolved state.
            .filter { it !is SessionStatus.Initializing }
            .map { status ->
                when (status) {
                    is SessionStatus.Authenticated -> status.session.user?.let(::mapUser)
                    else -> null
                }
            }

    override fun handleDeeplink(intent: Intent) {
        client.handleDeeplinks(intent)
    }

    /**
     * Port of `toUser` in lib/auth.tsx: name is the trimmed
     * `user_metadata.name`, falling back to the email's local part (matching
     * JS `email.split("@")[0]`, including the empty local-part edge case).
     */
    private fun mapUser(user: UserInfo): AuthUser {
        val email = user.email ?: ""
        val metaName = user.userMetadata?.get("name")?.jsonPrimitive?.content?.trim() ?: ""
        val fallback = email.split("@").first()
        return AuthUser(id = user.id, email = email, name = metaName.ifEmpty { fallback })
    }
}

/** `email.trim().toLowerCase()` from the web sign-in/sign-up handlers. */
private fun String.normalizedEmail(): String = trim().lowercase()
