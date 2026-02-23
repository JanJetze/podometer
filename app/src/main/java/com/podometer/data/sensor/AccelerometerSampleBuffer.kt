// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import kotlin.math.sqrt

/**
 * Feature vector computed from a sliding window of accelerometer samples.
 *
 * All fields are population statistics (not sample statistics), consistent with
 * a fixed-window classifier that treats the buffer contents as the full population.
 *
 * @param magnitudeMean      Mean of acceleration magnitudes in the window (m/s²).
 * @param magnitudeStd       Population standard deviation of magnitudes (m/s²).
 * @param magnitudeVariance  Population variance of magnitudes (m/s²)². Equals [magnitudeStd]².
 * @param sampleCount        Number of samples in the window.
 * @param windowDurationMs   Duration from oldest to newest sample in the window (milliseconds).
 */
data class WindowFeatures(
    val magnitudeMean: Double,
    val magnitudeStd: Double,
    val magnitudeVariance: Double,
    val sampleCount: Int,
    val windowDurationMs: Long,
)

/**
 * Thread-safe circular buffer storing accelerometer magnitude samples for
 * sliding-window feature extraction.
 *
 * Design constraints:
 * - Zero allocations in [addSample] (hot path from sensor callback).
 * - Pre-allocated [DoubleArray] and [LongArray] fixed at construction time.
 * - [addSample] and [computeWindowFeatures] may be called from different threads;
 *   both are `@Synchronized` on this instance to guarantee multi-field consistency
 *   across [magnitudes], [timestamps], [head], and [count].
 * - At SENSOR_DELAY_NORMAL (~5 Hz) the lock is acquired approximately 5 times/second,
 *   so contention overhead is negligible.
 *
 * @param capacity Maximum number of samples to retain. Default: [DEFAULT_CAPACITY]
 *   (75 slots = 15 seconds at ~5 Hz SENSOR_DELAY_NORMAL rate).
 */
class AccelerometerSampleBuffer(val capacity: Int = DEFAULT_CAPACITY) {

    // Pre-allocated backing arrays — no allocations happen after construction.
    private val magnitudes = DoubleArray(capacity)
    private val timestamps = LongArray(capacity)

    // Ring buffer state.
    private var head: Int = 0   // index of the next write position (oldest slot after wrap)
    private var count: Int = 0  // number of valid (filled) slots in the buffer

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Records a new magnitude sample into the circular buffer.
     *
     * This method is O(1) and performs zero heap allocations, making it safe
     * to call from the sensor-callback hot path.
     *
     * When the buffer is full, the oldest sample is silently overwritten (FIFO
     * eviction).
     *
     * @param magnitude   Acceleration vector magnitude in m/s², typically
     *                    `sqrt(x² + y² + z²)` from the raw sensor event.
     * @param timestampNs Monotonic event timestamp in nanoseconds
     *                    (use [android.hardware.SensorEvent.timestamp] in production).
     */
    @Synchronized
    fun addSample(magnitude: Double, timestampNs: Long) {
        magnitudes[head] = magnitude
        timestamps[head] = timestampNs
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /**
     * Computes [WindowFeatures] over all samples currently in the buffer.
     *
     * Uses a two-pass algorithm (mean then variance) which is accurate and
     * readable for the small fixed window size (≤ 75 samples).
     *
     * @return A [WindowFeatures] instance, or `null` if the buffer contains
     *   fewer than [MIN_SAMPLES] entries.
     */
    @Synchronized
    fun computeWindowFeatures(): WindowFeatures? {
        if (count < MIN_SAMPLES) return null

        // Snapshot the valid slice of the ring buffer into local variables
        // to keep arithmetic clean. We read `count` samples starting from
        // the oldest entry, which is at index `(head - count + capacity) % capacity`
        // when the buffer is not full, or simply `head` when it is full.
        val n = count
        val startIdx = (head - n + capacity) % capacity

        // Pass 1: mean and timestamp range.
        var sum = 0.0
        var oldestTs = Long.MAX_VALUE
        var newestTs = Long.MIN_VALUE
        for (i in 0 until n) {
            val idx = (startIdx + i) % capacity
            val mag = magnitudes[idx]
            sum += mag
            val ts = timestamps[idx]
            if (ts < oldestTs) oldestTs = ts
            if (ts > newestTs) newestTs = ts
        }
        val mean = sum / n

        // Pass 2: population variance.
        var varianceSum = 0.0
        for (i in 0 until n) {
            val idx = (startIdx + i) % capacity
            val diff = magnitudes[idx] - mean
            varianceSum += diff * diff
        }
        val variance = varianceSum / n
        val std = sqrt(variance)

        val durationMs = (newestTs - oldestTs) / 1_000_000L

        return WindowFeatures(
            magnitudeMean = mean,
            magnitudeStd = std,
            magnitudeVariance = variance,
            sampleCount = n,
            windowDurationMs = durationMs,
        )
    }

    /**
     * Resets the buffer, discarding all stored samples.
     *
     * Call this when the sensor is unregistered so stale data from a previous
     * session does not contaminate the next session's feature window.
     */
    @Synchronized
    fun reset() {
        head = 0
        count = 0
    }

    /**
     * Returns the number of valid samples currently stored in the buffer.
     *
     * This value is in the range `[0, capacity]`.
     */
    @Synchronized
    fun size(): Int = count

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Default circular buffer capacity.
         *
         * 75 slots covers 15 seconds at SENSOR_DELAY_NORMAL (~5 Hz delivery rate).
         * This is sufficient for activity-classification sliding windows.
         */
        const val DEFAULT_CAPACITY = 75

        /**
         * Minimum number of samples required before [computeWindowFeatures] will
         * return a non-null result.
         *
         * 10 samples = ~2 seconds at 5 Hz — the minimum meaningful window for
         * variance-based activity classification.
         */
        const val MIN_SAMPLES = 10
    }
}
