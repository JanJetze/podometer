// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
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
        private val todayStepsFlow: Flow<Int?> = flowOf(null),
        private val hourlyAggregatesFlow: Flow<List<HourlyStepAggregate>> = flowOf(emptyList()),
        private val dailySummaryFlow: Flow<DailySummary?> = flowOf(null),
        private val weeklyFlow: Flow<List<DailySummary>> = flowOf(emptyList()),
        private val stepsForHour: Int? = null,
        private val totalStepsSnapshot: Int? = null,
    ) : StepDao {
        var insertedAggregate: HourlyStepAggregate? = null
        var upsertedSummary: DailySummary? = null
        var upsertedAggregate: HourlyStepAggregate? = null
        var upsertedStepsAndDistanceDate: String? = null
        var upsertedStepsAndDistanceTotalSteps: Int? = null
        var upsertedStepsAndDistanceTotalDistance: Float? = null
        var addedWalkingMinutesDate: String? = null
        var addedWalkingMinutes: Int? = null
        var addedCyclingMinutesDate: String? = null
        var addedCyclingMinutes: Int? = null

        override fun getTodayHourlyAggregates(todayStart: Long): Flow<List<HourlyStepAggregate>> =
            hourlyAggregatesFlow

        override fun getTodayTotalSteps(todayStart: Long): Flow<Int?> = todayStepsFlow

        override fun getDailySummary(date: String): Flow<DailySummary?> = dailySummaryFlow

        override fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>> =
            weeklyFlow

        override suspend fun getStepsForHour(hourTimestamp: Long): Int? = stepsForHour

        override suspend fun getTodayTotalStepsSnapshot(todayStart: Long): Int? = totalStepsSnapshot

        override suspend fun deleteHourlyAggregateByTimestamp(hourTimestamp: Long) = Unit

        override suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate) {
            insertedAggregate = aggregate
        }

        override suspend fun upsertHourlyAggregate(aggregate: HourlyStepAggregate) {
            upsertedAggregate = aggregate
        }

        override suspend fun upsertDailySummary(summary: DailySummary) {
            upsertedSummary = summary
        }

        override suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) {
            upsertedStepsAndDistanceDate = date
            upsertedStepsAndDistanceTotalSteps = totalSteps
            upsertedStepsAndDistanceTotalDistance = totalDistance
        }

        override suspend fun addWalkingMinutes(date: String, minutes: Int) {
            addedWalkingMinutesDate = date
            addedWalkingMinutes = minutes
        }

        override suspend fun addCyclingMinutes(date: String, minutes: Int) {
            addedCyclingMinutesDate = date
            addedCyclingMinutes = minutes
        }

        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()

        override suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> = emptyList()

        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }

        override suspend fun insertAllHourlyAggregates(aggregates: List<HourlyStepAggregate>) { }
    }

    private class FakeActivityTransitionDao(
        private val transitionsFlow: Flow<List<ActivityTransition>> = flowOf(emptyList()),
    ) : ActivityTransitionDao {
        var insertedTransition: ActivityTransition? = null
        var updatedTransition: ActivityTransition? = null

        override fun getTodayTransitions(todayStart: Long): Flow<List<ActivityTransition>> =
            transitionsFlow

        override suspend fun insertTransition(transition: ActivityTransition) {
            insertedTransition = transition
        }

        override suspend fun updateTransition(transition: ActivityTransition) {
            updatedTransition = transition
        }

        override suspend fun getAllTransitions(): List<ActivityTransition> = emptyList()

        override suspend fun getNextTransitionAfter(afterTimestamp: Long): ActivityTransition? = null

        override suspend fun insertAllTransitions(transitions: List<ActivityTransition>) { }
    }

    // ─── getTodaySteps: null → 0 mapping ────────────────────────────────────

    @Test
    fun `getTodaySteps maps null to 0`() = runTest {
        val dao = FakeStepDao(todayStepsFlow = flowOf(null))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodaySteps().first()

        assertEquals(0, result)
    }

    @Test
    fun `getTodaySteps passes through non-null value`() = runTest {
        val dao = FakeStepDao(todayStepsFlow = flowOf(500))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodaySteps().first()

        assertEquals(500, result)
    }

    @Test
    fun `getTodaySteps maps 0 DAO result to 0`() = runTest {
        val dao = FakeStepDao(todayStepsFlow = flowOf(0))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodaySteps().first()

        assertEquals(0, result)
    }

    // ─── getTodayHourlyAggregates ────────────────────────────────────────────

    @Test
    fun `getTodayHourlyAggregates delegates to StepDao`() = runTest {
        val aggregates = listOf(
            HourlyStepAggregate(id = 1, timestamp = 1000L, stepCountDelta = 42, detectedActivity = "WALKING"),
        )
        val dao = FakeStepDao(hourlyAggregatesFlow = flowOf(aggregates))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodayHourlyAggregates().first()

        assertEquals(aggregates, result)
    }

    @Test
    fun `getTodayHourlyAggregates returns empty list when no rows`() = runTest {
        val dao = FakeStepDao(hourlyAggregatesFlow = flowOf(emptyList()))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodayHourlyAggregates().first()

        assertTrue(result.isEmpty())
    }

    // ─── getDailySummary ─────────────────────────────────────────────────────

    @Test
    fun `getDailySummary returns null when no row exists`() = runTest {
        val dao = FakeStepDao(dailySummaryFlow = flowOf(null))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getDailySummary("2026-02-17").first()

        assertEquals(null, result)
    }

    @Test
    fun `getDailySummary delegates to StepDao`() = runTest {
        val summary = DailySummary("2026-02-17", 8500, 6.2f, 70, 20)
        val dao = FakeStepDao(dailySummaryFlow = flowOf(summary))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getDailySummary("2026-02-17").first()

        assertEquals(summary, result)
    }

    // ─── getWeeklyDailySummaries ─────────────────────────────────────────────

    @Test
    fun `getWeeklyDailySummaries delegates to StepDao`() = runTest {
        val summaries = listOf(
            DailySummary("2026-02-10", 7000, 5.0f, 60, 0),
            DailySummary("2026-02-11", 9000, 6.5f, 80, 15),
        )
        val dao = FakeStepDao(weeklyFlow = flowOf(summaries))
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getWeeklyDailySummaries("2026-02-10", "2026-02-16").first()

        assertEquals(summaries, result)
    }

    // ─── getTodayTransitions ─────────────────────────────────────────────────

    @Test
    fun `getTodayTransitions delegates to ActivityTransitionDao`() = runTest {
        val transitions = listOf(
            ActivityTransition(id = 1, timestamp = 5000L, fromActivity = "STILL", toActivity = "WALKING"),
        )
        val transitionDao = FakeActivityTransitionDao(transitionsFlow = flowOf(transitions))
        val repo = StepRepository(FakeStepDao(), transitionDao)

        val result = repo.getTodayTransitions().first()

        assertEquals(transitions, result)
    }

    @Test
    fun `getTodayTransitions returns empty list when no rows`() = runTest {
        val transitionDao = FakeActivityTransitionDao(transitionsFlow = flowOf(emptyList()))
        val repo = StepRepository(FakeStepDao(), transitionDao)

        val result = repo.getTodayTransitions().first()

        assertTrue(result.isEmpty())
    }

    // ─── Recovery read methods ────────────────────────────────────────────────

    @Test
    fun `getStepsForHour maps null DAO result to 0`() = runTest {
        val dao = FakeStepDao(stepsForHour = null)
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getStepsForHour(1000L)

        assertEquals(0, result)
    }

    @Test
    fun `getStepsForHour passes through non-null DAO result`() = runTest {
        val dao = FakeStepDao(stepsForHour = 250)
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getStepsForHour(1000L)

        assertEquals(250, result)
    }

    @Test
    fun `getTodayTotalStepsSnapshot maps null DAO result to 0`() = runTest {
        val dao = FakeStepDao(totalStepsSnapshot = null)
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodayTotalStepsSnapshot()

        assertEquals(0, result)
    }

    @Test
    fun `getTodayTotalStepsSnapshot passes through non-null DAO result`() = runTest {
        val dao = FakeStepDao(totalStepsSnapshot = 1500)
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        val result = repo.getTodayTotalStepsSnapshot()

        assertEquals(1500, result)
    }

    // ─── Write methods ───────────────────────────────────────────────────────

    @Test
    fun `upsertHourlyAggregate delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())
        val aggregate = HourlyStepAggregate(timestamp = 2000L, stepCountDelta = 200, detectedActivity = "WALKING")

        repo.upsertHourlyAggregate(aggregate)

        assertEquals(aggregate, dao.upsertedAggregate)
    }

    @Test
    fun `insertHourlyAggregate delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())
        val aggregate = HourlyStepAggregate(timestamp = 1000L, stepCountDelta = 100, detectedActivity = "WALKING")

        repo.insertHourlyAggregate(aggregate)

        assertEquals(aggregate, dao.insertedAggregate)
    }

    @Test
    fun `upsertDailySummary delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())
        val summary = DailySummary("2026-02-17", 5000, 3.5f, 45, 10)

        repo.upsertDailySummary(summary)

        assertEquals(summary, dao.upsertedSummary)
    }

    @Test
    fun `insertTransition delegates to ActivityTransitionDao`() = runTest {
        val transitionDao = FakeActivityTransitionDao()
        val repo = StepRepository(FakeStepDao(), transitionDao)
        val transition = ActivityTransition(timestamp = 2000L, fromActivity = "STILL", toActivity = "WALKING")

        repo.insertTransition(transition)

        assertEquals(transition, transitionDao.insertedTransition)
    }

    @Test
    fun `updateTransition delegates to ActivityTransitionDao`() = runTest {
        val transitionDao = FakeActivityTransitionDao()
        val repo = StepRepository(FakeStepDao(), transitionDao)
        val transition = ActivityTransition(id = 1, timestamp = 3000L, fromActivity = "WALKING", toActivity = "CYCLING")

        repo.updateTransition(transition)

        assertEquals(transition, transitionDao.updatedTransition)
    }

    // ─── getTodayStartMillis ─────────────────────────────────────────────────

    @Test
    fun `getTodayStartMillis returns positive epoch millis`() {
        val repo = StepRepository(FakeStepDao(), FakeActivityTransitionDao())

        val millis = repo.getTodayStartMillis()

        assertTrue("Expected positive millis, got $millis", millis > 0L)
    }

    @Test
    fun `getTodayStartMillis returns value in the past or present`() {
        val repo = StepRepository(FakeStepDao(), FakeActivityTransitionDao())
        val before = System.currentTimeMillis()

        val millis = repo.getTodayStartMillis()

        assertTrue("Midnight should be before or equal to now", millis <= before)
    }

    // ─── upsertStepsAndDistance ──────────────────────────────────────────────

    @Test
    fun `upsertStepsAndDistance delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        repo.upsertStepsAndDistance(date = "2026-02-27", totalSteps = 3000, totalDistance = 2.25f)

        assertEquals("2026-02-27", dao.upsertedStepsAndDistanceDate)
        assertEquals(3000, dao.upsertedStepsAndDistanceTotalSteps)
        assertEquals(2.25f, dao.upsertedStepsAndDistanceTotalDistance!!, 0.0001f)
    }

    // ─── addWalkingMinutes ───────────────────────────────────────────────────

    @Test
    fun `addWalkingMinutes delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        repo.addWalkingMinutes(date = "2026-02-27", minutes = 60)

        assertEquals("2026-02-27", dao.addedWalkingMinutesDate)
        assertEquals(60, dao.addedWalkingMinutes)
    }

    // ─── addCyclingMinutes ───────────────────────────────────────────────────

    @Test
    fun `addCyclingMinutes delegates to StepDao`() = runTest {
        val dao = FakeStepDao()
        val repo = StepRepository(dao, FakeActivityTransitionDao())

        repo.addCyclingMinutes(date = "2026-02-27", minutes = 60)

        assertEquals("2026-02-27", dao.addedCyclingMinutesDate)
        assertEquals(60, dao.addedCyclingMinutes)
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
