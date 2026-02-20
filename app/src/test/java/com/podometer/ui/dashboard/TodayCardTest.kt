// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TodayCard] pure-logic helpers.
 *
 * Compose composables cannot be rendered in JVM unit tests without a device/emulator,
 * so these tests cover the pure-Kotlin logic extracted from [TodayCard]:
 *  - [formatDistance]: distance formatting
 *  - [clampProgress]: progress clamping to [0f, 1f]
 *  - The [TodayCard] composable class itself exists in the expected package.
 */
class TodayCardTest {

    // ─── formatDistance ──────────────────────────────────────────────────────

    @Test
    fun `formatDistance formats zero km`() {
        assertEquals("0.0 km", formatDistance(0f))
    }

    @Test
    fun `formatDistance formats whole km with one decimal`() {
        assertEquals("5.0 km", formatDistance(5f))
    }

    @Test
    fun `formatDistance rounds to one decimal place`() {
        assertEquals("3.8 km", formatDistance(3.75f))
    }

    @Test
    fun `formatDistance formats fractional km`() {
        assertEquals("1.5 km", formatDistance(1.5f))
    }

    @Test
    fun `formatDistance formats large distance`() {
        assertEquals("42.2 km", formatDistance(42.195f))
    }

    @Test
    fun `formatDistance formats single digit km`() {
        assertEquals("9.9 km", formatDistance(9.95f))
    }

    // ─── clampProgress ───────────────────────────────────────────────────────

    @Test
    fun `clampProgress returns zero for zero input`() {
        assertEquals(0f, clampProgress(0f), 0.001f)
    }

    @Test
    fun `clampProgress returns one for one input`() {
        assertEquals(1f, clampProgress(1f), 0.001f)
    }

    @Test
    fun `clampProgress clamps negative to zero`() {
        assertEquals(0f, clampProgress(-0.5f), 0.001f)
    }

    @Test
    fun `clampProgress clamps above one to one`() {
        assertEquals(1f, clampProgress(1.5f), 0.001f)
    }

    @Test
    fun `clampProgress passes through midrange value`() {
        assertEquals(0.75f, clampProgress(0.75f), 0.001f)
    }

    @Test
    fun `clampProgress passes through 0_5`() {
        assertEquals(0.5f, clampProgress(0.5f), 0.001f)
    }

    // ─── TodayCard class existence ───────────────────────────────────────────

    @Test
    fun `TodayCardKt exists in com_podometer_ui_dashboard package`() {
        // Verify the file compiled into the expected package by checking
        // the top-level function is reachable via reflection.
        val clazz = Class.forName("com.podometer.ui.dashboard.TodayCardKt")
        assertTrue(
            "TodayCardKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `formatDistance function is accessible as top-level function`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.TodayCardKt")
        val method = clazz.getDeclaredMethod("formatDistance", Float::class.java)
        val result = method.invoke(null, 7.5f) as String
        assertEquals("7.5 km", result)
    }

    @Test
    fun `clampProgress function is accessible as top-level function`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.TodayCardKt")
        val method = clazz.getDeclaredMethod("clampProgress", Float::class.java)
        val result = method.invoke(null, 0.3f) as Float
        assertEquals(0.3f, result, 0.001f)
    }
}
