// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.TransitionEvent
import com.podometer.domain.usecase.RecomputeActivitySessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * UI state for the Activities screen.
 *
 * @property selectedDate     The date whose activity sessions are displayed.
 * @property sessions         Recomputed activity sessions for [selectedDate].
 * @property isToday          True when [selectedDate] is the current day.
 * @property dateLabel        Formatted date label for display (e.g. "Monday, Mar 3").
 * @property isLoading        True while initial data is loading.
 */
data class ActivitiesUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val sessions: List<ActivitySession> = emptyList(),
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
) : ViewModel() {

    companion object {
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    /** The currently selected date. */
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** Combined UI state emitted to the Activities screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ActivitiesUiState> = _selectedDate.flatMapLatest { date ->
        val nowMillis = System.currentTimeMillis()
        recomputeActivitySessions(date, nowMillis).combine(
            MutableStateFlow(date),
        ) { sessions, selectedDate ->
            ActivitiesUiState(
                selectedDate = selectedDate,
                sessions = sessions,
                isToday = selectedDate == LocalDate.now(),
                dateLabel = formatDateLabel(selectedDate),
                isLoading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ActivitiesUiState(),
    )

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
