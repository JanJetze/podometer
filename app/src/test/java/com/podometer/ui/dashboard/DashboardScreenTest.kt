// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DashboardScreen] layout composable.
 *
 * Compose composables cannot be rendered in JVM unit tests without a device/emulator,
 * so these tests verify:
 *  - The [DashboardScreen] composable class exists in the expected package.
 *  - The private helper composable [SectionHeader] is compiled into the [DashboardScreenKt]
 *    class (verifiable via reflection on its enclosing class).
 *  - The [DashboardScreenKt] class is in the [com.podometer.ui.dashboard] package.
 */
class DashboardScreenTest {

    @Test
    fun `DashboardScreenKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.DashboardScreenKt")
        assertNotNull(clazz)
        assertTrue(
            "DashboardScreenKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `DashboardScreen composable function is accessible via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.DashboardScreenKt")
        // DashboardScreen is a @Composable — its JVM method signature includes
        // a trailing Function0 (onNavigateToSettings), Modifier, DashboardViewModel,
        // and the two synthetic Composer/Int params added by the Kotlin compiler.
        val methods = clazz.declaredMethods.map { it.name }
        assertTrue(
            "DashboardScreen method should be declared in DashboardScreenKt, found: $methods",
            methods.any { it == "DashboardScreen" },
        )
    }

    @Test
    fun `SectionHeader private composable is compiled into DashboardScreenKt`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.DashboardScreenKt")
        val methods = clazz.declaredMethods.map { it.name }
        assertTrue(
            "SectionHeader method should be declared in DashboardScreenKt, found: $methods",
            methods.any { it == "SectionHeader" },
        )
    }

}
