// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StepRepository].
 *
 * Uses fake DAO implementations to avoid Android dependencies while
 * verifying delegation and the null→0 mapping in [StepRepository.getTodaySteps].
 */
class StepRepositoryTest {

    // ─── Fakes ──────────────────────────────────────────────────────────────

    private class FakeStepDao(
        private val dailySummaryFlow: Flow<DailySummary?> = flowOf(null),
        private val weeklyFlow: Flow<List<DailySummary>> = flowOf(emptyList()),
        private val totalStepsSnapshot: Int? = null,
    ) : StepDao {
        var upsertedSummary: DailySummary? = null
        var upsertedStepsAndDistanceDate: String? = null
        var upsertedStepsAndDistanceTotalSteps: Int? = null
        var upsertedStepsAndDistanceTotalDistance: Float? = null

        override fun getDailySummary(date: String): Flow<DailySummary?> = dailySummaryFlow

        override fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>> =
            weeklyFlow

        override suspend fun getTodayTotalStepsSnapshot(date: String): Int? = totalStepsSnapshot

        override suspend fun upsertDailySummary(summary: DailySummary) {
            upsertedSummary = summary
        }

        override suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) {
            upsertedStepsAndDistanceDate = date
            upsertedStepsAndDistanceTotalSteps = totalSteps
            upsertedStepsAndDistanceTotalDistance = totalDistance
        }

        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()

        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }
    }

    // ─── getTodaySteps ───────────────────────────────────────────────────────

    @Test
    fun `getTodaySteps maps null to 0`() = runTest {
        val dao = FakeStepDao(dailySummaryFlow = flowOf(null))
        val repo = StepRepository(dao)

        val result = repo.getTodaySteps().first()

        assertEquals(0, result)
    }

    @Test
    fun `getTodaySteps passes through totalSteps from DailySummary`() = runTest {
        val summary = DailySummary(date = "2026-03-10", totalSteps = 500, totalDistance = 0.375f)
        val dao = FakeStepDao(dailySummaryFlow = flowOf(summary))
        val repo = StepRepository(dao)

        val result = repo.getTodaySteps().first()

        assertEquals(500, result)
    }

    @Test
    fun `getTodaySteps maps 0 totalSteps to 0`() = runTest {
        val summary = DailySummary(date = "2026-03-10", totalSteps = 0, totalDistance = 0f)
        val dao = FakeStepDao(dailySummaryFlow = flowOf(summary))
        val repo = StepRepository(dao)

        val result = repo.getTodaySteps().first()

        assertEquals(0, result)
    }

    // ─── getDailySummary ─────────────────────────────────────────────────────

    @Test
    fun `getDailySummary returns null when no row exists`() = runTest {
        val dao = FakeStepDao(dailySummaryFlow = flowOf(null))
        val repo = StepRepository(dao)

        val result = repo.getDailySummary("2026-02-17").first()

        assertEquals(null, result)
    }

    @Test
    fun `getDailySummary delegates to StepDao`() = runTest {
        val summary = DailySummary("2026-02-17", 8500, 6.2f)
        val dao = FakeStepDao(dailySummaryFlow = flowOf(summary))
        val repo = StepRepository(dao)

        val result = repo.getDailySummary("2026-02-17").first()

        assertEquals(summary, result)
    }

    // ─── getWeeklyDailySummaries ─────────────────────────────────────────────

    @Test
    fun `getWeeklyDailySummaries delegates to StepDao`() = runTest {
        val summaries = listOf(
            DailySummary("2026-02-10", 7000, 5.0f),
            DailySummary("2026-02-11", 9000, 6.5f),
        )
        val dao = FakeStepDao(weeklyFlow = flowOf(summaries))
        val repo = StepRepository(dao)

        val result = repo.getWeeklyDailySummaries("2026-02-10", "2026-02-16").first()

        assertEquals(summaries, result)
    }

    // ─── Recovery read methods ────────────────────────────────────────────────

    @Test
    fun `getTodayTotalStepsSnapshot maps null DAO result to 0`() = runTest {
        val dao = FakeStepDao(totalStepsSnapshot = null)
        val repo = StepRepository(dao)

        val result = repo.getTodayTotalStepsSnapshot()

        assertEquals(0, result)
    }

    @Test
    fun `getTodayTotalStepsSnapshot passes through non-null DAO result`() = runTest {
        val dao = FakeStepDao(totalStepsSnapshot = 1500)
        val repo = StepRepository(dao)

        val result = repo.getTodayTotalStepsSnapshot()

        assertEquals(1500, result)
    }

    // ─── Write methods ───────────────────────────────────────────────────────

    @Test
    fun `upsertDailySummary delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao)
        val summary = DailySummary("2026-02-17", 5000, 3.5f)

        repo.upsertDailySummary(summary)

        assertEquals(summary, dao.upsertedSummary)
    }

    // ─── getTodayStartMillis ─────────────────────────────────────────────────

    @Test
    fun `getTodayStartMillis returns positive epoch millis`() {
        val repo = StepRepository(FakeStepDao())

        val millis = repo.getTodayStartMillis()

        assertTrue("Expected positive millis, got $millis", millis > 0L)
    }

    @Test
    fun `getTodayStartMillis returns value in the past or present`() {
        val repo = StepRepository(FakeStepDao())
        val before = System.currentTimeMillis()

        val millis = repo.getTodayStartMillis()

        assertTrue("Midnight should be before or equal to now", millis <= before)
    }

    // ─── upsertStepsAndDistance ──────────────────────────────────────────────

    @Test
    fun `upsertStepsAndDistance delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao)

        repo.upsertStepsAndDistance(date = "2026-02-27", totalSteps = 3000, totalDistance = 2.25f)

        assertEquals("2026-02-27", dao.upsertedStepsAndDistanceDate)
        assertEquals(3000, dao.upsertedStepsAndDistanceTotalSteps)
        assertEquals(2.25f, dao.upsertedStepsAndDistanceTotalDistance!!, 0.0001f)
    }

    // ─── Class existence ────────────────────────────────────────────────────

    @Test
    fun `StepRepository class exists in repository package`() {
        val clazz = StepRepository::class.java
        assertNotNull(clazz)
        assertTrue(
            "StepRepository must be in com.podometer.data.repository",
            clazz.name == "com.podometer.data.repository.StepRepository",
        )
    }
}
