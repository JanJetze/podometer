// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.db.CyclingSession
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.DaySummary
import com.podometer.domain.model.TransitionEvent
import com.podometer.domain.usecase.GetTodayCyclingSessionsUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetTodayTransitionsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state for the Dashboard screen.
 *
 * All fields are derived from the four domain use cases and combined into a
 * single snapshot that the Compose UI observes.
 *
 * @property todaySteps       Total steps taken today.
 * @property dailyGoal        User's configured daily step goal.
 * @property progressPercent  Steps as a percentage of [dailyGoal], capped at 100.
 * @property distanceKm       Approximate distance walked today in kilometres.
 * @property currentActivity  Current inferred activity, derived from the most recent
 *                            transition or [ActivityState.STILL] if none.
 * @property transitions      All activity transitions detected today.
 * @property weeklySteps      Per-day summaries for the current calendar week.
 * @property cyclingSessions  Cycling sessions recorded today.
 * @property isLoading        True while the initial data is loading; false once all
 *                            flows have emitted at least one value.
 */
data class DashboardUiState(
    val todaySteps: Int = 0,
    val dailyGoal: Int = 10_000,
    val progressPercent: Float = 0f,
    val distanceKm: Float = 0f,
    val currentActivity: ActivityState = ActivityState.STILL,
    val transitions: List<TransitionEvent> = emptyList(),
    val weeklySteps: List<DaySummary> = emptyList(),
    val cyclingSessions: List<CyclingSession> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel for the Dashboard screen.
 *
 * Injects all four domain use cases via Hilt and merges their [kotlinx.coroutines.flow.Flow]s
 * into a single [StateFlow] of [DashboardUiState] using [combine].
 *
 * The [uiState] starts with [DashboardUiState.isLoading] = true and transitions to
 * `isLoading = false` as soon as all four flows emit their first values.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    getTodaySteps: GetTodayStepsUseCase,
    getWeeklySteps: GetWeeklyStepsUseCase,
    getTodayTransitions: GetTodayTransitionsUseCase,
    getTodayCyclingSessions: GetTodayCyclingSessionsUseCase,
) : ViewModel() {

    companion object {
        /** Timeout (ms) to keep the upstream flows active after the last subscriber leaves. */
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    /** Combined UI state emitted to the Dashboard Compose screen. */
    val uiState: StateFlow<DashboardUiState> = combine(
        getTodaySteps(),
        getWeeklySteps(),
        getTodayTransitions(),
        getTodayCyclingSessions(),
    ) { stepData, weekly, transitions, cycling ->
        DashboardUiState(
            todaySteps = stepData.steps,
            dailyGoal = stepData.goal,
            progressPercent = stepData.progressPercent,
            distanceKm = stepData.distanceKm,
            currentActivity = transitions.lastOrNull()?.toActivity ?: ActivityState.STILL,
            transitions = transitions,
            weeklySteps = weekly,
            cyclingSessions = cycling,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardUiState(),
    )
}
