// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ProgressRing] pure-logic helpers.
 *
 * These tests exercise [computeRingProgress], [computeTierSweepAngles], and
 * [progressRingContentDescription], which are pure-Kotlin functions testable on the JVM.
 */
class ProgressRingTest {

    // ─── computeRingProgress ──────────────────────────────────────────────────

    @Test
    fun `computeRingProgress returns 0 when steps is 0`() {
        val result = computeRingProgress(
            steps = 0,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `computeRingProgress returns 1 when steps equals stretchGoal`() {
        val result = computeRingProgress(
            steps = 12_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(1f, result, 0.001f)
    }

    @Test
    fun `computeRingProgress clamps to 1 when steps exceeds stretchGoal`() {
        val result = computeRingProgress(
            steps = 15_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(1f, result, 0.001f)
    }

    @Test
    fun `computeRingProgress returns correct fraction at midpoint`() {
        // 6000 / 12000 = 0.5
        val result = computeRingProgress(
            steps = 6_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `computeRingProgress is between 0 and 1 for any valid input`() {
        val cases = listOf(0, 1_000, 5_000, 8_000, 12_000, 20_000)
        cases.forEach { steps ->
            val result = computeRingProgress(steps, 5_000, 8_000, 12_000)
            assertTrue("Expected 0..1 for steps=$steps but got $result", result in 0f..1f)
        }
    }

    // ─── computeTierSweepAngles ───────────────────────────────────────────────

    @Test
    fun `computeTierSweepAngles returns three zeroes when steps is 0`() {
        val result = computeTierSweepAngles(
            steps = 0,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(0f, result.minimumSweep, 0.001f)
        assertEquals(0f, result.targetSweep, 0.001f)
        assertEquals(0f, result.stretchSweep, 0.001f)
    }

    @Test
    fun `computeTierSweepAngles minimum tier fully filled when steps equals minimumGoal`() {
        val result = computeTierSweepAngles(
            steps = 5_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        // minimum tier spans 5000/12000 of the full 360 degrees
        val expectedMin = (5_000f / 12_000f) * 360f
        assertEquals(expectedMin, result.minimumSweep, 0.5f)
        assertEquals(0f, result.targetSweep, 0.001f)
        assertEquals(0f, result.stretchSweep, 0.001f)
    }

    @Test
    fun `computeTierSweepAngles all tiers sum to 360 degrees when steps equals stretchGoal`() {
        val result = computeTierSweepAngles(
            steps = 12_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(360f, result.minimumSweep + result.targetSweep + result.stretchSweep, 0.5f)
    }

    @Test
    fun `computeTierSweepAngles individual sweeps are non-negative`() {
        val steps = listOf(0, 3_000, 5_000, 7_000, 8_000, 10_000, 12_000)
        steps.forEach { s ->
            val result = computeTierSweepAngles(s, 5_000, 8_000, 12_000)
            assertTrue("min sweep should be >= 0 for steps=$s", result.minimumSweep >= 0f)
            assertTrue("target sweep should be >= 0 for steps=$s", result.targetSweep >= 0f)
            assertTrue("stretch sweep should be >= 0 for steps=$s", result.stretchSweep >= 0f)
        }
    }

    // ─── progressRingContentDescription ──────────────────────────────────────

    @Test
    fun `progressRingContentDescription includes step count`() {
        val desc = progressRingContentDescription(steps = 7_500, stretchGoal = 12_000)
        assertTrue("Should mention 7500: $desc", desc.contains("7500") || desc.contains("7,500"))
    }

    @Test
    fun `progressRingContentDescription includes percentage`() {
        val desc = progressRingContentDescription(steps = 6_000, stretchGoal = 12_000)
        assertTrue("Should mention 50%: $desc", desc.contains("50"))
    }

    @Test
    fun `progressRingContentDescription handles zero steps`() {
        val desc = progressRingContentDescription(steps = 0, stretchGoal = 12_000)
        assertTrue("Description should not be blank", desc.isNotBlank())
    }

    @Test
    fun `progressRingContentDescription handles steps exceeding stretch goal`() {
        val desc = progressRingContentDescription(steps = 15_000, stretchGoal = 12_000)
        assertTrue("Description should not be blank", desc.isNotBlank())
    }
}
