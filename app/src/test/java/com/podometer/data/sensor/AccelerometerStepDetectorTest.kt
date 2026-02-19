// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccelerometerStepDetector].
 *
 * Tests call [AccelerometerStepDetector.processSample] directly with magnitude
 * and timestamp values, avoiding any dependency on the Android SensorEvent class.
 *
 * EMA filter maths (ALPHA=0.1, starting from GRAVITY=9.81):
 *   A single peak sample at PEAK_MAGNITUDE crosses THRESHOLD_RISE:
 *     EMA = 0.9 * 9.81 + 0.1 * 20.0 = 8.829 + 2.0 = 10.829 >= 10.5 (THRESHOLD_RISE)
 *   Falling back below THRESHOLD_FALL=10.0 from ~10.829 requires multiple low samples:
 *     6+ samples at FALL_MAGNITUDE=9.0 bring EMA below 10.0
 */
class AccelerometerStepDetectorTest {

    private lateinit var detector: AccelerometerStepDetector

    companion object {
        /**
         * Peak acceleration magnitude used in tests.
         * At 20.0 m/s² one EMA step from resting GRAVITY (9.81) gives:
         *   0.9 * 9.81 + 0.1 * 20.0 = 10.829, which exceeds THRESHOLD_RISE (10.5).
         */
        private const val PEAK_MAGNITUDE = 20.0

        /**
         * Low-magnitude value used to reset EMA below THRESHOLD_FALL.
         * After a peak the EMA is ~10.83; 10 samples at 9.0 bring it below 10.0.
         */
        private const val FALL_MAGNITUDE = 9.0

        /** Number of low samples needed to push EMA below THRESHOLD_FALL after a peak. */
        private const val FALL_SAMPLES = 10

        /** 20 ms in nanoseconds — simulates 50 Hz sensor rate. */
        private const val SAMPLE_INTERVAL_NS = 20_000_000L
    }

    @Before
    fun setUp() {
        detector = AccelerometerStepDetector()
    }

    /**
     * Helper: feeds [count] samples at [magnitude] starting from [startTs],
     * each [SAMPLE_INTERVAL_NS] apart.
     * Returns the timestamp of the sample immediately following the last sample fed.
     */
    private fun feedSamples(magnitude: Double, count: Int, startTs: Long): Long {
        for (i in 0 until count) {
            detector.processSample(magnitude, startTs + i * SAMPLE_INTERVAL_NS)
        }
        return startTs + count * SAMPLE_INTERVAL_NS
    }

    /**
     * Helper: warm-up the EMA at GRAVITY for 100 samples so it is essentially
     * at resting level before the test delivers a peak.
     * Returns the timestamp immediately after the last warmup sample.
     */
    private fun warmUp(startTs: Long = 0L): Long =
        feedSamples(AccelerometerStepDetector.GRAVITY, 100, startTs)

    // ─── Happy-path: step detection ───────────────────────────────────────────

    @Test
    fun `step detected when magnitude exceeds THRESHOLD_RISE after filter settles`() {
        // Warm up EMA at resting gravity for 100 samples.
        val nextTs = warmUp()

        // One peak sample at PEAK_MAGNITUDE drives EMA above THRESHOLD_RISE.
        // EMA = 0.9*9.81 + 0.1*20.0 = 10.829 >= 10.5 (THRESHOLD_RISE).
        // Time gap from warmup end = 300ms (well above MIN_STEP_MS=250ms).
        val peakTs = nextTs + 300_000_000L
        val result = detector.processSample(PEAK_MAGNITUDE, peakTs)
        assertTrue("Expected step to be detected on peak above THRESHOLD_RISE", result)
    }

    @Test
    fun `no step detected when magnitude stays below THRESHOLD_RISE`() {
        // Feed samples just below the threshold — no step should fire.
        // With ALPHA=0.1 and starting EMA=9.81, many samples at 10.4 converge to 10.4 < 10.5.
        for (i in 0 until 10) {
            val ts = i * 300_000_000L // 300ms apart, well above timing gate
            val result = detector.processSample(10.4, ts)
            assertFalse("Did not expect step below threshold at sample $i", result)
        }
    }

    // ─── Timing gate: minimum 250 ms between steps ───────────────────────────

    @Test
    fun `timing gate rejects second peak within MIN_STEP_MS`() {
        var ts = warmUp()

        // First peak at t=+300ms — registers a step.
        val firstStepTs = ts + 300_000_000L
        val firstStep = detector.processSample(PEAK_MAGNITUDE, firstStepTs)
        assertTrue("Expected first peak to register a step", firstStep)
        ts = firstStepTs + SAMPLE_INTERVAL_NS

        // Drop EMA below THRESHOLD_FALL with enough fall samples.
        ts = feedSamples(FALL_MAGNITUDE, FALL_SAMPLES, ts)

        // Second peak only 100ms after the first step — timing gate should reject it.
        // 100ms < MIN_STEP_MS (250ms).
        val secondStepTs = firstStepTs + 100_000_000L
        val secondStep = detector.processSample(PEAK_MAGNITUDE, secondStepTs)
        assertFalse("Expected second peak within 250ms to be rejected", secondStep)
    }

