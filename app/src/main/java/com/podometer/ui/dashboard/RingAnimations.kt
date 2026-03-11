// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

// ─── Animation timing constants (unit-testable) ───────────────────────────────

/**
 * Duration in milliseconds for the ring arc sweep animation.
 *
 * Used as the duration for [androidx.compose.animation.core.spring] or
 * [androidx.compose.animation.core.tween] specs that drive sweep-angle changes.
 * Kept as a top-level constant so tests can verify it stays within a sensible range.
 */
const val RING_ANIMATION_DURATION_MS: Int = 700

/**
 * Duration in milliseconds for the tier-reached pulse/scale animation.
 *
 * The ring briefly scales up then returns to 1.0 when the user crosses a new tier boundary.
 */
const val TIER_PULSE_DURATION_MS: Int = 400

/**
 * Stagger delay in milliseconds between each dashboard card entrance animation.
 *
 * Card sections enter sequentially: ring → streak → today chart → weekly chart.
 * Each section starts [CARD_STAGGER_DELAY_MS] after the previous one begins.
 */
const val CARD_STAGGER_DELAY_MS: Int = 80

// ─── Goal tier model ──────────────────────────────────────────────────────────

/**
 * Represents which goal tier the current step count has reached.
 *
 * Ordered from lowest to highest achievement so that ordinal comparisons are meaningful.
 */
enum class GoalTier {
    /** Below the minimum goal. */
    None,

    /** At or above the minimum goal, but below the target goal. */
    Minimum,

    /** At or above the target goal, but below the stretch goal. */
    Target,

    /** At or above the stretch goal. */
    Stretch,
}

// ─── Pure helper functions ────────────────────────────────────────────────────

/**
 * Determines which [GoalTier] the given [steps] count has reached.
 *
 * The tiers are exclusive and ascending: [GoalTier.None] < [GoalTier.Minimum] <
 * [GoalTier.Target] < [GoalTier.Stretch].
 *
 * Pure-Kotlin function — no Android or Compose dependencies — so it can be
 * unit-tested directly on the JVM.
 *
 * @param steps       Current step count.
 * @param minimumGoal Threshold for [GoalTier.Minimum].
 * @param targetGoal  Threshold for [GoalTier.Target].
 * @param stretchGoal Threshold for [GoalTier.Stretch].
 * @return The highest [GoalTier] reached by [steps].
 */
fun resolveGoalTier(
    steps: Int,
    minimumGoal: Int,
    targetGoal: Int,
    stretchGoal: Int,
): GoalTier = when {
    steps >= stretchGoal -> GoalTier.Stretch
    steps >= targetGoal  -> GoalTier.Target
    steps >= minimumGoal -> GoalTier.Minimum
    else                 -> GoalTier.None
}

/**
 * Returns `true` when the user has just advanced to a higher [GoalTier].
 *
 * A "new tier reached" event fires only when [currentTier] is strictly higher than
 * [previousTier] — regressions (which can happen when a ViewModel emits a stale
 * snapshot) must not trigger a celebration pulse.
 *
 * Pure-Kotlin function — no Android or Compose dependencies.
 *
 * @param previousTier The tier before the most recent step-count update.
 * @param currentTier  The tier after the most recent step-count update.
 * @return `true` if the user crossed into a higher tier.
 */
fun hasNewTierBeenReached(previousTier: GoalTier, currentTier: GoalTier): Boolean =
    currentTier.ordinal > previousTier.ordinal
