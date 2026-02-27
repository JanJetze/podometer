// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.CyclingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CyclingSessionManager].
 *
 * All tests run on the JVM — no Android framework dependencies.
 */
class CyclingSessionManagerTest {

    private lateinit var manager: CyclingSessionManager

    @Before
    fun setUp() {
        manager = CyclingSessionManager()
    }

    // ─── startSession ─────────────────────────────────────────────────────────

    @Test
    fun `startSession returns session with correct startTime`() {
        val startTime = 1_000_000L
        val session = manager.startSession(startTime)
        assertEquals(startTime, session.startTime)
    }

    @Test
    fun `startSession returns session with durationMinutes zero`() {
        val session = manager.startSession(1_000_000L)
        assertEquals(0, session.durationMinutes)
    }

    @Test
    fun `startSession returns session with null endTime`() {
        val session = manager.startSession(1_000_000L)
        assertNull(session.endTime)
    }

    @Test
    fun `startSession returns session with isManualOverride false`() {
        val session = manager.startSession(1_000_000L)
        assertFalse(session.isManualOverride)
    }

    @Test
    fun `startSession returns session with id zero (not yet persisted)`() {
        val session = manager.startSession(1_000_000L)
        assertEquals(0, session.id)
    }

    // ─── isStepCountingPaused ─────────────────────────────────────────────────

    @Test
    fun `isStepCountingPaused is false initially`() {
        assertFalse(manager.isStepCountingPaused)
    }

    @Test
    fun `isStepCountingPaused is true immediately after startSession (before setOngoingSessionId)`() {
        // This is the race-window fix: step counting must be paused as soon as
        // startSession() is called, not only after setOngoingSessionId() returns.
        manager.startSession(1_000_000L)
        assertTrue(manager.isStepCountingPaused)
    }

