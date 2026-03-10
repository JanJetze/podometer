// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.StepDao
import com.podometer.data.repository.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Indicates whether the user has already met the minimum step goal for today.
 */
enum class TodayProgress {
    /** Today's step count is at or above the minimum goal. */
    MET,

    /** Today's step count is below the minimum goal (or no data recorded yet). */
    NOT_MET,
}

/**
 * The result of a streak calculation.
 *
 * @property currentStreak The number of consecutive days (excluding skipped rest days)
 *   on which the minimum step goal was met. If today has not yet met the goal, the streak
 *   is measured from yesterday backwards.
 * @property todayProgress Whether today has already met the minimum step goal.
 */
data class StreakInfo(
    val currentStreak: Int,
    val todayProgress: TodayProgress,
)

/** Functional interface for retrieving the current step streak. */
fun interface GetStreakUseCase {
    /** Returns the current [StreakInfo] for the user. */
    suspend operator fun invoke(): StreakInfo
}

/**
 * Calculates the current consecutive-day streak of meeting the minimum step goal.
 *
 * Rules:
 * - A day counts toward the streak if `totalSteps >= minimumStepGoal`.
 * - Rest days (configured in [PreferencesManager]) are skipped; they neither count
 *   nor break the streak.
 * - The streak counts backwards from today.
 * - If today has not yet met the minimum goal, the streak is reported as the streak
 *   ending on yesterday (today is "in progress").
 * - A day with no data that is not a rest day breaks the streak.
 *
 * @param stepDao           DAO used to query historical daily summaries.
 * @param preferencesManager Source of the minimum goal and rest-day configuration.
 * @param today             The date treated as "today". Injected for testability;
 *   defaults to [LocalDate.now].
 */
class GetStreakUseCaseImpl @Inject constructor(
    private val stepDao: StepDao,
    private val preferencesManager: PreferencesManager,
    private val today: LocalDate = LocalDate.now(),
) : GetStreakUseCase {

    private companion object {
        /** How many past days to load at once when walking backwards through history. */
        private const val HISTORY_WINDOW = 400
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    override suspend operator fun invoke(): StreakInfo {
        val minimumGoal = preferencesManager.minimumStepGoal().first()
        val restDays = preferencesManager.restDays().first()

        // Load a window of recent summaries, most-recent first.
        val summaries = stepDao.getDailySummariesUpTo(
            endDate = today.format(FORMATTER),
            limit = HISTORY_WINDOW,
        )
        // Build a map for O(1) lookup.
        val summaryByDate: Map<String, Int> = summaries.associate { it.date to it.totalSteps }

        // Determine today's progress.
        val todaySteps = summaryByDate[today.format(FORMATTER)] ?: 0
        val todayMet = todaySteps >= minimumGoal
        val todayProgress = if (todayMet) TodayProgress.MET else TodayProgress.NOT_MET

        // If today is a rest day, skip it and start counting from yesterday.
        // If today has not met the goal, also start counting from yesterday.
        val startDate = when {
            today.dayOfWeek in restDays -> today.minusDays(1)
            !todayMet -> today.minusDays(1)
            else -> today
        }

        val streak = countStreak(
            startDate = startDate,
            minimumGoal = minimumGoal,
            restDays = restDays,
            summaryByDate = summaryByDate,
        )

        return StreakInfo(currentStreak = streak, todayProgress = todayProgress)
    }

    /**
     * Walks backwards from [startDate] counting consecutive days where the minimum
     * goal is met, skipping rest days.
     */
    private fun countStreak(
        startDate: LocalDate,
        minimumGoal: Int,
        restDays: Set<java.time.DayOfWeek>,
        summaryByDate: Map<String, Int>,
    ): Int {
        var streak = 0
        var date = startDate

        while (true) {
            if (date.dayOfWeek in restDays) {
                // Skip rest day without breaking streak.
                date = date.minusDays(1)
                continue
            }

            val steps = summaryByDate[date.format(FORMATTER)]
            if (steps == null || steps < minimumGoal) {
                // No data or below minimum — streak ends.
                break
            }

            streak++
            date = date.minusDays(1)
        }

        return streak
    }
}
