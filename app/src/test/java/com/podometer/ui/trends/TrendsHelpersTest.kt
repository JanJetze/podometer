// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import com.podometer.domain.model.DaySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for pure-Kotlin helper functions in the Trends screen components.
 *
 * Covers [buildTrendsChartBars], [computeTrendsGoalFraction], [buildMonthlyChartBars],
 * and [TrendsStats] computation helpers. All helpers are pure-Kotlin and JVM-testable.
 */
class TrendsHelpersTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────────

    private val targetGoal = 10_000
    private val minimumGoal = 6_000

    private fun summary(date: String, steps: Int, distanceKm: Float = 0f) = DaySummary(
        date = date,
        totalSteps = steps,
        totalDistanceKm = distanceKm,
    )

    // ─── buildTrendsChartBars — weekly ────────────────────────────────────────

    @Test
    fun `buildTrendsChartBars with 7 summaries returns 7 bars`() {
        val summaries = listOf(
            summary("2026-03-02", 8_000),
            summary("2026-03-03", 7_500),
            summary("2026-03-04", 11_000),
            summary("2026-03-05", 5_000),
            summary("2026-03-06", 9_000),
            summary("2026-03-07", 12_000),
            summary("2026-03-08", 6_500),
        )
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(7, bars.size)
    }

    @Test
    fun `buildTrendsChartBars with empty list returns empty bars`() {
        val bars = buildTrendsChartBars(emptyList(), targetGoal, minimumGoal)
        assertEquals(0, bars.size)
    }

    @Test
    fun `buildTrendsChartBars colors bar as belowMinimum when steps less than minimumGoal`() {
        val summaries = listOf(summary("2026-03-02", 3_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(TrendsBarTier.BELOW_MINIMUM, bars.first().tier)
    }

    @Test
    fun `buildTrendsChartBars colors bar as minimumToTarget when steps between minimum and target`() {
        val summaries = listOf(summary("2026-03-02", 8_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(TrendsBarTier.MINIMUM_TO_TARGET, bars.first().tier)
    }

    @Test
    fun `buildTrendsChartBars colors bar as aboveTarget when steps exceed target`() {
        val summaries = listOf(summary("2026-03-02", 12_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(TrendsBarTier.ABOVE_TARGET, bars.first().tier)
    }

    @Test
    fun `buildTrendsChartBars bar at exactly minimumGoal is minimumToTarget`() {
        val summaries = listOf(summary("2026-03-02", 6_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(TrendsBarTier.MINIMUM_TO_TARGET, bars.first().tier)
    }

    @Test
    fun `buildTrendsChartBars bar at exactly targetGoal is aboveTarget`() {
        val summaries = listOf(summary("2026-03-02", 10_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(TrendsBarTier.ABOVE_TARGET, bars.first().tier)
    }

    @Test
    fun `buildTrendsChartBars heightFraction is between 0 and 1`() {
        val summaries = listOf(
            summary("2026-03-02", 3_000),
            summary("2026-03-03", 10_000),
            summary("2026-03-04", 15_000),
        )
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        bars.forEach { bar ->
            assertTrue("heightFraction must be >= 0", bar.heightFraction >= 0f)
            assertTrue("heightFraction must be <= 1", bar.heightFraction <= 1f)
        }
    }

    @Test
    fun `buildTrendsChartBars max bar has heightFraction of 1`() {
        val summaries = listOf(
            summary("2026-03-02", 5_000),
            summary("2026-03-03", 10_000),
            summary("2026-03-04", 15_000), // max
        )
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        val maxBar = bars.maxByOrNull { it.steps }!!
        assertEquals(1f, maxBar.heightFraction, 0.001f)
    }

    @Test
    fun `buildTrendsChartBars preserves step count from summary`() {
        val summaries = listOf(summary("2026-03-02", 7_777))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals(7_777, bars.first().steps)
    }

    @Test
    fun `buildTrendsChartBars preserves date from summary`() {
        val summaries = listOf(summary("2026-03-05", 8_000))
        val bars = buildTrendsChartBars(summaries, targetGoal, minimumGoal)
        assertEquals("2026-03-05", bars.first().date)
    }

    // ─── computeTrendsGoalFraction ─────────────────────────────────────────────

    @Test
    fun `computeTrendsGoalFraction returns 1 when maxSteps less than targetGoal`() {
        val fraction = computeTrendsGoalFraction(targetGoal = 10_000, maxSteps = 8_000)
        assertEquals(1.0f, fraction, 0.001f)
    }

    @Test
    fun `computeTrendsGoalFraction returns less than 1 when maxSteps exceeds target`() {
        val fraction = computeTrendsGoalFraction(targetGoal = 10_000, maxSteps = 15_000)
        assertEquals(10_000f / 15_000f, fraction, 0.001f)
    }

    @Test
    fun `computeTrendsGoalFraction returns 1 when both are zero`() {
        val fraction = computeTrendsGoalFraction(targetGoal = 0, maxSteps = 0)
        assertEquals(1.0f, fraction, 0.001f)
    }

    @Test
    fun `computeTrendsGoalFraction returns 1 when maxSteps equals targetGoal`() {
        val fraction = computeTrendsGoalFraction(targetGoal = 10_000, maxSteps = 10_000)
        assertEquals(1.0f, fraction, 0.001f)
    }

    // ─── computeTrendsStats ────────────────────────────────────────────────────

    @Test
    fun `computeTrendsStats averageSteps is mean of all days`() {
        val summaries = listOf(
            summary("2026-03-02", 6_000),
            summary("2026-03-03", 14_000),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals(10_000, stats.averageSteps)
    }

    @Test
    fun `computeTrendsStats averageSteps is zero for empty list`() {
        val stats = computeTrendsStats(emptyList(), targetGoal)
        assertEquals(0, stats.averageSteps)
    }

    @Test
    fun `computeTrendsStats bestDaySteps is max steps`() {
        val summaries = listOf(
            summary("2026-03-02", 6_000),
            summary("2026-03-03", 14_000),
            summary("2026-03-04", 9_000),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals(14_000, stats.bestDaySteps)
    }

    @Test
    fun `computeTrendsStats bestDayDate matches best day`() {
        val summaries = listOf(
            summary("2026-03-02", 6_000),
            summary("2026-03-03", 14_000),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals("2026-03-03", stats.bestDayDate)
    }

    @Test
    fun `computeTrendsStats bestDaySteps is zero for empty list`() {
        val stats = computeTrendsStats(emptyList(), targetGoal)
        assertEquals(0, stats.bestDaySteps)
    }

    @Test
    fun `computeTrendsStats totalDistanceKm sums distances`() {
        val summaries = listOf(
            DaySummary("2026-03-02", 6_000, 4.5f),
            DaySummary("2026-03-03", 14_000, 10.0f),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals(14.5, stats.totalDistanceKm.toDouble(), 0.01)
    }

    @Test
    fun `computeTrendsStats achievementRate is fraction of days meeting target`() {
        val summaries = listOf(
            summary("2026-03-02", 5_000),  // below target
            summary("2026-03-03", 10_000), // meets target
            summary("2026-03-04", 12_000), // above target
            summary("2026-03-05", 8_000),  // below target
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        // 2 out of 4 meet or exceed target
        assertEquals(0.5f, stats.achievementRate, 0.001f)
    }

    @Test
    fun `computeTrendsStats achievementRate is 0 when no days meet target`() {
        val summaries = listOf(
            summary("2026-03-02", 5_000),
            summary("2026-03-03", 3_000),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals(0.0f, stats.achievementRate, 0.001f)
    }

    @Test
    fun `computeTrendsStats achievementRate is 1 when all days meet target`() {
        val summaries = listOf(
            summary("2026-03-02", 10_000),
            summary("2026-03-03", 11_000),
        )
        val stats = computeTrendsStats(summaries, targetGoal)
        assertEquals(1.0f, stats.achievementRate, 0.001f)
    }

    @Test
    fun `computeTrendsStats achievementRate is 0 for empty list`() {
        val stats = computeTrendsStats(emptyList(), targetGoal)
        assertEquals(0.0f, stats.achievementRate, 0.001f)
    }

    // ─── formatTrendsDistance ──────────────────────────────────────────────────

    @Test
    fun `formatTrendsDistance formats with one decimal place`() {
        val result = formatTrendsDistance(7.56, Locale.US)
        assertEquals("7.6 km", result)
    }

    @Test
    fun `formatTrendsDistance formats zero as 0_0 km`() {
        val result = formatTrendsDistance(0.0, Locale.US)
        assertEquals("0.0 km", result)
    }

    // ─── formatTrendsAchievementRate ──────────────────────────────────────────

    @Test
    fun `formatTrendsAchievementRate formats fraction as percentage`() {
        val result = formatTrendsAchievementRate(0.75f)
        assertEquals("75%", result)
    }

    @Test
    fun `formatTrendsAchievementRate rounds 0_5 to 50 percent`() {
        val result = formatTrendsAchievementRate(0.5f)
        assertEquals("50%", result)
    }

    @Test
    fun `formatTrendsAchievementRate formats 1_0 as 100 percent`() {
        val result = formatTrendsAchievementRate(1.0f)
        assertEquals("100%", result)
    }

    @Test
    fun `formatTrendsAchievementRate formats 0_0 as 0 percent`() {
        val result = formatTrendsAchievementRate(0.0f)
        assertEquals("0%", result)
    }

    // ─── formatWeekLabel ──────────────────────────────────────────────────────

    @Test
    fun `formatWeekLabel formats Mon to Sun of a given week`() {
        val label = formatWeekLabel("2026-03-02", "2026-03-08")
        // Should contain both dates in readable form
        assertTrue("Should mention Mar", label.contains("Mar"))
    }

    // ─── weekDayLabel ─────────────────────────────────────────────────────────

    @Test
    fun `weekDayLabel for Monday returns M`() {
        assertEquals("M", weekDayLabel("2026-03-02")) // 2026-03-02 is a Monday
    }

    @Test
    fun `weekDayLabel for Sunday returns S`() {
        assertEquals("S", weekDayLabel("2026-03-08")) // 2026-03-08 is a Sunday
    }

    // ─── TrendsChartBar — class existence ────────────────────────────────────

    @Test
    fun `TrendsChartBar data class exists`() {
        val bar = TrendsChartBar(
            date = "2026-03-02",
            dayLabel = "M",
            steps = 8_000,
            heightFraction = 0.8f,
            tier = TrendsBarTier.MINIMUM_TO_TARGET,
        )
        assertEquals("2026-03-02", bar.date)
        assertEquals(8_000, bar.steps)
    }

    // ─── TrendsStats — class existence ───────────────────────────────────────

    @Test
    fun `TrendsStats data class exists and holds values`() {
        val stats = TrendsStats(
            averageSteps = 8_500,
            bestDaySteps = 13_000,
            bestDayDate = "2026-03-03",
            totalDistanceKm = 42.5,
            achievementRate = 0.75f,
        )
        assertEquals(8_500, stats.averageSteps)
        assertEquals("2026-03-03", stats.bestDayDate)
        assertEquals(0.75f, stats.achievementRate, 0.001f)
    }
}
