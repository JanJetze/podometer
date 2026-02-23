// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.podometer.util.checkEssentialPermissions

private const val TAG = "ServiceStarter"

/**
 * Starts [StepTrackingService] as a foreground service if the essential runtime permissions
 * (ACTIVITY_RECOGNITION) are currently granted.
 *
 * This helper centralises service startup so that [com.podometer.MainActivity] and
 * [com.podometer.ui.dashboard.DashboardViewModel] both use the same guarded start logic.
 * If permissions are not granted, the call is silently skipped — the Dashboard will show
 * [com.podometer.ui.dashboard.PermissionRecoveryScreen] instead.
 *
 * @param context The Android [Context] used to start the foreground service and to check
 *   permissions via [checkEssentialPermissions].
 */
fun startTrackingServiceIfPermitted(context: Context) {
    if (!checkEssentialPermissions(context)) {
        Log.d(TAG, "Essential permissions not granted — skipping service start.")
        return
    }
    Log.d(TAG, "Permissions granted — starting StepTrackingService.")
    val intent = Intent(context, StepTrackingService::class.java)
    context.startForegroundService(intent)
}
