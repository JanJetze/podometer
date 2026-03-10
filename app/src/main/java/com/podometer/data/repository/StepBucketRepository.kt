// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepBucketDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for [StepBucket] — 5-minute clock-aligned step-count buckets.
 *
 * Delegates all persistence to [StepBucketDao]. Acts as the single source of
 * truth for bucket-level step data.
 */
@Singleton
class StepBucketRepository @Inject constructor(
    private val stepBucketDao: StepBucketDao,
) {

    // ─── Write ───────────────────────────────────────────────────────────────

    /**
     * Inserts or replaces a [StepBucket] row.
     *
     * Because the timestamp is the primary key, calling this with the same
     * timestamp overwrites the step count for that bucket (upsert semantics).
     */
    suspend fun upsert(bucket: StepBucket) {
        stepBucketDao.upsert(bucket)
    }

    // ─── Read ────────────────────────────────────────────────────────────────

    /**
     * Returns all [StepBucket] rows for the given day as a [Flow].
     *
     * @param startOfDay Epoch-millis for midnight at the start of the day.
     * @param endOfDay   Epoch-millis for the last instant of the day (e.g. midnight - 1 ms).
     */
    fun getBucketsForDay(startOfDay: Long, endOfDay: Long): Flow<List<StepBucket>> =
        stepBucketDao.getBucketsForDay(startOfDay, endOfDay)

    /**
     * Returns all [StepBucket] rows whose timestamp falls in [[start], [end]]
     * (inclusive) as a [Flow].
     */
    fun getBucketsInRange(start: Long, end: Long): Flow<List<StepBucket>> =
        stepBucketDao.getBucketsInRange(start, end)

    // ─── One-shot reads for service-restart recovery ──────────────────────────

    /**
     * Returns the step count for the bucket whose timestamp equals
     * [bucketTimestamp], or 0 if no row exists yet for that bucket.
     * Used during service restart to seed [com.podometer.service.StepAccumulator].
     */
    suspend fun getStepsForBucket(bucketTimestamp: Long): Int =
        stepBucketDao.getStepsForBucket(bucketTimestamp) ?: 0

    /**
     * Returns all step buckets ordered by timestamp ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllBuckets(): List<StepBucket> =
        stepBucketDao.getAllBuckets()

    // ─── Testing / Reset ─────────────────────────────────────────────────────

    /**
     * Deletes all rows from the step_buckets table.
     *
     * Intended for testing and data-reset flows only.
     */
    suspend fun deleteAll() {
        stepBucketDao.deleteAll()
    }
}
