// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the [Screen] sealed class route definitions.
 *
 * Verifies that every destination has a unique, non-blank route string and that
 * Dashboard is a distinct destination from Settings and Onboarding.
 */
class ScreenRoutesTest {

    @Test
    fun `Dashboard route is not blank`() {
        assert(Screen.Dashboard.route.isNotBlank()) {
            "Dashboard route must not be blank"
        }
    }

    @Test
    fun `Settings route is not blank`() {
        assert(Screen.Settings.route.isNotBlank()) {
            "Settings route must not be blank"
        }
    }

    @Test
    fun `Onboarding route is not blank`() {
        assert(Screen.Onboarding.route.isNotBlank()) {
            "Onboarding route must not be blank"
        }
    }

    @Test
    fun `Donate route is not blank`() {
        assert(Screen.Donate.route.isNotBlank()) {
            "Donate route must not be blank"
        }
    }

    @Test
    fun `All routes are unique`() {
        val routes = listOf(
            Screen.Dashboard.route,
            Screen.Settings.route,
            Screen.Onboarding.route,
            Screen.Donate.route,
        )
        assertEquals(
            "All Screen routes must be unique",
            routes.size,
            routes.toSet().size,
        )
    }

    @Test
    fun `Dashboard route equals expected string`() {
        assertEquals("dashboard", Screen.Dashboard.route)
    }

    @Test
    fun `Settings route equals expected string`() {
        assertEquals("settings", Screen.Settings.route)
    }

    @Test
    fun `Onboarding route equals expected string`() {
        assertEquals("onboarding", Screen.Onboarding.route)
    }

    @Test
    fun `Dashboard is distinct from Settings`() {
        assertNotEquals(Screen.Dashboard.route, Screen.Settings.route)
    }
}
