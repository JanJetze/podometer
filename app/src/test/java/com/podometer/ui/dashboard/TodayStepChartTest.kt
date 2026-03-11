// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TodayStepChart] pure-logic helpers.
 *
 * Tests exercise [aggregateBars], [buildTimeLabels], and [chartYAxisCeiling]
 * which are pure-Kotlin functions testable on the JVM.
 */
class TodayStepChartTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────

    /**
     * Creates a [StepBar] with the given epoch ms timestamp (5-min aligned) and step count.
     *
     * Uses an epoch base exactly divisible by 3_600_000 ms (1 hour) so that grouping by
     * 5-min, 15-min, 30-min, and 60-min bucket sizes all produce clean, predictable results.
     * Base = 3_600_000 * 472_222 = 1_699_999_200_000
     */
    private fun bar(startMinutesFromMidnight: Int, steps: Int): StepBar {
        val hourAlignedBase = 1_699_999_200_000L // divisible by 3_600_000
        val epochMs = hourAlignedBase + startMinutesFromMidnight.toLong() * 60_000L
        return StepBar(startTime = epochMs, stepCount = steps)
    }

    // ─── aggregateBars ────────────────────────────────────────────────────────

    @Test
    fun `aggregateBars with FIVE_MIN resolution returns same bars unchanged`() {
        val input = listOf(bar(0, 100), bar(5, 150), bar(10, 200))
        val result = aggregateBars(input, ChartResolution.FIVE_MIN)
        assertEquals(input.size, result.size)
    }

    @Test
    fun `aggregateBars with FIFTEEN_MIN combines 3 five-minute bars into 1`() {
        val input = listOf(
            bar(0, 100),
            bar(5, 150),
            bar(10, 200),
        )
        val result = aggregateBars(input, ChartResolution.FIFTEEN_MIN)
        assertEquals(1, result.size)
        assertEquals(450, result[0].stepCount)
    }

    @Test
    fun `aggregateBars with THIRTY_MIN combines 6 five-minute bars into 1`() {
        val input = (0 until 6).map { bar(it * 5, 100) }
        val result = aggregateBars(input, ChartResolution.THIRTY_MIN)
        assertEquals(1, result.size)
        assertEquals(600, result[0].stepCount)
    }

    @Test
    fun `aggregateBars with HOURLY combines 12 five-minute bars into 1`() {
        val input = (0 until 12).map { bar(it * 5, 100) }
        val result = aggregateBars(input, ChartResolution.HOURLY)
        assertEquals(1, result.size)
        assertEquals(1200, result[0].stepCount)
    }

    @Test
    fun `aggregateBars handles empty list`() {
        val result = aggregateBars(emptyList(), ChartResolution.FIFTEEN_MIN)
        assertEquals(0, result.size)
    }

    @Test
    fun `aggregateBars preserves startTime as the bucket boundary`() {
        val input = listOf(bar(0, 100), bar(5, 150), bar(10, 200))
        val result = aggregateBars(input, ChartResolution.FIFTEEN_MIN)
        assertEquals(input[0].startTime, result[0].startTime)
    }

    @Test
    fun `aggregateBars handles bars that don't fill a complete bucket`() {
        // Two 5-min bars for a 15-min bucket — should still aggregate them
        val input = listOf(bar(0, 100), bar(5, 150))
        val result = aggregateBars(input, ChartResolution.FIFTEEN_MIN)
        assertEquals(1, result.size)
        assertEquals(250, result[0].stepCount)
    }

    @Test
    fun `aggregateBars produces correct number of buckets for mixed data`() {
        // 6 bars at 5-min spacing, aggregated to 30-min → 1 bucket of 30 min
        val input = (0 until 6).map { bar(it * 5, 10) }
        val result = aggregateBars(input, ChartResolution.THIRTY_MIN)
        assertEquals(1, result.size)
    }

    // ─── chartYAxisCeiling ────────────────────────────────────────────────────

    @Test
    fun `chartYAxisCeiling returns 0 for empty bars`() {
        val ceiling = chartYAxisCeiling(emptyList())
        assertEquals(0, ceiling)
    }

    @Test
    fun `chartYAxisCeiling returns max step count`() {
        val bars = listOf(
            StepBar(1000L, 100),
            StepBar(2000L, 300),
            StepBar(3000L, 200),
        )
        val ceiling = chartYAxisCeiling(bars)
        assertEquals(300, ceiling)
    }

    @Test
    fun `chartYAxisCeiling with all zero steps returns 0`() {
        val bars = listOf(StepBar(1000L, 0), StepBar(2000L, 0))
        val ceiling = chartYAxisCeiling(bars)
        assertEquals(0, ceiling)
    }

    // ─── buildTimeLabels ──────────────────────────────────────────────────────

    @Test
    fun `buildTimeLabels returns empty list for empty bars`() {
        val labels = buildTimeLabels(emptyList(), ChartResolution.HOURLY, intervalHours = 2)
        assertEquals(0, labels.size)
    }

    @Test
    fun `buildTimeLabels returns non-empty string labels for non-empty bars`() {
        val bars = (0 until 12).map { StepBar(it.toLong() * 3_600_000L, 100) }
        val labels = buildTimeLabels(bars, ChartResolution.HOURLY, intervalHours = 2)
        assertTrue("Should have at least one label", labels.isNotEmpty())
        labels.forEach { pair ->
            assertTrue("Label should not be blank: ${pair.second}", pair.second.isNotBlank())
        }
    }

    @Test
    fun `buildTimeLabels uses compact lowercase format without space`() {
        // Use a known hour-aligned epoch so the time is predictable (midnight UTC = 1970-01-01T00:00Z)
        // At UTC+0, epoch 0 = 12am, epoch 3_600_000 = 1am, etc.
        // We just verify labels don't contain a space between hour and meridiem
        val bars = (0 until 12).map { StepBar(it.toLong() * 3_600_000L, 100) }
        val labels = buildTimeLabels(bars, ChartResolution.HOURLY, intervalHours = 1)
        labels.forEach { (_, label) ->
            assertTrue(
                "Label '$label' should not contain a space (expected compact format like '6am')",
                !label.contains(' '),
            )
            assertTrue(
                "Label '$label' should be lowercase",
                label == label.lowercase(),
            )
        }
    }

    @Test
    fun `buildTimeLabels FIVE_MIN resolution uses 3-hour intervals by default when called with intervalHours=3`() {
        // 24 hours * 12 bars/hour = 288 bars for a full day at 5m resolution
        val hourAlignedBase = 1_699_999_200_000L
        val bars = (0 until 288).map { StepBar(hourAlignedBase + it.toLong() * 5 * 60_000L, 100) }
        val labels = buildTimeLabels(bars, ChartResolution.FIVE_MIN, intervalHours = 3)
        // 3 hours * 12 bars/hour = 36 bars per interval
        // First label at index 0, then 36, 72, 108, 144, 180, 216, 252 → 8 labels for 288 bars
        assertEquals(8, labels.size)
        assertEquals(0, labels[0].first)
        assertEquals(36, labels[1].first)
    }

    @Test
    fun `buildTimeLabels non-FIVE_MIN resolutions use 2-hour intervals by default`() {
        // 24 hourly bars, intervalHours=2 → labels at indices 0, 2, 4, ..., 22 → 12 labels
        val hourAlignedBase = 1_699_999_200_000L
        val bars = (0 until 24).map { StepBar(hourAlignedBase + it.toLong() * 3_600_000L, 100) }
        val labels = buildTimeLabels(bars, ChartResolution.HOURLY, intervalHours = 2)
        assertEquals(12, labels.size)
        assertEquals(0, labels[0].first)
        assertEquals(2, labels[1].first)
    }

    // ─── ChartResolution enum ─────────────────────────────────────────────────

    @Test
    fun `ChartResolution FIVE_MIN has minutes 5`() {
        assertEquals(5, ChartResolution.FIVE_MIN.minutes)
    }

    @Test
    fun `ChartResolution FIFTEEN_MIN has minutes 15`() {
        assertEquals(15, ChartResolution.FIFTEEN_MIN.minutes)
    }

    @Test
    fun `ChartResolution THIRTY_MIN has minutes 30`() {
        assertEquals(30, ChartResolution.THIRTY_MIN.minutes)
    }

    @Test
    fun `ChartResolution HOURLY has minutes 60`() {
        assertEquals(60, ChartResolution.HOURLY.minutes)
    }

    @Test
    fun `ChartResolution labels are non-empty`() {
        ChartResolution.entries.forEach { res ->
            assertTrue("Label should not be blank for $res", res.label.isNotBlank())
        }
    }
}
