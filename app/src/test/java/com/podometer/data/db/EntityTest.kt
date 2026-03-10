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

    // ─── HourlyStepAggregate ────────────────────────────────────────────────

    @Test
    fun `HourlyStepAggregate default id is 0`() {
        val aggregate = HourlyStepAggregate(
            timestamp = 1_700_000_000_000L,
            stepCountDelta = 42,
            detectedActivity = "WALKING",
        )
        assertEquals(0, aggregate.id)
    }

    @Test
    fun `HourlyStepAggregate stores all fields correctly`() {
        val aggregate = HourlyStepAggregate(
            id = 7,
            timestamp = 1_700_000_000_000L,
            stepCountDelta = 100,
            detectedActivity = "CYCLING",
        )
        assertEquals(7, aggregate.id)
        assertEquals(1_700_000_000_000L, aggregate.timestamp)
        assertEquals(100, aggregate.stepCountDelta)
        assertEquals("CYCLING", aggregate.detectedActivity)
    }

    @Test
    fun `HourlyStepAggregate data class equality works`() {
        val a = HourlyStepAggregate(id = 1, timestamp = 1000L, stepCountDelta = 10, detectedActivity = "STILL")
        val b = HourlyStepAggregate(id = 1, timestamp = 1000L, stepCountDelta = 10, detectedActivity = "STILL")
        assertEquals(a, b)
    }

    @Test
    fun `HourlyStepAggregate copy changes only specified field`() {
        val original = HourlyStepAggregate(id = 1, timestamp = 1000L, stepCountDelta = 5, detectedActivity = "WALKING")
        val copied = original.copy(stepCountDelta = 99)
        assertEquals(99, copied.stepCountDelta)
        assertEquals(original.timestamp, copied.timestamp)
        assertEquals(original.detectedActivity, copied.detectedActivity)
    }

    @Test
    fun `HourlyStepAggregate accepts all three activity strings`() {
        listOf("WALKING", "CYCLING", "STILL").forEach { activity ->
            val agg = HourlyStepAggregate(timestamp = 0L, stepCountDelta = 0, detectedActivity = activity)
            assertEquals(activity, agg.detectedActivity)
        }
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
