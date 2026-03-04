// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Unit tests for [CyclingClassifier].
 *
 * Uses synthetic [WindowFeatures] and step-frequency values to verify
 * state-machine transitions without any Android framework dependency.
 *
 * Threshold defaults used in tests (unless overridden):
 *   varianceThreshold      = 2.0  (m/s²)²
 *   stepFreqThreshold      = 0.3  Hz
 *   consecutiveWindows     = 2
 *   stillVarianceThreshold = 0.5  (m/s²)²
 *   minCyclingDurationMs   = 60_000L
 *
 * Timestamp conventions:
 *   T0          = 0L      — first cycling window
 *   T_61S       = 61_000L — 61 seconds later (satisfies >=60 s gate)
 *   T_59S       = 59_000L — 59 seconds later (does NOT satisfy gate)
 */
class CyclingClassifierTest {

    private lateinit var classifier: CyclingClassifier

    companion object {
        // Variance values relative to thresholds
        private const val HIGH_VARIANCE = 3.0     // above DEFAULT_VARIANCE_THRESHOLD (2.0)
        private const val LOW_VARIANCE = 1.0      // below DEFAULT_VARIANCE_THRESHOLD, above STILL
        private const val STILL_VARIANCE = 0.2    // below DEFAULT_STILL_VARIANCE_THRESHOLD (0.5)

        // Step frequency values relative to threshold (0.3 Hz)
        private const val CYCLING_STEP_FREQ = 0.1  // below threshold — very few steps
        private const val WALKING_STEP_FREQ = 2.0  // well above threshold — active walking
        private const val ZERO_STEP_FREQ = 0.0     // no steps at all

        private const val DELTA = 1e-9

        // Timestamps
        private const val T0 = 0L
        private const val T_61S = 61_000L
        private const val T_59S = 59_000L
        private const val T_30S = 30_000L
        private const val T_65S = 65_000L

        /**
         * Interval between simulated 5-second windows so that a series of N
         * windows spans at least 60 seconds when N * WINDOW_INTERVAL_MS >= 60_000.
         */
        private const val WINDOW_INTERVAL_MS = 5_000L
    }

