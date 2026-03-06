// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ManualSessionOverride
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
}
