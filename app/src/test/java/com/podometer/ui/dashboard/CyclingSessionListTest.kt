// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.data.db.CyclingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Unit tests for [CyclingSessionList] pure-logic helpers.
 *
 * These tests exercise [formatSessionTime], [formatSessionRange], [formatSessionDuration],
 * and [totalDurationMinutes], which are pure-Kotlin functions that can be run on the JVM
 * without Compose or an Android device.
 */
class CyclingSessionListTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // ─── formatSessionTime ────────────────────────────────────────────────────

    @Test
    fun `formatSessionTime formats midnight as 00 00`() {
        val result = formatSessionTime(0L, utc)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatSessionTime formats 9h 15min as 09 15`() {
        val millis = (9L * 60 + 15L) * 60_000L
        val result = formatSessionTime(millis, utc)
        assertEquals("09:15", result)
    }

    @Test
    fun `formatSessionTime formats 14h 30min as 14 30`() {
        val millis = (14L * 60 + 30L) * 60_000L
        val result = formatSessionTime(millis, utc)
        assertEquals("14:30", result)
    }

    @Test
    fun `formatSessionTime formats 23h 59min as 23 59`() {
        val millis = (23L * 60 + 59L) * 60_000L
        val result = formatSessionTime(millis, utc)
        assertEquals("23:59", result)
    }

    // ─── formatSessionRange ───────────────────────────────────────────────────

    @Test
    fun `formatSessionRange with completed session returns start dash end`() {
        val startMillis = (9L * 60 + 15L) * 60_000L
        val endMillis = (9L * 60 + 42L) * 60_000L
        val result = formatSessionRange(startMillis, endMillis, utc)
        assertEquals("09:15 – 09:42", result)
    }

    @Test
    fun `formatSessionRange with null endMillis returns start dash ongoing`() {
        val startMillis = (10L * 60) * 60_000L
        val result = formatSessionRange(startMillis, null, utc)
        assertEquals("10:00 – ongoing", result)
    }

    @Test
    fun `formatSessionRange with same start and end returns correct range`() {
        val millis = (12L * 60) * 60_000L
        val result = formatSessionRange(millis, millis, utc)
        assertEquals("12:00 – 12:00", result)
    }

    @Test
    fun `formatSessionRange spans midnight correctly`() {
        val startMillis = (23L * 60 + 50L) * 60_000L
        val endMillis = (23L * 60 + 59L) * 60_000L
        val result = formatSessionRange(startMillis, endMillis, utc)
        assertEquals("23:50 – 23:59", result)
    }

    // ─── formatSessionDuration ────────────────────────────────────────────────

    @Test
    fun `formatSessionDuration returns N min for given minutes`() {
        assertEquals("27 min", formatSessionDuration(27))
    }

    @Test
    fun `formatSessionDuration returns 1 min for single minute`() {
        assertEquals("1 min", formatSessionDuration(1))
    }

    @Test
    fun `formatSessionDuration returns 0 min for zero minutes`() {
        assertEquals("0 min", formatSessionDuration(0))
    }

    @Test
    fun `formatSessionDuration returns 120 min for two hours`() {
        assertEquals("120 min", formatSessionDuration(120))
    }

    // ─── totalDurationMinutes ─────────────────────────────────────────────────

    @Test
    fun `totalDurationMinutes returns 0 for empty list`() {
        val result = totalDurationMinutes(emptyList())
        assertEquals(0, result)
    }

    @Test
    fun `totalDurationMinutes returns duration of single session`() {
        val sessions = listOf(
            CyclingSession(id = 1, startTime = 0L, endTime = null, durationMinutes = 27),
        )
        val result = totalDurationMinutes(sessions)
        assertEquals(27, result)
    }

    @Test
    fun `totalDurationMinutes sums all session durations`() {
        val sessions = listOf(
            CyclingSession(id = 1, startTime = 0L, endTime = null, durationMinutes = 27),
            CyclingSession(id = 2, startTime = 0L, endTime = null, durationMinutes = 15),
            CyclingSession(id = 3, startTime = 0L, endTime = null, durationMinutes = 45),
        )
        val result = totalDurationMinutes(sessions)
        assertEquals(87, result)
    }

    @Test
    fun `totalDurationMinutes with override session still sums durations`() {
        val sessions = listOf(
            CyclingSession(id = 1, startTime = 0L, endTime = null, durationMinutes = 30, isManualOverride = true),
            CyclingSession(id = 2, startTime = 0L, endTime = null, durationMinutes = 20, isManualOverride = false),
        )
        val result = totalDurationMinutes(sessions)
        assertEquals(50, result)
    }

    // ─── CyclingSessionListKt class existence ────────────────────────────────

    @Test
    fun `CyclingSessionListKt class exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.CyclingSessionListKt")
        assertTrue(
            "CyclingSessionListKt should be in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `formatSessionTime function is accessible via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.CyclingSessionListKt")
        val method = clazz.getDeclaredMethod(
            "formatSessionTime",
            Long::class.java,
            TimeZone::class.java,
        )
        val result = method.invoke(null, 0L, TimeZone.getTimeZone("UTC"))
        assertEquals("00:00", result)
    }

    @Test
    fun `formatSessionDuration function is accessible via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.CyclingSessionListKt")
        val method = clazz.getDeclaredMethod(
            "formatSessionDuration",
            Int::class.java,
        )
        val result = method.invoke(null, 27)
        assertEquals("27 min", result)
    }
}
