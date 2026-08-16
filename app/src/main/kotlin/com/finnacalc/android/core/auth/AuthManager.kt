//
// AuthManager.kt
//
// Port of iOS Core/Auth/AuthManager.swift — observable auth state for the
// app, the counterpart of the `AuthProvider` / `useAuth` context in the web
// `lib/auth.tsx`. iOS's ObservableObject becomes StateFlow on an app-scoped
// coroutine scope (one instance for the whole app, owned by FinnaApp).
//

package com.finnacalc.android.core.auth

import android.content.Intent
import com.finnacalc.android.core.networking.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthManager(
    private val client: AuthClient = makeAuthClient(),
    scope: CoroutineScope,
) {
    /** The signed-in user, or null. Mirrors `useAuth().user`. */
    private val _user = MutableStateFlow(client.currentUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    /** True until the initial session restore resolves. Mirrors `useAuth().loading`. */
    private val _loading = MutableStateFlow(client.isConfigured)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Whether Supabase is configured. Mirrors `useAuth().configured`. */
    val configured: Boolean = client.isConfigured

    init {
        if (configured) {
            // Restore session + subscribe to changes. The flow's first emission
            // is the restored session, like getSession() then onAuthStateChange().
            scope.launch {
                client.authStateChanges().collect { newUser ->
                    _user.value = newUser
                    _loading.value = false
                }
            }
            // Don't sit on a splash forever if the first event is slow to arrive.
            scope.launch {
                delay(1_500)
                _loading.value = false
            }
        }
    }

    suspend fun signIn(email: String, password: String) = client.signIn(email, password)

    suspend fun signUp(email: String, password: String, name: String): SignUpResult =
        client.signUp(email, password, name)

    suspend fun signInWithGoogle() = client.signInWithGoogle()

    suspend fun resetPassword(email: String) = client.resetPassword(email)

    suspend fun signOut() {
        client.signOut()
        _user.value = null
    }

    /**
     * Permanently deletes the signed-in user's account, then signs out
     * locally. The Supabase client can't delete its own user (that needs the
     * service_role key), so this calls the backend's /api/account/delete,
     * which resolves the user from the Bearer token and deletes them
     * admin-side. Throws on failure so the UI can surface it; the local
     * session is only cleared on success.
     */
    suspend fun deleteAccount() {
        ApiClient.shared.postData("/api/account/delete", "{}")
        client.signOut()
        _user.value = null
    }

    /** Current access token for the API Bearer header (null when signed out). */
    suspend fun accessToken(): String? = client.accessToken()

    /** Forward the OAuth redirect (finnacalc://auth-callback) to the client. */
    fun handleDeeplink(intent: Intent) = client.handleDeeplink(intent)
}
