// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [buildStepGraphData] and related pure functions.
 */
class StepGraphTest {

    private val dayStart = 0L
    private val dayEnd = 86_400_000L
    private val bucketMs = 300_000L // 5 min

    private fun window(ts: Long, steps: Int) = StepWindowPoint(
        id = 0,
        timestamp = ts,
        stepCount = steps,
    )

    // ─── Empty input ────────────────────────────────────────────────────────

    @Test
    fun `empty windows produce empty points`() {
        val data = buildStepGraphData(emptyList(), bucketMs, dayStart, dayEnd)
        assertTrue(data.points.isEmpty())
        assertEquals(0, data.maxCumulative)
        assertEquals(0, data.maxBucket)
    }

    // ─── Single bucket ──────────────────────────────────────────────────────

    @Test
    fun `single bucket sums steps correctly`() {
        val windows = listOf(
            window(1_000L, 5),
            window(31_000L, 3),
            window(61_000L, 2),
        )
        val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)
        assertEquals(1, data.points.size)
        assertEquals(10, data.points[0].bucketSteps)
        assertEquals(10, data.points[0].cumulativeSteps)
    }

    // ─── Multiple buckets ───────────────────────────────────────────────────

    @Test
    fun `multiple buckets have correct cumulative totals`() {
        val windows = listOf(
            window(0L, 5),            // bucket 0
            window(30_000L, 0),
            window(60_000L, 0),
            window(90_000L, 0),
            window(120_000L, 0),
            window(150_000L, 0),
            window(180_000L, 0),
            window(210_000L, 0),
            window(240_000L, 0),
            window(270_000L, 0),
            window(300_000L, 3),      // bucket 1
            window(330_000L, 0),
            window(360_000L, 0),
            window(390_000L, 0),
            window(420_000L, 0),
            window(450_000L, 0),
            window(480_000L, 0),
            window(510_000L, 0),
            window(540_000L, 0),
            window(570_000L, 0),
            window(600_000L, 7),      // bucket 2
        )
        val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)
        assertEquals(3, data.points.size)
        assertEquals(5, data.points[0].cumulativeSteps)
        assertEquals(8, data.points[1].cumulativeSteps)
        assertEquals(15, data.points[2].cumulativeSteps)
    }

    @Test
    fun `cumulative total is monotonically increasing`() {
        val windows = (0L until 3_600_000L step 30_000L).mapIndexed { i, ts ->
            window(ts, (i % 5) + 1)
        }
        val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)
        for (i in 1 until data.points.size) {
            assertTrue(
                "Point $i cumulative (${data.points[i].cumulativeSteps}) should be >= point ${i - 1} (${data.points[i - 1].cumulativeSteps})",
                data.points[i].cumulativeSteps >= data.points[i - 1].cumulativeSteps,
            )
        }
    }

    // ─── Max values ─────────────────────────────────────────────────────────

    @Test
    fun `maxCumulative is the last point's cumulative`() {
        val windows = listOf(
            window(0L, 10),
            window(300_000L, 20),
            window(600_000L, 5),
        )
        val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)
        assertEquals(35, data.maxCumulative)
    }

    @Test
    fun `maxBucket is the highest single bucket`() {
        val windows = listOf(
            window(0L, 10),
            window(30_000L, 0),
            window(60_000L, 0),
            window(90_000L, 0),
            window(120_000L, 0),
            window(150_000L, 0),
            window(180_000L, 0),
            window(210_000L, 0),
            window(240_000L, 0),
            window(270_000L, 0),
            window(300_000L, 20),
            window(330_000L, 0),
            window(360_000L, 0),
            window(390_000L, 0),
            window(420_000L, 0),
            window(450_000L, 0),
            window(480_000L, 0),
            window(510_000L, 0),
            window(540_000L, 0),
            window(570_000L, 0),
            window(600_000L, 5),
        )
        val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)
        assertEquals(20, data.maxBucket)
    }

    // ─── Bucket size changes ────────────────────────────────────────────────

    @Test
    fun `larger bucket size aggregates more windows`() {
        val windows = listOf(
            window(0L, 5),
            window(300_000L, 3),
            window(600_000L, 7),
        )
        // 5-min buckets: 3 buckets
        val data5m = buildStepGraphData(windows, 300_000L, dayStart, dayEnd)
        assertEquals(3, data5m.points.size)

        // 15-min buckets: 1 bucket (all within first 15 min)
        val data15m = buildStepGraphData(windows, 900_000L, dayStart, dayEnd)
        assertEquals(1, data15m.points.size)
        assertEquals(15, data15m.points[0].bucketSteps)
    }

    // ─── niceAxisMax ─────────────────────────────────────────────────────

    @Test
    fun `niceAxisMax returns 100 for zero`() {
        assertEquals(100, niceAxisMax(0))
    }

    @Test
    fun `niceAxisMax returns 100 for negative`() {
        assertEquals(100, niceAxisMax(-5))
    }

    @Test
    fun `niceAxisMax rounds small values to 1-2-5 multiples`() {
        assertEquals(1, niceAxisMax(1))
        assertEquals(2, niceAxisMax(2))
        assertEquals(5, niceAxisMax(3))
        assertEquals(5, niceAxisMax(5))
        assertEquals(10, niceAxisMax(6))
        assertEquals(10, niceAxisMax(9))
        assertEquals(10, niceAxisMax(10))
    }

    @Test
    fun `niceAxisMax rounds hundreds correctly`() {
        assertEquals(100, niceAxisMax(100))
        assertEquals(200, niceAxisMax(150))
        assertEquals(200, niceAxisMax(200))
        assertEquals(500, niceAxisMax(300))
        assertEquals(500, niceAxisMax(500))
        assertEquals(1000, niceAxisMax(600))
    }

    @Test
    fun `niceAxisMax rounds thousands correctly`() {
        assertEquals(1000, niceAxisMax(1000))
        assertEquals(2000, niceAxisMax(1500))
        assertEquals(5000, niceAxisMax(3000))
        assertEquals(10000, niceAxisMax(8000))
        assertEquals(20000, niceAxisMax(15000))
    }

    @Test
    fun `niceAxisMax result is always gte input`() {
        val testValues = listOf(1, 7, 42, 99, 123, 567, 1234, 5678, 9999, 15000)
        for (v in testValues) {
            assertTrue("niceAxisMax($v) = ${niceAxisMax(v)} should be >= $v", niceAxisMax(v) >= v)
        }
    }

    // ─── formatAxisLabel ─────────────────────────────────────────────────

    @Test
    fun `formatAxisLabel shows plain number below 1000`() {
        assertEquals("0", formatAxisLabel(0))
        assertEquals("500", formatAxisLabel(500))
        assertEquals("999", formatAxisLabel(999))
    }

    @Test
    fun `formatAxisLabel shows thousands with decimal`() {
        assertEquals("1k", formatAxisLabel(1000))
        assertEquals("1.5k", formatAxisLabel(1500))
        assertEquals("2.5k", formatAxisLabel(2500))
        assertEquals("9.9k", formatAxisLabel(9900))
    }

    @Test
    fun `formatAxisLabel shows plain k for 10000 and above`() {
        assertEquals("10k", formatAxisLabel(10000))
        assertEquals("15k", formatAxisLabel(15000))
        assertEquals("20k", formatAxisLabel(20000))
    }
}
