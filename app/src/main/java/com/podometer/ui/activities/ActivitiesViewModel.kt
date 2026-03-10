// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI state for the Activities screen.
 *
 * @property selectedDate     The date whose step windows are displayed.
 * @property windows          Step windows for the step graph.
 * @property bucketSizeMs     Time bucket size for step graph aggregation.
 * @property isToday          True when [selectedDate] is the current day.
 * @property dateLabel        Formatted date label for display (e.g. "Monday, Mar 3").
 * @property isLoading        True while initial data is loading.
 */
data class ActivitiesUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val windows: List<StepWindowPoint> = emptyList(),
    val bucketSizeMs: Long = 300_000L,
    val isToday: Boolean = true,
    val dateLabel: String = "",
    val isLoading: Boolean = true,
)

/**
 * ViewModel for the Activities screen.
 *
 * Manages date navigation and provides step windows for the selected date.
 */
@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _bucketSizeMs = MutableStateFlow(300_000L)

    /** The currently selected date. */
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** Combined UI state emitted to the Activities screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ActivitiesUiState> = combine(
        _selectedDate,
        preferencesManager.useTestData(),
    ) { date, useTest -> date to useTest }.flatMapLatest { (date, _) ->
        combine(
            flowOf(emptyList<StepWindowPoint>()),
            _bucketSizeMs,
        ) { windows, bucketSizeMs ->
            ActivitiesUiState(
                selectedDate = date,
                windows = windows,
                bucketSizeMs = bucketSizeMs,
                isToday = date == LocalDate.now(),
                dateLabel = formatDateLabel(date),
                isLoading = false,
            )
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
