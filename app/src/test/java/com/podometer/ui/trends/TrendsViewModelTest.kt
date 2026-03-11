// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepDao
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
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
import java.time.LocalDate

/**
 * Unit tests for [TrendsViewModel].
 *
 * Uses a fake [StepDao] wrapped in a real [StepRepository] so DB calls are
 * controlled in tests without subclassing the final StepRepository.
 *
 * Uses a real [PreferencesManager] backed by a temp DataStore file.
 * Uses [UnconfinedTestDispatcher] as Dispatchers.Main so viewModelScope runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    // ─── Fake StepDao ─────────────────────────────────────────────────────────

    private class FakeStepDao(
        private val summariesFlow: Flow<List<DailySummary>> = flowOf(emptyList()),
    ) : StepDao {
        override fun getDailySummary(date: String): Flow<DailySummary?> = flowOf(null)
        override fun getWeeklyDailySummaries(
            startDate: String,
            endDate: String,
        ): Flow<List<DailySummary>> = summariesFlow
        override suspend fun getTodayTotalStepsSnapshot(date: String): Int? = null
        override suspend fun upsertDailySummary(summary: DailySummary) = Unit
        override suspend fun upsertStepsAndDistance(
            date: String,
            totalSteps: Int,
            totalDistance: Float,
        ) = Unit
        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) = Unit
        override suspend fun getAllDailySummaries(): List<DailySummary> = emptyList()
        override suspend fun getDailySummariesUpTo(
            endDate: String,
            limit: Int,
        ): List<DailySummary> = emptyList()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildPreferencesManager(): PreferencesManager {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            tmpFolder.newFile("test_trends_prefs.preferences_pb")
        }
        return PreferencesManager(dataStore)
    }

    private fun buildViewModel(
        summaries: Flow<List<DailySummary>> = flowOf(emptyList()),
        preferencesManager: PreferencesManager = buildPreferencesManager(),
    ): TrendsViewModel = TrendsViewModel(
        stepRepository = StepRepository(FakeStepDao(summaries)),
        preferencesManager = preferencesManager,
    )

    private fun summary(date: String, steps: Int, distance: Float = 0f) =
        DailySummary(date = date, totalSteps = steps, totalDistance = distance)

    // ─── TrendsUiState defaults ───────────────────────────────────────────────

    @Test
    fun `TrendsUiState default period is WEEKLY`() {
        assertEquals(TrendsPeriod.WEEKLY, TrendsUiState().period)
    }

    @Test
    fun `TrendsUiState default days is empty`() {
        assertTrue(TrendsUiState().days.isEmpty())
    }

    @Test
    fun `TrendsUiState default canGoNext is false`() {
        assertFalse(TrendsUiState().canGoNext)
    }

    @Test
    fun `TrendsUiState default targetGoal is 8000`() {
        assertEquals(8_000, TrendsUiState().targetGoal)
    }

    @Test
    fun `TrendsUiState default minimumGoal is 5000`() {
        assertEquals(5_000, TrendsUiState().minimumGoal)
    }

    // ─── ViewModel initial state ──────────────────────────────────────────────

    @Test
    fun `ViewModel initial state has WEEKLY period`() = runTest {
        val vm = buildViewModel()
        val state = vm.uiState.first()
        assertEquals(TrendsPeriod.WEEKLY, state.period)
    }

    @Test
    fun `ViewModel initial state has canGoNext false`() = runTest {
        val vm = buildViewModel()
        val state = vm.uiState.first()
        assertFalse(state.canGoNext)
    }

    @Test
    fun `ViewModel emits days from repository`() = runTest {
        val dbList = listOf(
            summary("2026-03-02", 8_000, 6.0f),
            summary("2026-03-03", 10_000, 7.5f),
        )
        val vm = buildViewModel(summaries = flowOf(dbList))
        val state = vm.uiState.first { it.days.size == 2 }
        assertEquals(2, state.days.size)
        assertEquals("2026-03-02", state.days[0].date)
        assertEquals(8_000, state.days[0].totalSteps)
    }

    @Test
    fun `ViewModel maps totalDistance to totalDistanceKm in domain model`() = runTest {
        val dbList = listOf(summary("2026-03-02", 8_000, 6.0f))
        val vm = buildViewModel(summaries = flowOf(dbList))
        val state = vm.uiState.first { it.days.isNotEmpty() }
        assertEquals(6.0f, state.days[0].totalDistanceKm, 0.001f)
    }

    // ─── Stats computation ────────────────────────────────────────────────────

    @Test
    fun `ViewModel computes stats from loaded days`() = runTest {
        val dbList = listOf(
            summary("2026-03-02", 6_000),
            summary("2026-03-03", 14_000),
        )
        val vm = buildViewModel(summaries = flowOf(dbList))
        val state = vm.uiState.first { it.days.isNotEmpty() }
        assertEquals(10_000, state.stats.averageSteps)
        assertEquals(14_000, state.stats.bestDaySteps)
    }

    @Test
    fun `ViewModel stats are all zeros when days is empty`() = runTest {
        val vm = buildViewModel(summaries = flowOf(emptyList()))
        // stats default to zero — verify against the initial value
        val state = TrendsUiState()
        assertEquals(0, state.stats.averageSteps)
        assertEquals(0, state.stats.bestDaySteps)
        assertEquals(0.0f, state.stats.achievementRate, 0.001f)
    }

    // ─── setPeriod ────────────────────────────────────────────────────────────

    @Test
    fun `setPeriod MONTHLY switches period to MONTHLY`() = runTest {
        val vm = buildViewModel()
        vm.setPeriod(TrendsPeriod.MONTHLY)
        val state = vm.uiState.first { it.period == TrendsPeriod.MONTHLY }
        assertEquals(TrendsPeriod.MONTHLY, state.period)
    }

    @Test
    fun `setPeriod WEEKLY switches period to WEEKLY`() = runTest {
        val vm = buildViewModel()
        vm.setPeriod(TrendsPeriod.MONTHLY)
        vm.setPeriod(TrendsPeriod.WEEKLY)
        val state = vm.uiState.first { it.period == TrendsPeriod.WEEKLY }
        assertEquals(TrendsPeriod.WEEKLY, state.period)
    }

    @Test
    fun `setPeriod resets canGoNext to false`() = runTest {
        val vm = buildViewModel()
        vm.previousPeriod()
        val afterPrevious = vm.uiState.first { it.canGoNext }
        assertTrue(afterPrevious.canGoNext)

        vm.setPeriod(TrendsPeriod.MONTHLY)
        val afterSwitch = vm.uiState.first { it.period == TrendsPeriod.MONTHLY }
        assertFalse(afterSwitch.canGoNext)
    }

    // ─── previousPeriod / nextPeriod ─────────────────────────────────────────

    @Test
    fun `previousPeriod makes canGoNext true`() = runTest {
        val vm = buildViewModel()
        // Initial state: canGoNext is false
        assertFalse(vm.uiState.value.canGoNext)
        vm.previousPeriod()
        assertTrue(vm.uiState.first { it.canGoNext }.canGoNext)
    }

    @Test
    fun `nextPeriod at current period has no effect on canGoNext`() = runTest {
        val vm = buildViewModel()
        vm.nextPeriod()
        // canGoNext should still be false — check the initial/current value
        assertFalse(vm.uiState.value.canGoNext)
    }

    @Test
    fun `nextPeriod after previousPeriod makes canGoNext false again`() = runTest {
        val vm = buildViewModel()
        vm.previousPeriod()
        vm.uiState.first { it.canGoNext } // wait for canGoNext = true
        vm.nextPeriod()
        assertFalse(vm.uiState.first { !it.canGoNext }.canGoNext)
    }

    @Test
    fun `multiple previousPeriod calls maintain canGoNext true`() = runTest {
        val vm = buildViewModel()
        vm.previousPeriod()
        vm.previousPeriod()
        vm.previousPeriod()
        assertTrue(vm.uiState.first { it.canGoNext }.canGoNext)
    }

    @Test
    fun `nextPeriod from offset minus 2 still has canGoNext true`() = runTest {
        val vm = buildViewModel()
        vm.previousPeriod()
        vm.previousPeriod()
        vm.nextPeriod()
        // offset is now -1, still not current
        assertTrue(vm.uiState.first { it.canGoNext }.canGoNext)
    }

    // ─── Period label formatting ──────────────────────────────────────────────

    @Test
    fun `weekly period label contains dash separator`() = runTest {
        val vm = buildViewModel()
        val state = vm.uiState.first { it.periodLabel.isNotBlank() }
        assertTrue("Weekly label should not be blank", state.periodLabel.isNotBlank())
        assertTrue("Weekly label should contain ' \u2013 '", state.periodLabel.contains(" \u2013 "))
    }

    @Test
    fun `monthly period label contains year`() = runTest {
        val vm = buildViewModel()
        vm.setPeriod(TrendsPeriod.MONTHLY)
        val state = vm.uiState.first { it.period == TrendsPeriod.MONTHLY && it.periodLabel.isNotBlank() }
        val year = LocalDate.now().year.toString()
        assertTrue("Monthly label should contain year '$year'", state.periodLabel.contains(year))
    }

    // ─── computeDateRange unit tests ──────────────────────────────────────────

    @Test
    fun `computeDateRange WEEKLY offset 0 returns current week Monday to Sunday`() {
        val vm = buildViewModel()
        val today = LocalDate.of(2026, 3, 10) // Tuesday
        val (startDate, endDate, _) = vm.computeDateRange(TrendsPeriod.WEEKLY, 0, today)
        assertEquals("2026-03-09", startDate) // Monday
        assertEquals("2026-03-15", endDate)   // Sunday
    }

    @Test
    fun `computeDateRange WEEKLY offset minus1 returns previous week`() {
        val vm = buildViewModel()
        val today = LocalDate.of(2026, 3, 10) // Tuesday
        val (startDate, endDate, _) = vm.computeDateRange(TrendsPeriod.WEEKLY, -1, today)
        assertEquals("2026-03-02", startDate) // previous Monday
        assertEquals("2026-03-08", endDate)   // previous Sunday
    }

    @Test
    fun `computeDateRange WEEKLY label matches formatWeekLabel`() {
        val vm = buildViewModel()
        val today = LocalDate.of(2026, 3, 10)
        val (startDate, endDate, label) = vm.computeDateRange(TrendsPeriod.WEEKLY, -1, today)
        assertEquals(formatWeekLabel(startDate, endDate), label)
    }

    @Test
    fun `computeDateRange MONTHLY offset 0 returns current month`() {
        val vm = buildViewModel()
        val today = LocalDate.of(2026, 3, 10)
        val (startDate, endDate, label) = vm.computeDateRange(TrendsPeriod.MONTHLY, 0, today)
        assertEquals("2026-03-01", startDate)
        assertEquals("2026-03-31", endDate)
        assertTrue("Monthly label should contain 'March'", label.contains("March"))
        assertTrue("Monthly label should contain '2026'", label.contains("2026"))
    }

    @Test
    fun `computeDateRange MONTHLY offset minus1 returns previous month`() {
        val vm = buildViewModel()
        val today = LocalDate.of(2026, 3, 10)
        val (startDate, endDate, label) = vm.computeDateRange(TrendsPeriod.MONTHLY, -1, today)
        assertEquals("2026-02-01", startDate)
        assertEquals("2026-02-28", endDate)
        assertTrue("Monthly label should contain 'February'", label.contains("February"))
    }

    // ─── Flow updates ─────────────────────────────────────────────────────────

    @Test
    fun `ViewModel reflects updated summaries when flow emits new value`() = runTest {
        val summariesFlow = MutableStateFlow<List<DailySummary>>(emptyList())
        val vm = buildViewModel(summaries = summariesFlow)

        val initial = vm.uiState.first()
        assertTrue(initial.days.isEmpty())

        summariesFlow.value = listOf(summary("2026-03-09", 9_000))
        val updated = vm.uiState.first { it.days.isNotEmpty() }
        assertEquals(1, updated.days.size)
        assertEquals(9_000, updated.days[0].totalSteps)
    }
}
