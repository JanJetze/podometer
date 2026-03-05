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
 * Unit tests for [CyclingClassifier] (v2 — density-based walking detection).
 *
 * Uses synthetic [WindowFeatures] and step-frequency values to verify
 * state-machine transitions without any Android framework dependency.
 *
 * Threshold defaults used in tests (unless overridden):
 *   varianceThreshold      = 2.0  (m/s²)²
 *   stepFreqThreshold      = 0.3  Hz
 *   consecutiveWindows     = 6
 *   stillVarianceThreshold = 0.5  (m/s²)²
 *   minCyclingDurationMs   = 60_000L
 *   walkingHzThreshold     = 1.5  Hz
 *   walkingEntryCount      = 5
 *   walkingExitCount       = 2
 *   densityWindowMs        = 300_000L (5 min)
 *   cyclingWalkExitHz      = 2.0  Hz
 *   cyclingWalkExitCount   = 8
 */
class CyclingClassifierTest {

    private lateinit var classifier: CyclingClassifier

    companion object {
        // Variance values relative to thresholds
        private const val HIGH_VARIANCE = 3.0     // above DEFAULT_VARIANCE_THRESHOLD (2.0)
        private const val LOW_VARIANCE = 1.0      // below DEFAULT_VARIANCE_THRESHOLD, above STILL
        private const val STILL_VARIANCE = 0.2    // below DEFAULT_STILL_VARIANCE_THRESHOLD (0.5)

        // Step frequency values relative to thresholds
        private const val CYCLING_STEP_FREQ = 0.1  // below 0.3 threshold — very few steps
        private const val WALKING_STEP_FREQ = 2.0  // above walkingHzThreshold (1.5) AND cyclingWalkExitHz (2.0)
        private const val MODERATE_WALK_FREQ = 1.6 // above walkingHzThreshold but below cyclingWalkExitHz
        private const val ZERO_STEP_FREQ = 0.0     // no steps at all

        private const val DELTA = 1e-9

        // Timestamps
        private const val T0 = 0L
        private const val T_61S = 61_000L
        private const val T_30S = 30_000L
        private const val T_65S = 65_000L

        /** Interval between simulated 30-second windows. */
        private const val WINDOW_INTERVAL_MS = 30_000L
    }

