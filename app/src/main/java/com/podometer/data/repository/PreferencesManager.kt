// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user preferences backed by [DataStore].
 *
 * Provides access to:
 * - The auto-start preference that controls whether
 *   [com.podometer.service.StepTrackingService] is started automatically on boot.
 * - The stride length preference used for distance estimation.
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // ─── Keys ────────────────────────────────────────────────────────────────

    private companion object {
        val KEY_AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
        const val DEFAULT_AUTO_START = true

        val KEY_STRIDE_LENGTH_KM = floatPreferencesKey("stride_length_km")

        /** Default stride length: 75 cm = 0.00075 km. */
        const val DEFAULT_STRIDE_LENGTH_KM = 0.00075f

        /** Minimum allowed stride length: 10 cm = 0.0001 km. */
        const val MIN_STRIDE_LENGTH_KM = 0.0001f

        /** Maximum allowed stride length: 5 m = 0.005 km. */
        const val MAX_STRIDE_LENGTH_KM = 0.005f
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

    /**
     * Emits the user-configured stride length in kilometres.
     * Defaults to [DEFAULT_STRIDE_LENGTH_KM] (0.00075 km = 75 cm) when no value
     * has been stored yet.
     */
    fun strideLengthKm(): Flow<Float> =
        dataStore.data.map { prefs ->
            prefs[KEY_STRIDE_LENGTH_KM] ?: DEFAULT_STRIDE_LENGTH_KM
        }

    // ─── Write ───────────────────────────────────────────────────────────────

    /** Persists the given [enabled] state for the auto-start preference. */
    suspend fun setAutoStartEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_ENABLED] = enabled
        }
    }

    /**
     * Persists the given [strideKm] as the user's stride length.
     *
     * Values outside the reasonable range [[MIN_STRIDE_LENGTH_KM], [MAX_STRIDE_LENGTH_KM]]
     * are coerced to the nearest bound. Zero or negative values are rejected outright.
     *
     * @param strideKm Stride length in kilometres (e.g. 0.00075 for 75 cm).
     *   Must be positive (> 0). Reasonable range: 0.0001 km (10 cm) to 0.005 km (5 m).
     * @throws IllegalArgumentException if [strideKm] is zero or negative.
     */
    suspend fun setStrideLengthKm(strideKm: Float) {
        require(strideKm > 0f) { "Stride length must be positive, got $strideKm" }
        val coerced = strideKm.coerceIn(MIN_STRIDE_LENGTH_KM, MAX_STRIDE_LENGTH_KM)
        dataStore.edit { prefs ->
            prefs[KEY_STRIDE_LENGTH_KM] = coerced
        }
    }
}
