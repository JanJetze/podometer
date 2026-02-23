// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Unit tests for [StepFrequencyTracker].
 *
 * All tests manipulate timestamps directly so there is no dependency on wall
 * clock or Android framework APIs.
 */
class StepFrequencyTrackerTest {

    private lateinit var tracker: StepFrequencyTracker

    companion object {
        /** Tolerance for floating-point comparisons. */
        private const val DELTA = 1e-9
    }

    @Before
    fun setUp() {
        tracker = StepFrequencyTracker()
    }

    // ─── Empty state ──────────────────────────────────────────────────────────

    @Test
    fun `computeStepFrequency returns zero for empty tracker`() {
        assertEquals(0.0, tracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `computeStepFrequency returns zero after reset`() {
        tracker.recordStep(1_000L)
        tracker.recordStep(2_000L)
        tracker.reset()
        assertEquals(0.0, tracker.computeStepFrequency(), DELTA)
    }

    // ─── Known step rates ─────────────────────────────────────────────────────

    @Test
    fun `computeStepFrequency returns 1 Hz for steps 1 second apart`() {
        // 11 events 1 second apart → 10 intervals of 1s → 10 steps over 10 seconds → 1.0 Hz
        val startMs = 0L
        for (i in 0..10) {
            tracker.recordStep(startMs + i * 1_000L)
        }
        // frequency = (count - 1) / durationSeconds = 10 / 10.0 = 1.0
        assertEquals(1.0, tracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `computeStepFrequency returns 2 Hz for steps 500ms apart`() {
        // 11 events 500ms apart → 10 intervals of 500ms → 10 steps over 5s → 2.0 Hz
        for (i in 0..10) {
            tracker.recordStep(i * 500L)
        }
        assertEquals(2.0, tracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `computeStepFrequency returns correct value for non-uniform steps`() {
        // 3 steps at t=0, 1000, 3000 → 2 steps over 3 seconds → ~0.667 Hz
        tracker.recordStep(0L)
        tracker.recordStep(1_000L)
        tracker.recordStep(3_000L)
        val expected = 2.0 / 3.0
        assertEquals(expected, tracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `computeStepFrequency returns zero for single step event`() {
        // Only one timestamp → no duration can be measured → 0.0 Hz
        tracker.recordStep(1_000L)
        assertEquals(0.0, tracker.computeStepFrequency(), DELTA)
    }

    // ─── Window expiry ────────────────────────────────────────────────────────

    @Test
    fun `old steps outside the window are excluded from frequency computation`() {
        // Window is 30 seconds by default.
        // Add some old steps well outside the window, then recent steps.
        val nowMs = 100_000L // 100 seconds reference time

        // Three old steps clustered at t=0 (100s ago — outside 30s window)
        tracker.recordStep(0L)
        tracker.recordStep(500L)
        tracker.recordStep(1_000L)

        // Two recent steps 1 second apart, both within the 30s window
        tracker.recordStep(nowMs - 1_000L)
        tracker.recordStep(nowMs)

        // computeStepFrequency uses the most recent timestamp as "now" reference
        // Only the last two steps are within the window:
        // count = 2, oldest = nowMs-1000, newest = nowMs → duration = 1s
        // frequency = (2-1) / 1.0 = 1.0 Hz
        assertEquals(1.0, tracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `all steps expire leaving zero frequency`() {
        // Add steps that are all very old (will be outside window relative to last recorded)
        // Use a custom short-window tracker so the "now" reference is controlled.
        // Actually, use the default tracker: add steps at t=0, compute at t=0+window+1.
        // We simulate by adding a single far-future step after old steps.
        val shortTracker = StepFrequencyTracker(capacity = StepFrequencyTracker.DEFAULT_CAPACITY, windowMs = 5_000L)

        // Old steps at t=0,1s,2s
        shortTracker.recordStep(0L)
        shortTracker.recordStep(1_000L)
        shortTracker.recordStep(2_000L)

        // A single step far in the future — forces all prior steps out of window
        shortTracker.recordStep(100_000L)

        // Only the last step remains in the window → single timestamp → 0.0 Hz
        assertEquals(0.0, shortTracker.computeStepFrequency(), DELTA)
    }

    @Test
    fun `custom window duration is respected`() {
        // Window of 2 seconds.
        val shortTracker = StepFrequencyTracker(capacity = StepFrequencyTracker.DEFAULT_CAPACITY, windowMs = 2_000L)

        // Steps at t=0, 500, 1000, 1500, 2000 ms (within 2s window) relative to last step
        // All 5 steps are within 2s window (newest - oldest = 2000ms = 2s)
        for (i in 0..4) {
            shortTracker.recordStep(i * 500L)
        }
        // 4 intervals of 500ms → 4 steps over 2.0s → 2.0 Hz
        assertEquals(2.0, shortTracker.computeStepFrequency(), DELTA)
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset allows fresh accumulation`() {
        tracker.recordStep(0L)
        tracker.recordStep(500L)
        tracker.reset()
        // After reset, add 3 new steps at 2 Hz
        tracker.recordStep(10_000L)
        tracker.recordStep(10_500L)
        tracker.recordStep(11_000L)
        // 2 intervals of 500ms → 2 steps over 1.0s → 2.0 Hz
        assertEquals(2.0, tracker.computeStepFrequency(), DELTA)
    }

    // ─── Thread safety ────────────────────────────────────────────────────────

    @Test
    fun `concurrent recordStep and computeStepFrequency do not throw`() {
        val exceptions = mutableListOf<Throwable>()

        val writer = thread(start = true) {
            try {
                for (i in 0 until 5_000) {
                    tracker.recordStep(i * 200L)
                }
            } catch (e: Throwable) {
                synchronized(exceptions) { exceptions.add(e) }
            }
        }

        val reader = thread(start = true) {
            try {
                for (i in 0 until 1_000) {
                    tracker.computeStepFrequency() // result discarded; must not crash
                }
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
