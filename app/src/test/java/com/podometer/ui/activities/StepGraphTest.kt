// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import com.podometer.data.db.SensorWindow
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [buildStepGraphData] and related pure functions.
 */
class StepGraphTest {

    private val dayStart = 0L
    private val dayEnd = 86_400_000L
    private val bucketMs = 300_000L // 5 min

    private fun window(ts: Long, steps: Int) = SensorWindow(
        id = 0,
        timestamp = ts,
        magnitudeVariance = 0.0,
        stepFrequencyHz = 0.0,
        stepCount = steps,
    )

    private fun session(
        activity: ActivityState,
        startTime: Long,
        endTime: Long?,
    ) = ActivitySession(
        activity = activity,
        startTime = startTime,
        endTime = endTime,
        startTransitionId = 1,
        isManualOverride = false,
    )

    // ─── Empty input ────────────────────────────────────────────────────────

    @Test
    fun `empty windows produce empty points`() {
        val data = buildStepGraphData(emptyList(), emptyList(), bucketMs, dayStart, dayEnd)
        assertTrue(data.points.isEmpty())
        assertEquals(0, data.maxCumulative)
        assertEquals(0, data.maxBucket)
    }

    @Test
    fun `empty windows with sessions still produces activity regions`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 3_600_000L, 7_200_000L),
        )
        val data = buildStepGraphData(emptyList(), sessions, bucketMs, dayStart, dayEnd)
        assertTrue(data.points.isEmpty())
        assertEquals(1, data.activityRegions.size)
        assertEquals(ActivityState.WALKING, data.activityRegions[0].activity)
    }

    // ─── Single bucket ──────────────────────────────────────────────────────

    @Test
    fun `single bucket sums steps correctly`() {
        val windows = listOf(
            window(1_000L, 5),
            window(31_000L, 3),
            window(61_000L, 2),
        )
        val data = buildStepGraphData(windows, emptyList(), bucketMs, dayStart, dayEnd)
        assertEquals(1, data.points.size)
        assertEquals(10, data.points[0].bucketSteps)
        assertEquals(10, data.points[0].cumulativeSteps)
    }

    // ─── Multiple buckets ───────────────────────────────────────────────────

    @Test
    fun `multiple buckets have correct cumulative totals`() {
        val windows = listOf(
            window(0L, 5),           // bucket 0
            window(300_000L, 3),     // bucket 1
            window(600_000L, 7),     // bucket 2
        )
        val data = buildStepGraphData(windows, emptyList(), bucketMs, dayStart, dayEnd)
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
        val data = buildStepGraphData(windows, emptyList(), bucketMs, dayStart, dayEnd)
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
        val data = buildStepGraphData(windows, emptyList(), bucketMs, dayStart, dayEnd)
        assertEquals(35, data.maxCumulative)
    }

    @Test
    fun `maxBucket is the highest single bucket`() {
        val windows = listOf(
            window(0L, 10),
            window(300_000L, 20),
            window(600_000L, 5),
        )
        val data = buildStepGraphData(windows, emptyList(), bucketMs, dayStart, dayEnd)
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
        val data5m = buildStepGraphData(windows, emptyList(), 300_000L, dayStart, dayEnd)
        assertEquals(3, data5m.points.size)

        // 15-min buckets: 1 bucket (all within first 15 min)
        val data15m = buildStepGraphData(windows, emptyList(), 900_000L, dayStart, dayEnd)
        assertEquals(1, data15m.points.size)
        assertEquals(15, data15m.points[0].bucketSteps)
    }

    // ─── Activity regions ───────────────────────────────────────────────────

    @Test
    fun `activity regions map correctly from sessions`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L), // 6am-9am
            session(ActivityState.CYCLING, 32_400_000L, 43_200_000L), // 9am-12pm
        )
        val data = buildStepGraphData(emptyList(), sessions, bucketMs, dayStart, dayEnd)
        assertEquals(2, data.activityRegions.size)
        assertEquals(ActivityState.WALKING, data.activityRegions[0].activity)
        assertEquals(ActivityState.CYCLING, data.activityRegions[1].activity)

        // 6am = 6/24 = 0.25
        assertEquals(0.25f, data.activityRegions[0].startFraction, 0.001f)
        // 9am = 9/24 = 0.375
        assertEquals(0.375f, data.activityRegions[0].endFraction, 0.001f)
    }

    @Test
    fun `STILL sessions are excluded from regions`() {
        val sessions = listOf(
            session(ActivityState.STILL, 0L, 21_600_000L),
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L),
        )
        val data = buildStepGraphData(emptyList(), sessions, bucketMs, dayStart, dayEnd)
        assertEquals(1, data.activityRegions.size)
        assertEquals(ActivityState.WALKING, data.activityRegions[0].activity)
    }

    // ─── Dominant activity per bucket ───────────────────────────────────────

    @Test
    fun `dominant activity resolves to session with most overlap`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 0L, 200_000L),    // 200s overlap with [0, 300s] bucket
            session(ActivityState.CYCLING, 200_000L, 500_000L), // 100s overlap with [0, 300s] bucket
        )
        val dominant = determineDominantActivity(0L, 300_000L, sessions)
        assertEquals(ActivityState.WALKING, dominant)
    }

    @Test
    fun `dominant activity is STILL when no sessions cover the range`() {
        val dominant = determineDominantActivity(0L, 300_000L, emptyList())
        assertEquals(ActivityState.STILL, dominant)
    }

    // ─── Activity markers ─────────────────────────────────────────────────

    @Test
    fun `markers are generated at session boundaries`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L), // 6am-9am
            session(ActivityState.CYCLING, 32_400_000L, 43_200_000L), // 9am-12pm
        )
        val markers = buildActivityMarkers(sessions, dayStart, dayEnd)
        // 2 sessions x 2 boundaries = 4 markers
        assertEquals(4, markers.size)
        // First marker: walking start at 6am = 0.25
        assertEquals(0.25f, markers[0].fraction, 0.001f)
        assertTrue(markers[0].isStart)
        assertEquals(ActivityState.WALKING, markers[0].activity)
        assertEquals(0, markers[0].sessionIndex)
        // Second marker: walking end / cycling start at 9am = 0.375
        assertEquals(0.375f, markers[1].fraction, 0.001f)
        assertFalse(markers[1].isStart) // walking end
        assertEquals(0.375f, markers[2].fraction, 0.001f)
        assertTrue(markers[2].isStart) // cycling start
    }

    @Test
    fun `markers exclude STILL sessions`() {
        val sessions = listOf(
            session(ActivityState.STILL, 0L, 21_600_000L),
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L),
        )
        val markers = buildActivityMarkers(sessions, dayStart, dayEnd)
        assertEquals(2, markers.size) // only walking start + end
        assertEquals(ActivityState.WALKING, markers[0].activity)
    }

    @Test
    fun `ongoing session has start marker but no end marker`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 21_600_000L, null), // ongoing
        )
        val markers = buildActivityMarkers(sessions, dayStart, dayEnd)
        assertEquals(1, markers.size)
        assertTrue(markers[0].isStart)
    }

    @Test
    fun `markers are sorted by fraction`() {
        val sessions = listOf(
            session(ActivityState.CYCLING, 43_200_000L, 54_000_000L), // 12pm-3pm
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L), // 6am-9am
        )
        val markers = buildActivityMarkers(sessions, dayStart, dayEnd)
        for (i in 1 until markers.size) {
            assertTrue(markers[i].fraction >= markers[i - 1].fraction)
        }
    }

    @Test
    fun `markers are included in StepGraphData`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 21_600_000L, 32_400_000L),
        )
        val data = buildStepGraphData(emptyList(), sessions, bucketMs, dayStart, dayEnd)
        assertEquals(2, data.markers.size)
    }

    // ─── findNearestMarker ──────────────────────────────────────────────────

    @Test
    fun `findNearestMarker returns marker within threshold`() {
        val markers = listOf(
            ActivityMarker(0.25f, ActivityState.WALKING, true, 0),
            ActivityMarker(0.375f, ActivityState.WALKING, false, 0),
        )
        val found = findNearestMarker(markers, 0.26f, 0.02f)
        assertEquals(markers[0], found)
    }

    @Test
    fun `findNearestMarker returns null when no marker within threshold`() {
        val markers = listOf(
            ActivityMarker(0.25f, ActivityState.WALKING, true, 0),
        )
        val found = findNearestMarker(markers, 0.5f, 0.02f)
        assertEquals(null, found)
    }

    @Test
    fun `findNearestMarker returns closest when multiple within threshold`() {
        val markers = listOf(
            ActivityMarker(0.25f, ActivityState.WALKING, true, 0),
            ActivityMarker(0.27f, ActivityState.CYCLING, true, 1),
        )
        val found = findNearestMarker(markers, 0.26f, 0.02f)
        assertEquals(markers[0], found) // 0.25 is closer to 0.26 than 0.27
    }

    // ─── Dominant activity ──────────────────────────────────────────────────

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

    // ─── Dominant activity ──────────────────────────────────────────────

    @Test
    fun `dominant activity assigned to graph points from sessions`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 0L, 300_000L),
            session(ActivityState.CYCLING, 300_000L, 600_000L),
        )
        val windows = listOf(
            window(100_000L, 5),   // in walking session
            window(400_000L, 3),   // in cycling session
        )
        val data = buildStepGraphData(windows, sessions, bucketMs, dayStart, dayEnd)
        assertEquals(2, data.points.size)
        assertEquals(ActivityState.WALKING, data.points[0].dominantActivity)
        assertEquals(ActivityState.CYCLING, data.points[1].dominantActivity)
    }
}