    @Before
    fun setUp() {
        // Minimal thresholds for basic tests: cycling entry with 2 windows, walking with 1,
        // no grace periods. Override per test for specific behavior.
        classifier = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 1,
            walkingExitCount = 0,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
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
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), ZERO_STEP_FREQ, T0)
        assertNull("Expected no transition for low-variance window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Walking detection: density-based ───────────────────────────────────

    @Test
    fun `walking - high step frequency from STILL triggers WALKING`() {
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertNotNull("Walking step freq from STILL should trigger WALKING transition", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `walking - no duplicate transition when already WALKING`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0 + WINDOW_INTERVAL_MS)
        assertNull("No duplicate transition when already WALKING", result2)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `walking entry - density count respected`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 3,
            walkingExitCount = 0,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        // First 2 walking windows — not enough
        for (i in 0 until 2) {
            val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
            assertNull("Walking window $i should not trigger (need 3)", result)
            assertEquals(ActivityState.STILL, gc.getCurrentState())
        }
        // 3rd triggers
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 2 * WINDOW_INTERVAL_MS)
        assertNotNull("3rd walking window should trigger STILL → WALKING", result)
        assertEquals(ActivityState.WALKING, result!!.toState)
    }

    @Test
    fun `walking entry - effective timestamp is first walking window in density window`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 3,
            walkingExitCount = 0,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        // Still windows then walking windows
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 0L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 30_000L) // first walking
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 60_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 90_000L)
        assertNotNull(result)
        assertEquals(ActivityState.WALKING, result!!.toState)
        assertEquals(30_000L, result.effectiveTimestamp) // back-dated to first walking
    }

    @Test
    fun `walking entry - windows outside density window are not counted`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 3,
            walkingExitCount = 0,
            densityWindowMs = 120_000L, // 2 min
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        // 2 walking windows early
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 30_000L)
        // Gap that pushes the first 2 outside the 2-min window
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 150_000L) // 2:30
        // Only 1 walking window inside the density window now
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 180_000L) // 3:00
        assertNull("Only 1 walking window in density window (need 3)", result)
        assertEquals(ActivityState.STILL, gc.getCurrentState())
    }

    // ─── Walking exit: density drop ─────────────────────────────────────────

    @Test
    fun `walking exit - density drops below exit threshold`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = Long.MAX_VALUE, // disable grace
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 1,
            walkingExitCount = 2,
            densityWindowMs = 120_000L, // 2 min
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        // Enter WALKING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Stay walking with enough density
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 30_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 60_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Walking windows at 0, 30_000, 60_000. Density window = 2 min.
        // At t=180_000: cutoff = 60_000. Window at 60_000 is exactly at cutoff (included).
        // Walking count = 1 < 2 → exits immediately.
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 180_000L) // still at 3:00
        assertNotNull("Should exit WALKING when density drops", result)
        assertEquals(ActivityState.STILL, result!!.toState)
    }

    // ─── Consecutive windows requirement (cycling) ──────────────────────────

    @Test
    fun `single cycling window does not trigger transition`() {
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        assertNull("Single cycling window must not trigger transition", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `two consecutive cycling windows trigger transition to CYCLING`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNotNull("Second cycling window should trigger transition", result)
        assertEquals(ActivityState.STILL, result!!.fromState)
        assertEquals(ActivityState.CYCLING, result.toState)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    @Test
    fun `non-cycling window between cycling windows resets consecutive count`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        // Interruption — triggers STILL → WALKING (walkingEntryCount=1)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_30S)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())

        // Only 1 cycling window since reset
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNull("Consecutive count reset by non-cycling window", result)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `cycling entry - 6 consecutive windows required by default`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 100, // high to prevent walking
        )
        // 5 windows — not enough (default is 6)
        for (i in 0 until 5) {
            val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, i * WINDOW_INTERVAL_MS)
            assertNull("Window $i should not trigger (need 6)", result)
        }
        assertEquals(ActivityState.STILL, gc.getCurrentState())

        // 6th triggers
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5 * WINDOW_INTERVAL_MS)
        assertNotNull("6th consecutive cycling window should trigger", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    // ─── Cycling to WALKING transition (sticky exit) ────────────────────────

    @Test
    fun `cycling to walking - requires high hz for exit`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 1,
            walkingExitCount = 0,
            cyclingWalkExitHz = 2.0,
            cyclingWalkExitCount = 2,
        )
        // Enter CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // Moderate walking hz (1.6) — below cyclingWalkExitHz (2.0) — stays CYCLING
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), MODERATE_WALK_FREQ, T_65S)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), MODERATE_WALK_FREQ, T_65S + WINDOW_INTERVAL_MS)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // High walking hz (2.0) — meets threshold
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_65S + 2 * WINDOW_INTERVAL_MS)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T_65S + 3 * WINDOW_INTERVAL_MS)
        assertNotNull("High hz should exit CYCLING → WALKING", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
    }

    @Test
    fun `cycling to walking - interrupted high-hz streak resets counter`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 1,
            walkingExitCount = 0,
            cyclingWalkExitHz = 2.0,
            cyclingWalkExitCount = 3,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 2 high-hz windows
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 70_000L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 100_000L)
        // Cycling window interrupts
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 130_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        // 2 more — only 2 since reset, need 3
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 160_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 190_000L)
        assertNull("Only 2 consecutive high-hz since reset (need 3)", result)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())
    }

    @Test
    fun `cycling to walking - effective timestamp is first high-hz window`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 1,
            walkingExitCount = 0,
            cyclingWalkExitHz = 2.0,
            cyclingWalkExitCount = 2,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)

        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 100_000L)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 130_000L)
        assertNotNull(result)
        assertEquals(100_000L, result!!.effectiveTimestamp)
    }

    @Test
    fun `cycling to walking - ambiguous window stays CYCLING`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)

        // LOW_VARIANCE + CYCLING_STEP_FREQ: not cycling, not still, not walking
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNull("Ambiguous window should stay CYCLING", result)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    // ─── STILL detection ──────────────────────────────────────────────────────

    @Test
    fun `still detection - from WALKING via density drop`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 1,
            walkingExitCount = 1,
            densityWindowMs = 60_000L,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Still windows push walking outside density window
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 90_000L) // 1.5 min later
        assertNotNull("Should transition to STILL", result)
        assertEquals(ActivityState.STILL, result!!.toState)
    }

    @Test
    fun `still detection from CYCLING - very low variance transitions to STILL`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, T_65S)
        assertNotNull("Still from CYCLING should trigger transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
    }

    // ─── No duplicate transitions ─────────────────────────────────────────────

    @Test
    fun `repeated cycling windows after entering CYCLING do not emit further transitions`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)

        val extra1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_65S)
        assertNull("No duplicate transition when already CYCLING", extra1)
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

    // ─── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears state back to STILL`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        classifier.reset()
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset clears consecutive window counter`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        classifier.reset()

        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T_61S)
        assertNull("Counter should be zero after reset", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset clears density buffer`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 3,
            walkingExitCount = 0,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )
        // 2 walking windows
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 0L)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 30_000L)
        gc.reset()

        // After reset, only 1 walking window (the 2 before reset are gone)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 60_000L)
        assertNull("Density buffer should be cleared after reset (need 3, only 1)", result)
        assertEquals(ActivityState.STILL, gc.getCurrentState())
    }

    // ─── 60-second duration gate ──────────────────────────────────────────────

    @Test
    fun `duration gate - cycling windows spanning less than 60 seconds do NOT trigger`() {
        val gc = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 100,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 59_000L)
        assertNull("59-second span must not satisfy 60-second gate", result)
        assertEquals(ActivityState.STILL, gc.getCurrentState())
    }

    @Test
    fun `duration gate - cycling windows spanning 60 seconds DO trigger`() {
        val gc = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 100,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val result = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 60_000L)
        assertNotNull("60-second span must satisfy duration gate", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `duration gate - many windows accumulate to satisfy 60-second requirement`() {
        val gc = CyclingClassifier(
            consecutiveWindowsRequired = 2,
            minCyclingDurationMs = 60_000L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 100,
        )
        // 2 windows at 30s apart = 30s span < 60s
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        val r1 = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 30_000L)
        assertNull("30s span must not trigger", r1)

        // 3rd at 60s = 60s span
        val r2 = gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 60_000L)
        assertNotNull("60s span must trigger", r2)
        assertEquals(ActivityState.CYCLING, r2!!.toState)
    }

    // ─── Walking grace period ─────────────────────────────────────────────────

    @Test
    fun `grace period - walking plus still window stays WALKING within 2 min`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 120_000L,
            cyclingGracePeriodMs = 0L,
            walkingEntryCount = 1,
            walkingExitCount = 0, // disable density exit
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

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
            walkingEntryCount = 1,
            walkingExitCount = 0, // disable density exit
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)

        val stillStart = 10_000L
        var t = stillStart
        while (t < stillStart + 120_000L) {
            val r = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, t)
            assertNull("Should stay WALKING during grace period at t=$t", r)
            t += WINDOW_INTERVAL_MS
        }

        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, stillStart + 120_000L)
        assertNotNull("Should transition to STILL after 2 min", result)
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
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 100,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

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
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 100,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, T0)
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, 5_000L)
        assertEquals(ActivityState.CYCLING, gc.getCurrentState())

        val stillStart = 10_000L
        var t = stillStart
        while (t < stillStart + 180_000L) {
            val r = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, t)
            assertNull("Should stay CYCLING during grace period", r)
            t += WINDOW_INTERVAL_MS
        }

        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, stillStart + 180_000L)
        assertNotNull("Should transition to STILL after 3 min", result)
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
            walkingEntryCount = 1,
            walkingExitCount = 0,
        )
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, T0)

        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 10_000L)
        gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 40_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Walking window breaks the still streak
        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, 50_000L)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())

        // Even after original stillStart + 120s, no transition (counter was reset)
        val result = gc.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ, 130_000L)
        assertNull("Grace period was reset; not enough still time from new start", result)
        assertEquals(ActivityState.WALKING, gc.getCurrentState())
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
    fun `DEFAULT_CONSECUTIVE_WINDOWS is 6`() {
        assertEquals(6, CyclingClassifier.DEFAULT_CONSECUTIVE_WINDOWS)
    }

    @Test
    fun `DEFAULT_STILL_VARIANCE_THRESHOLD is 0_5`() {
        assertEquals(0.5, CyclingClassifier.DEFAULT_STILL_VARIANCE_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_MIN_CYCLING_DURATION_MS is 60_000`() {
        assertEquals(60_000L, CyclingClassifier.DEFAULT_MIN_CYCLING_DURATION_MS)
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
    fun `DEFAULT_WALKING_HZ_THRESHOLD is 1_5`() {
        assertEquals(1.5, CyclingClassifier.DEFAULT_WALKING_HZ_THRESHOLD, DELTA)
    }

    @Test
    fun `DEFAULT_WALKING_ENTRY_COUNT is 5`() {
        assertEquals(5, CyclingClassifier.DEFAULT_WALKING_ENTRY_COUNT)
    }

    @Test
    fun `DEFAULT_WALKING_EXIT_COUNT is 2`() {
        assertEquals(2, CyclingClassifier.DEFAULT_WALKING_EXIT_COUNT)
    }

    @Test
    fun `DEFAULT_DENSITY_WINDOW_MS is 300_000`() {
        assertEquals(300_000L, CyclingClassifier.DEFAULT_DENSITY_WINDOW_MS)
    }

    @Test
    fun `DEFAULT_CYCLING_WALK_EXIT_HZ is 2_0`() {
        assertEquals(2.0, CyclingClassifier.DEFAULT_CYCLING_WALK_EXIT_HZ, DELTA)
    }

    @Test
    fun `DEFAULT_CYCLING_WALK_EXIT_COUNT is 8`() {
        assertEquals(8, CyclingClassifier.DEFAULT_CYCLING_WALK_EXIT_COUNT)
    }

    // ─── Thread safety ────────────────────────────────────────────────────────

    @Test
    fun `concurrent evaluate calls do not corrupt state`() {
        val gc = CyclingClassifier(
            minCyclingDurationMs = 0L,
            walkingGracePeriodMs = 0L,
            cyclingGracePeriodMs = 0L,
            consecutiveWindowsRequired = 2,
            walkingEntryCount = 1,
            walkingExitCount = 0,
            cyclingWalkExitCount = 1,
            cyclingWalkExitHz = WALKING_STEP_FREQ,
        )

        val threads = (0 until 8).map { threadIdx ->
            thread(start = false) {
                for (i in 0 until 100) {
                    val t = (threadIdx * 100 + i) * WINDOW_INTERVAL_MS
                    if (i % 2 == 0) {
                        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ, t)
                    } else {
                        gc.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ, t)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val state = gc.getCurrentState()
        // Verify state is one of the valid enum values (no corruption)
        assert(state == ActivityState.STILL || state == ActivityState.WALKING || state == ActivityState.CYCLING)
    }

    // ─── TransitionResult data class ──────────────────────────────────────────

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
}
