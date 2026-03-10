// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for daily-summary data.
 *
 * Flow-returning queries emit a new value whenever the underlying table changes.
 * Write operations are [suspend] so callers must use a coroutine context.
 *
 * Step-bucket data is managed separately via [StepBucketDao].
 */
@Dao
interface StepDao {

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
     * Returns the sum of [DailySummary.totalSteps] for [date], or null if no row exists.
     * One-shot suspend query used for service-restart recovery.
     */
    @Query("SELECT totalSteps FROM daily_summaries WHERE date = :date LIMIT 1")
    suspend fun getTodayTotalStepsSnapshot(date: String): Int?

    /**
     * Inserts or replaces a [DailySummary] row. Because [DailySummary.date]
     * is the primary key, this acts as an upsert for the given calendar day.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySummary(summary: DailySummary)

    /**
     * Inserts or updates the [DailySummary.totalSteps] and
     * [DailySummary.totalDistance] columns for the given [date].
     *
     * On conflict (row already exists for [date]), only the steps and distance
     * columns are overwritten.
     * On insert (no row yet), both columns are set to the provided values.
     */
    @Query("""
        INSERT INTO daily_summaries (date, totalSteps, totalDistance)
        VALUES (:date, :totalSteps, :totalDistance)
        ON CONFLICT(date) DO UPDATE SET
            totalSteps = :totalSteps,
            totalDistance = :totalDistance
    """)
    suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float)

    /** Inserts multiple [DailySummary] rows, replacing on conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDailySummaries(summaries: List<DailySummary>)

    /**
     * Returns all [DailySummary] rows ordered by date ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM daily_summaries ORDER BY date ASC")
    suspend fun getAllDailySummaries(): List<DailySummary>

    /**
     * Returns up to [limit] [DailySummary] rows whose [DailySummary.date] is on
     * or before [endDate], ordered by date descending (most recent first).
     *
     * Used by streak calculation to walk backwards through history without
     * loading the entire table.
     *
     * @param endDate Inclusive upper bound in "yyyy-MM-dd" format.
     * @param limit   Maximum number of rows to return.
     */
    @Query("SELECT * FROM daily_summaries WHERE date <= :endDate ORDER BY date DESC LIMIT :limit")
    suspend fun getDailySummariesUpTo(endDate: String, limit: Int): List<DailySummary>
}
