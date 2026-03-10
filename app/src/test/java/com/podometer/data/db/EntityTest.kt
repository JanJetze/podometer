// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for Room entity data classes.
 *
 * Verifies default values, data class equality/copy behaviour, and
 * that fields can hold the values the rest of the app will supply.
 * Room annotations are verified indirectly via a successful build.
 */
class EntityTest {

    // ─── StepBucket ─────────────────────────────────────────────────────────

    @Test
    fun `StepBucket stores all fields correctly`() {
        val bucket = StepBucket(
            timestamp = 1_700_000_000_000L,
            stepCount = 42,
        )
        assertEquals(1_700_000_000_000L, bucket.timestamp)
        assertEquals(42, bucket.stepCount)
    }

    @Test
    fun `StepBucket data class equality works`() {
        val a = StepBucket(timestamp = 1000L, stepCount = 10)
        val b = StepBucket(timestamp = 1000L, stepCount = 10)
        assertEquals(a, b)
    }

    @Test
    fun `StepBucket copy changes only specified field`() {
        val original = StepBucket(timestamp = 1000L, stepCount = 5)
        val copied = original.copy(stepCount = 99)
        assertEquals(99, copied.stepCount)
        assertEquals(original.timestamp, copied.timestamp)
    }

    @Test
    fun `StepBucket not equal when timestamps differ`() {
        val a = StepBucket(timestamp = 1000L, stepCount = 10)
        val b = StepBucket(timestamp = 2000L, stepCount = 10)
        assertNotEquals(a, b)
    }

    @Test
    fun `StepBucket not equal when step counts differ`() {
        val a = StepBucket(timestamp = 1000L, stepCount = 10)
        val b = StepBucket(timestamp = 1000L, stepCount = 20)
        assertNotEquals(a, b)
    }

    @Test
    fun `StepBucket accepts zero step count`() {
        val bucket = StepBucket(timestamp = 0L, stepCount = 0)
        assertEquals(0, bucket.stepCount)
    }

    // ─── DailySummary ───────────────────────────────────────────────────────

    @Test
    fun `DailySummary stores all fields correctly`() {
        val summary = DailySummary(
            date = "2026-02-17",
            totalSteps = 8_500,
            totalDistance = 6.2f,
        )
        assertEquals("2026-02-17", summary.date)
        assertEquals(8_500, summary.totalSteps)
        assertEquals(6.2f, summary.totalDistance, 0.0001f)
    }

    @Test
    fun `DailySummary data class equality works`() {
        val a = DailySummary("2026-01-01", 1000, 0.8f)
        val b = DailySummary("2026-01-01", 1000, 0.8f)
        assertEquals(a, b)
    }

    @Test
    fun `DailySummary copy updates only specified field`() {
        val original = DailySummary("2026-02-17", 5000, 3.5f)
        val updated = original.copy(totalSteps = 9999)
        assertEquals(9999, updated.totalSteps)
        assertEquals(original.date, updated.date)
        assertEquals(original.totalDistance, updated.totalDistance, 0.0001f)
    }

    @Test
    fun `DailySummary not equal when dates differ`() {
        val a = DailySummary("2026-02-17", 1000, 0.8f)
        val b = DailySummary("2026-02-18", 1000, 0.8f)
        assertNotEquals(a, b)
    }
}
