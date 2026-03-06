// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.db.ManualSessionOverride
import com.podometer.data.db.ManualSessionOverrideDao
import com.podometer.data.db.SensorWindow
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.SensorWindowRepository
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.domain.usecase.RecomputeActivitySessionsUseCase
import com.podometer.domain.usecase.mergeSessionOverrides
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI state for the Activities screen.
 *
 * @property selectedDate     The date whose activity sessions are displayed.
 * @property sessions         Recomputed activity sessions for [selectedDate].
 * @property windows          Raw sensor windows for the step graph.
 * @property bucketSizeMs     Time bucket size for step graph aggregation.
 * @property isToday          True when [selectedDate] is the current day.
 * @property dateLabel        Formatted date label for display (e.g. "Monday, Mar 3").
 * @property isLoading        True while initial data is loading.
 */
data class ActivitiesUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val sessions: List<ActivitySession> = emptyList(),
    val windows: List<SensorWindow> = emptyList(),
    val bucketSizeMs: Long = 300_000L,
    val isToday: Boolean = true,
    val dateLabel: String = "",
    val isLoading: Boolean = true,
)

/**
 * ViewModel for the Activities screen.
 *
 * Manages date navigation and recomputes activity sessions from stored sensor
 * windows for the selected date using [RecomputeActivitySessionsUseCase].
 */
@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val recomputeActivitySessions: RecomputeActivitySessionsUseCase,
    private val sensorWindowRepository: SensorWindowRepository,
    private val manualSessionOverrideDao: ManualSessionOverrideDao,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _bucketSizeMs = MutableStateFlow(300_000L)
    private val _useTestData = MutableStateFlow(false)

    /** The currently selected date. */
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** Returns the override date key, prefixed for test data to avoid leaking. */
    private fun overrideDateKey(date: LocalDate, isTestData: Boolean): String {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return if (isTestData) "test:$dateStr" else dateStr
    }

    /** Combined UI state emitted to the Activities screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ActivitiesUiState> = combine(
        _selectedDate,
        preferencesManager.useTestData(),
    ) { date, useTest -> date to useTest }.flatMapLatest { (date, useTestData) ->
        _useTestData.value = useTestData
        val nowMillis = System.currentTimeMillis()
        val dateKey = overrideDateKey(date, useTestData)

        if (useTestData) {
            combine(
                flowOf(TestDataGenerator.generateSessions(date)),
                flowOf(TestDataGenerator.generateWindows(date)),
                manualSessionOverrideDao.getOverridesForDate(dateKey),
                _bucketSizeMs,
            ) { generatedSessions, windows, overrides, bucketSizeMs ->
                val sessions = mergeSessionOverrides(generatedSessions, overrides)
                ActivitiesUiState(
                    selectedDate = date,
                    sessions = sessions,
                    windows = windows,
                    bucketSizeMs = bucketSizeMs,
                    isToday = date == LocalDate.now(),
                    dateLabel = formatDateLabel(date),
                    isLoading = false,
                )
            }
        } else {
            combine(
                recomputeActivitySessions(date, nowMillis),
                sensorWindowRepository.getWindowsForDay(date),
                manualSessionOverrideDao.getOverridesForDate(dateKey),
                _bucketSizeMs,
            ) { recomputedSessions, windows, overrides, bucketSizeMs ->
                val sessions = mergeSessionOverrides(recomputedSessions, overrides)
                ActivitiesUiState(
                    selectedDate = date,
                    sessions = sessions,
                    windows = windows,
                    bucketSizeMs = bucketSizeMs,
                    isToday = date == LocalDate.now(),
                    dateLabel = formatDateLabel(date),
                    isLoading = false,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ActivitiesUiState(),
    )

    /** Updates the bucket size for the step graph. */
    fun setBucketSize(ms: Long) {
        _bucketSizeMs.value = ms
    }

    /** Saves a new or updated manual session override. */
    fun saveSessionOverride(
        startMs: Long,
        endMs: Long,
        activity: ActivityState,
        existingOverrideId: Long = 0,
    ) {
        viewModelScope.launch {
            val dateKey = overrideDateKey(_selectedDate.value, _useTestData.value)
            if (existingOverrideId > 0) {
                manualSessionOverrideDao.update(
                    ManualSessionOverride(
                        id = existingOverrideId,
                        startTime = startMs,
                        endTime = endMs,
                        activity = activity.name,
                        date = dateKey,
                    ),
                )
            } else {
                manualSessionOverrideDao.insert(
                    ManualSessionOverride(
                        startTime = startMs,
                        endTime = endMs,
                        activity = activity.name,
                        date = dateKey,
                    ),
                )
            }
        }
    }

    /** Deletes a manual session override by its session's transition ID. */
    fun deleteSessionOverride(overrideId: Long) {
        viewModelScope.launch {
            manualSessionOverrideDao.deleteById(overrideId)
        }
    }

    /** Navigates to the given [date]. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Navigates to the previous day. */
    fun goToPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    /** Navigates to the next day (capped at today). */
    fun goToNextDay() {
        val next = _selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) {
            _selectedDate.value = next
        }
    }
}

/**
 * Formats a [LocalDate] as a human-readable label like "Monday, Mar 3".
 *
 * @param date The date to format.
 * @return Formatted date string.
 */
internal fun formatDateLabel(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.name.lowercase()
        .replaceFirstChar { it.uppercase() }
    val month = date.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    return "$dayOfWeek, $month ${date.dayOfMonth}"
}
