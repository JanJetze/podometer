// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.sensor.SensorType
import com.podometer.domain.model.DaySummary
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * UI state for the Dashboard screen.
 *
 * All fields are derived from the domain use cases and combined into a
 * single snapshot that the Compose UI observes.
 *
 * @property todaySteps       Total steps taken today.
 * @property dailyGoal        User's configured daily step goal.
 * @property progressPercent  Steps as a percentage of [dailyGoal] (may exceed 100 when the goal is surpassed).
 * @property distanceKm       Approximate distance walked today in kilometres.
 * @property weeklySteps      Per-day summaries for the current calendar week.
 * @property isLoading        True while the initial data is loading; false once all
 *                            flows have emitted at least one value.
 * @property sensorType       The type of step-counting sensor currently in use.
 * @property permissionsDenied True when the user has denied all required permissions.
 */
data class DashboardUiState(
    val todaySteps: Int = 0,
    val dailyGoal: Int = 10_000,
    val progressPercent: Float = 0f,
    val distanceKm: Float = 0f,
    val weeklySteps: List<DaySummary> = emptyList(),
    val isLoading: Boolean = true,
    val sensorType: SensorType = SensorType.STEP_COUNTER,
    val permissionsDenied: Boolean = false,
)

/**
 * ViewModel for the Dashboard screen.
 *
 * Injects domain use cases via Hilt and merges their [kotlinx.coroutines.flow.Flow]s
 * into a single [StateFlow] of [DashboardUiState] using [combine].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    getTodaySteps: GetTodayStepsUseCase,
    getWeeklySteps: GetWeeklyStepsUseCase,
    preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        /** Timeout (ms) to keep the upstream flows active after the last subscriber leaves. */
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    /** Tracks runtime permission state independently of the data flows. */
    private val _permissionsDenied = MutableStateFlow(false)

    /** Combined UI state emitted to the Dashboard Compose screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = preferencesManager.useTestData()
        .flatMapLatest { _ ->
            combine(
                getTodaySteps(),
                getWeeklySteps(),
                _permissionsDenied,
            ) { stepData, weekly, permissionsDenied ->
                DashboardUiState(
                    todaySteps = stepData.steps,
                    dailyGoal = stepData.goal,
                    progressPercent = stepData.progressPercent,
                    distanceKm = stepData.distanceKm,
                    weeklySteps = weekly,
                    isLoading = false,
                    permissionsDenied = permissionsDenied,
                )
            }
        }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardUiState(),
    )

    /**
     * Updates the permission state in [uiState].
     *
     * @param permissionsGranted True if ACTIVITY_RECOGNITION is currently granted.
     */
    fun refreshPermissions(permissionsGranted: Boolean) {
        _permissionsDenied.update { !permissionsGranted }
    }
}
