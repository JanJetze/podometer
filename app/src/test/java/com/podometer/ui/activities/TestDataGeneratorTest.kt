// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import com.podometer.domain.model.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [TestDataGenerator].
 *
 * Verifies determinism, basic invariants, and that different dates produce
 * different data patterns.
 */
class TestDataGeneratorTest {

    private val date1 = LocalDate.of(2026, 3, 1) // Sunday
    private val date2 = LocalDate.of(2026, 3, 2) // Monday

    // ─── Determinism ────────────────────────────────────────────────────

    @Test
    fun `generateWindows is deterministic for same date`() {
        val a = TestDataGenerator.generateWindows(date1)
        val b = TestDataGenerator.generateWindows(date1)
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (wa, wb) ->
            assertEquals(wa.timestamp, wb.timestamp)
            assertEquals(wa.stepCount, wb.stepCount)
        }
    }

    @Test
    fun `generateSessions is deterministic for same date`() {
        val a = TestDataGenerator.generateSessions(date1)
        val b = TestDataGenerator.generateSessions(date1)
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (sa, sb) ->
            assertEquals(sa.activity, sb.activity)
            assertEquals(sa.startTime, sb.startTime)
            assertEquals(sa.endTime, sb.endTime)
        }
    }

    // ─── Variation across dates ─────────────────────────────────────────

    @Test
    fun `different dates produce different sessions`() {
        val s1 = TestDataGenerator.generateSessions(date1)
        val s2 = TestDataGenerator.generateSessions(date2)
        // Different dates should have different session counts or timings
        val timings1 = s1.map { it.startTime }
        val timings2 = s2.map { it.startTime }
        assertFalse(
            "Sessions for different dates should differ",
            s1.size == s2.size && timings1 == timings2,
        )
    }

    @Test
    fun `different dates produce different window step patterns`() {
        val w1 = TestDataGenerator.generateWindows(date1)
        val w2 = TestDataGenerator.generateWindows(date2)
        val steps1 = w1.map { it.stepCount }
        val steps2 = w2.map { it.stepCount }
        assertFalse(
            "Windows for different dates should have different step patterns",
            steps1 == steps2,
        )
    }

    // ─── Basic invariants ───────────────────────────────────────────────

    @Test
    fun `generateWindows produces non-empty output`() {
        val windows = TestDataGenerator.generateWindows(date1)
        assertTrue("Should produce windows", windows.isNotEmpty())
    }

    @Test
    fun `generateWindows timestamps are chronologically ordered`() {
        val windows = TestDataGenerator.generateWindows(date1)
        for (i in 1 until windows.size) {
            assertTrue(
                "Window $i timestamp should be >= window ${i - 1}",
                windows[i].timestamp >= windows[i - 1].timestamp,
            )
        }
    }

    @Test
    fun `generateWindows step counts are non-negative`() {
        val windows = TestDataGenerator.generateWindows(date1)
        windows.forEach { w ->
            assertTrue("Step count should be >= 0, got ${w.stepCount}", w.stepCount >= 0)
        }
    }

    @Test
    fun `generateSessions produces non-empty output`() {
        val sessions = TestDataGenerator.generateSessions(date1)
        assertTrue("Should produce sessions", sessions.isNotEmpty())
    }

    @Test
    fun `generateSessions are chronologically ordered`() {
        val sessions = TestDataGenerator.generateSessions(date1)
        for (i in 1 until sessions.size) {
            assertTrue(
                "Session $i should start after session ${i - 1}",
                sessions[i].startTime >= sessions[i - 1].startTime,
            )
        }
    }

    @Test
    fun `generateSessions all have end times`() {
        val sessions = TestDataGenerator.generateSessions(date1)
        sessions.forEach { s ->
            assertTrue("All test sessions should have an endTime", s.endTime != null)
        }
    }

    @Test
    fun `generateSessions endTime is after startTime`() {
        val sessions = TestDataGenerator.generateSessions(date1)
        sessions.forEach { s ->
            assertTrue(
                "endTime (${s.endTime}) should be > startTime (${s.startTime})",
                s.endTime!! > s.startTime,
            )
        }
    }

    @Test
    fun `generateSessions walking sessions have positive step counts`() {
        val sessions = TestDataGenerator.generateSessions(date1)
        sessions.filter { it.activity == ActivityState.WALKING }.forEach { s ->
            assertTrue("Walking session should have steps > 0, got ${s.stepCount}", s.stepCount > 0)
        }
    }

    // ─── Weekly summaries ───────────────────────────────────────────────

    @Test
    fun `generateWeeklySummaries has entries up to today`() {
        val summaries = TestDataGenerator.generateWeeklySummaries()
        val today = LocalDate.now()
        val expectedDays = today.dayOfWeek.value
        assertEquals(expectedDays, summaries.size)
    }

    @Test
    fun `generateWeeklySummaries have positive step counts`() {
        val summaries = TestDataGenerator.generateWeeklySummaries()
        summaries.forEach { s ->
            assertTrue("Step count should be > 0, got ${s.totalSteps}", s.totalSteps > 0)
        }
    }

    // ─── Day type coverage ──────────────────────────────────────────────

    @Test
    fun `all 7 day types produce valid data`() {
        // 7 consecutive days covers all day types
        val baseDate = LocalDate.of(2026, 1, 1)
        for (i in 0L..6L) {
            val date = baseDate.plusDays(i)
            val sessions = TestDataGenerator.generateSessions(date)
            val windows = TestDataGenerator.generateWindows(date)
            assertTrue("Day type ${i}: should have sessions", sessions.isNotEmpty())
            assertTrue("Day type ${i}: should have windows", windows.isNotEmpty())
        }
    }
}
