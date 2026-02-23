// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [formatStepCount] and [isNewDay] pure-Kotlin helpers in [FormatHelpers].
 */
class FormatHelpersTest {

    // ─── formatStepCount ─────────────────────────────────────────────────────

    @Test
    fun `formatStepCount formats zero steps`() {
        val result = formatStepCount(0)
        assertEquals("0", result)
    }

    @Test
    fun `formatStepCount formats three-digit number without separator`() {
        val result = formatStepCount(999)
        assertEquals("999", result)
    }

    @Test
    fun `formatStepCount formats 1000 with thousands separator`() {
        // Use Locale.US to verify comma grouping
        val result = formatStepCount(1_000, Locale.US)
        assertEquals("1,000", result)
    }

    @Test
    fun `formatStepCount formats 10000 with thousands separator`() {
        val result = formatStepCount(10_000, Locale.US)
        assertEquals("10,000", result)
    }

    @Test
    fun `formatStepCount formats 100000 with thousands separator`() {
        val result = formatStepCount(100_000, Locale.US)
        assertEquals("100,000", result)
    }

    @Test
    fun `formatStepCount formats 1000000 with correct separators`() {
        val result = formatStepCount(1_000_000, Locale.US)
        assertEquals("1,000,000", result)
    }

    @Test
    fun `formatStepCount formats 9999 with thousands separator`() {
        val result = formatStepCount(9_999, Locale.US)
        assertEquals("9,999", result)
    }

    @Test
    fun `formatStepCount returns a non-empty string for any positive int`() {
        assertTrue(formatStepCount(42).isNotEmpty())
    }

    // ─── isNewDay ─────────────────────────────────────────────────────────────

    @Test
    fun `isNewDay returns false when dates are equal`() {
        assertFalse(isNewDay("2026-02-23", "2026-02-23"))
    }

    @Test
    fun `isNewDay returns true when current date is one day after last known date`() {
        assertTrue(isNewDay("2026-02-23", "2026-02-24"))
    }

    @Test
    fun `isNewDay returns true when current date is multiple days after last known date`() {
        assertTrue(isNewDay("2026-01-01", "2026-02-23"))
    }

    @Test
    fun `isNewDay returns false when current date is before last known date`() {
        assertFalse(isNewDay("2026-02-24", "2026-02-23"))
    }

    @Test
    fun `isNewDay returns true across month boundary`() {
        assertTrue(isNewDay("2026-01-31", "2026-02-01"))
    }

    @Test
    fun `isNewDay returns true across year boundary`() {
        assertTrue(isNewDay("2025-12-31", "2026-01-01"))
    }

    @Test
    fun `isNewDay returns false for same day in different year format representation`() {
        // Both are the same day
        assertFalse(isNewDay("2026-02-23", "2026-02-23"))
    }

    // ─── Class existence checks ───────────────────────────────────────────────

    @Test
    fun `FormatHelpersKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.FormatHelpersKt")
        assertTrue(
            "FormatHelpersKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }
}
