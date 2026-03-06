// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ManualSessionOverride
import com.podometer.data.db.SensorWindow
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [mergeSessionOverrides].
 */
class MergeSessionOverridesUseCaseTest {

    private val hour = 3_600_000L

    private fun session(
        activity: ActivityState,
        startTime: Long,
        endTime: Long?,
        id: Int = 1,
    ) = ActivitySession(
        activity = activity,
        startTime = startTime,
        endTime = endTime,
        startTransitionId = id,
        isManualOverride = false,
    )

    private fun override(
        activity: String,
        startTime: Long,
        endTime: Long,
        id: Long = 1,
    ) = ManualSessionOverride(
        id = id,
        startTime = startTime,
        endTime = endTime,
        activity = activity,
        date = "2026-03-05",
    )

    @Test
    fun `no overrides returns recomputed sessions unchanged`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(sessions, emptyList())
        assertEquals(sessions, result)
    }

    @Test
    fun `override replaces overlapping recomputed session`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour),
        )
        val overrides = listOf(
            override("CYCLING", 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(1, result.size)
        assertEquals(ActivityState.CYCLING, result[0].activity)
        assertTrue(result[0].isManualOverride)
    }

    @Test
    fun `override replaces partially overlapping session`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 11 * hour),
        )
        val overrides = listOf(
            override("CYCLING", 10 * hour, 12 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        // Walking session is removed because it overlaps
        assertEquals(1, result.size)
        assertEquals(ActivityState.CYCLING, result[0].activity)
    }

    @Test
    fun `non-overlapping sessions are preserved`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour, id = 1),
            session(ActivityState.WALKING, 14 * hour, 15 * hour, id = 2),
        )
        val overrides = listOf(
            override("CYCLING", 11 * hour, 12 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(3, result.size)
        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(ActivityState.CYCLING, result[1].activity)
        assertEquals(ActivityState.WALKING, result[2].activity)
    }

    @Test
    fun `result is sorted by start time`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 14 * hour, 15 * hour),
        )
        val overrides = listOf(
            override("CYCLING", 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(2, result.size)
        assertTrue(result[0].startTime < result[1].startTime)
    }

    @Test
    fun `multiple overrides replace multiple sessions`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour, id = 1),
            session(ActivityState.CYCLING, 11 * hour, 12 * hour, id = 2),
        )
        val overrides = listOf(
            override("CYCLING", 9 * hour, 10 * hour, id = 1),
            override("WALKING", 11 * hour, 12 * hour, id = 2),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(2, result.size)
        assertEquals(ActivityState.CYCLING, result[0].activity) // was walking
        assertEquals(ActivityState.WALKING, result[1].activity) // was cycling
        assertTrue(result.all { it.isManualOverride })
    }

    @Test
    fun `override for ongoing session works`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 14 * hour, null),
        )
        val overrides = listOf(
            override("CYCLING", 14 * hour, 15 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(1, result.size)
        assertEquals(ActivityState.CYCLING, result[0].activity)
    }

    @Test
    fun `STILL override suppresses detected session without appearing in result`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour),
        )
        val overrides = listOf(
            override("STILL", 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertTrue("STILL override should suppress the session entirely", result.isEmpty())
    }

    @Test
    fun `STILL override only suppresses overlapping sessions`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour, id = 1),
            session(ActivityState.WALKING, 14 * hour, 15 * hour, id = 2),
        )
        val overrides = listOf(
            override("STILL", 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(sessions, overrides)
        assertEquals(1, result.size)
        assertEquals(14 * hour, result[0].startTime)
    }

    private fun window(timestamp: Long, stepCount: Int) = SensorWindow(
        timestamp = timestamp,
        magnitudeVariance = 0.0,
        stepFrequencyHz = 0.0,
        stepCount = stepCount,
    )

    @Test
    fun `override session gets step count from sensor windows`() {
        val sessions = listOf(
            session(ActivityState.WALKING, 9 * hour, 10 * hour),
        )
        val overrides = listOf(
            override("CYCLING", 9 * hour, 10 * hour),
        )
        val windows = listOf(
            window(9 * hour, 50),
            window(9 * hour + 30_000L, 60),
            window(10 * hour, 100), // outside override range
        )
        val result = mergeSessionOverrides(sessions, overrides, windows)
        assertEquals(1, result.size)
        assertEquals(110, result[0].stepCount)
    }

    @Test
    fun `new activity gets step count from sensor windows in range`() {
        val overrides = listOf(
            override("WALKING", 12 * hour, 13 * hour),
        )
        val windows = listOf(
            window(11 * hour, 30), // outside range
            window(12 * hour, 40),
            window(12 * hour + 30_000L, 50),
            window(13 * hour, 20), // outside range (at endTime, exclusive)
        )
        val result = mergeSessionOverrides(emptyList(), overrides, windows)
        assertEquals(1, result.size)
        assertEquals(90, result[0].stepCount)
    }

    @Test
    fun `override without windows has zero step count`() {
        val overrides = listOf(
            override("WALKING", 9 * hour, 10 * hour),
        )
        val result = mergeSessionOverrides(emptyList(), overrides)
        assertEquals(1, result.size)
        assertEquals(0, result[0].stepCount)
    }
}
