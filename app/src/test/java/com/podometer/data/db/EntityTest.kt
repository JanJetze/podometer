// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    // ─── ActivityTransition ─────────────────────────────────────────────────

    @Test
    fun `ActivityTransition default id is 0 and isManualOverride is false`() {
        val transition = ActivityTransition(
            timestamp = 1_700_000_000_000L,
            fromActivity = "STILL",
            toActivity = "WALKING",
        )
        assertEquals(0, transition.id)
        assertFalse(transition.isManualOverride)
    }

    @Test
    fun `ActivityTransition stores all fields correctly`() {
        val transition = ActivityTransition(
            id = 3,
            timestamp = 2_000_000_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = true,
        )
        assertEquals(3, transition.id)
        assertEquals(2_000_000_000L, transition.timestamp)
        assertEquals("WALKING", transition.fromActivity)
        assertEquals("CYCLING", transition.toActivity)
        assertEquals(true, transition.isManualOverride)
    }

    @Test
    fun `ActivityTransition data class equality works`() {
        val a = ActivityTransition(id = 1, timestamp = 500L, fromActivity = "STILL", toActivity = "WALKING")
        val b = ActivityTransition(id = 1, timestamp = 500L, fromActivity = "STILL", toActivity = "WALKING")
        assertEquals(a, b)
    }

    @Test
    fun `ActivityTransition copy preserves defaults when not overridden`() {
        val t = ActivityTransition(timestamp = 100L, fromActivity = "STILL", toActivity = "WALKING")
        val copied = t.copy(fromActivity = "CYCLING")
        assertFalse(copied.isManualOverride)
        assertEquals("CYCLING", copied.fromActivity)
        assertEquals("WALKING", copied.toActivity)
    }

    // ─── DailySummary ───────────────────────────────────────────────────────

    @Test
    fun `DailySummary stores all fields correctly`() {
        val summary = DailySummary(
            date = "2026-02-17",
            totalSteps = 8_500,
            totalDistance = 6.2f,
            walkingMinutes = 70,
            cyclingMinutes = 20,
        )
        assertEquals("2026-02-17", summary.date)
        assertEquals(8_500, summary.totalSteps)
        assertEquals(6.2f, summary.totalDistance, 0.0001f)
        assertEquals(70, summary.walkingMinutes)
        assertEquals(20, summary.cyclingMinutes)
    }

    @Test
    fun `DailySummary data class equality works`() {
        val a = DailySummary("2026-01-01", 1000, 0.8f, 15, 0)
        val b = DailySummary("2026-01-01", 1000, 0.8f, 15, 0)
        assertEquals(a, b)
    }

    @Test
    fun `DailySummary copy updates only specified field`() {
        val original = DailySummary("2026-02-17", 5000, 3.5f, 45, 10)
        val updated = original.copy(totalSteps = 9999)
        assertEquals(9999, updated.totalSteps)
        assertEquals(original.date, updated.date)
        assertEquals(original.totalDistance, updated.totalDistance, 0.0001f)
    }

    @Test
    fun `DailySummary not equal when dates differ`() {
        val a = DailySummary("2026-02-17", 1000, 0.8f, 15, 0)
        val b = DailySummary("2026-02-18", 1000, 0.8f, 15, 0)
        assertNotEquals(a, b)
    }

    // ─── CyclingSession ─────────────────────────────────────────────────────

    @Test
    fun `CyclingSession default id is 0, endTime is null, isManualOverride is false`() {
        val session = CyclingSession(
            startTime = 1_700_000_000_000L,
            durationMinutes = 0,
        )
        assertEquals(0, session.id)
        assertNull(session.endTime)
        assertFalse(session.isManualOverride)
    }

    @Test
    fun `CyclingSession stores completed session correctly`() {
        val session = CyclingSession(
            id = 5,
            startTime = 1_700_000_000_000L,
            endTime = 1_700_003_600_000L,
            durationMinutes = 60,
            isManualOverride = false,
        )
        assertEquals(5, session.id)
        assertEquals(1_700_000_000_000L, session.startTime)
        assertEquals(1_700_003_600_000L, session.endTime)
        assertEquals(60, session.durationMinutes)
        assertFalse(session.isManualOverride)
    }

    @Test
    fun `CyclingSession with null endTime represents ongoing session`() {
        val session = CyclingSession(startTime = 1_700_000_000_000L, endTime = null, durationMinutes = 0)
        assertNull(session.endTime)
    }

    @Test
    fun `CyclingSession isManualOverride can be set to true`() {
        val session = CyclingSession(
            startTime = 1_700_000_000_000L,
            durationMinutes = 30,
            isManualOverride = true,
        )
        assertEquals(true, session.isManualOverride)
    }

    @Test
    fun `CyclingSession data class equality works`() {
        val a = CyclingSession(id = 1, startTime = 100L, endTime = 200L, durationMinutes = 1)
        val b = CyclingSession(id = 1, startTime = 100L, endTime = 200L, durationMinutes = 1)
        assertEquals(a, b)
    }

    @Test
    fun `CyclingSession copy can mark session complete`() {
        val ongoing = CyclingSession(startTime = 1_700_000_000_000L, durationMinutes = 0)
        val completed = ongoing.copy(endTime = 1_700_001_800_000L, durationMinutes = 30)
        assertEquals(1_700_001_800_000L, completed.endTime)
        assertEquals(30, completed.durationMinutes)
        assertEquals(ongoing.startTime, completed.startTime)
    }

    // ─── SensorWindow ────────────────────────────────────────────────────────

    @Test
    fun `SensorWindow default id is 0`() {
        val window = SensorWindow(
            timestamp = 1_700_000_000_000L,
            magnitudeVariance = 2.5,
            stepFrequencyHz = 1.8,
            stepCount = 9,
        )
        assertEquals(0L, window.id)
    }

    @Test
    fun `SensorWindow stores all fields correctly`() {
        val window = SensorWindow(
            id = 42,
            timestamp = 1_700_000_000_000L,
            magnitudeVariance = 3.14,
            stepFrequencyHz = 0.5,
            stepCount = 3,
        )
        assertEquals(42L, window.id)
        assertEquals(1_700_000_000_000L, window.timestamp)
        assertEquals(3.14, window.magnitudeVariance, 0.001)
        assertEquals(0.5, window.stepFrequencyHz, 0.001)
        assertEquals(3, window.stepCount)
    }

    @Test
    fun `SensorWindow data class equality works`() {
        val a = SensorWindow(id = 1, timestamp = 1000L, magnitudeVariance = 1.0, stepFrequencyHz = 0.0, stepCount = 0)
        val b = SensorWindow(id = 1, timestamp = 1000L, magnitudeVariance = 1.0, stepFrequencyHz = 0.0, stepCount = 0)
        assertEquals(a, b)
    }

    // ─── sumStepsInRange ─────────────────────────────────────────────────────

    private fun window(timestamp: Long, stepCount: Int) = SensorWindow(
        timestamp = timestamp,
        magnitudeVariance = 0.0,
        stepFrequencyHz = 0.0,
        stepCount = stepCount,
    )

    @Test
    fun `sumStepsInRange sums windows within range`() {
        val windows = listOf(window(100, 10), window(200, 20), window(300, 30))
        assertEquals(60, windows.sumStepsInRange(100, 400))
    }

    @Test
    fun `sumStepsInRange includes start boundary and excludes end boundary`() {
        val windows = listOf(window(100, 10), window(200, 20), window(300, 30))
        assertEquals(10, windows.sumStepsInRange(100, 200)) // only 100 included
        assertEquals(20, windows.sumStepsInRange(200, 300)) // only 200 included
    }

    @Test
    fun `sumStepsInRange returns zero for empty list`() {
        assertEquals(0, emptyList<SensorWindow>().sumStepsInRange(0, 1000))
    }

    @Test
    fun `sumStepsInRange returns zero when no windows in range`() {
        val windows = listOf(window(100, 10), window(200, 20))
        assertEquals(0, windows.sumStepsInRange(300, 400))
    }
}
