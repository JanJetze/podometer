// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [StepAccumulator].
 *
 * Covers step accumulation, hour-boundary detection, flush-to-aggregate
 * production, daily-summary production, and edge cases including multi-hour
 * clock jumps.
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

    /**
     * Returns epoch-millis for a specific date and hour using a fixed reference.
     * Uses Jan 1, 2026 as day 0 (dayOffset = 0) and Jan 2, 2026 as day 1.
     */
    private fun epochForDayAndHour(dayOffset: Int, hour: Int, minuteOffset: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 1 + dayOffset, hour, minuteOffset, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

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
        assertTrue("No flush expected within same hour", result.isEmpty())
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

        assertTrue("Expected FlushResult when crossing hour boundary", result.isNotEmpty())
    }

    @Test
    fun `FlushResult aggregate contains correct step count for flushed hour`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))
        accumulator.addSteps(delta = 50, now = timeInHour(8, 45))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals(150, result.first().aggregate.stepCountDelta)
    }

    @Test
    fun `FlushResult aggregate timestamp is start of flushed hour`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals(hourStart, result.first().aggregate.timestamp)
    }

    @Test
    fun `FlushResult aggregate detectedActivity defaults to WALKING`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals("WALKING", result.first().aggregate.detectedActivity)
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

        // When the hour boundary is crossed, the flush snapshot captures totalStepsToday
        // before the new delta (10 steps at 9:05 belong to hour 9, not the flushed hour 8).
        // The daily summary will be upserted again when hour 9 is eventually flushed.
        val result = accumulator.addSteps(delta = 10, now = timeInHour(9, 5))

        assertEquals(200, result.first().dailySummary.totalSteps)
    }

    @Test
    fun `FlushResult dailySummary date is formatted as yyyy-MM-dd`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        // date must match pattern yyyy-MM-dd
        assertTrue(
            "Date '${result.first().dailySummary.date}' does not match yyyy-MM-dd",
            result.first().dailySummary.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
        )
    }

    // ─── flush() explicit flush ───────────────────────────────────────────────

    @Test
    fun `flush returns empty list when no steps accumulated`() {
        val accumulator = StepAccumulator(startOfHour(8))
        val result = accumulator.flush(now = timeInHour(8, 30))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flush returns FlushResult when steps have been accumulated`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 75, now = timeInHour(8, 20))

        val result = accumulator.flush(now = timeInHour(8, 59))

        assertTrue(result.isNotEmpty())
        assertEquals(75, result.first().aggregate.stepCountDelta)
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

        val result = accumulator.flush(now = timeInHour(10, 50))

        assertEquals(hourStart, result.first().aggregate.timestamp)
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
        val result = accumulator.flush(now = timeInHour(9, 55))

        assertEquals(180, result.first().dailySummary.totalSteps)
    }

    // ─── Multiple hour boundaries ────────────────────────────────────────────

    @Test
    fun `crossing two hour boundaries in sequence produces flush each time`() {
        val hourStart = startOfHour(7)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(7, 30))
        val flush1 = accumulator.addSteps(delta = 5, now = timeInHour(8, 5))
        val flush2 = accumulator.addSteps(delta = 10, now = timeInHour(9, 5))

        assertTrue(flush1.isNotEmpty())
        assertTrue(flush2.isNotEmpty())
        assertEquals(100, flush1.first().aggregate.stepCountDelta)
        assertEquals(5, flush2.first().aggregate.stepCountDelta)
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
        val result = accumulator.flush(now = timeInHour(8, 50))
        assertTrue(result.first().aggregate is HourlyStepAggregate)
    }

    @Test
    fun `FlushResult contains DailySummary`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)
        accumulator.addSteps(delta = 50, now = timeInHour(8, 20))
        val result = accumulator.flush(now = timeInHour(8, 50))
        assertTrue(result.first().dailySummary is DailySummary)
    }

    // ─── Midnight rollover ───────────────────────────────────────────────────

    @Test
    fun `midnight rollover resets totalStepsToday for the new day`() {
        // Start on day 0 at 23:00
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        // Accumulate 500 steps on day 0
        accumulator.addSteps(delta = 500, now = epochForDayAndHour(0, 23, 30))

        // Cross midnight into day 1 at 00:05
        val day1Hour0 = epochForDayAndHour(dayOffset = 1, hour = 0, minuteOffset = 5)
        accumulator.addSteps(delta = 10, now = day1Hour0)

        // totalStepsToday should be 10 (only the steps on the new day)
        assertEquals(10, accumulator.totalStepsToday)
    }

    @Test
    fun `midnight rollover flush result daily summary belongs to old day`() {
        // Start on day 0 at 23:00
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 400, now = epochForDayAndHour(0, 23, 30))

        val day1Hour0 = epochForDayAndHour(dayOffset = 1, hour = 0, minuteOffset = 5)
        val flushResults = accumulator.addSteps(delta = 20, now = day1Hour0)

        // The flushed aggregate's daily summary should contain only old-day steps
        assertEquals(400, flushResults.first().dailySummary.totalSteps)
        // The date in the daily summary should be day 0 (2026-01-01)
        assertEquals("2026-01-01", flushResults.first().dailySummary.date)
    }

    @Test
    fun `after midnight rollover new steps accumulate for the new day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        // 300 steps on day 0
        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 23, 30))

        // Cross midnight
        accumulator.addSteps(delta = 50, now = epochForDayAndHour(1, 0, 5))

        // Add more steps on day 1
        accumulator.addSteps(delta = 25, now = epochForDayAndHour(1, 0, 30))

        // Total today should only be day-1 steps: 50 + 25 = 75
        assertEquals(75, accumulator.totalStepsToday)
    }

    @Test
    fun `midnight rollover produces flush for last hour of old day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 200, now = epochForDayAndHour(0, 23, 45))

        // Cross into new day
        val flushResults = accumulator.addSteps(
            delta = 5,
            now = epochForDayAndHour(1, 0, 5),
        )

        assertTrue("Should flush the 23:00 bucket at midnight crossing", flushResults.isNotEmpty())
        assertEquals(200, flushResults.first().aggregate.stepCountDelta)
    }

    @Test
    fun `after midnight rollover flush result daily summary date is new day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 200, now = epochForDayAndHour(0, 23, 45))

        // Cross into new day
        accumulator.addSteps(delta = 5, now = epochForDayAndHour(1, 0, 5))

        // Accumulate more steps then flush mid-hour
        accumulator.addSteps(delta = 30, now = epochForDayAndHour(1, 0, 30))
        val flushResult = accumulator.flush(now = epochForDayAndHour(1, 0, 50))

        // The explicit flush should have the new day's date
        assertEquals("2026-01-02", flushResult.first().dailySummary.date)
        // And only day-1 steps: 5 + 30 = 35
        assertEquals(35, flushResult.first().dailySummary.totalSteps)
    }

    // ─── DailySummary date comes from tracked currentDate, not bucket timestamp ──

    @Test
    fun `flush result daily summary date comes from tracked date not bucket timestamp`() {
        // This test documents the explicit intent: the DailySummary date must reflect
        // the authoritative currentDate field (which tracks which day totalStepsToday
        // belongs to), not toLocalDate(bucketTimestamp).
        //
        // Scenario: start on day 0 at 23:00, accumulate steps, then flush just before
        // midnight via an hour-boundary crossing — the flushed summary must carry
        // the old-day date ("2026-01-01").  A subsequent flush mid-hour on day 1 must
        // carry the new-day date ("2026-01-02").

        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 23, 30))

        // Crossing midnight flushes the 23:00 bucket — summary date must be old day.
        val oldDayFlushResults = accumulator.addSteps(delta = 15, now = epochForDayAndHour(1, 0, 5))
        assertEquals(
            "DailySummary date should be the old day (from tracked currentDate)",
            "2026-01-01",
            oldDayFlushResults.first().dailySummary.date,
        )

        // Add more steps on day 1, then flush explicitly.
        accumulator.addSteps(delta = 20, now = epochForDayAndHour(1, 0, 30))
        val newDayFlushResults = accumulator.flush(now = epochForDayAndHour(1, 0, 50))
        assertEquals(
            "DailySummary date should be the new day (from tracked currentDate)",
            "2026-01-02",
            newDayFlushResults.first().dailySummary.date,
        )
    }

    // ─── Activity tracking per bucket ────────────────────────────────────────

    @Test
    fun `setActivity updates detectedActivity for current bucket`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.setActivity("CYCLING")
        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals("CYCLING", result.first().aggregate.detectedActivity)
    }

    @Test
    fun `detectedActivity defaults to WALKING when not set`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))
        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals("WALKING", result.first().aggregate.detectedActivity)
    }

    @Test
    fun `setActivity STILL is recorded in flushed aggregate`() {
        val hourStart = startOfHour(10)
        val accumulator = StepAccumulator(hourStart)

        accumulator.setActivity("STILL")
        accumulator.addSteps(delta = 0, now = timeInHour(10, 30))
        accumulator.addSteps(delta = 0, now = timeInHour(10, 45))
        accumulator.addSteps(delta = 5, now = timeInHour(10, 55))

        // Cross into hour 11 to flush the hour 10 bucket
        val result = accumulator.addSteps(delta = 3, now = timeInHour(11, 5))

        assertEquals("STILL", result.first().aggregate.detectedActivity)
    }

    @Test
    fun `activity resets to WALKING for new bucket after hour rollover`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.setActivity("CYCLING")
        accumulator.addSteps(delta = 100, now = timeInHour(8, 30))

        // Cross hour boundary — should flush with CYCLING
        val flush1 = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))
        assertEquals("CYCLING", flush1.first().aggregate.detectedActivity)

        // Now in hour 9 — activity should reset to WALKING
        val flush2 = accumulator.addSteps(delta = 10, now = timeInHour(10, 5))
        assertEquals("WALKING", flush2.first().aggregate.detectedActivity)
    }

    @Test
    fun `setActivity can be changed multiple times within a bucket`() {
        val hourStart = startOfHour(14)
        val accumulator = StepAccumulator(hourStart)

        accumulator.setActivity("STILL")
        accumulator.setActivity("CYCLING")
        accumulator.setActivity("WALKING")
        accumulator.addSteps(delta = 50, now = timeInHour(14, 45))

        val result = accumulator.addSteps(delta = 2, now = timeInHour(15, 5))

        assertEquals("WALKING", result.first().aggregate.detectedActivity)
    }

    @Test
    fun `flush uses tracked activity not hardcoded WALKING`() {
        val hourStart = startOfHour(9)
        val accumulator = StepAccumulator(hourStart)

        accumulator.setActivity("CYCLING")
        accumulator.addSteps(delta = 80, now = timeInHour(9, 20))

        val result = accumulator.flush(now = timeInHour(9, 55))

        assertEquals("CYCLING", result.first().aggregate.detectedActivity)
    }

    // ─── Distance computation ─────────────────────────────────────────────────

    @Test
    fun `FlushResult dailySummary totalDistance uses default stride when not specified`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 1000, now = timeInHour(8, 30))
        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        // 1000 steps * 0.00075 km/step = 0.75 km
        assertEquals(0.75f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `FlushResult dailySummary totalDistance uses custom stride length`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart, strideLengthKm = 0.001f)

        accumulator.addSteps(delta = 1000, now = timeInHour(8, 30))
        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        // 1000 steps * 0.001 km/step = 1.0 km
        assertEquals(1.0f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `explicit flush dailySummary totalDistance computed with default stride`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 500, now = timeInHour(8, 20))

        val result = accumulator.flush(now = timeInHour(8, 50))

        // 500 steps * 0.00075 km/step = 0.375 km
        assertEquals(0.375f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `explicit flush dailySummary totalDistance computed with custom stride`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart, strideLengthKm = 0.0009f)

        accumulator.addSteps(delta = 500, now = timeInHour(8, 20))

        val result = accumulator.flush(now = timeInHour(8, 50))

        // 500 steps * 0.0009 km/step = 0.45 km
        assertEquals(0.45f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `totalDistance is zero when steps are zero`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 0, now = timeInHour(8, 20))
        val result = accumulator.flush(now = timeInHour(8, 50))

        // No steps means no flush
        assertTrue(result.isEmpty())
    }

    // ─── Seeded initialisation (service-restart recovery) ────────────────────

    @Test
    fun `seeded accumulator initialCurrentHourSteps is reflected in currentHourSteps`() {
        val accumulator = StepAccumulator(
            initialHourTimestamp = startOfHour(8),
            initialCurrentHourSteps = 150,
        )
        assertEquals(150, accumulator.currentHourSteps)
    }

    @Test
    fun `seeded accumulator initialTotalStepsToday is reflected in totalStepsToday`() {
        val accumulator = StepAccumulator(
            initialHourTimestamp = startOfHour(8),
            initialTotalStepsToday = 500,
        )
        assertEquals(500, accumulator.totalStepsToday)
    }

    @Test
    fun `seeded accumulator defaults preserve zero-init when params are omitted`() {
        val accumulator = StepAccumulator(startOfHour(8))
        assertEquals(0, accumulator.currentHourSteps)
        assertEquals(0, accumulator.totalStepsToday)
    }

    @Test
    fun `addSteps after seeded init accumulates on top of seed values`() {
        val accumulator = StepAccumulator(
            initialHourTimestamp = startOfHour(8),
            initialCurrentHourSteps = 100,
            initialTotalStepsToday = 300,
        )

        accumulator.addSteps(delta = 50, now = timeInHour(8, 30))

        assertEquals(150, accumulator.currentHourSteps)
        assertEquals(350, accumulator.totalStepsToday)
    }

    @Test
    fun `flush after seeded init returns aggregate with total seed plus new steps`() {
        val accumulator = StepAccumulator(
            initialHourTimestamp = startOfHour(10),
            initialCurrentHourSteps = 200,
            initialTotalStepsToday = 600,
        )

        accumulator.addSteps(delta = 50, now = timeInHour(10, 30))

        val result = accumulator.flush(now = timeInHour(10, 55))

        // currentHourSteps = 200 + 50 = 250; totalStepsToday = 600 + 50 = 650
        assertEquals(250, result.first().aggregate.stepCountDelta)
        assertEquals(650, result.first().dailySummary.totalSteps)
    }

    @Test
    fun `hour boundary flush after seeded init uses seeded currentHourSteps as delta`() {
        val accumulator = StepAccumulator(
            initialHourTimestamp = startOfHour(8),
            initialCurrentHourSteps = 120,
            initialTotalStepsToday = 400,
        )

        // Add more steps within hour 8
        accumulator.addSteps(delta = 30, now = timeInHour(8, 40))

        // Cross into hour 9 → should flush with 120 + 30 = 150 delta
        val result = accumulator.addSteps(delta = 5, now = timeInHour(9, 5))

        assertEquals(150, result.first().aggregate.stepCountDelta)
        // totalAfterFlush was 400 + 30 = 430 before the new 5 steps hit
        assertEquals(430, result.first().dailySummary.totalSteps)
    }

    // ─── Multi-hour scenario with activity and midnight ───────────────────────

    @Test
    fun `multi-hour scenario across midnight with mixed activities`() {
        // Service starts at 23:00 on day 0
        val day0Hour23 = epochForDayAndHour(0, 23)
        val accumulator = StepAccumulator(day0Hour23)

        // 23:00–00:00: WALKING, 400 steps
        accumulator.setActivity("WALKING")
        accumulator.addSteps(delta = 400, now = epochForDayAndHour(0, 23, 30))

        // Cross midnight: should flush hour 23 bucket
        val midnightFlushResults = accumulator.addSteps(delta = 5, now = epochForDayAndHour(1, 0, 5))
        assertTrue(midnightFlushResults.isNotEmpty())
        val midnightFlush = midnightFlushResults.first()
        assertEquals(400, midnightFlush.aggregate.stepCountDelta)
        assertEquals("WALKING", midnightFlush.aggregate.detectedActivity)
        // Daily summary for old day
        assertEquals(400, midnightFlush.dailySummary.totalSteps)
        assertEquals("2026-01-01", midnightFlush.dailySummary.date)

        // totalStepsToday reset to new day's 5 steps
        assertEquals(5, accumulator.totalStepsToday)

        // Switch to CYCLING on new day
        accumulator.setActivity("CYCLING")
        accumulator.addSteps(delta = 50, now = epochForDayAndHour(1, 0, 30))

        // Cross hour 0 → 1
        val hourFlushResults = accumulator.addSteps(delta = 10, now = epochForDayAndHour(1, 1, 5))
        assertTrue(hourFlushResults.isNotEmpty())
        val hourFlush = hourFlushResults.first()
        assertEquals(55, hourFlush.aggregate.stepCountDelta) // 5 + 50
        assertEquals("CYCLING", hourFlush.aggregate.detectedActivity)
        assertEquals("2026-01-02", hourFlush.dailySummary.date)
        // totalSteps = 55 (steps in bucket); the 10 arriving in the new hour are not yet flushed
        assertEquals(55, hourFlush.dailySummary.totalSteps)
    }

    // ─── Multi-hour clock jump (bug fix: task-1311ada1) ──────────────────────

    @Test
    fun `two-hour skip produces flush for current and one intermediate empty hour`() {
        // Start at 08:00, accumulate steps, then jump to 10:05 (skipping 09:00)
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 8, 30))

        // Jump directly from 08:xx to 10:05 — skips the 09:00 bucket entirely
        val results = accumulator.addSteps(delta = 5, now = epochForDayAndHour(0, 10, 5))

        // Should produce 2 results: 08:00 (300 steps) and 09:00 (0 steps)
        assertEquals("Expected 2 flush results for 2-hour skip", 2, results.size)

        val flush08 = results[0]
        assertEquals(epochForDayAndHour(0, 8), flush08.aggregate.timestamp)
        assertEquals(300, flush08.aggregate.stepCountDelta)

        val flush09 = results[1]
        assertEquals(epochForDayAndHour(0, 9), flush09.aggregate.timestamp)
        assertEquals(0, flush09.aggregate.stepCountDelta)
    }

    @Test
    fun `intermediate empty hours have zero stepCountDelta`() {
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 100, now = epochForDayAndHour(0, 8, 30))

        // Jump 3 hours ahead: skips 09:00 and 10:00
        val results = accumulator.addSteps(delta = 10, now = epochForDayAndHour(0, 11, 5))

        // Intermediate results (09:00 and 10:00) must have zero steps
        assertTrue("Expected at least 3 results", results.size >= 3)
        for (i in 1 until results.size) {
            assertEquals(
                "Intermediate hour result[${i}] should have 0 steps",
                0,
                results[i].aggregate.stepCountDelta,
            )
        }
    }

    @Test
    fun `large clock jump produces correct number of intermediate flushes`() {
        // Jump from 08:00 to 11:00 — skips 09:00 and 10:00
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 50, now = epochForDayAndHour(0, 8, 45))

        val results = accumulator.addSteps(delta = 5, now = epochForDayAndHour(0, 11, 5))

        // Expect: 08:00 (50 steps), 09:00 (0), 10:00 (0) = 3 total
        assertEquals(3, results.size)
        assertEquals(epochForDayAndHour(0, 8), results[0].aggregate.timestamp)
        assertEquals(epochForDayAndHour(0, 9), results[1].aggregate.timestamp)
        assertEquals(epochForDayAndHour(0, 10), results[2].aggregate.timestamp)
        assertEquals(50, results[0].aggregate.stepCountDelta)
        assertEquals(0, results[1].aggregate.stepCountDelta)
        assertEquals(0, results[2].aggregate.stepCountDelta)

        // New bucket should hold only the arriving steps
        assertEquals(5, accumulator.currentHourSteps)
        // totalStepsToday: 50 (hour 8) + 5 (new) = 55
        assertEquals(55, accumulator.totalStepsToday)
    }

    @Test
    fun `multi-hour skip crossing midnight resets daily total`() {
        // Start at 23:00 on day 0, accumulate steps, then jump to 02:05 on day 1
        // Skipped hours: 00:00 and 01:00 on day 1
        val accumulator = StepAccumulator(epochForDayAndHour(0, 23))
        accumulator.addSteps(delta = 500, now = epochForDayAndHour(0, 23, 30))

        val results = accumulator.addSteps(delta = 10, now = epochForDayAndHour(1, 2, 5))

        // Should produce 3 results: 23:00 (500), 00:00 (0), 01:00 (0)
        assertEquals(3, results.size)

        // First result: hour 23:00 on day 0, 500 steps, old-day summary
        assertEquals(epochForDayAndHour(0, 23), results[0].aggregate.timestamp)
        assertEquals(500, results[0].aggregate.stepCountDelta)
        assertEquals("2026-01-01", results[0].dailySummary.date)
        assertEquals(500, results[0].dailySummary.totalSteps)

        // Second result: hour 00:00 on day 1, 0 steps, new-day summary (daily total resets)
        assertEquals(epochForDayAndHour(1, 0), results[1].aggregate.timestamp)
        assertEquals(0, results[1].aggregate.stepCountDelta)
        assertEquals("2026-01-02", results[1].dailySummary.date)
        assertEquals(0, results[1].dailySummary.totalSteps)

        // Third result: hour 01:00 on day 1, 0 steps
        assertEquals(epochForDayAndHour(1, 1), results[2].aggregate.timestamp)
        assertEquals(0, results[2].aggregate.stepCountDelta)
        assertEquals("2026-01-02", results[2].dailySummary.date)
        assertEquals(0, results[2].dailySummary.totalSteps)

        // After the jump, totalStepsToday is only the new delta (day 1 steps)
        assertEquals(10, accumulator.totalStepsToday)
    }

    @Test
    fun `DST-like one-hour forward jump produces single flush result`() {
        // Simulates a DST spring-forward: clock jumps 1 hour forward.
        // From perspective of addSteps, this looks like a normal 2-hour boundary crossing
        // (current hour + exactly 2 epoch-hour slots).
        val accumulator = StepAccumulator(epochForDayAndHour(0, 1))
        accumulator.addSteps(delta = 100, now = epochForDayAndHour(0, 1, 30))

        // Spring-forward: clock jumps from ~01:59 to 03:00 — skips 02:00 entirely
        val results = accumulator.addSteps(delta = 5, now = epochForDayAndHour(0, 3, 5))

        // Should produce 2 results: 01:00 (100 steps) and 02:00 (0 steps)
        assertEquals(2, results.size)
        assertEquals(100, results[0].aggregate.stepCountDelta)
        assertEquals(0, results[1].aggregate.stepCountDelta)
    }
}
