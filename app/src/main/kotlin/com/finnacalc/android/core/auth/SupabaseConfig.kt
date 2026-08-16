//
// SupabaseConfig.kt
//
// Port of iOS Core/Auth/SupabaseConfig.swift — Supabase project credentials,
// the equivalent of the web app's NEXT_PUBLIC_SUPABASE_URL /
// NEXT_PUBLIC_SUPABASE_ANON_KEY. The anon key is safe to ship in a client
// build (row-level security protects the data) — the website embeds the very
// same key in its browser bundle.
//
// While these are empty the app runs fully, just signed-out, and never
// crashes — mirroring lib/supabase.ts `isSupabaseConfigured === false`.
//

package com.finnacalc.android.core.auth

object SupabaseConfig {
    /** e.g. "https://abcdefgh.supabase.co" */
    const val URL = "https://kesloqtidckaanjtrzmb.supabase.co"

    /** The public anon key (Settings → API). */
    const val ANON_KEY = "sb_publishable_4zy7dgYW3NiNCsyKfDXS2g_qyBqEu1u"

    /**
     * True only when both values are present — the gate the rest of the auth
     * layer checks before talking to Supabase.
     */
    val isConfigured: Boolean
        get() = URL.isNotEmpty() && ANON_KEY.isNotEmpty()
}
