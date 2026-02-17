// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub for the foreground step-tracking service.
 *
 * TODO: Implement actual step-counting logic (task-ca3e67ab).
 *       Add @AndroidEntryPoint when Hilt injection is wired up.
 */
class StepTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
