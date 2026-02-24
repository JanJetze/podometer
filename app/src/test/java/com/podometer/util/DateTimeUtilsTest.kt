// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Unit tests for [DateTimeUtils].
 *
 * All tests are pure JVM — no Android framework dependencies.
 */
class DateTimeUtilsTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns epoch-millis at [minuteOffset] minutes into [hour] today (local time).
     */
    private fun timeInHour(hour: Int, minuteOffset: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minuteOffset)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns epoch-millis for a specific date and hour.
     * Uses Jan 1, 2026 as day 0 (dayOffset = 0).
     */
    private fun epochForDayAndHour(dayOffset: Int, hour: Int, minuteOffset: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 1 + dayOffset, hour, minuteOffset, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ─── todayStartMillis ─────────────────────────────────────────────────────

    @Test
    fun `todayStartMillis returns positive epoch millis`() {
        val millis = DateTimeUtils.todayStartMillis()

        assertTrue("Expected positive millis, got $millis", millis > 0L)
    }

    @Test
    fun `todayStartMillis returns value less than or equal to current time`() {
        val before = System.currentTimeMillis()

        val millis = DateTimeUtils.todayStartMillis()

        assertTrue("Midnight should be before or equal to now", millis <= before)
    }

    @Test
    fun `todayStartMillis returns start of today not yesterday or tomorrow`() {
        val millis = DateTimeUtils.todayStartMillis()
        val today = LocalDate.now()
        val expectedMillis = today
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals("todayStartMillis should equal start of today", expectedMillis, millis)
    }

    @Test
    fun `todayStartMillis produces same result as LocalDate atStartOfDay`() {
        val expected = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val actual = DateTimeUtils.todayStartMillis()

        assertEquals(expected, actual)
    }

    // ─── toLocalDate ─────────────────────────────────────────────────────────

    @Test
    fun `toLocalDate converts epoch millis to correct LocalDate`() {
        val epochMillis = epochForDayAndHour(dayOffset = 0, hour = 10)
        val expected = LocalDate.of(2026, 1, 1)

        val result = DateTimeUtils.toLocalDate(epochMillis)

        assertEquals(expected, result)
    }

    @Test
    fun `toLocalDate day 1 returns correct date`() {
        val epochMillis = epochForDayAndHour(dayOffset = 1, hour = 8)
        val expected = LocalDate.of(2026, 1, 2)

        val result = DateTimeUtils.toLocalDate(epochMillis)

        assertEquals(expected, result)
    }

    @Test
    fun `toLocalDate matches Instant atZone toLocalDate`() {
        val epochMillis = epochForDayAndHour(dayOffset = 0, hour = 14, minuteOffset = 37)
        val expected = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val result = DateTimeUtils.toLocalDate(epochMillis)

        assertEquals(expected, result)
    }

    @Test
    fun `toLocalDate works for time just before midnight`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 1, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val epochMillis = cal.timeInMillis
        val expected = LocalDate.of(2026, 1, 1)

        val result = DateTimeUtils.toLocalDate(epochMillis)

        assertEquals(expected, result)
    }

    @Test
    fun `toLocalDate works for time exactly at midnight`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 2, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val epochMillis = cal.timeInMillis
        val expected = LocalDate.of(2026, 1, 2)

        val result = DateTimeUtils.toLocalDate(epochMillis)

        assertEquals(expected, result)
    }

    // ─── truncateToHour ───────────────────────────────────────────────────────

    @Test
    fun `truncateToHour returns start of hour for time within hour`() {
        val midHour = timeInHour(hour = 8, minuteOffset = 37)
        val expected = timeInHour(hour = 8, minuteOffset = 0)

        val result = DateTimeUtils.truncateToHour(midHour)

        assertEquals(expected, result)
    }

    @Test
    fun `truncateToHour returns same value when already at hour start`() {
        val hourStart = timeInHour(hour = 10, minuteOffset = 0)

        val result = DateTimeUtils.truncateToHour(hourStart)

        assertEquals(hourStart, result)
    }

    @Test
    fun `truncateToHour clears minutes seconds and nanos`() {
        val midHour = timeInHour(hour = 15, minuteOffset = 59)

        val result = DateTimeUtils.truncateToHour(midHour)

        val resultCal = Calendar.getInstance()
        resultCal.timeInMillis = result
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `truncateToHour preserves the correct hour`() {
        val midHour = timeInHour(hour = 23, minuteOffset = 45)

        val result = DateTimeUtils.truncateToHour(midHour)

        val resultCal = Calendar.getInstance()
        resultCal.timeInMillis = result
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `truncateToHour matches StepAccumulator companion result for same input`() {
        val epochMillis = epochForDayAndHour(dayOffset = 0, hour = 8, minuteOffset = 37)

        val fromUtils = DateTimeUtils.truncateToHour(epochMillis)
        val fromAccumulator = com.podometer.service.StepAccumulator.truncateToHour(epochMillis)

        assertEquals(
            "DateTimeUtils.truncateToHour should match StepAccumulator.truncateToHour",
            fromAccumulator,
            fromUtils,
        )
    }

    // ─── Class and package existence ──────────────────────────────────────────

    @Test
    fun `DateTimeUtils object exists in util package`() {
        val clazz = DateTimeUtils::class.java
        assertEquals("com.podometer.util", clazz.packageName)
    }
}
