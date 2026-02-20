// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivityBadge] pure-logic helpers.
 *
 * Compose composables cannot be rendered in JVM unit tests without a device/emulator,
 * so these tests cover the pure-Kotlin extension functions extracted from ActivityBadge:
 *  - [ActivityState.displayText]: label text for each activity state
 *  - [ActivityState.contentDescriptionText]: accessibility text for each activity state
 *  - The [ActivityBadge] composable class itself exists in the expected package.
 */
class ActivityBadgeTest {

    // ─── displayText ──────────────────────────────────────────────────────────

    @Test
    fun `displayText returns Walking for WALKING state`() {
        assertEquals("Walking", ActivityState.WALKING.displayText())
    }

    @Test
    fun `displayText returns Cycling for CYCLING state`() {
        assertEquals("Cycling", ActivityState.CYCLING.displayText())
    }

    @Test
    fun `displayText returns Still for STILL state`() {
        assertEquals("Still", ActivityState.STILL.displayText())
    }

    // ─── contentDescriptionText ───────────────────────────────────────────────

    @Test
    fun `contentDescriptionText returns Currently walking for WALKING state`() {
        assertEquals("Currently walking", ActivityState.WALKING.contentDescriptionText())
    }

    @Test
    fun `contentDescriptionText returns Currently cycling for CYCLING state`() {
        assertEquals("Currently cycling", ActivityState.CYCLING.contentDescriptionText())
    }

    @Test
    fun `contentDescriptionText returns Currently still for STILL state`() {
        assertEquals("Currently still", ActivityState.STILL.contentDescriptionText())
    }

    // ─── displayText coverage for all enum values ─────────────────────────────

    @Test
    fun `displayText covers all ActivityState values without throwing`() {
        ActivityState.entries.forEach { state ->
            val text = state.displayText()
            assertTrue(
                "displayText for $state should be non-empty",
                text.isNotBlank(),
            )
        }
    }

    @Test
    fun `contentDescriptionText covers all ActivityState values without throwing`() {
        ActivityState.entries.forEach { state ->
            val text = state.contentDescriptionText()
            assertTrue(
                "contentDescriptionText for $state should start with 'Currently'",
                text.startsWith("Currently"),
            )
        }
    }

    // ─── ActivityBadgeKt class existence ──────────────────────────────────────

    @Test
    fun `ActivityBadgeKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        assertTrue(
            "ActivityBadgeKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `displayText function is accessible as extension function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        val method = clazz.getDeclaredMethod("displayText", ActivityState::class.java)
        val result = method.invoke(null, ActivityState.WALKING) as String
        assertEquals("Walking", result)
    }

    @Test
    fun `contentDescriptionText function is accessible as extension function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityBadgeKt")
        val method = clazz.getDeclaredMethod("contentDescriptionText", ActivityState::class.java)
        val result = method.invoke(null, ActivityState.CYCLING) as String
        assertEquals("Currently cycling", result)
    }
}
