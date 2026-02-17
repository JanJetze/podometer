// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Stub for the boot-completed broadcast receiver.
 *
 * TODO: Implement restart-service-on-boot logic (task-3d48abba).
 *       Add @AndroidEntryPoint when Hilt injection is wired up.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Start StepTrackingService when the device finishes booting (task-3d48abba).
    }
}
