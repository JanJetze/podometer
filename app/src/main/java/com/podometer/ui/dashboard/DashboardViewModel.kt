// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.CyclingSession
import com.podometer.data.repository.PreferencesManager
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.DaySummary
import com.podometer.domain.model.TransitionEvent
import com.podometer.data.sensor.SensorType
import com.podometer.domain.usecase.GetTodayCyclingSessionsUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetTodayTransitionsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import com.podometer.domain.usecase.OverrideActivityUseCase
import com.podometer.ui.activities.TestDataGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * UI state for the Dashboard screen.
 *
 * All fields are derived from the four domain use cases and combined into a
 * single snapshot that the Compose UI observes.
 *
 * @property todaySteps       Total steps taken today.
 * @property dailyGoal        User's configured daily step goal.
 * @property progressPercent  Steps as a percentage of [dailyGoal] (may exceed 100 when the goal is surpassed).
 * @property distanceKm       Approximate distance walked today in kilometres.
 * @property currentActivity  Current inferred activity, derived from the most recent
 *                            transition or [ActivityState.STILL] if none.
 * @property transitions      All activity transitions detected today.
 * @property weeklySteps      Per-day summaries for the current calendar week.
 * @property cyclingSessions  Cycling sessions recorded today.
 * @property isLoading        True while the initial data is loading; false once all
 *                            flows have emitted at least one value.
 * @property sensorType       The type of step-counting sensor currently in use.
 *                            Defaults to [SensorType.STEP_COUNTER]. When
 *                            [SensorType.ACCELEROMETER] or [SensorType.NONE], the UI
 *                            shows a degraded-mode notice.
 * @property permissionsDenied True when the user has denied all required permissions.
 *                             When true, the UI shows a full-screen recovery prompt
 *                             directing the user to system app settings.
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
    val sensorType: SensorType = SensorType.STEP_COUNTER,
    val permissionsDenied: Boolean = false,
)

/**
 * ViewModel for the Dashboard screen.
 *
 * Injects all four domain use cases via Hilt and merges their [kotlinx.coroutines.flow.Flow]s
 * into a single [StateFlow] of [DashboardUiState] using [combine].
 *
 * The [uiState] starts with [DashboardUiState.isLoading] = true and transitions to
 * `isLoading = false` as soon as all four flows emit their first values.
 *
 * Permission state is managed via [refreshPermissions]: the composable calls this function
 * with the result of [com.podometer.util.checkEssentialPermissions] on every lifecycle resume.
 * This keeps the ViewModel free of Android framework dependencies (no Context injected),
 * making it straightforward to unit-test.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    getTodaySteps: GetTodayStepsUseCase,
    getWeeklySteps: GetWeeklyStepsUseCase,
    getTodayTransitions: GetTodayTransitionsUseCase,
    getTodayCyclingSessions: GetTodayCyclingSessionsUseCase,
    private val overrideActivityUseCase: OverrideActivityUseCase,
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
        .flatMapLatest { useTestData ->
            if (useTestData) {
                val today = LocalDate.now()
                val stepData = TestDataGenerator.generateTodaySteps()
                val transitions = TestDataGenerator.generateTransitions(today)
                val weekly = TestDataGenerator.generateWeeklySummaries()
                val cycling = TestDataGenerator.generateCyclingSessions(today)
                combine(
                    flowOf(Unit),
                    _permissionsDenied,
                ) { _, permissionsDenied ->
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
                        permissionsDenied = permissionsDenied,
                    )
                }
            } else {
                combine(
                    getTodaySteps(),
                    getWeeklySteps(),
                    getTodayTransitions(),
                    getTodayCyclingSessions(),
                    _permissionsDenied,
                ) { stepData, weekly, transitions, cycling, permissionsDenied ->
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
                        permissionsDenied = permissionsDenied,
                    )
                }
            }
        }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardUiState(),
    )

    /**
     * Updates the permission state in [uiState].
     *
     * The composable calls this on every lifecycle resume with the result of
     * [com.podometer.util.checkEssentialPermissions]. When [permissionsGranted] is false,
     * [DashboardUiState.permissionsDenied] becomes true and the UI shows
     * [PermissionRecoveryScreen].
     *
     * @param permissionsGranted True if ACTIVITY_RECOGNITION is currently granted.
     */
    fun refreshPermissions(permissionsGranted: Boolean) {
        _permissionsDenied.update { !permissionsGranted }
    }

    /**
     * Stores the state of the transition before the most recent override so that
     * [undoLastOverride] can revert it.
     */
    private var previousTransitionState: TransitionEvent? = null

    /**
     * Overrides an activity transition identified by [transitionId] with [newActivity].
     *
     * Finds the matching [TransitionEvent] in the current [uiState], maps it to the
     * [ActivityTransition] DB entity, stores the previous state for undo, and calls
     * [OverrideActivityUseCase].
     *
     * @param transitionId The id of the [TransitionEvent] to override.
     * @param newActivity  The new [ActivityState] to assign as [ActivityTransition.toActivity].
     */
    fun overrideTransition(transitionId: Int, newActivity: ActivityState) {
        val current = uiState.value.transitions.find { it.id == transitionId } ?: return
        previousTransitionState = current
        val dbEntity = ActivityTransition(
            id = current.id,
            timestamp = current.timestamp,
            fromActivity = current.fromActivity.name,
            toActivity = current.toActivity.name,
            isManualOverride = current.isManualOverride,
        )
        viewModelScope.launch {
            overrideActivityUseCase(dbEntity, newActivity)
        }
    }

    /**
     * Reverts the most recent override by restoring the previous [TransitionEvent] state.
     *
     * If no previous state is available (e.g. no override has been performed yet), this
     * method is a no-op. After undoing, the stored previous state is cleared.
     */
    fun undoLastOverride() {
        val previous = previousTransitionState ?: return
        previousTransitionState = null
        // Re-build the DB entity from the stored previous domain model so we can call the use case
        // to restore the original toActivity. Note: isManualOverride will remain true after undo
        // because OverrideActivityUseCase always sets it — this is an accepted trade-off.
        val dbEntity = ActivityTransition(
            id = previous.id,
            timestamp = previous.timestamp,
            fromActivity = previous.fromActivity.name,
            toActivity = previous.toActivity.name,
            isManualOverride = previous.isManualOverride,
        )
        viewModelScope.launch {
            overrideActivityUseCase(dbEntity, previous.toActivity)
        }
    }
}
