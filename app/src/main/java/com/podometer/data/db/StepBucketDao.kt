// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [StepBucket] — 5-minute clock-aligned step-count buckets.
 *
 * Flow-returning queries emit a new value whenever the underlying table changes.
 * Write operations are [suspend] so callers must use a coroutine context.
 */
@Dao
interface StepBucketDao {

    /**
     * Inserts or replaces a [StepBucket] row.
     *
     * Because [StepBucket.timestamp] is the primary key, calling this with an
     * existing timestamp overwrites the step count for that bucket.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bucket: StepBucket)

    /**
     * Returns all [StepBucket] rows whose [StepBucket.timestamp] falls in the
     * range [[startOfDay], [endOfDay]] (inclusive), ordered by timestamp ascending.
     *
     * Use midnight of the target day for [startOfDay] and the last millisecond of
     * that day (or midnight of the next day minus 1 ms) for [endOfDay].
     */
    @Query("SELECT * FROM step_buckets WHERE timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp ASC")
    fun getBucketsForDay(startOfDay: Long, endOfDay: Long): Flow<List<StepBucket>>

    /**
     * Returns all [StepBucket] rows whose [StepBucket.timestamp] falls in the
     * range [[start], [end]] (inclusive), ordered by timestamp ascending.
     */
    @Query("SELECT * FROM step_buckets WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getBucketsInRange(start: Long, end: Long): Flow<List<StepBucket>>

    /**
     * Returns the step count for the bucket whose [StepBucket.timestamp] equals
     * [bucketTimestamp], or null if no row exists for that timestamp.
     * One-shot suspend query used for service-restart recovery.
     */
    @Query("SELECT stepCount FROM step_buckets WHERE timestamp = :bucketTimestamp LIMIT 1")
    suspend fun getStepsForBucket(bucketTimestamp: Long): Int?

    /**
     * Returns all [StepBucket] rows ordered by timestamp ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM step_buckets ORDER BY timestamp ASC")
    suspend fun getAllBuckets(): List<StepBucket>

    /**
     * Deletes all rows from the [StepBucket] table.
     *
     * Intended for testing and data-reset flows only.
     */
    @Query("DELETE FROM step_buckets")
    suspend fun deleteAll()
}
