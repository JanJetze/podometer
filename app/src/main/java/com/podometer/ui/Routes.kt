// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui

/**
 * Sealed class representing all top-level navigation destinations in Podometer.
 *
 * Each object's [route] string is used as the key in the Compose NavHost.
 */
sealed class Screen(val route: String) {
    /** Main step-counter dashboard — the start destination. */
    data object Dashboard : Screen("dashboard")

    /** App settings screen. */
    data object Settings : Screen("settings")

    /** First-run onboarding flow. */
    data object Onboarding : Screen("onboarding")

    /** Donate / support development screen. */
    data object Donate : Screen("donate")
}
