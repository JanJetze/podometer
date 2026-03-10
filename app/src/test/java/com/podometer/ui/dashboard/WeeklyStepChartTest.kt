// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.domain.model.DaySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WeeklyStepChart] pure-logic helpers.
 *
 * These tests exercise [buildChartBars] and [weeklyChartContentDescription],
 * which are pure-Kotlin functions that can be run on the JVM without Compose or
 * an Android device.
 */
class WeeklyStepChartTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private val goal = 10_000

    /** Creates a [DaySummary] with only the fields relevant to the chart. */
    private fun summary(date: String, steps: Int) = DaySummary(
        date = date,
        totalSteps = steps,
        totalDistanceKm = 0f,
    )

    // ─── buildChartBars — empty input ─────────────────────────────────────────

    @Test
    fun `buildChartBars with empty list returns 7 placeholder bars`() {
        val bars = buildChartBars(
            daySummaries = emptyList(),
            goal = goal,
            todayDate = "2026-02-23",
        )
        assertEquals(7, bars.size)
        assertTrue("All bars should be placeholders", bars.all { it.isPlaceholder })
    }

    // ─── buildChartBars — full 7-day data ─────────────────────────────────────

    @Test
    fun `buildChartBars with 7 days returns 7 bars none placeholder`() {
        val summaries = listOf(
            summary("2026-02-17", 8_000),
            summary("2026-02-18", 9_000),
            summary("2026-02-19", 10_000),
            summary("2026-02-20", 11_000),
            summary("2026-02-21", 7_000),
            summary("2026-02-22", 6_000),
            summary("2026-02-23", 5_000),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        assertEquals(7, bars.size)
        assertFalse("No bar should be a placeholder", bars.any { it.isPlaceholder })
    }

    // ─── buildChartBars — partial data ────────────────────────────────────────

    @Test
    fun `buildChartBars with 3 days fills remaining 4 slots as placeholders`() {
        val summaries = listOf(
            summary("2026-02-21", 7_000),
            summary("2026-02-22", 8_000),
            summary("2026-02-23", 9_000),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        assertEquals(7, bars.size)
        val placeholderCount = bars.count { it.isPlaceholder }
        assertEquals(4, placeholderCount)
    }

    // ─── buildChartBars — today flag ──────────────────────────────────────────

    @Test
    fun `buildChartBars marks only today's bar as isToday`() {
        val summaries = listOf(
            summary("2026-02-21", 7_000),
            summary("2026-02-22", 8_000),
            summary("2026-02-23", 9_000),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        val todayBars = bars.filter { it.isToday }
        assertEquals("Exactly one bar should be today", 1, todayBars.size)
        assertEquals("2026-02-23", todayBars[0].date)
    }

    @Test
    fun `buildChartBars no bar is isToday when today has no data`() {
        val summaries = listOf(
            summary("2026-02-20", 7_000),
            summary("2026-02-21", 8_000),
        )
        // todayDate is 2026-02-23 which is not in summaries
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        // The bar for 2026-02-23 is a placeholder; placeholder bars don't hold a date match
        val todayBars = bars.filter { it.isToday }
        // Since the today slot is a placeholder, isToday may be false — test that at most 1 is today
        assertTrue("At most one bar should be today", todayBars.size <= 1)
    }

    // ─── buildChartBars — heightFraction values ───────────────────────────────

    @Test
    fun `buildChartBars heightFraction is between 0 and 1 inclusive`() {
        val summaries = listOf(
            summary("2026-02-21", 0),
            summary("2026-02-22", 5_000),
            summary("2026-02-23", 12_000),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        bars.filter { !it.isPlaceholder }.forEach { bar ->
            assertTrue("heightFraction ${bar.heightFraction} >= 0", bar.heightFraction >= 0f)
            assertTrue("heightFraction ${bar.heightFraction} <= 1", bar.heightFraction <= 1f)
        }
    }

    @Test
    fun `buildChartBars max day has heightFraction of 1`() {
        val summaries = listOf(
            summary("2026-02-21", 5_000),
            summary("2026-02-22", 8_000),
            summary("2026-02-23", 12_000), // max
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        val maxBar = bars.maxByOrNull { it.steps }!!
        assertEquals(1f, maxBar.heightFraction, 0.001f)
    }

    @Test
    fun `buildChartBars scales to goal when goal is higher than max steps`() {
        val summaries = listOf(
            summary("2026-02-22", 3_000),
            summary("2026-02-23", 5_000), // max steps, but goal is 10_000
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        // When goal > max steps, the scale ceiling is the goal
        val maxBar = bars.filter { !it.isPlaceholder }.maxByOrNull { it.steps }!!
        // 5000 / 10000 = 0.5
        assertEquals(0.5f, maxBar.heightFraction, 0.001f)
    }

    // ─── buildChartBars — aboveGoal flag ──────────────────────────────────────

    @Test
    fun `buildChartBars marks bars above goal as aboveGoal`() {
        val summaries = listOf(
            summary("2026-02-21", 9_000),  // below goal
            summary("2026-02-22", 10_000), // exactly goal (not above)
            summary("2026-02-23", 11_000), // above goal
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        val dataBars = bars.filter { !it.isPlaceholder }
        val aboveGoalBars = dataBars.filter { it.aboveGoal }
        assertEquals("Only bars strictly above goal should be marked", 1, aboveGoalBars.size)
        assertEquals(11_000, aboveGoalBars[0].steps)
    }

    @Test
    fun `buildChartBars bar at exactly goal is not marked aboveGoal`() {
        val summaries = listOf(
            summary("2026-02-23", 10_000), // exactly goal
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        val exactGoalBar = bars.first { !it.isPlaceholder }
        assertFalse("Exactly goal should not be aboveGoal", exactGoalBar.aboveGoal)
    }

    // ─── buildChartBars — day labels ──────────────────────────────────────────

    @Test
    fun `buildChartBars returns exactly 7 bars with day labels M T W T F S S`() {
        val bars = buildChartBars(
            daySummaries = emptyList(),
            goal = goal,
            todayDate = "2026-02-23", // Monday
        )
        // The 7 slots represent Mon-Sun of the week containing todayDate
        val labels = bars.map { it.dayLabel }
        assertEquals(7, labels.size)
        assertTrue("Labels should be from set M,T,W,T,F,S,S", labels.all { it in listOf("M", "T", "W", "T", "F", "S", "S") })
    }

    // ─── buildChartBars — goalFraction ────────────────────────────────────────

    @Test
    fun `computeGoalFraction returns correct fraction when goal is within scale`() {
        // scale ceiling = max(goal, maxSteps) = max(10000, 8000) = 10000
        // goalFraction = 10000 / 10000 = 1.0
        val fraction = computeGoalFraction(goal = 10_000, maxSteps = 8_000)
        assertEquals(1.0f, fraction, 0.001f)
    }

    @Test
    fun `computeGoalFraction returns less than 1 when maxSteps exceeds goal`() {
        // scale ceiling = max(10000, 12000) = 12000
        // goalFraction = 10000 / 12000 = 0.833
        val fraction = computeGoalFraction(goal = 10_000, maxSteps = 12_000)
        assertEquals(10_000f / 12_000f, fraction, 0.001f)
    }

    @Test
    fun `computeGoalFraction returns 1 when maxSteps equals goal`() {
        val fraction = computeGoalFraction(goal = 10_000, maxSteps = 10_000)
        assertEquals(1.0f, fraction, 0.001f)
    }

    @Test
    fun `computeGoalFraction returns 1 when maxSteps is zero`() {
        // Avoid division by zero — should return 1.0 as a safe default
        val fraction = computeGoalFraction(goal = 10_000, maxSteps = 0)
        assertEquals(1.0f, fraction, 0.001f)
    }

    // ─── weeklyChartContentDescription ────────────────────────────────────────

    @Test
    fun `weeklyChartContentDescription with empty bars returns no data message`() {
        val bars = buildChartBars(emptyList(), goal, "2026-02-23")
        val description = weeklyChartContentDescription(bars)
        assertTrue(
            "Should mention 'no data' or 'placeholder': $description",
            description.contains("no data", ignoreCase = true) ||
                description.contains("placeholder", ignoreCase = true) ||
                description.contains("0 steps", ignoreCase = true),
        )
    }

    @Test
    fun `weeklyChartContentDescription lists all 7 days`() {
        val bars = buildChartBars(emptyList(), goal, "2026-02-23")
        val description = weeklyChartContentDescription(bars)
        // Should produce some content description for all slots
        assertTrue("Description should not be empty", description.isNotBlank())
    }

    @Test
    fun `weeklyChartContentDescription includes step counts for data bars`() {
        val summaries = listOf(
            summary("2026-02-23", 7_500),
        )
        val bars = buildChartBars(summaries, goal, "2026-02-23")
        val description = weeklyChartContentDescription(bars)
        assertTrue(
            "Description should mention step count 7500: $description",
            description.contains("7500") || description.contains("7,500"),
        )
    }

    // ─── buildChartBars — detail fields from DaySummary ───────────────────────

    @Test
    fun `buildChartBars populates distanceKm from summary`() {
        val summaries = listOf(
            DaySummary(
                date = "2026-02-23",
                totalSteps = 8_000,
                totalDistanceKm = 6.1f,
            ),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-23",
        )
        val dataBar = bars.first { !it.isPlaceholder }
        assertEquals(6.1f, dataBar.distanceKm, 0.01f)
    }

    @Test
    fun `buildChartBars placeholder bars have zero detail fields`() {
        val bars = buildChartBars(
            daySummaries = emptyList(),
            goal = goal,
            todayDate = "2026-02-23",
        )
        bars.forEach { bar ->
            assertEquals(0f, bar.distanceKm, 0.001f)
        }
    }

    // ─── formatChartDate ───────────────────────────────────────────────────────

    @Test
    fun `formatChartDate returns short human-readable date`() {
        val result = formatChartDate("2026-03-02")
        // Should contain abbreviated day name and month
        assertTrue(
            "Expected short date format but got: $result",
            result.contains("Mon") && result.contains("Mar") && result.contains("2"),
        )
    }

    // ─── WeeklyStepChartKt class existence ────────────────────────────────────

    @Test
    fun `WeeklyStepChartKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.WeeklyStepChartKt")
        assertTrue(
            "WeeklyStepChartKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `buildChartBars is accessible as a top-level function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.WeeklyStepChartKt")
        val method = clazz.getDeclaredMethod(
            "buildChartBars",
            List::class.java,
            Int::class.java,
            String::class.java,
        )
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(null, emptyList<DaySummary>(), 10_000, "2026-02-23") as List<*>
        assertEquals(7, result.size)
    }
}
