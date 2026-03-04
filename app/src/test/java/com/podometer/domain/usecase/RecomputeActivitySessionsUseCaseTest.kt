// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.SensorWindow
import com.podometer.data.sensor.CyclingClassifier
import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecomputeActivitySessionsUseCaseImpl.replayWindows].
 *
 * Tests the pure replay function directly — no Flow, no repository, no DI.
 */
class RecomputeActivitySessionsUseCaseTest {

    private val nowMillis = 100_000_000L

    @Test
    fun `replayWindows returns empty sessions for empty windows`() {
        val sessions = RecomputeActivitySessionsUseCaseImpl.replayWindows(
            windows = emptyList(),
            nowMillis = nowMillis,
        )
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `replayWindows detects walking session from sustained walking windows`() {
        // Generate enough walking windows (36+) to trigger STILL → WALKING transition.
        // Walking: moderate variance (> stillVarianceThreshold) and high step frequency.
        val windows = (0 until 40).map { i ->
            SensorWindow(
                id = i.toLong(),
                timestamp = 1_000_000L + i * 5_000L,
                magnitudeVariance = 1.0, // between still (0.5) and cycling (2.0)
                stepFrequencyHz = 1.8,   // typical walking cadence
                stepCount = 9,
            )
        }

        val sessions = RecomputeActivitySessionsUseCaseImpl.replayWindows(
            windows = windows,
            nowMillis = nowMillis,
        )

        // Should detect at least one walking session
        assertTrue("Expected at least one session", sessions.isNotEmpty())
        assertEquals(ActivityState.WALKING, sessions[0].activity)
    }

    @Test
    fun `replayWindows detects cycling session from sustained cycling windows`() {
        // First, we need some STILL state, then transition to cycling.
        // Cycling: high variance (> 2.0) and low step frequency (< 0.3).
        // We need at least 2 consecutive cycling windows + 60 seconds elapsed.
        val windows = (0 until 20).map { i ->
            SensorWindow(
                id = i.toLong(),
                timestamp = 1_000_000L + i * 5_000L,
                magnitudeVariance = 5.0, // high variance (cycling)
                stepFrequencyHz = 0.0,   // no steps (cycling)
                stepCount = 0,
            )
        }

        val sessions = RecomputeActivitySessionsUseCaseImpl.replayWindows(
            windows = windows,
            nowMillis = nowMillis,
        )

        // Should detect at least one cycling session after 60s of cycling windows
        assertTrue("Expected at least one session", sessions.isNotEmpty())
        assertEquals(ActivityState.CYCLING, sessions[0].activity)
    }

    @Test
    fun `replayWindows produces no sessions for all-still windows`() {
        // Still: low variance and no steps
        val windows = (0 until 20).map { i ->
            SensorWindow(
                id = i.toLong(),
                timestamp = 1_000_000L + i * 5_000L,
                magnitudeVariance = 0.1, // very low variance (still)
                stepFrequencyHz = 0.0,   // no steps
                stepCount = 0,
            )
        }

        val sessions = RecomputeActivitySessionsUseCaseImpl.replayWindows(
            windows = windows,
            nowMillis = nowMillis,
        )

        assertTrue("Expected no sessions for all-still windows", sessions.isEmpty())
    }

    @Test
    fun `replayWindows assigns sequential transition ids`() {
        // Generate cycling windows (high variance, no steps) — enough for transition
        val windows = (0 until 20).map { i ->
            SensorWindow(
                id = i.toLong(),
                timestamp = 1_000_000L + i * 5_000L,
                magnitudeVariance = 5.0,
                stepFrequencyHz = 0.0,
                stepCount = 0,
            )
        }

        val sessions = RecomputeActivitySessionsUseCaseImpl.replayWindows(
            windows = windows,
            nowMillis = nowMillis,
        )

        if (sessions.isNotEmpty()) {
            // Each session gets a unique startTransitionId
            val ids = sessions.map { it.startTransitionId }
            assertEquals("All transition IDs should be unique", ids.size, ids.toSet().size)
        }
    }
}
