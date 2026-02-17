// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Podometer.
 *
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection
 * throughout the app.
 */
@HiltAndroidApp
class PodometerApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
