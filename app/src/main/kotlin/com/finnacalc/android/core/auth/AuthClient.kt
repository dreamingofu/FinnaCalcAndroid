//
// AuthClient.kt
//
// Port of iOS Core/Auth/AuthClient.swift — backend-agnostic auth surface.
// AuthManager talks to this interface so the app compiles and runs whether or
// not Supabase credentials are configured.
//
// Deviation from iOS: no Sign in with Apple on Android — Google is the one
// OAuth provider here (Apple's native flow has no Android equivalent and the
// web app's Apple OAuth redirect is not worth the friction on this platform).
//

package com.finnacalc.android.core.auth

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AuthClient {
    /** False when Supabase isn't configured (mirrors `isSupabaseConfigured`). */
    val isConfigured: Boolean

    /**
     * Best-effort synchronous snapshot of the current user (may be null until
     * the persisted session finishes loading; the flow below is the source of
     * truth).
     */
    fun currentUser(): AuthUser?

    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String, name: String): SignUpResult

    /** Native Google sign-in via a browser Custom Tab + Supabase OAuth deep link. */
    suspend fun signInWithGoogle()

    /** Sends the Supabase password-reset email. */
    suspend fun resetPassword(email: String)
    suspend fun signOut()

    /**
     * The current Supabase access token, sent as a Bearer to the API. Null
     * when signed out or unconfigured. Refreshes an expired JWT before
     * returning it.
     */
    suspend fun accessToken(): String?

    /**
     * Emits on every session change. The first value is the restored session
     * on launch — equivalent to `getSession()` followed by `onAuthStateChange`
     * in lib/auth.tsx.
     */
    fun authStateChanges(): Flow<AuthUser?>

    /** Feed the OAuth redirect (finnacalc://auth-callback) back to the client. */
    fun handleDeeplink(intent: Intent)
}

/**
 * Used when Supabase isn't configured: the app behaves as a signed-out client
 * and never crashes — the native counterpart of lib/auth.tsx short-circuiting
 * on `!isSupabaseConfigured`.
 */
class UnconfiguredAuthClient : AuthClient {
    override val isConfigured = false
    override fun currentUser(): AuthUser? = null
    override suspend fun signIn(email: String, password: String): Unit = throw AuthException.NotConfigured()
    override suspend fun signUp(email: String, password: String, name: String): SignUpResult =
        throw AuthException.NotConfigured()
    override suspend fun signInWithGoogle(): Unit = throw AuthException.NotConfigured()
    override suspend fun resetPassword(email: String): Unit = throw AuthException.NotConfigured()
    override suspend fun signOut() {}
    override suspend fun accessToken(): String? = null
    override fun authStateChanges(): Flow<AuthUser?> = emptyFlow()
    override fun handleDeeplink(intent: Intent) {}
}

/**
 * Resolves the concrete client: the Supabase-backed one when credentials are
 * set, otherwise the unconfigured fallback.
 */
fun makeAuthClient(): AuthClient =
    if (SupabaseConfig.isConfigured) SupabaseAuthClient() else UnconfiguredAuthClient()
