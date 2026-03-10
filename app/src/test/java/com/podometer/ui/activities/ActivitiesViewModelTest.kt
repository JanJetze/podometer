// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.podometer.data.repository.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Unit tests for [ActivitiesViewModel].
 *
 * Verifies date navigation, state emission, and date label formatting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {

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

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildPreferencesManager(): PreferencesManager {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            tmpFolder.newFile("test_prefs.preferences_pb")
        }
        return PreferencesManager(dataStore)
    }

    private fun buildViewModel(): ActivitiesViewModel = ActivitiesViewModel(
        preferencesManager = buildPreferencesManager(),
    )

    // ─── Initial state ───────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading true`() {
        val viewModel = buildViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `initial selectedDate is today`() {
        val viewModel = buildViewModel()
        assertEquals(LocalDate.now(), viewModel.selectedDate.value)
    }

    // ─── Date navigation ─────────────────────────────────────────────────────

    @Test
    fun `goToPreviousDay moves date back one day`() = runTest {
        val viewModel = buildViewModel()
        val today = LocalDate.now()

        viewModel.goToPreviousDay()

        assertEquals(today.minusDays(1), viewModel.selectedDate.value)
    }

    @Test
    fun `goToNextDay does not go past today`() = runTest {
        val viewModel = buildViewModel()

        viewModel.goToNextDay()

        assertEquals(LocalDate.now(), viewModel.selectedDate.value)
    }

    @Test
    fun `goToNextDay advances when not at today`() = runTest {
        val viewModel = buildViewModel()
        val today = LocalDate.now()

        viewModel.goToPreviousDay()
        viewModel.goToPreviousDay()
        viewModel.goToNextDay()

        assertEquals(today.minusDays(1), viewModel.selectedDate.value)
    }

    @Test
    fun `selectDate sets the selected date`() = runTest {
        val viewModel = buildViewModel()
        val target = LocalDate.of(2026, 2, 28)

        viewModel.selectDate(target)

        assertEquals(target, viewModel.selectedDate.value)
    }

    // ─── UI state emission ───────────────────────────────────────────────────

    @Test
    fun `uiState isToday is true when selectedDate is today`() = runTest {
        val viewModel = buildViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.isToday)
    }

    @Test
    fun `uiState isToday is false when selectedDate is not today`() = runTest {
        val viewModel = buildViewModel()

        viewModel.goToPreviousDay()

        val state = viewModel.uiState.first { !it.isLoading && !it.isToday }

        assertFalse(state.isToday)
    }

    // ─── formatDateLabel ─────────────────────────────────────────────────────

    @Test
    fun `formatDateLabel produces expected format`() {
        val date = LocalDate.of(2026, 3, 3) // Tuesday
        val label = formatDateLabel(date)
        assertEquals("Tuesday, Mar 3", label)
    }

    @Test
    fun `formatDateLabel handles January`() {
        val date = LocalDate.of(2026, 1, 15) // Thursday
        val label = formatDateLabel(date)
        assertEquals("Thursday, Jan 15", label)
    }
}
