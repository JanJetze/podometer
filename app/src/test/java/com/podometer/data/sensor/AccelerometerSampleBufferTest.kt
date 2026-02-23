// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [AccelerometerSampleBuffer] and [WindowFeatures].
 *
 * All tests use [AccelerometerSampleBuffer.addSample] directly with synthetic
 * magnitude and timestamp values, without any Android framework dependency.
 *
 * Mathematical reference (population statistics over n samples):
 *   mean     = sum(x_i) / n
 *   variance = sum((x_i - mean)^2) / n
 *   std      = sqrt(variance)
 */
class AccelerometerSampleBufferTest {

    private lateinit var buffer: AccelerometerSampleBuffer

    companion object {
        /** Tolerance for floating-point comparisons in feature values. */
        private const val DELTA = 1e-9

        /** One second in nanoseconds. */
        private const val ONE_SECOND_NS = 1_000_000_000L

        /** Nominal SENSOR_DELAY_NORMAL interval: ~200 ms in nanoseconds. */
        private const val SENSOR_DELAY_NORMAL_NS = 200_000_000L
    }

    @Before
    fun setUp() {
        buffer = AccelerometerSampleBuffer()
    }

    // ─── size() and basic storage ─────────────────────────────────────────────

    @Test
    fun `size returns 0 for empty buffer`() {
        assertEquals(0, buffer.size())
    }

    @Test
    fun `size increments with each added sample up to capacity`() {
        buffer.addSample(9.81, 0L)
        assertEquals(1, buffer.size())
        buffer.addSample(9.81, ONE_SECOND_NS)
        assertEquals(2, buffer.size())
    }

    @Test
    fun `size does not exceed capacity`() {
        val capacity = AccelerometerSampleBuffer.DEFAULT_CAPACITY
        for (i in 0 until capacity + 20) {
            buffer.addSample(9.81, i * ONE_SECOND_NS)
        }
        assertEquals(capacity, buffer.size())
    }

    // ─── computeWindowFeatures: null when insufficient samples ───────────────

    @Test
    fun `computeWindowFeatures returns null on empty buffer`() {
        assertNull(buffer.computeWindowFeatures())
    }

