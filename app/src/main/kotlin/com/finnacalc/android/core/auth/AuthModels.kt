//
// AuthModels.kt
//
// Port of iOS Core/Auth/AuthModels.swift — auth value types, originally from
// the web `lib/auth.tsx`.
//

package com.finnacalc.android.core.auth

/** Mirrors the web `User` type: `{ id, email, name }`. */
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
) {
    /** What the header shows on the account chip: name, falling back to email. */
    val displayName: String get() = name.ifEmpty { email }
}

/**
 * Mirrors the web `SignUpResult`. When Supabase requires email confirmation it
 * returns a user but no session, so `needsConfirmation` is true.
 */
data class SignUpResult(val needsConfirmation: Boolean)

sealed class AuthException(message: String) : Exception(message) {
    /**
     * Supabase URL/anon key not set — the app runs signed-out, exactly like
     * the web's `isSupabaseConfigured === false`.
     */
    class NotConfigured : AuthException(
        "Accounts aren't available yet — Supabase credentials haven't been configured."
    )

    class Canceled : AuthException("Sign-in was canceled.")

    class Message(message: String) : AuthException(message)
}
