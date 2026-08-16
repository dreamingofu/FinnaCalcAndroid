//
// JsonPrefs.kt
//
// The Android analogue of the iOS stores' UserDefaults persistence (itself the
// analogue of the web's localStorage): synchronous SharedPreferences holding
// JSON blobs under the same keys the iOS app uses. Loads swallow decode errors
// the way the iOS stores do — a bad blob degrades to the default, never a
// crash on launch.
//

package com.finnacalc.android.core.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

object JsonPrefs {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var prefs: SharedPreferences? = null

    /**
     * In-memory fallback used when no Context has been supplied — JVM unit
     * tests construct the stores directly, and a lateinit crash there would
     * be a test-harness failure rather than anything about the code.
     */
    private val memory = mutableMapOf<String, String>()

    /** Called once from FinnaApp.onCreate before any store touches it. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("finnacalc", Context.MODE_PRIVATE)
    }

    /** Test seam: drops every stored value (and any Context binding). */
    fun resetForTesting() {
        prefs = null
        memory.clear()
    }

    inline fun <reified T> load(key: String): T? {
        val raw = raw(key) ?: return null
        return try {
            json.decodeFromString<T>(raw)
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T> persist(value: T, key: String) {
        try {
            put(key, json.encodeToString(value))
        } catch (_: Exception) {
            // Mirrors iOS `try?` — a failed encode loses one save, not the app.
        }
    }

    fun raw(key: String): String? = prefs?.getString(key, null) ?: memory[key]

    fun put(key: String, value: String) {
        val store = prefs
        if (store != null) store.edit().putString(key, value).apply() else memory[key] = value
    }
}
