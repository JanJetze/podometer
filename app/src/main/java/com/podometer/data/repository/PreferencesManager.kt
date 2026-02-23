// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * - The daily step goal preference for the progress ring on the dashboard.
 * - The notification style preference controlling the foreground notification detail level.
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

        val KEY_DAILY_STEP_GOAL = intPreferencesKey("daily_step_goal")

        /** Default daily step goal. */
        const val DEFAULT_DAILY_STEP_GOAL = 10_000

        val KEY_NOTIFICATION_STYLE = stringPreferencesKey("notification_style")

        /** Default notification style: minimal. */
        const val DEFAULT_NOTIFICATION_STYLE = "minimal"
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

    /**
     * Emits the user's daily step goal.
     * Defaults to [DEFAULT_DAILY_STEP_GOAL] (10,000 steps) when no value has been stored yet.
     */
    fun dailyStepGoal(): Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[KEY_DAILY_STEP_GOAL] ?: DEFAULT_DAILY_STEP_GOAL
        }

    /**
     * Emits the user's preferred notification style as a string ("minimal" or "detailed").
     * Defaults to [DEFAULT_NOTIFICATION_STYLE] when no value has been stored yet.
     */
    fun notificationStyle(): Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_NOTIFICATION_STYLE] ?: DEFAULT_NOTIFICATION_STYLE
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

    /**
     * Persists the given [goal] as the user's daily step goal.
     *
     * @param goal Daily step goal. Must be a positive integer (> 0).
     * @throws IllegalArgumentException if [goal] is zero or negative.
     */
    suspend fun setDailyStepGoal(goal: Int) {
        require(goal > 0) { "Daily step goal must be positive, got $goal" }
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_STEP_GOAL] = goal
        }
    }

    /**
     * Persists the given [style] as the user's notification style preference.
     *
     * Expected values: "minimal" or "detailed". Other values are stored as-is.
     *
     * @param style The notification style string ("minimal" or "detailed").
     */
    suspend fun setNotificationStyle(style: String) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_STYLE] = style
        }
    }
}