    @Test
    fun `timing gate allows step after MIN_STEP_MS has elapsed`() {
        var ts = warmUp()

        // First step.
        val firstStepTs = ts + 300_000_000L
        val first = detector.processSample(PEAK_MAGNITUDE, firstStepTs)
        assertTrue("Expected first step", first)
        ts = firstStepTs + SAMPLE_INTERVAL_NS

        // Drop EMA below THRESHOLD_FALL.
        ts = feedSamples(FALL_MAGNITUDE, FALL_SAMPLES, ts)

        // Second peak 400ms after the first — timing gate allows it (400ms >= 250ms).
        val second = detector.processSample(PEAK_MAGNITUDE, firstStepTs + 400_000_000L)
        assertTrue("Expected second step after 400ms", second)
    }

    // ─── Hysteresis: no re-trigger without falling below THRESHOLD_FALL ───────

    @Test
    fun `hysteresis prevents re-trigger while magnitude stays above THRESHOLD_FALL`() {
        val ts = warmUp()

        // Trigger a step.
        val firstStepTs = ts + 300_000_000L
        val first = detector.processSample(PEAK_MAGNITUDE, firstStepTs)
        assertTrue("Expected initial step", first)

        // EMA is now ~10.829 (above THRESHOLD_FALL=10.0).
        // Without any fall samples, aboveThreshold is still true.
        // A second peak 400ms later must NOT fire.
        val secondAttempt = detector.processSample(PEAK_MAGNITUDE, firstStepTs + 400_000_000L)
        assertFalse("Expected no re-trigger while still above THRESHOLD_FALL", secondAttempt)
    }

    @Test
    fun `hysteresis resets when magnitude drops below THRESHOLD_FALL`() {
        var ts = warmUp()

        // First step.
        val firstStepTs = ts + 300_000_000L
        detector.processSample(PEAK_MAGNITUDE, firstStepTs)
        ts = firstStepTs + SAMPLE_INTERVAL_NS

        // Feed enough fall samples to bring EMA below THRESHOLD_FALL.
        ts = feedSamples(FALL_MAGNITUDE, FALL_SAMPLES, ts)

        // Now rise again 400ms after the first step — should fire.
        val second = detector.processSample(PEAK_MAGNITUDE, firstStepTs + 400_000_000L)
        assertTrue("Expected step after hysteresis reset", second)
    }

    // ─── Multiple steps at walking cadence (~100 steps/min ≈ 600ms interval) ──

    @Test
    fun `multiple steps at walking cadence are all counted`() {
        warmUp()
        // Use absolute timestamps independent of warmup so timing gate is easy to reason about.
        // Each step cycle: peak at t=0, 600ms, 1200ms, …; fall samples in between.
        val stepIntervalNs = 600_000_000L // 600ms — well above MIN_STEP_MS (250ms)
        var ts = 10_000_000_000L // start 10 seconds out so warmup doesn't interfere

        var stepCount = 0
        for (step in 0 until 5) {
            val peakTs = ts
            if (detector.processSample(PEAK_MAGNITUDE, peakTs)) stepCount++
            // Feed fall samples immediately after the peak to reset hysteresis.
            ts = feedSamples(FALL_MAGNITUDE, FALL_SAMPLES, peakTs + SAMPLE_INTERVAL_NS)
            // Advance to next step interval.
            ts = peakTs + stepIntervalNs
        }

        assertEquals("Expected 5 steps at walking cadence", 5, stepCount)
    }

    // ─── reset() clears state ─────────────────────────────────────────────────

    @Test
    fun `reset clears state so first event after reset cannot violate timing gate`() {
        // Warm up and trigger a step.
        var ts = warmUp()
        val firstStepTs = ts + 300_000_000L
        detector.processSample(PEAK_MAGNITUDE, firstStepTs)

        // Reset — clears lastStepNs, filteredMagnitude and aboveThreshold.
        detector.reset()

        // Re-warm EMA from reset state (starts again at GRAVITY).
        ts = warmUp()

        // A peak 300ms from warm-up end should succeed: timing gate has lastStepNs=0,
        // so any non-zero elapsed time satisfies the MIN_STEP_MS gate.
        val afterReset = detector.processSample(PEAK_MAGNITUDE, ts + 300_000_000L)
        assertTrue("Expected step to succeed after reset", afterReset)
    }

    @Test
    fun `reset clears filteredMagnitude back to GRAVITY`() {
        // Drive EMA well above resting by feeding high-magnitude samples.
        feedSamples(PEAK_MAGNITUDE, 50, 0L)
        // EMA is now ~20.0 (converged to PEAK_MAGNITUDE).

        detector.reset()

        // After reset EMA = GRAVITY (9.81). A single sample at GRAVITY keeps it at 9.81.
        // 9.81 < THRESHOLD_RISE (10.5), so no step should fire.
        val result = detector.processSample(AccelerometerStepDetector.GRAVITY, 1_000_000_000L)
        assertFalse("Expected no step after reset when magnitude is at resting gravity", result)
    }

