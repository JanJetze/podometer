// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepBucketDao
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepBucketRepository
import com.podometer.domain.model.DaySummary
import com.podometer.domain.model.StepData
import com.podometer.domain.usecase.GetStreakUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import com.podometer.domain.usecase.StreakInfo
import com.podometer.domain.usecase.TodayProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [DashboardViewModel].
 *
 * Fakes the use cases so the ViewModel logic (combine, initial state) can be
 * verified in isolation without Android or Hilt dependencies.
 *
 * Uses [UnconfinedTestDispatcher] installed as [Dispatchers.Main] so that
 * [androidx.lifecycle.viewModelScope] coroutines run eagerly in tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun buildPreferencesManager(): PreferencesManager {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            tmpFolder.newFile("test_prefs.preferences_pb")
        }
        return PreferencesManager(dataStore)
    }

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeGetTodayStepsUseCase(
        private val flow: Flow<StepData> = flowOf(
            StepData(steps = 0, goal = 10_000, progressPercent = 0f, distanceKm = 0f),
        ),
    ) : GetTodayStepsUseCase {
        override fun invoke(): Flow<StepData> = flow
    }

    private class FakeGetWeeklyStepsUseCase(
        private val flow: Flow<List<DaySummary>> = flowOf(emptyList()),
    ) : GetWeeklyStepsUseCase {
        override fun invoke(): Flow<List<DaySummary>> = flow
    }

    private class FakeGetStreakUseCase(
        private val info: StreakInfo = StreakInfo(
            currentStreak = 0,
            todayProgress = TodayProgress.NOT_MET,
        ),
    ) : GetStreakUseCase {
        override suspend fun invoke(): StreakInfo = info
    }

    private class FakeStepBucketDao(
        private val flow: Flow<List<StepBucket>> = flowOf(emptyList()),
    ) : com.podometer.data.db.StepBucketDao {
        override suspend fun upsert(bucket: StepBucket) = Unit
        override fun getBucketsForDay(startOfDay: Long, endOfDay: Long): Flow<List<StepBucket>> = flow
        override fun getBucketsInRange(start: Long, end: Long): Flow<List<StepBucket>> = flowOf(emptyList())
        override suspend fun getStepsForBucket(bucketTimestamp: Long): Int? = null
        override suspend fun getAllBuckets(): List<StepBucket> = emptyList()
        override suspend fun deleteAll() = Unit
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildViewModel(
        stepData: StepData = StepData(steps = 0, goal = 10_000, progressPercent = 0f, distanceKm = 0f),
        weeklySteps: List<DaySummary> = emptyList(),
        streakInfo: StreakInfo = StreakInfo(currentStreak = 0, todayProgress = TodayProgress.NOT_MET),
        buckets: List<StepBucket> = emptyList(),
        preferencesManager: PreferencesManager = buildPreferencesManager(),
    ): DashboardViewModel = DashboardViewModel(
        getTodaySteps = FakeGetTodayStepsUseCase(flowOf(stepData)),
        getWeeklySteps = FakeGetWeeklyStepsUseCase(flowOf(weeklySteps)),
        getStreak = FakeGetStreakUseCase(streakInfo),
        stepBucketRepository = StepBucketRepository(FakeStepBucketDao(flowOf(buckets))),
        preferencesManager = preferencesManager,
    )

    // ─── DashboardUiState default state ──────────────────────────────────────

    @Test
    fun `DashboardUiState default has zero steps`() {
        val state = DashboardUiState()
        assertEquals(0, state.todaySteps)
    }

    @Test
    fun `DashboardUiState default has empty weeklyDays`() {
        val state = DashboardUiState()
        assertTrue(state.weeklyDays.isEmpty())
    }

    @Test
    fun `DashboardUiState default has minimumGoal 5000`() {
        val state = DashboardUiState()
        assertEquals(5_000, state.minimumGoal)
    }

    @Test
    fun `DashboardUiState default has targetGoal 8000`() {
        val state = DashboardUiState()
        assertEquals(8_000, state.targetGoal)
    }

    @Test
    fun `DashboardUiState default has stretchGoal 12000`() {
        val state = DashboardUiState()
        assertEquals(12_000, state.stretchGoal)
    }

    @Test
    fun `DashboardUiState default has streakDays 0`() {
        val state = DashboardUiState()
        assertEquals(0, state.streakDays)
    }

    @Test
    fun `DashboardUiState default has todayGoalMet false`() {
        val state = DashboardUiState()
        assertFalse(state.todayGoalMet)
    }

    @Test
    fun `DashboardUiState default has empty todayBuckets`() {
        val state = DashboardUiState()
        assertTrue(state.todayBuckets.isEmpty())
    }

    @Test
    fun `DashboardUiState default has chartResolution HOURLY`() {
        val state = DashboardUiState()
        assertEquals(ChartResolution.HOURLY, state.chartResolution)
    }

    @Test
    fun `DashboardUiState default has isRestDay false`() {
        val state = DashboardUiState()
        assertFalse(state.isRestDay)
    }

    // ─── ViewModel initial state ──────────────────────────────────────────────

    @Test
    fun `ViewModel initial uiState value has isLoading true`() {
        val viewModel = buildViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    // ─── ViewModel combines use case flows ───────────────────────────────────

    @Test
    fun `ViewModel emits step count from GetTodayStepsUseCase`() = runTest {
        val stepData = StepData(steps = 7_500, goal = 10_000, progressPercent = 75f, distanceKm = 5.625f)
        val viewModel = buildViewModel(stepData = stepData)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(7_500, state.todaySteps)
    }

    @Test
    fun `ViewModel emits weeklyDays from GetWeeklyStepsUseCase`() = runTest {
        val weekly = listOf(
            DaySummary(date = "2026-02-17", totalSteps = 8_000, totalDistanceKm = 6f),
        )
        val viewModel = buildViewModel(weeklySteps = weekly)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(1, state.weeklyDays.size)
        assertEquals("2026-02-17", state.weeklyDays[0].date)
        assertEquals(8_000, state.weeklyDays[0].totalSteps)
    }

    @Test
    fun `ViewModel emits streakDays from GetStreakUseCase`() = runTest {
        val streakInfo = StreakInfo(currentStreak = 7, todayProgress = TodayProgress.MET)
        val viewModel = buildViewModel(streakInfo = streakInfo)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(7, state.streakDays)
    }

    @Test
    fun `ViewModel emits todayGoalMet true when streak todayProgress is MET`() = runTest {
        val streakInfo = StreakInfo(currentStreak = 3, todayProgress = TodayProgress.MET)
        val viewModel = buildViewModel(streakInfo = streakInfo)

        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.todayGoalMet)
    }

    @Test
    fun `ViewModel emits todayGoalMet false when streak todayProgress is NOT_MET`() = runTest {
        val streakInfo = StreakInfo(currentStreak = 0, todayProgress = TodayProgress.NOT_MET)
        val viewModel = buildViewModel(streakInfo = streakInfo)

        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.todayGoalMet)
    }

    @Test
    fun `ViewModel maps StepBuckets to StepBars in todayBuckets`() = runTest {
        val buckets = listOf(
            StepBucket(timestamp = 1_000_000L, stepCount = 42),
            StepBucket(timestamp = 1_300_000L, stepCount = 17),
        )
        val viewModel = buildViewModel(buckets = buckets)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(2, state.todayBuckets.size)
        assertEquals(1_000_000L, state.todayBuckets[0].startTime)
        assertEquals(42, state.todayBuckets[0].stepCount)
        assertEquals(1_300_000L, state.todayBuckets[1].startTime)
        assertEquals(17, state.todayBuckets[1].stepCount)
    }

    // ─── isLoading state ─────────────────────────────────────────────────────

    @Test
    fun `ViewModel emits isLoading false once all flows emit`() = runTest {
        val viewModel = buildViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.isLoading)
    }

    // ─── Flow updates ────────────────────────────────────────────────────────

    @Test
    fun `ViewModel reflects updated step count when flow emits new value`() = runTest {
        val stepsFlow = MutableStateFlow(
            StepData(steps = 0, goal = 10_000, progressPercent = 0f, distanceKm = 0f),
        )
        val viewModel = DashboardViewModel(
            getTodaySteps = FakeGetTodayStepsUseCase(stepsFlow),
            getWeeklySteps = FakeGetWeeklyStepsUseCase(),
            getStreak = FakeGetStreakUseCase(),
            stepBucketRepository = StepBucketRepository(FakeStepBucketDao()),
            preferencesManager = buildPreferencesManager(),
        )

        val firstState = viewModel.uiState.first { !it.isLoading }
        assertEquals(0, firstState.todaySteps)

        stepsFlow.value = StepData(steps = 3_000, goal = 10_000, progressPercent = 30f, distanceKm = 2.25f)

        val updatedState = viewModel.uiState.first { it.todaySteps == 3_000 }
        assertEquals(3_000, updatedState.todaySteps)
    }

    // ─── Chart resolution ─────────────────────────────────────────────────────

    @Test
    fun `setChartResolution updates chartResolution in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.setChartResolution(ChartResolution.FIVE_MIN)

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(ChartResolution.FIVE_MIN, state.chartResolution)
    }

    @Test
    fun `setChartResolution to FIFTEEN_MIN is reflected in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.setChartResolution(ChartResolution.FIFTEEN_MIN)

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(ChartResolution.FIFTEEN_MIN, state.chartResolution)
    }

    // ─── Permission state ─────────────────────────────────────────────────────

    @Test
    fun `DashboardUiState default has permissionsDenied false`() {
        val state = DashboardUiState()
        assertFalse(state.permissionsDenied)
    }

    @Test
    fun `refreshPermissions with true sets permissionsDenied to false`() = runTest {
        val viewModel = buildViewModel()
        viewModel.refreshPermissions(permissionsGranted = true)

        val state = viewModel.uiState.first { !it.isLoading }
        assertFalse(state.permissionsDenied)
    }

    @Test
    fun `refreshPermissions with false sets permissionsDenied to true`() = runTest {
        val viewModel = buildViewModel()
        viewModel.refreshPermissions(permissionsGranted = false)

        val state = viewModel.uiState.first { it.permissionsDenied }
        assertTrue(state.permissionsDenied)
    }

    @Test
    fun `refreshPermissions transitions from denied to granted`() = runTest {
        val viewModel = buildViewModel()

        viewModel.refreshPermissions(permissionsGranted = false)
        val deniedState = viewModel.uiState.first { it.permissionsDenied }
        assertTrue(deniedState.permissionsDenied)

        viewModel.refreshPermissions(permissionsGranted = true)
        val grantedState = viewModel.uiState.first { !it.permissionsDenied }
        assertFalse(grantedState.permissionsDenied)
    }
}