    @Test
    fun `isStepCountingPaused is true after startSession and setOngoingSessionId`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(42)
        assertTrue(manager.isStepCountingPaused)
    }

    @Test
    fun `isStepCountingPaused is false after endSession`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(42)
        manager.endSession(2_000_000L)
        assertFalse(manager.isStepCountingPaused)
    }

    @Test
    fun `isStepCountingPaused is false after reset`() {
        manager.startSession(1_000_000L)
        manager.reset()
        assertFalse(manager.isStepCountingPaused)
    }

    @Test
    fun `isStepCountingPaused is false after endSession called without setOngoingSessionId`() {
        // Edge case: startSession called, DB insert happens, but endSession fires
        // before setOngoingSessionId — sessionActive must still be cleared.
        manager.startSession(1_000_000L)
        // endSession returns null (no ongoingSessionId), but sessionActive should still reset
        manager.endSession(2_000_000L)
        assertFalse(manager.isStepCountingPaused)
    }

    // ─── endSession ───────────────────────────────────────────────────────────

    @Test
    fun `endSession returns null when no ongoing session`() {
        val result = manager.endSession(2_000_000L)
        assertNull(result)
    }

    @Test
    fun `endSession returns session with correct endTime`() {
        val startTime = 1_000_000L
        val endTime = 2_000_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(7)
        val result = manager.endSession(endTime)
        assertNotNull(result)
        assertEquals(endTime, result!!.endTime)
    }

    @Test
    fun `endSession returns session with correct startTime`() {
        val startTime = 1_000_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(7)
        val result = manager.endSession(3_000_000L)
        assertNotNull(result)
        assertEquals(startTime, result!!.startTime)
    }

    @Test
    fun `endSession returns session with correct ongoing session id`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(99)
        val result = manager.endSession(2_000_000L)
        assertNotNull(result)
        assertEquals(99, result!!.id)
    }

    @Test
    fun `endSession computes durationMinutes correctly for 5 minutes`() {
        val startTime = 0L
        val endTime = 5 * 60 * 1_000L // 5 minutes in ms
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(5, result.durationMinutes)
    }

    @Test
    fun `endSession computes durationMinutes correctly for 90 seconds`() {
        // 90 seconds should round to 2 minutes (nearest minute)
        val startTime = 0L
        val endTime = 90_000L // 1.5 minutes
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(2, result.durationMinutes)
    }

    @Test
    fun `endSession computes durationMinutes rounds to nearest minute — rounds down`() {
        // 89 seconds: 89/60 = 1.483... rounds to 1
        val startTime = 0L
        val endTime = 89_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(1, result.durationMinutes)
    }

    @Test
    fun `endSession computes durationMinutes for less than 30 seconds rounds to zero`() {
        // 29 seconds: rounds to 0
        val startTime = 0L
        val endTime = 29_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(0, result.durationMinutes)
    }

    @Test
    fun `endSession computes durationMinutes for exactly 60 seconds`() {
        val startTime = 0L
        val endTime = 60_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(1, result.durationMinutes)
    }

    @Test
    fun `endSession computes durationMinutes for large duration`() {
        // 3 hours = 180 minutes
        val startTime = 0L
        val endTime = 3 * 60 * 60 * 1_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        assertEquals(180, result.durationMinutes)
    }

    @Test
    fun `endSession returns null on second call (no ongoing session)`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(1)
        manager.endSession(2_000_000L)
        val secondResult = manager.endSession(3_000_000L)
        assertNull(secondResult)
    }

    // ─── setOngoingSessionId ──────────────────────────────────────────────────

    @Test
    fun `setOngoingSessionId stores the db-generated id`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(123)
        assertEquals(123, manager.getOngoingSessionId())
    }

    // ─── hasOngoingSession ────────────────────────────────────────────────────

    @Test
    fun `hasOngoingSession returns false initially`() {
        assertFalse(manager.hasOngoingSession())
    }

    @Test
    fun `hasOngoingSession returns false after only startSession (before setOngoingSessionId)`() {
        manager.startSession(1_000_000L)
        assertFalse(manager.hasOngoingSession())
    }

    @Test
    fun `hasOngoingSession returns true after startSession and setOngoingSessionId`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(5)
        assertTrue(manager.hasOngoingSession())
    }

    @Test
    fun `hasOngoingSession returns false after endSession`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(5)
        manager.endSession(2_000_000L)
        assertFalse(manager.hasOngoingSession())
    }

    // ─── getOngoingSessionId ─────────────────────────────────────────────────

    @Test
    fun `getOngoingSessionId returns null initially`() {
        assertNull(manager.getOngoingSessionId())
    }

    @Test
    fun `getOngoingSessionId returns null after endSession`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(77)
        manager.endSession(2_000_000L)
        assertNull(manager.getOngoingSessionId())
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears ongoing session`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(10)
        manager.reset()
        assertFalse(manager.hasOngoingSession())
    }

    @Test
    fun `reset clears isStepCountingPaused`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(10)
        manager.reset()
        assertFalse(manager.isStepCountingPaused)
    }

    @Test
    fun `reset clears ongoingSessionId`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(10)
        manager.reset()
        assertNull(manager.getOngoingSessionId())
    }

    @Test
    fun `reset allows new session to start cleanly`() {
        manager.startSession(1_000_000L)
        manager.setOngoingSessionId(10)
        manager.reset()

        val newSession = manager.startSession(5_000_000L)
        manager.setOngoingSessionId(11)
        assertTrue(manager.hasOngoingSession())
        assertEquals(5_000_000L, newSession.startTime)
    }

    // ─── MIN_SESSION_DURATION_MS threshold ────────────────────────────────────

    @Test
    fun `MIN_SESSION_DURATION_MS constant is 60 seconds`() {
        assertEquals(60_000L, CyclingSessionManager.MIN_SESSION_DURATION_MS)
    }

    @Test
    fun `endSession returns session for duration exactly at threshold (60 s)`() {
        // A session of exactly MIN_SESSION_DURATION_MS should NOT be considered short.
        manager.startSession(0L)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(60_000L)
        assertNotNull(result)
    }

    @Test
    fun `endSession returns session for duration one ms below threshold (59999 ms)`() {
        // Duration is below MIN_SESSION_DURATION_MS — session is still returned so
        // the caller can identify and delete it from the DB.
        manager.startSession(0L)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(59_999L)
        assertNotNull(result)
    }

    @Test
    fun `endSession returned session durationMs is below threshold for 30 second session`() {
        // Caller must check endTime - startTime against MIN_SESSION_DURATION_MS.
        val startTime = 0L
        val endTime = 30_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        val durationMs = result.endTime!! - result.startTime
        assertTrue(durationMs < CyclingSessionManager.MIN_SESSION_DURATION_MS)
    }

    @Test
    fun `endSession returned session durationMs is at or above threshold for 60 second session`() {
        val startTime = 0L
        val endTime = 60_000L
        manager.startSession(startTime)
        manager.setOngoingSessionId(1)
        val result = manager.endSession(endTime)!!
        val durationMs = result.endTime!! - result.startTime
        assertTrue(durationMs >= CyclingSessionManager.MIN_SESSION_DURATION_MS)
    }

    @Test
    fun `endSession clears state even for short session below threshold`() {
        manager.startSession(0L)
        manager.setOngoingSessionId(1)
        manager.endSession(10_000L) // 10 seconds, well below threshold
        assertFalse(manager.hasOngoingSession())
        assertFalse(manager.isStepCountingPaused)
        assertNull(manager.getOngoingSessionId())
    }

    // ─── Thread safety ────────────────────────────────────────────────────────

    @Test
    fun `concurrent startSession and endSession do not throw`() {
        val threads = (1..10).map { i ->
            Thread {
                manager.startSession((i * 1_000_000L))
                manager.setOngoingSessionId(i)
                manager.endSession((i * 1_000_000L + 500_000L))
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // No assertion — just verifying no exception is thrown
    }

    // ─── Class package ────────────────────────────────────────────────────────

    @Test
    fun `CyclingSessionManager is in service package`() {
        assertEquals("com.podometer.service", CyclingSessionManager::class.java.packageName)
    }
}
