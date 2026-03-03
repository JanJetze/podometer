// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/**
 * Unit tests for [ActivityLog] pure-logic helpers.
 *
 * These tests exercise [formatActivityTime], [formatActivityRange],
 * [formatActivityDuration], and [activityLabel], which are pure-Kotlin functions
 * that can be run on the JVM without Compose or an Android device.
 */
class ActivityLogTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // ─── formatActivityTime ────────────────────────────────────────────────────

    @Test
    fun `formatActivityTime formats midnight as 00 00`() {
        assertEquals("00:00", formatActivityTime(0L, utc))
    }

    @Test
    fun `formatActivityTime formats 9h 15min as 09 15`() {
        val millis = (9L * 60 + 15L) * 60_000L
        assertEquals("09:15", formatActivityTime(millis, utc))
    }

    @Test
    fun `formatActivityTime formats 14h 30min as 14 30`() {
        val millis = (14L * 60 + 30L) * 60_000L
        assertEquals("14:30", formatActivityTime(millis, utc))
    }

    // ─── formatActivityRange ───────────────────────────────────────────────────

    @Test
    fun `formatActivityRange with completed session returns start dash end`() {
        val startMillis = (9L * 60 + 15L) * 60_000L
        val endMillis = (10L * 60 + 30L) * 60_000L
        assertEquals("09:15 – 10:30", formatActivityRange(startMillis, endMillis, utc))
    }

    @Test
    fun `formatActivityRange with null end returns start dash`() {
        val startMillis = (14L * 60) * 60_000L
        assertEquals("14:00 –", formatActivityRange(startMillis, null, utc))
    }

    // ─── formatActivityDuration ────────────────────────────────────────────────

    @Test
    fun `formatActivityDuration null returns ongoing`() {
        assertEquals("ongoing", formatActivityDuration(null))
    }

    @Test
    fun `formatActivityDuration 75 minutes returns 1h 15m`() {
        assertEquals("1h 15m", formatActivityDuration(75L * 60_000L))
    }

    @Test
    fun `formatActivityDuration 45 minutes returns 45m`() {
        assertEquals("45m", formatActivityDuration(45L * 60_000L))
    }

    @Test
    fun `formatActivityDuration 120 minutes returns 2h`() {
        assertEquals("2h", formatActivityDuration(120L * 60_000L))
    }

    @Test
    fun `formatActivityDuration 0 minutes returns 0m`() {
        assertEquals("0m", formatActivityDuration(0L))
    }

    // ─── activityLabel ─────────────────────────────────────────────────────────

    @Test
    fun `activityLabel WALKING returns Walking`() {
        assertEquals("Walking", activityLabel(ActivityState.WALKING))
    }

    @Test
    fun `activityLabel CYCLING returns Cycling`() {
        assertEquals("Cycling", activityLabel(ActivityState.CYCLING))
    }

    @Test
    fun `activityLabel STILL returns Still`() {
        assertEquals("Still", activityLabel(ActivityState.STILL))
    }
}
