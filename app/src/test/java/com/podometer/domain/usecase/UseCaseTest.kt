// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.CyclingSession
import com.podometer.data.db.CyclingSessionDao
import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.StepDao
import com.podometer.data.repository.CyclingRepository
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.ActivityState
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

        override suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate) = Unit
        override suspend fun upsertDailySummary(summary: DailySummary) = Unit
        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()
        override suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> = emptyList()
    }

    private class FakeActivityTransitionDao(
        private val transitionsFlow: Flow<List<ActivityTransition>> = flowOf(emptyList()),
    ) : ActivityTransitionDao {
        var updatedTransition: ActivityTransition? = null

        override fun getTodayTransitions(todayStart: Long): Flow<List<ActivityTransition>> =
            transitionsFlow

        override suspend fun insertTransition(transition: ActivityTransition) = Unit

        override suspend fun updateTransition(transition: ActivityTransition) {
            updatedTransition = transition
        }

        override suspend fun getAllTransitions(): List<ActivityTransition> = emptyList()
    }

    private class FakeCyclingSessionDao(
        private val sessionsFlow: Flow<List<CyclingSession>> = flowOf(emptyList()),
    ) : CyclingSessionDao {
        override fun getTodaySessions(todayStart: Long): Flow<List<CyclingSession>> = sessionsFlow
        override suspend fun insertSession(session: CyclingSession): Long = 0L
        override suspend fun updateSession(session: CyclingSession) = Unit
        override suspend fun deleteSession(session: CyclingSession) = Unit
        override suspend fun getAllSessions(): List<CyclingSession> = emptyList()

        override suspend fun getOngoingSession(): CyclingSession? = null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun stepRepo(
        todaySteps: Int? = null,
        weekly: List<DailySummary> = emptyList(),
        transitions: List<ActivityTransition> = emptyList(),
        transitionDao: FakeActivityTransitionDao = FakeActivityTransitionDao(flowOf(transitions)),
    ): StepRepository = StepRepository(
        FakeStepDao(
            todayStepsFlow = flowOf(todaySteps),
            weeklyFlow = flowOf(weekly),
        ),
        transitionDao,
    )

    private fun cyclingRepo(sessions: List<CyclingSession> = emptyList()): CyclingRepository =
        CyclingRepository(FakeCyclingSessionDao(flowOf(sessions)))

    private fun preferencesManager(): PreferencesManager =
        PreferencesManager(FakeDataStore())

    // ─── ActivityState.fromString ────────────────────────────────────────────

    @Test
    fun `ActivityState fromString converts WALKING`() {
        assertEquals(ActivityState.WALKING, ActivityState.fromString("WALKING"))
    }

    @Test
    fun `ActivityState fromString converts CYCLING`() {
        assertEquals(ActivityState.CYCLING, ActivityState.fromString("CYCLING"))
    }

    @Test
    fun `ActivityState fromString converts STILL`() {
        assertEquals(ActivityState.STILL, ActivityState.fromString("STILL"))
    }

    @Test
    fun `ActivityState fromString is case-insensitive`() {
        assertEquals(ActivityState.WALKING, ActivityState.fromString("walking"))
        assertEquals(ActivityState.CYCLING, ActivityState.fromString("Cycling"))
        assertEquals(ActivityState.STILL, ActivityState.fromString("still"))
    }

    @Test
    fun `ActivityState fromString defaults to STILL for unknown value`() {
        assertEquals(ActivityState.STILL, ActivityState.fromString("UNKNOWN"))
        assertEquals(ActivityState.STILL, ActivityState.fromString(""))
    }

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
    fun `GetTodayStepsUseCase caps progressPercent at 100`() = runTest {
        val useCase = GetTodayStepsUseCaseImpl(stepRepo(todaySteps = 15_000), preferencesManager())

        val result = useCase().first()

        assertEquals(100.0f, result.progressPercent, 0.001f)
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

    // ─── GetWeeklyStepsUseCase ───────────────────────────────────────────────

    @Test
    fun `GetWeeklyStepsUseCase maps DB DailySummary to domain DaySummary`() = runTest {
        val dbSummaries = listOf(
            DailySummary(
                date = "2026-02-12",
                totalSteps = 8_500,
                totalDistance = 6.375f,
                walkingMinutes = 70,
                cyclingMinutes = 20,
            ),
        )
        val useCase = GetWeeklyStepsUseCaseImpl(stepRepo(weekly = dbSummaries))

        val result = useCase().first()

        assertEquals(1, result.size)
        val day = result[0]
        assertEquals("2026-02-12", day.date)
        assertEquals(8_500, day.totalSteps)
        assertEquals(6.375f, day.totalDistanceKm, 0.0001f)
        assertEquals(70, day.walkingMinutes)
        assertEquals(20, day.cyclingMinutes)
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
            DailySummary("2026-02-10", 7_000, 5.25f, 60, 0),
            DailySummary("2026-02-11", 9_000, 6.75f, 80, 15),
            DailySummary("2026-02-12", 5_000, 3.75f, 45, 0),
        )
        val useCase = GetWeeklyStepsUseCaseImpl(stepRepo(weekly = dbSummaries))

        val result = useCase().first()

        assertEquals(3, result.size)
        assertEquals("2026-02-10", result[0].date)
        assertEquals("2026-02-11", result[1].date)
        assertEquals("2026-02-12", result[2].date)
    }

    // ─── GetTodayTransitionsUseCase ──────────────────────────────────────────

    @Test
    fun `GetTodayTransitionsUseCase maps DB ActivityTransition to domain TransitionEvent`() = runTest {
        val dbTransitions = listOf(
            ActivityTransition(
                id = 1,
                timestamp = 1_700_000_000_000L,
                fromActivity = "STILL",
                toActivity = "WALKING",
                isManualOverride = false,
            ),
        )
        val dao = FakeActivityTransitionDao(flowOf(dbTransitions))
        val useCase = GetTodayTransitionsUseCaseImpl(StepRepository(FakeStepDao(), dao))

        val result = useCase().first()

        assertEquals(1, result.size)
        val event = result[0]
        assertEquals(1, event.id)
        assertEquals(1_700_000_000_000L, event.timestamp)
        assertEquals(ActivityState.STILL, event.fromActivity)
        assertEquals(ActivityState.WALKING, event.toActivity)
        assertEquals(false, event.isManualOverride)
    }

    @Test
    fun `GetTodayTransitionsUseCase converts CYCLING activity string`() = runTest {
        val dbTransitions = listOf(
            ActivityTransition(
                id = 2,
                timestamp = 1_700_000_001_000L,
                fromActivity = "WALKING",
                toActivity = "CYCLING",
                isManualOverride = true,
            ),
        )
        val dao = FakeActivityTransitionDao(flowOf(dbTransitions))
        val useCase = GetTodayTransitionsUseCaseImpl(StepRepository(FakeStepDao(), dao))

        val result = useCase().first()

        assertEquals(ActivityState.WALKING, result[0].fromActivity)
        assertEquals(ActivityState.CYCLING, result[0].toActivity)
        assertEquals(true, result[0].isManualOverride)
    }

    @Test
    fun `GetTodayTransitionsUseCase returns empty list when no transitions`() = runTest {
        val useCase = GetTodayTransitionsUseCaseImpl(stepRepo(transitions = emptyList()))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetTodayTransitionsUseCase preserves all transition fields`() = runTest {
        val dbTransitions = listOf(
            ActivityTransition(id = 5, timestamp = 9_000L, fromActivity = "CYCLING", toActivity = "STILL", isManualOverride = true),
        )
        val dao = FakeActivityTransitionDao(flowOf(dbTransitions))
        val useCase = GetTodayTransitionsUseCaseImpl(StepRepository(FakeStepDao(), dao))

        val result = useCase().first()

        val event = result[0]
        assertEquals(5, event.id)
        assertEquals(9_000L, event.timestamp)
        assertEquals(ActivityState.CYCLING, event.fromActivity)
        assertEquals(ActivityState.STILL, event.toActivity)
        assertEquals(true, event.isManualOverride)
    }

    // ─── GetTodayCyclingSessionsUseCase ──────────────────────────────────────

    @Test
    fun `GetTodayCyclingSessionsUseCase returns sessions from repository`() = runTest {
        val sessions = listOf(
            CyclingSession(id = 1, startTime = 1_000L, endTime = 2_000L, durationMinutes = 16),
        )
        val useCase = GetTodayCyclingSessionsUseCaseImpl(cyclingRepo(sessions))

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
        assertEquals(1_000L, result[0].startTime)
        assertEquals(2_000L, result[0].endTime)
        assertEquals(16, result[0].durationMinutes)
    }

    @Test
    fun `GetTodayCyclingSessionsUseCase returns empty list when no sessions`() = runTest {
        val useCase = GetTodayCyclingSessionsUseCaseImpl(cyclingRepo(emptyList()))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    // ─── OverrideActivityUseCase ─────────────────────────────────────────────

    @Test
    fun `OverrideActivityUseCase updates toActivity and sets isManualOverride true`() = runTest {
        val originalTransition = ActivityTransition(
            id = 3,
            timestamp = 5_000L,
            fromActivity = "STILL",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(originalTransition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        val useCase = OverrideActivityUseCaseImpl(repo)

        useCase(originalTransition, ActivityState.CYCLING)

        val updated = transitionDao.updatedTransition
        assertEquals(3, updated?.id)
        assertEquals(5_000L, updated?.timestamp)
        assertEquals("STILL", updated?.fromActivity)
        assertEquals("CYCLING", updated?.toActivity)
        assertEquals(true, updated?.isManualOverride)
    }

    @Test
    fun `OverrideActivityUseCase sets isManualOverride to true`() = runTest {
        val originalTransition = ActivityTransition(
            id = 7,
            timestamp = 1_000L,
            fromActivity = "WALKING",
            toActivity = "STILL",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(originalTransition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        val useCase = OverrideActivityUseCaseImpl(repo)

        useCase(originalTransition, ActivityState.WALKING)

        assertEquals(true, transitionDao.updatedTransition?.isManualOverride)
    }

    @Test
    fun `OverrideActivityUseCase preserves all other fields from the original transition`() = runTest {
        val originalTransition = ActivityTransition(
            id = 11,
            timestamp = 99_000L,
            fromActivity = "CYCLING",
            toActivity = "STILL",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(originalTransition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        val useCase = OverrideActivityUseCaseImpl(repo)

        useCase(originalTransition, ActivityState.WALKING)

        val updated = transitionDao.updatedTransition
        assertEquals(11, updated?.id)
        assertEquals(99_000L, updated?.timestamp)
        assertEquals("CYCLING", updated?.fromActivity)
        // Only toActivity changes
        assertEquals("WALKING", updated?.toActivity)
    }
}