    @Test
    fun `computeWindowFeatures returns null when below MIN_SAMPLES`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples - 1) {
            buffer.addSample(9.81, i * ONE_SECOND_NS)
        }
        assertNull(buffer.computeWindowFeatures())
    }

    @Test
    fun `computeWindowFeatures returns non-null at exactly MIN_SAMPLES`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * ONE_SECOND_NS)
        }
        assertNotNull(buffer.computeWindowFeatures())
    }

    // ─── Mean computation ─────────────────────────────────────────────────────

    @Test
    fun `computeWindowFeatures calculates correct mean for uniform magnitudes`() {
        val magnitude = 9.81
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(magnitude, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(magnitude, features.magnitudeMean, DELTA)
    }

    @Test
    fun `computeWindowFeatures calculates correct mean for varied magnitudes`() {
        // Add exactly 4 samples with known magnitudes: 1.0, 2.0, 3.0, 4.0, ...
        // Use MIN_SAMPLES count for clarity
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        var sum = 0.0
        for (i in 0 until minSamples) {
            val mag = (i + 1).toDouble()
            sum += mag
            buffer.addSample(mag, i * SENSOR_DELAY_NORMAL_NS)
        }
        val expectedMean = sum / minSamples
        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedMean, features.magnitudeMean, DELTA)
    }

    // ─── Variance computation ─────────────────────────────────────────────────

    @Test
    fun `computeWindowFeatures returns variance zero for identical magnitudes`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(5.0, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(0.0, features.magnitudeVariance, DELTA)
    }

    @Test
    fun `computeWindowFeatures calculates correct variance for two distinct values`() {
        // Use MIN_SAMPLES samples alternating between 8.0 and 12.0.
        // mean = 10.0; each deviation = ±2.0; variance = 4.0
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        // Ensure even count for perfect alternation
        val count = if (minSamples % 2 == 0) minSamples else minSamples + 1
        for (i in 0 until count) {
            val mag = if (i % 2 == 0) 8.0 else 12.0
            buffer.addSample(mag, i * SENSOR_DELAY_NORMAL_NS)
        }
        val expectedVariance = 4.0 // (2^2 + 2^2) / 2 = 4.0
        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedVariance, features.magnitudeVariance, DELTA)
    }

    @Test
    fun `computeWindowFeatures calculates correct variance for known dataset`() {
        // Dataset: [2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0]
        // n=8, mean=5.0, variance = sum((x-5)^2)/8 = (9+1+1+1+0+0+4+16)/8 = 32/8 = 4.0
        val data = doubleArrayOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        // Need at least MIN_SAMPLES; pad with mean value to not change mean/variance
        // Actually MIN_SAMPLES=10, so add 2 more at mean=5.0
        val padded = data.toMutableList()
        padded.add(5.0)
        padded.add(5.0)
        // Now n=10, mean=5.0, variance = (9+1+1+1+0+0+4+16+0+0)/10 = 32/10 = 3.2
        val n = padded.size.toDouble()
        val mean = padded.sum() / n
        val expectedVariance = padded.sumOf { (it - mean) * (it - mean) } / n
        padded.forEachIndexed { i, mag ->
            buffer.addSample(mag, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedVariance, features.magnitudeVariance, DELTA)
        assertEquals(mean, features.magnitudeMean, DELTA)
    }

    // ─── Standard deviation computation ───────────────────────────────────────

    @Test
    fun `computeWindowFeatures returns std zero for identical magnitudes`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(5.0, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(0.0, features.magnitudeStd, DELTA)
    }

    @Test
    fun `computeWindowFeatures std equals sqrt of variance`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample((i + 1).toDouble(), i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(sqrt(features.magnitudeVariance), features.magnitudeStd, DELTA)
    }

    @Test
    fun `computeWindowFeatures std is sqrt of variance for alternating values`() {
        // Alternating 8.0/12.0 — variance=4.0, std=2.0
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        val count = if (minSamples % 2 == 0) minSamples else minSamples + 1
        for (i in 0 until count) {
            val mag = if (i % 2 == 0) 8.0 else 12.0
            buffer.addSample(mag, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(2.0, features.magnitudeStd, DELTA)
    }

    // ─── Window duration computation ───────────────────────────────────────────

    @Test
    fun `computeWindowFeatures windowDurationMs is difference between newest and oldest timestamp`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        val intervalNs = SENSOR_DELAY_NORMAL_NS
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * intervalNs)
        }
        // oldest = 0, newest = (minSamples - 1) * intervalNs
        val expectedDurationMs = ((minSamples - 1) * intervalNs) / 1_000_000L
        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedDurationMs, features.windowDurationMs)
    }

    @Test
    fun `computeWindowFeatures windowDurationMs is zero when all samples have same timestamp`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(9.81, 1_000_000_000L)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(0L, features.windowDurationMs)
    }

    // ─── sampleCount field ────────────────────────────────────────────────────

    @Test
    fun `computeWindowFeatures sampleCount matches number of samples added`() {
        val count = AccelerometerSampleBuffer.MIN_SAMPLES + 5
        for (i in 0 until count) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(count, features.sampleCount)
    }

    @Test
    fun `computeWindowFeatures sampleCount capped at capacity after wrap-around`() {
        val capacity = AccelerometerSampleBuffer.DEFAULT_CAPACITY
        for (i in 0 until capacity + 10) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(capacity, features.sampleCount)
    }

    // ─── Circular buffer wrap-around behaviour ────────────────────────────────

    @Test
    fun `buffer wraps around and oldest samples are overwritten`() {
        val capacity = AccelerometerSampleBuffer.DEFAULT_CAPACITY
        // Fill buffer with magnitude 1.0
        for (i in 0 until capacity) {
            buffer.addSample(1.0, i * SENSOR_DELAY_NORMAL_NS)
        }
        // Overwrite with 10 samples at magnitude 100.0
        val overwriteCount = 10
        for (i in 0 until overwriteCount) {
            buffer.addSample(100.0, (capacity + i) * SENSOR_DELAY_NORMAL_NS)
        }
        // Buffer holds: 10 samples at 100.0 + (capacity-10) samples at 1.0
        // Mean = (10*100 + (capacity-10)*1) / capacity
        val expectedMean = (overwriteCount * 100.0 + (capacity - overwriteCount) * 1.0) / capacity
        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedMean, features.magnitudeMean, 1e-6)
    }

    @Test
    fun `size stays at capacity after wrap-around`() {
        val capacity = AccelerometerSampleBuffer.DEFAULT_CAPACITY
        for (i in 0 until capacity * 2) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        assertEquals(capacity, buffer.size())
    }

    // ─── reset() ─────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all samples so size returns 0`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.reset()
        assertEquals(0, buffer.size())
    }

    @Test
    fun `reset causes computeWindowFeatures to return null`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.reset()
        assertNull(buffer.computeWindowFeatures())
    }

    @Test
    fun `buffer works correctly after reset`() {
        // Fill and reset
        for (i in 0 until AccelerometerSampleBuffer.DEFAULT_CAPACITY) {
            buffer.addSample(100.0, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.reset()

        // Add fresh samples after reset
        val freshMagnitude = 5.0
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(freshMagnitude, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(freshMagnitude, features.magnitudeMean, DELTA)
        assertEquals(0.0, features.magnitudeVariance, DELTA)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `single outlier affects features correctly`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        // 9 samples at 10.0, 1 outlier at 100.0
        val regularCount = minSamples - 1
        for (i in 0 until regularCount) {
            buffer.addSample(10.0, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.addSample(100.0, regularCount * SENSOR_DELAY_NORMAL_NS)

        val expectedMean = (regularCount * 10.0 + 100.0) / minSamples
        val expectedVariance = (
            (regularCount * (10.0 - expectedMean) * (10.0 - expectedMean)) +
                (100.0 - expectedMean) * (100.0 - expectedMean)
            ) / minSamples
        val expectedStd = sqrt(expectedVariance)

        val features = buffer.computeWindowFeatures()!!
        assertEquals(expectedMean, features.magnitudeMean, DELTA)
        assertEquals(expectedVariance, features.magnitudeVariance, DELTA)
        assertEquals(expectedStd, features.magnitudeStd, DELTA)
    }

    @Test
    fun `zero-variance window with all same magnitude returns std of zero`() {
        val magnitude = 9.81
        for (i in 0 until AccelerometerSampleBuffer.DEFAULT_CAPACITY) {
            buffer.addSample(magnitude, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(magnitude, features.magnitudeMean, DELTA)
        assertEquals(0.0, features.magnitudeVariance, DELTA)
        assertEquals(0.0, features.magnitudeStd, DELTA)
    }

    @Test
    fun `custom capacity buffer respects that capacity`() {
        val customCapacity = 20
        val customBuffer = AccelerometerSampleBuffer(customCapacity)
        for (i in 0 until customCapacity + 10) {
            customBuffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        assertEquals(customCapacity, customBuffer.size())
    }

    @Test
    fun `DEFAULT_CAPACITY is 75 to cover 15 seconds at 5Hz`() {
        assertEquals(75, AccelerometerSampleBuffer.DEFAULT_CAPACITY)
    }

    @Test
    fun `MIN_SAMPLES is 10`() {
        assertEquals(10, AccelerometerSampleBuffer.MIN_SAMPLES)
    }

    // ─── Thread safety: concurrent writes and reads do not crash ─────────────

    @Test
    fun `concurrent addSample and computeWindowFeatures do not throw`() {
        // Seed with enough samples so computeWindowFeatures has something to compute
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }

        val writeCount = 5_000
        val readCount = 1_000
        val exceptions = mutableListOf<Throwable>()

        val writer = thread(start = true) {
            try {
                for (i in 0 until writeCount) {
                    buffer.addSample(9.81 + (i % 5), i * SENSOR_DELAY_NORMAL_NS)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val reader = thread(start = true) {
            try {
                for (i in 0 until readCount) {
                    buffer.computeWindowFeatures() // result is discarded; must not crash
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        writer.join(10_000)
        reader.join(10_000)

        assertTrue(
            "Expected no exceptions during concurrent access but got: $exceptions",
            exceptions.isEmpty(),
        )
    }

    // ─── WindowFeatures data class ────────────────────────────────────────────

    @Test
    fun `WindowFeatures equality and copy work correctly`() {
        val f1 = WindowFeatures(
            magnitudeMean = 9.81,
            magnitudeStd = 0.5,
            magnitudeVariance = 0.25,
            sampleCount = 10,
            windowDurationMs = 2000L,
            zeroCrossingRate = 1.5,
        )
        val f2 = f1.copy(sampleCount = 20)
        assertEquals(9.81, f2.magnitudeMean, DELTA)
        assertEquals(20, f2.sampleCount)
    }

    @Test
    fun `WindowFeatures magnitudeVariance equals magnitudeStd squared`() {
        for (i in 0 until AccelerometerSampleBuffer.MIN_SAMPLES) {
            buffer.addSample((i % 3 + 8).toDouble(), i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        val stdSquared = features.magnitudeStd * features.magnitudeStd
        assertEquals(stdSquared, features.magnitudeVariance, 1e-10)
    }

    // ─── Zero-crossing rate ───────────────────────────────────────────────────

    @Test
    fun `zeroCrossingRate is zero for constant signal`() {
        // All samples the same → mean equals every value → zero sign changes
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        assertEquals(0.0, features.zeroCrossingRate, DELTA)
    }

    @Test
    fun `zeroCrossingRate is high for perfectly alternating signal`() {
        // Alternating 8.0/12.0 → mean=10 → centred signal alternates +2/-2
        // N=10 samples → 9 consecutive pairs → 9 sign changes
        // Duration = (10-1) * 200ms = 1800ms = 1.8s
        // ZCR = 9 / 1.8 = 5.0 crossings/second
        val count = AccelerometerSampleBuffer.MIN_SAMPLES // 10 (even)
        for (i in 0 until count) {
            val mag = if (i % 2 == 0) 8.0 else 12.0
            buffer.addSample(mag, i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        // 9 crossings over 1.8 seconds = 5.0 Hz
        assertEquals(5.0, features.zeroCrossingRate, DELTA)
    }

    @Test
    fun `zeroCrossingRate for known signal with predictable crossings`() {
        // Signal: [10, 11, 9, 11, 9, 11, 9, 11, 9, 11] → mean = 10.1
        // Centred: [-0.1, 0.9, -1.1, 0.9, -1.1, 0.9, -1.1, 0.9, -1.1, 0.9]
        // Sign pattern: -, +, -, +, -, +, -, +, -, +  → 9 sign changes
        // Duration = 9 * 200ms = 1800ms = 1.8s → ZCR = 9 / 1.8 = 5.0
        val values = doubleArrayOf(10.0, 11.0, 9.0, 11.0, 9.0, 11.0, 9.0, 11.0, 9.0, 11.0)
        for (i in values.indices) {
            buffer.addSample(values[i], i * SENSOR_DELAY_NORMAL_NS)
        }
        val features = buffer.computeWindowFeatures()!!
        // mean = (10+11+9+11+9+11+9+11+9+11)/10 = 101/10 = 10.1
        // centred first sample is negative, rest alternate
        assertEquals(5.0, features.zeroCrossingRate, DELTA)
    }

    @Test
    fun `zeroCrossingRate returns zero when window duration is zero`() {
        // All samples have same timestamp → durationMs = 0 → ZCR should be 0 not NaN/Inf
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(if (i % 2 == 0) 8.0 else 12.0, 1_000_000_000L)
        }
        val features = buffer.computeWindowFeatures()!!
        // Duration is 0 ms → ZCR should be 0.0 (no division by zero)
        assertEquals(0.0, features.zeroCrossingRate, DELTA)
    }

    // ─── Defensive: non-monotonic timestamps ──────────────────────────────────

    @Test
    fun `windowDurationMs is non-negative when timestamps are non-monotonic`() {
        // Simulate a device that delivers timestamps out of order: newest first,
        // then a stale (older) timestamp arrives.  newestTs - oldestTs would be
        // negative without the maxOf clamp, producing a negative duration.
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        val baseTs = 5_000_000_000L // 5 s in nanoseconds
        // Add MIN_SAMPLES - 1 samples with a high timestamp …
        for (i in 0 until minSamples - 1) {
            buffer.addSample(9.81, baseTs + i * SENSOR_DELAY_NORMAL_NS)
        }
        // … then add one sample with a much older timestamp (non-monotonic)
        buffer.addSample(9.81, 1_000L) // tiny timestamp, older than all others
        val features = buffer.computeWindowFeatures()!!
        assertTrue(
            "windowDurationMs must be >= 0 even for non-monotonic timestamps, " +
                "but was ${features.windowDurationMs}",
            features.windowDurationMs >= 0L,
        )
    }

    // ─── Defensive: NaN / Infinity sensor values ──────────────────────────────

    @Test
    fun `addSample with NaN magnitude is rejected and does not enter buffer`() {
        // Pre-fill with valid samples so computeWindowFeatures can return a result.
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        // Attempt to add a NaN sample.
        buffer.addSample(Double.NaN, minSamples * SENSOR_DELAY_NORMAL_NS)

        val features = buffer.computeWindowFeatures()!!
        // Mean and variance must still be finite — NaN must not have entered.
        assertTrue(
            "magnitudeMean must be finite after NaN addSample, but was ${features.magnitudeMean}",
            features.magnitudeMean.isFinite(),
        )
        assertTrue(
            "magnitudeVariance must be finite after NaN addSample, but was ${features.magnitudeVariance}",
            features.magnitudeVariance.isFinite(),
        )
    }

    @Test
    fun `addSample with positive Infinity magnitude is rejected and does not enter buffer`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.addSample(Double.POSITIVE_INFINITY, minSamples * SENSOR_DELAY_NORMAL_NS)

        val features = buffer.computeWindowFeatures()!!
        assertTrue(
            "magnitudeMean must be finite after Infinity addSample, but was ${features.magnitudeMean}",
            features.magnitudeMean.isFinite(),
        )
        assertTrue(
            "magnitudeVariance must be finite after Infinity addSample, but was ${features.magnitudeVariance}",
            features.magnitudeVariance.isFinite(),
        )
    }

    @Test
    fun `addSample with negative Infinity magnitude is rejected and does not enter buffer`() {
        val minSamples = AccelerometerSampleBuffer.MIN_SAMPLES
        for (i in 0 until minSamples) {
            buffer.addSample(9.81, i * SENSOR_DELAY_NORMAL_NS)
        }
        buffer.addSample(Double.NEGATIVE_INFINITY, minSamples * SENSOR_DELAY_NORMAL_NS)

        val features = buffer.computeWindowFeatures()!!
        assertTrue(
            "magnitudeMean must be finite after -Infinity addSample, but was ${features.magnitudeMean}",
            features.magnitudeMean.isFinite(),
        )
        assertTrue(
            "magnitudeVariance must be finite after -Infinity addSample, but was ${features.magnitudeVariance}",
            features.magnitudeVariance.isFinite(),
        )
    }
}
