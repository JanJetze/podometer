// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.podometer.data.repository.PreferencesManager
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.DaySummary
import com.podometer.domain.model.StepData
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
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

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildViewModel(
        stepData: StepData = StepData(steps = 0, goal = 10_000, progressPercent = 0f, distanceKm = 0f),
        weeklySteps: List<DaySummary> = emptyList(),
    ): DashboardViewModel = DashboardViewModel(
        getTodaySteps = FakeGetTodayStepsUseCase(flowOf(stepData)),
        getWeeklySteps = FakeGetWeeklyStepsUseCase(flowOf(weeklySteps)),
        preferencesManager = buildPreferencesManager(),
    )

    // ─── DashboardUiState default state ──────────────────────────────────────

    @Test
    fun `DashboardUiState default has isLoading true`() {
        val state = DashboardUiState()
        assertTrue(state.isLoading)
    }

    @Test
    fun `DashboardUiState default has zero steps`() {
        val state = DashboardUiState()
        assertEquals(0, state.todaySteps)
    }

    @Test
    fun `DashboardUiState default has STILL activity`() {
        val state = DashboardUiState()
        assertEquals(ActivityState.STILL, state.currentActivity)
    }

    @Test
    fun `DashboardUiState default has empty weeklySteps`() {
        val state = DashboardUiState()
        assertTrue(state.weeklySteps.isEmpty())
    }

    @Test
    fun `DashboardUiState default has daily goal 10000`() {
        val state = DashboardUiState()
        assertEquals(10_000, state.dailyGoal)
    }

    // ─── ViewModel initial state ──────────────────────────────────────────────

    @Test
    fun `ViewModel initial uiState value has isLoading true`() {
        val viewModel = buildViewModel()
        // The initial value of the StateFlow (before flows emit) is a loading state.
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
    fun `ViewModel emits dailyGoal from GetTodayStepsUseCase`() = runTest {
        val stepData = StepData(steps = 0, goal = 10_000, progressPercent = 0f, distanceKm = 0f)
        val viewModel = buildViewModel(stepData = stepData)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(10_000, state.dailyGoal)
    }

    @Test
    fun `ViewModel emits progressPercent from GetTodayStepsUseCase`() = runTest {
        val stepData = StepData(steps = 5_000, goal = 10_000, progressPercent = 50f, distanceKm = 3.75f)
        val viewModel = buildViewModel(stepData = stepData)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(50f, state.progressPercent, 0.001f)
    }

    @Test
    fun `ViewModel emits distanceKm from GetTodayStepsUseCase`() = runTest {
        val stepData = StepData(steps = 5_000, goal = 10_000, progressPercent = 50f, distanceKm = 3.75f)
        val viewModel = buildViewModel(stepData = stepData)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(3.75f, state.distanceKm, 0.001f)
    }

    @Test
    fun `ViewModel emits weeklySteps from GetWeeklyStepsUseCase`() = runTest {
        val weekly = listOf(
            DaySummary(date = "2026-02-17", totalSteps = 8_000, totalDistanceKm = 6f, walkingMinutes = 70, cyclingMinutes = 0),
        )
        val viewModel = buildViewModel(weeklySteps = weekly)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(1, state.weeklySteps.size)
        assertEquals("2026-02-17", state.weeklySteps[0].date)
        assertEquals(8_000, state.weeklySteps[0].totalSteps)
    }

    // ─── currentActivity derivation ──────────────────────────────────────────

    @Test
    fun `ViewModel currentActivity is always STILL`() = runTest {
        val viewModel = buildViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(ActivityState.STILL, state.currentActivity)
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
            preferencesManager = buildPreferencesManager(),
        )

        val firstState = viewModel.uiState.first { !it.isLoading }
        assertEquals(0, firstState.todaySteps)

        stepsFlow.value = StepData(steps = 3_000, goal = 10_000, progressPercent = 30f, distanceKm = 2.25f)

        val updatedState = viewModel.uiState.first { it.todaySteps == 3_000 }
        assertEquals(3_000, updatedState.todaySteps)
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

        // After refresh with denied, permissionsDenied should be true
        val state = viewModel.uiState.first { it.permissionsDenied }
        assertTrue(state.permissionsDenied)
    }

    @Test
    fun `refreshPermissions transitions from denied to granted`() = runTest {
        val viewModel = buildViewModel()

        // Initially denied
        viewModel.refreshPermissions(permissionsGranted = false)
        val deniedState = viewModel.uiState.first { it.permissionsDenied }
        assertTrue(deniedState.permissionsDenied)

        // Permissions become granted
        viewModel.refreshPermissions(permissionsGranted = true)
        val grantedState = viewModel.uiState.first { !it.permissionsDenied }
        assertFalse(grantedState.permissionsDenied)
    }

    @Test
    fun `permissionsDenied is false by default before refreshPermissions is called`() = runTest {
        val viewModel = buildViewModel()

        val state = viewModel.uiState.first { !it.isLoading }
        assertFalse(state.permissionsDenied)
    }
}
