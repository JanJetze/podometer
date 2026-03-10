// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepBucketRepository
import com.podometer.domain.model.DaySummary
import com.podometer.domain.usecase.GetStreakUseCase
import com.podometer.domain.usecase.GetTodayStepsUseCase
import com.podometer.domain.usecase.GetWeeklyStepsUseCase
import com.podometer.domain.usecase.TodayProgress
import com.podometer.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * UI state for the Dashboard screen (v2).
 *
 * All fields are derived from domain use cases, repositories, and preferences,
 * combined into a single snapshot that the Compose UI observes.
 *
 * @property todaySteps      Total steps taken today.
 * @property todayDistance   Approximate distance walked today in kilometres.
 * @property minimumGoal     Lower step-goal tier (sage green on the ring).
 * @property targetGoal      Middle step-goal tier (forest green on the ring).
 * @property stretchGoal     Upper step-goal tier; 100% ring fill.
 * @property isRestDay       True when today is a configured rest day.
 * @property streakDays      Number of consecutive goal-met days.
 * @property todayGoalMet    True when today's minimum goal has already been met.
 * @property todayBuckets    5-minute step bars for the today chart.
 * @property chartResolution Currently selected resolution for [TodayStepChart].
 * @property weeklyDays      Per-day summaries for the current calendar week.
 * @property isLoading       True while the initial data is loading.
 * @property permissionsDenied True when required permissions have been denied.
 */
data class DashboardUiState(
    val todaySteps: Int = 0,
    val todayDistance: Double = 0.0,
    val minimumGoal: Int = 5_000,
    val targetGoal: Int = 8_000,
    val stretchGoal: Int = 12_000,
    val isRestDay: Boolean = false,
    val streakDays: Int = 0,
    val todayGoalMet: Boolean = false,
    val todayBuckets: List<StepBar> = emptyList(),
    val chartResolution: ChartResolution = ChartResolution.HOURLY,
    val weeklyDays: List<DaySummary> = emptyList(),
    val isLoading: Boolean = true,
    val permissionsDenied: Boolean = false,
)

/**
 * ViewModel for the Dashboard screen.
 *
 * Injects domain use cases and repositories via Hilt and merges their
 * [kotlinx.coroutines.flow.Flow]s into a single [StateFlow] of [DashboardUiState].
 *
 * @param getTodaySteps         Provides live today step count and distance.
 * @param getWeeklySteps        Provides per-day weekly summaries.
 * @param getStreak             Provides the current streak and today's goal progress.
 * @param stepBucketRepository  Provides 5-minute step buckets for today's chart.
 * @param preferencesManager    Source for goal tiers and rest-day configuration.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    getTodaySteps: GetTodayStepsUseCase,
    getWeeklySteps: GetWeeklyStepsUseCase,
    private val getStreak: GetStreakUseCase,
    private val stepBucketRepository: StepBucketRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        /** Timeout (ms) to keep the upstream flows active after the last subscriber leaves. */
        internal const val STOP_TIMEOUT_MS = 5_000L
    }

    /** Tracks runtime permission state independently of the data flows. */
    private val _permissionsDenied = MutableStateFlow(false)

    /** Tracks the selected chart resolution; defaults to hourly. */
    private val _chartResolution = MutableStateFlow(ChartResolution.HOURLY)

    /**
     * A cold [Flow] that fetches streak info once on collection.
     *
     * [GetStreakUseCase] is a one-shot suspend function, so we wrap it in a [flow]
     * builder. The combine below will call this each time the outer combine restarts.
     */
    private fun streakFlow(): Flow<com.podometer.domain.usecase.StreakInfo> = flow {
        emit(getStreak())
    }

    /** Combined UI state emitted to the Dashboard Compose screen. */
    val uiState: StateFlow<DashboardUiState> = combine(
        getTodaySteps(),
        getWeeklySteps(),
        preferencesManager.minimumStepGoal(),
        preferencesManager.targetStepGoal(),
        preferencesManager.stretchStepGoal(),
        preferencesManager.restDays(),
        stepBucketRepository.getBucketsForDay(
            startOfDay = DateTimeUtils.todayStartMillis(),
            endOfDay = DateTimeUtils.todayStartMillis() + 24L * 60 * 60 * 1000 - 1,
        ),
        streakFlow(),
    ) { args ->
        // combine with 8 sources uses an Array<Any?> overload
        @Suppress("UNCHECKED_CAST")
        val stepData = args[0] as com.podometer.domain.model.StepData
        @Suppress("UNCHECKED_CAST")
        val weekly = args[1] as List<DaySummary>
        @Suppress("UNCHECKED_CAST")
        val minimumGoal = args[2] as Int
        @Suppress("UNCHECKED_CAST")
        val targetGoal = args[3] as Int
        @Suppress("UNCHECKED_CAST")
        val stretchGoal = args[4] as Int
        @Suppress("UNCHECKED_CAST")
        val restDays = args[5] as Set<DayOfWeek>
        @Suppress("UNCHECKED_CAST")
        val dbBuckets = args[6] as List<com.podometer.data.db.StepBucket>
        @Suppress("UNCHECKED_CAST")
        val streak = args[7] as com.podometer.domain.usecase.StreakInfo

        val todayBuckets = dbBuckets.map { StepBar(startTime = it.timestamp, stepCount = it.stepCount) }
        val isRestDay = LocalDate.now().dayOfWeek in restDays

        DashboardUiState(
            todaySteps = stepData.steps,
            todayDistance = (stepData.distanceKm).toDouble(),
            minimumGoal = minimumGoal,
            targetGoal = targetGoal,
            stretchGoal = stretchGoal,
            isRestDay = isRestDay,
            streakDays = streak.currentStreak,
            todayGoalMet = streak.todayProgress == TodayProgress.MET,
            todayBuckets = todayBuckets,
            chartResolution = ChartResolution.HOURLY,
            weeklyDays = weekly,
            isLoading = false,
            permissionsDenied = _permissionsDenied.value,
        )
    }.let { combinedFlow ->
        // Layer in permissionsDenied and chartResolution changes
        combine(combinedFlow, _permissionsDenied, _chartResolution) { base, denied, resolution ->
            base.copy(permissionsDenied = denied, chartResolution = resolution)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardUiState(),
    )

    /**
     * Updates the chart resolution in [uiState].
     *
     * @param resolution The new [ChartResolution] to display.
     */
    fun setChartResolution(resolution: ChartResolution) {
        _chartResolution.update { resolution }
    }

    /**
     * Updates the permission state in [uiState].
     *
     * @param permissionsGranted True if ACTIVITY_RECOGNITION is currently granted.
     */
    fun refreshPermissions(permissionsGranted: Boolean) {
        _permissionsDenied.update { !permissionsGranted }
    }
}
