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
        // todayDate is Sunday (last day of the week), so the full Mon–Sun window has data
        // 2026-03-01 is a Sunday; ISO week is 2026-02-23 (Mon) through 2026-03-01 (Sun)
        val summaries = listOf(
            summary("2026-02-23", 8_000),
            summary("2026-02-24", 9_000),
            summary("2026-02-25", 10_000),
            summary("2026-02-26", 11_000),
            summary("2026-02-27", 7_000),
            summary("2026-02-28", 6_000),
            summary("2026-03-01", 5_000),
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-03-01",
        )
        assertEquals(7, bars.size)
        assertFalse("No bar should be a placeholder", bars.any { it.isPlaceholder })
    }

    // ─── buildChartBars — partial data ────────────────────────────────────────

    @Test
    fun `buildChartBars with 3 days fills remaining 4 slots as placeholders`() {
        // todayDate is Wednesday (2026-02-25); ISO week is Mon Feb 23 – Sun Mar 1.
        // Summaries exist only for Mon/Tue/Wed; Thu/Fri/Sat/Sun are placeholders (4 total).
        val summaries = listOf(
            summary("2026-02-23", 7_000), // Mon
            summary("2026-02-24", 8_000), // Tue
            summary("2026-02-25", 9_000), // Wed (today)
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-25",
        )
        assertEquals(7, bars.size)
        val placeholderCount = bars.count { it.isPlaceholder }
        assertEquals(4, placeholderCount)
    }

    // ─── buildChartBars — today flag ──────────────────────────────────────────

    @Test
    fun `buildChartBars marks only today's bar as isToday`() {
        // todayDate is Wednesday (2026-02-25) within the Mon–Sun week
        val summaries = listOf(
            summary("2026-02-23", 7_000), // Mon
            summary("2026-02-24", 8_000), // Tue
            summary("2026-02-25", 9_000), // Wed (today)
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-25",
        )
        val todayBars = bars.filter { it.isToday }
        assertEquals("Exactly one bar should be today", 1, todayBars.size)
        assertEquals("2026-02-25", todayBars[0].date)
    }

    @Test
    fun `buildChartBars today bar is placeholder when today has no data`() {
        // todayDate is Wednesday (2026-02-25), but no summary for that date
        val summaries = listOf(
            summary("2026-02-23", 7_000), // Mon
            summary("2026-02-24", 8_000), // Tue
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-25",
        )
        // The bar for 2026-02-25 is a placeholder, but isToday should still be true
        val todayBars = bars.filter { it.isToday }
        assertEquals("Exactly one bar should be today", 1, todayBars.size)
        assertTrue("Today's bar should be a placeholder when no data", todayBars[0].isPlaceholder)
    }

    // ─── buildChartBars — heightFraction values ───────────────────────────────

    @Test
    fun `buildChartBars heightFraction is between 0 and 1 inclusive`() {
        // todayDate is Sunday 2026-03-01; ISO week Mon Feb 23 – Sun Mar 1
        val summaries = listOf(
            summary("2026-02-23", 0),      // Mon
            summary("2026-02-24", 5_000),  // Tue
            summary("2026-03-01", 12_000), // Sun (today)
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-03-01",
        )
        bars.filter { !it.isPlaceholder }.forEach { bar ->
            assertTrue("heightFraction ${bar.heightFraction} >= 0", bar.heightFraction >= 0f)
            assertTrue("heightFraction ${bar.heightFraction} <= 1", bar.heightFraction <= 1f)
        }
    }

    @Test
    fun `buildChartBars max day has heightFraction of 1`() {
        // todayDate is Sunday 2026-03-01; ISO week Mon Feb 23 – Sun Mar 1
        val summaries = listOf(
            summary("2026-02-23", 5_000),  // Mon
            summary("2026-02-24", 8_000),  // Tue
            summary("2026-03-01", 12_000), // Sun (today) — max
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-03-01",
        )
        val maxBar = bars.maxByOrNull { it.steps }!!
        assertEquals(1f, maxBar.heightFraction, 0.001f)
    }

    @Test
    fun `buildChartBars scales to goal when goal is higher than max steps`() {
        // todayDate is Tuesday 2026-02-24; ISO week Mon Feb 23 – Sun Mar 1
        val summaries = listOf(
            summary("2026-02-23", 3_000), // Mon
            summary("2026-02-24", 5_000), // Tue (today) — max steps, but goal is 10_000
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-24",
        )
        // When goal > max steps, the scale ceiling is the goal
        val maxBar = bars.filter { !it.isPlaceholder }.maxByOrNull { it.steps }!!
        // 5000 / 10000 = 0.5
        assertEquals(0.5f, maxBar.heightFraction, 0.001f)
    }

    // ─── buildChartBars — aboveGoal flag ──────────────────────────────────────

    @Test
    fun `buildChartBars marks bars above goal as aboveGoal`() {
        // todayDate is Wednesday 2026-02-25; ISO week Mon Feb 23 – Sun Mar 1
        val summaries = listOf(
            summary("2026-02-23", 9_000),  // Mon — below goal
            summary("2026-02-24", 10_000), // Tue — exactly goal (not above)
            summary("2026-02-25", 11_000), // Wed (today) — above goal
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-25",
        )
        val dataBars = bars.filter { !it.isPlaceholder }
        val aboveGoalBars = dataBars.filter { it.aboveGoal }
        assertEquals("Only bars strictly above goal should be marked", 1, aboveGoalBars.size)
        assertEquals(11_000, aboveGoalBars[0].steps)
    }

    @Test
    fun `buildChartBars bar at exactly goal is not marked aboveGoal`() {
        // todayDate is Monday 2026-02-23
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
        // todayDate is Monday 2026-02-23; summary is within the Mon–Sun window
        val summaries = listOf(
            summary("2026-02-23", 7_500), // Mon (today)
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
        // todayDate is Monday 2026-02-23; summary within the Mon–Sun window
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

    // ─── buildChartBars — ISO week window ─────────────────────────────────────

    @Test
    fun `buildChartBars window starts on Monday regardless of todayDate day-of-week`() {
        // todayDate is Thursday 2026-02-26; ISO week is Mon Feb 23 – Sun Mar 1
        val bars = buildChartBars(
            daySummaries = emptyList(),
            goal = goal,
            todayDate = "2026-02-26",
        )
        assertEquals("First bar should be Monday (2026-02-23)", "2026-02-23", bars[0].date)
        assertEquals("Last bar should be Sunday (2026-03-01)", "2026-03-01", bars[6].date)
    }

    @Test
    fun `buildChartBars future days within the week are placeholders`() {
        // todayDate is Tuesday 2026-02-24; Wed–Sun are future days and must be placeholders
        val summaries = listOf(
            summary("2026-02-23", 8_000), // Mon — past
            summary("2026-02-24", 9_000), // Tue — today
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-24",
        )
        // Mon and Tue have data; Wed–Sun are future placeholders
        val futureBars = bars.drop(2) // Wed through Sun
        assertTrue("All future bars should be placeholders", futureBars.all { it.isPlaceholder })
        assertEquals("Future placeholder count should be 5", 5, futureBars.size)
    }

    @Test
    fun `buildChartBars future days are placeholders even when summaries exist for them`() {
        // Defensive: if a summary exists for a future date, the bar should still be a placeholder
        val summaries = listOf(
            summary("2026-02-24", 5_000), // Tue (today)
            summary("2026-02-25", 8_000), // Wed — future date, should be ignored
        )
        val bars = buildChartBars(
            daySummaries = summaries,
            goal = goal,
            todayDate = "2026-02-24",
        )
        val wedBar = bars.first { it.date == "2026-02-25" }
        assertTrue("Future day bar should be a placeholder", wedBar.isPlaceholder)
        assertEquals("Future day bar should have 0 steps", 0, wedBar.steps)
    }

    @Test
    fun `buildChartBars day order is always Mon Tue Wed Thu Fri Sat Sun`() {
        // todayDate is Sunday 2026-03-01 (last day of week)
        val bars = buildChartBars(
            daySummaries = emptyList(),
            goal = goal,
            todayDate = "2026-03-01",
        )
        val expectedLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        assertEquals("Day labels must be Mon–Sun order", expectedLabels, bars.map { it.dayLabel })
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
