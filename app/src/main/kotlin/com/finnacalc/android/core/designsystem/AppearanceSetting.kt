//
// AppearanceSetting.kt
//
// Port of iOS AppearanceSetting.swift. User-selectable appearance: follow the
// system, or force light/dark. The design tokens carry both palettes — this
// setting decides which scheme the root prefers.
//
// Deviation from iOS: @AppStorage becomes Jetpack DataStore (preferences); the
// stored raw values ("system"/"light"/"dark") match iOS so a future shared
// backend setting round-trips cleanly.
//

package com.finnacalc.android.core.designsystem

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppearanceSetting(val raw: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    val title: String
        get() = when (this) {
            System -> "System"
            Light -> "Light"
            Dark -> "Dark"
        }

    val icon: ImageVector
        get() = when (this) {
            System -> Icons.Default.Contrast
            Light -> Icons.Default.LightMode
            Dark -> Icons.Default.DarkMode
        }

    /** Whether this setting resolves to dark, given the current system state. */
    @Composable
    fun resolvesToDark(): Boolean = when (this) {
        System -> isSystemInDarkTheme()
        Light -> false
        Dark -> true
    }

    companion object {
        const val STORAGE_KEY = "finnacalc.appearance"

        fun fromRaw(raw: String?): AppearanceSetting =
            entries.firstOrNull { it.raw == raw } ?: System
    }
}

// MARK: DataStore-backed persistence

private val Context.appearanceDataStore by preferencesDataStore(name = "settings")
private val appearanceKey = stringPreferencesKey(AppearanceSetting.STORAGE_KEY)

class AppearanceStore(private val context: Context) {
    val setting: Flow<AppearanceSetting> =
        context.appearanceDataStore.data.map { AppearanceSetting.fromRaw(it[appearanceKey]) }

    suspend fun set(setting: AppearanceSetting) {
        context.appearanceDataStore.edit { it[appearanceKey] = setting.raw }
    }
}
