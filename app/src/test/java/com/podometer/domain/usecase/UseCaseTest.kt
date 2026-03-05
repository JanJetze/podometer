// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        override suspend fun addWalkingMinutes(date: String, minutes: Int) = Unit
        override suspend fun addCyclingMinutes(date: String, minutes: Int) = Unit
        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()
        override suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> = emptyList()
        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }
        override suspend fun insertAllHourlyAggregates(aggregates: List<HourlyStepAggregate>) { }
    }

    private class FakeActivityTransitionDao(
        private val transitionsFlow: Flow<List<ActivityTransition>> = flowOf(emptyList()),
        private val nextTransitionAfter: ActivityTransition? = null,
    ) : ActivityTransitionDao {
        var updatedTransition: ActivityTransition? = null

        override fun getTodayTransitions(todayStart: Long): Flow<List<ActivityTransition>> =
            transitionsFlow

        override suspend fun insertTransition(transition: ActivityTransition) = Unit

        override suspend fun updateTransition(transition: ActivityTransition) {
            updatedTransition = transition
        }

        override suspend fun getAllTransitions(): List<ActivityTransition> = emptyList()

        override suspend fun getNextTransitionAfter(afterTimestamp: Long): ActivityTransition? =
            nextTransitionAfter

        override suspend fun insertAllTransitions(transitions: List<ActivityTransition>) { }
    }

    private class FakeCyclingSessionDao(
        private val sessionsFlow: Flow<List<CyclingSession>> = flowOf(emptyList()),
        private val sessionCoveringTimestamp: CyclingSession? = null,
    ) : CyclingSessionDao {
        var insertedSession: CyclingSession? = null
        var updatedSession: CyclingSession? = null
        var deletedSession: CyclingSession? = null

        override fun getTodaySessions(todayStart: Long): Flow<List<CyclingSession>> = sessionsFlow
        override suspend fun insertSession(session: CyclingSession): Long {
            insertedSession = session
            return 0L
        }
        override suspend fun updateSession(session: CyclingSession) {
            updatedSession = session
        }
        override suspend fun deleteSession(session: CyclingSession) {
            deletedSession = session
        }
        override suspend fun getAllSessions(): List<CyclingSession> = emptyList()
        override suspend fun getOngoingSession(): CyclingSession? = null
        override suspend fun getSessionCoveringTimestamp(timestamp: Long): CyclingSession? =
            sessionCoveringTimestamp

        override suspend fun insertAllSessions(sessions: List<CyclingSession>) { }
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

    private fun stepRepoWithNextTransition(
        transitions: List<ActivityTransition> = emptyList(),
        nextTransitionAfter: ActivityTransition? = null,
    ): Pair<StepRepository, FakeActivityTransitionDao> {
        val dao = FakeActivityTransitionDao(flowOf(transitions), nextTransitionAfter)
        val repo = StepRepository(FakeStepDao(), dao)
        return repo to dao
    }

    private fun cyclingRepo(
        sessions: List<CyclingSession> = emptyList(),
        sessionCoveringTimestamp: CyclingSession? = null,
        daoOut: ((FakeCyclingSessionDao) -> Unit)? = null,
    ): CyclingRepository {
        val dao = FakeCyclingSessionDao(flowOf(sessions), sessionCoveringTimestamp)
        daoOut?.invoke(dao)
        return CyclingRepository(dao)
    }

    private fun preferencesManager(): PreferencesManager =
        PreferencesManager(FakeDataStore())

    /**
     * A [TransactionRunner] that executes the block directly without a real Room transaction.
     *
     * Used in JVM unit tests where no Room database is available. The logic under test is
     * identical to the production path; only the transaction boundary is elided.
     */
    private val noOpTransactionRunner: TransactionRunner = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

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
        val useCase = OverrideActivityUseCaseImpl(repo, cyclingRepo(), noOpTransactionRunner)

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
        val useCase = OverrideActivityUseCaseImpl(repo, cyclingRepo(), noOpTransactionRunner)

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
        val useCase = OverrideActivityUseCaseImpl(repo, cyclingRepo(), noOpTransactionRunner)

        useCase(originalTransition, ActivityState.WALKING)

        val updated = transitionDao.updatedTransition
        assertEquals(11, updated?.id)
        assertEquals(99_000L, updated?.timestamp)
        assertEquals("CYCLING", updated?.fromActivity)
        // Only toActivity changes
        assertEquals("WALKING", updated?.toActivity)
    }

    // ─── OverrideActivityUseCase — cycling session management ─────────────────

    @Test
    fun `OverrideActivityUseCase overriding to CYCLING creates a CyclingSession with isManualOverride true`() = runTest {
        val transition = ActivityTransition(
            id = 1,
            timestamp = 10_000L,
            fromActivity = "WALKING",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.CYCLING)

        val inserted = capturedDao?.insertedSession
        assertNotNull("Expected a CyclingSession to be inserted", inserted)
        assertEquals(10_000L, inserted?.startTime)
        assertEquals(true, inserted?.isManualOverride)
    }

    @Test
    fun `OverrideActivityUseCase overriding to CYCLING does not create session when already CYCLING`() = runTest {
        val transition = ActivityTransition(
            id = 2,
            timestamp = 20_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.CYCLING)

        assertNull(
            "Expected no CyclingSession to be inserted when transition was already CYCLING",
            capturedDao?.insertedSession,
        )
    }

    @Test
    fun `OverrideActivityUseCase overriding from CYCLING deletes the covering CyclingSession`() = runTest {
        val existingSession = CyclingSession(
            id = 7,
            startTime = 5_000L,
            endTime = 15_000L,
            durationMinutes = 10,
            isManualOverride = false,
        )
        val transition = ActivityTransition(
            id = 3,
            timestamp = 10_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(
            sessionCoveringTimestamp = existingSession,
            daoOut = { capturedDao = it },
        )
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.WALKING)

        assertEquals(existingSession, capturedDao?.deletedSession)
    }

    @Test
    fun `OverrideActivityUseCase overriding between non-cycling activities does not affect cycling sessions`() = runTest {
        val transition = ActivityTransition(
            id = 4,
            timestamp = 30_000L,
            fromActivity = "WALKING",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.STILL)

        assertNull(capturedDao?.insertedSession)
        assertNull(capturedDao?.deletedSession)
    }

    @Test
    fun `OverrideActivityUseCase overriding from CYCLING when no session exists does not crash`() = runTest {
        val transition = ActivityTransition(
            id = 5,
            timestamp = 50_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        // No session covering this timestamp
        val cycling = cyclingRepo(sessionCoveringTimestamp = null, daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        // Should not throw
        useCase(transition, ActivityState.WALKING)

        assertNull(capturedDao?.deletedSession)
    }

    @Test
    fun `OverrideActivityUseCase undo flow override to CYCLING then undo removes session`() = runTest {
        val transition = ActivityTransition(
            id = 6,
            timestamp = 60_000L,
            fromActivity = "WALKING",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)

        // Step 1: override to CYCLING → should create a session at timestamp 60_000L
        var insertedCyclingSessionDao: FakeCyclingSessionDao? = null
        val cycling1 = cyclingRepo(daoOut = { insertedCyclingSessionDao = it })
        val useCase1 = OverrideActivityUseCaseImpl(repo, cycling1, noOpTransactionRunner)
        useCase1(transition, ActivityState.CYCLING)

        val createdSession = CyclingSession(
            id = 1,
            startTime = 60_000L,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = true,
        )
        assertNotNull("Expected a session to have been inserted on override to CYCLING", insertedCyclingSessionDao?.insertedSession)

        // Step 2: undo → override back to WALKING — the session (0ms duration) is under the
        // 60s threshold, so it should be deleted rather than updated.
        var deletingCyclingSessionDao: FakeCyclingSessionDao? = null
        val cycling2 = cyclingRepo(
            sessionCoveringTimestamp = createdSession,
            daoOut = { deletingCyclingSessionDao = it },
        )
        val useCase2 = OverrideActivityUseCaseImpl(repo, cycling2, noOpTransactionRunner)
        val afterOverride = transition.copy(toActivity = "CYCLING", isManualOverride = true)
        useCase2(afterOverride, ActivityState.WALKING)

        assertEquals(createdSession, deletingCyclingSessionDao?.deletedSession)
    }

    // ─── OverrideActivityUseCase — createCyclingSession with next transition ──

    @Test
    fun `OverrideActivityUseCase overriding to CYCLING sets endTime and duration when next transition exists`() = runTest {
        val transition = ActivityTransition(
            id = 10,
            timestamp = 100_000L,
            fromActivity = "WALKING",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        // next transition is 5 minutes later
        val nextTransition = ActivityTransition(
            id = 11,
            timestamp = 400_000L, // 300_000 ms = 5 min later
            fromActivity = "CYCLING",
            toActivity = "STILL",
            isManualOverride = false,
        )
        val (repo, _) = stepRepoWithNextTransition(
            transitions = listOf(transition),
            nextTransitionAfter = nextTransition,
        )
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.CYCLING)

        val inserted = capturedDao?.insertedSession
        assertNotNull("Expected a CyclingSession to be inserted", inserted)
        assertEquals(100_000L, inserted?.startTime)
        assertEquals(400_000L, inserted?.endTime)
        // durationMs = 300_000 ms, rounded: (300_000 + 30_000) / 60_000 = 5 min
        assertEquals(5, inserted?.durationMinutes)
        assertEquals(true, inserted?.isManualOverride)
    }

    @Test
    fun `OverrideActivityUseCase overriding to CYCLING leaves endTime null when no next transition exists`() = runTest {
        val transition = ActivityTransition(
            id = 20,
            timestamp = 200_000L,
            fromActivity = "STILL",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        // no next transition
        val (repo, _) = stepRepoWithNextTransition(
            transitions = listOf(transition),
            nextTransitionAfter = null,
        )
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.CYCLING)

        val inserted = capturedDao?.insertedSession
        assertNotNull("Expected a CyclingSession to be inserted", inserted)
        assertEquals(200_000L, inserted?.startTime)
        assertNull("endTime should be null for ongoing session", inserted?.endTime)
        assertEquals(0, inserted?.durationMinutes)
    }

    @Test
    fun `OverrideActivityUseCase overriding to CYCLING drops sub-60s session when next transition is too soon`() = runTest {
        val transition = ActivityTransition(
            id = 30,
            timestamp = 300_000L,
            fromActivity = "WALKING",
            toActivity = "WALKING",
            isManualOverride = false,
        )
        // next transition is only 30 seconds later — below the 60s threshold
        val nextTransition = ActivityTransition(
            id = 31,
            timestamp = 330_000L, // 30_000 ms = 30 s
            fromActivity = "CYCLING",
            toActivity = "STILL",
            isManualOverride = false,
        )
        val (repo, _) = stepRepoWithNextTransition(
            transitions = listOf(transition),
            nextTransitionAfter = nextTransition,
        )
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.CYCLING)

        // Session under 60s should not be inserted at all
        assertNull("Expected no CyclingSession to be inserted for sub-60s duration", capturedDao?.insertedSession)
    }

    // ─── OverrideActivityUseCase — closeCyclingSession (away from CYCLING) ────

    @Test
    fun `OverrideActivityUseCase overriding away from CYCLING closes session when duration is at least 60s`() = runTest {
        val startTimeMs = 1_000_000L
        val endTimeMs = 1_120_000L // 120_000 ms = 2 min
        val existingSession = CyclingSession(
            id = 42,
            startTime = startTimeMs,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = true,
        )
        val transition = ActivityTransition(
            id = 50,
            timestamp = endTimeMs,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(
            sessionCoveringTimestamp = existingSession,
            daoOut = { capturedDao = it },
        )
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.WALKING)

        // Should be updated (closed), not deleted
        assertNull("Expected session NOT to be deleted for long-enough duration", capturedDao?.deletedSession)
        val updated = capturedDao?.updatedSession
        assertNotNull("Expected session to be updated (closed)", updated)
        assertEquals(42, updated?.id)
        assertEquals(startTimeMs, updated?.startTime)
        assertEquals(endTimeMs, updated?.endTime)
        // durationMs = 120_000, rounded: (120_000 + 30_000) / 60_000 = 2 min
        assertEquals(2, updated?.durationMinutes)
    }

    @Test
    fun `OverrideActivityUseCase overriding away from CYCLING deletes session when duration is under 60s`() = runTest {
        val startTimeMs = 2_000_000L
        val endTimeMs = 2_030_000L // 30_000 ms = 30 s — below threshold
        val existingSession = CyclingSession(
            id = 55,
            startTime = startTimeMs,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = true,
        )
        val transition = ActivityTransition(
            id = 60,
            timestamp = endTimeMs,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(
            sessionCoveringTimestamp = existingSession,
            daoOut = { capturedDao = it },
        )
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.STILL)

        // Should be deleted (too short)
        assertNotNull("Expected session to be deleted for sub-60s duration", capturedDao?.deletedSession)
        assertNull("Expected session NOT to be updated for sub-60s duration", capturedDao?.updatedSession)
        assertEquals(existingSession, capturedDao?.deletedSession)
    }

    @Test
    fun `OverrideActivityUseCase overriding away from CYCLING closes session with exact 60s duration`() = runTest {
        val startTimeMs = 3_000_000L
        val endTimeMs = 3_060_000L // exactly 60_000 ms = exactly 60 s
        val existingSession = CyclingSession(
            id = 70,
            startTime = startTimeMs,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = true,
        )
        val transition = ActivityTransition(
            id = 75,
            timestamp = endTimeMs,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        val cycling = cyclingRepo(
            sessionCoveringTimestamp = existingSession,
            daoOut = { capturedDao = it },
        )
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.WALKING)

        // Exactly 60s meets the threshold → should be updated
        assertNull("Expected session NOT to be deleted at exact 60s threshold", capturedDao?.deletedSession)
        assertNotNull("Expected session to be updated (closed) at exact 60s", capturedDao?.updatedSession)
        assertEquals(1, capturedDao?.updatedSession?.durationMinutes)
    }

    @Test
    fun `OverrideActivityUseCase overriding away from CYCLING when no session covers the timestamp sets neither updatedSession nor deletedSession`() = runTest {
        val transition = ActivityTransition(
            id = 80,
            timestamp = 4_000_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = false,
        )
        val transitionDao = FakeActivityTransitionDao(flowOf(listOf(transition)))
        val repo = StepRepository(FakeStepDao(), transitionDao)
        var capturedDao: FakeCyclingSessionDao? = null
        // No session covers this timestamp
        val cycling = cyclingRepo(sessionCoveringTimestamp = null, daoOut = { capturedDao = it })
        val useCase = OverrideActivityUseCaseImpl(repo, cycling, noOpTransactionRunner)

        useCase(transition, ActivityState.WALKING)

        assertNull("Expected updatedSession to be null when no session covers the timestamp", capturedDao?.updatedSession)
        assertNull("Expected deletedSession to be null when no session covers the timestamp", capturedDao?.deletedSession)
    }
}