    @Before
    fun setUp() {
        // Use minCyclingDurationMs = 0 so that existing consecutive-window tests
        // are not broken by the duration gate.  Grace periods, walking entry
        // threshold, and cycling exit threshold are also disabled so existing
        // tests behave as before.
        classifier = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            consecutiveWalkingWindowsForCyclingExit = 1,
        )
    }

    // ─── Helper to build a WindowFeatures with given variance ─────────────────

    private fun featuresWithVariance(variance: Double): WindowFeatures = WindowFeatures(
        magnitudeMean = 9.81,
        magnitudeStd = Math.sqrt(variance),
        magnitudeVariance = variance,
        sampleCount = 20,
        windowDurationMs = 4_000L,
        zeroCrossingRate = 1.0,
    )

    // ─── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state is STILL`() {
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Still state: low variance and no steps → remains STILL ──────────────

    @Test
    fun `still state - very low variance and zero steps - no transition`() {
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, T0)
        assertNull("Expected no transition for still window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `still state - low variance stays non-cycling - no transition`() {
        // LOW_VARIANCE is below varianceThreshold but above stillVarianceThreshold
        // With zero steps, should stay in STILL
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), ZERO_STEP_FREQ, T0)
        assertNull("Expected no transition for low-variance window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Walking detection: moderate variance, high step frequency ────────────

    @Test
    fun `walking - high step frequency prevents cycling detection even with high variance`() {
        // High variance but WALKING_STEP_FREQ is above stepFrequencyThreshold → not a cycling window
        // With the STILL → WALKING fix, this should now produce a WALKING transition
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertNotNull("Walking step freq from STILL should trigger WALKING transition", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `walking - consecutive windows with walking step freq do not trigger cycling`() {
        // First window: STILL → WALKING transition
        val firstResult = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertNotNull("First walking window should trigger STILL → WALKING transition", firstResult)
        assertEquals(ActivityState.WALKING, firstResult!!.toState)

        // Subsequent windows: no cycling transition (walking step freq blocks it), no duplicate WALKING
        for (i in 1 until 5) {
            val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0 + i * WINDOW_INTERVAL_MS)
            assertNull("No cycling transition when walking step freq present", result)
        }
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    // ─── STILL → WALKING transition ──────────────────────────────────────────

    @Test
    fun `still to walking - walking window from STILL transitions to WALKING`() {
        // High variance + high step frequency = walking (not cycling, not still)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertNotNull("Walking window from STILL should emit a WALKING transition", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `still to walking - no duplicate transition when already WALKING`() {
        // First window triggers STILL → WALKING
        val result1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertNotNull(result1)
        assertEquals(ActivityState.WALKING, result1!!.toState)

        // Subsequent walking windows must not emit another transition
        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0 + WINDOW_INTERVAL_MS)
        assertNull("No duplicate transition when already WALKING", result2)
        val result3 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0 + 2 * WINDOW_INTERVAL_MS)
        assertNull("No duplicate transition when already WALKING", result3)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    // ─── Consecutive windows requirement ──────────────────────────────────────

    @Test
    fun `single cycling window does not trigger transition`() {
        // Only 1 cycling window — require 2 (DEFAULT_CONSECUTIVE_WINDOWS)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        assertNull("Single cycling window must not trigger transition", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `two consecutive cycling windows trigger transition to CYCLING`() {
        val result1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        assertNull("First cycling window should not trigger", result1)

        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNotNull("Second cycling window should trigger transition", result2)
        assertEquals(ActivityState.STILL, result2!!.fromState)
        assertEquals(ActivityState.CYCLING, result2.toState)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    @Test
    fun `non-cycling window between cycling windows resets consecutive count`() {
        // First cycling window — state stays STILL (only 1 of 2 required)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)

        // Non-cycling interruption (walking step freq) — triggers STILL → WALKING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_30S)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())

        // Second cycling window (first after reset) — count=1, not enough for 2 required
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNull("Consecutive count reset by non-cycling window; only 1 window so far", result)
        // State is WALKING (cycling check not satisfied yet, no walking transition since it's a cycling window)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())

        // Third cycling window — second consecutive cycling window, triggers WALKING → CYCLING
        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNotNull("Second consecutive cycling window triggers cycling", result2)
        assertEquals(ActivityState.CYCLING, result2!!.toState)
    }

    // ─── Cycling to WALKING transition ────────────────────────────────────────

    @Test
    fun `cycling to walking - steps resume after cycling`() {
        // Transition to CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        // Now steps resume (WALKING_STEP_FREQ above threshold) → transition back to WALKING
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_65S)
        assertNotNull("Resuming steps while in CYCLING should trigger WALKING transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `cycling to walking - ambiguous window stays CYCLING`() {
        // Reach CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)

        // LOW_VARIANCE + CYCLING_STEP_FREQ is not cycling, not still, and not walking
        // (step freq too low to be walking) — stays CYCLING
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNull("Ambiguous window (low variance, low step freq) should stay CYCLING", result)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    // ─── STILL detection ─────────────────────────────────────────────────────

    @Test
    fun `still detection - very low variance and near-zero steps from WALKING`() {
        // Manually move to WALKING first by triggering cycling then walking
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_65S)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())

        // Now still-level variance with zero steps → should transition to STILL
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, T_65S + WINDOW_INTERVAL_MS)
        assertNotNull("Still variance + no steps from WALKING should transition to STILL", result)
        assertEquals(ActivityState.WALKING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `still detection from CYCLING - very low variance transitions to STILL`() {
        // Reach CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        // Still variance with zero steps
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, T_65S)
        assertNotNull("Still variance + no steps from CYCLING should trigger transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── No duplicate transitions ─────────────────────────────────────────────

    @Test
    fun `repeated cycling windows after entering CYCLING do not emit further transitions`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val transition = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNotNull(transition)

        // Additional cycling windows while already CYCLING → no new transition
        val extra1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNull("No duplicate transition when already CYCLING", extra1)
        val extra2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S + WINDOW_INTERVAL_MS)
        assertNull("No duplicate transition when already CYCLING", extra2)

        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    @Test
    fun `staying still emits no transitions`() {
        repeat(10) { i ->
            val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, T0 + i * WINDOW_INTERVAL_MS)
            assertNull("No transitions when already STILL", result)
        }
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Reset ───────────────────────────────────────────────────────────────

    @Test
    fun `reset clears state back to STILL`() {
        // Advance to CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        classifier.reset()
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset clears consecutive window counter`() {
        // One cycling window before reset
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.reset()

        // After reset, single cycling window should not trigger (counter was cleared)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNull("Counter should be zero after reset", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset allows fresh cycling detection`() {
        // Full cycle then reset
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        classifier.reset()

        // Need 2 new consecutive cycling windows to re-detect
        val r1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNull(r1)
        val r2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S + WINDOW_INTERVAL_MS)
        assertNotNull("Should re-detect cycling after reset + 2 windows (minCyclingDurationMs=0)", r2)
        assertEquals(ActivityState.CYCLING, r2!!.toState)
    }

    @Test
    fun `reset clears cycling window start timestamp`() {
        // Use a classifier with the real 60 s duration gate
        val durationClassifier = CyclingClassifier(
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )

        // Accumulate one cycling window at T0
        durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        // Reset wipes the start timestamp
        durationClassifier.reset()

        // After reset, even with timestamps spanning >60 s from T0, only 1 consecutive window
        // has elapsed — should NOT trigger
        val result = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNull("After reset the cyclingWindowStartTimeMs must be cleared", result)
        assertEquals(ActivityState.STILL, durationClassifier.getCurrentState())
    }

    // ─── 60-second duration gate ──────────────────────────────────────────────

    /**
     * Spec requirement: "step counter reports zero/very few steps for >60 seconds
     * → classify as cycling".
     *
     * Consecutive cycling windows spanning less than 60 seconds must NOT trigger
     * the CYCLING transition even when the consecutive-windows count is satisfied.
     */
    @Test
    fun `duration gate - cycling windows spanning less than 60 seconds do NOT trigger transition`() {
        val durationClassifier = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Window 1 at T=0
        val r1 = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        assertNull(r1)
        // Window 2 at T=59 s — only 59 s elapsed, below the 60 s gate
        val r2 = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_59S)
        assertNull("59-second span must not satisfy the 60-second duration gate", r2)
        assertEquals(ActivityState.STILL, durationClassifier.getCurrentState())
    }

    @Test
    fun `duration gate - cycling windows spanning exactly 60 seconds DO trigger transition`() {
        val durationClassifier = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Window 1 at T=0
        durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        // Window 2 at T=60 s — exactly at threshold (>= 60 000 ms elapsed)
        val result = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 60_000L)
        assertNotNull("60-second span must satisfy the duration gate", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `duration gate - cycling windows spanning more than 60 seconds DO trigger transition`() {
        val durationClassifier = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val result = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNotNull("61-second span must satisfy the duration gate", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `duration gate - many short windows accumulate to satisfy 60-second requirement`() {
        // 13 consecutive 5-second windows = 60 s of cycling data (windows 0..12)
        val durationClassifier = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        var lastResult: TransitionResult? = null
        // Windows at 0, 5, 10, …, 55 s: only 11 windows, spanning 55 s — not enough
        for (i in 0 until 12) {
            val t = i * WINDOW_INTERVAL_MS
            lastResult = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, t)
        }
        // 12 windows spans 0–55 s = 55 s elapsed: still below 60 s gate
        assertNull("55-second span (12 windows at 5 s each) must not trigger", lastResult)

        // Window 13 at 60 s: exactly 60 s elapsed from the first window
        lastResult = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 60_000L)
        assertNotNull("13th window at t=60 s must finally satisfy duration gate", lastResult)
        assertEquals(ActivityState.CYCLING, lastResult!!.toState)
    }

    @Test
    fun `duration gate - streak interrupted resets start time`() {
        val durationClassifier = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Build up 55 s of cycling windows (12 windows at t=0,5,...,55 s)
        // Duration gate not yet satisfied (55 s < 60 s required), so state stays STILL
        for (i in 0 until 12) {
            durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
        }
        assertEquals(ActivityState.STILL, durationClassifier.getCurrentState())

        // Interruption at t=55 s with walking step freq — triggers STILL → WALKING
        durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 55_000L)
        assertEquals(ActivityState.WALKING, durationClassifier.getCurrentState())

        // New streak starts at T=60 s; second window 5 s later is only 5 s from new start
        durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 60_000L)
        val result = durationClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 65_000L)
        assertNull("After streak reset, new streak spanning 5 s must not satisfy 60-second gate", result)
        // State is WALKING: consecutive cycling windows insufficient duration, no cycling transition
        assertEquals(ActivityState.WALKING, durationClassifier.getCurrentState())
    }

    @Test
    fun `DEFAULT_MIN_CYCLING_DURATION_MS is 60_000`() {
        assertEquals(60_000L, CyclingClassifier.DEFAULT_MIN_CYCLING_DURATION_MS)
    }

    // ─── Configurable thresholds ──────────────────────────────────────────────

    @Test
    fun `custom variance threshold - lower threshold triggers cycling at lower variance`() {
        val lowThresholdClassifier = CyclingClassifier(
            varianceThreshold = 0.5,          // lower than the 1.0 we'll use
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Use variance of 1.0 which is above the 0.5 threshold but below default 2.0
        val customVariance = 1.0

        lowThresholdClassifier.evaluate(featuresWithVariance(customVariance), CYCLING_STEP_FREQ, T0)
        val result = lowThresholdClassifier.evaluate(featuresWithVariance(customVariance), CYCLING_STEP_FREQ, T_61S)
        assertNotNull("Custom lower threshold should detect cycling at variance $customVariance", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom step frequency threshold - higher threshold triggers cycling with more steps`() {
        val highFreqThresholdClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 3.0,    // higher threshold: 3.0 Hz
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // At 2.0 Hz steps (WALKING_STEP_FREQ) — above default 0.3 but below custom 3.0 threshold
        // So with custom classifier, 2.0 Hz steps still count as "cycling-like"
        highFreqThresholdClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        val result = highFreqThresholdClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_61S)
        assertNotNull("At 2.0 Hz steps, custom 3.0 Hz threshold should still detect cycling", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom consecutive windows required - 1 window sufficient`() {
        val singleWindowClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 1,
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        val result = singleWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        assertNotNull("Single window required: should detect on first window", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom consecutive windows required - 3 windows required`() {
        val threeWindowClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 3,
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val r2 = threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_30S)
        assertNull("Two windows not enough when 3 required", r2)
        val r3 = threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNotNull("Third window triggers cycling with 3 required", r3)
        assertEquals(ActivityState.CYCLING, r3!!.toState)
    }

    // ─── TransitionResult data class ─────────────────────────────────────────

    @Test
    fun `TransitionResult has correct fromState and toState`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)!!
        assertEquals(ActivityState.STILL, result.fromState)
        assertEquals(ActivityState.CYCLING, result.toState)
    }

    @Test
    fun `TransitionResult is data class with equality semantics`() {
        val r1 = TransitionResult(ActivityState.STILL, ActivityState.CYCLING, 0L)
        val r2 = TransitionResult(ActivityState.STILL, ActivityState.CYCLING, 0L)
        assertEquals(r1, r2)
    }

    // ─── Companion object constants ───────────────────────────────────────────

    @Test
    fun `DEFAULT_VARIANCE_THRESHOLD is 2_0`() {
        assertEquals(2.0, CyclingClassifier.DEFAULT_VARIANCE_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_STEP_FREQ_THRESHOLD is 0_3`() {
        assertEquals(0.3, CyclingClassifier.DEFAULT_STEP_FREQ_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_CONSECUTIVE_WINDOWS is 2`() {
        assertEquals(2, CyclingClassifier.DEFAULT_CONSECUTIVE_WINDOWS)
    }

    @Test
    fun `DEFAULT_STILL_VARIANCE_THRESHOLD is 0_5`() {
        assertEquals(0.5, CyclingClassifier.DEFAULT_STILL_VARIANCE_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_WALKING_GRACE_PERIOD_MS is 120_000`() {
        assertEquals(120_000L, CyclingClassifier.DEFAULT_WALKING_GRACE_PERIOD_MS)
    }

    @Test
    fun `DEFAULT_CYCLING_GRACE_PERIOD_MS is 180_000`() {
        assertEquals(180_000L, CyclingClassifier.DEFAULT_CYCLING_GRACE_PERIOD_MS)
    }

    @Test
    fun `DEFAULT_CONSECUTIVE_WALKING_WINDOWS is 36`() {
        assertEquals(36, CyclingClassifier.DEFAULT_CONSECUTIVE_WALKING_WINDOWS)
    }

    @Test
    fun `DEFAULT_CADENCE_CV_THRESHOLD is 0_35`() {
        assertEquals(0.35, CyclingClassifier.DEFAULT_CADENCE_CV_THRESHOLD, DELTA)
    }

    // ─── Walking grace period ─────────────────────────────────────────────────

    @Test
    fun `grace period - walking plus still window stays WALKING within 2 min`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 120_000L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Still window at T=5 s — within 2 min grace period
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 5_000L)
        assertNull("Should stay WALKING within grace period", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    @Test
    fun `grace period - walking plus still for 2 min transitions to STILL`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 120_000L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Still windows spanning 2 minutes
        val stillStart = 10_000L
        var t = stillStart
        while (t < stillStart + 120_000L) {
            val r = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, t)
            assertNull("Should stay WALKING during grace period at t=$t", r)
            t += WINDOW_INTERVAL_MS
        }

        // Window at stillStart + 120_000 should trigger transition
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, stillStart + 120_000L)
        assertNotNull("Should transition to STILL after 2 min grace period", result)
        assertEquals(ActivityState.WALKING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(stillStart, result.effectiveTimestamp)
    }

    // ─── Cycling grace period ─────────────────────────────────────────────────

    @Test
    fun `grace period - cycling plus still window stays CYCLING within 3 min`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 180_000L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // Still window at T=10 s — within 3 min grace period
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 10_000L)
        assertNull("Should stay CYCLING within grace period", result)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())
    }

    @Test
    fun `grace period - cycling plus still for 3 min transitions to STILL`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 180_000L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // Still windows spanning 3 minutes
        val stillStart = 10_000L
        var t = stillStart
        while (t < stillStart + 180_000L) {
            val r = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, t)
            assertNull("Should stay CYCLING during grace period at t=$t", r)
            t += WINDOW_INTERVAL_MS
        }

        // Window at stillStart + 180_000 should trigger transition
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, stillStart + 180_000L)
        assertNotNull("Should transition to STILL after 3 min grace period", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(stillStart, result.effectiveTimestamp)
    }

    // ─── Grace period reset ───────────────────────────────────────────────────

    @Test
    fun `grace period reset - walking plus still plus walking resets counter`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 120_000L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Several still windows (within grace period)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 10_000L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 15_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Walking window breaks the still streak, resets grace period
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 20_000L)
        assertNull("No transition — still in WALKING", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // New still windows — grace period starts fresh from 25 s
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Even after original stillStart + 120 s (10_000 + 120_000 = 130_000), no transition
        // because the still counter was reset at 20_000. New grace started at 25_000.
        val shouldStayWalking = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 130_000L)
        assertNull("Grace period was reset; 130s from new start (25s) is only 105s", shouldStayWalking)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    // ─── Walking entry threshold ──────────────────────────────────────────────

    @Test
    fun `walking entry - 1 to 3 walking windows stays STILL`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
        )
        for (i in 0 until 3) {
            val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
            assertNull("Walking window $i of 3 should not trigger transition (need 4)", result)
            assertEquals(ActivityState.STILL, gc.getCurrentState())
        }
    }

    @Test
    fun `walking entry - 4 walking windows transitions to WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
        )
        for (i in 0 until 3) {
            gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
        }
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 3 * WINDOW_INTERVAL_MS)
        assertNotNull("4th walking window should trigger STILL → WALKING", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
    }

    @Test
    fun `walking entry - interrupted by still window resets counter`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
        )
        // 3 walking windows
        for (i in 0 until 3) {
            gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
        }
        // Still window interrupts
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 3 * WINDOW_INTERVAL_MS)
        assertEquals(ActivityState.STILL, gc.getCurrentState())

        // 3 more walking windows — only 3 since reset, not 4
        for (i in 0 until 3) {
            val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, (4 + i) * WINDOW_INTERVAL_MS)
            assertNull("Walking window after reset ($i of 3) should not trigger", result)
        }
        assertEquals(ActivityState.STILL, gc.getCurrentState())

        // 4th window after reset triggers transition
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 7 * WINDOW_INTERVAL_MS)
        assertNotNull("4th consecutive walking window after reset triggers transition", result)
        assertEquals(ActivityState.WALKING, result!!.toState)
    }

    // ─── 3-minute walking entry threshold ────────────────────────────────────

    @Test
    fun `walking entry - 35 walking windows stays STILL`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 36,
        )
        for (i in 0 until 35) {
            val result = gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                i * WINDOW_INTERVAL_MS,
            )
            assertNull("Walking window $i of 35 should not trigger transition (need 36)", result)
            assertEquals(ActivityState.STILL, gc.getCurrentState())
        }
    }

    @Test
    fun `walking entry - 36 walking windows transitions to WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 36,
        )
        for (i in 0 until 35) {
            gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                i * WINDOW_INTERVAL_MS,
            )
        }
        val result = gc.evaluate(
            featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
            35 * WINDOW_INTERVAL_MS,
        )
        assertNotNull("36th walking window should trigger STILL → WALKING", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
    }

    @Test
    fun `walking entry - effective timestamp is back-dated to first walking window`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
        )
        // 4 walking windows starting at 10_000
        for (i in 0 until 3) {
            gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                10_000L + i * WINDOW_INTERVAL_MS,
            )
        }
        val result = gc.evaluate(
            featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
            10_000L + 3 * WINDOW_INTERVAL_MS,
        )
        assertNotNull(result)
        assertEquals(ActivityState.WALKING, result!!.toState)
        assertEquals(10_000L, result.effectiveTimestamp)
    }

    // ─── Cadence CV check ─────────────────────────────────────────────────────

    @Test
    fun `walking entry - high CV step frequency does not trigger WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
            cadenceCvThreshold = 0.35,
        )
        // Alternate between 0.5 Hz and 2.0 Hz — high variability
        // mean = (0.5 + 2.0 + 0.5 + 2.0) / 4 = 1.25
        // variance = ((0.5^2 + 2.0^2 + 0.5^2 + 2.0^2)/4) - 1.25^2
        //          = (0.25 + 4.0 + 0.25 + 4.0)/4 - 1.5625 = 2.125 - 1.5625 = 0.5625
        // CV = sqrt(0.5625) / 1.25 = 0.75 / 1.25 = 0.6 > 0.35
        val frequencies = listOf(0.5, 2.0, 0.5, 2.0)
        for (i in frequencies.indices) {
            val result = gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), frequencies[i],
                i * WINDOW_INTERVAL_MS,
            )
            assertNull("High CV should prevent WALKING transition at window $i", result)
        }
        assertEquals(ActivityState.STILL, gc.getCurrentState())
    }

    @Test
    fun `walking entry - low CV step frequency triggers WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
            cadenceCvThreshold = 0.35,
        )
        // Steady cadence: 1.8, 1.9, 1.8, 1.9 Hz
        // mean = 1.85, variance = very small, CV << 0.35
        val frequencies = listOf(1.8, 1.9, 1.8, 1.9)
        var lastResult: TransitionResult? = null
        for (i in frequencies.indices) {
            lastResult = gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), frequencies[i],
                i * WINDOW_INTERVAL_MS,
            )
        }
        assertNotNull("Low CV should allow WALKING transition", lastResult)
        assertEquals(ActivityState.WALKING, lastResult!!.toState)
    }

    @Test
    fun `walking entry - identical frequencies have zero CV and pass`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 4,
            cadenceCvThreshold = 0.35,
        )
        // All windows at exactly 2.0 Hz → CV = 0.0
        for (i in 0 until 3) {
            gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 2.0, i * WINDOW_INTERVAL_MS)
        }
        val result = gc.evaluate(
            featuresWithVariance(HIGH_VARIANCE), 2.0,
            3 * WINDOW_INTERVAL_MS,
        )
        assertNotNull("Identical frequencies (CV=0) should trigger WALKING", result)
        assertEquals(ActivityState.WALKING, result!!.toState)
    }

    // ─── Effective timestamp ──────────────────────────────────────────────────

    @Test
    fun `effective timestamp - grace period STILL transition uses first still window time`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 10_000L, // short grace for test
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)

        // First still at 5_000
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 5_000L)
        // Grace period expires at 5_000 + 10_000 = 15_000
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 15_000L)
        assertNotNull(result)
        assertEquals(ActivityState.STILL, result!!.toState)
        assertEquals(5_000L, result.effectiveTimestamp)
    }

    @Test
    fun `effective timestamp - non-grace transitions use currentTimeMs`() {
        // STILL → WALKING: effectiveTimestamp = currentTimeMs
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 42_000L)
        assertNotNull(result)
        assertEquals(42_000L, result!!.effectiveTimestamp)

        // WALKING → STILL (grace = 0): effectiveTimestamp = stillWindowStartTimeMs = currentTimeMs
        val result2 = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 50_000L)
        assertNotNull(result2)
        assertEquals(50_000L, result2!!.effectiveTimestamp)
    }

    // ─── Cycling exit walking threshold ────────────────────────────────────────

    @Test
    fun `cycling exit - 1 to 3 walking windows stays CYCLING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            consecutiveWalkingWindowsForCyclingExit = 4,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 1 to 3 walking windows — not enough to exit
        for (i in 0 until 3) {
            val result = gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                10_000L + i * WINDOW_INTERVAL_MS,
            )
            assertNull("Walking window $i of 3 should not exit CYCLING (need 4)", result)
            assertEquals(ActivityState.CYCLING, gc.getCurrentState())
        }
    }

    @Test
    fun `cycling exit - 4 walking windows transitions to WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            consecutiveWalkingWindowsForCyclingExit = 4,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 3 walking windows — not enough
        for (i in 0 until 3) {
            gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                10_000L + i * WINDOW_INTERVAL_MS,
            )
        }
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 4th walking window triggers exit
        val result = gc.evaluate(
            featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
            10_000L + 3 * WINDOW_INTERVAL_MS,
        )
        assertNotNull("4th walking window should exit CYCLING → WALKING", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
    }

    @Test
    fun `cycling exit - walking streak interrupted resets counter`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            consecutiveWalkingWindowsForCyclingExit = 4,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 3 walking windows
        for (i in 0 until 3) {
            gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                10_000L + i * WINDOW_INTERVAL_MS,
            )
        }
        // Cycling window interrupts
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 25_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 3 more walking windows (only 3 since reset, not 4)
        for (i in 0 until 3) {
            val result = gc.evaluate(
                featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
                30_000L + i * WINDOW_INTERVAL_MS,
            )
            assertNull("Walking window $i after reset should not exit CYCLING", result)
        }
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 4th walking window after reset triggers exit
        val result = gc.evaluate(
            featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ,
            30_000L + 3 * WINDOW_INTERVAL_MS,
        )
        assertNotNull("4th consecutive walking window after reset triggers exit", result)
        assertEquals(ActivityState.WALKING, result!!.toState)
    }

    @Test
    fun `cycling exit - effective timestamp is back-dated to first walking window`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            consecutiveWalkingWindowsForCyclingExit = 3,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 3 walking windows starting at 10_000
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 10_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 15_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 20_000L)

        assertNotNull(result)
        assertEquals(ActivityState.WALKING, result!!.toState)
        assertEquals(10_000L, result.effectiveTimestamp)
    }

    @Test
    fun `DEFAULT_CONSECUTIVE_WALKING_WINDOWS_FOR_CYCLING_EXIT is 4`() {
        assertEquals(4, CyclingClassifier.DEFAULT_CONSECUTIVE_WALKING_WINDOWS_FOR_CYCLING_EXIT)
    }

    // ─── Cadence breakdown constants ──────────────────────────────────────────

    @Test
    fun `DEFAULT_CADENCE_BREAKDOWN_WINDOW_SIZE is 36`() {
        assertEquals(36, CyclingClassifier.DEFAULT_CADENCE_BREAKDOWN_WINDOW_SIZE)
    }

    @Test
    fun `DEFAULT_CADENCE_BREAKDOWN_DENSITY_THRESHOLD is 0_4`() {
        assertEquals(0.4, CyclingClassifier.DEFAULT_CADENCE_BREAKDOWN_DENSITY_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_CADENCE_BREAKDOWN_CV_THRESHOLD is 0_7`() {
        assertEquals(0.7, CyclingClassifier.DEFAULT_CADENCE_BREAKDOWN_CV_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_CADENCE_BREAKDOWN_MEAN_FLOOR is 1_0`() {
        assertEquals(1.0, CyclingClassifier.DEFAULT_CADENCE_BREAKDOWN_MEAN_FLOOR, DELTA)
    }

    // ─── Cadence breakdown: density exit ──────────────────────────────────────

    @Test
    fun `cadence breakdown - chores pattern exits WALKING via low density`() {
        // Small buffer (6 windows) for test tractability. Density threshold 0.4 → need < 2.4 walking windows
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE, // disable grace period
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING (this eval runs in STILL branch — no buffer push)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Chores pattern: 2 walking + 4 still in WALKING state = 6 buffer entries
        // density = 2/6 = 0.33 < 0.4
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 5_000L)   // walking
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 10_000L)  // walking
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 15_000L)    // still
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 20_000L)    // still
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)    // still
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 30_000L) // still → buffer full

        assertNotNull("Low density chores pattern should exit WALKING", result)
        assertEquals(ActivityState.WALKING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(ActivityState.STILL, gc.getCurrentState())
    }

    @Test
    fun `cadence breakdown - effective timestamp is oldest buffer entry`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 4,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING at t=100 (STILL branch — no buffer push)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 100L)

        // 4 evals in WALKING to fill buffer: 1 walking + 3 still
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 200L) // buffer[0], ts=200
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 300L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 400L)
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 500L)

        // density = 1/4 = 0.25 < 0.4 → exits. Oldest buffer entry is t=200
        assertNotNull(result)
        assertEquals(ActivityState.STILL, result!!.toState)
        assertEquals(200L, result.effectiveTimestamp)
    }

    @Test
    fun `cadence breakdown - crosswalk stays WALKING (high density)`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // 30s crosswalk: 5 walking + 1 still = density 5/6 = 83% > 40%
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 5_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 10_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 15_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 20_000L)
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)

        assertNull("Crosswalk with high density should stay WALKING", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    @Test
    fun `cadence breakdown - slower steady walking stays WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
            cadenceBreakdownCvThreshold = 0.7,
            cadenceBreakdownMeanFloor = 1.0,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 1.2, 0L)

        // Steady 1.2 Hz walking: density = 100%, CV ≈ 0, mean = 1.2 > 1.0
        for (i in 1 until 6) {
            gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 1.2, i * WINDOW_INTERVAL_MS)
        }
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    // ─── Cadence breakdown: CV + mean exit (puttering) ────────────────────────

    @Test
    fun `cadence breakdown - puttering exits WALKING via high CV and low mean`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
            cadenceBreakdownCvThreshold = 0.7,
            cadenceBreakdownMeanFloor = 1.0,
        )
        // Enter WALKING (STILL branch — no buffer push)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // 6 evals in WALKING to fill buffer. Mix of slow walking + one spike:
        // Buffer: [0.3, 0.3, 0.3, 2.0, 0.0, 0.0]
        // Walking windows (>= 0.3): 4 → density = 4/6 = 67% > 40% (passes density)
        // Among walking windows: mean = (0.3+0.3+0.3+2.0)/4 = 0.725 < 1.0 ✓
        // sum_sq = (0.09+0.09+0.09+4.0)/4 = 4.27/4 = 1.0675
        // var = 1.0675 - 0.725^2 = 0.5419
        // CV = sqrt(0.5419)/0.725 = 0.736/0.725 = 1.015 > 0.7 ✓
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 0.3, 5_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 0.3, 10_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 0.3, 15_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 2.0, 20_000L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 30_000L)

        assertNotNull("Puttering (high CV, low mean) should exit WALKING", result)
        assertEquals(ActivityState.WALKING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
    }

    @Test
    fun `cadence breakdown - high CV but high mean stays WALKING`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
            cadenceBreakdownCvThreshold = 0.7,
            cadenceBreakdownMeanFloor = 1.0,
        )
        // Enter WALKING at 2.0 Hz
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // Alternating 1.0 and 3.0 Hz — high CV but high mean
        // Buffer: [2.0, 1.0, 3.0, 1.0, 3.0, 1.0]
        // All >= 0.3 → density 100%. mean = (2+1+3+1+3+1)/6 = 11/6 = 1.833 > 1.0
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 1.0, 5_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 3.0, 10_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 1.0, 15_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 3.0, 20_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), 1.0, 25_000L)

        assertNull("High CV but mean > 1.0 should stay WALKING", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    // ─── Cadence breakdown: buffer management ─────────────────────────────────

    @Test
    fun `cadence breakdown - buffer not checked before full`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 6,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // 4 still windows — only 5 entries in buffer (not full at 6)
        // If buffer were checked, density = 1/5 = 20% < 40% would trigger exit
        for (i in 1 until 5) {
            val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, i * WINDOW_INTERVAL_MS)
            assertNull("Buffer not full — should not check cadence breakdown at window $i", result)
        }
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    @Test
    fun `cadence breakdown - buffer resets on WALKING entry from STILL`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 4,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING, fill buffer with 4 windows (mostly still → would trigger)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 5_000L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 10_000L)
        // 3 entries so far, buffer not yet full

        // Force back to STILL via cycling then still
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 15_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 20_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)
        assertEquals(ActivityState.STILL, gc.getCurrentState())

        // Re-enter WALKING (buffer should be reset)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 30_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Feed 3 all-walking windows — buffer has 4 entries (full), all walking
        // density = 4/4 = 100% → should NOT trigger breakdown
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 35_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 40_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 45_000L)
        assertNull("Buffer reset on WALKING entry — all walking windows should stay WALKING", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
    }

    @Test
    fun `cadence breakdown - grace period still fires before cadence check`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 10_000L,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 4,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // 3 still windows (buffer at 4 entries = full), still duration = 15s > 10s grace
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 5_000L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 10_000L)
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 15_000L)

        // Grace period fires (stillStart=5000, current=15000, duration=10000 >= 10000)
        // Effective timestamp should be stillWindowStartTimeMs (5000), not buffer oldest
        assertNotNull("Grace period should fire", result)
        assertEquals(ActivityState.STILL, result!!.toState)
        assertEquals(5_000L, result.effectiveTimestamp)
    }

    @Test
    fun `cadence breakdown - rolling window continues sliding after full`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE,
            cyclingGracePeriodMs = 0L,
            consecutiveWalkingWindowsRequired = 1,
            cadenceBreakdownWindowSize = 4,
            cadenceBreakdownDensityThreshold = 0.4,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)

        // Fill buffer with 3 more walking windows (4 total, all walking, density 100%)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 5_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 10_000L)
        val r1 = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 15_000L)
        assertNull("All walking → stays WALKING", r1)

        // Now push 3 still windows — they slide in, pushing out walking windows
        // After 1 still: [walking, walking, walking, still] → density 3/4 = 75% > 40%
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 20_000L)
        // After 2 still: [walking, walking, still, still] → density 2/4 = 50% > 40%
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 25_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
        // After 3 still: [walking, still, still, still] → density 1/4 = 25% < 40% → exits!
        val r2 = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 30_000L)
        assertNotNull("Density dropped below threshold after sliding", r2)
        assertEquals(ActivityState.STILL, r2!!.toState)
    }

    // ─── Thread safety ────────────────────────────────────────────────────────

    @Test
    fun `concurrent evaluate calls do not throw`() {
        val features = featuresWithVariance(HIGH_VARIANCE)
        val exceptions = mutableListOf<Throwable>()

        val writer1 = thread(start = true) {
            try {
                repeat(1_000) { i ->
                    classifier.evaluate(features, CYCLING_STEP_FREQ, T0 + i * WINDOW_INTERVAL_MS)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val writer2 = thread(start = true) {
            try {
                repeat(1_000) { i ->
                    classifier.evaluate(featuresWithVariance(LOW_VARIANCE), WALKING_STEP_FREQ, T0 + i * WINDOW_INTERVAL_MS)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val resetter = thread(start = true) {
            try {
                repeat(100) {
                    classifier.reset()
                    Thread.sleep(1)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        writer1.join(10_000)
        writer2.join(10_000)
        resetter.join(10_000)

        assert(exceptions.isEmpty()) {
            "Expected no exceptions during concurrent access but got: $exceptions"
        }
    }

    @Test
    fun `concurrent getCurrentState and evaluate do not throw`() {
        val features = featuresWithVariance(HIGH_VARIANCE)
        val exceptions = mutableListOf<Throwable>()

        val writer = thread(start = true) {
            try {
                repeat(2_000) { i -> classifier.evaluate(features, CYCLING_STEP_FREQ, T0 + i * WINDOW_INTERVAL_MS) }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val reader = thread(start = true) {
            try {
                repeat(2_000) { classifier.getCurrentState() }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        writer.join(10_000)
        reader.join(10_000)

        assert(exceptions.isEmpty()) {
            "Expected no exceptions during concurrent access but got: $exceptions"
        }
    }
}
