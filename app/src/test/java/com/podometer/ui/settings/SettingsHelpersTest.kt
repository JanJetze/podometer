// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for pure-Kotlin helper functions used by the Settings screen.
 *
 * These helpers handle unit conversion between km (DataStore storage format)
 * and cm (UI display format), plus input validation for the step goal dialog.
 */
class SettingsHelpersTest {

    // ─── strideLengthKmToCm ───────────────────────────────────────────────────

    @Test
    fun `strideLengthKmToCm converts 0_00075f to 75 cm`() {
        val result = strideLengthKmToCm(0.00075f)
        assertEquals(75, result)
    }

    @Test
    fun `strideLengthKmToCm converts 0_001f to 100 cm`() {
        val result = strideLengthKmToCm(0.001f)
        assertEquals(100, result)
    }

    @Test
    fun `strideLengthKmToCm converts 0_0005f to 50 cm`() {
        val result = strideLengthKmToCm(0.0005f)
        assertEquals(50, result)
    }

    @Test
    fun `strideLengthKmToCm converts 0_0012f to 120 cm`() {
        val result = strideLengthKmToCm(0.0012f)
        assertEquals(120, result)
    }

    // ─── strideLengthCmToKm ───────────────────────────────────────────────────

    @Test
    fun `strideLengthCmToKm converts 75 cm to 0_00075f`() {
        val result = strideLengthCmToKm(75)
        assertEquals(0.00075f, result, 0.0000001f)
    }

    @Test
    fun `strideLengthCmToKm converts 100 cm to 0_001f`() {
        val result = strideLengthCmToKm(100)
        assertEquals(0.001f, result, 0.0000001f)
    }

    @Test
    fun `strideLengthCmToKm converts 50 cm to 0_0005f`() {
        val result = strideLengthCmToKm(50)
        assertEquals(0.0005f, result, 0.0000001f)
    }

    @Test
    fun `strideLengthCmToKm converts 120 cm to 0_0012f`() {
        val result = strideLengthCmToKm(120)
        assertEquals(0.0012f, result, 0.0000001f)
    }

    @Test
    fun `strideLengthCmToKm is inverse of strideLengthKmToCm for 75`() {
        val original = 75
        val km = strideLengthCmToKm(original)
        val backToCm = strideLengthKmToCm(km)
        assertEquals(original, backToCm)
    }

    @Test
    fun `strideLengthKmToCm is inverse of strideLengthCmToKm for 0_00075f`() {
        val original = 0.00075f
        val cm = strideLengthKmToCm(original)
        val backToKm = strideLengthCmToKm(cm)
        assertEquals(original, backToKm, 0.0000001f)
    }

    // ─── validateStepGoal ─────────────────────────────────────────────────────

    @Test
    fun `validateStepGoal returns null for empty input`() {
        val result = validateStepGoal("")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns null for blank input`() {
        val result = validateStepGoal("   ")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns null for non-numeric input`() {
        val result = validateStepGoal("abc")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns null for zero`() {
        val result = validateStepGoal("0")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns null for negative number`() {
        val result = validateStepGoal("-100")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns parsed int for valid input 10000`() {
        val result = validateStepGoal("10000")
        assertEquals(10_000, result)
    }

    @Test
    fun `validateStepGoal returns parsed int for valid input 5000`() {
        val result = validateStepGoal("5000")
        assertEquals(5_000, result)
    }

    @Test
    fun `validateStepGoal returns parsed int for minimum valid value 1`() {
        val result = validateStepGoal("1")
        assertEquals(1, result)
    }

    @Test
    fun `validateStepGoal returns null for value exceeding maximum 100000`() {
        val result = validateStepGoal("100001")
        assertNull(result)
    }

    @Test
    fun `validateStepGoal returns 100000 for exact maximum`() {
        val result = validateStepGoal("100000")
        assertEquals(100_000, result)
    }

    @Test
    fun `validateStepGoal handles leading and trailing whitespace`() {
        val result = validateStepGoal("  8000  ")
        assertEquals(8_000, result)
    }

    @Test
    fun `validateStepGoal returns null for decimal input`() {
        val result = validateStepGoal("5000.5")
        assertNull(result)
    }
}
