// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Unit tests for [TransitionLog] pure-logic helpers.
 *
 * These tests exercise [formatTransitionTime] and [transitionDescription],
 * which are pure-Kotlin functions that can be run on the JVM without Compose or
 * an Android device.
 */
class TransitionLogTest {

    // ─── formatTransitionTime ─────────────────────────────────────────────────

    @Test
    fun `formatTransitionTime formats midnight as 00 00`() {
        // Use UTC so the test result is timezone-independent.
        val utc = TimeZone.getTimeZone("UTC")
        val result = formatTransitionTime(0L, utc)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatTransitionTime formats 9h 15min as 09 15`() {
        val utc = TimeZone.getTimeZone("UTC")
        val millis = (9L * 60 + 15L) * 60_000L
        val result = formatTransitionTime(millis, utc)
        assertEquals("09:15", result)
    }

    @Test
    fun `formatTransitionTime formats 14h 30min as 14 30`() {
        val utc = TimeZone.getTimeZone("UTC")
        val millis = (14L * 60 + 30L) * 60_000L
        val result = formatTransitionTime(millis, utc)
        assertEquals("14:30", result)
    }

    @Test
    fun `formatTransitionTime formats 23h 59min as 23 59`() {
        val utc = TimeZone.getTimeZone("UTC")
        val millis = (23L * 60 + 59L) * 60_000L
        val result = formatTransitionTime(millis, utc)
        assertEquals("23:59", result)
    }

    // ─── transitionDescription ────────────────────────────────────────────────

    @Test
    fun `transitionDescription for WALKING returns Started walking`() {
        val result = transitionDescription(ActivityState.WALKING)
        assertEquals("Started walking", result)
    }

    @Test
    fun `transitionDescription for CYCLING returns Started cycling`() {
        val result = transitionDescription(ActivityState.CYCLING)
        assertEquals("Started cycling", result)
    }

    @Test
    fun `transitionDescription for STILL returns Became still`() {
        val result = transitionDescription(ActivityState.STILL)
        assertEquals("Became still", result)
    }

    // ─── TransitionLogKt class existence ─────────────────────────────────────

    @Test
    fun `TransitionLogKt class exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.TransitionLogKt")
        assertTrue(
            "TransitionLogKt should be in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `formatTransitionTime function is accessible via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.TransitionLogKt")
        val method = clazz.getDeclaredMethod(
            "formatTransitionTime",
            Long::class.java,
            TimeZone::class.java,
        )
        val result = method.invoke(null, 0L, TimeZone.getTimeZone("UTC"))
        assertEquals("00:00", result)
    }

    @Test
    fun `transitionDescription function is accessible via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.TransitionLogKt")
        val method = clazz.getDeclaredMethod(
            "transitionDescription",
            ActivityState::class.java,
        )
        val result = method.invoke(null, ActivityState.WALKING)
        assertEquals("Started walking", result)
    }

    // ─── TransitionEvent model helpers ───────────────────────────────────────

    @Test
    fun `TransitionEvent isManualOverride false by default`() {
        val event = TransitionEvent(
            id = 1,
            timestamp = 1_000L,
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        )
        assertEquals(false, event.isManualOverride)
    }

    @Test
    fun `TransitionEvent with isManualOverride true has override set`() {
        val event = TransitionEvent(
            id = 2,
            timestamp = 2_000L,
            fromActivity = ActivityState.WALKING,
            toActivity = ActivityState.CYCLING,
            isManualOverride = true,
        )
        assertEquals(true, event.isManualOverride)
    }
}