    // ─── SensorType enum ──────────────────────────────────────────────────────

    @Test
    fun `SensorType enum has ACCELEROMETER entry`() {
        assertEquals(SensorType.ACCELEROMETER, SensorType.valueOf("ACCELEROMETER"))
    }

    @Test
    fun `SensorType enum now contains exactly four values`() {
        assertEquals(4, SensorType.entries.size)
    }

    @Test
    fun `ACCELEROMETER is ordered between STEP_DETECTOR and NONE`() {
        val values = SensorType.entries
        val detectorIdx = values.indexOf(SensorType.STEP_DETECTOR)
        val accelIdx = values.indexOf(SensorType.ACCELEROMETER)
        val noneIdx = values.indexOf(SensorType.NONE)
        assertTrue("ACCELEROMETER must come after STEP_DETECTOR", accelIdx > detectorIdx)
        assertTrue("ACCELEROMETER must come before NONE", accelIdx < noneIdx)
    }

    // ─── NaN / Infinity input validation ─────────────────────────────────────

    @Test
    fun `NaN magnitude returns false and detector remains functional after`() {
        val ts = warmUp()

        // NaN magnitude must be silently rejected (returns false).
        val nanResult = detector.processSample(Double.NaN, ts + 300_000_000L)
        assertFalse("Expected NaN magnitude to return false", nanResult)

        // Feed fall samples to ensure EMA is not corrupted, then trigger a real step.
        var nextTs = feedSamples(AccelerometerStepDetector.GRAVITY, 20, ts + 300_000_000L + SAMPLE_INTERVAL_NS)
        val afterNan = detector.processSample(PEAK_MAGNITUDE, nextTs + 300_000_000L)
        assertTrue("Expected detector to remain functional after NaN sample", afterNan)
    }

    @Test
    fun `Infinity magnitude returns false and detector remains functional after`() {
        val ts = warmUp()

        // Positive infinity must be rejected.
        val infResult = detector.processSample(Double.POSITIVE_INFINITY, ts + 300_000_000L)
        assertFalse("Expected Infinity magnitude to return false", infResult)

        // Feed fall samples then trigger a real step.
        var nextTs = feedSamples(AccelerometerStepDetector.GRAVITY, 20, ts + 300_000_000L + SAMPLE_INTERVAL_NS)
        val afterInf = detector.processSample(PEAK_MAGNITUDE, nextTs + 300_000_000L)
        assertTrue("Expected detector to remain functional after Infinity sample", afterInf)
    }

    @Test
    fun `backwards timestamp smaller than previous is rejected via timing gate`() {
        var ts = warmUp()

        // Trigger a legitimate first step.
        val firstStepTs = ts + 300_000_000L
        val first = detector.processSample(PEAK_MAGNITUDE, firstStepTs)
        assertTrue("Expected first step to register", first)
        ts = firstStepTs + SAMPLE_INTERVAL_NS

        // Drop EMA below THRESHOLD_FALL.
        ts = feedSamples(FALL_MAGNITUDE, FALL_SAMPLES, ts)

        // Supply a peak with a timestamp BEFORE the first step (backwards timestamp).
        // elapsedMs = (backwardsTs - firstStepTs) / 1_000_000 will be negative,
        // which is < MIN_STEP_MS (250), so the timing gate correctly rejects it.
        val backwardsTs = firstStepTs - 100_000_000L
        val backwards = detector.processSample(PEAK_MAGNITUDE, backwardsTs)
        assertFalse("Expected backwards timestamp to be rejected by timing gate", backwards)
    }

    // ─── selectSensorType with hasAccelerometer ───────────────────────────────

    @Test
    fun `selectSensorType returns ACCELEROMETER when only accelerometer is available`() {
        val result = selectSensorType(
            hasStepCounter = false,
            hasStepDetector = false,
            hasAccelerometer = true,
        )
        assertEquals(SensorType.ACCELEROMETER, result)
    }

    @Test
    fun `selectSensorType returns NONE when all sensors unavailable`() {
        val result = selectSensorType(
            hasStepCounter = false,
            hasStepDetector = false,
            hasAccelerometer = false,
        )
        assertEquals(SensorType.NONE, result)
    }

    @Test
    fun `selectSensorType prefers STEP_COUNTER over ACCELEROMETER`() {
        val result = selectSensorType(
            hasStepCounter = true,
            hasStepDetector = false,
            hasAccelerometer = true,
        )
        assertEquals(SensorType.STEP_COUNTER, result)
    }

    @Test
    fun `selectSensorType prefers STEP_DETECTOR over ACCELEROMETER`() {
        val result = selectSensorType(
            hasStepCounter = false,
            hasStepDetector = true,
            hasAccelerometer = true,
        )
        assertEquals(SensorType.STEP_DETECTOR, result)
    }
}
