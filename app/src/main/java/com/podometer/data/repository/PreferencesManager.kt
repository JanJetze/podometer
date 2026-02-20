// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user preferences backed by [DataStore].
 *
 * Provides access to the auto-start preference that controls whether
 * [com.podometer.service.StepTrackingService] is started automatically on boot.
 * Additional preferences will be added here when the settings screen is built.
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // ─── Keys ────────────────────────────────────────────────────────────────

    private companion object {
        val KEY_AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
        const val DEFAULT_AUTO_START = true
    }

    // ─── Read ────────────────────────────────────────────────────────────────

    /**
     * Emits whether the auto-start preference is enabled.
     * Defaults to `true` when no value has been stored yet.
     */
    fun isAutoStartEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_AUTO_START_ENABLED] ?: DEFAULT_AUTO_START
        }

    // ─── Write ───────────────────────────────────────────────────────────────

    /** Persists the given [enabled] state for the auto-start preference. */
    suspend fun setAutoStartEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_ENABLED] = enabled
        }
    }
}
