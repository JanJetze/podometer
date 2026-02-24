// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for step-count and daily-summary data.
 *
 * Flow-returning queries emit a new value whenever the underlying table changes.
 * Write operations are [suspend] so callers must use a coroutine context.
 */
@Dao
interface StepDao {

    /**
     * Returns all [HourlyStepAggregate] rows with a timestamp >= [todayStart],
     * ordered by timestamp ascending.
     */
    @Query("SELECT * FROM hourly_step_aggregates WHERE timestamp >= :todayStart ORDER BY timestamp ASC")
    fun getTodayHourlyAggregates(todayStart: Long): Flow<List<HourlyStepAggregate>>

    /**
     * Returns the sum of [HourlyStepAggregate.stepCountDelta] for today,
     * or null if there are no rows yet.
     */
    @Query("SELECT SUM(stepCountDelta) FROM hourly_step_aggregates WHERE timestamp >= :todayStart")
    fun getTodayTotalSteps(todayStart: Long): Flow<Int?>

    /** Returns the [DailySummary] for the given [date] ("yyyy-MM-dd"), or null. */
    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    fun getDailySummary(date: String): Flow<DailySummary?>

    /**
     * Returns [DailySummary] rows whose [DailySummary.date] falls in the
     * range [[startDate], [endDate]] (inclusive), ordered by date ascending.
     */
    @Query("SELECT * FROM daily_summaries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getWeeklyDailySummaries(startDate: String, endDate: String): Flow<List<DailySummary>>

    /**
     * Returns the step count delta for the hourly aggregate matching
     * [hourTimestamp] exactly, or null if no row exists for that timestamp.
     * One-shot suspend query used for service-restart recovery.
     */
    @Query("SELECT stepCountDelta FROM hourly_step_aggregates WHERE timestamp = :hourTimestamp LIMIT 1")
    suspend fun getStepsForHour(hourTimestamp: Long): Int?

    /**
     * Returns the sum of [HourlyStepAggregate.stepCountDelta] for all rows
     * on or after [todayStart] (i.e. today), or null if no rows exist yet.
     * One-shot suspend query used for service-restart recovery.
     */
    @Query("SELECT SUM(stepCountDelta) FROM hourly_step_aggregates WHERE timestamp >= :todayStart")
    suspend fun getTodayTotalStepsSnapshot(todayStart: Long): Int?

    /**
     * Deletes the [HourlyStepAggregate] row whose timestamp equals
     * [hourTimestamp]. Used as the delete half of a delete-then-insert upsert
     * when re-flushing a partially-persisted hour after a service restart.
     */
    @Query("DELETE FROM hourly_step_aggregates WHERE timestamp = :hourTimestamp")
    suspend fun deleteHourlyAggregateByTimestamp(hourTimestamp: Long)

    /** Inserts a new [HourlyStepAggregate] row. */
    @Insert
    suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate)

    /**
     * Deletes any existing [HourlyStepAggregate] row for the same timestamp,
     * then inserts [aggregate] — a safe upsert given the auto-generated PK.
     * Wrapped in a [Transaction] so both operations are atomic.
     */
    @Transaction
    suspend fun upsertHourlyAggregate(aggregate: HourlyStepAggregate) {
        deleteHourlyAggregateByTimestamp(aggregate.timestamp)
        insertHourlyAggregate(aggregate)
    }

    /**
     * Inserts or replaces a [DailySummary] row. Because [DailySummary.date]
     * is the primary key, this acts as an upsert for the given calendar day.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySummary(summary: DailySummary)

    /**
     * Returns all [DailySummary] rows ordered by date ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM daily_summaries ORDER BY date ASC")
    suspend fun getAllDailySummaries(): List<DailySummary>

    /**
     * Returns all [HourlyStepAggregate] rows ordered by timestamp ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM hourly_step_aggregates ORDER BY timestamp ASC")
    suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate>
}
