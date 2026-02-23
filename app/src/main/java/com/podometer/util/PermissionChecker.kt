// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.util

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Returns true if the ACTIVITY_RECOGNITION permission is granted in the given [permissionResults] map.
 *
 * ACTIVITY_RECOGNITION is the critical permission for step counting. Without it, the Android
 * step counter sensor is inaccessible. Only ACTIVITY_RECOGNITION is required; BODY_SENSORS
 * allows the physical sensor but the app can still use accelerometer-based detection without it.
 *
 * @param permissionResults Map of permission name to grant result, as returned by
 *   [androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions].
 */
fun areEssentialPermissionsGranted(permissionResults: Map<String, Boolean>): Boolean {
    // ACTIVITY_RECOGNITION is the key permission. Without it, step counting is not possible.
    // BODY_SENSORS is nice-to-have (enables the hardware step counter) but the app degrades
    // gracefully to accelerometer-based detection when it is absent.
    return permissionResults[Manifest.permission.ACTIVITY_RECOGNITION] == true
}

/**
 * Returns true if all three onboarding permissions are granted.
 *
 * The three permissions are:
 * - [Manifest.permission.BODY_SENSORS] — hardware step counter access
 * - [Manifest.permission.ACTIVITY_RECOGNITION] — step counting and activity detection
 * - [Manifest.permission.POST_NOTIFICATIONS] — foreground service notification
 *
 * @param permissionResults Map of permission name to grant result.
 */
fun areAllPermissionsGranted(permissionResults: Map<String, Boolean>): Boolean {
    return permissionResults[Manifest.permission.BODY_SENSORS] == true &&
        permissionResults[Manifest.permission.ACTIVITY_RECOGNITION] == true &&
        permissionResults[Manifest.permission.POST_NOTIFICATIONS] == true
}

/**
 * Returns true if ACTIVITY_RECOGNITION is explicitly denied (i.e. mapped to `false`).
 *
 * Returns false if the permission is not present in the map (meaning it was not part of the
 * request, not that it is denied).
 *
 * @param permissionResults Map of permission name to grant result.
 */
fun isActivityRecognitionDenied(permissionResults: Map<String, Boolean>): Boolean {
    return permissionResults[Manifest.permission.ACTIVITY_RECOGNITION] == false
}

/**
 * Checks whether ACTIVITY_RECOGNITION is currently granted using the system permission state.
 *
 * This is used by [com.podometer.ui.dashboard.DashboardViewModel] at startup and on resume to
 * determine whether to show [com.podometer.ui.dashboard.PermissionRecoveryScreen].
 *
 * @param context Android [Context] used for [ContextCompat.checkSelfPermission].
 * @return True if ACTIVITY_RECOGNITION is granted; false otherwise.
 */
fun checkEssentialPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) == PackageManager.PERMISSION_GRANTED
}
