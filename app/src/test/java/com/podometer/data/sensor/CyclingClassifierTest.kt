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
 *   varianceThreshold     = 2.0  (m/s²)²
 *   stepFreqThreshold     = 0.3  Hz
 *   consecutiveWindows    = 2
 *   stillVarianceThreshold = 0.5 (m/s²)²
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
    }

    @Before
    fun setUp() {
        classifier = CyclingClassifier()
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
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ)
        assertNull("Expected no transition for still window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `still state - low variance stays non-cycling - no transition`() {
        // LOW_VARIANCE is below varianceThreshold but above stillVarianceThreshold
        // With zero steps, should stay in STILL
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), ZERO_STEP_FREQ)
        assertNull("Expected no transition for low-variance window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Walking detection: moderate variance, high step frequency ────────────

    @Test
    fun `walking - high step frequency prevents cycling detection even with high variance`() {
        // High variance but WALKING_STEP_FREQ is above stepFrequencyThreshold → not a cycling window
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
        assertNull("Walking step freq should block cycling detection", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `walking - consecutive windows with walking step freq do not trigger cycling`() {
        repeat(5) {
            val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
            assertNull("No cycling transition when walking step freq present", result)
        }
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Consecutive windows requirement ──────────────────────────────────────

    @Test
    fun `single cycling window does not trigger transition`() {
        // Only 1 cycling window — require 2 (DEFAULT_CONSECUTIVE_WINDOWS)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("Single cycling window must not trigger transition", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `two consecutive cycling windows trigger transition to CYCLING`() {
        val result1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("First cycling window should not trigger", result1)

        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Second cycling window should trigger transition", result2)
        assertEquals(ActivityState.STILL, result2!!.fromState)
        assertEquals(ActivityState.CYCLING, result2.toState)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    @Test
    fun `non-cycling window between cycling windows resets consecutive count`() {
        // First cycling window
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)

        // Non-cycling interruption (walking step freq)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)

        // Second cycling window — should NOT trigger (count was reset)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("Consecutive count reset by non-cycling window", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())

        // Third cycling window — first of a new consecutive pair
        val result2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Second consecutive cycling window triggers cycling", result2)
        assertEquals(ActivityState.CYCLING, result2!!.toState)
    }

    // ─── Cycling to WALKING transition ────────────────────────────────────────

    @Test
    fun `cycling to walking - steps resume after cycling`() {
        // Transition to CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        // Now steps resume (WALKING_STEP_FREQ above threshold) → transition back to WALKING
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
        assertNotNull("Resuming steps while in CYCLING should trigger WALKING transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())
    }

    @Test
    fun `cycling to walking - variance drops to walking level`() {
        // Reach CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)

        // LOW_VARIANCE is below varianceThreshold but step freq is also low — still not cycling window
        // so transition from CYCLING occurs
        val result = classifier.evaluate(featuresWithVariance(LOW_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Non-cycling window while in CYCLING triggers WALKING transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.WALKING, result.toState)
    }

    // ─── STILL detection ─────────────────────────────────────────────────────

    @Test
    fun `still detection - very low variance and near-zero steps from WALKING`() {
        // Manually move to WALKING first by triggering cycling then walking
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
        assertEquals(ActivityState.WALKING, classifier.getCurrentState())

        // Now still-level variance with zero steps → should transition to STILL
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ)
        assertNotNull("Still variance + no steps from WALKING should transition to STILL", result)
        assertEquals(ActivityState.WALKING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `still detection from CYCLING - very low variance transitions to STILL`() {
        // Reach CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        // Still variance with zero steps
        val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ)
        assertNotNull("Still variance + no steps from CYCLING should trigger transition", result)
        assertEquals(ActivityState.CYCLING, result!!.fromState)
        assertEquals(ActivityState.STILL, result.toState)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── No duplicate transitions ─────────────────────────────────────────────

    @Test
    fun `repeated cycling windows after entering CYCLING do not emit further transitions`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        val transition = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull(transition)

        // Additional cycling windows while already CYCLING → no new transition
        val extra1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("No duplicate transition when already CYCLING", extra1)
        val extra2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("No duplicate transition when already CYCLING", extra2)

        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())
    }

    @Test
    fun `staying still emits no transitions`() {
        repeat(10) {
            val result = classifier.evaluate(featuresWithVariance(STILL_VARIANCE), ZERO_STEP_FREQ)
            assertNull("No transitions when already STILL", result)
        }
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    // ─── Reset ───────────────────────────────────────────────────────────────

    @Test
    fun `reset clears state back to STILL`() {
        // Advance to CYCLING
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertEquals(ActivityState.CYCLING, classifier.getCurrentState())

        classifier.reset()
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset clears consecutive window counter`() {
        // One cycling window before reset
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.reset()

        // After reset, single cycling window should not trigger (counter was cleared)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("Counter should be zero after reset", result)
        assertEquals(ActivityState.STILL, classifier.getCurrentState())
    }

    @Test
    fun `reset allows fresh cycling detection`() {
        // Full cycle then reset
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        classifier.reset()

        // Need 2 new consecutive cycling windows to re-detect
        val r1 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull(r1)
        val r2 = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Should re-detect cycling after reset + 2 windows", r2)
        assertEquals(ActivityState.CYCLING, r2!!.toState)
    }

    // ─── Configurable thresholds ──────────────────────────────────────────────

    @Test
    fun `custom variance threshold - lower threshold triggers cycling at lower variance`() {
        val lowThresholdClassifier = CyclingClassifier(
            varianceThreshold = 0.5,          // lower than the 1.0 we'll use
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 2,
        )
        // Use variance of 1.0 which is above the 0.5 threshold but below default 2.0
        val customVariance = 1.0

        lowThresholdClassifier.evaluate(featuresWithVariance(customVariance), CYCLING_STEP_FREQ)
        val result = lowThresholdClassifier.evaluate(featuresWithVariance(customVariance), CYCLING_STEP_FREQ)
        assertNotNull("Custom lower threshold should detect cycling at variance $customVariance", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom step frequency threshold - higher threshold triggers cycling with more steps`() {
        val highFreqThresholdClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 3.0,    // higher threshold: 3.0 Hz
            consecutiveWindowsRequired = 2,
        )
        // At 2.0 Hz steps (WALKING_STEP_FREQ) — above default 0.3 but below custom 3.0 threshold
        // So with custom classifier, 2.0 Hz steps still count as "cycling-like"
        highFreqThresholdClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
        val result = highFreqThresholdClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), WALKING_STEP_FREQ)
        assertNotNull("At 2.0 Hz steps, custom 3.0 Hz threshold should still detect cycling", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom consecutive windows required - 1 window sufficient`() {
        val singleWindowClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 1,
        )
        val result = singleWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Single window required: should detect on first window", result)
        assertEquals(ActivityState.CYCLING, result!!.toState)
    }

    @Test
    fun `custom consecutive windows required - 3 windows required`() {
        val threeWindowClassifier = CyclingClassifier(
            varianceThreshold = 2.0,
            stepFrequencyThreshold = 0.3,
            consecutiveWindowsRequired = 3,
        )
        threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        val r2 = threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNull("Two windows not enough when 3 required", r2)
        val r3 = threeWindowClassifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        assertNotNull("Third window triggers cycling with 3 required", r3)
        assertEquals(ActivityState.CYCLING, r3!!.toState)
    }

    // ─── TransitionResult data class ─────────────────────────────────────────

    @Test
    fun `TransitionResult has correct fromState and toState`() {
        classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)
        val result = classifier.evaluate(featuresWithVariance(HIGH_VARIANCE), CYCLING_STEP_FREQ)!!
        assertEquals(ActivityState.STILL, result.fromState)
        assertEquals(ActivityState.CYCLING, result.toState)
    }

    @Test
    fun `TransitionResult is data class with equality semantics`() {
        val r1 = TransitionResult(ActivityState.STILL, ActivityState.CYCLING)
        val r2 = TransitionResult(ActivityState.STILL, ActivityState.CYCLING)
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

    // ─── Thread safety ────────────────────────────────────────────────────────

    @Test
    fun `concurrent evaluate calls do not throw`() {
        val features = featuresWithVariance(HIGH_VARIANCE)
        val exceptions = mutableListOf<Throwable>()

        val writer1 = thread(start = true) {
            try {
                repeat(1_000) {
                    classifier.evaluate(features, CYCLING_STEP_FREQ)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val writer2 = thread(start = true) {
            try {
                repeat(1_000) {
                    classifier.evaluate(featuresWithVariance(LOW_VARIANCE), WALKING_STEP_FREQ)
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
                repeat(2_000) { classifier.evaluate(features, CYCLING_STEP_FREQ) }
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
