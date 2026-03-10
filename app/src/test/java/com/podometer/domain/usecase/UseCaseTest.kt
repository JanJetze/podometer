// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.StepDao
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for domain use cases.
 *
 * Uses fake DAOs (following the same pattern as repository tests) to build
 * real repository instances, verifying mapping logic, default values, and
 * computations without Android dependencies.
 */
class UseCaseTest {

    // ─── Fake DataStore ──────────────────────────────────────────────────────

    private class FakeDataStore(
        private val initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {
        private var current: Preferences = initial
        override val data: Flow<Preferences> get() = flowOf(current)
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(current)
            current = updated
            return updated
        }
    }

    // ─── Fake DAOs ───────────────────────────────────────────────────────────

    private class FakeStepDao(
        private val todayStepsFlow: Flow<Int?> = flowOf(null),
        private val weeklyFlow: Flow<List<DailySummary>> = flowOf(emptyList()),
    ) : StepDao {
        override fun getTodayHourlyAggregates(todayStart: Long): Flow<List<HourlyStepAggregate>> =
            flowOf(emptyList())

        override fun getTodayTotalSteps(todayStart: Long): Flow<Int?> = todayStepsFlow

        override fun getDailySummary(date: String): Flow<DailySummary?> = flowOf(null)

        override fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>> =
            weeklyFlow

        override suspend fun getStepsForHour(hourTimestamp: Long): Int? = null
        override suspend fun getTodayTotalStepsSnapshot(todayStart: Long): Int? = null
        override suspend fun deleteHourlyAggregateByTimestamp(hourTimestamp: Long) = Unit
        override suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate) = Unit
        override suspend fun upsertHourlyAggregate(aggregate: HourlyStepAggregate) = Unit
        override suspend fun upsertDailySummary(summary: DailySummary) = Unit
        override suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) = Unit
        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()
        override suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> = emptyList()
        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }
        override suspend fun insertAllHourlyAggregates(aggregates: List<HourlyStepAggregate>) { }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun stepRepo(
        todaySteps: Int? = null,
        weekly: List<DailySummary> = emptyList(),
    ): StepRepository = StepRepository(
        FakeStepDao(
            todayStepsFlow = flowOf(todaySteps),
            weeklyFlow = flowOf(weekly),
        ),
    )

    private fun preferencesManager(): PreferencesManager =
        PreferencesManager(FakeDataStore())

    // ─── GetTodayStepsUseCase ────────────────────────────────────────────────

    @Test
    fun `GetTodayStepsUseCase emits StepData with default goal 10000`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 5000), preferencesManager())

        val result = useCase().first()

        assertEquals(5000, result.steps)
        assertEquals(10_000, result.goal)
    }

    @Test
    fun `GetTodayStepsUseCase computes progressPercent correctly`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 5000), preferencesManager())

        val result = useCase().first()

        // 5000 / 10000 * 100 = 50.0
        assertEquals(50.0f, result.progressPercent, 0.001f)
    }

    @Test
    fun `GetTodayStepsUseCase allows progressPercent to exceed 100 when goal surpassed`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 15_000), preferencesManager())

        val result = useCase().first()

        // 15000 / 10000 * 100 = 150.0%
        assertEquals(150.0f, result.progressPercent, 0.001f)
    }

    @Test
    fun `GetTodayStepsUseCase computes distanceKm with default stride`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 1000), preferencesManager())

        val result = useCase().first()

        // 1000 steps * 0.00075 km = 0.75 km
        assertEquals(0.75f, result.distanceKm, 0.0001f)
    }

    @Test
    fun `GetTodayStepsUseCase computes distanceKm with custom stride from preferences`() = runTest {
        val pm = preferencesManager()
        pm.setStrideLengthKm(0.001f)
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 1000), pm)

        val result = useCase().first()

        // 1000 steps * 0.001 km = 1.0 km
        assertEquals(1.0f, result.distanceKm, 0.0001f)
    }

    @Test
    fun `GetTodayStepsUseCase emits zero StepData when steps are zero`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 0), preferencesManager())

        val result = useCase().first()

        assertEquals(0, result.steps)
        assertEquals(0.0f, result.progressPercent, 0.001f)
        assertEquals(0.0f, result.distanceKm, 0.0001f)
    }

    @Test
    fun `GetTodayStepsUseCase emits exactly 100 percent at goal steps`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 10_000), preferencesManager())

        val result = useCase().first()

        assertEquals(100.0f, result.progressPercent, 0.001f)
    }

    @Test
    fun `GetTodayStepsUseCase maps null step count to 0 steps`() = runTest {
        // Null from DAO is mapped to 0 inside StepRepository.getTodaySteps()
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = null), preferencesManager())

        val result = useCase().first()

        assertEquals(0, result.steps)
    }

    @Test
    fun `GetTodayStepsUseCase reflects custom daily step goal from preferences`() = runTest {
        val pm = preferencesManager()
        pm.setDailyStepGoal(5_000)
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 2_500), pm)

        val result = useCase().first()

        assertEquals(5_000, result.goal)
        // 2500 / 5000 * 100 = 50.0
        assertEquals(50.0f, result.progressPercent, 0.001f)
    }

    @Test
    fun `GetTodayStepsUseCase progressPercent uses custom goal not hardcoded 10000`() = runTest {
        val pm = preferencesManager()
        pm.setDailyStepGoal(20_000)
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 5_000), pm)

        val result = useCase().first()

        assertEquals(20_000, result.goal)
        // 5000 / 20000 * 100 = 25.0
        assertEquals(25.0f, result.progressPercent, 0.001f)
    }

    @Test
    fun `GetTodayStepsUseCase falls back to DEFAULT_DAILY_STEP_GOAL when goal is zero`() = runTest {
        // Simulate a corrupt/zero goal stored in DataStore (cannot happen via the normal
        // setDailyStepGoal path which validates goal > 0, but the use case must be
        // arithmetically safe on its own).
        //
        // We bypass setDailyStepGoal's validation by seeding the DataStore directly with 0.
        val goalKey = intPreferencesKey("daily_step_goal")
        val pm = PreferencesManager(FakeDataStore(preferencesOf(goalKey to 0)))
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 5_000), pm)

        val result = useCase().first()

        // goal stored in StepData should be the safe fallback (10_000)
        assertEquals(10_000, result.goal)
        // progressPercent must be computed with the safe goal, not trigger divide-by-zero
        assertEquals(50.0f, result.progressPercent, 0.001f)
    }

    // ─── GetWeeklyStepsUseCase ───────────────────────────────────────────────

    @Test
    fun `GetWeeklyStepsUseCase maps DB DailySummary to domain DaySummary`() = runTest {
        val dbSummaries = listOf(
            DailySummary(
                date = "2026-02-12",
                totalSteps = 8_500,
                totalDistance = 6.375f,
            ),
        )
        val useCase = GetWeeklyStepsUseCaseImpl(stepRepo(weekly = dbSummaries))

        val result = useCase().first()

        assertEquals(1, result.size)
        val day = result[0]
        assertEquals("2026-02-12", day.date)
        assertEquals(8_500, day.totalSteps)
        assertEquals(6.375f, day.totalDistanceKm, 0.0001f)
    }

    @Test
    fun `GetWeeklyStepsUseCase returns empty list when repository is empty`() = runTest {
        val useCase = GetWeeklyStepsUseCaseImpl(stepRepo(weekly = emptyList()))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetWeeklyStepsUseCase maps multiple summaries preserving order`() = runTest {
        val dbSummaries = listOf(
            DailySummary("2026-02-10", 7_000, 5.25f),
            DailySummary("2026-02-11", 9_000, 6.75f),
            DailySummary("2026-02-12", 5_000, 3.75f),
        )
        val useCase = GetWeeklyStepsUseCaseImpl(stepRepo(weekly = dbSummaries))

        val result = useCase().first()

        assertEquals(3, result.size)
        assertEquals("2026-02-10", result[0].date)
        assertEquals("2026-02-11", result[1].date)
        assertEquals("2026-02-12", result[2].date)
    }
}
