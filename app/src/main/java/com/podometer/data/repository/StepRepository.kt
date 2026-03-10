// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepDao
import com.podometer.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for daily step-count summary data.
 *
 * Delegates all persistence to [StepDao].
 * Step-bucket data is managed separately by [StepBucketRepository].
 *
 * The only transformation applied is mapping a null step count to 0 in
 * [getTodaySteps].
 */
@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao,
) {

    // ─── Read ────────────────────────────────────────────────────────────────

    /**
     * Returns today's total step count from [DailySummary] as a [Flow].
     * Emits 0 when the DAO returns null (no summary row yet for today).
     */
    fun getTodaySteps(): Flow<Int> =
        stepDao.getDailySummary(DateTimeUtils.toLocalDate(getTodayStartMillis()).toString())
            .map { it?.totalSteps ?: 0 }

    /** Returns the [DailySummary] for the given [date] ("yyyy-MM-dd"), or null. */
    fun getDailySummary(date: String): Flow<DailySummary?> =
        stepDao.getDailySummary(date)

    /**
     * Returns daily summaries whose date falls in [[startDate], [endDate]]
     * (inclusive), ordered by date ascending.
     */
    fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>> =
        stepDao.getWeeklyDailySummaries(startDate, endDate)

    // ─── One-shot reads for service-restart recovery ──────────────────────────

    /**
     * Returns today's total step count from [DailySummary], or 0 if no row exists.
     * Used during service restart to seed [com.podometer.service.StepAccumulator].
     */
    suspend fun getTodayTotalStepsSnapshot(): Int =
        stepDao.getTodayTotalStepsSnapshot(
            DateTimeUtils.toLocalDate(getTodayStartMillis()).toString()
        ) ?: 0

    // ─── Write ───────────────────────────────────────────────────────────────

    /** Inserts or replaces the daily summary for the given calendar day. */
    suspend fun upsertDailySummary(summary: DailySummary) {
        stepDao.upsertDailySummary(summary)
    }

    /**
     * Updates the [DailySummary.totalSteps] and [DailySummary.totalDistance]
     * for [date].
     */
    suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) {
        stepDao.upsertStepsAndDistance(date, totalSteps, totalDistance)
    }

    /**
     * Returns all daily summaries ordered by date ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllDailySummaries(): List<DailySummary> =
        stepDao.getAllDailySummaries()

    // ─── Helper ──────────────────────────────────────────────────────────────

    /**
     * Returns the epoch-millisecond timestamp for midnight at the start of today.
     *
     * Delegates to [DateTimeUtils.todayStartMillis].
     */
    fun getTodayStartMillis(): Long = DateTimeUtils.todayStartMillis()
}
