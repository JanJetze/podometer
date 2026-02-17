// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import android.app.Application

/**
 * Application class for Podometer.
 *
 * Hilt integration (@HiltAndroidApp) will be added in a separate task.
 */
class PodometerApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
