// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks step-event timestamps in a sliding time window and computes step
 * frequency (steps per second) on demand.
 *
 * Design constraints (consistent with [AccelerometerSampleBuffer]):
 * - Pre-allocated [LongArray] ring buffer — zero heap allocations in [recordStep].
 * - All public methods are `@Synchronized` to guarantee thread safety across
 *   concurrent sensor-callback writers and classifier-coroutine readers.
 * - Pure Kotlin with no Android framework dependencies, making it fully
 *   unit-testable on the JVM.
 *
 * Algorithm:
 *  1. [recordStep] stores the step timestamp in a circular ring buffer. If the
 *     buffer is full the oldest entry is overwritten (FIFO eviction).
 *  2. [computeStepFrequency] scans the buffer, discards any timestamps older
 *     than `now − windowMs` (where *now* is the newest stored timestamp), and
 *     computes:
 *
 *     ```
 *     frequency = (validCount − 1) / windowDurationSeconds
 *     ```
 *
 *     where `validCount` is the number of timestamps in the window and
 *     `windowDurationSeconds` is the span from the oldest to the newest of
 *     those timestamps.  Returns `0.0` if fewer than two timestamps survive
 *     the window filter.
 *
 * @param capacity  Maximum number of step timestamps to retain.  Defaults to
 *   [DEFAULT_CAPACITY] (150 slots — well above the maximum step rate of ~3 Hz
 *   over a 30-second window, i.e. 90 steps).
 * @param windowMs  Sliding window width in milliseconds.  Only timestamps
 *   within this many milliseconds of the most recent recorded step are
 *   included in [computeStepFrequency].  Defaults to [DEFAULT_WINDOW_MS]
 *   (30 seconds).
 */
@Singleton
class StepFrequencyTracker(
    private val capacity: Int,
    private val windowMs: Long,
) {

    /**
     * No-argument constructor used by Hilt for dependency injection.
     *
     * Uses the default [DEFAULT_CAPACITY] and [DEFAULT_WINDOW_MS] values.
     * The parameterized constructor is provided for unit tests that need
     * custom window sizes without requiring the DI graph.
     */
    @Inject
    constructor() : this(capacity = DEFAULT_CAPACITY, windowMs = DEFAULT_WINDOW_MS)

    // Pre-allocated backing array — no heap allocations after construction.
    private val timestamps = LongArray(capacity)

    // Ring buffer state.
    private var head: Int = 0   // next write position (oldest slot after wrap)
    private var count: Int = 0  // number of valid slots

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Records a step event at [timestampMs] (wall-clock milliseconds).
     *
     * This method is O(1) and performs zero heap allocations, making it safe
     * to call from the sensor-callback hot path.
     *
     * When the buffer is full the oldest timestamp is silently overwritten.
     *
     * @param timestampMs Monotonic or wall-clock time of the step event in
     *   milliseconds.  Use `System.currentTimeMillis()` in production.
     */
    @Synchronized
    fun recordStep(timestampMs: Long) {
        timestamps[head] = timestampMs
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /**
     * Computes the step frequency (steps per second) over the sliding window.
     *
     * Only timestamps within [windowMs] of the most recently recorded step are
     * considered.  Returns `0.0` when fewer than two qualifying timestamps
     * exist (i.e., there are not enough events to measure a rate).
     *
     * Time complexity: O([capacity]) — iterates over all stored timestamps.
     *
     * @return Steps per second, or `0.0` if the frequency cannot be determined.
     */
    @Synchronized
    fun computeStepFrequency(): Double {
        if (count < 2) return 0.0

        val n = count
        val startIdx = (head - n + capacity) % capacity

        // Find the newest timestamp to use as the "now" reference.
        var newestTs = Long.MIN_VALUE
        for (i in 0 until n) {
            val ts = timestamps[(startIdx + i) % capacity]
            if (ts > newestTs) newestTs = ts
        }

        val cutoff = newestTs - windowMs

        // Collect timestamps within the window.
        var validCount = 0
        var oldestInWindow = Long.MAX_VALUE
        for (i in 0 until n) {
            val ts = timestamps[(startIdx + i) % capacity]
            if (ts >= cutoff) {
                validCount++
                if (ts < oldestInWindow) oldestInWindow = ts
            }
        }

        if (validCount < 2) return 0.0

        val durationSeconds = (newestTs - oldestInWindow) / 1_000.0
        if (durationSeconds <= 0.0) return 0.0

        // (validCount - 1) intervals observed over durationSeconds.
        return (validCount - 1).toDouble() / durationSeconds
    }

    /**
     * Clears all stored step timestamps.
     *
     * Call this when the step sensor is stopped so stale data from a previous
     * session does not contaminate the next session's frequency estimate.
     */
    @Synchronized
    fun reset() {
        head = 0
        count = 0
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Default ring-buffer capacity.
         *
         * 150 slots comfortably exceeds the maximum feasible step count in the
         * default 30-second window (a sprinter at 3 Hz produces 90 steps).
         */
        const val DEFAULT_CAPACITY = 150

        /**
         * Default sliding window width in milliseconds (30 seconds).
         *
         * Walking cadence is typically 1.5–2.5 Hz; 30 seconds provides at
         * least 45 step events, giving a stable frequency estimate.
         */
        const val DEFAULT_WINDOW_MS = 30_000L
    }
}
