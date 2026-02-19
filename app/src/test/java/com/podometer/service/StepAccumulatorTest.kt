// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [StepAccumulator].
 *
 * Covers step accumulation, hour-boundary detection, flush-to-aggregate
 * production, daily-summary production, and edge cases.
 */
class StepAccumulatorTest {

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Returns epoch-millis at the start of the given [hour] of today (local time).
     */
    private fun startOfHour(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns epoch-millis at [minuteOffset] minutes into [hour] today (local time).
     */
    private fun timeInHour(hour: Int, minuteOffset: Int = 0): Long =
        startOfHour(hour) + minuteOffset * 60_000L

    // ─── Initial state ───────────────────────────────────────────────────────

    @Test
    fun `initial accumulated steps is zero`() {
        val accumulator = StepAccumulator(startOfHour(8))
        assertEquals(0, accumulator.currentHourSteps)
    }

    @Test
    fun `initial total steps is zero`() {
        val accumulator = StepAccumulator(startOfHour(8))
        assertEquals(0, accumulator.totalStepsToday)
    }

    // ─── addSteps within same hour ────────────────────────────────────────────

    @Test
    fun `addSteps increments currentHourSteps`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 30))

        assertEquals(5, accumulator.currentHourSteps)
        assertNull("No flush expected within same hour", result)
    }

    @Test
    fun `addSteps accumulates multiple deltas within same hour`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 10, now = timeInHour(8, 10))
        accumulator.addSteps(delta = 20, now = timeInHour(8, 20))
        accumulator.addSteps(delta = 5, now = timeInHour(8, 50))

        assertEquals(35, accumulator.currentHourSteps)
    }

    @Test
    fun `addSteps increments totalStepsToday`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 10, now = timeInHour(8, 10))
        accumulator.addSteps(delta = 15, now = timeInHour(8, 20))

        assertEquals(25, accumulator.totalStepsToday)
    }

    // ─── addSteps at hour boundary — produces FlushResult ────────────────────

    @Test
    fun `addSteps returns FlushResult when hour rolls over`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        // Accumulate steps during hour 8
        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        // Cross into hour 9 → should flush
        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertNotNull("Expected FlushResult when crossing hour boundary", result)
    }

    @Test
    fun `FlushResult aggregate contains correct step count for flushed hour`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))
        accumulator.addSteps(delta = 50, now = timeInHour(8, 45))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))!!

        assertEquals(150, result.aggregate.stepCountDelta)
    }

    @Test
    fun `FlushResult aggregate timestamp is start of flushed hour`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))!!

        assertEquals(hourStart, result.aggregate.timestamp)
    }

    @Test
    fun `FlushResult aggregate detectedActivity defaults to WALKING`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))!!

        assertEquals("WALKING", result.aggregate.detectedActivity)
    }

    @Test
    fun `after flush currentHourSteps resets to delta of new event`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))
        accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        // After crossing to hour 9, new hour should contain only the 5 steps from the new event
        assertEquals(5, accumulator.currentHourSteps)
    }

    @Test
    fun `after flush totalStepsToday continues accumulating`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))
        accumulator.addSteps(delta = 5, now = timeInHour(9, 5))
        accumulator.addSteps(delta = 20, now = timeInHour(9, 30))

        assertEquals(125, accumulator.totalStepsToday)
    }

    // ─── FlushResult daily summary ────────────────────────────────────────────

    @Test
    fun `FlushResult dailySummary totalSteps equals accumulated total`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 200, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 10, now = timeInHour(9, 5))!!

        assertEquals(210, result.dailySummary.totalSteps)
    }

    @Test
    fun `FlushResult dailySummary date is formatted as yyyy-MM-dd`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))!!

        // date must match pattern yyyy-MM-dd
        assertTrue(
            "Date '${result.dailySummary.date}' does not match yyyy-MM-dd",
            result.dailySummary.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
        )
    }

    // ─── flush() explicit flush ───────────────────────────────────────────────

    @Test
    fun `flush returns null when no steps accumulated`() {
        val accumulator = StepAccumulator(startOfHour(8))
        val result = accumulator.flush(now = timeInHour(8, 30))
        assertNull(result)
    }

    @Test
    fun `flush returns FlushResult when steps have been accumulated`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 75, now = timeInHour(8, 20))

        val result = accumulator.flush(now = timeInHour(8, 59))

        assertNotNull(result)
        assertEquals(75, result!!.aggregate.stepCountDelta)
    }

    @Test
    fun `flush resets currentHourSteps to zero`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 75, now = timeInHour(8, 20))
        accumulator.flush(now = timeInHour(8, 59))

        assertEquals(0, accumulator.currentHourSteps)
    }

    @Test
    fun `flush aggregate timestamp equals current hour start`() {
        val hourStart = startOfHour(10)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 50, now = timeInHour(10, 30))

        val result = accumulator.flush(now = timeInHour(10, 50))!!

        assertEquals(hourStart, result.aggregate.timestamp)
    }

    @Test
    fun `flush dailySummary totalSteps includes all accumulated today`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        // Steps in hour 8 then flush at hour boundary
        accumulator.addSteps(delta = 100, now = timeInHour(8, 20))
        accumulator.addSteps(delta = 50, now = timeInHour(9, 5)) // triggers flush of hour 8

        // Steps in hour 9
        accumulator.addSteps(delta = 30, now = timeInHour(9, 40))

        // Explicit flush mid-hour 9
        val result = accumulator.flush(now = timeInHour(9, 55))!!

        assertEquals(180, result.dailySummary.totalSteps)
    }

    // ─── Multiple hour boundaries ────────────────────────────────────────────

    @Test
    fun `crossing two hour boundaries in sequence produces flush each time`() {
        val hourStart = startOfHour(7)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(7, 30))
        val flush1 = accumulator.addSteps(delta = 5, now = timeInHour(8, 5))
        val flush2 = accumulator.addSteps(delta = 10, now = timeInHour(9, 5))

        assertNotNull(flush1)
        assertNotNull(flush2)
        assertEquals(100, flush1!!.aggregate.stepCountDelta)
        assertEquals(5, flush2!!.aggregate.stepCountDelta)
    }

    // ─── Zero-delta edge case ────────────────────────────────────────────────

    @Test
    fun `addSteps with zero delta does not change accumulated count`() {
        val accumulator = StepAccumulator(startOfHour(8))

        accumulator.addSteps(delta = 10, now = timeInHour(8, 10))
        accumulator.addSteps(delta = 0, now = timeInHour(8, 20))

        assertEquals(10, accumulator.currentHourSteps)
    }

    // ─── Class and package existence ─────────────────────────────────────────

    @Test
    fun `StepAccumulator class exists in service package`() {
        val clazz = StepAccumulator::class.java
        assertEquals("com.podometer.service", clazz.packageName)
    }

    @Test
    fun `FlushResult class exists in service package`() {
        val clazz = FlushResult::class.java
        assertEquals("com.podometer.service", clazz.packageName)
    }

    @Test
    fun `FlushResult contains HourlyStepAggregate`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)
        accumulator.addSteps(delta = 50, now = timeInHour(8, 20))
        val result = accumulator.flush(now = timeInHour(8, 50))!!
        assertTrue(result.aggregate is HourlyStepAggregate)
    }

    @Test
    fun `FlushResult contains DailySummary`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)
        accumulator.addSteps(delta = 50, now = timeInHour(8, 20))
        val result = accumulator.flush(now = timeInHour(8, 50))!!
        assertTrue(result.dailySummary is DailySummary)
    }
}
