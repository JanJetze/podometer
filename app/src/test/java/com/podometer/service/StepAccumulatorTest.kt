// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [StepAccumulator].
 *
 * Covers step accumulation, 5-minute-boundary detection, flush-to-bucket
 * production, daily-summary production, and edge cases including multi-bucket
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
        assertEquals(0, accumulator.currentBucketSteps)
    }

    @Test
    fun `initial total steps is zero`() {
        val accumulator = StepAccumulator(startOfHour(8))
        assertEquals(0, accumulator.totalStepsToday)
    }

    // ─── addSteps within same bucket ──────────────────────────────────────────

    @Test
    fun `addSteps increments currentBucketSteps`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 2))

        assertEquals(5, accumulator.currentBucketSteps)
        assertTrue("No flush expected within same bucket", result.isEmpty())
    }

    @Test
    fun `addSteps accumulates multiple deltas within same bucket`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 10, now = timeInHour(8, 1))
        accumulator.addSteps(delta = 20, now = timeInHour(8, 3))
        accumulator.addSteps(delta = 5, now = timeInHour(8, 4))

        assertEquals(35, accumulator.currentBucketSteps)
    }

    @Test
    fun `addSteps increments totalStepsToday`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 10, now = timeInHour(8, 1))
        accumulator.addSteps(delta = 15, now = timeInHour(8, 3))

        assertEquals(25, accumulator.totalStepsToday)
    }

    // ─── addSteps at bucket boundary — produces FlushResult ──────────────────

    @Test
    fun `addSteps returns FlushResult when bucket rolls over`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        // Accumulate steps during first bucket (08:00–08:05)
        accumulator.addSteps(delta = 100, now = timeInHour(8, 3))

        // Cross into next bucket (08:05 or later) → should flush
        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        assertTrue("Expected FlushResult when crossing bucket boundary", result.isNotEmpty())
    }

    @Test
    fun `FlushResult bucket contains correct step count for flushed bucket`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 2))
        accumulator.addSteps(delta = 50, now = timeInHour(8, 4))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        assertEquals(150, result.first().bucket.stepCount)
    }

    @Test
    fun `FlushResult bucket timestamp is start of flushed bucket`() {
        // 08:00 bucket start
        val bucketStart = startOfHour(8)
        val accumulator = StepAccumulator(bucketStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 3))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        assertEquals(bucketStart, result.first().bucket.timestamp)
    }

    @Test
    fun `after flush currentBucketSteps resets to delta of new event`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 3))
        accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        // After crossing to next bucket, new bucket should contain only the 5 steps from the new event
        assertEquals(5, accumulator.currentBucketSteps)
    }

    @Test
    fun `after flush totalStepsToday continues accumulating`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 3))
        accumulator.addSteps(delta = 5, now = timeInHour(8, 6))
        accumulator.addSteps(delta = 20, now = timeInHour(8, 8))

        assertEquals(125, accumulator.totalStepsToday)
    }

    // ─── FlushResult daily summary ────────────────────────────────────────────

    @Test
    fun `FlushResult dailySummary totalSteps equals accumulated total at flush time`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 200, now = timeInHour(8, 3))

        // When the bucket boundary is crossed, the flush snapshot captures totalStepsToday
        // before the new delta.
        val result = accumulator.addSteps(delta = 10, now = timeInHour(8, 6))

        assertEquals(200, result.first().dailySummary.totalSteps)
    }

    @Test
    fun `FlushResult dailySummary date is formatted as yyyy-MM-dd`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(8, 3))

        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

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
        val result = accumulator.flush(now = timeInHour(8, 3))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flush returns FlushResult when steps have been accumulated`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 75, now = timeInHour(8, 2))

        val result = accumulator.flush(now = timeInHour(8, 4))

        assertTrue(result.isNotEmpty())
        assertEquals(75, result.first().bucket.stepCount)
    }

    @Test
    fun `flush resets currentBucketSteps to zero`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 75, now = timeInHour(8, 2))
        accumulator.flush(now = timeInHour(8, 4))

        assertEquals(0, accumulator.currentBucketSteps)
    }

    @Test
    fun `flush bucket timestamp equals current bucket start`() {
        val hourStart = startOfHour(10)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 50, now = timeInHour(10, 2))

        val result = accumulator.flush(now = timeInHour(10, 4))

        assertEquals(hourStart, result.first().bucket.timestamp)
    }

    @Test
    fun `flush dailySummary totalSteps includes all accumulated today`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        // Steps in first bucket then flush at bucket boundary
        accumulator.addSteps(delta = 100, now = timeInHour(8, 2))
        accumulator.addSteps(delta = 50, now = timeInHour(8, 6)) // triggers flush of 08:00 bucket

        // Steps in second bucket
        accumulator.addSteps(delta = 30, now = timeInHour(8, 8))

        // Explicit flush mid-bucket
        val result = accumulator.flush(now = timeInHour(8, 9))

        assertEquals(180, result.first().dailySummary.totalSteps)
    }

    // ─── Multiple bucket boundaries ──────────────────────────────────────────

    @Test
    fun `crossing two bucket boundaries in sequence produces flush each time`() {
        val hourStart = startOfHour(7)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 100, now = timeInHour(7, 2))
        val flush1 = accumulator.addSteps(delta = 5, now = timeInHour(7, 6))
        val flush2 = accumulator.addSteps(delta = 10, now = timeInHour(7, 11))

        assertTrue(flush1.isNotEmpty())
        assertTrue(flush2.isNotEmpty())
        assertEquals(100, flush1.first().bucket.stepCount)
        assertEquals(5, flush2.first().bucket.stepCount)
    }

    // ─── Zero-delta edge case ────────────────────────────────────────────────

    @Test
    fun `addSteps with zero delta does not change accumulated count`() {
        val accumulator = StepAccumulator(startOfHour(8))

        accumulator.addSteps(delta = 10, now = timeInHour(8, 1))
        accumulator.addSteps(delta = 0, now = timeInHour(8, 3))

        assertEquals(10, accumulator.currentBucketSteps)
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
    fun `FlushResult contains StepBucket`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)
        accumulator.addSteps(delta = 50, now = timeInHour(8, 2))
        val result = accumulator.flush(now = timeInHour(8, 4))
        assertTrue(result.first().bucket is StepBucket)
    }

    @Test
    fun `FlushResult contains DailySummary`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)
        accumulator.addSteps(delta = 50, now = timeInHour(8, 2))
        val result = accumulator.flush(now = timeInHour(8, 4))
        assertTrue(result.first().dailySummary is DailySummary)
    }

    // ─── Midnight rollover ───────────────────────────────────────────────────

    @Test
    fun `midnight rollover resets totalStepsToday for the new day`() {
        // Start on day 0 at 23:00
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        // Accumulate 500 steps on day 0
        accumulator.addSteps(delta = 500, now = epochForDayAndHour(0, 23, 2))

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

        accumulator.addSteps(delta = 400, now = epochForDayAndHour(0, 23, 2))

        val day1Hour0 = epochForDayAndHour(dayOffset = 1, hour = 0, minuteOffset = 5)
        val flushResults = accumulator.addSteps(delta = 20, now = day1Hour0)

        // The flushed bucket's daily summary should contain only old-day steps
        assertEquals(400, flushResults.first().dailySummary.totalSteps)
        // The date in the daily summary should be day 0 (2026-01-01)
        assertEquals("2026-01-01", flushResults.first().dailySummary.date)
    }

    @Test
    fun `after midnight rollover new steps accumulate for the new day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        // 300 steps on day 0
        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 23, 2))

        // Cross midnight
        accumulator.addSteps(delta = 50, now = epochForDayAndHour(1, 0, 5))

        // Add more steps on day 1
        accumulator.addSteps(delta = 25, now = epochForDayAndHour(1, 0, 7))

        // Total today should only be day-1 steps: 50 + 25 = 75
        assertEquals(75, accumulator.totalStepsToday)
    }

    @Test
    fun `midnight rollover produces flush for last bucket of old day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 200, now = epochForDayAndHour(0, 23, 2))

        // Cross into new day
        val flushResults = accumulator.addSteps(
            delta = 5,
            now = epochForDayAndHour(1, 0, 5),
        )

        assertTrue("Should flush the 23:00 bucket at midnight crossing", flushResults.isNotEmpty())
        assertEquals(200, flushResults.first().bucket.stepCount)
    }

    @Test
    fun `after midnight rollover flush result daily summary date is new day`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 200, now = epochForDayAndHour(0, 23, 2))

        // Cross into new day
        accumulator.addSteps(delta = 5, now = epochForDayAndHour(1, 0, 5))

        // Accumulate more steps then flush mid-bucket
        accumulator.addSteps(delta = 30, now = epochForDayAndHour(1, 0, 7))
        val flushResult = accumulator.flush(now = epochForDayAndHour(1, 0, 8))

        // The explicit flush should have the new day's date
        assertEquals("2026-01-02", flushResult.first().dailySummary.date)
        // And only day-1 steps: 5 + 30 = 35
        assertEquals(35, flushResult.first().dailySummary.totalSteps)
    }

    // ─── DailySummary date comes from tracked currentDate, not bucket timestamp ──

    @Test
    fun `flush result daily summary date comes from tracked date not bucket timestamp`() {
        val day0Hour23 = epochForDayAndHour(dayOffset = 0, hour = 23)
        val accumulator = StepAccumulator(day0Hour23)

        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 23, 2))

        // Crossing midnight flushes the 23:00 bucket — summary date must be old day.
        val oldDayFlushResults = accumulator.addSteps(delta = 15, now = epochForDayAndHour(1, 0, 5))
        assertEquals(
            "DailySummary date should be the old day (from tracked currentDate)",
            "2026-01-01",
            oldDayFlushResults.first().dailySummary.date,
        )

        // Add more steps on day 1, then flush explicitly.
        accumulator.addSteps(delta = 20, now = epochForDayAndHour(1, 0, 7))
        val newDayFlushResults = accumulator.flush(now = epochForDayAndHour(1, 0, 8))
        assertEquals(
            "DailySummary date should be the new day (from tracked currentDate)",
            "2026-01-02",
            newDayFlushResults.first().dailySummary.date,
        )
    }

    // ─── Distance computation ─────────────────────────────────────────────────

    @Test
    fun `FlushResult dailySummary totalDistance uses default stride when not specified`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 1000, now = timeInHour(8, 2))
        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        // 1000 steps * 0.00075 km/step = 0.75 km
        assertEquals(0.75f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `FlushResult dailySummary totalDistance uses custom stride length`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart, strideLengthKm = 0.001f)

        accumulator.addSteps(delta = 1000, now = timeInHour(8, 2))
        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        // 1000 steps * 0.001 km/step = 1.0 km
        assertEquals(1.0f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `explicit flush dailySummary totalDistance computed with default stride`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 500, now = timeInHour(8, 2))

        val result = accumulator.flush(now = timeInHour(8, 4))

        // 500 steps * 0.00075 km/step = 0.375 km
        assertEquals(0.375f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `explicit flush dailySummary totalDistance computed with custom stride`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart, strideLengthKm = 0.0009f)

        accumulator.addSteps(delta = 500, now = timeInHour(8, 2))

        val result = accumulator.flush(now = timeInHour(8, 4))

        // 500 steps * 0.0009 km/step = 0.45 km
        assertEquals(0.45f, result.first().dailySummary.totalDistance, 0.0001f)
    }

    @Test
    fun `totalDistance is zero when steps are zero`() {
        val hourStart = startOfHour(8)
        val accumulator = StepAccumulator(hourStart)

        accumulator.addSteps(delta = 0, now = timeInHour(8, 2))
        val result = accumulator.flush(now = timeInHour(8, 4))

        // No steps means no flush
        assertTrue(result.isEmpty())
    }

    // ─── Seeded initialisation (service-restart recovery) ────────────────────

    @Test
    fun `seeded accumulator initialCurrentBucketSteps is reflected in currentBucketSteps`() {
        val accumulator = StepAccumulator(
            initialBucketTimestamp = startOfHour(8),
            initialCurrentBucketSteps = 150,
        )
        assertEquals(150, accumulator.currentBucketSteps)
    }

    @Test
    fun `seeded accumulator initialTotalStepsToday is reflected in totalStepsToday`() {
        val accumulator = StepAccumulator(
            initialBucketTimestamp = startOfHour(8),
            initialTotalStepsToday = 500,
        )
        assertEquals(500, accumulator.totalStepsToday)
    }

    @Test
    fun `seeded accumulator defaults preserve zero-init when params are omitted`() {
        val accumulator = StepAccumulator(startOfHour(8))
        assertEquals(0, accumulator.currentBucketSteps)
        assertEquals(0, accumulator.totalStepsToday)
    }

    @Test
    fun `addSteps after seeded init accumulates on top of seed values`() {
        val accumulator = StepAccumulator(
            initialBucketTimestamp = startOfHour(8),
            initialCurrentBucketSteps = 100,
            initialTotalStepsToday = 300,
        )

        accumulator.addSteps(delta = 50, now = timeInHour(8, 2))

        assertEquals(150, accumulator.currentBucketSteps)
        assertEquals(350, accumulator.totalStepsToday)
    }

    @Test
    fun `flush after seeded init returns bucket with total seed plus new steps`() {
        val accumulator = StepAccumulator(
            initialBucketTimestamp = startOfHour(10),
            initialCurrentBucketSteps = 200,
            initialTotalStepsToday = 600,
        )

        accumulator.addSteps(delta = 50, now = timeInHour(10, 2))

        val result = accumulator.flush(now = timeInHour(10, 4))

        // currentBucketSteps = 200 + 50 = 250; totalStepsToday = 600 + 50 = 650
        assertEquals(250, result.first().bucket.stepCount)
        assertEquals(650, result.first().dailySummary.totalSteps)
    }

    @Test
    fun `bucket boundary flush after seeded init uses seeded currentBucketSteps as delta`() {
        val accumulator = StepAccumulator(
            initialBucketTimestamp = startOfHour(8),
            initialCurrentBucketSteps = 120,
            initialTotalStepsToday = 400,
        )

        // Add more steps within current bucket
        accumulator.addSteps(delta = 30, now = timeInHour(8, 3))

        // Cross into next bucket → should flush with 120 + 30 = 150 stepCount
        val result = accumulator.addSteps(delta = 5, now = timeInHour(8, 6))

        assertEquals(150, result.first().bucket.stepCount)
        // totalAfterFlush was 400 + 30 = 430 before the new 5 steps hit
        assertEquals(430, result.first().dailySummary.totalSteps)
    }

    // ─── Multi-bucket scenario across midnight ───────────────────────────────

    @Test
    fun `multi-bucket scenario across midnight`() {
        // Service starts at 23:00 on day 0
        val day0Hour23 = epochForDayAndHour(0, 23)
        val accumulator = StepAccumulator(day0Hour23)

        // 23:00–23:05: 400 steps
        accumulator.addSteps(delta = 400, now = epochForDayAndHour(0, 23, 2))

        // Cross midnight: should flush 23:00 bucket
        val midnightFlushResults = accumulator.addSteps(delta = 5, now = epochForDayAndHour(1, 0, 5))
        assertTrue(midnightFlushResults.isNotEmpty())
        val midnightFlush = midnightFlushResults.first()
        assertEquals(400, midnightFlush.bucket.stepCount)
        // Daily summary for old day
        assertEquals(400, midnightFlush.dailySummary.totalSteps)
        assertEquals("2026-01-01", midnightFlush.dailySummary.date)

        // totalStepsToday reset to new day's 5 steps
        assertEquals(5, accumulator.totalStepsToday)

        accumulator.addSteps(delta = 50, now = epochForDayAndHour(1, 0, 7))

        // Cross bucket boundary into 00:10
        val bucketFlushResults = accumulator.addSteps(delta = 10, now = epochForDayAndHour(1, 0, 11))
        assertTrue(bucketFlushResults.isNotEmpty())
        val bucketFlush = bucketFlushResults.first()
        assertEquals(55, bucketFlush.bucket.stepCount) // 5 + 50
        assertEquals("2026-01-02", bucketFlush.dailySummary.date)
        assertEquals(55, bucketFlush.dailySummary.totalSteps)
    }

    // ─── Multi-bucket clock jump ──────────────────────────────────────────────

    @Test
    fun `two-bucket skip produces flush for current and one intermediate empty bucket`() {
        // Start at 08:00, accumulate steps, then jump by 10+ minutes (skipping 08:05)
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 300, now = epochForDayAndHour(0, 8, 2))

        // Jump directly from 08:02 to 08:11 — skips the 08:05 bucket entirely
        val results = accumulator.addSteps(delta = 5, now = epochForDayAndHour(0, 8, 11))

        // Should produce 2 results: 08:00 (300 steps) and 08:05 (0 steps)
        assertEquals("Expected 2 flush results for 2-bucket skip", 2, results.size)

        val flush08_00 = results[0]
        assertEquals(300, flush08_00.bucket.stepCount)

        val flush08_05 = results[1]
        assertEquals(0, flush08_05.bucket.stepCount)
    }

    @Test
    fun `intermediate empty buckets have zero stepCount`() {
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 100, now = epochForDayAndHour(0, 8, 2))

        // Jump 15+ minutes ahead: skips 08:05 and 08:10
        val results = accumulator.addSteps(delta = 10, now = epochForDayAndHour(0, 8, 16))

        // Intermediate results must have zero steps
        assertTrue("Expected at least 3 results", results.size >= 3)
        for (i in 1 until results.size) {
            assertEquals(
                "Intermediate bucket result[$i] should have 0 steps",
                0,
                results[i].bucket.stepCount,
            )
        }
    }

    @Test
    fun `large clock jump produces correct number of intermediate flushes`() {
        // Jump from 08:00 to 08:16 — skips 08:05 and 08:10
        val accumulator = StepAccumulator(epochForDayAndHour(0, 8))
        accumulator.addSteps(delta = 50, now = epochForDayAndHour(0, 8, 3))

        val results = accumulator.addSteps(delta = 5, now = epochForDayAndHour(0, 8, 16))

        // Expect: 08:00 (50 steps), 08:05 (0), 08:10 (0) = 3 total
        assertEquals(3, results.size)
        assertEquals(50, results[0].bucket.stepCount)
        assertEquals(0, results[1].bucket.stepCount)
        assertEquals(0, results[2].bucket.stepCount)

        // New bucket should hold only the arriving steps
        assertEquals(5, accumulator.currentBucketSteps)
        // totalStepsToday: 50 + 5 = 55
        assertEquals(55, accumulator.totalStepsToday)
    }

    @Test
    fun `multi-bucket skip crossing midnight resets daily total`() {
        // Start at 23:00 on day 0, accumulate steps, then jump to 00:10 on day 1
        val accumulator = StepAccumulator(epochForDayAndHour(0, 23))
        accumulator.addSteps(delta = 500, now = epochForDayAndHour(0, 23, 2))

        val results = accumulator.addSteps(delta = 10, now = epochForDayAndHour(1, 0, 10))

        // Should produce results: 23:00 (500), 23:05 (0), 00:00 (0), 00:05 (0)
        assertTrue("Expected multiple flush results", results.size >= 2)

        // First result: 23:00 on day 0, 500 steps, old-day summary
        assertEquals(500, results[0].bucket.stepCount)
        assertEquals("2026-01-01", results[0].dailySummary.date)
        assertEquals(500, results[0].dailySummary.totalSteps)

        // After the jump, totalStepsToday is only the new delta (day 1 steps)
        assertEquals(10, accumulator.totalStepsToday)
    }
}
