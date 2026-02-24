// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StepTrackingService.spreadTimestamps].
 *
 * Exercises the pure helper that distributes burst step-counter events across
 * time so that [com.podometer.data.sensor.StepFrequencyTracker] receives
 * distinct timestamps instead of identical ones.
 */
class SpreadTimestampsTest {

    // ─── delta == 1 (single step) ─────────────────────────────────────────────

    @Test
    fun `delta 1 returns single element equal to nowMs`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 1,
        )
        assertEquals("should produce exactly one timestamp", 1, result.size)
        assertEquals("single step should equal nowMs", 100_000L, result[0])
    }

    @Test
    fun `delta 1 with no prior history returns single element at nowMs`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 0L,
            nowMs = 50_000L,
            delta = 1,
        )
        assertEquals(1, result.size)
        assertEquals(50_000L, result[0])
    }

    // ─── delta > 1, with prior history ────────────────────────────────────────

    @Test
    fun `delta 5 with prior history produces 5 timestamps`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 5,
        )
        assertEquals("should produce exactly delta timestamps", 5, result.size)
    }

    @Test
    fun `delta 5 with prior history produces strictly ascending timestamps`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 5,
        )
        for (i in 1 until result.size) {
            assertTrue(
                "timestamps must be strictly ascending: result[$i]=${result[i]} <= result[${i-1}]=${result[i-1]}",
                result[i] > result[i - 1],
            )
        }
    }

    @Test
    fun `delta 5 with prior history last timestamp equals nowMs`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 5,
        )
        assertEquals("last timestamp should equal nowMs", 100_000L, result[result.size - 1])
    }

    @Test
    fun `delta 5 with prior history all timestamps are unique`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 5,
        )
        val unique = result.toSet()
        assertEquals("all timestamps must be unique (no duplicates)", result.size, unique.size)
    }

    @Test
    fun `delta 5 with prior history timestamps spread from after lastEventMs to nowMs`() {
        val lastEventMs = 90_000L
        val nowMs = 100_000L
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = lastEventMs,
            nowMs = nowMs,
            delta = 5,
        )
        // All timestamps must be strictly after lastEventMs and at or before nowMs
        for (ts in result) {
            assertTrue("timestamp $ts should be > lastEventMs $lastEventMs", ts > lastEventMs)
            assertTrue("timestamp $ts should be <= nowMs $nowMs", ts <= nowMs)
        }
    }

    // ─── delta > 1, first event (lastEventMs == 0) ────────────────────────────

    @Test
    fun `delta 5 first event produces 5 timestamps`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 0L,
            nowMs = 100_000L,
            delta = 5,
        )
        assertEquals(5, result.size)
    }

    @Test
    fun `delta 5 first event produces strictly ascending timestamps`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 0L,
            nowMs = 100_000L,
            delta = 5,
        )
        for (i in 1 until result.size) {
            assertTrue(
                "timestamps must be strictly ascending on first event",
                result[i] > result[i - 1],
            )
        }
    }

    @Test
    fun `delta 5 first event all timestamps are unique`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 0L,
            nowMs = 100_000L,
            delta = 5,
        )
        val unique = result.toSet()
        assertEquals("all timestamps must be unique on first event", result.size, unique.size)
    }

    @Test
    fun `delta 5 first event last timestamp equals nowMs`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 0L,
            nowMs = 100_000L,
            delta = 5,
        )
        assertEquals("last timestamp should equal nowMs on first event", 100_000L, result[result.size - 1])
    }

    // ─── Edge: delta 2 ────────────────────────────────────────────────────────

    @Test
    fun `delta 2 with prior history produces two distinct timestamps`() {
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 90_000L,
            nowMs = 100_000L,
            delta = 2,
        )
        assertEquals(2, result.size)
        assertTrue("two timestamps must be distinct", result[0] != result[1])
        assertEquals("last must be nowMs", 100_000L, result[1])
    }

    // ─── Very short span (lastEventMs just before nowMs) ─────────────────────

    @Test
    fun `delta 3 with 3ms span between events produces unique timestamps`() {
        // Only 3ms available for 3 steps → each gets exactly 1ms apart
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 997L,
            nowMs = 1_000L,
            delta = 3,
        )
        assertEquals(3, result.size)
        val unique = result.toSet()
        assertEquals("all timestamps must be unique even in short span", 3, unique.size)
    }

    // ─── Span shorter than delta (coerceAtLeast applied) ─────────────────────

    @Test
    fun `when span is shorter than delta timestamps are still unique`() {
        // span = 2ms, delta = 5 → span coerced to delta(5) to guarantee uniqueness
        val result = StepTrackingService.spreadTimestamps(
            lastEventMs = 998L,
            nowMs = 1_000L,
            delta = 5,
        )
        assertEquals(5, result.size)
        val unique = result.toSet()
        assertEquals("timestamps must still be unique when span < delta", 5, unique.size)
    }
}
