// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivitySession] and [buildActivitySessions].
 */
class ActivitySessionTest {

    private val now = 100_000L

    // ─── Empty input ──────────────────────────────────────────────────────────

    @Test
    fun `empty transitions produce empty sessions`() {
        val result = buildActivitySessions(emptyList(), now)
        assertTrue(result.isEmpty())
    }

    // ─── Single transition ────────────────────────────────────────────────────

    @Test
    fun `single WALKING transition produces one ongoing session`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(1, result.size)
        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(10_000L, result[0].startTime)
        assertNull(result[0].endTime)
        assertEquals(1, result[0].startTransitionId)
        assertFalse(result[0].isManualOverride)
    }

    @Test
    fun `single STILL transition produces no sessions`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.WALKING, toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertTrue(result.isEmpty())
    }

    // ─── Walking then still ───────────────────────────────────────────────────

    @Test
    fun `WALKING then STILL produces one closed session`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2, timestamp = 20_000L,
                fromActivity = ActivityState.WALKING, toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(1, result.size)
        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(10_000L, result[0].startTime)
        assertEquals(20_000L, result[0].endTime)
    }

    // ─── Multiple activities ──────────────────────────────────────────────────

    @Test
    fun `WALKING then CYCLING then STILL produces two sessions`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2, timestamp = 20_000L,
                fromActivity = ActivityState.WALKING, toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 3, timestamp = 30_000L,
                fromActivity = ActivityState.CYCLING, toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(2, result.size)

        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(10_000L, result[0].startTime)
        assertEquals(20_000L, result[0].endTime)

        assertEquals(ActivityState.CYCLING, result[1].activity)
        assertEquals(20_000L, result[1].startTime)
        assertEquals(30_000L, result[1].endTime)
    }

    @Test
    fun `last active session is ongoing`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2, timestamp = 20_000L,
                fromActivity = ActivityState.WALKING, toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 3, timestamp = 50_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(2, result.size)

        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(20_000L, result[0].endTime)

        assertEquals(ActivityState.CYCLING, result[1].activity)
        assertNull(result[1].endTime)
    }

    // ─── Manual override flag ─────────────────────────────────────────────────

    @Test
    fun `manual override flag is propagated from transition`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.CYCLING,
                isManualOverride = true,
            ),
            TransitionEvent(
                id = 2, timestamp = 20_000L,
                fromActivity = ActivityState.CYCLING, toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(1, result.size)
        assertTrue(result[0].isManualOverride)
        assertEquals(1, result[0].startTransitionId)
    }

    // ─── Back-to-back activities ──────────────────────────────────────────────

    @Test
    fun `direct WALKING to CYCLING without STILL gap produces adjacent sessions`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1, timestamp = 10_000L,
                fromActivity = ActivityState.STILL, toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2, timestamp = 20_000L,
                fromActivity = ActivityState.WALKING, toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
        )
        val result = buildActivitySessions(transitions, now)
        assertEquals(2, result.size)

        assertEquals(ActivityState.WALKING, result[0].activity)
        assertEquals(10_000L, result[0].startTime)
        assertEquals(20_000L, result[0].endTime)

        assertEquals(ActivityState.CYCLING, result[1].activity)
        assertEquals(20_000L, result[1].startTime)
        assertNull(result[1].endTime)
    }
}
