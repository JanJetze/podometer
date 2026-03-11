// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RingAnimations] pure-logic helpers.
 *
 * These tests exercise [resolveGoalTier] and [hasNewTierBeenReached], which are
 * pure-Kotlin functions testable on the JVM without a device or emulator.
 */
class RingAnimationsTest {

    // ─── resolveGoalTier ──────────────────────────────────────────────────────

    @Test
    fun `resolveGoalTier returns None when steps is 0`() {
        val tier = resolveGoalTier(
            steps = 0,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.None, tier)
    }

    @Test
    fun `resolveGoalTier returns None when steps is below minimum`() {
        val tier = resolveGoalTier(
            steps = 4_999,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.None, tier)
    }

    @Test
    fun `resolveGoalTier returns Minimum when steps equals minimumGoal`() {
        val tier = resolveGoalTier(
            steps = 5_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Minimum, tier)
    }

    @Test
    fun `resolveGoalTier returns Minimum when steps is between minimum and target`() {
        val tier = resolveGoalTier(
            steps = 6_500,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Minimum, tier)
    }

    @Test
    fun `resolveGoalTier returns Target when steps equals targetGoal`() {
        val tier = resolveGoalTier(
            steps = 8_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Target, tier)
    }

    @Test
    fun `resolveGoalTier returns Target when steps is between target and stretch`() {
        val tier = resolveGoalTier(
            steps = 10_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Target, tier)
    }

    @Test
    fun `resolveGoalTier returns Stretch when steps equals stretchGoal`() {
        val tier = resolveGoalTier(
            steps = 12_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Stretch, tier)
    }

    @Test
    fun `resolveGoalTier returns Stretch when steps exceeds stretchGoal`() {
        val tier = resolveGoalTier(
            steps = 15_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
        )
        assertEquals(GoalTier.Stretch, tier)
    }

    @Test
    fun `resolveGoalTier covers all tiers in ascending order`() {
        val cases = listOf(
            0 to GoalTier.None,
            2_500 to GoalTier.None,
            5_000 to GoalTier.Minimum,
            7_000 to GoalTier.Minimum,
            8_000 to GoalTier.Target,
            11_000 to GoalTier.Target,
            12_000 to GoalTier.Stretch,
            20_000 to GoalTier.Stretch,
        )
        cases.forEach { (steps, expected) ->
            val actual = resolveGoalTier(steps, 5_000, 8_000, 12_000)
            assertEquals("Expected $expected for steps=$steps but got $actual", expected, actual)
        }
    }

    // ─── hasNewTierBeenReached ────────────────────────────────────────────────

    @Test
    fun `hasNewTierBeenReached returns false when previous and current tier are the same`() {
        assertFalse(hasNewTierBeenReached(GoalTier.None, GoalTier.None))
        assertFalse(hasNewTierBeenReached(GoalTier.Minimum, GoalTier.Minimum))
        assertFalse(hasNewTierBeenReached(GoalTier.Target, GoalTier.Target))
        assertFalse(hasNewTierBeenReached(GoalTier.Stretch, GoalTier.Stretch))
    }

    @Test
    fun `hasNewTierBeenReached returns true when advancing to Minimum`() {
        assertTrue(hasNewTierBeenReached(GoalTier.None, GoalTier.Minimum))
    }

    @Test
    fun `hasNewTierBeenReached returns true when advancing to Target`() {
        assertTrue(hasNewTierBeenReached(GoalTier.Minimum, GoalTier.Target))
    }

    @Test
    fun `hasNewTierBeenReached returns true when advancing to Stretch`() {
        assertTrue(hasNewTierBeenReached(GoalTier.Target, GoalTier.Stretch))
    }

    @Test
    fun `hasNewTierBeenReached returns false when tier regresses`() {
        // Steps could theoretically go down if a ViewModel emits an older snapshot —
        // we should not fire a pulse in that direction.
        assertFalse(hasNewTierBeenReached(GoalTier.Target, GoalTier.Minimum))
        assertFalse(hasNewTierBeenReached(GoalTier.Stretch, GoalTier.Target))
        assertFalse(hasNewTierBeenReached(GoalTier.Minimum, GoalTier.None))
    }

    @Test
    fun `hasNewTierBeenReached returns true when skipping tiers (e g None to Target)`() {
        // If the user is restored from DB with an existing count that already crossed tiers,
        // first emission should still fire a celebration.
        assertTrue(hasNewTierBeenReached(GoalTier.None, GoalTier.Target))
        assertTrue(hasNewTierBeenReached(GoalTier.None, GoalTier.Stretch))
    }

    // ─── RING_ANIMATION_DURATION_MS constant ──────────────────────────────────

    @Test
    fun `RING_ANIMATION_DURATION_MS is a positive reasonable value`() {
        assertTrue(
            "RING_ANIMATION_DURATION_MS should be > 0",
            RING_ANIMATION_DURATION_MS > 0,
        )
        assertTrue(
            "RING_ANIMATION_DURATION_MS should be <= 2000ms for responsiveness",
            RING_ANIMATION_DURATION_MS <= 2_000,
        )
    }

    @Test
    fun `TIER_PULSE_DURATION_MS is a positive reasonable value`() {
        assertTrue(
            "TIER_PULSE_DURATION_MS should be > 0",
            TIER_PULSE_DURATION_MS > 0,
        )
        assertTrue(
            "TIER_PULSE_DURATION_MS should be <= 1000ms so pulse feels snappy",
            TIER_PULSE_DURATION_MS <= 1_000,
        )
    }

    @Test
    fun `CARD_STAGGER_DELAY_MS is non-negative`() {
        assertTrue(
            "CARD_STAGGER_DELAY_MS should be >= 0",
            CARD_STAGGER_DELAY_MS >= 0,
        )
    }
}
