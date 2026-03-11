// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.DaySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── TrendsPeriod enum ────────────────────────────────────────────────────────

/**
 * The time period granularity shown in the Trends screen.
 */
enum class TrendsPeriod {
    /** View 7 days (Monday to Sunday) for the selected week. */
    WEEKLY,

    /** View all days of the selected month. */
    MONTHLY,
}

// ─── TrendsUiState ────────────────────────────────────────────────────────────

/**
 * UI state for the Trends screen.
 *
 * @property period       Currently selected [TrendsPeriod].
 * @property days         Per-day summaries for the current period.
 * @property stats        Aggregated [TrendsStats] computed from [days].
 * @property targetGoal   User's target daily step goal.
 * @property minimumGoal  User's minimum daily step goal.
 * @property periodLabel  Human-readable label for the current period (e.g. "Mar 3 – Mar 9").
 * @property canGoNext    True when a later period exists to navigate to (false at current week/month).
 */
data class TrendsUiState(
    val period: TrendsPeriod = TrendsPeriod.WEEKLY,
    val days: List<DaySummary> = emptyList(),
    val stats: TrendsStats = TrendsStats(0, 0, "", 0.0, 0f),
    val targetGoal: Int = 8_000,
    val minimumGoal: Int = 5_000,
    val periodLabel: String = "",
    val canGoNext: Boolean = false,
)

// ─── TrendsViewModel ──────────────────────────────────────────────────────────

/**
 * ViewModel for the Trends screen.
 *
 * Manages weekly and monthly step history, period navigation, period toggle,
 * and stats computation. Injects [StepRepository] and [PreferencesManager] via Hilt.
 *
 * Navigation logic:
 * - offset 0 = current week or month.
 * - Negative offsets navigate to earlier periods.
 * - [TrendsUiState.canGoNext] is false when at offset 0 (the current period).
 *
 * @param stepRepository     Provides daily summaries for a date range.
 * @param preferencesManager Provides target and minimum step goals.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        /** Timeout (ms) to keep the upstream flows active after the last subscriber leaves. */
        internal const val STOP_TIMEOUT_MS = 5_000L

        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    // ─── Mutable navigation state ─────────────────────────────────────────────

    /** Current period granularity. */
    private val _period = MutableStateFlow(TrendsPeriod.WEEKLY)

    /**
     * Current offset relative to today's week/month.
     * 0 = current, -1 = previous, -2 = two periods ago, etc.
     */
    private val _offset = MutableStateFlow(0)

    // ─── Date range computation ───────────────────────────────────────────────

    /**
     * Computes the [startDate], [endDate] ISO strings and a human-readable label
     * for the given [period] and [offset].
     *
     * @param period The [TrendsPeriod] to compute the range for.
     * @param offset The offset relative to today's period (0 = current, -1 = previous, etc.).
     * @param today  The reference date; defaults to [LocalDate.now].
     * @return A [Triple] of (startDate, endDate, label) where dates are "yyyy-MM-dd" strings.
     */
    internal fun computeDateRange(
        period: TrendsPeriod,
        offset: Int,
        today: LocalDate = LocalDate.now(),
    ): Triple<String, String, String> {
        return when (period) {
            TrendsPeriod.WEEKLY -> {
                // ISO week: Monday to Sunday
                val mondayThisWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
                val monday = mondayThisWeek.plusWeeks(offset.toLong())
                val sunday = monday.plusDays(6)
                val startDate = monday.format(ISO_FORMATTER)
                val endDate = sunday.format(ISO_FORMATTER)
                val label = formatWeekLabel(startDate, endDate)
                Triple(startDate, endDate, label)
            }
            TrendsPeriod.MONTHLY -> {
                val thisMonth = YearMonth.from(today)
                val month = thisMonth.plusMonths(offset.toLong())
                val startDate = month.atDay(1).format(ISO_FORMATTER)
                val endDate = month.atEndOfMonth().format(ISO_FORMATTER)
                val label = month.format(MONTH_LABEL_FORMATTER)
                Triple(startDate, endDate, label)
            }
        }
    }

    // ─── Data flow ────────────────────────────────────────────────────────────

    /**
     * A flow that emits the current (startDate, endDate, label) triple whenever
     * [_period] or [_offset] changes.
     */
    private val dateRangeFlow = combine(_period, _offset) { period, offset ->
        computeDateRange(period, offset)
    }

    /**
     * Queries the repository for daily summaries in the current date range.
     * Re-queries whenever the period or offset changes via [flatMapLatest].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val summariesFlow = dateRangeFlow.flatMapLatest { (startDate, endDate, _) ->
        stepRepository.getWeeklyDailySummaries(startDate, endDate).map { dbList ->
            dbList.map { db ->
                DaySummary(
                    date = db.date,
                    totalSteps = db.totalSteps,
                    totalDistanceKm = db.totalDistance,
                )
            }
        }
    }

    /** Combined UI state exposed to the Trends screen. */
    val uiState: StateFlow<TrendsUiState> = combine(
        _period,
        _offset,
        summariesFlow,
        preferencesManager.targetStepGoal(),
        preferencesManager.minimumStepGoal(),
    ) { period, offset, days, targetGoal, minimumGoal ->
        val (_, _, label) = computeDateRange(period, offset)
        val stats = computeTrendsStats(days, targetGoal)
        TrendsUiState(
            period = period,
            days = days,
            stats = stats,
            targetGoal = targetGoal,
            minimumGoal = minimumGoal,
            periodLabel = label,
            canGoNext = offset < 0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TrendsUiState(),
    )

    // ─── User actions ─────────────────────────────────────────────────────────

    /**
     * Switches the active period granularity and resets the offset to 0
     * (current week or month).
     *
     * @param period The new [TrendsPeriod] to display.
     */
    fun setPeriod(period: TrendsPeriod) {
        _period.update { period }
        _offset.update { 0 }
    }

    /**
     * Navigates to the previous week or month (decrements the offset by 1).
     */
    fun previousPeriod() {
        _offset.update { it - 1 }
    }

    /**
     * Navigates to the next week or month (increments the offset toward 0).
     * Has no effect when already at the current period (offset == 0).
     */
    fun nextPeriod() {
        _offset.update { current -> if (current < 0) current + 1 else 0 }
    }
}
