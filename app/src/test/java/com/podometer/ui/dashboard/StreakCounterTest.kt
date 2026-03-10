// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StreakCounter] pure-logic helpers.
 *
 * Tests exercise [streakLabel] and [streakContentDescription] which are
 * pure-Kotlin functions testable on the JVM.
 */
class StreakCounterTest {

    // ─── streakLabel ──────────────────────────────────────────────────────────

    @Test
    fun `streakLabel returns singular day for streak of 1`() {
        val label = streakLabel(1)
        assertTrue("Should contain '1' and 'day': $label", label.contains("1") && label.contains("day"))
        assertFalse("Should not contain 'days' (plural): $label", label.contains("days"))
    }

    @Test
    fun `streakLabel returns plural days for streak of 2`() {
        val label = streakLabel(2)
        assertTrue("Should contain '2' and 'days': $label", label.contains("2") && label.contains("days"))
    }

    @Test
    fun `streakLabel handles zero streak`() {
        val label = streakLabel(0)
        assertTrue("Should contain '0': $label", label.contains("0"))
    }

    @Test
    fun `streakLabel handles large streak`() {
        val label = streakLabel(100)
        assertTrue("Should contain '100': $label", label.contains("100"))
    }

    // ─── streakContentDescription ─────────────────────────────────────────────

    @Test
    fun `streakContentDescription includes streak count`() {
        val desc = streakContentDescription(streakDays = 5, todayMet = true)
        assertTrue("Should mention 5: $desc", desc.contains("5"))
    }

    @Test
    fun `streakContentDescription mentions today met when true`() {
        val desc = streakContentDescription(streakDays = 3, todayMet = true)
        assertTrue(
            "Should mention today is met: $desc",
            desc.contains("today", ignoreCase = true) ||
                desc.contains("met", ignoreCase = true) ||
                desc.contains("goal", ignoreCase = true),
        )
    }

    @Test
    fun `streakContentDescription handles zero streak`() {
        val desc = streakContentDescription(streakDays = 0, todayMet = false)
        assertTrue("Description should not be blank for zero streak", desc.isNotBlank())
    }

    @Test
    fun `streakContentDescription handles todayMet false`() {
        val desc = streakContentDescription(streakDays = 7, todayMet = false)
        assertTrue("Description should not be blank when todayMet is false", desc.isNotBlank())
    }

    // ─── isStreakActive helper ────────────────────────────────────────────────

    @Test
    fun `isStreakActive returns false for zero streak`() {
        assertFalse(isStreakActive(0))
    }

    @Test
    fun `isStreakActive returns true for positive streak`() {
        assertTrue(isStreakActive(1))
        assertTrue(isStreakActive(10))
    }
}
