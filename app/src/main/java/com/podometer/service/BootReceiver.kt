// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.podometer.data.repository.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and starts [StepTrackingService]
 * when the user has auto-start enabled (the default).
 *
 * Boot receivers on Android 13+ (minSdk 33 for this project) are permitted to
 * call [Context.startForegroundService] from the BOOT_COMPLETED broadcast —
 * this is an explicit exemption from background-start restrictions.
 *
 * [runBlocking] is safe here: DataStore performs a fast local disk read
 * (typically < 100 ms) and boot receivers have a 10-second window before the
 * system reclaims their process.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (context == null) return

        val autoStartEnabled = resolveAutoStartEnabled {
            runBlocking { preferencesManager.isAutoStartEnabled().first() }
        }

        if (!autoStartEnabled) {
            Log.d(TAG, "Auto-start is disabled; StepTrackingService will not be started.")
            return
        }

        Log.d(TAG, "Boot completed — starting StepTrackingService.")
        val serviceIntent = Intent(context, StepTrackingService::class.java)
        context.startForegroundService(serviceIntent)
    }

    internal companion object {
        const val TAG = "BootReceiver"

        /**
         * Reads the auto-start preference via [readPreference] and returns its value.
         *
         * If [readPreference] throws any [Exception] (e.g. DataStore I/O failure,
         * coroutine cancellation), the exception is caught, an error is logged,
         * and `true` is returned so that step tracking is not silently lost on
         * boot (fail-open policy).
         *
         * @param readPreference A lambda that returns the stored auto-start preference value.
         * @return The preference value, or `true` when [readPreference] throws.
         */
        fun resolveAutoStartEnabled(readPreference: () -> Boolean): Boolean {
            return try {
                readPreference()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read auto-start preference, defaulting to enabled", e)
                true
            }
        }
    }
}
