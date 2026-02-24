// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.StepDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for step-count and activity-transition data.
 *
 * Delegates all persistence to [StepDao] and [ActivityTransitionDao].
 * The only transformation applied is mapping a null step count to 0 in
 * [getTodaySteps].
 */
@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao,
    private val activityTransitionDao: ActivityTransitionDao,
) {

    // ─── Read ────────────────────────────────────────────────────────────────

    /**
     * Returns today's total step count as a [Flow].
     * Emits 0 when the DAO returns null (no rows yet for today).
     */
    fun getTodaySteps(): Flow<Int> =
        stepDao.getTodayTotalSteps(getTodayStartMillis()).map { it ?: 0 }

    /** Returns all hourly step aggregates for today as a [Flow]. */
    fun getTodayHourlyAggregates(): Flow<List<HourlyStepAggregate>> =
        stepDao.getTodayHourlyAggregates(getTodayStartMillis())

    /** Returns the [DailySummary] for the given [date] ("yyyy-MM-dd"), or null. */
    fun getDailySummary(date: String): Flow<DailySummary?> =
        stepDao.getDailySummary(date)

    /**
     * Returns daily summaries whose date falls in [[startDate], [endDate]]
     * (inclusive), ordered by date ascending.
     */
    fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>> =
        stepDao.getWeeklyDailySummaries(startDate, endDate)

    /** Returns all activity transitions for today as a [Flow]. */
    fun getTodayTransitions(): Flow<List<ActivityTransition>> =
        activityTransitionDao.getTodayTransitions(getTodayStartMillis())

    // ─── One-shot reads for service-restart recovery ──────────────────────────

    /**
     * Returns the step count for the hourly aggregate whose timestamp equals
     * [hourTimestamp], or 0 if no row exists yet for that hour.
     * Used during service restart to seed [com.podometer.service.StepAccumulator].
     */
    suspend fun getStepsForHour(hourTimestamp: Long): Int =
        stepDao.getStepsForHour(hourTimestamp) ?: 0

    /**
     * Returns the sum of all hourly aggregate step counts for today, or 0 if
     * no rows exist yet.
     * Used during service restart to seed [com.podometer.service.StepAccumulator].
     */
    suspend fun getTodayTotalStepsSnapshot(): Int =
        stepDao.getTodayTotalStepsSnapshot(getTodayStartMillis()) ?: 0

    // ─── Write ───────────────────────────────────────────────────────────────

    /**
     * Upserts an hourly step aggregate: deletes any existing row for the same
     * timestamp then inserts the new one, all within a single transaction.
     * This avoids duplicate rows when the service restarts mid-hour and
     * re-flushes an hour that was already partially persisted.
     */
    suspend fun upsertHourlyAggregate(aggregate: HourlyStepAggregate) {
        stepDao.upsertHourlyAggregate(aggregate)
    }

    /** Inserts a new hourly step aggregate row. */
    suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate) {
        stepDao.insertHourlyAggregate(aggregate)
    }

    /** Inserts or replaces the daily summary for the given calendar day. */
    suspend fun upsertDailySummary(summary: DailySummary) {
        stepDao.upsertDailySummary(summary)
    }

    /** Inserts a new activity transition row. */
    suspend fun insertTransition(transition: ActivityTransition) {
        activityTransitionDao.insertTransition(transition)
    }

    /** Updates an existing activity transition row (matched by primary key). */
    suspend fun updateTransition(transition: ActivityTransition) {
        activityTransitionDao.updateTransition(transition)
    }

    /**
     * Returns all daily summaries ordered by date ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllDailySummaries(): List<DailySummary> =
        stepDao.getAllDailySummaries()

    /**
     * Returns all hourly step aggregates ordered by timestamp ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> =
        stepDao.getAllHourlyAggregates()

    /**
     * Returns all activity transitions ordered by timestamp ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllTransitions(): List<ActivityTransition> =
        activityTransitionDao.getAllTransitions()

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Returns the epoch-millisecond timestamp for midnight at the start of today. */
    fun getTodayStartMillis(): Long =
        LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
